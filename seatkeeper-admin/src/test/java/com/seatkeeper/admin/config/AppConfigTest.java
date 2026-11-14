package com.seatkeeper.admin.config;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void readsValuesFromProperties() {
        Properties props = new Properties();
        props.setProperty("app.server.baseUrl", "http://192.168.1.21:8080/");
        props.setProperty("app.auth.mode", "http");

        AppConfig config = AppConfig.fromProperties(props);

        assertEquals("http://192.168.1.21:8080", config.getServerBaseUrl());
        assertFalse(config.isMockAuth());
        assertTrue(config.describeMode().startsWith("真实接口"));
    }

    @Test
    void defaultsToMockMode() {
        AppConfig config = AppConfig.fromProperties(new Properties());
        assertTrue(config.isMockAuth());
        assertEquals(AppConfig.DEFAULT_BASE_URL, config.getServerBaseUrl());
    }

    @Test
    void unknownModeFallsBackToMock() {
        Properties props = new Properties();
        props.setProperty("app.auth.mode", "something-else");
        assertTrue(AppConfig.fromProperties(props).isMockAuth());
    }
}
