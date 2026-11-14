package com.seatkeeper.admin.ui;

import com.seatkeeper.admin.domain.AdminReservation;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Optional;

/**
 * W5 的两个管理动作弹窗。
 *
 * 契约要求这两件事**都必须填原因**：
 * - 强制取消：reason 会写进发给学生的通知正文
 * - 信用调整：reason 写进 credit_log，同一预约 + 同一原因只能调一次
 */
final class AdminDialogs {

    private AdminDialogs() {
    }

    /** 信用调整要提交的东西 */
    record CreditInput(long userId, long reservationId, int delta, String reason) {
    }

    // ---------------- 强制取消 ----------------

    static Optional<String> askForceCancelReason(Window owner, AdminReservation reservation) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("强制取消预约");
        dialog.setHeaderText("预约 #" + reservation.id() + " · " + reservation.studentName()
                + "（" + reservation.studentNo() + "）");

        Label seat = new Label(reservation.seatText());
        Label slot = new Label(reservation.slotText());
        Label hint = new Label("取消后会发一条系统通知给学生；不扣信用分，也不返还已扣的分。");
        hint.setWrapText(true);
        hint.getStyleClass().addAll("form-label");
        hint.setMaxWidth(360);

        TextArea reason = new TextArea();
        reason.setPromptText("例如：座位设备故障，临时封闭");
        reason.setPrefRowCount(3);
        reason.setWrapText(true);

        Label error = new Label();
        error.getStyleClass().addAll("form-label");
        error.setStyle("-fx-text-fill: #b91c1c;");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(8);
        form.setPadding(new Insets(6, 0, 0, 0));
        form.addRow(0, new Label("座位"), seat);
        form.addRow(1, new Label("时段"), slot);
        form.addRow(2, new Label("原因（必填）"), reason);
        form.addRow(3, new Label(""), hint);
        form.addRow(4, new Label(""), error);
        dialog.getDialogPane().setContent(form);

        ButtonType ok = new ButtonType("确认取消", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);

        dialog.getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION, event -> {
            String text = reason.getText() == null ? "" : reason.getText().trim();
            if (text.isEmpty()) {
                error.setText("原因不能为空");
                event.consume();
            } else if (text.length() > 64) {
                error.setText("原因不能超过 64 个字符（当前 " + text.length() + "）");
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ok) {
            return Optional.empty();
        }
        return Optional.of(reason.getText().trim());
    }

    // ---------------- 信用调整 ----------------

    /**
     * @param userId        预约行里的用户 ID；契约暂未返回时为 null，让管理员手填
     * @param reservationId 归属预约 ID，默认填当前行
     */
    static Optional<CreditInput> askCreditAdjust(Window owner, Long userId, Long reservationId) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("信用调整");
        dialog.setHeaderText("调整学生信用分");

        TextField userIdField = new TextField(userId == null ? "" : String.valueOf(userId));
        userIdField.setPromptText("用户 ID");
        userIdField.setPrefWidth(140);

        TextField reservationField = new TextField(reservationId == null ? "" : String.valueOf(reservationId));
        reservationField.setPromptText("归属预约 ID");
        reservationField.setPrefWidth(140);

        TextField deltaField = new TextField();
        deltaField.setPromptText("-100 ~ 100，负数扣分");
        deltaField.setPrefWidth(140);

        TextField reasonField = new TextField();
        reasonField.setPromptText("例如：ADMIN_APPEAL 申诉通过");
        reasonField.setPrefWidth(220);

        Label note = new Label(userId == null
                ? "预约接口暂未返回用户 ID，请手动填写（等成员3 在 VO 补 userId 后可自动带出）。"
                : "调整必须绑定该预约作为流水归属；同一预约 + 同一原因只能调一次。");
        note.setWrapText(true);
        note.setMaxWidth(360);
        note.getStyleClass().add("form-label");

        Label error = new Label();
        error.getStyleClass().add("form-label");
        error.setStyle("-fx-text-fill: #b91c1c;");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(8);
        form.setPadding(new Insets(6, 0, 0, 0));
        form.addRow(0, new Label("用户 ID"), userIdField);
        form.addRow(1, new Label("归属预约"), reservationField);
        form.addRow(2, new Label("分值变化"), deltaField);
        form.addRow(3, new Label("原因（必填）"), reasonField);
        form.addRow(4, new Label(""), note);
        form.addRow(5, new Label(""), error);
        dialog.getDialogPane().setContent(form);

        ButtonType ok = new ButtonType("提交调整", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);

        dialog.getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION, event -> {
            if (parseLong(userIdField.getText()) == null) {
                error.setText("用户 ID 必须是数字");
                event.consume();
                return;
            }
            if (parseLong(reservationField.getText()) == null) {
                error.setText("归属预约 ID 必须是数字");
                event.consume();
                return;
            }
            Integer delta = parseInt(deltaField.getText());
            if (delta == null) {
                error.setText("分值变化必须是数字");
                event.consume();
                return;
            }
            if (delta == 0) {
                error.setText("分值变化不能为 0");
                event.consume();
                return;
            }
            if (delta < -100 || delta > 100) {
                error.setText("单次最多加 100、最多扣 100");
                event.consume();
                return;
            }
            String reason = reasonField.getText() == null ? "" : reasonField.getText().trim();
            if (reason.isEmpty()) {
                error.setText("原因不能为空");
                event.consume();
            } else if (reason.length() > 64) {
                error.setText("原因不能超过 64 个字符（当前 " + reason.length() + "）");
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ok) {
            return Optional.empty();
        }
        return Optional.of(new CreditInput(
                parseLong(userIdField.getText()),
                parseLong(reservationField.getText()),
                parseInt(deltaField.getText()),
                reasonField.getText().trim()));
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

    private static Integer parseInt(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
