package net.hwyz.iov.cloud.framework.mysql.crud;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import net.hwyz.iov.cloud.framework.mysql.config.MyBatisMetaObjectHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * FW-MYSQL-DSN-CR-003 原生持久化真实 MySQL 集成测试（IT-01/IT-02/IT-03、时间回归、RG-01/RG-02、桥接真实执行）。
 * <p>
 * 门禁与凭据约定同 {@code MysqlTimeMysqlIntegrationTest}：默认可跳过；激活
 * {@code mysql-integration} profile（{@code -Dmysql.integration.required=true}）后
 * MySQL 不可用/凭据缺失一律 FAIL。实际 MySQL 版本以执行时锁定并回填（本次 8.4.11）。
 * </p>
 * <p>
 * 覆盖（DSN §7 / §10.4）：
 * <ul>
 *   <li>IT-01：零 XML 原生 CRUD（insert/selectById/updateById/deleteById/selectList/selectCount），
 *       id 回填、影响行数、逻辑删除过滤、应用时间填充（本批例外）；</li>
 *   <li>IT-02：乐观锁并发（同一原版本并发两更新仅一成功、版本仅增一次、旧版本/空版本负例）；</li>
 *   <li>IT-03：逻辑删除有效值方向（1 有效/0 无效）、重复逻辑删除、受控物理删除；</li>
 *   <li>时间回归：应用填充保留 + 原生路径 UTC handler 往返（autoResultMap，无 XML resultMap）；</li>
 *   <li>RG-01：代表性存量 BaseDao 十方法 XML 在 P1 框架升级后全部正常（旧路径不回归）；</li>
 *   <li>RG-02：原生 BaseMapper 消费者共享 handler/插件兼容（同工厂同配置）；</li>
 *   <li>桥接（UT-01 真实执行）：四个 default 方法在真实 MySQL 上分派到原生语句。</li>
 * </ul>
 * </p>
 */
@Tag("mysql-integration")
class MapperCrudMysqlIntegrationTest {

    private static final String BASE_URL = System.getProperty("mysql.it.url",
            System.getenv().getOrDefault("IT_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/"));
    private static final String USER = System.getProperty("mysql.it.user",
            System.getenv().getOrDefault("IT_MYSQL_USER", "root"));
    private static final String PASSWORD = System.getProperty("mysql.it.password",
            System.getenv().getOrDefault("IT_MYSQL_PASSWORD", null));
    private static final boolean REQUIRED =
            Boolean.parseBoolean(System.getProperty("mysql.integration.required", "false"));

    private static final String SCHEMA = "fw_mysql_cr003_it_" + ThreadLocalRandom.current().nextInt(100000, 999999);
    private static final String PARAMS =
            "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"
                    + "&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true";

    private static boolean mysqlAvailable;
    private static SqlSessionFactory factory;
    private static DruidDataSource dataSource;

    @BeforeAll
    static void prepare() throws Exception {
        mysqlAvailable = tryConnect();
        if (!mysqlAvailable) {
            if (REQUIRED) {
                throw new IllegalStateException(
                        "强制 MySQL 集成测试（mysql.integration.required=true）但无法连接或凭据缺失：url="
                                + BASE_URL + ", user=" + USER);
            }
            return;
        }
        createSchema();
        createTable();
        buildFactory();
    }

    @AfterAll
    static void cleanup() {
        if (!mysqlAvailable) {
            return;
        }
        if (dataSource != null) {
            dataSource.close();
        }
        try (Connection admin = DriverManager.getConnection(BASE_URL + PARAMS, USER, PASSWORD);
             Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        } catch (SQLException ignored) {
            // 清理失败不影响结论
        }
    }

