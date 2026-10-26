package com.campus.user.config;

import com.campus.common.crypto.DataCipher;
import com.campus.common.crypto.SearchHash;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 历史明文数据迁移（一次性任务，默认关闭）。
 *
 * <p>字段加密上线后，<b>新写入</b>的手机号/学号/姓名自动是密文，<b>老数据</b>还是明文。
 * 读取侧对无 {@code enc:v1:} 前缀的值按原样返回，所以不迁移也能跑；
 * 但只要不迁移，库里就一直是「一半明文一半密文」，等于加密白做。
 *
 * <p>开启方式（迁移完记得关掉）：
 * <pre>
 *   CAMPUS_SECURITY_MIGRATE_PLAINTEXT=true  # 或 --campus.security.migrate-plaintext=true
 * </pre>
 *
 * <p>为什么不写成 SQL 脚本：AES-GCM 需要应用侧密钥，MySQL 里算不出来。
 * 这个 runner 逐批（500 行）读原始列 → 加密 → 回写，可重复执行（已加密的行会跳过）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.security.migrate-plaintext", havingValue = "true")
public class PlaintextMigrationRunner implements ApplicationRunner {

    private static final int BATCH = 500;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.warn("[security] 开始迁移 t_user 明文敏感字段（手机号 / 学号 / 真实姓名）");
        long lastId = 0;
        int total = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id, school_id, phone, student_no, real_name, phone_hash, student_no_hash "
                            + "from t_user where id > ? order by id limit ?", lastId, BATCH);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                lastId = ((Number) row.get("id")).longValue();
                if (migrateRow(row)) {
                    total++;
                }
            }
            log.info("[security] t_user 已处理 {} 行，当前进度 id={}", total, lastId);
        }
        log.warn("[security] t_user 明文迁移完成，共更新 {} 行。请关闭 CAMPUS_SECURITY_MIGRATE_PLAINTEXT", total);
    }

    private boolean migrateRow(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        String phone = str(row.get("phone"));
        String studentNo = str(row.get("student_no"));
        String realName = str(row.get("real_name"));
        String phoneHash = str(row.get("phone_hash"));
        String studentNoHash = str(row.get("student_no_hash"));
        Object schoolId = row.get("school_id");

        boolean dirty = false;
        if (needEncrypt(phone)) {
            dirty = true;
        }
        if (needEncrypt(studentNo)) {
            dirty = true;
        }
        if (needEncrypt(realName)) {
            dirty = true;
        }
        // 缺盲索引的历史行，即使已经加密也要补（否则同校查重会漏）
        String newPhoneHash = phoneHash;
        if (phone != null && !phone.isEmpty() && (phoneHash == null || phoneHash.isEmpty())) {
            newPhoneHash = SearchHash.of("phone", DataCipher.decrypt(phone));
            dirty = true;
        }
        String newStudentNoHash = studentNoHash;
        if (studentNo != null && !studentNo.isEmpty() && (studentNoHash == null || studentNoHash.isEmpty())) {
            newStudentNoHash = SearchHash.of(String.valueOf(schoolId), DataCipher.decrypt(studentNo));
            dirty = true;
        }
        if (!dirty) {
            return false;
        }
        jdbcTemplate.update("update t_user set phone = ?, student_no = ?, real_name = ?, "
                        + "phone_hash = ?, student_no_hash = ? where id = ?",
                DataCipher.encrypt(DataCipher.decrypt(phone)),
                DataCipher.encrypt(DataCipher.decrypt(studentNo)),
                DataCipher.encrypt(DataCipher.decrypt(realName)),
                newPhoneHash, newStudentNoHash, id);
        return true;
    }

    private boolean needEncrypt(String raw) {
        return raw != null && !raw.isEmpty() && !DataCipher.isEncrypted(raw);
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
