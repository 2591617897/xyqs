package com.campus.common.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 敏感字段透明加解密。
 *
 * <p>只在需要的字段上通过 {@code @TableField(typeHandler = EncryptedStringTypeHandler.class)}
 * 或注解 SQL 的 {@code @Result(typeHandler = ...)} 显式指定，<b>不要</b>配到
 * {@code mybatis-plus.type-handlers-package} 全局注册 —— 那会把所有 String 列都加密，
 * 包括 nickname / content，查询与全文检索立刻全废。
 *
 * <p>写入统一加密；读取时无 {@code enc:v1:} 前缀的历史明文原样返回，
 * 因此可以在不停机的情况下逐行升级（见 {@code PlaintextMigrationRunner}）。
 */
@MappedTypes(String.class)
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, DataCipher.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return DataCipher.decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return DataCipher.decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return DataCipher.decrypt(cs.getString(columnIndex));
    }
}
