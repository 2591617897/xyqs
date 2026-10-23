package com.campus.user.controller;

import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传。文件落本地磁盘（生产建议替换为 COS / OSS），
 * 返回的 URL 已带网关地址，前端可直接放进 image 组件。
 */
@Slf4j
@RestController
public class UploadController {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @Value("${campus.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${campus.upload.base-url:http://127.0.0.1:8888}")
    private String baseUrl;

    @Value("${campus.upload.max-size:5242880}")
    private long maxSize;

    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片");
        }
        if (file.getSize() > maxSize) {
            throw new BizException("图片不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED.contains(ext)) {
            throw new BizException("只支持 jpg / png / gif / webp 格式");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File dir = new File(uploadDir, datePath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException("上传目录创建失败，请检查服务端权限");
        }
        try {
            file.transferTo(new File(dir, fileName).getAbsoluteFile());
        } catch (IOException e) {
            log.error("图片保存失败 dir={}", dir.getAbsolutePath(), e);
            throw new BizException("图片保存失败，请重试");
        }

        String base = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String url = base + "/uploads/" + datePath + "/" + fileName;
        log.info("图片上传成功 size={} url={}", file.getSize(), url);
        return R.ok(Map.of("url", url));
    }
}
