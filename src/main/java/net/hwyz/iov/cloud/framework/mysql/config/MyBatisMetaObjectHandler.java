package net.hwyz.iov.cloud.framework.mysql.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * MyBatis Plus 元数据自动填充处理器
 * 自动填充 create_time, modify_time, row_version, row_valid 等字段
 *
 * @author hwyz_leo
 */
@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
        this.strictInsertFill(metaObject, "modifyTime", Date.class, new Date());
        this.strictInsertFill(metaObject, "rowVersion", Integer.class, 1);
        // FW-MYSQL-DSN-CR-003 RD-04：rowValid 为 Boolean，必须用 Boolean.class 精确匹配（Integer.class 不匹配、实际从不填充）；
        // strictFill 已有值不覆盖：显式 false 不被覆盖，也不强制设 true
        this.strictInsertFill(metaObject, "rowValid", Boolean.class, Boolean.TRUE);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "modifyTime", Date.class, new Date());
    }
}