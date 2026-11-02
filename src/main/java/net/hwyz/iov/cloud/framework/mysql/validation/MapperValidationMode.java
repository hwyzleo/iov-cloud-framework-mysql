package net.hwyz.iov.cloud.framework.mysql.validation;

/**
 * Mapper 语句完整性校验模式（FW-MYSQL-DSN-CR-003，§5.3）。
 */
public enum MapperValidationMode {

    /** 不校验（默认，保持老应用启动兼容）。 */
    OFF,
    /** 发现问题仅告警，不阻断启动。 */
    WARN,
    /** 发现问题抛异常阻断启动（VIAM 与新服务模板显式启用）。 */
    FAIL
}
