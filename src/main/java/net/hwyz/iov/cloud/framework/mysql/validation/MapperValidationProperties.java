package net.hwyz.iov.cloud.framework.mysql.validation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapper 语句完整性校验配置（FW-MYSQL-DSN-CR-003，§5.3）。
 * <p>配置项 {@code iov.mysql.mapper-validation.mode=off|warn|fail}，默认 {@code off}
 * 保持老应用启动兼容；VIAM 与新服务模板显式配置 {@code fail}。</p>
 */
@ConfigurationProperties(prefix = "iov.mysql.mapper-validation")
public class MapperValidationProperties {

    private MapperValidationMode mode = MapperValidationMode.OFF;

    public MapperValidationMode getMode() {
    return mode;
    }

    public void setMode(MapperValidationMode mode) {
        this.mode = mode;
    }
}
