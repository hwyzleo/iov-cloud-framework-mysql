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
        this.strictInsertFill(metaObject, "rowValid", Integer.class, 1);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "modifyTime", Date.class, new Date());
    }
}