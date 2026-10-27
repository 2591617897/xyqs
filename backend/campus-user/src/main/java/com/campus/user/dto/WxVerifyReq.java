package com.campus.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 微信学生身份认证入参。
 *
 * <p>为什么不带学号/姓名：微信的 quickcheckstudentidentity 只返回
 * 「是不是学生」，姓名与学号都不给。落库时这两列保持为空，
 * 页面不要假设「已认证」就等于「有学号」。
 *
 * <p>为什么还要学校：接口同样不返回学校。学校是本站的内容分区维度
 * （同校/同城筛选），必须由用户自己选，且必须是字典里的有效学校。
 */
@Data
public class WxVerifyReq {

    @NotNull(message = "请选择所在学校")
    private Long schoolId;

    /** 授权插件 bind:auth 回调里的 wx_studentcheck_code，两小时有效、一次性 */
    @NotBlank(message = "未获取到微信认证凭证，请重新发起认证")
    private String code;
}
