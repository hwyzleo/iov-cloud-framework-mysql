package ${package.Mapper};

import ${package.Entity}.${entity};
import ${superMapperClassPackage};
<#if mapperAnnotationClass??>
import ${mapperAnnotationClass.name};
</#if>

/**
 * <p>
 * ${table.comment!} DAO
 * </p>
 *
 * @author ${author}
 * @since ${date}
 */
<#if mapperAnnotationClass??>
@${mapperAnnotationClass.simpleName}
</#if>
<#if kotlin>
interface ${table.mapperName} : ${superMapperClass}<${entity}, ${id}>
<#else>
<#-- FW-MYSQL-DSN-CR-003：新标准 superClass=BaseMapper（单泛型）；legacy 显式 superClass=BaseDao（双泛型）
     superMapperClassPackage 为全限定类名（MP 模型），superMapperClass 为简单类名 -->
<#if superMapperClassPackage == "com.baomidou.mybatisplus.core.mapper.BaseMapper">
public interface ${table.mapperName} extends ${superMapperClass}<${entity}> {

}
<#else>
public interface ${table.mapperName} extends ${superMapperClass}<${entity}, Long> {

}
</#if>
</#if>
