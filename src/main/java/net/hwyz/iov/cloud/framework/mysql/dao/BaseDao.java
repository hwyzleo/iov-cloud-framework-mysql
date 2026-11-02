package net.hwyz.iov.cloud.framework.mysql.dao;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 基础DAO
 *
 * <p><b>已冻结废弃（FW-MYSQL-DSN-CR-003）</b>：新代码请直接继承
 * {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} 使用 MyBatis-Plus 原生 CRUD
 * （基础增删改查无需手写 XML 与旧十方法）；存量代码按迁移指南逐 Mapper 渐进迁移。
 * 本接口保持<b>兼容冻结</b>：全部方法签名、{@link org.apache.ibatis.annotations.Param} 注解
 * 与 SQL 调用路径不变，在真实依赖清零前不删除（{@code forRemoval=false}）。</p>
 *
 * @author hwyz_leo
 * @deprecated since 0.0.1-SNAPSHOT — 使用 {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} 替代
 */
@Deprecated(since = "0.0.1-SNAPSHOT", forRemoval = false)
public interface BaseDao<T, ID> {

    /**
     * 新增数据
     *
     * @param po 数据对象
     * @return 影响行数
     */
    int insertPo(T po);

    /**
     * 批量新增数据
     *
     * @param poList 数据对象列表
     * @return 影响行数
     */
    int batchInsertPo(List<T> poList);

    /**
     * 更新数据
     *
     * @param po 数据对象
     * @return 影响行数
     */
    int updatePo(T po);

    /**
     * 逻辑删除数据
     *
     * @param id 主键
     * @return 影响行数
     */
    int logicalDeletePo(ID id);

    /**
     * 物理删除数据
     *
     * @param id 主键
     * @return 影响行数
     */
    int physicalDeletePo(ID id);

    /**
     * 批量物理删除数据
     *
     * @param ids id数组
     * @return 影响行数
     */
    int batchPhysicalDeletePo(@Param("array") Long[] ids);

    /**
     * 根据主键获取对应数据
     *
     * @param id 主键
     * @return 数据对象
     */
    T selectPoById(ID id);

    /**
     * 根据数据对象获取对应数据列表
     *
     * @param po 数据对象
     * @return 数据对象列表
     */
    List<T> selectPoByExample(T po);

    /**
     * 根据Map对象获取对应数据列表
     *
     * @param map Map对象
     * @return 数据对象列表
     */
    List<T> selectPoByMap(Map<String, Object> map);

    /**
     * 根据Map对象统计记录数
     *
     * @param map Map对象
     * @return 数据对象列表
     */
    int countPoByMap(Map<String, Object> map);

}
