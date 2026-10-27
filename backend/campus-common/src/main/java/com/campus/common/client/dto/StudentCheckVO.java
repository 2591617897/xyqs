package com.campus.common.client.dto;

/**
 * 微信学生身份核验结果。
 *
 * <p>字段与 {@code /intp/quickcheckstudentidentity} 的返回一一对应，不做语义加工：
 * 到底算不算认证通过由业务侧（campus-user）判断，避免两个服务各有一套口径。
 *
 * <p><b>微信侧只返回「是不是学生」</b>，不返回学校 / 姓名 / 学号 / 学历。
 * 所以认证通过后学校仍要用户自己选，姓名与学号在这条通道上就是空的。
 *
 * @param errcode   微信错误码，0 表示成功
 * @param errmsg    微信错误描述
 * @param bindStatus 绑定状态：1 未绑定 / 2 审核中 / 3 已绑定
 * @param isStudent 是否为学生（bindStatus=3 时才有意义）
 * @param mock      是否为本地 mock 结果（true 时业务侧仍按通过处理，但会打 warn）
 */
public record StudentCheckVO(Integer errcode, String errmsg, Integer bindStatus,
                             Boolean isStudent, boolean mock) {

    /** 认证通过的唯一口径：微信调用成功 + 已绑定 + 是学生 */
    public boolean passed() {
        return errcode != null && errcode == 0
                && bindStatus != null && bindStatus == 3
                && Boolean.TRUE.equals(isStudent);
    }
}
