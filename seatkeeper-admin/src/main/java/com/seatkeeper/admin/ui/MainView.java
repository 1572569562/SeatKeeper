package com.seatkeeper.admin.ui;

import com.seatkeeper.admin.config.AppConfig;
import com.seatkeeper.admin.repo.CreditRepository;
import com.seatkeeper.admin.repo.HttpCreditRepository;
import com.seatkeeper.admin.repo.HttpReservationRepository;
import com.seatkeeper.admin.repo.HttpSeatRepository;
import com.seatkeeper.admin.repo.HttpStatsRepository;
import com.seatkeeper.admin.repo.MockCreditRepository;
import com.seatkeeper.admin.repo.MockReservationRepository;
import com.seatkeeper.admin.repo.MockSeatRepository;
import com.seatkeeper.admin.repo.MockStatsRepository;
import com.seatkeeper.admin.repo.ReservationRepository;
import com.seatkeeper.admin.repo.SeatRepository;
import com.seatkeeper.admin.repo.StatsRepository;
import com.seatkeeper.admin.session.Session;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录之后的主界面外壳：左边导航，右边内容。
 * 现在每个菜单都是占位页，W2 起往里填真实内容。
 */
public final class MainView {

    private static final Map<String, String> MENUS = new LinkedHashMap<>();
    private static final Map<String, String> PLACEHOLDER = new LinkedHashMap<>();

    static {
        MENUS.put("座位管理", "M2 2H7V7H2ZM9 2H14V7H9ZM2 9H7V14H2ZM9 9H14V14H9Z");
        MENUS.put("预约查询", "M2 3H14V4.6H2ZM2 7.4H14V9H2ZM2 11.8H14V13.4H2Z");
        MENUS.put("信用管理", "M8 1L10 6L15 6.5L11.5 9.5L12.5 14.5L8 12L3.5 14.5L4.5 9.5L1 6.5L6 6Z");
        MENUS.put("统计看板", "M2 8H5V14H2ZM6.5 4H9.5V14H6.5ZM11 10H14V14H11Z");

        PLACEHOLDER.put("座位管理", "按楼层和阅览室查看座位，点击座位可以停用或启用。\n停用后学生端看不到这个座位，已有预约不受影响。");
        PLACEHOLDER.put("预约查询", "按学号 / 座位 / 状态 / 日期查询预约记录。\nW5 会在这里加强制取消和信用调整。");
        PLACEHOLDER.put("信用管理", "按用户 ID 查信用快照，可以带原因调整分值。\n低于 60 分会被限制预约 7 天；同一预约 + 同一原因只能调一次。");
        PLACEHOLDER.put("统计看板", "区间内的预约数、签到率、爽约率。\n爽约率依赖守护节点把超期预约置为 EXPIRED。");
    }

    private final BorderPane root = new BorderPane();
    private final StackPane contentArea = new StackPane();
    private final Label breadcrumbCurrent = new Label();
    private final ToggleGroup navGroup = new ToggleGroup();

    /** W2 用本地假数据；W3 起配置切到 http 就换成真接口，界面代码不用动 */
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final CreditRepository creditRepository;
    private final StatsRepository statsRepository;

    /** 页面缓存：切走再切回来，楼层/阅览室的选择和改过的状态都还在 */
    private final Map<String, Node> pageCache = new LinkedHashMap<>();

    /** 导航项，按菜单名索引；切换页面和预览工具用 */
    private final Map<String, ToggleButton> navButtons = new LinkedHashMap<>();

    /** 当前是不是本地模拟模式，页面里用来显示提示文案 */
    private final boolean mockMode;

    public MainView(AppConfig config, Session session, Runnable onLogout) {
        boolean mock = config.isMockAuth();
        this.mockMode = mock;
        String baseUrl = config.getServerBaseUrl();
        String token = session.token();

        if (mock) {
            // 统计和预约共用同一份假数据，保证两个页面的数字对得上
            MockReservationRepository mockReservations = new MockReservationRepository();
            this.seatRepository = new MockSeatRepository();
            this.reservationRepository = mockReservations;
            this.creditRepository = new MockCreditRepository();
            this.statsRepository = new MockStatsRepository(mockReservations);
        } else {
            this.seatRepository = new HttpSeatRepository(baseUrl, token);
            this.reservationRepository = new HttpReservationRepository(baseUrl, token);
            this.creditRepository = new HttpCreditRepository(baseUrl, token);
            this.statsRepository = new HttpStatsRepository(baseUrl, token);
        }
        build(config, session, onLogout);
    }

    public Parent getRoot() {
        return root;
    }

    /** 切到指定菜单（自动打开那个页面） */
    void selectPage(String menu) {
        ToggleButton button = navButtons.get(menu);
        if (button != null) {
            button.setSelected(true);
            showContent(menu);
        }
    }

    private void build(AppConfig config, Session session, Runnable onLogout) {
        root.getStyleClass().add("content-bg");
        String css = MainView.class.getResource("/admin.css").toExternalForm();
        root.getStylesheets().add(css);

        root.setTop(buildTopBar(config, session, onLogout));
        root.setLeft(buildSideNav());
        root.setCenter(buildContentArea());
    }

