package net.hwyz.iov.cloud.framework.mysql.validation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FW-MYSQL-DSN-CR-003：MapperValidationAutoConfiguration 装配测试（§5.3）。
 * <p>
 * 断言：存在 SqlSessionFactory 时装配两个校验器（默认 off 不阻断）；无 SqlSessionFactory
 * 时自动配置不激活；fail 模式且存在缺语句 Mapper 时上下文启动失败（早失败）。
 * </p>
 */
class MapperValidationAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MapperValidationAutoConfiguration.class));

    @Test
    @DisplayName("存在 SqlSessionFactory 且默认 off：装配校验器且不阻断启动")
    void activatedWithSqlSessionFactoryDefaultOff() {
        contextRunner
                .withUserConfiguration(SqlSessionFactoryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MapperStatementValidator.class);
                    assertThat(context).hasSingleBean(LegacyMapperStatementValidator.class);
                    assertThat(context).hasSingleBean(MapperValidationProperties.class);
                });
    }

    @Test
    @DisplayName("无 SqlSessionFactory：自动配置不激活（不影响无 MP 的服务）")
    void notActivatedWithoutSqlSessionFactory() {
        contextRunner
                .withPropertyValues("iov.mysql.mapper-validation.mode=fail")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MapperStatementValidator.class);
                    assertThat(context).doesNotHaveBean(LegacyMapperStatementValidator.class);
                });
    }

    @Test
    @DisplayName("fail 模式且存在缺语句的 legacy Mapper：上下文启动失败（早失败）")
    void failModeWithMissingStatementsFailsStartup() {
        contextRunner
                .withUserConfiguration(SqlSessionFactoryConfig.class)
                .withPropertyValues("iov.mysql.mapper-validation.mode=fail")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("warn 模式且存在缺语句：上下文正常启动（仅告警）")
    void warnModeWithMissingStatementsStarts() {
        contextRunner
                .withUserConfiguration(SqlSessionFactoryConfig.class)
                .withPropertyValues("iov.mysql.mapper-validation.mode=warn")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class SqlSessionFactoryConfig {

        @Bean
        SqlSessionFactory sqlSessionFactory() {
            // 缺语句的 legacy Mapper（事故根因场景），用于 fail/warn 路径
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.addMapper(MapperStatementValidatorTest.SampleLegacyMapper.class);
            return new SqlSessionFactoryBuilder().build(configuration);
        }
    }
}
