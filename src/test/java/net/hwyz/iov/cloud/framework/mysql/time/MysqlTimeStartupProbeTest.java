package net.hwyz.iov.cloud.framework.mysql.time;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MysqlTimeStartupProbe} 单元测试（FW-MYSQL-DSN-CR-001，TC-01/TC-03/TC-05 单元部分）。
 * <p>
 * 失败路径覆盖（修正 5）：非 UTC JVM 拒绝、URL 参数缺失拒绝、初始化 SQL 缺失拒绝、
 * 非 UTC 会话拒绝、NOW(3) 与 UTC_TIMESTAMP(3) 不一致拒绝、非 Druid 数据源拒绝、无数据源拒绝。
 * 修正 4：严格探针在 Asia/Shanghai JVM 下<b>拒绝</b>，不是两种 JVM 都启动成功。
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
class MysqlTimeStartupProbeTest {

    private static final String VALID_URL =
            "jdbc:mysql://127.0.0.1:3306/db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true";

    private TimeZone original;

    @BeforeEach
    void saveDefaultZone() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(original);
    }

    @Test
    @DisplayName("enabled=false 不校验（兼容阶段，未接入治理）")
    void disabledDoesNotValidate() {
        DruidDataSource ds = mock(DruidDataSource.class);
        MysqlTimeProperties properties = properties(false);
        assertDoesNotThrow(() -> new MysqlTimeStartupProbe(properties, List.of(ds)).afterSingletonsInstantiated());
    }

    @Test
    @DisplayName("Asia/Shanghai JVM 严格拒绝（不是两种 JVM 都启动成功）")
    void nonUtcJvmRejected() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        DruidDataSource ds = mock(DruidDataSource.class);
        when(ds.getUrl()).thenReturn(VALID_URL);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new MysqlTimeStartupProbe(properties(true), List.of(ds)).afterSingletonsInstantiated());
        assertTrue(e.getMessage().contains("JVM/OS 时区非 UTC"), e.getMessage());
    }

    @Test
    @DisplayName("全部满足时通过（JVM UTC、URL 参数、初始化 SQL、真实会话 UTC）")
    void fullyCompliantPasses() throws Exception {
        DruidDataSource ds = compliantDruid("+00:00", "2026-10-30 08:00:00.123", "2026-10-30 08:00:00.123");
        assertDoesNotThrow(() -> new MysqlTimeStartupProbe(properties(true), List.of(ds)).afterSingletonsInstantiated());
    }

    @Test
    @DisplayName("URL 缺 UTC 参数拒绝")
    void urlMissingParamsRejected() {
        DruidDataSource ds = mock(DruidDataSource.class);
        when(ds.getUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/db");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new MysqlTimeStartupProbe(properties(true), List.of(ds)).afterSingletonsInstantiated());
        assertTrue(e.getMessage().contains("UTC"), e.getMessage());
    }

    @Test
    @DisplayName("connectionInitSqls 缺会话 UTC 初始化拒绝")
    void missingInitSqlRejected() throws Exception {
        DruidDataSource ds = mock(DruidDataSource.class);
        when(ds.getUrl()).thenReturn(VALID_URL);
        when(ds.getConnectionInitSqls()).thenReturn(List.of("SET NAMES utf8mb4"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new MysqlTimeStartupProbe(properties(true), List.of(ds)).afterSingletonsInstantiated());
        assertTrue(e.getMessage().contains("SET time_zone"), e.getMessage());
    }

    @Test
    @DisplayName("真实会话非 UTC（+08:00）拒绝")
    void nonUtcSessionRejected() throws Exception {
        DruidDataSource ds = compliantDruid("+08:00", "2026-10-30 16:00:00.123", "2026-10-30 08:00:00.123");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new MysqlTimeStartupProbe(properties(true), List.of(ds)).afterSingletonsInstantiated());
        assertTrue(e.getMessage().contains("会话时区非 UTC"), e.getMessage());
    }

    @Test
    @DisplayName("NOW(3) 与 UTC_TIMESTAMP(3) 不一致拒绝")
    void nowAndUtcTimestampMismatchRejected() throws Exception {
        DruidDataSource ds = compliantDruid("+00:00", "2026-10-30 08:00:01.000", "2026-10-30 08:00:00.000");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new MysqlTimeStartupProbe(properties(true), List.of(ds)).afterSingletonsInstantiated());
        assertTrue(e.getMessage().contains("NOW(3)"), e.getMessage());
    }

    @Test
    @DisplayName("非 Druid 数据源拒绝（无法应用会话初始化，禁止静默跳过）")
    void nonDruidDataSourceRejected() {
        DataSource plain = mock(DataSource.class);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new MysqlTimeStartupProbe(properties(true), List.of(plain)).afterSingletonsInstantiated());
        assertTrue(e.getMessage().contains("非 Druid"), e.getMessage());
    }

    @Test
    @DisplayName("enabled=true 但无任何 DataSource 拒绝（无法证明覆盖）")
    void noDataSourceRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new MysqlTimeStartupProbe(properties(true), List.of()).afterSingletonsInstantiated());
        assertTrue(e.getMessage().contains("未发现任何 DataSource"), e.getMessage());
    }

    @Test
    @DisplayName("会话探测查询无结果拒绝")
    void emptySessionResultRejected() throws Exception {
        DruidDataSource ds = mock(DruidDataSource.class);
        when(ds.getUrl()).thenReturn(VALID_URL);
        when(ds.getConnectionInitSqls()).thenReturn(List.of("SET time_zone='+00:00'"));
        DruidPooledConnection connection = mock(DruidPooledConnection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(ps.executeQuery()).thenReturn(rs);
        when(connection.prepareStatement(MysqlTimeStartupProbe.SESSION_PROBE_SQL)).thenReturn(ps);
        when(ds.getConnection()).thenReturn(connection);
        assertThrows(IllegalStateException.class,
                () -> new MysqlTimeStartupProbe(properties(true), List.of(ds)).afterSingletonsInstantiated());
    }

    private static DruidDataSource compliantDruid(String sessionTz, String now3, String utc3) throws Exception {
        DruidDataSource ds = mock(DruidDataSource.class);
        when(ds.getUrl()).thenReturn(VALID_URL);
        when(ds.getConnectionInitSqls()).thenReturn(List.of("SET time_zone='+00:00'"));
        DruidPooledConnection connection = mock(DruidPooledConnection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getString("session_tz")).thenReturn(sessionTz);
        when(rs.getTimestamp("now3")).thenReturn(Timestamp.valueOf(now3));
        when(rs.getTimestamp("utc3")).thenReturn(Timestamp.valueOf(utc3));
        when(ps.executeQuery()).thenReturn(rs);
        when(connection.prepareStatement(MysqlTimeStartupProbe.SESSION_PROBE_SQL)).thenReturn(ps);
        when(ds.getConnection()).thenReturn(connection);
        return ds;
    }

    private static MysqlTimeProperties properties(boolean enabled) {
        MysqlTimeProperties properties = new MysqlTimeProperties();
        properties.setEnabled(enabled);
        return properties;
    }
}
