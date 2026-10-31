package net.hwyz.iov.cloud.framework.mysql.boot3;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FW-MYSQL-DSN-CR-002 T-01：Boot 3.0.2 下官方 druid-spring-boot-3-starter 自动装配验证。
 * <p>
 * 目标：证明替换 starter 后，Spring Boot 3 上下文按 {@code spring.datasource.druid.*}
 * 配置实际创建 {@link DruidDataSource} Bean（不是回退 Hikari），URL、代表性池参数与
 * connectionInitSqls 均实际绑定。仅核验 Bean 装配与配置绑定，不建立真实连接
 * （initial-size=0，Druid init() 不触发物理连接），因此 URL 可指向本地集成 MySQL。
 * <p>
 * 凭据通过环境变量注入（IT_MYSQL_URL/IT_MYSQL_USER/IT_MYSQL_PASSWORD），不写入源码或日志；
 * 本测试不打印完整连接 URL。
 * <p>
 * 依赖说明：本测试同时验证框架自身自动配置（探针/初始化合并器）与 Druid starter 共存，
 * 探针通过 {@code iov.mysql.time.enabled=false} 关闭校验，专注 Druid 自动装配本身。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
@SpringBootTest(
        classes = DruidBoot3AutoConfigurationTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 探针不参与本测试：仅验证 Druid 自动装配与配置绑定（探针行为由专用测试覆盖）
                "iov.mysql.time.enabled=false",
                // Flyway 不参与本测试：否则启动时 Flyway 会借 Druid 池连接执行迁移，
                // 池空触发急切建连，破坏本测试的 Bean 级封闭性（Flyway UTC 由集成测试单独验证）
                "spring.flyway.enabled=false",
                // 与消费服务（iov-cloud-iov-tsp）相同的 spring.datasource.druid.* 配置形态；
                // URL 为固定本地地址（不含凭据/敏感信息），不使用 IT_MYSQL_URL（该变量被集成测试指向根库，会覆盖断言）
                "spring.datasource.druid.url=jdbc:mysql://127.0.0.1:13306/fw_mysql_boot3_it?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true",
                "spring.datasource.druid.username=${IT_MYSQL_USER:root}",
                "spring.datasource.druid.password=${IT_MYSQL_PASSWORD:}",
                "spring.datasource.druid.initial-size=0",
                "spring.datasource.druid.min-idle=0",
                // keep-alive=false：Druid 1.2.x 默认 keepAlive=true 会在 init() 时 emptySignal 触发急切建连，
                // 关闭后本测试保持封闭（不依赖真实 MySQL/凭据）；急切建连与会话 UTC 由集成测试 T-02 验证
                "spring.datasource.druid.keep-alive=false",
                "spring.datasource.druid.max-active=8",
                "spring.datasource.druid.connection-init-sqls[0]=SET time_zone='+00:00'"
        })
class DruidBoot3AutoConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("实际数据源必须是 Druid（不得静默回退 Hikari 或其他池）")
    void actualDataSourceIsDruidNotHikari() {
        assertNotNull(dataSource, "容器中必须存在 DataSource Bean");
        assertTrue(dataSource instanceof DruidDataSource,
                "实际数据源类型=" + dataSource.getClass().getName() + "，必须是 DruidDataSource（禁止静默回退 Hikari，AC-01）");
    }

    @Test
    @DisplayName("URL 实际绑定且保留 UTC 参数")
    void urlBoundWithUtcParams() {
        DruidDataSource ds = (DruidDataSource) dataSource;
        String url = ds.getUrl();
        assertNotNull(url, "DruidDataSource.getUrl() 不得为空");
        assertTrue(url.startsWith("jdbc:mysql://"), "URL 前缀异常");
        assertTrue(url.contains("connectionTimeZone=UTC"), "URL 必须含 connectionTimeZone=UTC");
        assertTrue(url.contains("forceConnectionTimeZoneToSession=true"), "URL 必须含 forceConnectionTimeZoneToSession=true");
        assertTrue(url.contains("preserveInstants=true"), "URL 必须含 preserveInstants=true");
    }

    @Test
    @DisplayName("代表性池参数实际绑定（AC-01：应用已声明的连接池配置）")
    void poolParamsBound() {
        DruidDataSource ds = (DruidDataSource) dataSource;
        assertEquals(8, ds.getMaxActive(), "max-active 必须绑定为 8");
        assertEquals(0, ds.getInitialSize(), "initial-size 必须绑定为 0（本测试不触发物理连接）");
    }

    @Test
    @DisplayName("connectionInitSqls 实际绑定（SET time_zone='+00:00'，UTC 会话初始化链路）")
    void connectionInitSqlsBound() {
        DruidDataSource ds = (DruidDataSource) dataSource;
        Collection<String> initSqls = ds.getConnectionInitSqls();
        assertNotNull(initSqls, "getConnectionInitSqls() 不得为 null");
        assertTrue(initSqls.contains("SET time_zone='+00:00'"),
                "connectionInitSqls 必须包含 SET time_zone='+00:00'，实际=" + initSqls);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
    }
}
