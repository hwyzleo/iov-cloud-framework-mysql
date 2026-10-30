package net.hwyz.iov.cloud.framework.mysql.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MysqlUrlUtcValidator} 单元测试（FW-MYSQL-DSN-CR-001，TC-05 单元部分）。
 * <p>
 * URL 校验为<b>解析式</b>：非字符串包含判断；覆盖重复参数、冲突参数、缺失参数、非法时区值。
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
class MysqlUrlUtcValidatorTest {

    private static final String BASE = "jdbc:mysql://127.0.0.1:3306/db";

    @Test
    @DisplayName("合法 UTC URL 通过")
    void validUrlPasses() {
        assertDoesNotThrow(() -> MysqlUrlUtcValidator.validate(
                BASE + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true"));
    }

    @Test
    @DisplayName("值大小写不敏感")
    void caseInsensitiveValuesPass() {
        assertDoesNotThrow(() -> MysqlUrlUtcValidator.validate(
                BASE + "?connectionTimeZone=utc&forceConnectionTimeZoneToSession=TRUE&preserveInstants=true"));
    }

    @Test
    @DisplayName("兼容参数 serverTimezone=UTC 通过（connector/J 别名）")
    void serverTimezoneAliasPasses() {
        assertDoesNotThrow(() -> MysqlUrlUtcValidator.validate(
                BASE + "?serverTimezone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true"));
    }

    @Test
    @DisplayName("缺 connectionTimeZone 拒绝")
    void missingConnectionTimeZoneRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MysqlUrlUtcValidator.validate(BASE + "?forceConnectionTimeZoneToSession=true&preserveInstants=true"));
        assertTrue(e.getMessage().contains("UTC"), e.getMessage());
    }

    @Test
    @DisplayName("缺 forceConnectionTimeZoneToSession 拒绝")
    void missingForceSessionRejected() {
        assertThrows(IllegalArgumentException.class, () -> MysqlUrlUtcValidator.validate(
                BASE + "?connectionTimeZone=UTC&preserveInstants=true"));
    }

    @Test
    @DisplayName("缺 preserveInstants 拒绝")
    void missingPreserveInstantsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MysqlUrlUtcValidator.validate(
                BASE + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"));
    }

    @Test
    @DisplayName("非 UTC 时区值拒绝")
    void nonUtcTimezoneRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MysqlUrlUtcValidator.validate(
                BASE + "?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&preserveInstants=true"));
        assertTrue(e.getMessage().contains("UTC"), e.getMessage());
    }

    @Test
    @DisplayName("同一参数重复出现拒绝（连接属性不确定）")
    void duplicateParamRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MysqlUrlUtcValidator.validate(
                BASE + "?connectionTimeZone=UTC&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true"));
        assertTrue(e.getMessage().contains("重复"), e.getMessage());
    }

    @Test
    @DisplayName("connectionTimeZone 与 serverTimezone 值冲突拒绝")
    void conflictingTimezoneParamsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MysqlUrlUtcValidator.validate(
                BASE + "?connectionTimeZone=UTC&serverTimezone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&preserveInstants=true"));
        assertTrue(e.getMessage().contains("冲突"), e.getMessage());
    }

    @Test
    @DisplayName("无 query 部分拒绝")
    void urlWithoutQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> MysqlUrlUtcValidator.validate(BASE));
    }

    @Test
    @DisplayName("空 URL 拒绝")
    void blankUrlRejected() {
        assertThrows(IllegalArgumentException.class, () -> MysqlUrlUtcValidator.validate("  "));
        assertThrows(IllegalArgumentException.class, () -> MysqlUrlUtcValidator.validate(null));
    }

    @Test
    @DisplayName("URL 编码值 +00:00 解码后通过")
    void encodedPlusOffsetPasses() {
        assertDoesNotThrow(() -> MysqlUrlUtcValidator.validate(
                BASE + "?connectionTimeZone=%2B00%3A00&forceConnectionTimeZoneToSession=true&preserveInstants=true"));
    }

    @Test
    @DisplayName("非法编码不崩溃（保持原样按大小写不敏感比较）")
    void malformedEncodingDoesNotThrow() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MysqlUrlUtcValidator.validate(
                BASE + "?connectionTimeZone=%zz&forceConnectionTimeZoneToSession=true&preserveInstants=true"));
        assertTrue(e.getMessage().contains("UTC"), e.getMessage());
    }
}
