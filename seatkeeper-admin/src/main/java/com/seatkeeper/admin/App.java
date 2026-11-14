package com.seatkeeper.admin;

import com.seatkeeper.admin.config.AppConfig;
import com.seatkeeper.admin.session.Session;
import com.seatkeeper.admin.ui.LoginView;
import com.seatkeeper.admin.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 管理端入口。
 * 启动：mvn javafx:run
 * 换服务端：mvn javafx:run -Dapp.server.baseUrl=http://192.168.1.21:8080 -Dapp.auth.mode=http
 */
public class App extends Application {

    private final AppConfig config = AppConfig.load();
    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("SeatKeeper 管理端");
        showLogin();
        stage.show();
    }

    private void showLogin() {
        LoginView loginView = new LoginView(config, this::showMain);
        stage.setScene(new Scene(loginView.getRoot(), 420, 520));
        stage.setMinWidth(380);
        stage.setMinHeight(480);
        stage.centerOnScreen();
    }

    private void showMain(Session session) {
        MainView mainView = new MainView(config, session, this::showLogin);
        stage.setScene(new Scene(mainView.getRoot(), 1000, 640));
        stage.setMinWidth(900);
        stage.setMinHeight(560);
        stage.centerOnScreen();
    }
}
