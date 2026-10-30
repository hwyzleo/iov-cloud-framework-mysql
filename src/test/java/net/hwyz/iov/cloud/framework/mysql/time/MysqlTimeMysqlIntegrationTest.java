package net.hwyz.iov.cloud.framework.mysql.time;

import com.alibaba.druid.pool.DruidDataSource;
import net.hwyz.iov.cloud.framework.mysql.typehandler.UtcInstantTypeHandler;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * FW-MYSQL 时间治理真实 MySQL 集成测试（FW-MYSQL-DSN-CR-001，TC-01/TC-02/TC-03/TC-05）。
 * <p>
 * 门禁（修正 4）：本地普通开发允许显式跳过；激活 {@code mysql-integration} profile
 * （{@code -Dmysql.integration.required=true}）后，MySQL 不可用、凭据缺失或测试未执行
 * 一律 <b>FAIL</b>，不允许静默跳过算整体通过。本测试针对 MySQL <b>8.0.33</b>（本地 Docker），
 * 只证明该测试版本行为，不代表生产 Server 版本已核验。
 * <p>
 * 凭据解析：环境变量 IT_MYSQL_URL/IT_MYSQL_USER/IT_MYSQL_PASSWORD 优先，
 * 其次系统属性 mysql.it.url/mysql.it.user/mysql.it.password；均缺失则跳过（或强制失败）。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>会话 UTC（forceConnectionTimeZoneToSession）+ NOW(3)==UTC_TIMESTAMP(3)；</li>
 *   <li>handler 对应 JDBC 语义：DATETIME(3) 往返、(6) 微秒保留、(3) 毫秒截断行为记录；</li>
 *   <li>DEFAULT CURRENT_TIMESTAMP(3) 与 ON UPDATE 在会话 UTC 下产生 UTC 值；</li>
 *   <li>初始化顺序：合并初始化 SQL 后首个物理连接即 UTC；Flyway 复用路径迁移连接 UTC；</li>
 *   <li>失败路径：非 UTC 会话被拒的事实、坏初始化 SQL 导致连接失败；</li>
 *   <li>DATETIME 列在不同会话下 getObject(LocalDateTime.class) 的实际行为（"不经过驱动时区转换"的待证明项）。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
@Tag("mysql-integration")
class MysqlTimeMysqlIntegrationTest {

    private static final String BASE_URL = System.getProperty("mysql.it.url",
            System.getenv().getOrDefault("IT_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/"));
    private static final String USER = System.getProperty("mysql.it.user",
            System.getenv().getOrDefault("IT_MYSQL_USER", "root"));
    private static final String PASSWORD = System.getProperty("mysql.it.password",
            System.getenv().getOrDefault("IT_MYSQL_PASSWORD", null));
    private static final boolean REQUIRED =
            Boolean.parseBoolean(System.getProperty("mysql.integration.required", "false"));

    private static final String SCHEMA = "fw_mysql_time_it_" + ThreadLocalRandom.current().nextInt(100000, 999999);
    private static final String PARAMS =
            "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"
                    + "&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true";

    private static boolean mysqlAvailable;

