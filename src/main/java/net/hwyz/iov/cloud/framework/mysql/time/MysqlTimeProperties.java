package net.hwyz.iov.cloud.framework.mysql.time;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FW-MYSQL 时间治理配置（FW-MYSQL-DSN-CR-001，RD-002/RD-003）。
 * <p>
 * 仅一个开关 {@code enabled}：默认 false 表示<b>尚未接入治理</b>（兼容阶段，不影响既有服务）；
 * 置为 true 后执行<b>完整严格 UTC 策略</b>，不允许以任何子开关绕过生产门禁：
 * <ul>
 *   <li>JVM/OS 时区必须 UTC 等价（不提供 strict-utc-jvm 关闭项）；</li>
 *   <li>真实物理连接会话必须 UTC（Druid connectionInitSqls 合并 {@code SET time_zone='+00:00'}）；</li>
 *   <li>datasource URL 必须含 {@code connectionTimeZone=UTC}、{@code forceConnectionTimeZoneToSession=true}、
 *       {@code preserveInstants=true}，且无重复/冲突参数（不提供 require-url-utc-params 关闭项）；</li>
 *   <li>启动探针对所有实际 DataSource 校验真实连接行为，失败 fail-fast 阻断启动。</li>
 * </ul>
 * 配置为静态版本化；仅在服务完成旧链路 UTC 兼容核验并显式启用后生效。
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
@Data
@ConfigurationProperties(prefix = "iov.mysql.time")
public class MysqlTimeProperties {

    /**
     * 是否启用完整严格 UTC 治理（默认 false = 未接入治理，仅兼容）。
     * 置为 true 后 JVM、真实会话、驱动参数与数据源覆盖校验全部强制执行，任一不满足即启动失败。
     */
    private boolean enabled = false;
}
