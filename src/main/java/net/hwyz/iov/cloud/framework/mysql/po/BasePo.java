package net.hwyz.iov.cloud.framework.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

/**
 * 基础数据对象
 *
 * @author hwyz_leo
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class BasePo {

    /**
     * 描述
     */
    @TableField("description")
    private String description;
    /**
     * 创建者
     */
    @TableField("create_by")
    private String createBy;
    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;
    /**
     * 修改者
     */
    @TableField("modify_by")
    private String modifyBy;
    /**
     * 修改时间
     */
    @TableField("modify_time")
    private Date modifyTime;
    /**
     * 记录版本
     */
    @TableField("row_version")
    @Version
    private Integer rowVersion;
    /**
     * 记录是否有效
     */
    @TableField("row_valid")
    @TableLogic
    private Boolean rowValid;

}
