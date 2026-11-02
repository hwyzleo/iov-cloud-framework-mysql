package net.hwyz.iov.cloud.framework.mysql.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.io.Serializable;

/**
 * 存量迁移 opt-in 桥接接口（FW-MYSQL-DSN-CR-003，RD-02）。
 *
 * <p>仅为签名与语义确认等价的四个旧命名方法保留名称；<b>不继承旧 {@link BaseDao}</b>，
 * 不声称完整替代十方法。选择本桥接的 Mapper 必须显式处理未覆盖方法
 * （batchInsertPo / physicalDeletePo / batchPhysicalDeletePo / selectPoByExample /
 * selectPoByMap / countPoByMap 等继续使用具名 SQL 或显式业务重构）。</p>
 *
 * <p>边界（RD-02/RD-04）：
 * <ul>
 *   <li>不自动补救空 rowVersion、缺 {@code @TableLogic} 或不匹配旧 XML 的语义；启用前有专项测试；</li>
 *   <li>不得同时继承旧 {@link BaseDao} 与本桥接（避免 abstract/default 冲突）；</li>
 *   <li>logicalDeletePo 仅允许逻辑删除映射已核验的具体 PO 使用；不提供物理删除假桥接。</li>
 * </ul>
 * </p>
 *
 * @param <T> 数据对象类型（必须映射真实 PO）
 */
public interface LegacyCrudBridgeMapper<T> extends BaseMapper<T> {

    /**
     * 新增数据（等价于 {@link BaseMapper#insert(Object)}）。
     *
     * @param po 数据对象
     * @return 影响行数
     */
    default int insertPo(T po) {
        return insert(po);
    }

    /**
     * 更新数据（等价于 {@link BaseMapper#updateById(Object)}，乐观锁语义按 PO 配置生效）。
     *
     * @param po 数据对象
     * @return 影响行数
     */
    default int updatePo(T po) {
        return updateById(po);
    }

    /**
     * 根据主键获取对应数据（等价于 {@link BaseMapper#selectById(Serializable)}）。
     *
     * @param id 主键
     * @return 数据对象
     */
    default T selectPoById(Serializable id) {
        return selectById(id);
    }

    /**
     * 逻辑删除数据（等价于 {@link BaseMapper#deleteById(Serializable)}，仅限有 {@code @TableLogic} 的 PO）。
     *
     * @param id 主键
     * @return 影响行数
     */
    default int logicalDeletePo(Serializable id) {
        return deleteById(id);
    }
}