    // ---------------- 顶栏 ----------------

    private HBox buildTopBar(AppConfig config, Session session, Runnable onLogout) {
        Label brand = new Label("管理端");
        brand.getStyleClass().add("crumb-root");

        Label slash = new Label("/");
        slash.getStyleClass().add("crumb-root");

        breadcrumbCurrent.getStyleClass().add("crumb-current");

        HBox crumb = new HBox(6, brand, slash, breadcrumbCurrent);
        crumb.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label mode = new Label(config.describeMode());
        mode.getStyleClass().addAll("badge", config.isMockAuth() ? "badge-mock" : "badge");

        Label user = new Label(session.name() + "（" + session.role() + "）");
        user.getStyleClass().add("user-name");

        Label credit = new Label("信用 " + session.credit()
                + (session.isRestricted() ? " · 受限中" : ""));
        credit.getStyleClass().addAll("badge", session.isRestricted() ? "badge-mock" : "badge");

        Button logout = new Button("退出登录");
        logout.getStyleClass().add("link-button");
        logout.setOnAction(e -> onLogout.run());

        HBox right = new HBox(14, mode, credit, user, logout);
        right.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(crumb, spacer, right);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 24, 10, 24));
        bar.setMinHeight(52);
        return bar;
    }

    // ---------------- 左侧导航 ----------------

    private VBox buildSideNav() {
        VBox nav = new VBox();
        nav.getStyleClass().add("side-nav");
        nav.setPrefWidth(184);
        nav.setMinWidth(184);

        Label section = new Label("功能");
        section.getStyleClass().add("nav-section");

        nav.getChildren().add(section);

        MENUS.forEach((name, iconPath) -> {
            ToggleButton item = new ToggleButton(name);
            item.getStyleClass().add("nav-item");
            item.setToggleGroup(navGroup);
            item.setContentDisplay(ContentDisplay.LEFT);
            item.setGraphicTextGap(10);
            item.setGraphic(buildIcon(iconPath));
            item.setOnAction(e -> {
                // 再点一次已选中的项不要取消选中
                if (!item.isSelected()) {
                    item.setSelected(true);
                    return;
                }
                showContent(name);
            });
            nav.getChildren().add(item);
            navButtons.put(name, item);
            if (breadcrumbCurrent.getText().isEmpty()) {
                item.setSelected(true);
                showContent(name);
            }
        });

        Region filler = new Region();
        VBox.setVgrow(filler, Priority.ALWAYS);
        nav.getChildren().add(filler);

        Label version = new Label("v0.1.0-SNAPSHOT");
        version.getStyleClass().add("empty-text");
        VBox.setMargin(version, new Insets(0, 0, 14, 20));
        nav.getChildren().add(version);

        return nav;
    }

    private SVGPath buildIcon(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("nav-icon");
        icon.setScaleX(0.95);
        icon.setScaleY(0.95);
        return icon;
    }

    // ---------------- 内容区 ----------------

    private StackPane buildContentArea() {
        contentArea.getStyleClass().add("content-bg");
        contentArea.setPadding(new Insets(20, 24, 24, 24));
        contentArea.setAlignment(Pos.TOP_LEFT);
        return contentArea;
    }

    private void showContent(String menu) {
        if (menu == null) {
            return;
        }
        breadcrumbCurrent.setText(menu);

        Label title = new Label(menu);
        title.getStyleClass().add("page-title");

        Label desc = new Label(PLACEHOLDER.getOrDefault(menu, ""));
        desc.getStyleClass().add("page-desc");
        desc.setWrapText(true);
        // 换行文本在 VBox 里会被压成一行高度，这里让它按内容撑开
        desc.setMinHeight(Region.USE_PREF_SIZE);

        VBox header = new VBox(8, title, desc);

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: #f3f4f6;");
        VBox.setMargin(divider, new Insets(18, 0, 0, 0));

        Node body = pageCache.computeIfAbsent(menu, this::createPage);
        if (body == null) {
            body = buildEmptyState(menu);
        }
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox.setMargin(body, new Insets(16, 0, 0, 0));

        VBox card = new VBox(header, divider, body);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMaxHeight(Double.MAX_VALUE);

        contentArea.getChildren().setAll(card);
    }

    /** 已经做好的页面放这里，没做的返回 null 走空状态 */
    private Node createPage(String menu) {
        return switch (menu) {
            case "座位管理" -> new SeatManageView(seatRepository).getRoot();
            case "预约查询" -> new ReservationQueryView(reservationRepository, creditRepository).getRoot();
            case "信用管理" -> new CreditView(creditRepository, mockMode).getRoot();
            case "统计看板" -> new StatsView(statsRepository, mockMode).getRoot();
            default -> null;
        };
    }

    private VBox buildEmptyState(String menu) {
        SVGPath icon = buildIcon(MENUS.getOrDefault(menu, ""));
        icon.getStyleClass().setAll("empty-icon");
        icon.setScaleX(1.8);
        icon.setScaleY(1.8);

        Label main = new Label("暂无数据");
        main.getStyleClass().add("empty-text");

        VBox box = new VBox(14, icon, main);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 0, 40, 0));
        return box;
    }
}
