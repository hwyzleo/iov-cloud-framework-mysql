package net.hwyz.iov.cloud.framework.mysql.validation;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * FW-MYSQL Mapper 语句完整性校验自动配置（FW-MYSQL-DSN-CR-003，§5.3）。
 * <p>
 * 仅当容器存在 {@link SqlSessionFactory} 时装配；默认 {@code mode=off} 保持老应用启动兼容，
 * VIAM 与新服务模板显式配置 {@code fail}。校验在 Mapper 注册完成后、业务流量前运行，
 * 多 SqlSessionFactory 分别校验，不访问/不写入业务表。
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(MapperValidationProperties.class)
@ConditionalOnBean(SqlSessionFactory.class)
public class MapperValidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MapperStatementValidator mapperStatementValidator(MapperValidationProperties properties,
                                                             List<SqlSessionFactory> sqlSessionFactories) {
        return new MapperStatementValidator(properties, sqlSessionFactories);
    }

    @Bean
    @ConditionalOnMissingBean
    public LegacyMapperStatementValidator legacyMapperStatementValidator(MapperValidationProperties properties,
                                                                         List<SqlSessionFactory> sqlSessionFactories) {
        return new LegacyMapperStatementValidator(properties, sqlSessionFactories);
    }
}
