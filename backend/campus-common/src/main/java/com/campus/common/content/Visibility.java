package com.campus.common.content;

/**
 * 内容可见范围（闲置 / 帖子通用）。
 *
 * 发布时由作者选定，是**内容自己的属性**：
 * - {@link #SCHOOL}：仅本校可见（默认值，也是安全值）
 * - {@link #CITY}  ：同城所有高校可见
 *
 * ⚠️ 别把它和列表接口的 scope 参数搞混：
 *    scope 是「浏览范围」，描述浏览者以什么身份在逛（本校 / 本市）；
 *    visibility 是「可见范围」，描述这条内容允许被谁看到。
 *    两个概念同名（都叫 scope）会让那段 OR 嵌套查询写得极易出错，所以内容字段一律叫 visibility。
 *
 * 过滤规则（列表查询，两个服务一致）：
 * <pre>
 *   浏览本校：school_id = 我的学校
 *             —— 本校内容无论可见范围如何都能看（本校 ⊂ 本市）
 *   浏览本市：school_id IN (同城高校) AND (visibility = 'city' OR school_id = 我的学校)
 *             —— 他校内容只有对方主动放开到「同市」才看得到
 * </pre>
 *
 * 默认值取 school 是有意的收紧：新功能上线后老内容也不会在他校的同城流里意外曝光，
 * 而对本校用户没有任何影响。
 *
 * @since 2026-10-28
 */
public final class Visibility {

    /** 仅本校可见（默认） */
    public static final String SCHOOL = "school";

    /** 同城所有高校可见 */
    public static final String CITY = "city";

    private Visibility() {
    }

    /**
     * 归一化：只认 school / city，其它值（含 null、空串、大小写变体）一律按 school 处理。
     * 取默认值而不是抛异常，是为了不让旧客户端 / 脏参数直接把发布请求打回 400。
     */
    public static String normalize(String v) {
        return CITY.equalsIgnoreCase(v) ? CITY : SCHOOL;
    }

    /** 是否同城可见 */
    public static boolean isCity(String v) {
        return CITY.equalsIgnoreCase(v);
    }
}
