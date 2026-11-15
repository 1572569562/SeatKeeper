package com.seatkeeper.admin.ui;

import com.seatkeeper.admin.domain.Seat;
import com.seatkeeper.admin.domain.SeatStatus;
import com.seatkeeper.admin.repo.SeatRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;

/**
 * 座位管理页（W2 交互 + W3 接接口）。
 *
 * 楼层 / 阅览室切换 → 座位网格 → 点座位改状态 → 保存后调接口并刷新。
 * 数据来自 SeatRepository，Mock 还是 HTTP 由 AppConfig 决定，这个类不关心。
 */
public final class SeatManageView {

    private final SeatRepository repository;

    private final VBox root = new VBox(14);
    private final ComboBox<Integer> floorBox = new ComboBox<>();
    private final ComboBox<String> roomBox = new ComboBox<>();
    private final Label summaryLabel = new Label();
    private final Label statusLabel = new Label();
    private final Button refreshButton = new Button("刷新");
    private final FlowPane grid = new FlowPane();

    private boolean loading;

    public SeatManageView(SeatRepository repository) {
        this.repository = repository;
        build();
        loadFloors();
    }

    public Parent getRoot() {
        return root;
    }

    private void build() {
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(2));

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("seat-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        floorBox.setPrefWidth(110);
        floorBox.setConverter(new IntegerFloorConverter());
        floorBox.setOnAction(e -> loadRooms());

        roomBox.setPrefWidth(110);
        roomBox.setOnAction(e -> loadSeats());

        refreshButton.getStyleClass().add("toolbar-button");
        refreshButton.setOnAction(e -> {
            repository.refresh();
            loadFloors();
        });

        summaryLabel.getStyleClass().add("muted-small");
        statusLabel.getStyleClass().add("muted-small");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(8, label("楼层"), floorBox, label("阅览室"), roomBox,
                refreshButton, spacer, statusLabel, summaryLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(toolbar, scroll);
    }

    // ---------------- 数据加载（都走后台线程） ----------------

    private void loadFloors() {
        setBusy("加载中…");
        Async.run(repository::floors, floors -> {
            floorBox.getItems().setAll(floors);
            if (!floors.isEmpty()) {
                floorBox.getSelectionModel().selectFirst();
            }
            loadRooms();
        }, this::showError);
    }

    private void loadRooms() {
        Integer floor = floorBox.getValue();
        if (floor == null) {
            return;
        }
        setBusy("加载中…");
        Async.run(() -> repository.roomsOf(floor), rooms -> {
            roomBox.getItems().setAll(rooms);
            if (!rooms.isEmpty()) {
                roomBox.getSelectionModel().selectFirst();
            }
            loadSeats();
        }, this::showError);
    }

    private void loadSeats() {
        Integer floor = floorBox.getValue();
        String room = roomBox.getValue();
        if (floor == null || room == null) {
            return;
        }
        setBusy("加载中…");
        Async.run(() -> repository.seatsOf(floor, room), seats -> {
            render(floor, room, seats);
            setBusy(null);
        }, this::showError);
    }

    private void render(int floor, String room, List<Seat> seats) {
        grid.getChildren().clear();

        long available = seats.stream().filter(s -> s.status() == SeatStatus.AVAILABLE).count();
        long disabled = seats.size() - available;
        summaryLabel.setText(String.format("%d楼 %s · 共 %d 座 · 可用 %d · 停用 %d",
                floor, room, seats.size(), available, disabled));

        for (Seat seat : seats) {
            grid.getChildren().add(buildCell(seat));
        }
    }

    private Node buildCell(Seat seat) {
        Label seatNo = new Label(seat.seatNo());
        seatNo.getStyleClass().add("seat-no");

        Label status = new Label(seat.status().getLabel());
        status.getStyleClass().addAll("seat-status-text", "seat-status-" + seat.status().getCssSuffix());

        VBox content = new VBox(2, seatNo, status);
        content.setAlignment(Pos.CENTER);

        Button cell = new Button();
        cell.setGraphic(content);
        cell.setPrefSize(88, 62);
        cell.getStyleClass().addAll("seat-cell", "seat-" + seat.status().getCssSuffix());
        cell.setTooltip(new Tooltip(seat.location() + " · " + seat.status().getLabel() + "（点击编辑）"));
        cell.setOnAction(e -> editSeat(seat));
        return cell;
    }

    /** 点座位 → 弹窗改状态 → 调接口 → 刷新 */
    private void editSeat(Seat seat) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (root.getScene() != null) {
            dialog.initOwner(root.getScene().getWindow());
        }
        dialog.setTitle("编辑座位");
        dialog.setHeaderText(seat.location());

        ComboBox<SeatStatus> statusBox = new ComboBox<>();
        statusBox.getItems().setAll(SeatStatus.values());
        statusBox.setValue(seat.status());
        statusBox.setPrefWidth(160);
        statusBox.setConverter(new SeatStatusConverter());

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(10);
        form.setPadding(new Insets(6, 0, 0, 0));
        form.addRow(0, new Label("所在位置"), new Label(seat.location()));
        form.addRow(1, new Label("当前状态"), new Label(seat.status().getLabel()));
        form.addRow(2, new Label("改为"), statusBox);
        dialog.getDialogPane().setContent(form);

        ButtonType save = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != save) {
            return;
        }
        if (statusBox.getValue() == seat.status()) {
            return;
        }

        SeatStatus target = statusBox.getValue();
        setBusy("保存中…");
        Async.run(() -> repository.updateStatus(seat.id(), target), updated -> {
            loadSeats();
            statusLabel.setStyle("-fx-text-fill: #059669;");
            statusLabel.setText("「" + seat.seatNo() + "」已改为" + target.getLabel());
        }, error -> {
            setBusy(null);
            statusLabel.setStyle("-fx-text-fill: #b91c1c;");
            statusLabel.setText("保存失败：" + error.getMessage());
        });
    }

    private void setBusy(String message) {
        loading = message != null;
        refreshButton.setDisable(loading);
        floorBox.setDisable(loading);
        roomBox.setDisable(loading);
        if (message != null) {
            statusLabel.setStyle("-fx-text-fill: #6b7280;");
            statusLabel.setText(message);
        } else {
            statusLabel.setText("");
        }
    }

    private void showError(Throwable error) {
        loading = false;
        refreshButton.setDisable(false);
        floorBox.setDisable(false);
        roomBox.setDisable(false);
        statusLabel.setStyle("-fx-text-fill: #b91c1c;");
        statusLabel.setText("加载失败：" + error.getMessage());
        grid.getChildren().clear();
        summaryLabel.setText("");
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("form-label");
        return l;
    }

    /** 楼层下拉显示「1楼」而不是 1 */
    private static final class IntegerFloorConverter extends StringConverter<Integer> {
        @Override
        public String toString(Integer floor) {
            return floor == null ? "" : floor + "楼";
        }

        @Override
        public Integer fromString(String text) {
            return null;
        }
    }

    /** 状态下拉显示中文而不是枚举名 */
    private static final class SeatStatusConverter extends StringConverter<SeatStatus> {
        @Override
        public String toString(SeatStatus status) {
            return status == null ? "" : status.getLabel();
        }

        @Override
        public SeatStatus fromString(String text) {
            return null;
        }
    }
}
