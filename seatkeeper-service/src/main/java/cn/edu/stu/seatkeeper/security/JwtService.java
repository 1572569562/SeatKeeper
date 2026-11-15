package cn.edu.stu.seatkeeper.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HS256 JWT 的签发与校验，只使用 JDK 与 Jackson，避免为一门课程项目引入额外依赖。
 *
 * <p>令牌载荷包含 {@code sub}（用户 id）、{@code role}、{@code iat} 与 {@code exp}。
 * 密钥来自 {@code app.jwt.secret}，必须至少 32 字节；校验失败一律返回空，由调用方转 401。
 */
@Component
public class JwtService {

    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param secret 签名密钥，来自配置，长度不足会在启动期直接失败
     * @param ttlHours 令牌有效期（小时）
     */
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl-hours:8}") long ttlHours) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 至少需要 32 字节");
        }
        if (ttlHours <= 0 || ttlHours > 168) {
            throw new IllegalStateException("app.jwt.ttl-hours 必须在 1..168 之间");
        }
        this.secret = bytes;
        this.ttlSeconds = ttlHours * 3600;
    }

    /**
     * 签发令牌。
     * @param userId 用户标识
     * @param role 角色
     * @return JWT 字符串
     */
    public String issue(long userId, String role) {
        long now = System.currentTimeMillis() / 1000;
        ObjectNode payload = mapper.createObjectNode();
        payload.put("sub", Long.toString(userId));
        payload.put("role", role);
        payload.put("iat", now);
        payload.put("exp", now + ttlSeconds);
        String header = ENCODER.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
            .getBytes(StandardCharsets.UTF_8));
        String body = ENCODER.encodeToString(toJson(payload));
        String signingInput = header + "." + body;
        return signingInput + "." + ENCODER.encodeToString(sign(signingInput));
    }

    /**
     * 校验签名与有效期。
     * @param token 客户端提交的令牌
     * @return 有效时返回载荷，否则为空
     */
    public Optional<Claims> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, DECODER.decode(parts[2]))) {
                return Optional.empty();
            }
            JsonNode payload = mapper.readTree(DECODER.decode(parts[1]));
            long exp = payload.path("exp").asLong(0);
            if (exp <= System.currentTimeMillis() / 1000) {
                return Optional.empty();
            }
            long userId = Long.parseLong(payload.path("sub").asText("0"));
            String role = payload.path("role").asText("");
            if (userId <= 0 || role.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new Claims(userId, role));
        } catch (RuntimeException | java.io.IOException error) {
            return Optional.empty();
        }
    }

    /** 令牌载荷：用户标识与角色。 */
    public record Claims(long userId, String role) { }

    /**
     * @param signingInput 待签名内容
     * @return HMAC-SHA256 签名
     */
    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("JWT 签名失败", error);
        }
    }

    /**
     * @param node JSON 节点
     * @return UTF-8 字节
     */
    private byte[] toJson(JsonNode node) {
        try {
            return mapper.writeValueAsBytes(node);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("JWT 载荷序列化失败", error);
        }
    }
}
