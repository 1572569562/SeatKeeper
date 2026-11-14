package com.seatkeeper.admin.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 客户端配置。优先级从高到低：
 * 1. 启动参数 -Dapp.server.baseUrl=...
 * 2. 环境变量 SEATKEEPER_SERVER
 * 3. classpath 里的 application.properties
 * 4. 代码里的默认值
 */
public final class AppConfig {

    public static final String DEFAULT_BASE_URL = "http://localhost:8080";
    public static final String DEFAULT_AUTH_MODE = "mock";

    private final String serverBaseUrl;
    private final String authMode;

    private AppConfig(String serverBaseUrl, String authMode) {
        this.serverBaseUrl = serverBaseUrl;
        this.authMode = authMode;
    }

    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 application.properties 失败", e);
        }
        return fromProperties(props);
    }

    /** 方便测试：直接给一份 Properties。 */
    public static AppConfig fromProperties(Properties props) {
        String baseUrl = firstNonBlank(
                System.getProperty("app.server.baseUrl"),
                System.getenv("SEATKEEPER_SERVER"),
                props.getProperty("app.server.baseUrl"),
                DEFAULT_BASE_URL);

        String mode = firstNonBlank(
                System.getProperty("app.auth.mode"),
                props.getProperty("app.auth.mode"),
                DEFAULT_AUTH_MODE);

        return new AppConfig(stripTrailingSlash(baseUrl.trim()), mode.trim().toLowerCase());
    }

    public String getServerBaseUrl() {
        return serverBaseUrl;
    }

    public String getAuthMode() {
        return authMode;
    }

    public boolean isMockAuth() {
        return !"http".equals(authMode);
    }

    /** 界面上显示用的一句话。 */
    public String describeMode() {
        return isMockAuth() ? "本地模拟（未连服务端）" : "真实接口 " + serverBaseUrl;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
