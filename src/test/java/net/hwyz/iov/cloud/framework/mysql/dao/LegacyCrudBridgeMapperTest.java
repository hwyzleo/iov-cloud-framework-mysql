package net.hwyz.iov.cloud.framework.mysql.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FW-MYSQL-DSN-CR-003 UT-01：LegacyCrudBridgeMapper 结构、default 分派目标与语句注入（RD-01/RD-02）。
 * <p>
 * 覆盖（无真实数据库，仅 MyBatis/MP 元数据级验证）：
 * <ul>
 *   <li>桥接仅继承 {@link BaseMapper}，不继承旧 {@link BaseDao}（避免 abstract/default 冲突）；</li>
 *   <li>四个命名方法均为 {@code default}（非 abstract，不要求 XML/注解语句）；</li>
 *   <li>选择桥接的 Mapper 注册后，MP 注入 insert/updateById/deleteById/selectById 等原生语句，
 *       证明 default 分派进入原生 statement（真实执行在 IT 阶段验证）；</li>
 *   <li>default 方法不因同名旧 XML 或子接口重声明而改变分派（旧 BaseDao 未被切换）。</li>
 * </ul>
 * </p>
 */
class LegacyCrudBridgeMapperTest {

    @Test
    @DisplayName("桥接仅继承 BaseMapper，不继承 BaseDao")
    void bridgeExtendsBaseMapperOnly() {
        assertTrue(BaseMapper.class.isAssignableFrom(LegacyCrudBridgeMapper.class),
                "桥接必须继承 BaseMapper");
        assertFalse(BaseDao.class.isAssignableFrom(LegacyCrudBridgeMapper.class),
                "桥接不得继承旧 BaseDao（RD-02：不原地混入 default）");
        assertEquals(4, bridgeDefaultMethods().size(),
                "桥接仅提供四个已核对语义的方法（insertPo/updatePo/selectPoById/logicalDeletePo）");
    }

    @Test
    @DisplayName("四个命名方法均为 default 实现（非 abstract），签名与 BaseDao 对应方法一致")
    void bridgeMethodsAreDefaultWithMatchingSignatures() throws NoSuchMethodException {
        assertTrue(bridgeDefaultMethods().get("insertPo").isDefault());
        assertTrue(bridgeDefaultMethods().get("updatePo").isDefault());
        assertTrue(bridgeDefaultMethods().get("selectPoById").isDefault());
        assertTrue(bridgeDefaultMethods().get("logicalDeletePo").isDefault());

        // 与旧 BaseDao 对应方法签名核对：返回值与参数
        assertEquals(int.class, bridgeDefaultMethods().get("insertPo").getReturnType());
        assertEquals(int.class, bridgeDefaultMethods().get("updatePo").getReturnType());
        assertEquals(int.class, bridgeDefaultMethods().get("logicalDeletePo").getReturnType());
        assertEquals(Object.class, bridgeDefaultMethods().get("selectPoById").getReturnType(),
                "泛型 T 擦除为 Object；实际类型由具体 Mapper 的泛型解析（与 BaseDao 相同机制）");
        assertEquals(Serializable.class, bridgeDefaultMethods().get("selectPoById").getParameterTypes()[0]);
        assertEquals(Serializable.class, bridgeDefaultMethods().get("logicalDeletePo").getParameterTypes()[0]);
    }

    @Test
    @DisplayName("选择桥接的 Mapper 注册后 MP 注入原生语句（default 分派目标存在）")
    void bridgeMapperGetsMpStatementsInjected() {
        SqlSessionFactory factory = factoryWith(BridgeSampleMapper.class);
        String ns = BridgeSampleMapper.class.getName();
        for (String statement : List.of("insert", "updateById", "deleteById", "selectById", "selectList")) {
            assertTrue(factory.getConfiguration().hasStatement(ns + "." + statement, false),
                    "MP 必须注入 " + ns + "." + statement + "（default 方法分派到原生语句的目标）");
        }
    }

    @Test
    @DisplayName("旧 BaseDao 未被切换：BridgeSampleMapper 不继承 BaseDao，不存在 abstract 冲突")
    void bridgeMapperHasNoBaseDaoInheritance() {
        assertFalse(BaseDao.class.isAssignableFrom(BridgeSampleMapper.class),
                "选择桥接的 Mapper 不得同时继承旧 BaseDao（RD-02）");
    }

    private static java.util.Map<String, Method> bridgeDefaultMethods() {
        java.util.Map<String, Method> byName = new java.util.HashMap<>();
        for (Method m : LegacyCrudBridgeMapper.class.getDeclaredMethods()) {
            byName.put(m.getName(), m);
        }
        return byName;
    }

    private static SqlSessionFactory factoryWith(Class<?>... mappers) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> mapper : mappers) {
            configuration.addMapper(mapper);
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    @TableName("t_bridge_sample")
    static class BridgeSamplePo {
        @TableId(type = IdType.AUTO)
        private Long id;
        @TableField("name")
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    interface BridgeSampleMapper extends LegacyCrudBridgeMapper<BridgeSamplePo> {
    }
}
