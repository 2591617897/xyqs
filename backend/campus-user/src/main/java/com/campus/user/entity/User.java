package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.common.crypto.EncryptedStringTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * autoResultMap = true 是必须的：MyBatis-Plus 只有在开启它时，
 * 才会把 {@code @TableField(typeHandler = ...)} 写进生成的 ResultMap。
 * 少了这个属性，写入会走加密、读出来还是密文（真机上表现就是页面上显示 enc:v1:...）。
 */
@TableName(value = "t_user", autoResultMap = true)
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 宿主平台：wx 微信小程序 / qq QQ 小程序 */
    private String platform;

    /** 仅在 platform 内唯一，与 platform 组成联合唯一键 */
    private String openid;

    private String unionid;

    private String nickname;

    private String avatar;

    /** 0未知 1男 2女 */
    private Integer gender;

    /**
     * 手机号。落库为 AES-256-GCM 密文（enc:v1: 前缀），读取时由 TypeHandler 自动解密。
     * 业务代码拿到的永远是明文，SQL 里看到的永远是密文。
     */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String phone;

    /** 手机号盲索引：后台按号码精确搜索用（加密列没法 LIKE） */
    private String phoneHash;

    private Long schoolId;

    /** 学号：加密存储，另存盲索引 {@link #studentNoHash} 用于同校查重 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String studentNo;

    /**
     * 学号盲索引（HMAC-SHA256，密钥在应用侧）。
     *
     * <p>AES-GCM 每次加密结果不同，加密列没法直接等值查询；
     * 认证时「这个学号有没有被别人占用」只能靠这一列比对，见
     * {@code UserMapper#countStudentNoUsed}。
     */
    private String studentNoHash;

    /** 真实姓名：加密存储 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String realName;

    /** 0未认证 1审核中 2已认证 3驳回 */
    private Integer verifyStatus;

    /** 学生证 / 校园卡照片地址。认证材料，仅本人与后台可见 */
    private String verifyImg;

    private String verifyRemark;

    private String bio;

    /** 粉丝数（关注关系计数器，greatest 防负数） */
    private Integer fansCount;

    /** 关注数（关注关系计数器，greatest 防负数） */
    private Integer followCount;

    /** 1正常 0封禁 */
    private Integer status;

    /**
     * 禁言到期时间（t_user.mute_until）。由后台 campus-admin 通过
     * /internal/admin/users/{id}/mute 写入，本服务只持有这一列、不负责维护事实
     * —— 真正的禁言记录是 t_user_mute。NULL 表示未禁言。
     */
    private LocalDateTime muteUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
