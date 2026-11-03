package net.hwyz.iov.cloud.framework.mysql.validation;

import net.hwyz.iov.cloud.framework.mysql.dao.BaseDao;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 存量 BaseDao Mapper 语句完整性校验器（FW-MYSQL-DSN-CR-003，§5.3）。
 * <p>
 * 对继承旧 {@link BaseDao} 的 Mapper，按 MyBatis 语句解析规则检查其继承的抽象 CRUD 方法
 * 是否具备对应 MappedStatement（XML 或注解 SQL），将"漏 XML 到业务请求阶段才暴露"的
 * 故障提前到启动阶段。跳过 Java {@code default} 与非 SQL 方法。
 * </p>
 * <p>
 * 约束：按实际 Mapper 注册配置（namespace/父接口/注解）解析，不用文本 grep 代替；
 * 不访问/不写入业务表；遗留抽象方法不以"新 BaseMapper 已有 insert"抵消旧 insertPo 缺失；
 * 错误只输出 Mapper 类名与语句 ID。
 * </p>
 */
public class LegacyMapperStatementValidator implements SmartInitializingSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyMapperStatementValidator.class);

    private final MapperValidationProperties properties;
    private final List<SqlSessionFactory> sqlSessionFactories;

    public LegacyMapperStatementValidator(MapperValidationProperties properties,
                                          List<SqlSessionFactory> sqlSessionFactories) {
        this.properties = properties;
        this.sqlSessionFactories = sqlSessionFactories;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (properties.getMode() == MapperValidationMode.OFF) {
            return;
        }
        List<String> issues = new ArrayList<>();
        for (SqlSessionFactory factory : sqlSessionFactories) {
            Configuration configuration = factory.getConfiguration();
            String factoryName = configuration.getEnvironment() == null
                    ? "default" : configuration.getEnvironment().getId();
            Collection<Class<?>> mappers = configuration.getMapperRegistry().getMappers();
            for (Class<?> mapperClass : mappers) {
                if (!BaseDao.class.isAssignableFrom(mapperClass)) {
                    continue;
                }
                // 框架自带契约类型（BaseDao 自身）不是业务 Mapper，由框架自身测试保证，
                // 不参与业务校验（FW-MYSQL-DSN-CR-003 启动回归）。业务 Mapper 需逐方法校验。
                if (mapperClass == BaseDao.class) {
                    continue;
                }
                for (Method method : BaseDao.class.getDeclaredMethods()) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    Method override = findOverride(mapperClass, method);
                    if (override != null && override.isDefault()) {
                        // Java default 实现，无需语句
                        continue;
                    }
                    if (override != null && hasSqlAnnotation(override)) {
                        // 注解 SQL 已由 Mapper 解析注册
                        continue;
                    }
                    if (override != null && !Modifier.isAbstract(override.getModifiers())
                            && !override.isDefault()) {
                        // 具体实现（接口中仅 default 可达，防御性跳过）
                        continue;
                    }
                    String statementId = mapperClass.getName() + "." + method.getName();
                    if (!configuration.hasStatement(statementId, false)) {
                        issues.add("mapper=" + mapperClass.getName()
                                + ", factory=" + factoryName
                                + ", missing=" + statementId);
                    }
                }
            }
        }
        handleIssues(issues);
    }

    private static Method findOverride(Class<?> mapperClass, Method baseMethod) {
        try {
            return mapperClass.getDeclaredMethod(baseMethod.getName(), baseMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static boolean hasSqlAnnotation(Method method) {
        return method.isAnnotationPresent(Select.class)
                || method.isAnnotationPresent(Insert.class)
                || method.isAnnotationPresent(Update.class)
                || method.isAnnotationPresent(Delete.class);
    }

    private void handleIssues(List<String> issues) {
        if (issues.isEmpty()) {
            LOGGER.info("FW-MYSQL 存量 BaseDao Mapper 语句完整性校验通过（{} 个 SqlSessionFactory）",
                    sqlSessionFactories.size());
            return;
        }
        switch (properties.getMode()) {
            case WARN ->
                    LOGGER.warn("FW-MYSQL 存量 BaseDao Mapper 语句完整性校验发现问题（mode=warn，仅告警）：{}", issues);
            case FAIL -> {
                LOGGER.error("FW-MYSQL 存量 BaseDao Mapper 语句完整性校验失败（mode=fail），阻断启动：{}", issues);
                throw new IllegalStateException(
                        "FW-MYSQL 存量 BaseDao Mapper 语句完整性校验失败"
                                + "（iov.mysql.mapper-validation.mode=fail）: " + issues);
            }
            default -> {
                // OFF 已在上层返回，不会到达
            }
        }
    }
}
