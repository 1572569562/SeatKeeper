package cn.edu.stu.seatkeeper.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * 密码哈希工具，使用 JDK 自带的 PBKDF2-HMAC-SHA256，不引入额外依赖。
 *
 * <p>存储格式为 {@code pbkdf2$迭代次数$盐$摘要}，盐与摘要均为 Base64。
 * 演示数据里的 {@code LOGIN_DISABLED_DEMO_ONLY} 不是合法格式，{@link #matches} 会直接判为不匹配。
 */
public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private PasswordHasher() { }

    /**
     * 生成可写入 {@code app_user.password_hash} 的字符串。
     * @param raw 明文密码
     * @return 带盐的哈希串
     */
    public static String hash(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] derived = derive(raw, salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
    }

    /**
     * 校验明文密码与存储的哈希是否匹配，使用常量时间比较避免时序侧信道。
     * @param raw 明文密码
     * @param stored 数据库中的哈希串
     * @return 匹配返回 true；格式非法或为空返回 false
     */
    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] expected = decoder.decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(raw, salt, iterations));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    /**
     * @param raw 明文密码
     * @param salt 盐
     * @param iterations 迭代次数
     * @return 派生的密钥字节
     */
    private static byte[] derive(String raw, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException error) {
            throw new IllegalStateException("当前 JDK 不支持 PBKDF2WithHmacSHA256", error);
        } finally {
            spec.clearPassword();
        }
    }
}
