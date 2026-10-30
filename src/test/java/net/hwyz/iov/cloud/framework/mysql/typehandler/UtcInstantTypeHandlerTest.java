package net.hwyz.iov.cloud.framework.mysql.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UtcInstantTypeHandler} 单元测试（FW-MYSQL-DSN-CR-001，TC-01/TC-02 单元部分）。
 * <p>
 * 断言：显式 UTC 映射不依赖 JVM 默认时区——在 UTC 与 Asia/Shanghai 两种 JVM 默认时区下
 * 写读结果一致（修正 4：handler 往返两种 JVM 同结果）。
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
class UtcInstantTypeHandlerTest {

    private final UtcInstantTypeHandler handler = new UtcInstantTypeHandler();
    private TimeZone original;

    @BeforeEach
    void saveDefaultZone() {
        original = TimeZone.getDefault();
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(original);
    }

    @Test
    @DisplayName("写：两种 JVM 时区下绑定同一 UTC 墙钟")
    void writeUsesUtcWallClockRegardlessOfJvmDefaultZone() throws Exception {
        Instant instant = Instant.parse("2026-10-30T08:00:00.123Z");
        runInZone("UTC", () -> verifyWrite(instant));
        runInZone("Asia/Shanghai", () -> verifyWrite(instant));
    }

    @Test
    @DisplayName("读：列墙钟按 UTC 还原 Instant，两种 JVM 时区同结果")
    void readInterpretsColumnAsUtcWallClockRegardlessOfJvmDefaultZone() throws Exception {
        LocalDateTime wallClock = LocalDateTime.of(2026, 10, 30, 8, 0, 0, 500_000_000);
        Instant expected = Instant.parse("2026-10-30T08:00:00.500Z");
        runInZone("UTC", () -> verifyRead(wallClock, expected));
        runInZone("Asia/Shanghai", () -> verifyRead(wallClock, expected));
    }

    @Test
    @DisplayName("纳秒精度原样保留（不截断），精度由 DB 列定义决定")
    void nanosPreservedOnWriteAndRead() throws Exception {
        Instant instant = Instant.parse("2026-10-30T08:00:00.123456789Z");
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, instant, JdbcType.TIMESTAMP);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(ps).setObject(eq(1), captor.capture(), eq(Types.TIMESTAMP));
        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 0, 0, 123_456_789), captor.getValue());

        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("occurred_at", LocalDateTime.class))
                .thenReturn(LocalDateTime.of(2026, 10, 30, 8, 0, 0, 123_456_789));
        assertEquals(instant, handler.getNullableResult(rs, "occurred_at"));
    }

    @Test
    @DisplayName("null 原样返回 null，不转换为 epoch 0")
    void nullReturnsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("occurred_at", LocalDateTime.class)).thenReturn(null);
        assertNull(handler.getNullableResult(rs, "occurred_at"));
    }

    @Test
    @DisplayName("负 epoch 与 epoch 边界支持")
    void negativeEpochAndBoundarySupported() throws Exception {
        Instant preEpoch = Instant.parse("1969-12-31T23:59:59.999Z");
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, preEpoch, JdbcType.TIMESTAMP);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(ps).setObject(eq(1), captor.capture(), eq(Types.TIMESTAMP));
        assertEquals(LocalDateTime.of(1969, 12, 31, 23, 59, 59, 999_000_000), captor.getValue());

        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(1, LocalDateTime.class)).thenReturn(LocalDateTime.of(1970, 1, 1, 0, 0, 0));
        assertEquals(Instant.EPOCH, handler.getNullableResult(rs, 1));
    }

    @Test
    @DisplayName("CallableStatement 读路径同样按 UTC 还原")
    void callableStatementReadPath() throws Exception {
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getObject(2, LocalDateTime.class)).thenReturn(LocalDateTime.of(2026, 10, 30, 8, 0, 0));
        assertEquals(Instant.parse("2026-10-30T08:00:00Z"), handler.getNullableResult(cs, 2));
    }

    private void verifyWrite(Instant instant) throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, instant, JdbcType.TIMESTAMP);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(ps).setObject(eq(1), captor.capture(), eq(Types.TIMESTAMP));
        LocalDateTime value = (LocalDateTime) captor.getValue();
        assertEquals(LocalDateTime.ofInstant(instant, ZoneOffset.UTC), value, "写入必须为 UTC 墙钟");
        assertEquals(instant, value.toInstant(ZoneOffset.UTC), "UTC 墙钟还原后必须等于原 Instant");
    }

    private void verifyRead(LocalDateTime wallClock, Instant expected) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("occurred_at", LocalDateTime.class)).thenReturn(wallClock);
        assertEquals(expected, handler.getNullableResult(rs, "occurred_at"));
    }

    private void runInZone(String zone, ThrowingRunnable action) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        action.run();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
