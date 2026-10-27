package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.dto.AdminUserInfo;
import com.campus.admin.dto.ChangePasswordReq;
import com.campus.admin.dto.LoginReq;
import com.campus.admin.dto.LoginResp;
import com.campus.admin.dto.MenuNode;
import com.campus.admin.dto.ResetPasswordByEmailReq;
import com.campus.admin.dto.SendResetCodeReq;
import com.campus.admin.dto.SendResetCodeResp;
import com.campus.admin.service.AdminPasswordResetService;
import com.campus.admin.service.AuthAdminService;
import com.campus.admin.service.MenuService;
import com.campus.common.api.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台认证与个人信息。
 *
 * <p>登录与找回密码由网关按「方法 + 路径」精确放行（详见 AuthGlobalFilter 的 ADMIN_PUBLIC_POST），
 * 其余接口都要带管理员令牌。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthAdminService authAdminService;
    private final AdminPasswordResetService passwordResetService;
    private final MenuService menuService;

    /**
     * 找回密码第一步：向该账号绑定的邮箱发送 6 位验证码。
     *
     * <p><b>未登录接口</b>，网关按「POST + 精确路径」放行，见 AuthGlobalFilter#ADMIN_PUBLIC_POST。
     * 收件邮箱不接受前端传值——只发库里绑定的那个，否则就成了任意邮箱收码 + 任意账号改密。
     */
    @AuditLog(value = "邮箱找回密码-发送验证码", module = "AUTH", saveParams = false)
    @PostMapping("/find-password/code")
    public R<SendResetCodeResp> sendResetCode(@RequestBody @Valid SendResetCodeReq req) {
        return R.ok(passwordResetService.sendCode(req));
    }

    /**
     * 找回密码第二步：用验证码换新密码。成功后该账号所有旧令牌立即失效。
     */
    @AuditLog(value = "邮箱找回密码-重置密码", module = "AUTH", saveParams = false)
    @PostMapping("/find-password/reset")
    public R<Void> resetPassword(@RequestBody @Valid ResetPasswordByEmailReq req) {
        passwordResetService.resetPassword(req);
        return R.ok();
    }

    @AuditLog(value = "管理员登录", module = "AUTH", saveParams = false)
    @PostMapping("/login")
    public R<LoginResp> login(@RequestBody @Valid LoginReq req) {
        return R.ok(authAdminService.login(req));
    }

    @AuditLog(value = "退出管理后台", module = "AUTH")
    @PostMapping("/logout")
    public R<Void> logout() {
        // 令牌本身无状态，服务端不需要黑名单；前端负责清掉本地 token
        return R.ok();
    }

    @GetMapping("/info")
    public R<AdminUserInfo> info() {
        return R.ok(authAdminService.info());
    }

    /**
     * 当前管理员可见的菜单树，前端既用它渲染侧边栏，也用它注册动态路由。
     */
    @GetMapping("/menus")
    public R<List<MenuNode>> menus() {
        return R.ok(menuService.treeOfCurrentAdmin());
    }

    /**
     * 修改自己的登录密码。
     *
     * <p>改密会让所有旧令牌失效（token_version +1），所以这里返回一张新令牌，
     * 前端拿到后直接替换本地存储，用户不会被自己的改密操作踢出去。
     */
    @AuditLog(value = "修改登录密码", module = "AUTH", saveParams = false)
    @PostMapping("/password")
    public R<String> password(@RequestBody @Valid ChangePasswordReq req) {
        return R.ok(authAdminService.changePassword(req));
    }
}
