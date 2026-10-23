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

    @NotBlank(message = "请上传学生证/校园卡照片")
    private String verifyImg;
}
