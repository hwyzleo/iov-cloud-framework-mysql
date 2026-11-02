package net.hwyz.iov.cloud.framework.mysql.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * FW-MYSQL-DSN-CR-003 UT-02：MetaObjectHandler fill 语义回归（RD-04）。
 * <p>
 * 覆盖：Boolean rowValid 用 Boolean.class 精确匹配填充（Integer.class 不匹配、实际不填充的
 * 旧行为回归证据）；已有值（含显式 false）不被覆盖；updateFill 不递增 rowVersion、不写
 * DB-owned 时间；createTime/modifyTime 本批仍由应用填充（本批例外，见 DSN §10.2）。
 * </p>
 */
class MyBatisMetaObjectHandlerTest {

    private final MyBatisMetaObjectHandler handler = new MyBatisMetaObjectHandler();

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), FillPo.class);
    }

    @TableName("t_fill_test")
    static class FillPo {
        @TableId(type = IdType.AUTO)
        private Long id;
        @TableField(value = "row_valid", fill = FieldFill.INSERT)
        private Boolean rowValid;
        @TableField(value = "row_version")
        private Integer rowVersion;
        @TableField(value = "create_time", fill = FieldFill.INSERT)
        private Date createTime;
        @TableField(value = "modify_time", fill = FieldFill.INSERT_UPDATE)
        private Date modifyTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Boolean getRowValid() { return rowValid; }
        public void setRowValid(Boolean rowValid) { this.rowValid = rowValid; }
        public Integer getRowVersion() { return rowVersion; }
        public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }
        public Date getModifyTime() { return modifyTime; }
        public void setModifyTime(Date modifyTime) { this.modifyTime = modifyTime; }
    }

    private static MetaObject metaObject(FillPo po) {
        return MetaObject.forObject(po, SystemMetaObject.DEFAULT_OBJECT_FACTORY,
                SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY, new DefaultReflectorFactory());
    }

    @Test
    @DisplayName("insertFill：Boolean rowValid 为 null 时以 Boolean.class 精确匹配填充 true")
    void insertFillFillsBooleanRowValidWhenNull() {
        FillPo po = new FillPo();
        handler.insertFill(metaObject(po));
        assertSame(Boolean.TRUE, po.getRowValid(), "rowValid 必须被填充为 true（Boolean 类型匹配）");
        assertNull(po.getRowVersion(),
                "rowVersion 无 fill 注解，strictInsertFill 不填（与真实 BasePo 一致：rowVersion=1 由字段初始化兜底）");
        assertNotNull(po.getCreateTime(), "createTime 本批仍由应用填充（DSN §10.2 本批例外）");
        assertNotNull(po.getModifyTime(), "modifyTime 本批仍由应用填充");
    }

    @Test
    @DisplayName("insertFill：已有值不覆盖，显式 false 保留（RD-04）")
    void insertFillDoesNotOverwriteExplicitFalse() {
        FillPo po = new FillPo();
        po.setRowValid(Boolean.FALSE);
        po.setRowVersion(5);
        handler.insertFill(metaObject(po));
        assertSame(Boolean.FALSE, po.getRowValid(), "显式 false 不能被覆盖为 true");
        assertEquals(Integer.valueOf(5), po.getRowVersion(), "已有 rowVersion 不能被覆盖");
    }

    @Test
    @DisplayName("回归证据：Integer.class 不匹配 Boolean 字段，strictInsertFill 实际不填充（旧行为）")
    void integerClassDoesNotMatchBooleanField() {
        FillPo po = new FillPo();
        handler.strictInsertFill(metaObject(po), "rowValid", Integer.class, 1);
        assertNull(po.getRowValid(),
                "strictInsertFill 要求精确 Java 类型匹配：Integer.class 对 Boolean 字段不填充（旧 handler 缺陷依据）");
    }

    @Test
    @DisplayName("updateFill：只填 modifyTime，不递增 rowVersion、不写 createTime")
    void updateFillFillsModifyTimeOnly() {
        FillPo po = new FillPo();
        po.setRowVersion(3);
        handler.updateFill(metaObject(po));
        assertNotNull(po.getModifyTime(), "updateFill 必须填充 modifyTime");
        assertEquals(Integer.valueOf(3), po.getRowVersion(), "updateFill 不得递增 rowVersion（禁止双重递增）");
        assertNull(po.getCreateTime(), "updateFill 不得写 createTime");
    }
}
