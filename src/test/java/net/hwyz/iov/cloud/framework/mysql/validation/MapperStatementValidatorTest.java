package net.hwyz.iov.cloud.framework.mysql.validation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.hwyz.iov.cloud.framework.mysql.dao.BaseDao;
import net.hwyz.iov.cloud.framework.mysql.dao.LegacyCrudBridgeMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FW-MYSQL-DSN-CR-003 UT-03：Mapper 语句完整性校验器（§5.3）。
 * <p>
 * 覆盖：缺 insertPo 的 legacy Mapper 在 fail 模式阻断（事故根因场景）、warn 仅告警、off 跳过；
 * 新标准 BaseMapper 与桥接 Mapper 注入语句后通过；注解 SQL 覆盖的方法不算缺失；多
 * SqlSessionFactory 分别校验；无主键 PO 报缺失。全部为元数据级校验，不访问/不写入业务表。
 * </p>
 */
class MapperStatementValidatorTest {

    // ---------- 样例 PO / Mapper ----------

    @TableName("t_sample")
    public static class SamplePo {
        @TableId(type = IdType.AUTO)
        private Long id;
        @TableField("name")
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /** 事故根因场景：继承纯抽象 BaseDao，无任何 CRUD XML/注解语句。 */
    public interface SampleLegacyMapper extends BaseDao<SamplePo, Long> {
    }

    /** 新标准：直接继承 BaseMapper，MP 注入原生语句。 */
    public interface SampleNativeMapper extends BaseMapper<SamplePo> {
    }

    /** opt-in 桥接：继承 LegacyCrudBridgeMapper（仅 BaseMapper）。 */
    public interface SampleBridgeMapper extends LegacyCrudBridgeMapper<SamplePo> {
    }

    /** 部分注解覆盖：selectPoById 有 @Select，其余 BaseDao 抽象方法仍缺语句。 */
    public interface SampleAnnotatedMapper extends BaseDao<SamplePo, Long> {
        @Override
        @Select("SELECT * FROM t_sample WHERE id = #{id}")
        SamplePo selectPoById(Long id);
    }

    /** 无主键 PO：@TableName 但无 @TableId 且无名为 id 的字段。 */
    @TableName("t_no_key")
    public static class NoKeyPo {
        @TableField("name")
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public interface SampleNoKeyMapper extends BaseMapper<NoKeyPo> {
    }

    // ---------- 工具 ----------

    private static SqlSessionFactory factoryWith(Class<?>... mappers) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> mapper : mappers) {
            configuration.addMapper(mapper);
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static MapperValidationProperties properties(MapperValidationMode mode) {
        MapperValidationProperties p = new MapperValidationProperties();
        p.setMode(mode);
        return p;
    }

    private static void runBoth(List<SqlSessionFactory> factories, MapperValidationMode mode) {
        MapperValidationProperties p = properties(mode);
        new MapperStatementValidator(p, factories).afterSingletonsInstantiated();
        new LegacyMapperStatementValidator(p, factories).afterSingletonsInstantiated();
    }

    // ---------- legacy 校验器 ----------

    @Test
    @DisplayName("mode=off：跳过校验，缺语句不阻断")
    void offModeSkipsValidation() {
        assertDoesNotThrow(() -> runBoth(
                List.of(factoryWith(SampleLegacyMapper.class)), MapperValidationMode.OFF));
    }

    @Test
    @DisplayName("mode=fail：缺 insertPo 的 legacy Mapper 阻断启动并定位 Mapper/语句")
    void legacyMissingStatementsFailInFailMode() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> runBoth(
                List.of(factoryWith(SampleLegacyMapper.class)), MapperValidationMode.FAIL));
        assertTrue(e.getMessage().contains(SampleLegacyMapper.class.getName()), "必须定位到 Mapper 类名");
        assertTrue(e.getMessage().contains("insertPo"), "必须列出缺失的 insertPo 语句（事故根因）");
        assertTrue(e.getMessage().contains("updatePo"), "必须列出缺失的 updatePo 语句");
    }

    @Test
    @DisplayName("mode=warn：缺语句仅告警不阻断")
    void legacyMissingStatementsWarnInWarnMode() {
        assertDoesNotThrow(() -> runBoth(
                List.of(factoryWith(SampleLegacyMapper.class)), MapperValidationMode.WARN));
    }

    @Test
    @DisplayName("注解 SQL 覆盖的方法不算缺失；其余 BaseDao 抽象方法仍报缺失")
    void annotatedOverrideExcludedFromMissing() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> runBoth(
                List.of(factoryWith(SampleAnnotatedMapper.class)), MapperValidationMode.FAIL));
        String msg = e.getMessage();
        assertFalse(msg.contains("selectPoById"), "有 @Select 注解的 selectPoById 不算缺失");
        assertTrue(msg.contains("insertPo"), "未覆盖的 insertPo 仍必须报缺失");
        assertTrue(msg.contains("batchInsertPo"), "未覆盖的 batchInsertPo 仍必须报缺失");
    }

    // ---------- 新标准 / 桥接 ----------

    @Test
    @DisplayName("新标准 BaseMapper：MP 注入语句齐全，fail 模式通过")
    void nativeMapperPassesInFailMode() {
        assertDoesNotThrow(() -> runBoth(
                List.of(factoryWith(SampleNativeMapper.class)), MapperValidationMode.FAIL));
    }

    @Test
    @DisplayName("桥接 Mapper：MP 注入语句齐全且不受 legacy 校验（不继承 BaseDao），fail 模式通过")
    void bridgeMapperPassesInFailMode() {
        assertDoesNotThrow(() -> runBoth(
                List.of(factoryWith(SampleBridgeMapper.class)), MapperValidationMode.FAIL));
        assertFalse(BaseDao.class.isAssignableFrom(SampleBridgeMapper.class),
                "桥接不继承 BaseDao，legacy 校验不适用");
    }

    @Test
    @DisplayName("无主键 PO：校验器报缺失主键（@TableId）")
    void noKeyPoReportsMissingPrimaryKey() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> runBoth(
                List.of(factoryWith(SampleNoKeyMapper.class)), MapperValidationMode.FAIL));
        assertTrue(e.getMessage().contains("主键"), "必须报缺失主键（@TableId），实际=" + e.getMessage());
    }

    // ---------- 多 Factory ----------

    @Test
    @DisplayName("多 SqlSessionFactory 分别校验：坏 Factory 单独定位，好 Factory 不影响")
    void multiFactoryValidatedIndividually() {
        SqlSessionFactory goodFactory = factoryWith(SampleNativeMapper.class);
        SqlSessionFactory badFactory = factoryWith(SampleLegacyMapper.class);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> runBoth(
                List.of(goodFactory, badFactory), MapperValidationMode.FAIL));
        assertTrue(e.getMessage().contains(SampleLegacyMapper.class.getName()),
                "必须定位坏 Factory 中的 Mapper");
        assertFalse(e.getMessage().contains(SampleNativeMapper.class.getName()),
                "好 Factory 的原生 Mapper 不应出现在缺失清单中");
    }

    @Test
    @DisplayName("legacy 校验器跳过非 BaseDao Mapper（新标准/桥接不受 legacy 检查）")
    void legacyValidatorSkipsNonBaseDaoMappers() {
        MapperValidationProperties p = properties(MapperValidationMode.FAIL);
        // 只有原生 Mapper：legacy 校验器不应产生任何缺失
        assertDoesNotThrow(() -> new LegacyMapperStatementValidator(
                p, List.of(factoryWith(SampleNativeMapper.class, SampleBridgeMapper.class)))
                .afterSingletonsInstantiated());
    }
}
