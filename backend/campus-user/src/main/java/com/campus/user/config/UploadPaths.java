package com.campus.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传目录解析。
 *
 * `campus.upload.dir` 常写成相对路径 `./uploads`，而相对路径是相对**进程工作目录**解析的，
 * 不是相对 jar 所在目录 —— 换个启动方式（systemd 的 WorkingDirectory、`cd` 后 `java -jar`、
 * `nohup` 从别的目录启动）指向就变了，于是出现「上传写 A 目录、静态资源读 B 目录」，
 * 表现为上传 200、图片访问 404，极难排查。
 *
 * 这里统一规则，保证 {@code UploadController}（写）和 {@code WebConfig}（读）**永远算同一个目录**：
 * <ul>
 *   <li>绝对路径：直接使用</li>
 *   <li>相对路径 + 以 jar 方式运行：按 <b>jar 所在目录</b>解析（部署时 jar 与 uploads 同级即可）</li>
 *   <li>相对路径 + IDE/classes 运行：退回进程工作目录，避免把文件写进 target/</li>
 * </ul>
 */
public final class UploadPaths {

    private static final Logger log = LoggerFactory.getLogger(UploadPaths.class);

    private UploadPaths() {
    }

    /**
     * @param configured 配置值（可能是相对路径）
     * @return 绝对路径（已规范化）
     */
    public static Path resolve(String configured) {
        String value = configured == null || configured.isBlank() ? "./uploads" : configured.trim();
        Path p = Paths.get(value);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        try {
            URL location = UploadPaths.class.getProtectionDomain().getCodeSource().getLocation();
            Path self = Paths.get(location.toURI());
            if (self.toString().endsWith(".jar")) {
                Path base = self.getParent();
                Path dir = base.resolve(value).normalize();
                log.info("[upload] 相对路径 {} 按 jar 所在目录解析 -> {}", value, dir);
                return dir;
            }
        } catch (Exception e) {
            log.warn("[upload] 解析 jar 位置失败，退回进程工作目录: {}", e.getMessage());
        }
        return p.toAbsolutePath().normalize();
    }
}
