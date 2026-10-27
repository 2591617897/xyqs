package com.campus.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 校园身份认证：学校 + 学号 + 真实姓名 + 学生证照片，四项缺一不可。
 */
@Data
public class VerifyReq {

    @NotNull(message = "请选择所在学校")
    private Long schoolId;

    @NotBlank(message = "请填写学号")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "学号格式不正确")
    private String studentNo;

    @NotBlank(message = "请填写真实姓名")
    @Size(min = 2, max = 20, message = "姓名长度需在 2-20 字之间")
    private String realName;

    /**
     * 学生证 / 校园卡照片地址。必须是**服务端返回的 URL**，不能是本端临时路径。
     *
     * <p>曾经这里没有任何格式约束，小程序把 {@code wxfile://tmp_xxx.jpg}（微信选择图片返回的
     * 本机临时文件路径，只在当前进程生命周期有效）原样提交上来也被照单全收，写进
     * {@code t_user.verify_img} 之后：后台「用户详情 → 认证材料」用它在浏览器里渲染，
     * 结果永远是「加载失败」，审核员连材料都看不到，而且没人知道这条数据为什么坏。
     *
     * <p>这是最后一道防线（前端已先上传再提交），明确拒绝非 http(s) 地址，
     * 把「垃圾进」堵在边界上，别再让它落库。
     */
    @NotBlank(message = "请上传学生证/校园卡照片")
    @Pattern(regexp = "https?://.+", message = "认证材料上传失败，请重新上传照片")
    private String verifyImg;
}
