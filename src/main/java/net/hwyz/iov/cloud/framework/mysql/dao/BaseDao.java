package net.hwyz.iov.cloud.framework.mysql.dao;

import java.util.List;
import java.util.Map;

/**
 * 基础DAO
 *
 * @author hwyz_leo
 */
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
    int batchPhysicalDeletePo(Long[] ids);

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
