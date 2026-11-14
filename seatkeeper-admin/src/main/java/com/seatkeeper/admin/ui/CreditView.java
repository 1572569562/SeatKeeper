package com.seatkeeper.admin.ui;

import com.seatkeeper.admin.domain.Credit;
import com.seatkeeper.admin.repo.CreditRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Optional;

/**
 * 信用管理页（W5）。
 *
 * 契约里只有「按 userId 查快照」和「按 userId 调整」两个接口，没有按学号查用户的接口，
 * 所以这里要填用户 ID。用户 ID 一般从预约行拿（预约查询页的「信用」按钮会自动带过来）。
 */
public final class CreditView {

    private final CreditRepository repository;

    private final VBox root = new VBox(14);
    private final TextField userIdField = new TextField();
    private final Button queryButton = new Button("查询");
    private final Button adjustButton = new Button("调整信用");
    private final Label statusLabel = new Label();

    private final Label scoreLabel = new Label("—");
    private final Label levelLabel = new Label("");
    private final Label restrictedLabel = new Label("");
    private final Label versionLabel = new Label("");

    private Long currentUserId;

    private final boolean mockMode;

    public CreditView(CreditRepository repository, boolean mockMode) {
        this.repository = repository;
        this.mockMode = mockMode;
        build();
    }

    public Parent getRoot() {
        return root;
    }

    private void build() {
        userIdField.setPromptText("用户 ID（可从预约行获取）");
        userIdField.setPrefWidth(220);
        userIdField.setOnAction(e -> load());

        queryButton.getStyleClass().add("primary-button");
        queryButton.setOnAction(e -> load());

        adjustButton.getStyleClass().add("toolbar-button");
        adjustButton.setDisable(true);
        adjustButton.setOnAction(e -> openAdjust());

        HBox toolbar = new HBox(8, label("用户 ID"), userIdField, queryButton, adjustButton, statusLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        scoreLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 34));
        scoreLabel.setStyle("-fx-text-fill: #111827;");

        levelLabel.getStyleClass().add("badge");
        levelLabel.setVisible(false);

        restrictedLabel.getStyleClass().add("page-desc");
        versionLabel.getStyleClass().add("muted-small");

        HBox scoreRow = new HBox(10, scoreLabel, label("分"), levelLabel);
        scoreRow.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(8, scoreRow, restrictedLabel, versionLabel);
        panel.getStyleClass().add("sub-panel");
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        panel.setMinHeight(Region.USE_PREF_SIZE);

        Label hint = new Label(mockMode
                ? "本地模拟数据：学号 20230101 / 20230102 / 20230103 / 20230104 对应用户 ID 5001 / 5002 / 5003 / 5004"
                : "用户 ID 一般从预约查询页的「信用」按钮带过来。");
        hint.getStyleClass().add("empty-text");
        hint.setWrapText(true);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        root.getChildren().addAll(toolbar, panel, hint, spacer);
    }

    private void load() {
        Long userId = parseLong(userIdField.getText());
        if (userId == null) {
            showError("用户 ID 必须是数字");
            return;
        }
        setBusy("查询中…");
        Async.run(() -> repository.get(userId), credit -> {
            currentUserId = credit.userId();
            apply(credit);
            adjustButton.setDisable(false);
            statusLabel.setStyle("-fx-text-fill: #6b7280;");
            statusLabel.setText("");
        }, this::showError);
    }

    private void apply(Credit credit) {
        scoreLabel.setText(String.valueOf(credit.score()));
        levelLabel.setVisible(true);
        levelLabel.setText(credit.levelText());
        levelLabel.getStyleClass().setAll("badge",
                credit.score() < 60 ? "badge-mock" : "badge");
        restrictedLabel.setText("预约限制：" + credit.restrictedText());
        versionLabel.setText("乐观锁版本：" + credit.version());
    }

    private void openAdjust() {
        if (currentUserId == null) {
            showError("请先查询一个用户");
            return;
        }
        Optional<AdminDialogs.CreditInput> input =
                AdminDialogs.askCreditAdjust(window(), currentUserId, null);
        if (input.isEmpty()) {
            return;
        }
        AdminDialogs.CreditInput in = input.get();
        setBusy("正在调整…");
        Async.run(() -> repository.adjust(in.userId(), in.reservationId(), in.delta(), in.reason()),
                credit -> {
                    apply(credit);
                    statusLabel.setStyle("-fx-text-fill: #059669;");
                    statusLabel.setText("已调整为 " + credit.score() + " 分");
                },
                this::showError);
    }

    private void setBusy(String message) {
        queryButton.setDisable(true);
        adjustButton.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: #6b7280;");
        statusLabel.setText(message);
    }

    private void showError(String message) {
        queryButton.setDisable(false);
        statusLabel.setStyle("-fx-text-fill: #b91c1c;");
        statusLabel.setText(message);
    }

    private void showError(Throwable error) {
        queryButton.setDisable(false);
        adjustButton.setDisable(currentUserId == null);
        statusLabel.setStyle("-fx-text-fill: #b91c1c;");
        statusLabel.setText("操作失败：" + error.getMessage());
    }

    private javafx.stage.Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("form-label");
        return l;
    }

    private static Long parseLong(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