    private static boolean tryConnect() {
        if (PASSWORD == null || PASSWORD.isBlank()) {
            return false;
        }
        try (Connection c = DriverManager.getConnection(BASE_URL + PARAMS, USER, PASSWORD)) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static void requireMysql() {
        if (!mysqlAvailable) {
            if (REQUIRED) {
                throw new IllegalStateException("强制模式但 MySQL 不可用，测试 FAIL（不跳过）");
            }
            assumeTrue(false, "MySQL 不可用/凭据缺失，显式跳过（本地开发）；CI 需激活 mysql-integration profile");
        }
    }

    private static String schemaUrl() {
        return BASE_URL + SCHEMA + PARAMS;
    }

    private static void createSchema() throws SQLException {
        try (Connection admin = DriverManager.getConnection(BASE_URL + PARAMS, USER, PASSWORD);
             Statement st = admin.createStatement()) {
            st.execute("CREATE DATABASE " + SCHEMA);
        }
    }

    private static void createTable() throws SQLException {
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t_cr003_crud ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "code VARCHAR(64) NULL,"
                    + "payload VARCHAR(255) NULL,"
                    + "description VARCHAR(255) NULL,"
                    + "create_by VARCHAR(64) NULL,"
                    + "create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),"
                    + "modify_by VARCHAR(64) NULL,"
                    + "modify_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),"
                    + "row_version INT NOT NULL DEFAULT 1,"
                    + "row_valid TINYINT(1) NOT NULL DEFAULT 1,"
                    + "occurred_at DATETIME(3) NULL)");
        }
    }

    private static void buildFactory() throws Exception {
        dataSource = new DruidDataSource();
        dataSource.setUrl(schemaUrl());
        dataSource.setUsername(USER);
        dataSource.setPassword(PASSWORD);
        dataSource.setConnectionInitSqls(java.util.List.of("SET time_zone='+00:00'"));

        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(new MyBatisMetaObjectHandler());
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setConfiguration(configuration);
        bean.setGlobalConfig(globalConfig);
        bean.setPlugins(interceptor);
        bean.afterPropertiesSet();
        factory = bean.getObject();

        factory.getConfiguration().addMapper(CrudNativeMapper.class);
        factory.getConfiguration().addMapper(CrudBridgeMapper.class);
        factory.getConfiguration().addMapper(CrudLegacyMapper.class);
    }

    // ---------- 数据库辅助 ----------

    private static int rowVersionInDb(Long id) throws SQLException {
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement("SELECT row_version FROM t_cr003_crud WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static int rowValidInDb(Long id) throws SQLException {
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement("SELECT row_valid FROM t_cr003_crud WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static int physicalCountInDb(Long id) throws SQLException {
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM t_cr003_crud WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static boolean auditTimesPresentInDb(Long id) throws SQLException {
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT create_time, modify_time FROM t_cr003_crud WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getTimestamp(1) != null && rs.getTimestamp(2) != null;
            }
        }
    }

    // ---------- IT-01 原生 CRUD ----------

    @Test
    @DisplayName("IT-01：零 XML 原生 CRUD——insert/selectById/updateById/deleteById/查询过滤")
    void nativeCrudWithoutXml() throws SQLException {
        requireMysql();
        try (SqlSession session = factory.openSession()) {
            CrudNativeMapper mapper = session.getMapper(CrudNativeMapper.class);
            CrudPo po = new CrudPo();
            po.setCode("IT01-A");
            po.setPayload("p1");
            int inserted = mapper.insert(po);
            assertEquals(1, inserted, "insert 影响行数必须为 1");
            assertNotNull(po.getId(), "AUTO 主键必须回填");
            assertTrue(po.getRowValid(), "rowValid 默认 true");
            assertNotNull(po.getCreateTime(), "createTime 本批保留应用填充（BasePo fill=INSERT，DSN §10.2 本批例外）");
            assertTrue(auditTimesPresentInDb(po.getId()),
                    "modify_time 由 DB DEFAULT CURRENT_TIMESTAMP 提供（BasePo 无 fill 注解，handler 不填）；"
                            + "DB 生成审计时间需回读（DSN §6.4），内存 PO 不回读");

            CrudPo read = mapper.selectById(po.getId());
            assertEquals("IT01-A", read.getCode());
            assertEquals(Integer.valueOf(1), read.getRowVersion());

            CrudPo upd = new CrudPo();
            upd.setId(po.getId());
            upd.setPayload("p2");
            upd.setRowVersion(read.getRowVersion());
            assertEquals(1, mapper.updateById(upd), "updateById 影响行数必须为 1");
            assertEquals(Integer.valueOf(2), rowVersionInDb(po.getId()), "乐观锁更新版本仅增一次");

            List<CrudPo> list = mapper.selectList(
                    Wrappers.<CrudPo>lambdaQuery().eq(CrudPo::getCode, "IT01-A"));
            assertEquals(1, list.size());
            assertEquals("p2", list.get(0).getPayload());

            assertEquals(1, mapper.deleteById(po.getId()), "逻辑删除影响行数必须为 1");
            assertNull(mapper.selectById(po.getId()), "TableLogic 过滤：逻辑删除后 selectById 返回 null");
            assertEquals(0, mapper.selectCount(Wrappers.<CrudPo>lambdaQuery().eq(CrudPo::getCode, "IT01-A")),
                    "selectCount 必须排除逻辑删除行");
            assertEquals(0, rowValidInDb(po.getId()), "逻辑删除有效值方向：row_valid=0 表示无效");
        }
    }

    // ---------- IT-02 乐观锁 ----------

    @Test
    @DisplayName("IT-02：乐观锁——同一原版本并发两更新仅一个成功，版本仅增一次")
    void optimisticLockConcurrentUpdate() throws Exception {
        requireMysql();
        CrudPo po;
        try (SqlSession session = factory.openSession()) {
            CrudNativeMapper mapper = session.getMapper(CrudNativeMapper.class);
            po = new CrudPo();
            po.setCode("IT02-LOCK");
            po.setPayload("init");
            mapper.insert(po);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try (SqlSession session = factory.openSession()) {
                        CrudNativeMapper mapper = session.getMapper(CrudNativeMapper.class);
                        CrudPo current = mapper.selectById(po.getId()); // 都读到原版本 1
                        ready.countDown();
                        go.await();
                        CrudPo upd = new CrudPo();
                        upd.setId(current.getId());
                        upd.setPayload("winner-" + idx);
                        upd.setRowVersion(current.getRowVersion()); // 同一原版本
                        return mapper.updateById(upd);
                    }
                }));
            }
            ready.await();
            go.countDown();
            int success = 0;
            for (Future<Integer> f : futures) {
                success += f.get();
            }
            assertEquals(1, success, "同一原版本并发两更新必须恰好一个成功（CAS 冲突检测）");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(Integer.valueOf(2), rowVersionInDb(po.getId()), "成功更新仅使版本 +1");
    }

    @Test
    @DisplayName("IT-02 负例：旧版本更新 0 行；空版本在 3.5.3.1 下降级为无 CAS 更新（RD-03 行为记录）")
    void optimisticLockNegativeCases() {
        requireMysql();
        try (SqlSession session = factory.openSession()) {
            CrudNativeMapper mapper = session.getMapper(CrudNativeMapper.class);
            CrudPo po = new CrudPo();
            po.setCode("IT02-NEG");
            po.setPayload("v1");
            mapper.insert(po);

            // 旧版本（当前 1，用 1 更新成功 → 2）
            CrudPo first = new CrudPo();
            first.setId(po.getId());
            first.setPayload("v2");
            first.setRowVersion(1);
            assertEquals(1, mapper.updateById(first));
            // 再用旧版本 1 更新 → 0 行（版本冲突）
            CrudPo stale = new CrudPo();
            stale.setId(po.getId());
            stale.setPayload("v3");
            stale.setRowVersion(1);
            assertEquals(0, mapper.updateById(stale), "旧版本更新必须 0 行（CAS 拒绝）");

            // 空版本：3.5.3.1 插件对空版本直接返回（不抛异常、不加版本条件）→ 无 CAS 降级更新
            CrudPo empty = new CrudPo();
            empty.setId(po.getId());
            empty.setPayload("v4");
            empty.setRowVersion(null);
            assertEquals(1, mapper.updateById(empty),
                    "RD-03 行为记录：MP 3.5.3.1 空版本不触发 CAS（降级），新标准写入边界须自行拒绝缺失版本");
        }
    }

    // ---------- IT-03 逻辑/物理删除 ----------

    @Test
    @DisplayName("IT-03：逻辑删除值方向、重复逻辑删除、受控物理删除")
    void logicalAndPhysicalDelete() throws SQLException {
        requireMysql();
        try (SqlSession session = factory.openSession()) {
            CrudNativeMapper mapper = session.getMapper(CrudNativeMapper.class);
            CrudPo po = new CrudPo();
            po.setCode("IT03-DEL");
            mapper.insert(po);
            assertEquals(1, rowValidInDb(po.getId()), "插入后 row_valid=1（true 表示有效）");

            assertEquals(1, mapper.deleteById(po.getId()), "首次逻辑删除 1 行");
            assertEquals(0, rowValidInDb(po.getId()), "逻辑删除后 row_valid=0");
            assertEquals(0, mapper.deleteById(po.getId()), "重复逻辑删除 0 行（已删除）");

            // 受控物理删除（专用 SQL，非逻辑表的 deleteById 冒充）
            CrudPo purge = new CrudPo();
            purge.setCode("IT03-PURGE");
            mapper.insert(purge);
            assertEquals(1, mapper.purgeById(purge.getId()), "受控物理删除 1 行");
            assertEquals(0, physicalCountInDb(purge.getId()), "物理删除后表中无该行（含 row_valid=0 也不存在）");
        }
    }

    // ---------- 时间回归 ----------

    @Test
    @DisplayName("时间回归：应用填充保留；原生路径（无 XML resultMap）UTC handler 往返")
    void timeFillAndUtcRoundTrip() throws SQLException {
        requireMysql();
        try (SqlSession session = factory.openSession()) {
            CrudNativeMapper mapper = session.getMapper(CrudNativeMapper.class);
            CrudPo po = new CrudPo();
            po.setCode("IT-TIME");
            Instant instant = Instant.parse("2026-10-30T08:00:00.123Z");
            po.setOccurredAt(instant);
            mapper.insert(po);

            assertNotNull(po.getCreateTime(), "createTime 本批保留应用填充（BasePo fill=INSERT）");
            assertTrue(auditTimesPresentInDb(po.getId()),
                    "modify_time 由 DB DEFAULT CURRENT_TIMESTAMP 提供（BasePo 无 fill 注解）；DB 生成时间需回读");

            CrudPo read = mapper.selectById(po.getId());
            assertEquals(instant, read.getOccurredAt(),
                    "原生 selectById（autoResultMap + 字段级 handler，无 XML resultMap）必须还原同一 Instant");
        }
        // 库内 UTC 墙钟断言（handler 写语义：Instant → LocalDateTime(UTC)）
        try (Connection c = DriverManager.getConnection(schemaUrl(), USER, PASSWORD);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT occurred_at FROM t_cr003_crud WHERE code = 'IT-TIME' ORDER BY id DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            LocalDateTime wallClock = rs.getObject(1, LocalDateTime.class);
            assertEquals(LocalDateTime.of(2026, 10, 30, 8, 0, 0, 123_000_000), wallClock,
                    "DATETIME(3) 存储 UTC 墙钟 2026-10-30T08:00:00.123，会话 UTC 下读写一致");
        }
    }

    // ---------- 桥接真实执行（UT-01 落地） ----------

    @Test
    @DisplayName("桥接：insertPo/updatePo/selectPoById/logicalDeletePo 在真实 MySQL 分派到原生语句")
    void bridgeDispatchOnRealMysql() throws SQLException {
        requireMysql();
        try (SqlSession session = factory.openSession()) {
            CrudBridgeMapper bridge = session.getMapper(CrudBridgeMapper.class);
            CrudPo po = new CrudPo();
            po.setCode("BRIDGE-1");
            po.setPayload("bp");
            assertEquals(1, bridge.insertPo(po), "insertPo default 必须进入原生 insert");
            assertNotNull(po.getId());

            CrudPo read = bridge.selectPoById(po.getId());
            assertEquals("BRIDGE-1", read.getCode(), "selectPoById default 必须进入原生 selectById");

            CrudPo upd = new CrudPo();
            upd.setId(po.getId());
            upd.setPayload("bp2");
            upd.setRowVersion(read.getRowVersion());
            assertEquals(1, bridge.updatePo(upd), "updatePo default 必须进入原生 updateById");

            assertEquals(1, bridge.logicalDeletePo(po.getId()), "logicalDeletePo default 必须进入原生 deleteById（TableLogic）");
            assertNull(bridge.selectPoById(po.getId()), "逻辑删除后桥接查询不可见");
            assertEquals(0, rowValidInDb(po.getId()), "桥接逻辑删除同样置 row_valid=0");
        }
    }

    // ---------- RG-01 存量十方法 / RG-02 共享配置 ----------

    @Test
    @DisplayName("RG-01：代表性存量 BaseDao 十方法 XML 在 P1 框架升级后全部正常（旧路径不回归）")
    void legacyTrackTenMethodsStillWork() throws SQLException {
        requireMysql();
        try (SqlSession session = factory.openSession()) {
            CrudLegacyMapper legacy = session.getMapper(CrudLegacyMapper.class);

            // 1/2. insertPo + selectPoById
            CrudPo po = new CrudPo();
            po.setCode("LEG-1");
            po.setPayload("lp");
            assertEquals(1, legacy.insertPo(po));
            assertNotNull(po.getId());
            CrudPo read = legacy.selectPoById(po.getId());
            assertNotNull(read);
            assertEquals("LEG-1", read.getCode());
            assertTrue(read.getRowValid());

            // 3. updatePo：row_version+1 且无版本 WHERE（旧 XML 语义）
            CrudPo upd = new CrudPo();
            upd.setId(po.getId());
            upd.setPayload("lp2");
            assertEquals(1, legacy.updatePo(upd));
            assertEquals(Integer.valueOf(2), rowVersionInDb(po.getId()), "旧 updatePo 版本自增（非 CAS）");

            // 8. selectPoByExample
            CrudPo ex = new CrudPo();
            ex.setCode("LEG-1");
            List<CrudPo> byExample = legacy.selectPoByExample(ex);
            assertTrue(byExample.stream().anyMatch(p -> p.getId().equals(po.getId())));

            // 9. selectPoByMap（静态 row_valid=1）
            List<CrudPo> byMap = legacy.selectPoByMap(Map.of());
            assertTrue(byMap.stream().anyMatch(p -> p.getId().equals(po.getId())));

            // 10. countPoByMap
            assertTrue(legacy.countPoByMap(Map.of()) >= 1);

            // 2. batchInsertPo（两个）
            CrudPo b1 = new CrudPo();
            b1.setCode("LEG-B1");
            CrudPo b2 = new CrudPo();
            b2.setCode("LEG-B2");
            assertEquals(2, legacy.batchInsertPo(List.of(b1, b2)));
            assertNotNull(b1.getId());
            assertNotNull(b2.getId());

            // 4. logicalDeletePo
            assertEquals(1, legacy.logicalDeletePo(po.getId()));
            assertNull(legacy.selectPoById(po.getId()), "逻辑删除后 selectPoById（WHERE row_valid=1）不可见");
            assertEquals(0, rowValidInDb(po.getId()));

            // 6. batchPhysicalDeletePo（Long[]）
            assertEquals(2, legacy.batchPhysicalDeletePo(new Long[]{b1.getId(), b2.getId()}));
            assertEquals(0, physicalCountInDb(b1.getId()));
            assertEquals(0, physicalCountInDb(b2.getId()));

            // 5. physicalDeletePo
            CrudPo phys = new CrudPo();
            phys.setCode("LEG-PHYS");
            legacy.insertPo(phys);
            assertEquals(1, legacy.physicalDeletePo(phys.getId()));
            assertEquals(0, physicalCountInDb(phys.getId()));
        }
    }

    @Test
    @DisplayName("RG-02：原生 BaseMapper 与存量 BaseDao 共享同一 handler/插件配置（同工厂）")
    void sharedHandlerAndPluginsAcrossTracks() {
        requireMysql();
        try (SqlSession session = factory.openSession()) {
            CrudNativeMapper nativeMapper = session.getMapper(CrudNativeMapper.class);
            CrudLegacyMapper legacyMapper = session.getMapper(CrudLegacyMapper.class);

            CrudPo po = new CrudPo();
            po.setCode("RG02-SHARED");
            nativeMapper.insert(po);
            // 共享 MetaObjectHandler：应用填充生效
            assertNotNull(po.getCreateTime(), "原生路径共享 handler 应用填充生效");

            // 共享乐观锁插件：原生 updateById 版本 +1
            CrudPo upd = new CrudPo();
            upd.setId(po.getId());
            upd.setPayload("rg02");
            upd.setRowVersion(1);
            assertEquals(1, nativeMapper.updateById(upd));
            assertEquals(Integer.valueOf(2), rowVersionInDb(po.getId()));

            // 同一表 legacy 路径仍可读取（不同 namespace 不冲突）
            assertNotNull(legacyMapper.selectPoById(po.getId()), "legacy 与原生共存于同一表/工厂");
            assertFalse(legacyMapper.selectPoByMap(Map.of()).isEmpty());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
