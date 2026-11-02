package net.hwyz.iov.cloud.framework.mysql.crud;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.hwyz.iov.cloud.framework.mysql.po.BasePo;
import net.hwyz.iov.cloud.framework.mysql.typehandler.UtcInstantTypeHandler;

import java.time.Instant;

/**
 * FW-MYSQL-DSN-CR-003 集成测试 PO：结构与消费服务（如 VIAM DecisionAuditPo）同构
 * （继承 BasePo、AUTO 主键、公共审计/版本/逻辑删除字段）。
 * <p>
 * {@code autoResultMap = true} + 字段级 {@link UtcInstantTypeHandler}：
 * 证明原生 CRUD（无 XML resultMap）下 UTC 类型映射仍生效（DSN §10.2 / RD-04）。
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName(value = "t_cr003_crud", autoResultMap = true)
public class CrudPo extends BasePo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("code")
    private String code;

    @TableField("payload")
    private String payload;

    /** UTC 事件时间：原生查询路径必须经 handler 正确往返（不依赖旧 XML resultMap）。 */
    @TableField(value = "occurred_at", typeHandler = UtcInstantTypeHandler.class)
    private Instant occurredAt;
}
