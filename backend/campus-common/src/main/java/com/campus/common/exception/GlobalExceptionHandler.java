package com.campus.common.exception;

import com.campus.common.api.R;
import lombok.extern.slf4j.Slf4j;
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

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("服务器异常", e);
        // 开发环境返回根因 message，方便定位；生产环境（R.fail 统一文案）不暴露
        String devMsg = e.getCause() != null && e.getCause().getMessage() != null
                ? e.getCause().getMessage() : e.getMessage();
        return R.fail(500, "服务器开小差了: " + (devMsg == null ? "" : devMsg.substring(0, Math.min(200, devMsg.length()))));
    }
}
