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

    /**
     * 认证来源：0未认证 1学生证材料（人工审核）2微信学生认证（免材料）。
     *
     * <p>只用于展示与运营分析，是否认证仍以 {@link #verifyStatus} 为准。
     * 注意微信通道不返回姓名与学号，所以 {@code verifyStatus == 2} 不代表
     * {@link #realName} / {@link #studentNo} 有值。
     */
    private Integer verifySource;

    /** 微信学生身份是否已核验通过（0否 1是），与 verifySource=2 同事务写入 */
    private Integer wxStudentVerified;

    /** 微信返回的 bind_status：1未绑定 2审核中 3已绑定。留档用于申诉复核 */
    private Integer wxStudentStatus;

    /** 微信学生认证通过时间。微信侧毕业到期会解绑，这里只是我方通过时间 */
    private LocalDateTime wxStudentVerifyAt;

    /**
     * 最近一次提交认证的时间（首次提交与重认证都会写）。
     *
     * <p>只服务于「重认证冷却」：已认证用户想换学校 / 换材料时，两次提交之间
     * 必须间隔若干天，否则可以把认证当成「选校开关」反复刷，绕过同校区隔。
     * 用 {@code updated_at} 顶替不行——改昵称、换头像都会刷新它。
     */
    private LocalDateTime verifySubmitAt;

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
     * 令牌版本（t_user.token_version）：封禁 / 解封 / 注销 / 恢复时 +1，
     * 网关按它比对令牌里的 claim {@code tv}，旧令牌 60 秒内全部失效。
     *
     * <p>解决的是「JWT 无状态，封禁改库对已签发令牌毫无影响」——
     * 以前封禁一个号，他手里的令牌还能再发 72 小时内容。
     * 与 t_admin_user 的做法完全一致，见 {@code migration-20261028-user-token-revoke.sql}。
     */
    private Integer tokenVersion;

    /**
     * 禁言到期时间（t_user.mute_until）。由后台 campus-admin 通过
     * /internal/admin/users/{id}/mute 写入，本服务只持有这一列、不负责维护事实
     * —— 真正的禁言记录是 t_user_mute。NULL 表示未禁言。
     */
    private LocalDateTime muteUntil;

    /**
     * 注销申请时间；NULL = 正常账号。
     *
     * <p>刻意不复用 {@link #status}：那是「后台封禁」，解封流程与注销恢复完全不同，
     * 混用会让运营点一下「解封」就把注销账号放回来。注销期间账号不可用，
     * 但数据保留——满保留期（默认 365 天）才由定时任务清理个人信息。
     */
    private LocalDateTime cancelledAt;

    /**
     * 注销前的昵称。注销时昵称会被改成「已注销用户」让内容区显示为已注销，
     * 保留期内重新登录恢复账号时用它还原；满保留期清理时一并删除。
     */
    private String cancelNicknameBak;

    /** 注销前头像，恢复时还原（注销期间置空，避免用头像把人认出来） */
    private String cancelAvatarBak;

    /** 注销前个人简介，恢复时还原 */
    private String cancelBioBak;

    /** 个人信息清理完成时间（满保留期后）。NULL = 尚未清理；清理后 openid 会被占位替换，无法再恢复 */
    private LocalDateTime purgedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