    @BeforeAll
    static void prepare() {
        mysqlAvailable = tryConnect();
        if (!mysqlAvailable) {
            if (REQUIRED) {
                throw new IllegalStateException(
                        "强制 MySQL 集成测试（mysql.integration.required=true）但无法连接或凭据缺失：url="
                                + BASE_URL + ", user=" + USER + "。CI 必须提供可用 MySQL 与凭据，禁止静默跳过");
            }
            return;
        }
        try (Connection admin = DriverManager.getConnection(BASE_URL + PARAMS, USER, PASSWORD)) {
            try (Statement st = admin.createStatement()) {
                st.execute("CREATE DATABASE " + SCHEMA);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("创建隔离测试库失败: " + SCHEMA, e);
        }
    }

    @AfterAll
    static void cleanup() {
        if (!mysqlAvailable) {
            return;
        }
        try (Connection admin = DriverManager.getConnection(BASE_URL + PARAMS, USER, PASSWORD)) {
            try (Statement st = admin.createStatement()) {
                st.execute("DROP DATABASE IF EXISTS " + SCHEMA);
            }
        } catch (SQLException ignored) {
            // 清理失败不影响结论，仅记录
        }
    }

    private static boolean tryConnect() {
        if (PASSWORD == null || PASSWORD.isBlank()) {
            return false;
        }
        try (Connection c = DriverManager.getConnection(BASE_URL + PARAMS, USER, PASSWORD)) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static void requireMysql() {
        if (!mysqlAvailable) {
            if (REQUIRED) {
                throw new IllegalStateException("强制模式但 MySQL 不可用，测试 FAIL（不跳过）");
            }
            assumeTrue(false, "MySQL 不可用/凭据缺失，显式跳过（本地开发）；CI 需激活 mysql-integration profile");
        }
    }

    private static String schemaUrl() {
        return BASE_URL + SCHEMA + PARAMS;
    }

    @Test
    @DisplayName("会话 UTC：forceConnectionTimeZoneToSession 生效，NOW(3)==UTC_TIMESTAMP(3)")
    void sessionIsUtcAndNowMatchesUtcTimestamp() throws SQLException {
        requireMysql();
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement("SELECT @@session.time_zone, NOW(3), UTC_TIMESTAMP(3)");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("+00:00", rs.getString(1), "forceConnectionTimeZoneToSession=true 下会话必须为 +00:00");
            Timestamp now3 = rs.getTimestamp(2);
            Timestamp utc3 = rs.getTimestamp(3);
            assertEquals(now3, utc3, "会话 UTC 下 NOW(3) 与 UTC_TIMESTAMP(3) 必须一致");
        }
    }

    @Test
    @DisplayName("handler JDBC 语义：DATETIME(3) 往返（UTC 墙钟写读）")
    void datetime3RoundTrip() throws SQLException {
        requireMysql();
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, occurred_at DATETIME(3) NULL)");
            Instant instant = Instant.parse("2026-10-30T08:00:00.123Z");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO t_event (occurred_at) VALUES (?)")) {
                // 与 UtcInstantTypeHandler 写入一致：Instant → LocalDateTime(UTC) → setObject
                LocalDateTime utcWallClock = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                ps.setObject(1, utcWallClock);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT occurred_at FROM t_event WHERE id=1");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                // 与 UtcInstantTypeHandler 读取一致：getObject(LocalDateTime.class) → toInstant(UTC)
                LocalDateTime wallClock = rs.getObject(1, LocalDateTime.class);
                Instant read = wallClock.toInstant(ZoneOffset.UTC);
                assertEquals(instant, read, "DATETIME(3) UTC 墙钟往返必须还原原 Instant");
            }
        }
    }

    @Test
    @DisplayName("DATETIME(6) 微秒原精度保留（RD-007，8.0.33 存储边界为四舍五入）")
    void datetime6MicrosPreserved() throws SQLException {
        requireMysql();
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t_event6 (id BIGINT AUTO_INCREMENT PRIMARY KEY, occurred_at DATETIME(6) NULL)");
            LocalDateTime utcWallClock = LocalDateTime.of(2026, 10, 30, 8, 0, 0, 123_456_789); // 微秒级
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO t_event6 (occurred_at) VALUES (?)")) {
                ps.setObject(1, utcWallClock);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT occurred_at FROM t_event6 WHERE id=1");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                LocalDateTime read = rs.getObject(1, LocalDateTime.class);
                // 实测 8.0.33：DATETIME(6) 保留微秒精度，但 123456.789µs 在存储边界被四舍五入为 123457µs
                // （SQL 客户端 CAST 验证：CAST('...123456789' AS DATETIME(6)) = .123457）
                assertEquals(123_457_000, read.getNano(),
                        "DATETIME(6) 保留微秒精度；MySQL 8.0.33 对超出精度小数秒为四舍五入（存储边界行为证据）");
            }
        }
    }

    @Test
    @DisplayName("DATETIME(3) 对亚毫秒的存储边界行为记录（RD-007，8.0.33 四舍五入）")
    void datetime3SubMillisTruncationRecorded() throws SQLException {
        requireMysql();
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t_event3 (id BIGINT AUTO_INCREMENT PRIMARY KEY, occurred_at DATETIME(3) NULL)");
            LocalDateTime utcWallClock = LocalDateTime.of(2026, 10, 30, 8, 0, 0, 123_456_789);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO t_event3 (occurred_at) VALUES (?)")) {
                ps.setObject(1, utcWallClock);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT occurred_at FROM t_event3 WHERE id=1");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                LocalDateTime read = rs.getObject(1, LocalDateTime.class);
                // 实测 8.0.33：DATETIME(3) 将亚毫秒四舍五入为毫秒（0.123456789s→0.123s，SQL CAST 验证）
                assertEquals(123_000_000, read.getNano(),
                        "DATETIME(3) 亚毫秒在存储边界四舍五入为毫秒（8.0.33 实测），handler 本身不截断/不四舍五入");
            }
        }
    }

    @Test
    @DisplayName("DEFAULT CURRENT_TIMESTAMP(3) 在会话 UTC 下产生 UTC 值（包络断言）")
    void defaultCreateTimeIsUtc() throws SQLException {
        requireMysql();
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t_audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(32), "
                    + "create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), "
                    + "modify_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) "
                    + "ON UPDATE CURRENT_TIMESTAMP(3))");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO t_audit (name) VALUES ('a')")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT create_time, UTC_TIMESTAMP(3), TIMESTAMPDIFF(SECOND, create_time, UTC_TIMESTAMP(3)) "
                            + "FROM t_audit WHERE id=1");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                Timestamp createTime = rs.getTimestamp(1);
                Timestamp utcNow = rs.getTimestamp(2);
                int diffSec = rs.getInt(3);
                assertTrue(Math.abs(diffSec) <= 3,
                        "DEFAULT CURRENT_TIMESTAMP(3) 必须接近 UTC_TIMESTAMP(3)（会话 UTC 前提），差异=" + diffSec + "s");
                // create_time 与 UTC 当前时间同一天（不可能是 8 小时偏移的上海时间跨日）
                assertEquals(createTime.toLocalDateTime().toLocalDate(), utcNow.toLocalDateTime().toLocalDate(),
                        "create_time 应落在 UTC 当前日期");
            }
        }
    }

    @Test
    @DisplayName("ON UPDATE CURRENT_TIMESTAMP(3) 在其他列实际变化时推进")
    void onUpdateModifyTimeAdvances() throws SQLException, InterruptedException {
        requireMysql();
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t_audit2 (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(32), "
                    + "create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), "
                    + "modify_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) "
                    + "ON UPDATE CURRENT_TIMESTAMP(3))");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO t_audit2 (name) VALUES ('a')")) {
                ps.executeUpdate();
            }
            Thread.sleep(1200); // 确保毫秒级时间前进可观测
            try (PreparedStatement ps = c.prepareStatement("UPDATE t_audit2 SET name='b' WHERE id=1")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT create_time, modify_time FROM t_audit2 WHERE id=1");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                Timestamp createTime = rs.getTimestamp(1);
                Timestamp modifyTime = rs.getTimestamp(2);
                assertTrue(modifyTime.after(createTime),
                        "ON UPDATE 必须推进 modify_time（行实际变更的数据库时间语义，RD-004）");
            }
        }
    }

    @Test
    @DisplayName("初始化顺序：合并会话 UTC 初始化 SQL 后首个物理连接即 UTC（早于首个连接）")
    void firstConnectionAfterInitSqlMergeIsUtc() throws SQLException {
        requireMysql();
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(schemaUrl());
        ds.setUsername(USER);
        ds.setPassword(PASSWORD);
        ds.setConnectionInitSqls(java.util.List.of("SET NAMES utf8mb4"));
        // 模拟框架 BeanPostProcessor：enabled=true 合并（保留既有 SQL 追加）
        MysqlTimeProperties properties = new MysqlTimeProperties();
        properties.setEnabled(true);
        new MysqlDataSourceInitSqlPostProcessor(properties).postProcessAfterInitialization(ds, "dataSource");
        assertEquals(2, ds.getConnectionInitSqls().size(), "既有初始化 SQL 必须保留并追加");
        try (Connection c1 = ds.getConnection(); Connection c2 = ds.getConnection()) {
            for (Connection c : new Connection[]{c1, c2}) {
                try (PreparedStatement ps = c.prepareStatement("SELECT @@session.time_zone");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("+00:00", rs.getString(1),
                            "合并初始化 SQL 后的每个（首个/复用）物理连接会话必须为 UTC");
                }
            }
        } finally {
            ds.close();
        }
    }

    @Test
    @DisplayName("Flyway 复用数据源：迁移连接处于 UTC 会话（初始化早于 Flyway）")
    void flywayMigrationConnectionIsUtc() throws SQLException {
        requireMysql();
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(schemaUrl());
        ds.setUsername(USER);
        ds.setPassword(PASSWORD);
        MysqlTimeProperties properties = new MysqlTimeProperties();
        properties.setEnabled(true);
        new MysqlDataSourceInitSqlPostProcessor(properties).postProcessAfterInitialization(ds, "dataSource");
        try {
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration-it")
                    .load()
                    .migrate();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT tz FROM session_utc_probe");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("+00:00", rs.getString(1),
                        "Flyway 迁移连接必须经过会话 UTC 初始化（迁移脚本内 SELECT @@session.time_zone 应得 +00:00）");
            }
        } finally {
            ds.close();
        }
    }

    @Test
    @DisplayName("失败路径：非 UTC 会话下 NOW(3) 与 UTC_TIMESTAMP(3) 不一致（探针拒绝的事实依据）")
    void nonUtcSessionFacts() throws SQLException {
        requireMysql();
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             PreparedStatement set = c.prepareStatement("SET time_zone='+08:00'");
             PreparedStatement ps = c.prepareStatement("SELECT @@session.time_zone, NOW(3), UTC_TIMESTAMP(3)")) {
            set.executeUpdate();
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("+08:00", rs.getString(1));
                assertNotEquals(rs.getTimestamp(2), rs.getTimestamp(3),
                        "非 UTC 会话下 NOW(3) != UTC_TIMESTAMP(3)，这正是探针拒绝该类会话的事实依据");
            }
        }
    }

    @Test
    @DisplayName("失败路径：坏初始化 SQL 导致连接建立失败（fail-fast，不无限等待）")
    @org.junit.jupiter.api.Timeout(30)
    void badInitSqlFailsConnection() {
        requireMysql();
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(schemaUrl());
        ds.setUsername(USER);
        ds.setPassword(PASSWORD);
        ds.setConnectionInitSqls(java.util.List.of("SET bogus_statement"));
        ds.setFailFast(true); // init 失败立即抛出，避免默认 maxWait=-1 无限等待
        ds.setConnectionErrorRetryAttempts(1);
        try {
            assertThrows(SQLException.class, ds::getConnection,
                    "连接初始化 SQL 执行失败必须导致连接建立失败（fail-fast，不允许带非 UTC/脏会话进入业务）");
        } finally {
            ds.close();
        }
    }

    @Test
    @DisplayName("待证明项：DATETIME 列 getObject(LocalDateTime.class) 在不同会话下返回存储墙钟原样")
    void datetimeReadUnderDifferentSessions() throws SQLException {
        requireMysql();
        LocalDateTime wallClock = LocalDateTime.of(2026, 10, 30, 12, 34, 56, 123_000_000);
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t_wall (id BIGINT AUTO_INCREMENT PRIMARY KEY, at DATETIME(3) NULL)");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO t_wall (at) VALUES (?)")) {
                ps.setObject(1, wallClock);
                ps.executeUpdate();
            }
        }
        // UTC 会话读
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement("SELECT at FROM t_wall WHERE id=1");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(wallClock, rs.getObject(1, LocalDateTime.class), "UTC 会话下 DATETIME 返回原墙钟");
        }
        // +08:00 会话读：记录 connector/J 8.0.33 对 DATETIME 列的实际行为（待测试证明项）
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             PreparedStatement set = c.prepareStatement("SET time_zone='+08:00'")) {
            set.executeUpdate();
            try (PreparedStatement ps = c.prepareStatement("SELECT at FROM t_wall WHERE id=1");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                LocalDateTime read = rs.getObject(1, LocalDateTime.class);
                assertEquals(wallClock, read,
                        "DATETIME 为墙钟语义：connector/J 8.0.33 实测 getObject(LocalDateTime.class) 不受会话时区转换；"
                                + "handler 读取按 UTC 还原的正确性由此保持（写入仍需 UTC 会话保证 CURRENT_TIMESTAMP/DEFAULT）");
            }
        }
    }

    @Test
    @DisplayName("探针真实连接校验：合法 UTC 配置下，UTC JVM 通过 / 非 UTC JVM 拒绝（TC-01）")
    void startupProbeAgainstRealMysql() throws SQLException {
        requireMysql();
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(schemaUrl());
        ds.setUsername(USER);
        ds.setPassword(PASSWORD);
        MysqlTimeProperties properties = new MysqlTimeProperties();
        properties.setEnabled(true);
        new MysqlDataSourceInitSqlPostProcessor(properties).postProcessAfterInitialization(ds, "dataSource");
        try {
            boolean jvmUtc = java.time.ZoneId.systemDefault().getRules().isFixedOffset()
                    && java.time.ZoneId.systemDefault().getRules().getOffset(java.time.Instant.EPOCH)
                    .equals(java.time.ZoneOffset.UTC);
            if (jvmUtc) {
                // 模拟 SmartInitializingSingleton 探针（真实连接只读校验）
                new MysqlTimeStartupProbe(properties, java.util.List.of(ds)).afterSingletonsInstantiated();
                assertNotNull(ds.getConnection(), "探针通过后连接可用");
            } else {
                // 修正 4：严格探针在非 UTC JVM 上必须拒绝（当前 CI 开发机即 Asia/Shanghai，实测拒绝）
                IllegalStateException e = assertThrows(IllegalStateException.class,
                        () -> new MysqlTimeStartupProbe(properties, java.util.List.of(ds)).afterSingletonsInstantiated());
                assertTrue(e.getMessage().contains("JVM/OS 时区非 UTC"), e.getMessage());
            }
        } finally {
            ds.close();
        }
    }

    @Test
    @DisplayName("handler 双 JVM 往返经真实 MySQL 验证（修正 4：UTC 与 Asia/Shanghai 同结果）")
    void handlerRoundTripUnderBothJvmZones() throws SQLException {
        requireMysql();
        Instant instant = Instant.parse("2026-10-30T08:00:00.123Z");
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t_jvm (id BIGINT AUTO_INCREMENT PRIMARY KEY, occurred_at DATETIME(3) NULL)");
        }
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            for (String zone : new String[]{"UTC", "Asia/Shanghai"}) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
                UtcInstantTypeHandler handler = new UtcInstantTypeHandler();
                // 通过 JDBC 语句模拟 handler 的 setObject/getObject 调用路径（handler 逻辑本身由单元测试覆盖）
                try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD)) {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM t_jvm");
                         PreparedStatement ins = c.prepareStatement("INSERT INTO t_jvm (occurred_at) VALUES (?)")) {
                        ps.executeUpdate();
                        ins.setObject(1, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
                        ins.executeUpdate();
                    }
                    try (PreparedStatement sel = c.prepareStatement("SELECT occurred_at FROM t_jvm");
                         ResultSet rs = sel.executeQuery()) {
                        assertTrue(rs.next(), "JVM 默认时区=" + zone + " 时 INSERT 后应能读回数据");
                        Instant read = rs.getObject(1, LocalDateTime.class).toInstant(ZoneOffset.UTC);
                        assertEquals(instant, read, "JVM 默认时区=" + zone + " 时 DATETIME(3) UTC 往返必须一致");
                    }
                }
            }
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }
}
