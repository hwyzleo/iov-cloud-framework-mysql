package net.hwyz.iov.cloud.framework.mysql.time;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.List;

/**
 * FW-MYSQL 时间治理自动配置（FW-MYSQL-DSN-CR-001，RD-002/RD-003）。
 * <p>
 * 职责：
 * <ul>
 *   <li>注册 {@link MysqlTimeProperties}（{@code iov.mysql.time.enabled}，默认 false = 未接入治理）；</li>
 *   <li>{@link MysqlDataSourceInitSqlPostProcessor}：Druid 会话 UTC 初始化 SQL 合并，
 *       在 bean 创建流程内执行，早于首个物理连接与 Flyway 迁移（修正：初始化先于连接，探针只校验）；</li>
 *   <li>{@link MysqlTimeStartupProbe}：就绪前校验 JVM UTC、URL 参数、会话初始化与真实连接行为，
 *       失败 fail-fast 阻断启动。</li>
 * </ul>
 * 仅在容器中存在 {@link DataSource} 时装配（无 MySQL 依赖的服务不受影响）；
 * 所有校验仅作用于已登记的数据源链路，不扩大治理面（RD-010）。
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
@AutoConfiguration
@EnableConfigurationProperties(MysqlTimeProperties.class)
@ConditionalOnBean(DataSource.class)
public class MysqlTimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MysqlDataSourceInitSqlPostProcessor mysqlDataSourceInitSqlPostProcessor(MysqlTimeProperties properties) {
        return new MysqlDataSourceInitSqlPostProcessor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MysqlTimeStartupProbe mysqlTimeStartupProbe(MysqlTimeProperties properties,
                                                       List<DataSource> dataSources) {
        return new MysqlTimeStartupProbe(properties, dataSources);
    }
}
