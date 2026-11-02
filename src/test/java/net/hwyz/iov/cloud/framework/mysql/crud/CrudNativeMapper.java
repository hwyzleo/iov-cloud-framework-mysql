package net.hwyz.iov.cloud.framework.mysql.crud;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * FW-MYSQL-DSN-CR-003 新标准 Mapper：零 XML 基础 CRUD（IT-01/IT-02/IT-03）。
 * <p>物理删除走独立受控的具名方法（禁止用逻辑表的 deleteById 冒充硬删除，RD-04）。</p>
 */
public interface CrudNativeMapper extends BaseMapper<CrudPo> {

    /**
     * 受控物理删除（专用 SQL，非逻辑删除）。
     *
     * @param id 主键
     * @return 影响行数
     */
    @Delete("DELETE FROM t_cr003_crud WHERE id = #{id}")
    int purgeById(@Param("id") Long id);
}
