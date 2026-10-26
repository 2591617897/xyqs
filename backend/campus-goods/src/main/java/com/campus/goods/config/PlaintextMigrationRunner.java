package com.campus.goods.config;

import com.campus.common.crypto.DataCipher;
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
 * t_goods_order.contact（买家留下的 QQ/微信/手机号）历史明文迁移，幂等、默认关闭。
 *
 * <p>开启：{@code CAMPUS_SECURITY_MIGRATE_PLAINTEXT=true}，迁移完关闭。
 * 与 campus-user 的同名 runner 是同一套思路：应用侧加密，SQL 做不到。
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
        log.warn("[security] 开始迁移 t_goods_order.contact 明文数据");
        long lastId = 0;
        int total = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select id, contact from t_goods_order where id > ? order by id limit ?", lastId, BATCH);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                lastId = ((Number) row.get("id")).longValue();
                String contact = row.get("contact") == null ? null : String.valueOf(row.get("contact"));
                if (contact == null || contact.isEmpty() || DataCipher.isEncrypted(contact)) {
                    continue;
                }
                jdbcTemplate.update("update t_goods_order set contact = ? where id = ?",
                        DataCipher.encrypt(contact), lastId);
                total++;
            }
        }
        log.warn("[security] t_goods_order.contact 迁移完成，共更新 {} 行。请关闭 CAMPUS_SECURITY_MIGRATE_PLAINTEXT", total);
    }
}
