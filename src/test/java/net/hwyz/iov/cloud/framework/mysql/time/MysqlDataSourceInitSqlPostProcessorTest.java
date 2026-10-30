package net.hwyz.iov.cloud.framework.mysql.time;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MysqlDataSourceInitSqlPostProcessor} 单元测试（FW-MYSQL-DSN-CR-001，TC-05 单元部分）。
 * <p>
 * 断言：仅 enabled 时合并；<b>保留既有初始化 SQL 追加</b>（不覆盖）；已含则不重复；
 * 非 Druid bean 不受影响。合并时机在 bean 创建流程内（postProcessAfterInitialization），
 * 早于首个物理连接与 Flyway（顺序证据见集成测试）。
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
class MysqlDataSourceInitSqlPostProcessorTest {

    @Test
    @DisplayName("enabled=false 不修改任何数据源（兼容阶段）")
    void disabledDoesNotModify() {
        DruidDataSource ds = new DruidDataSource();
        ds.setConnectionInitSqls(List.of("SET NAMES utf8mb4"));
        Object result = new MysqlDataSourceInitSqlPostProcessor(properties(false))
                .postProcessAfterInitialization(ds, "dataSource");
        assertEquals(ds, result);
        assertEquals(List.of("SET NAMES utf8mb4"), new ArrayList<>(ds.getConnectionInitSqls()));
    }

    @Test
    @DisplayName("enabled=true 保留既有 SQL 并追加会话 UTC 初始化")
    void enabledAppendsUtcInitSqlPreservingExisting() {
        DruidDataSource ds = new DruidDataSource();
        ds.setConnectionInitSqls(List.of("SET NAMES utf8mb4", "SET autocommit=1"));
        new MysqlDataSourceInitSqlPostProcessor(properties(true))
                .postProcessAfterInitialization(ds, "dataSource");
        List<String> initSqls = new ArrayList<>(ds.getConnectionInitSqls());
        assertEquals(3, initSqls.size());
        assertEquals("SET NAMES utf8mb4", initSqls.get(0));
        assertEquals("SET autocommit=1", initSqls.get(1));
        assertEquals(MysqlDataSourceInitSqlPostProcessor.SESSION_UTC_INIT_SQL, initSqls.get(2));
    }

    @Test
    @DisplayName("enabled=true 无既有初始化 SQL 时仅含会话 UTC 初始化")
    void enabledWithNoExistingInitSql() {
        DruidDataSource ds = new DruidDataSource();
        new MysqlDataSourceInitSqlPostProcessor(properties(true))
                .postProcessAfterInitialization(ds, "dataSource");
        List<String> initSqls = new ArrayList<>(ds.getConnectionInitSqls());
        assertEquals(1, initSqls.size());
        assertEquals(MysqlDataSourceInitSqlPostProcessor.SESSION_UTC_INIT_SQL, initSqls.get(0));
    }

    @Test
    @DisplayName("enabled=true 已含会话 UTC 初始化时不重复追加")
    void enabledDoesNotDuplicate() {
        DruidDataSource ds = new DruidDataSource();
        ds.setConnectionInitSqls(List.of(MysqlDataSourceInitSqlPostProcessor.SESSION_UTC_INIT_SQL));
        new MysqlDataSourceInitSqlPostProcessor(properties(true))
                .postProcessAfterInitialization(ds, "dataSource");
        List<String> initSqls = new ArrayList<>(ds.getConnectionInitSqls());
        assertEquals(1, initSqls.size());
        assertTrue(initSqls.contains(MysqlDataSourceInitSqlPostProcessor.SESSION_UTC_INIT_SQL));
    }

    @Test
    @DisplayName("非 Druid bean 原样返回，不受影响")
    void nonDruidBeanUntouched() {
        Object bean = new Object();
        Object result = new MysqlDataSourceInitSqlPostProcessor(properties(true))
                .postProcessAfterInitialization(bean, "any");
        assertEquals(bean, result);
    }

    @Test
    @DisplayName("DruidDataSource.getConnectionInitSqls() 为空集合时正常处理")
    void emptyInitSqlsHandled() {
        DruidDataSource ds = new DruidDataSource();
        ds.setConnectionInitSqls(new ArrayList<>());
        new MysqlDataSourceInitSqlPostProcessor(properties(true))
                .postProcessAfterInitialization(ds, "dataSource");
        List<String> initSqls = new ArrayList<>(ds.getConnectionInitSqls());
        assertFalse(initSqls.isEmpty());
        assertEquals(MysqlDataSourceInitSqlPostProcessor.SESSION_UTC_INIT_SQL, initSqls.get(0));
    }

    private static MysqlTimeProperties properties(boolean enabled) {
        MysqlTimeProperties properties = new MysqlTimeProperties();
        properties.setEnabled(enabled);
        return properties;
    }
}
