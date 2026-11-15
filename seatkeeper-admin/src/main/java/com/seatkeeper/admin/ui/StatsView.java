package com.seatkeeper.admin.ui;

import com.seatkeeper.admin.domain.Stats;
import com.seatkeeper.admin.repo.StatsRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 统计看板（W6）。
 *
 * 三个数字 + 两个比率，接口 GET /api/v1/admin/stats?from&to。
 *
 * 口径：总数 = 区间内全部预约；已签到 = CHECKED_IN + COMPLETED；过期 = EXPIRED。
 * 爽约率依赖守护节点把超期预约置成 EXPIRED —— 那块还没人就位，
 * 所以页面上明确写了这个前提，不让人把 0 当成「没人爽约」。
 */
public final class StatsView {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    private final StatsRepository repository;
    private final boolean mockMode;

    private final VBox root = new VBox(14);
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final Button queryButton = new Button("查询");
    private final Button allButton = new Button("全部时间");
    private final Label statusLabel = new Label();

    private final Label totalValue = new Label("—");
    private final Label checkedValue = new Label("—");
    private final Label noShowValue = new Label("—");
    private final Label checkInRate = new Label("—");
    private final Label noShowRate = new Label("—");
    private final ProgressBar checkInBar = new ProgressBar(0);
    private final ProgressBar noShowBar = new ProgressBar(0);

    public StatsView(StatsRepository repository, boolean mockMode) {
        this.repository = repository;
        this.mockMode = mockMode;
        build();
        load();
    }

    public Parent getRoot() {
        return root;
    }

    private void build() {
        fromPicker.setPromptText("开始日期");
        fromPicker.setPrefWidth(130);
        toPicker.setPromptText("结束日期");
        toPicker.setPrefWidth(130);

        queryButton.getStyleClass().add("primary-button");
        queryButton.setOnAction(e -> load());

        allButton.getStyleClass().add("toolbar-button");
        allButton.setOnAction(e -> {
            fromPicker.setValue(null);
            toPicker.setValue(null);
            load();
        });

        HBox toolbar = new HBox(8, label("日期"), fromPicker, label("至"), toPicker,
                queryButton, allButton, statusLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        HBox cards = new HBox(12,
                statCard("预约总数", totalValue),
                statCard("已签到", checkedValue),
                statCard("已过期（爽约）", noShowValue));
        for (var node : cards.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        VBox rates = new VBox(12,
                rateRow("签到率", checkInRate, checkInBar, "#059669"),
                rateRow("爽约率", noShowRate, noShowBar, "#b91c1c"));
        rates.getStyleClass().add("sub-panel");
        rates.setMaxWidth(Double.MAX_VALUE);

        Label note = new Label(mockMode
                ? "当前是本地模拟数据，数字由假预约算出来，和「预约查询」页一致。"
                : "爽约率依赖守护节点把超期未签到的预约置成 EXPIRED；"
                + "守护节点没跑时这个数会偏小，甚至为 0——那是数据没被推进，不是统计算错。");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        note.getStyleClass().add("empty-text");

        root.getChildren().addAll(toolbar, cards, rates, note);
    }

    private VBox statCard(String caption, Label value) {
        value.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 26));
        value.setStyle("-fx-text-fill: #111827;");

        Label cap = new Label(caption);
        cap.getStyleClass().add("muted-small");

        VBox card = new VBox(6, value, cap);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private HBox rateRow(String caption, Label value, ProgressBar bar, String color) {
        Label cap = new Label(caption);
        cap.setPrefWidth(60);
        cap.getStyleClass().add("form-label");

        value.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 15));
        value.setStyle("-fx-text-fill: " + color + ";");
        value.setPrefWidth(70);

        bar.setPrefWidth(240);
        bar.setStyle("-fx-accent: " + color + ";");
        bar.getStyleClass().add("rate-bar");

        HBox row = new HBox(12, cap, bar, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void load() {
        OffsetDateTime from = startOfDay(fromPicker.getValue());
        OffsetDateTime to = endOfDayExclusive(toPicker.getValue());

        setBusy(true);
        statusLabel.setStyle("-fx-text-fill: #6b7280;");
        statusLabel.setText("加载中…");

        Async.run(() -> repository.load(from, to), stats -> {
            apply(stats);
            setBusy(false);
            statusLabel.setStyle("-fx-text-fill: #6b7280;");
            statusLabel.setText(stats.isEmpty() ? "区间内没有预约" : "已更新");
        }, error -> {
            setBusy(false);
            statusLabel.setStyle("-fx-text-fill: #b91c1c;");
            statusLabel.setText("加载失败：" + error.getMessage());
        });
    }

    private void apply(Stats stats) {
        totalValue.setText(String.valueOf(stats.totalReservations()));
        checkedValue.setText(String.valueOf(stats.checkedIn()));
        noShowValue.setText(String.valueOf(stats.noShow()));

        checkInRate.setText(stats.checkInPercent());
        noShowRate.setText(stats.noShowPercent());
        checkInBar.setProgress(clamp(stats.checkInRate()));
        noShowBar.setProgress(clamp(stats.noShowRate()));
    }

    private void setBusy(boolean busy) {
        queryButton.setDisable(busy);
        allButton.setDisable(busy);
        fromPicker.setDisable(busy);
        toPicker.setDisable(busy);
    }

    private static double clamp(double rate) {
        return Math.max(0, Math.min(1, rate));
    }

    private static OffsetDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay().atOffset(ZONE);
    }

    private static OffsetDateTime endOfDayExclusive(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay().atOffset(ZONE);
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("form-label");
        return l;
    }
}
