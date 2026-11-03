package net.hwyz.iov.cloud.framework.mysql.validation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.ResolvableType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 新标准 Mapper 语句完整性校验器（FW-MYSQL-DSN-CR-003，§5.3）。
 * <p>
 * 在 Mapper 注册完成后、业务流量前（{@link SmartInitializingSingleton}）对容器中<b>每个</b>
 * {@link SqlSessionFactory} 的 MapperRegistry 校验：继承 {@link BaseMapper} 的 Mapper
 * 是否具备 MP 注入的基础 CRUD 语句（insert/updateById/deleteById/selectById/selectList/
 * selectCount/selectPage）及实体主键元数据，将语句遗漏提前暴露到启动阶段（早失败），
 * 不等待首个业务请求触发 BindingException。
 * </p>
 * <p>
 * 约束：仅校验语句存在性与关键元数据，<b>不访问/不写入业务表</b>；只检查语句存在
 * 不能证明表列正确，不宣称编译期可校验所有数据库结构；错误只输出 Mapper 类名与语句 ID，
 * 不泄露 SQL 参数或数据库凭据。
 * </p>
 */
public class MapperStatementValidator implements SmartInitializingSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapperStatementValidator.class);

    /** MP BaseMapper 注入的基础 CRUD 语句名（与 3.5.3.1 AbstractMethod 注入集合对应）。 */
    static final List<String> REQUIRED_MP_STATEMENTS = List.of(
            "insert", "updateById", "deleteById", "selectById",
            "selectList", "selectCount", "selectPage");

    private final MapperValidationProperties properties;
    private final List<SqlSessionFactory> sqlSessionFactories;

    public MapperStatementValidator(MapperValidationProperties properties,
                                    List<SqlSessionFactory> sqlSessionFactories) {
        this.properties = properties;
        this.sqlSessionFactories = sqlSessionFactories;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (properties.getMode() == MapperValidationMode.OFF) {
            LOGGER.info("FW-MYSQL Mapper 语句完整性校验未启用（iov.mysql.mapper-validation.mode=off）");
            return;
        }
        List<String> issues = new ArrayList<>();
        for (SqlSessionFactory factory : sqlSessionFactories) {
            Configuration configuration = factory.getConfiguration();
            String factoryName = configuration.getEnvironment() == null
                    ? "default" : configuration.getEnvironment().getId();
            Collection<Class<?>> mappers = configuration.getMapperRegistry().getMappers();
            for (Class<?> mapperClass : mappers) {
                if (!BaseMapper.class.isAssignableFrom(mapperClass)) {
                    continue;
                }
                // 泛型不可解析：桥接/标记接口（如框架 LegacyCrudBridgeMapper<T>）不是
                // 具体业务 Mapper，跳过（FW-MYSQL-DSN-CR-003 启动回归：VIAM 通配符扫描误注册）。
                Class<?> entityClass = resolveEntityClass(mapperClass);
                if (entityClass == null) {
                    continue;
                }
                List<String> missing = new ArrayList<>();
                String namespace = mapperClass.getName();
                for (String statement : REQUIRED_MP_STATEMENTS) {
                    if (!configuration.hasStatement(namespace + "." + statement, false)) {
                        missing.add(statement);
                    }
                }
                TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
                if (tableInfo == null) {
                    missing.add("PO 未解析 TableInfo（缺少 @TableName 或未注册）");
                } else if (tableInfo.getKeyProperty() == null || tableInfo.getKeyProperty().isBlank()) {
                    missing.add("PO 未声明主键（@TableId）");
                }
                if (!missing.isEmpty()) {
                    issues.add("mapper=" + mapperClass.getName()
                            + ", factory=" + factoryName
                            + ", missing=" + missing);
                }
            }
        }
        handleIssues(issues);
    }

    /** 从 Mapper 泛型解析实体类型（{@code BaseMapper<T>} 的 T）。 */
    static Class<?> resolveEntityClass(Class<?> mapperClass) {
        ResolvableType resolvableType = ResolvableType.forClass(mapperClass).as(BaseMapper.class);
        return resolvableType.getGeneric(0).resolve();
    }

    private void handleIssues(List<String> issues) {
        if (issues.isEmpty()) {
            LOGGER.info("FW-MYSQL Mapper 语句完整性校验通过（{} 个 SqlSessionFactory）", sqlSessionFactories.size());
            return;
        }
        switch (properties.getMode()) {
            case WARN ->
                    LOGGER.warn("FW-MYSQL Mapper 语句完整性校验发现问题（mode=warn，仅告警）：{}", issues);
            case FAIL -> {
                LOGGER.error("FW-MYSQL Mapper 语句完整性校验失败（mode=fail），阻断启动：{}", issues);
                throw new IllegalStateException(
                        "FW-MYSQL Mapper 语句完整性校验失败（iov.mysql.mapper-validation.mode=fail）: " + issues);
            }
            default -> {
                // OFF 已在上层返回，不会到达
            }
        }
    }
}
