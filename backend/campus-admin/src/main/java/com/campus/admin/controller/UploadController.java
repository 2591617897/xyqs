package com.campus.admin.controller;

import com.campus.admin.config.annotation.RequirePermission;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 后台图片上传（广告素材、公告配图）。
 *
 * <p>返回的是可直接访问的完整 URL，不是相对路径——后台表单里要能立刻预览，
 * 前端不该再拼一遍域名。
 *
 * <p><b>目录必须与 campus-user 的 campus.upload.dir 指向同一处</b>：
 * 图片由网关 /uploads/** 路由到 campus-user 的静态资源映射提供，
 * 本服务只负责写文件、不做静态映射。两边目录不一致就会出现
 * 「上传 200、预览 404」——这也是本项目踩过的坑，所以这里落盘后立刻校验一次。
 */
@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @Value("${campus.admin.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${campus.admin.upload.base-url:http://127.0.0.1:8888}")
    private String baseUrl;

    @Value("${campus.admin.upload.max-size:5242880}")
    private long maxSize;

    @RequirePermission({"admin:ad:material:create", "admin:notice:create"})
    @PostMapping
    public R<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.of("请选择要上传的图片");
        }
        if (file.getSize() > maxSize) {
            throw BizException.of("图片不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED.contains(ext)) {
            throw BizException.of("只支持 jpg / png / gif / webp 格式");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        // 相对路径在这里是明确的：相对进程工作目录，而工作目录随启动方式变化，
        // 所以生产环境 UPLOAD_DIR 必须配绝对路径（见 application.yml 注释）
        Path baseDir = Path.of(uploadDir).toAbsolutePath().normalize();
        File dir = baseDir.resolve(datePath).toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw BizException.of("上传目录创建失败，请检查服务端权限");
        }
        File target = new File(dir, fileName).getAbsoluteFile();
        try {
            file.transferTo(target);
        } catch (IOException e) {
            log.error("图片保存失败 dir={}", dir.getAbsolutePath(), e);
            throw BizException.of("图片保存失败，请重试");
        }
        if (!target.isFile() || target.length() == 0) {
            log.error("图片落盘校验失败 path={} exists={}", target.getAbsolutePath(), target.isFile());
            throw BizException.of("图片保存失败，请重试");
        }

        String base = baseUrl == null ? "" : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        return R.ok(base + "/uploads/" + datePath + "/" + fileName);
    }
}
