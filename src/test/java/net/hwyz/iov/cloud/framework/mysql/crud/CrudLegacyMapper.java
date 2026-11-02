package net.hwyz.iov.cloud.framework.mysql.crud;

import net.hwyz.iov.cloud.framework.mysql.dao.BaseDao;

/**
 * FW-MYSQL-DSN-CR-003 代表性存量 Mapper（RG-01）：纯抽象 BaseDao + 服务侧 XML。
 * <p>模拟存量服务形态（如 VIAM 事故前/rsms 等），证明 P1 框架升级（BaseDao 仅加 @Deprecated）
 * 不改变旧路径 SQL 与行为。</p>
 */
@SuppressWarnings("deprecation")
public interface CrudLegacyMapper extends BaseDao<CrudPo, Long> {
}
