package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String openid;

    private String unionid;

    private String nickname;

    private String avatar;

    /** 0未知 1男 2女 */
    private Integer gender;

    private String phone;

    private Long schoolId;

    private String studentNo;

    private String realName;

    /** 0未认证 1审核中 2已认证 3驳回 */
    private Integer verifyStatus;

    private String verifyImg;

    private String verifyRemark;

    private String bio;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
