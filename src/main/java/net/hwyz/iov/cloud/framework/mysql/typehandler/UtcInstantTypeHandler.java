package net.hwyz.iov.cloud.framework.mysql.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 显式 UTC 事件时间 TypeHandler（FW-MYSQL-DSN-CR-001，RD-001）。
 * <p>
 * 语义：将 {@link Instant} 与 DATETIME 列的 <b>UTC 墙钟</b> 显式映射，不依赖 JVM 默认时区、
 * 不依赖驱动对 TIMESTAMP 的时刻转换语义。
 * <ul>
 *   <li>写：{@code Instant → LocalDateTime(UTC)}，以 JDBC 本地日期时间（{@code setObject(..., Types.TIMESTAMP)}）
 *       绑定，表达"该 DATETIME 列存储 UTC 墙钟"；</li>
 *   <li>读：{@code getObject(col, LocalDateTime.class)} 取列墙钟后以 {@code ZoneOffset.UTC} 还原 Instant；
 *       该读路径是否随会话时区转换由真实驱动行为决定，属于<b>待测试证明</b>项（见集成测试），
 *       会话 UTC（RD-002）是 handler 正确工作的前提；</li>
 *   <li>null：按字段可空性处理，返回 null；</li>
 *   <li>精度：handler 不做截断，{@link Instant} 纳秒原样映射到 LocalDateTime；实际存储精度由
 *       DATETIME(3)/DATETIME(6) 列定义决定，不允许同一个 handler 静默损失既有微秒精度
 *       （RD-007：既有 DATETIME(6) 原精度保留）。</li>
 * </ul>
 * 使用（字段级 opt-in，<b>不全局注册</b>为所有 Instant 的默认映射，避免误覆盖存量 TIMESTAMP 路径）：
 * <pre>
 *   &#064;TableName(value = "xxx", autoResultMap = true)
 *   public class XxxPo {
 *       &#064;TableField(value = "occurred_at", typeHandler = UtcInstantTypeHandler.class)
 *       private Instant occurredAt;
 *   }
 * </pre>
 * <ul>
 *   <li>{@code autoResultMap = true} 使 MyBatis-Plus 自动生成的结果映射携带字段级 typeHandler，
 *       否则 SELECT 读路径不生效（仅有写入生效）；</li>
 *   <li>自定义 XML/resultMap 查询需在 resultMap 或 SQL 中显式声明该 typeHandler；</li>
 *   <li>仅用于已登记为"UTC 事件"的字段；预约/排程墙钟字段（RD-006）必须走原样 LocalDateTime
 *       墙钟映射，不得使用本 handler。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
@MappedTypes(Instant.class)
@MappedJdbcTypes(JdbcType.TIMESTAMP)
public class UtcInstantTypeHandler extends BaseTypeHandler<Instant> {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType)
            throws SQLException {
        LocalDateTime utcWallClock = LocalDateTime.ofInstant(parameter, UTC);
        ps.setObject(i, utcWallClock, Types.TIMESTAMP);
    }

    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toInstant(rs.getObject(columnName, LocalDateTime.class));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toInstant(rs.getObject(columnIndex, LocalDateTime.class));
    }

    @Override
    public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toInstant(cs.getObject(columnIndex, LocalDateTime.class));
    }

    private static Instant toInstant(LocalDateTime wallClock) {
        return wallClock == null ? null : wallClock.toInstant(UTC);
    }
}
