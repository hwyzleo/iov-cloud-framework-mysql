package net.hwyz.iov.cloud.framework.mysql.time;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MysqlTimeAutoConfiguration} 装配测试（FW-MYSQL-DSN-CR-001，TC-05 自动配置部分）。
 * <p>
 * 断言：存在 DataSource 时自动配置装配（探针、初始化合并器 bean 存在）；enabled=false 时
 * 探针运行不校验（兼容）。无 DataSource 时自动配置不激活（无 MySQL 依赖的服务不受影响）。
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
class MysqlTimeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MysqlTimeAutoConfiguration.class));

    @Test
    @DisplayName("存在 DataSource 时装配探针与初始化合并器")
    void registersBeansWhenDataSourcePresent() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .withPropertyValues("iov.mysql.time.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(MysqlTimeStartupProbe.class);
                    assertThat(context).hasSingleBean(MysqlDataSourceInitSqlPostProcessor.class);
                    assertThat(context).hasSingleBean(MysqlTimeProperties.class);
                });
    }

    @Test
    @DisplayName("无 DataSource 时自动配置不激活（不影响无 MySQL 依赖的服务）")
    void notActivatedWithoutDataSource() {
        contextRunner
                .withPropertyValues("iov.mysql.time.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MysqlTimeStartupProbe.class);
                    assertThat(context).doesNotHaveBean(MysqlDataSourceInitSqlPostProcessor.class);
                });
    }

    @Test
    @DisplayName("enabled=true 且数据源未配置（URL 缺失/非 UTC JVM）时上下文启动失败（fail-fast）")
    void enabledTrueWithUnconfiguredDruidFailsStartup() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .withPropertyValues("iov.mysql.time.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class DataSourceConfig {

        @Bean
        DruidDataSource dataSource() {
            // 仅验证装配与探针逻辑；enabled=false 或探针判定失败路径由专门测试覆盖
            return new DruidDataSource();
        }
    }
}
