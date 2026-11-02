package net.hwyz.iov.cloud.framework.mysql.crud;

import net.hwyz.iov.cloud.framework.mysql.dao.LegacyCrudBridgeMapper;

/**
 * FW-MYSQL-DSN-CR-003 opt-in 桥接 Mapper（UT-01 真实执行部分 / IT）：
 * 四个 default 方法（insertPo/updatePo/selectPoById/logicalDeletePo）在真实 MySQL 上
 * 分派到 MP 原生语句；本 Mapper 无任何 XML，若分派错误将直接 BindingException。
 */
public interface CrudBridgeMapper extends LegacyCrudBridgeMapper<CrudPo> {
}
