package com.seatkeeper.admin.ui;

import com.seatkeeper.admin.domain.AdminReservation;
import com.seatkeeper.admin.domain.PageResult;
import com.seatkeeper.admin.domain.ReservationFilter;
import com.seatkeeper.admin.domain.ReservationStatus;
import com.seatkeeper.admin.repo.CreditRepository;
import com.seatkeeper.admin.repo.ReservationRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Function;

/**
 * 预约查询页（W4 查询 + W5 管理动作）。
 *
 * 筛选：学号（模糊）/ 座位 ID / 状态 / 日期区间，四种条件可任意组合。
 * 每行可以「强制取消」（必须填原因）和「信用调整」（必须填原因、绑定该预约）。
 */
public final class ReservationQueryView {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);
    private static final int[] PAGE_SIZES = {10, 20, 50};

    private final ReservationRepository repository;
    private final CreditRepository creditRepository;

    private final VBox root = new VBox(12);
    private final TextField studentNoField = new TextField();
    private final TextField seatIdField = new TextField();
    private final ComboBox<ReservationStatus> statusBox = new ComboBox<>();
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final TableView<AdminReservation> table = new TableView<>();
    private final ObservableList<AdminReservation> rows = FXCollections.observableArrayList();

    private final Button queryButton = new Button("查询");
    private final Button resetButton = new Button("重置");
    private final Button prevButton = new Button("上一页");
    private final Button nextButton = new Button("下一页");
    private final ComboBox<Integer> sizeBox = new ComboBox<>();
    private final Label pageLabel = new Label();
    private final Label statusLabel = new Label();

    private int page = 1;
    private int size = 20;
    private boolean hasMore;

    /** 动作完成后想在底部显示的一句话，等下一次查询刷新时一起显示 */
    private String pendingMessage;

    public ReservationQueryView(ReservationRepository repository, CreditRepository creditRepository) {
        this.repository = repository;
        this.creditRepository = creditRepository;
        build();
        query();
    }

    public Parent getRoot() {
        return root;
    }

    // ---------------- 界面 ----------------

    private void build() {
        root.getChildren().addAll(buildFilterBar(), buildTable(), buildFooter());
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private FlowPane buildFilterBar() {
        studentNoField.setPromptText("学号");
        studentNoField.setPrefWidth(120);
        studentNoField.setMinWidth(100);

        seatIdField.setPromptText("座位 ID");
        seatIdField.setPrefWidth(90);
        seatIdField.setMinWidth(80);

        statusBox.getItems().add(null);
        statusBox.getItems().addAll(ReservationStatus.values());
        statusBox.setConverter(new NullableStatusConverter());
        statusBox.getSelectionModel().selectFirst();
        statusBox.setPrefWidth(100);

        fromPicker.setPromptText("开始日期");
        fromPicker.setPrefWidth(120);
        toPicker.setPromptText("结束日期");
        toPicker.setPrefWidth(120);

        queryButton.getStyleClass().add("primary-button");
        queryButton.setMinWidth(64);
        queryButton.setOnAction(e -> {
            page = 1;
            query();
        });

        resetButton.getStyleClass().add("toolbar-button");
        resetButton.setMinWidth(56);
        resetButton.setOnAction(e -> reset());

        // 输入框回车也能查
        studentNoField.setOnAction(e -> {
            page = 1;
            query();
        });
        seatIdField.setOnAction(e -> {
            page = 1;
            query();
        });

        // 用 FlowPane 而不是 HBox：窗口窄的时候自动换行，不会把控件挤成「...」
        FlowPane bar = new FlowPane(8, 8,
                label("学号"), studentNoField,
                label("座位ID"), seatIdField,
                label("状态"), statusBox,
                label("日期"), fromPicker, label("至"), toPicker,
                queryButton, resetButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setRowValignment(javafx.geometry.VPos.CENTER);
        return bar;
    }

    private TableView<AdminReservation> buildTable() {
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("没有符合条件的预约"));

        table.getColumns().addAll(
                column("预约号", 90, r -> String.valueOf(r.id())),
                column("学号", 110, AdminReservation::studentNo),
                column("姓名", 90, AdminReservation::studentName),
                column("座位", 160, AdminReservation::seatText),
                column("时段", 150, AdminReservation::slotText),
                column("签到时间", 110, AdminReservation::checkedInText),
                statusColumn(),
                actionColumn());
        return table;
    }

    /** 操作列：强制取消 / 信用调整（两个都要填原因） */
    private TableColumn<AdminReservation, Void> actionColumn() {
        TableColumn<AdminReservation, Void> col = new TableColumn<>("操作");
        col.setPrefWidth(130);
        col.setSortable(false);
        col.setCellFactory(c -> new TableCell<>() {
            private final Button cancelButton = new Button("取消");
            private final Button creditButton = new Button("信用");
            private final HBox box = new HBox(4, cancelButton, creditButton);

            {
                cancelButton.getStyleClass().add("link-button");
                creditButton.getStyleClass().add("link-button");
                box.setAlignment(Pos.CENTER_LEFT);

                cancelButton.setOnAction(e -> {
                    AdminReservation row = rowOf(this);
                    if (row != null) {
                        forceCancel(row);
                    }
                });
                creditButton.setOnAction(e -> {
                    AdminReservation row = rowOf(this);
                    if (row != null) {
                        adjustCredit(row);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                AdminReservation row = rowOf(this);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                // 只有「已预约 / 使用中」可以取消，其它状态点了服务端也会 409
                cancelButton.setDisable(!row.cancellable());
                setGraphic(box);
            }
        });
        return col;
    }

    private static AdminReservation rowOf(TableCell<AdminReservation, ?> cell) {
        if (cell.getTableRow() == null) {
            return null;
        }
        return cell.getTableRow().getItem();
    }

    private TableColumn<AdminReservation, String> column(String title, double width,
                                                         Function<AdminReservation, String> getter) {
        TableColumn<AdminReservation, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        return col;
    }

    /** 状态列：带颜色的小标签，扫一眼就能分清 */
    private TableColumn<AdminReservation, String> statusColumn() {
        TableColumn<AdminReservation, String> col = new TableColumn<>("状态");
        col.setPrefWidth(100);
        col.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().status() == null ? "" : c.getValue().status().getLabel()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                ReservationStatus status = getTableRow().getItem().status();
                Label chip = new Label(status.getLabel());
                chip.getStyleClass().addAll("status-chip", "status-" + status.getCssSuffix());
                setGraphic(chip);
            }
        });
        return col;
    }

    private HBox buildFooter() {
        prevButton.getStyleClass().add("toolbar-button");
        prevButton.setOnAction(e -> {
            if (page > 1) {
                page--;
                query();
            }
        });

        nextButton.getStyleClass().add("toolbar-button");
        nextButton.setOnAction(e -> {
            if (hasMore) {
                page++;
                query();
            }
        });

        sizeBox.getItems().addAll(PAGE_SIZES[0], PAGE_SIZES[1], PAGE_SIZES[2]);
        sizeBox.setValue(size);
        sizeBox.setOnAction(e -> {
            Integer picked = sizeBox.getValue();
            if (picked != null && picked != size) {
                size = picked;
                page = 1;
                query();
            }
        });
        sizeBox.setPrefWidth(80);

        pageLabel.getStyleClass().add("muted-small");
        statusLabel.getStyleClass().add("muted-small");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox footer = new HBox(8, statusLabel, spacer, prevButton, pageLabel, nextButton,
                label("每页"), sizeBox);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(4, 0, 0, 0));
        return footer;
    }

    // ---------------- 数据 ----------------

    private void query() {
        ReservationFilter filter = buildFilter();
        setBusy("查询中…");
        Async.run(() -> repository.search(filter), this::apply, this::showError);
    }

    private ReservationFilter buildFilter() {
        String studentNo = studentNoField.getText();
        Long seatId = parseLong(seatIdField.getText());
        ReservationStatus status = statusBox.getValue();
        OffsetDateTime from = startOfDay(fromPicker.getValue());
        OffsetDateTime to = endOfDayExclusive(toPicker.getValue());

        return new ReservationFilter(studentNo, seatId, status, from, to, page, size);
    }

    private void apply(PageResult<AdminReservation> result) {
        rows.setAll(result.items());
        hasMore = result.hasMore();
        page = result.page();

        prevButton.setDisable(page <= 1);
        nextButton.setDisable(!hasMore);
        pageLabel.setText("第 " + page + " 页");

        String summary = rows.isEmpty()
                ? "没有符合条件的预约"
                : "本页 " + rows.size() + " 条" + (hasMore ? "（还有下一页）" : "（已到最后一页）");

        statusLabel.setStyle("-fx-text-fill: #6b7280;");
        if (pendingMessage != null) {
            statusLabel.setStyle("-fx-text-fill: #059669;");
            statusLabel.setText(pendingMessage + " · " + summary);
            pendingMessage = null;
        } else {
            statusLabel.setText(summary);
        }
    }

    // ---------------- W5：强制取消 / 信用调整 ----------------

    private void forceCancel(AdminReservation reservation) {
        Optional<String> reason = AdminDialogs.askForceCancelReason(window(), reservation);
        if (reason.isEmpty()) {
            return;
        }
        setBusy("正在取消…");
        Async.run(() -> repository.forceCancel(reservation.id(), reason.get()),
                updated -> {
                    pendingMessage = "预约 #" + updated.id() + " 已取消";
                    query();
                },
                this::showError);
    }

    private void adjustCredit(AdminReservation reservation) {
        Optional<AdminDialogs.CreditInput> input =
                AdminDialogs.askCreditAdjust(window(), reservation.userId(), reservation.id());
        if (input.isEmpty()) {
            return;
        }
        AdminDialogs.CreditInput in = input.get();
        setBusy("正在调整信用…");
        Async.run(() -> creditRepository.adjust(in.userId(), in.reservationId(), in.delta(), in.reason()),
                credit -> {
                    pendingMessage = "用户 " + credit.userId() + " 信用分现为 " + credit.score()
                            + "（" + credit.levelText() + "）";
                    query();
                },
                this::showError);
    }

    private javafx.stage.Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private void showError(Throwable error) {
        prevButton.setDisable(true);
        nextButton.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: #b91c1c;");
        statusLabel.setText("查询失败：" + error.getMessage());
    }

    private void setBusy(String message) {
        queryButton.setDisable(true);
        resetButton.setDisable(true);
        prevButton.setDisable(true);
        nextButton.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: #6b7280;");
        statusLabel.setText(message);
    }

    private void reset() {
        studentNoField.clear();
        seatIdField.clear();
        statusBox.getSelectionModel().selectFirst();
        fromPicker.setValue(null);
        toPicker.setValue(null);
        page = 1;
        query();
    }

    // ---------------- 小工具 ----------------

    /** 起始日期 → 当天 00:00:00+08:00 */
    private static OffsetDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay().atOffset(ZONE);
    }

    /** 结束日期 → 次日 00:00:00+08:00（契约里 to 是上界不含，所以要加一天） */
    private static OffsetDateTime endOfDayExclusive(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay().atOffset(ZONE);
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

    private static Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("form-label");
        return l;
    }

    /** 状态下拉里第一项是「全部」，用 null 表示 */
    private static final class NullableStatusConverter extends StringConverter<ReservationStatus> {
        @Override
        public String toString(ReservationStatus status) {
            return status == null ? "全部" : status.getLabel();
        }

        @Override
        public ReservationStatus fromString(String text) {
            return null;
        }
    }
}
