package net.hwyz.iov.cloud.framework.mysql.time;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Druid 数据源会话 UTC 初始化合并器（FW-MYSQL-DSN-CR-001，RD-002）。
 * <p>
 * 在 {@link DruidDataSource} bean 创建完成后立即合并会话 UTC 初始化 SQL
 * （{@code SET time_zone='+00:00'}），<b>保留既有 connectionInitSqls 追加</b>。
 * 时机保证：
 * <ul>
 *   <li>Druid 连接池为惰性初始化（首个 {@code getConnection} 才建立物理连接），
 *       {@code postProcessAfterInitialization} 在 bean 创建流程内立即执行，必然早于首个物理连接；</li>
 *   <li>Druid 的 {@code connectionInitSqls} 作用于每个新建/重连的物理连接，天然覆盖池借还后的
 *       新建与失败重连连接；</li>
 *   <li>Spring Boot 自动配置排序保证 DataSource 先于 Flyway 创建，Flyway 若复用主数据源，
 *       迁移连接同样经过初始化 SQL（该顺序由集成测试验证）。</li>
 * </ul>
 * 仅当 {@link MysqlTimeProperties#isEnabled()} 为 true 时执行；探针只负责<b>校验</b>，
 * 不负责在发现错误后补设时区。
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
public class MysqlDataSourceInitSqlPostProcessor implements BeanPostProcessor {

    /** 会话 UTC 初始化 SQL（幂等，重复执行无副作用）。 */
    public static final String SESSION_UTC_INIT_SQL = "SET time_zone='+00:00'";

    private final MysqlTimeProperties properties;

    public MysqlDataSourceInitSqlPostProcessor(MysqlTimeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!properties.isEnabled()) {
            return bean;
        }
        if (bean instanceof DruidDataSource druidDataSource) {
            Collection<String> initSqls = druidDataSource.getConnectionInitSqls();
            List<String> merged = initSqls == null ? new ArrayList<>() : new ArrayList<>(initSqls);
            if (!merged.contains(SESSION_UTC_INIT_SQL)) {
                merged.add(SESSION_UTC_INIT_SQL);
                druidDataSource.setConnectionInitSqls(merged);
            }
        }
        return bean;
    }
}
