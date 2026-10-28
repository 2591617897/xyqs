package com.campus.common.exception;

import com.campus.common.api.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 是否把异常根因写进响应体。仅本地联调可开，生产必须保持 false
     * （{@code CAMPUS_ERROR_EXPOSE_DETAIL=true} 才开启，默认关闭）。
     */
    @Value("${campus.error.expose-detail:false}")
    private boolean exposeDetail;

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValid(BindException e) {
        FieldError error = e.getBindingResult().getFieldError();
        String msg = error == null ? "参数不合法" : error.getDefaultMessage();
        return R.fail(400, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegal(IllegalArgumentException e) {
        return R.fail(400, e.getMessage());
    }

    /**
     * 静态资源未命中（Spring Boot 3.x 抛 NoResourceFoundException）。
     * 必须单独处理：它继承自 ServletException，会被下面的兜底分支吞成 500，
     * 前端把 500 的 JSON 当图片渲染 → 图片位置显示灰块，且因 HTTP 200 连 binderror 都不触发。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        log.warn("静态资源不存在: {}", e.getResourcePath());
        return R.fail(404, "资源不存在");
    }

    /**
     * 上传文件超过 multipart 上限。不单独处理会被兜底分支吞成 500「服务器开小差了」，
     * 用户完全看不出是图片太大（真机原图很容易超 5MB）。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return R.fail(400, "图片太大，请换一张或截图后重试");
    }

    /**
     * 兜底异常。
     *
     * <p><b>为什么默认不返回异常根因</b>：以前这里无条件把 {@code e.getCause().getMessage()}
     * 拼进响应体，注释却写着「生产环境不暴露」——注释与实现不一致，等于把内部细节
     * 直接吐给公网。实际会泄漏这些东西：
     * <ul>
     *   <li>SQL 异常 → 表名 / 列名 / 唯一键名，甚至 {@code Duplicate entry '138xxxx' for key} 里的手机号；</li>
     *   <li>NPE → 内部类名 + 行号，直接暴露代码结构；</li>
     *   <li>文件异常 → 服务器绝对路径（如 {@code /mnt/ssd/web/...}）。</li>
     * </ul>
     *
     * <p>现在改成显式开关 {@code campus.error.expose-detail}，默认 {@code false}：
     * 细节一律只进日志（带 URI，方便按 traceId 对齐），响应体固定一句「服务器开小差了，请稍后重试」。
     * 本地联调想看根因，设环境变量 {@code CAMPUS_ERROR_EXPOSE_DETAIL=true} 即可。
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e, HttpServletRequest request) {
        log.error("服务器异常 uri={} method={}", request.getRequestURI(), request.getMethod(), e);
        if (!exposeDetail) {
            return R.fail(500, "服务器开小差了，请稍后重试");
        }
        String devMsg = e.getCause() != null && e.getCause().getMessage() != null
                ? e.getCause().getMessage() : e.getMessage();
        return R.fail(500, "服务器开小差了: " + (devMsg == null ? "" : devMsg.substring(0, Math.min(200, devMsg.length()))));
    }
}
