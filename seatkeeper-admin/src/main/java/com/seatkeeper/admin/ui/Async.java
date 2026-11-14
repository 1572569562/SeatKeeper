package com.seatkeeper.admin.ui;

import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 把可能阻塞的调用（HTTP 请求）放到后台线程，
 * 结果回到 JavaFX 线程再更新界面，避免界面卡住。
 */
final class Async {

    private Async() {
    }

    static <T> void run(Supplier<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(task)
                .whenComplete((value, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        onError.accept(rootCause(error));
                    } else {
                        onSuccess.accept(value);
                    }
                }));
    }

    static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
