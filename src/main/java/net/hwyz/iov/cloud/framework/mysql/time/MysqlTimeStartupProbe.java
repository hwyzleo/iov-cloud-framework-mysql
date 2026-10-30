package net.hwyz.iov.cloud.framework.mysql.time;

import com.alibaba.druid.pool.DruidDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * FW-MYSQL 时间治理启动探针（FW-MYSQL-DSN-CR-001，RD-003）。
 * <p>
 * 与 FW-WEB {@code JacksonStartupProbe} 同模式：{@link SmartInitializingSingleton}，
 * 在 web server 就绪放行前运行；<b>仅当 {@link MysqlTimeProperties#isEnabled()} 为 true 时校验</b>，
 * 任一失败抛异常 → 启动失败，不允许未治理连接接入业务流量。
 * <p>
 * 校验内容（完整严格，无绕过子开关）：
 * <ol>
 *   <li>JVM/OS 时区：{@code ZoneId.systemDefault()} 必须为 UTC 等价的固定偏移时区；</li>
 *   <li>datasource URL：{@link MysqlUrlUtcValidator} 解析式校验
 *       （connectionTimeZone=UTC / forceConnectionTimeZoneToSession=true / preserveInstants=true，
 *       重复/冲突参数拒绝）；</li>
 *   <li>Druid 会话初始化：connectionInitSqls 必须已含 {@code SET time_zone='+00:00'}
 *       （由 {@link MysqlDataSourceInitSqlPostProcessor} 合并，此处只校验）；</li>
 *   <li>真实连接（只读）：对每个实际 DataSource 建立连接执行
 *       {@code SELECT @@session.time_zone, NOW(3), UTC_TIMESTAMP(3)}，断言会话时区 UTC 等价、
 *       {@code NOW(3)} 与 {@code UTC_TIMESTAMP(3)} 一致（会话 UTC 下同一时刻）；</li>
 *   <li>数据源覆盖：容器内所有 DataSource 逐一校验；非 Druid 数据源无法应用会话初始化 SQL，
 *       直接拒绝；无任何 DataSource 时拒绝（无法证明治理覆盖）。</li>
 * </ol>
 * 约束：
 * <ul>
 *   <li>生产探针<b>只读</b>（仅 SELECT 系统变量）；DEFAULT/ON UPDATE/handler 写入验证使用隔离测试表（测试环境）；</li>
 *   <li>探针只校验，发现漂移时摘除就绪并告警，<b>不自动 SET 回去掩盖违规</b>；</li>
 *   <li>日志不输出带账号口令的 URL。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
public class MysqlTimeStartupProbe implements SmartInitializingSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(MysqlTimeStartupProbe.class);

    /** 会话探测 SQL（包可见，供测试断言使用）。 */
    static final String SESSION_PROBE_SQL =
            "SELECT @@session.time_zone AS session_tz, NOW(3) AS now3, UTC_TIMESTAMP(3) AS utc3";

    private final MysqlTimeProperties properties;
    private final List<DataSource> dataSources;

    public MysqlTimeStartupProbe(MysqlTimeProperties properties, List<DataSource> dataSources) {
        this.properties = properties;
        this.dataSources = dataSources;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isEnabled()) {
            LOGGER.info("FW-MYSQL 时间治理未启用（iov.mysql.time.enabled=false）：跳过启动探针，服务未接入 UTC 治理");
            return;
        }
        try {
            verifyJvmUtc();
            if (dataSources == null || dataSources.isEmpty()) {
                throw new IllegalStateException("FW-MYSQL 时间治理已启用但未发现任何 DataSource，无法证明 UTC 会话覆盖，阻断启动");
            }
            for (DataSource dataSource : dataSources) {
                verifyDataSource(dataSource);
            }
        } catch (IllegalStateException | IllegalArgumentException | SQLException e) {
            throw new IllegalStateException(
                    "FW-MYSQL 时间治理启动探针失败，阻断发布（iov.mysql.time.enabled=true）: " + e.getMessage(), e);
        }
        LOGGER.info("FW-MYSQL 时间治理启动探针通过：JVM UTC、会话 UTC、驱动参数与数据源覆盖均符合要求");
    }

    private void verifyJvmUtc() {
        ZoneId zone = ZoneId.systemDefault();
        if (!isUtcEquivalent(zone)) {
            throw new IllegalStateException(
                    "JVM/OS 时区非 UTC 等价（实际: " + zone
                            + "）。要求 -Duser.timezone=UTC 且容器 TZ=UTC；生产探针拒绝非 UTC 启动环境");
        }
    }

    private void verifyDataSource(DataSource dataSource) throws SQLException {
        if (!(dataSource instanceof DruidDataSource druidDataSource)) {
            throw new IllegalStateException(
                    "发现非 Druid DataSource（" + dataSource.getClass().getName()
                            + "），无法应用会话初始化 SQL（SET time_zone='+00:00'），禁止静默跳过，阻断启动");
        }
        String url = druidDataSource.getUrl();
        MysqlUrlUtcValidator.validate(url);
        List<String> initSqls = null;
        Collection<String> collection = druidDataSource.getConnectionInitSqls();
        if (collection != null) {
            initSqls = new java.util.ArrayList<>(collection);
        }
        if (initSqls == null || initSqls.stream().noneMatch(
                s -> s != null && s.trim().toLowerCase(Locale.ROOT).startsWith("set time_zone"))) {
            throw new IllegalStateException(
                    "Druid connectionInitSqls 缺少会话 UTC 初始化（SET time_zone='+00:00'），阻断启动");
        }
        // 只读真实连接校验（触发首次物理连接，验证初始化 SQL 已生效于实际会话）
        try (Connection connection = druidDataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(SESSION_PROBE_SQL);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("会话探测查询无结果");
            }
            String sessionTz = rs.getString("session_tz");
            Timestamp now3 = rs.getTimestamp("now3");
            Timestamp utc3 = rs.getTimestamp("utc3");
            if (!utcEquivalentSession(sessionTz)) {
                throw new IllegalStateException(
                        "实际会话时区非 UTC 等价（@@session.time_zone=" + sessionTz + "），阻断启动");
            }
            if (now3 == null || utc3 == null || !now3.equals(utc3)) {
                throw new IllegalStateException(
                        "实际会话下 NOW(3) 与 UTC_TIMESTAMP(3) 不一致（now3=" + now3 + ", utc3=" + utc3
                                + "），会话并非 UTC，阻断启动");
            }
        }
    }

    private static boolean isUtcEquivalent(ZoneId zone) {
        ZoneRules rules = zone.getRules();
        return rules.isFixedOffset() && rules.getOffset(Instant.EPOCH).equals(ZoneOffset.UTC);
    }

    private static boolean utcEquivalentSession(String sessionTz) {
        if (sessionTz == null) {
            return false;
        }
        String normalized = sessionTz.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("+00:00") || normalized.equals("utc")
                || normalized.equals("etc/utc") || normalized.equals("gmt") || normalized.equals("z");
    }
}
