-- FW-MYSQL-DSN-CR-001 集成测试：验证 Flyway 复用数据源（已合并会话 UTC 初始化 SQL）时，
-- 迁移连接本身处于 UTC 会话（@@session.time_zone='+00:00'），证明初始化早于 Flyway。
CREATE TABLE session_utc_probe (tz VARCHAR(32));
INSERT INTO session_utc_probe SELECT @@session.time_zone;
