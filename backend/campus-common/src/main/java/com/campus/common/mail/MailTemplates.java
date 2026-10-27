package com.campus.common.mail;

/**
 * 邮件模板编码。
 *
 * <p>放在 common 是为了让投递方（各业务服务）不用依赖 campus-mail 这个实现模块 ——
 * 业务服务只认「模板编码」这个契约，具体排版怎么变都在邮件服务内部。
 * 新增模板时：这里加常量 + campus-mail 的 MailTemplateRegistry 加一个 case。
 */
public final class MailTemplates {

    private MailTemplates() {
    }

    /** 后台找回密码验证码；params: username / code / expireMinutes / ip */
    public static final String ADMIN_RESET_CODE = "ADMIN_RESET_CODE";

    /** 通用兜底；params: subject / content */
    public static final String GENERIC = "GENERIC";
}
