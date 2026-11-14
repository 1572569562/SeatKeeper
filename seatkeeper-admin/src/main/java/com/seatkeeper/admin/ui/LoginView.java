package com.seatkeeper.admin.ui;

import com.seatkeeper.admin.api.AuthService;
import com.seatkeeper.admin.api.HttpAuthService;
import com.seatkeeper.admin.api.LoginResult;
import com.seatkeeper.admin.api.MockAuthService;
import com.seatkeeper.admin.config.AppConfig;
import com.seatkeeper.admin.session.Session;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.Consumer;

/** 登录页。W1 的交付物就是它。 */
public final class LoginView {

    private final AppConfig config;
    private final Consumer<Session> onSuccess;
    private final AuthService authService;

    private final VBox root = new VBox(14);
    private final TextField accountField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton = new Button("登 录");
    private final Label statusLabel = new Label();

    public LoginView(AppConfig config, Consumer<Session> onSuccess) {
        this.config = config;
        this.onSuccess = onSuccess;
        this.authService = config.isMockAuth()
                ? new MockAuthService()
                : new HttpAuthService(config.getServerBaseUrl());
        build();
    }

    public Parent getRoot() {
        return root;
    }

    private void build() {
        Label title = new Label("SeatKeeper 管理端");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));

        Label subtitle = new Label("图书馆 / 自习室座位预约系统");
        subtitle.setStyle("-fx-text-fill: #6b7280;");

        accountField.setPromptText("管理员账号");
        accountField.setPrefHeight(36);
        passwordField.setPromptText("密码");
        passwordField.setPrefHeight(36);

        loginButton.setPrefWidth(Double.MAX_VALUE);
        loginButton.setPrefHeight(40);
        loginButton.setDefaultButton(true);
        loginButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white;"
                + " -fx-font-size: 14px; -fx-background-radius: 6;");
        loginButton.setOnAction(e -> doLogin());
        passwordField.setOnAction(e -> doLogin());

        statusLabel.setWrapText(true);
        statusLabel.setMinHeight(40);
        statusLabel.setStyle("-fx-text-fill: #b45309;");

        Label modeBadge = new Label(config.describeMode());
        modeBadge.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #4b5563;"
                + " -fx-padding: 3 8 3 8; -fx-background-radius: 10; -fx-font-size: 11px;");

        HBox modeRow = new HBox(modeBadge);
        modeRow.setAlignment(Pos.CENTER);

        root.getChildren().addAll(title, subtitle, new javafx.scene.control.Separator(),
                accountField, passwordField, loginButton, statusLabel);

        if (config.isMockAuth()) {
            Label hint = new Label("演示账号：admin / admin123\n学生账号 20230101 / 123456 会被拒绝");
            hint.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11px;");
            root.getChildren().add(hint);
        }
        root.getChildren().add(modeRow);

        root.setPadding(new Insets(36, 40, 28, 40));
        root.setAlignment(Pos.CENTER);
        root.setFillWidth(true);
        root.setStyle("-fx-background-color: white;");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setAlignment(Pos.CENTER);
        accountField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setMaxWidth(Double.MAX_VALUE);
    }

    private void doLogin() {
        String account = accountField.getText();
        String password = passwordField.getText();

        if (account == null || account.isBlank()) {
            showStatus("请输入账号", true);
            accountField.requestFocus();
            return;
        }
        if (password == null || password.isEmpty()) {
            showStatus("请输入密码", true);
            passwordField.requestFocus();
            return;
        }

        setBusy(true);
        showStatus("正在登录…", false);

        authService.login(account.trim(), password).whenComplete((result, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                showStatus("登录出错：" + error.getMessage(), true);
                return;
            }
            handle(result);
        }));
    }

    private void handle(LoginResult result) {
        if (result.success()) {
            statusLabel.setStyle("-fx-text-fill: #15803d;");
            statusLabel.setText("登录成功，正在进入…");
            onSuccess.accept(result.session());
        } else {
            showStatus(result.message(), true);
            passwordField.clear();
            passwordField.requestFocus();
        }
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        accountField.setDisable(busy);
        passwordField.setDisable(busy);
        loginButton.setText(busy ? "登录中…" : "登 录");
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setStyle("-fx-text-fill: " + (isError ? "#b91c1c" : "#6b7280") + ";");
        statusLabel.setText(message);
    }
}
