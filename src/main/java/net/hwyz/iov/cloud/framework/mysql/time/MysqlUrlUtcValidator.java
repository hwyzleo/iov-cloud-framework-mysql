package net.hwyz.iov.cloud.framework.mysql.time;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MySQL JDBC URL 的 UTC 参数校验（FW-MYSQL-DSN-CR-001，RD-002）。
 * <p>
 * 对 {@code jdbc:mysql://host:port/db?k1=v1&k2=v2} 形式的 URL 做<b>解析式</b>校验，
 * 不做字符串包含判断。要求：
 * <ul>
 *   <li>{@code connectionTimeZone}（兼容旧参数 {@code serverTimezone}）存在且值为 UTC 等价
 *       （UTC / Etc/UTC / GMT / +00:00 / +0000 / Z，大小写不敏感）；</li>
 *   <li>{@code forceConnectionTimeZoneToSession=true}（大小写不敏感）；</li>
 *   <li>{@code preserveInstants=true}（大小写不敏感）；</li>
 *   <li>同一参数名不允许重复出现；</li>
 *   <li>{@code connectionTimeZone} 与 {@code serverTimezone} 同时出现且值不同视为冲突，拒绝。</li>
 * </ul>
 * 合法 URL 中仍可能存在未声明参数的未知行为（如驱动版本差异），故 URL 校验只是探针的一环，
 * 不替代真实连接会话校验。
 *
 * @author hwyz_leo
 * @since 2026-10-30
 */
final class MysqlUrlUtcValidator {

    private static final String PARAM_CONNECTION_TIMEZONE = "connectionTimeZone";
    private static final String PARAM_SERVER_TIMEZONE = "serverTimezone";
    private static final String PARAM_FORCE_SESSION = "forceConnectionTimeZoneToSession";
    private static final String PARAM_PRESERVE_INSTANTS = "preserveInstants";

    private static final Set<String> UTC_EQUIVALENT = new HashSet<>(Arrays.asList(
            "utc", "etc/utc", "gmt", "z", "+00:00", "+0000", "0"));

    private MysqlUrlUtcValidator() {
    }

    /**
     * 校验 JDBC URL；不合法时抛出 {@link IllegalArgumentException}，说明具体原因。
     *
     * @param url 如 {@code jdbc:mysql://127.0.0.1:3306/db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true}
     */
    static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("datasource URL 为空，无法校验 UTC 参数");
        }
        List<Param> params = parseParams(url);
        // 同一参数名重复出现：拒绝（避免驱动按最后一次/第一次取值的不确定行为）
        Set<String> seen = new HashSet<>();
        for (Param p : params) {
            if (!seen.add(p.name)) {
                throw new IllegalArgumentException("datasource URL 参数重复出现: " + p.name
                        + "（连接属性不确定，禁止放行）");
            }
        }
        String connectionTimeZone = valueOf(params, PARAM_CONNECTION_TIMEZONE);
        String serverTimezone = valueOf(params, PARAM_SERVER_TIMEZONE);
        if (connectionTimeZone != null && serverTimezone != null) {
            boolean a = utcEquivalent(connectionTimeZone);
            boolean b = utcEquivalent(serverTimezone);
            if (a != b) {
                throw new IllegalArgumentException("datasource URL 同时声明 connectionTimeZone 与 serverTimezone 且值不一致，冲突拒绝");
            }
        }
        String tz = connectionTimeZone != null ? connectionTimeZone : serverTimezone;
        if (tz == null || !utcEquivalent(tz)) {
            throw new IllegalArgumentException("datasource URL 缺少 UTC 时区参数：要求 connectionTimeZone=UTC"
                    + "（或兼容参数 serverTimezone=UTC），实际 URL: " + url);
        }
        requireTrue(params, PARAM_FORCE_SESSION, "forceConnectionTimeZoneToSession=true");
        requireTrue(params, PARAM_PRESERVE_INSTANTS, "preserveInstants=true");
    }

    private static void requireTrue(List<Param> params, String name, String expectation) {
        String value = valueOf(params, name);
        if (value == null || !isTrue(value)) {
            throw new IllegalArgumentException("datasource URL 缺少 " + expectation + "，禁止放行");
        }
    }

    private static String valueOf(List<Param> params, String name) {
        for (Param p : params) {
            if (p.name.equals(name)) {
                return p.value;
            }
        }
        return null;
    }

    private static boolean isTrue(String value) {
        return "true".equalsIgnoreCase(value.trim());
    }

    private static boolean utcEquivalent(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return UTC_EQUIVALENT.contains(normalized);
    }

    private static List<Param> parseParams(String url) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return List.of();
        }
        String query = url.substring(queryStart + 1);
        List<Param> result = new ArrayList<>();
        for (String segment : query.split("&")) {
            if (segment.isEmpty()) {
                continue;
            }
            String name;
            String value;
            int eq = segment.indexOf('=');
            if (eq < 0) {
                name = segment;
                value = "";
            } else {
                name = segment.substring(0, eq);
                value = segment.substring(eq + 1);
            }
            result.add(new Param(name, decode(value)));
        }
        return result;
    }

    private static String decode(String value) {
        try {
            return new URI("x://y/?" + value).getQuery();
        } catch (URISyntaxException e) {
            // 非标准编码保持原样，后续比较仍按大小写不敏感处理
            return value;
        }
    }

    private record Param(String name, String value) {
    }
}
