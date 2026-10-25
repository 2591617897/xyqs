package com.campus.user.controller;

import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.user.config.UploadPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
    /** 聊天视频消息支持的视频格式 */
    private static final Set<String> ALLOWED_VIDEO = Set.of("mp4", "mov", "m4v");

    @Value("${campus.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${campus.upload.base-url:http://127.0.0.1:8888}")
    private String baseUrl;

    @Value("${campus.upload.max-size:5242880}")
    private long maxSize;

    /** 视频上限（聊天视频消息），默认 50MB；需小于 multipart 上限 */
    @Value("${campus.upload.video-max-size:52428800}")
    private long videoMaxSize;

    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        boolean isVideo = ALLOWED_VIDEO.contains(ext);

        long limit = isVideo ? videoMaxSize : maxSize;
        if (file.getSize() > limit) {
            throw new BizException(isVideo
                    ? "视频不能超过 " + (limit / 1024 / 1024) + "MB"
                    : "图片不能超过 " + (limit / 1024 / 1024) + "MB");
        }
        if (!isVideo && !ALLOWED.contains(ext)) {
            throw new BizException("只支持 jpg / png / gif / webp 图片或 mp4 / mov 视频");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        // 与 WebConfig 用同一套解析规则，保证「写到哪」和「从哪读」永远是同一个目录
        Path baseDir = UploadPaths.resolve(uploadDir);
        File dir = baseDir.resolve(datePath).toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException("上传目录创建失败，请检查服务端权限");
        }
        try {
            File target = new File(dir, fileName).getAbsoluteFile();
            file.transferTo(target);
            // 落盘后立刻确认：写成功但路径和静态资源映射不一致时，
            // 会出现「上传 200、图片 404」这种极难排查的现象，这里直接暴露出来
            if (!target.isFile() || target.length() == 0) {
                log.error("图片落盘校验失败 path={} exists={}", target.getAbsolutePath(), target.isFile());
                throw new BizException("图片保存失败，请重试");
            }
        } catch (IOException e) {
            log.error("图片保存失败 dir={}", dir.getAbsolutePath(), e);
            throw new BizException("图片保存失败，请重试");
        }

        String base = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String url = base + "/uploads/" + datePath + "/" + fileName;
        log.info("文件上传成功 type={} size={} url={}", isVideo ? "video" : "image", file.getSize(), url);
        return R.ok(Map.of("url", url));
    }
}
