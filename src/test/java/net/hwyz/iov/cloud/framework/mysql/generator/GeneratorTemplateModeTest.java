package net.hwyz.iov.cloud.framework.mysql.generator;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FW-MYSQL-DSN-CR-003 UT-04：生成器模板双模式渲染测试（DSN §6.6 / §10.3）。
 * <p>
 * 模板快照取自 rsms-service 生成器模板副本（本 CR 首批修改入口），验证：
 * <ul>
 *   <li>新标准（superClass=BaseMapper）：Java 模板生成单泛型 {@code BaseMapper<XxxPo>}；
 *       XML 模板<b>不</b>生成十个旧 CRUD 语句（MP 注入原生语句），仅保留 namespace/resultMap/baseColumnList；</li>
 *   <li>legacy 显式模式（superClass=BaseDao）：Java 模板生成双泛型 {@code BaseDao<XxxPo, Long>}；
 *       XML 模板仍生成十个 CRUD 语句（兼容旧模式）。</li>
 * </ul>
 * 两种模式均由生成器配置源（{@code superClass}）切换，模板不硬编码 BaseMapper（§10.3）。
 * </p>
 */
class GeneratorTemplateModeTest {

    private static final String BASE_MAPPER = "com.baomidou.mybatisplus.core.mapper.BaseMapper";
    private static final String LEGACY_BASE_DAO = "net.hwyz.iov.cloud.framework.mysql.dao.BaseDao";
    private static final String[] LEGACY_CRUD_STATEMENTS = {
            "insertPo", "batchInsertPo", "updatePo", "logicalDeletePo", "physicalDeletePo",
            "batchPhysicalDeletePo", "selectPoById", "selectPoByExample", "selectPoByMap", "countPoByMap"
    };

    private Template template(String name) throws Exception {
        Configuration cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        cfg.setClassForTemplateLoading(GeneratorTemplateModeTest.class, "/generator-templates");
        cfg.setDefaultEncoding("UTF-8");
        return cfg.getTemplate(name);
    }

    private String render(Template template, String superClass) throws Exception {
        StringWriter out = new StringWriter();
        template.process(model(superClass), out);
        return out.toString();
    }

    private Map<String, Object> model(String superClass) {
        String simpleName = superClass.substring(superClass.lastIndexOf('.') + 1);
        Map<String, Object> table = new HashMap<>();
        table.put("mapperName", "FooMapper");
        table.put("comment", "测试表");
        table.put("name", "tb_foo");
        table.put("fields", List.of(
                field("id", "id", "id", true, "Long"),
                field("code", "code", "code", false, "String")));
        table.put("commonFields", List.of(
                field("description", "description", "description", false, "String"),
                field("row_valid", "rowValid", "row_valid", false, "Boolean"),
                field("row_version", "rowVersion", "row_version", false, "Integer")));

        Map<String, Object> pkg = Map.of(
                "Mapper", "net.hwyz.iov.cloud.tsp.rsms.service.infrastructure.repository.dao",
                "Entity", "net.hwyz.iov.cloud.tsp.rsms.service.infrastructure.repository.po");

        Map<String, Object> model = new HashMap<>();
        model.put("package", pkg);
        model.put("entity", "FooPo");
        model.put("superMapperClass", simpleName);
        // MP 生成器模型：superMapperClassPackage 实际为全限定类名（import 用），superMapperClass 为简单类名（extends 用）
        model.put("superMapperClassPackage", superClass);
        model.put("mapperAnnotationClass", null);
        model.put("kotlin", false);
        model.put("table", table);
        model.put("author", "test");
        model.put("date", "2026-11-02");
        model.put("enableCache", false);
        model.put("cacheClassName", "");
        model.put("baseResultMap", true);
        model.put("baseColumnList", true);
        return model;
    }

    private static Map<String, Object> field(String name, String propertyName, String columnName,
                                             boolean keyFlag, String propertyType) {
        Map<String, Object> f = new HashMap<>();
        f.put("name", name);
        f.put("propertyName", propertyName);
        f.put("columnName", columnName);
        f.put("keyFlag", keyFlag);
        f.put("propertyType", propertyType);
        return f;
    }

    // ---------- 新标准模式（BaseMapper） ----------

    @Test
    @DisplayName("新标准：Java 模板生成单泛型 BaseMapper<XxxPo>（零 XML CRUD）")
    void nativeModeGeneratesSingleGenericBaseMapper() throws Exception {
        String java = render(template("HwyzMapper.java.ftl"), BASE_MAPPER);
        assertTrue(java.contains("import com.baomidou.mybatisplus.core.mapper.BaseMapper;"),
                "必须导入 BaseMapper（FQCN）：\n" + java);
        assertTrue(java.contains("extends BaseMapper<FooPo>"),
                "新标准必须生成单泛型 BaseMapper<FooPo>：\n" + java);
        assertFalse(java.contains("<FooPo, Long>"), "新标准不得生成双泛型：\n" + java);
        assertFalse(java.contains("BaseDao"), "新标准不得引用旧 BaseDao：\n" + java);
    }

    @Test
    @DisplayName("新标准：XML 模板不生成十个旧 CRUD 语句，保留 namespace/resultMap/baseColumnList")
    void nativeModeXmlHasNoLegacyCrud() throws Exception {
        String xml = render(template("HwyzMapper.xml.ftl"), BASE_MAPPER);
        assertTrue(xml.contains("<mapper namespace="), "必须保留 namespace");
        assertTrue(xml.contains("id=\"baseResultMap\""), "必须保留 resultMap（自定义 SQL 引用）");
        assertTrue(xml.contains("id=\"baseColumnList\""), "必须保留 baseColumnList（自定义 SQL 引用）");
        for (String statement : LEGACY_CRUD_STATEMENTS) {
            assertFalse(xml.contains("id=\"" + statement + "\""),
                    "新标准 XML 不得生成 " + statement + "（MP 注入原生语句）：\n" + xml);
        }
    }

    // ---------- legacy 显式模式（BaseDao） ----------

    @Test
    @DisplayName("legacy 显式模式：Java 模板生成双泛型 BaseDao<XxxPo, Long>（模式可切换，未破坏）")
    void legacyModeGeneratesTwoGenericBaseDao() throws Exception {
        String java = render(template("HwyzMapper.java.ftl"), LEGACY_BASE_DAO);
        assertTrue(java.contains("import net.hwyz.iov.cloud.framework.mysql.dao.BaseDao;"),
                "legacy 模式必须导入 BaseDao：\n" + java);
        assertTrue(java.contains("extends BaseDao<FooPo, Long>"),
                "legacy 模式必须生成双泛型 BaseDao<FooPo, Long>：\n" + java);
    }

    @Test
    @DisplayName("legacy 显式模式：XML 模板仍生成十个 CRUD 语句（兼容旧模式）")
    void legacyModeXmlKeepsTenCrudStatements() throws Exception {
        String xml = render(template("HwyzMapper.xml.ftl"), LEGACY_BASE_DAO);
        for (String statement : LEGACY_CRUD_STATEMENTS) {
            assertTrue(xml.contains("id=\"" + statement + "\""),
                    "legacy 模式 XML 必须生成 " + statement + "：\n" + xml);
        }
    }
}
