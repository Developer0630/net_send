package com.netsend;

import com.netsend.config.AppConfig;
import com.netsend.model.ChatMessage;
import com.netsend.model.Device;
import com.netsend.model.GroupConfig;
import com.netsend.model.TargetType;
import com.netsend.service.MessengerService;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final MessengerService service = new MessengerService();
    private final ListView<ChatMessage> historyList = new ListView<>();
    private final ListView<Device> deviceList = new ListView<>();
    private final TextArea logs = new TextArea();
    private final Label status = new Label("Stopped");
    private final Label dashboardStatus = new Label("Stopped");
    private final Label dashboardDevices = new Label("0");
    private final Label dashboardMessages = new Label("0");
    private final TextField directIp = new TextField();
    private final TextField directPort = new TextField();
    private final TextField directText = new TextField();
    private final TextField broadcastText = new TextField();
    private final TextField groupText = new TextField();
    private final ComboBox<String> groupPicker = new ComboBox<>();
    private final TextField search = new TextField();
    private final ComboBox<String> historyFilter = new ComboBox<>();
    private final TextField historyDate = new TextField();
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "netsend-ui-send"); thread.setDaemon(true); return thread;
    });
    private Stage stage;

    @Override public void start(Stage stage) {
        this.stage = stage;
        BorderPane root = new BorderPane();
        root.setLeft(createSidebar());
        root.setCenter(createPages());
        root.getStyleClass().add("root");
        Scene scene = new Scene(root, 1120, 740);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setTitle("NetSend LAN Messenger");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        Timeline deviceStatusRefresh = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
            deviceList.refresh();
            dashboardDevices.setText(String.valueOf(service.getDevices().stream().filter(Device::online).count()));
        }));
        deviceStatusRefresh.setCycleCount(Timeline.INDEFINITE);
        deviceStatusRefresh.play();
        stage.setOnCloseRequest(event -> { deviceStatusRefresh.stop(); sendExecutor.shutdownNow(); service.close(); });
        wireService();
        refreshConfigFields();
        refreshHistory();
        stage.show();
        try {
            service.start();
            status.setText("Listening · UDP " + service.getConfig().getListenPort());
            dashboardStatus.setText(status.getText());
        }
        catch (IOException | IllegalArgumentException e) {
            status.setText("Error");
            dashboardStatus.setText("Error");
            showError("Không thể khởi động dịch vụ UDP", e.getMessage());
        }
    }

    private Node createSidebar() {
        VBox side = new VBox();
        side.setPrefWidth(205);
        side.getStyleClass().add("sidebar");
        Label brand = new Label("NetSend LAN");
        brand.getStyleClass().add("brand");
        side.getChildren().add(brand);
        String[] pages = {"Dashboard", "Devices", "Direct Message", "Groups", "LAN Broadcast", "Conversations", "Settings", "Logs"};
        for (String page : pages) {
            Button button = new Button(page);
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(e -> selectPage(page));
            side.getChildren().add(button);
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        side.getChildren().addAll(spacer, status);
        status.setStyle("-fx-text-fill: #9fe3b1; -fx-padding: 12 8;");
        return side;
    }

    private TabPane pageTabs;
    private Node createPages() {
        pageTabs = new TabPane();
        pageTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pageTabs.getTabs().addAll(
                tab("Dashboard", dashboardPage()), tab("Devices", devicesPage()), tab("Direct Message", directPage()),
                tab("Groups", groupsPage()), tab("LAN Broadcast", broadcastPage()), tab("Conversations", conversationsPage()),
                tab("Settings", settingsPage()), tab("Logs", logsPage()));
        pageTabs.setStyle("-fx-tab-min-height: 0; -fx-tab-max-height: 0;");
        return pageTabs;
    }

    private Tab tab(String name, Node content) {
        Tab tab = new Tab(name, content);
        tab.setClosable(false);
        return tab;
    }

    private void selectPage(String name) {
        for (Tab tab : pageTabs.getTabs()) if (tab.getText().equals(name)) pageTabs.getSelectionModel().select(tab);
    }

    private Node dashboardPage() {
        VBox box = page("Dashboard", "Tổng quan trạng thái ứng dụng và mạng LAN.");
        HBox cards = new HBox(14,
                statCard("Trạng thái", dashboardStatus),
                statCard("Thiết bị gần đây", dashboardDevices),
                statCard("Tin nhắn lưu cục bộ", dashboardMessages));
        Label ip = new Label("Địa chỉ IPv4 cục bộ: " + String.join(", ", service.localIpv4Addresses()));
        ip.getStyleClass().add("muted");
        Button start = primary("Start / Restart listener", () -> restartService());
        Button stop = new Button("Stop listener");
        stop.setOnAction(e -> { service.stop(); status.setText("Stopped"); dashboardStatus.setText("Stopped"); });
        Label note = new Label("UDP không xác nhận người nhận đã nhận tin. Discovery phụ thuộc broadcast, subnet và firewall.");
        note.getStyleClass().add("muted");
        box.getChildren().addAll(cards, ip, new HBox(10, start, stop), card(note));
        return scroll(box);
    }

    private Node devicesPage() {
        VBox box = page("Devices", "Thiết bị được phát hiện qua UDP discovery/heartbeat; Online là ước tính trong 45 giây gần nhất.");
        deviceList.setPlaceholder(new Label("Chưa phát hiện thiết bị. Kiểm tra subnet, broadcast address và firewall."));
        deviceList.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(Device device, boolean empty) {
                super.updateItem(device, empty);
                if (empty || device == null) setText(null);
                else setText((device.online() ? "● Online" : "○ Offline") + "   " + device.name() + "   " + device.ip() + ":" + device.listenPort()
                        + "   · gần nhất " + TIME.format(device.lastSeen().atZone(ZoneId.systemDefault())));
            }
        });
        deviceList.setOnMouseClicked(e -> {
            Device chosen = deviceList.getSelectionModel().getSelectedItem();
            if (chosen != null) { directIp.setText(chosen.ip()); directPort.setText(String.valueOf(chosen.listenPort())); selectPage("Direct Message"); }
        });
        VBox.setVgrow(deviceList, Priority.ALWAYS);
        box.getChildren().add(deviceList);
        return box;
    }

    private Node directPage() {
        VBox box = page("Direct Message", "Gửi unicast tới IP và cổng UDP listener đang cấu hình.");
        directIp.setPromptText("192.168.1.20");
        directPort.setText(String.valueOf(service.getConfig().getListenPort()));
        directText.setPromptText("Nội dung tin nhắn (tối đa 1000 ký tự)");
        Button send = primary("Send direct", () -> runSend(() -> service.sendDirect(directIp.getText().trim(),
                Integer.parseInt(directPort.getText().trim()), directText.getText())));
        box.getChildren().addAll(field("Địa chỉ IP máy nhận", directIp), field("Cổng UDP máy nhận", directPort),
                field("Nội dung", directText), send,
                card(new Label("Trạng thái gửi chỉ xác nhận datagram được gửi từ socket cục bộ; không xác nhận đã đến máy nhận.")));
        return box;
    }

    private Node groupsPage() {
        VBox box = page("Groups", "Tạo nhóm multicast và tham gia nhận trên group address/port đã cấu hình.");
        ListView<GroupConfig> groups = new ListView<>();
        groups.setItems(FXCollections.observableArrayList(service.getConfig().getGroups()));
        groups.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(GroupConfig group, boolean empty) {
                super.updateItem(group, empty);
                setText(empty || group == null ? null : (group.isJoined() ? "● joined · " : "○ left · ")
                        + group.getName() + " — " + group.getDescription() + " (" + group.getAddress() + ":" + group.getPort() + ")");
            }
        });
        TextField name = new TextField(); name.setPromptText("Tên nhóm, ví dụ GROUP_IT");
        TextField description = new TextField(); description.setPromptText("Mô tả");
        TextField address = new TextField(); address.setPromptText("239.10.10.10");
        TextField port = new TextField("45454");
        groups.setOnMouseClicked(e -> {
            GroupConfig selected = groups.getSelectionModel().getSelectedItem();
            if (selected != null) {
                name.setText(selected.getName());
                description.setText(selected.getDescription());
                address.setText(selected.getAddress());
                port.setText(String.valueOf(selected.getPort()));
            }
        });
        Button add = primary("Add / update group", () -> {
            try {
                GroupConfig group = new GroupConfig(name.getText().trim(), description.getText().trim(), address.getText().trim(), Integer.parseInt(port.getText().trim()));
                List<GroupConfig> updated = new ArrayList<>(service.getConfig().getGroups());
                updated.removeIf(g -> g.getName().equalsIgnoreCase(group.getName()));
                updated.add(group);
                service.setGroups(updated);
                groups.setItems(FXCollections.observableArrayList(updated));
                refreshGroupPicker();
            } catch (IOException | IllegalArgumentException e) { showError("Cấu hình nhóm không hợp lệ", e.getMessage()); }
        });
        Button remove = new Button("Delete selected");
        remove.setOnAction(e -> {
            GroupConfig selected = groups.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            List<GroupConfig> updated = new ArrayList<>(service.getConfig().getGroups());
            updated.removeIf(g -> g.getName().equals(selected.getName()));
            try { service.setGroups(updated); groups.setItems(FXCollections.observableArrayList(updated)); refreshGroupPicker(); }
            catch (IOException ex) { showError("Không lưu được nhóm", ex.getMessage()); }
        });
        Button toggleJoin = new Button("Join / leave selected");
        toggleJoin.setOnAction(e -> {
            GroupConfig selected = groups.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            List<GroupConfig> updated = new ArrayList<>(service.getConfig().getGroups());
            for (GroupConfig group : updated) if (group.getName().equals(selected.getName())) group.setJoined(!group.isJoined());
            try { service.setGroups(updated); groups.setItems(FXCollections.observableArrayList(updated)); }
            catch (IOException ex) { showError("Không thể cập nhật trạng thái group", ex.getMessage()); }
        });
        groupPicker.setPromptText("Chọn group");
        refreshGroupPicker();
        groupText.setPromptText("Nội dung multicast");
        Button send = primary("Send to group", () -> runSend(() -> service.sendGroup(groupPicker.getValue(), groupText.getText())));
        box.getChildren().addAll(groups, new HBox(8, name, description), new HBox(8, address, port), new HBox(8, add, toggleJoin, remove),
                new Label("Gửi tin"), groupPicker, groupText, send);
        return scroll(box);
    }

    private Node broadcastPage() {
        VBox box = page("LAN Broadcast", "UDP broadcast chỉ hoạt động trên các mạng cho phép broadcast; không truyền broadcast qua Internet.");
        broadcastText.setPromptText("Thông báo gửi đến broadcast address đã cấu hình");
        CheckBox caution = new CheckBox("Tôi hiểu firewall/router có thể chặn broadcast; không có xác nhận người nhận.");
        Button send = primary("Broadcast message", () -> {
            if (!caution.isSelected()) { showError("Cần xác nhận", "Đánh dấu cảnh báo trước khi broadcast."); return; }
            runSend(() -> service.sendBroadcast(broadcastText.getText()));
        });
        box.getChildren().addAll(field("Broadcast address (cấu hình trong Settings)", new Label(service.getConfig().getBroadcastAddress())),
                field("Nội dung", broadcastText), caution, send);
        return box;
    }

    private Node conversationsPage() {
        VBox box = page("Conversations", "Lịch sử cục bộ chỉ có trên máy này; không được đồng bộ sang peers.");
        search.setPromptText("Tìm nội dung, tên hoặc IP...");
        search.textProperty().addListener((obs, old, value) -> refreshHistory());
        Button clear = new Button("Clear local history");
        clear.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Xóa toàn bộ lịch sử trên máy này?");
            confirm.showAndWait().filter(button -> button == javafx.scene.control.ButtonType.OK).ifPresent(button -> {
                try { service.clearHistory(); refreshHistory(); dashboardMessages.setText("0"); }
                catch (IOException ex) { showError("Không xóa được lịch sử", ex.getMessage()); }
            });
        });
        historyList.setPlaceholder(new Label("Chưa có tin nhắn."));
        historyFilter.setItems(FXCollections.observableArrayList("Tất cả", "Sent", "Received", "Direct", "Group", "Broadcast"));
        historyFilter.getSelectionModel().selectFirst();
        historyFilter.valueProperty().addListener((obs, old, value) -> refreshHistory());
        historyDate.setPromptText("Ngày YYYY-MM-DD (tùy chọn)");
        historyDate.textProperty().addListener((obs, old, value) -> refreshHistory());
        historyList.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(ChatMessage message, boolean empty) {
                super.updateItem(message, empty);
                if (empty || message == null) { setText(null); setContextMenu(null); return; }
                setText((message.outgoing() ? "↑ Sent" : "↓ Received") + " · " + message.senderName() + " · "
                        + message.sourceIp() + " · " + TIME.format(message.timestamp().atZone(ZoneId.systemDefault()))
                        + "\n" + message.targetType() + (message.targetId() == null ? "" : " / " + message.targetId())
                        + " · " + message.status() + "\n" + message.content());
                javafx.scene.control.MenuItem copy = new javafx.scene.control.MenuItem("Copy message");
                copy.setOnAction(e -> {
                    ClipboardContent content = new ClipboardContent(); content.putString(message.content());
                    Clipboard.getSystemClipboard().setContent(content);
                });
                setContextMenu(new javafx.scene.control.ContextMenu(copy));
            }
        });
        VBox.setVgrow(historyList, Priority.ALWAYS);
        box.getChildren().addAll(new HBox(10, search, historyFilter, historyDate, clear), historyList);
        return box;
    }

    private Node settingsPage() {
        AppConfig config = service.getConfig();
        VBox box = page("Settings", "Cấu hình được lưu trong ~/.netsend-lan/config.json.");
        TextField displayName = new TextField(config.getDisplayName());
        ComboBox<String> interfaces = new ComboBox<>(FXCollections.observableArrayList(service.localIpv4Addresses()));
        interfaces.setEditable(true);
        interfaces.setPromptText("Tự chọn / bind all");
        interfaces.setValue(config.getInterfaceAddress());
        TextField listenPort = new TextField(String.valueOf(config.getListenPort()));
        TextField broadcastAddress = new TextField(config.getBroadcastAddress());
        CheckBox notifications = new CheckBox("Thông báo khi nhận tin mới"); notifications.setSelected(config.isNotificationsEnabled());
        Button save = primary("Save and restart listener", () -> {
            try {
                config.setDisplayName(displayName.getText().trim());
                config.setInterfaceAddress(interfaces.getEditor().getText().trim());
                config.setListenPort(Integer.parseInt(listenPort.getText().trim()));
                config.setBroadcastAddress(broadcastAddress.getText().trim());
                config.setNotificationsEnabled(notifications.isSelected());
                service.saveConfig(config);
                restartService();
            } catch (IOException | IllegalArgumentException e) { showError("Không lưu được cấu hình", e.getMessage()); }
        });
        box.getChildren().addAll(field("Display name", displayName), field("Network interface IPv4 (trống = tự chọn)", interfaces),
                field("UDP listen port", listenPort), field("Broadcast address", broadcastAddress), notifications, save,
                card(new Label("Giao thức UDP cơ bản không mã hóa, không xác thực sender và không bảo đảm giao hàng. Tên người gửi/IP không phải danh tính đã xác thực.")));
        return scroll(box);
    }

    private Node logsPage() {
        VBox box = page("Logs", "Chẩn đoán socket, discovery và gói tin không hợp lệ.");
        logs.setEditable(false);
        logs.setWrapText(true);
        VBox.setVgrow(logs, Priority.ALWAYS);
        box.getChildren().add(logs);
        return box;
    }

    private void wireService() {
        service.onMessage(message -> Platform.runLater(() -> {
            refreshHistory();
            dashboardMessages.setText(String.valueOf(service.getHistory().size()));
            if (!message.outgoing() && service.getConfig().isNotificationsEnabled()) {
                stage.setTitle("NetSend LAN Messenger — Tin nhắn mới");
                Alert notification = new Alert(Alert.AlertType.INFORMATION);
                notification.initOwner(stage);
                notification.setTitle("Tin nhắn mới");
                notification.setHeaderText(message.senderName() + " · " + message.sourceIp());
                notification.setContentText(message.content());
                notification.show();
            }
        }));
        service.onLog(message -> Platform.runLater(() -> {
            logs.appendText(message + System.lineSeparator());
            if (logs.getLength() > 50000) logs.deleteText(0, 10000);
        }));
        service.onDevice(device -> Platform.runLater(() -> {
            deviceList.setItems(FXCollections.observableArrayList(service.getDevices()));
            dashboardDevices.setText(String.valueOf(service.getDevices().stream().filter(Device::online).count()));
        }));
    }

    private void refreshHistory() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        String selected = historyFilter.getValue() == null ? "Tất cả" : historyFilter.getValue();
        String date = historyDate.getText() == null ? "" : historyDate.getText().trim();
        List<ChatMessage> messages = service.getHistory().stream().filter(m -> query.isEmpty()
                || m.content().toLowerCase().contains(query) || m.senderName().toLowerCase().contains(query)
                || m.sourceIp().toLowerCase().contains(query) || (m.targetId() != null && m.targetId().toLowerCase().contains(query)))
                .filter(m -> switch (selected) {
                    case "Sent" -> m.outgoing();
                    case "Received" -> !m.outgoing();
                    case "Direct" -> m.targetType() == TargetType.DIRECT;
                    case "Group" -> m.targetType() == TargetType.GROUP;
                    case "Broadcast" -> m.targetType() == TargetType.BROADCAST;
                    default -> true;
                })
                .filter(m -> date.isEmpty() || TIME.format(m.timestamp().atZone(ZoneId.systemDefault())).startsWith(date)).toList();
        historyList.setItems(FXCollections.observableArrayList(messages));
        dashboardMessages.setText(String.valueOf(service.getHistory().size()));
    }

    private void refreshGroupPicker() {
        groupPicker.setItems(FXCollections.observableArrayList(service.getConfig().getGroups().stream().map(GroupConfig::getName).toList()));
        if (!groupPicker.getItems().isEmpty() && groupPicker.getValue() == null) groupPicker.getSelectionModel().selectFirst();
    }

    private void refreshConfigFields() {
        deviceList.setItems(FXCollections.observableArrayList(service.getDevices()));
        dashboardMessages.setText(String.valueOf(service.getHistory().size()));
        dashboardDevices.setText(String.valueOf(service.getDevices().stream().filter(Device::online).count()));
    }

    private void restartService() {
        try {
            service.restart();
            status.setText("Listening · UDP " + service.getConfig().getListenPort());
            dashboardStatus.setText("Listening · UDP " + service.getConfig().getListenPort());
        } catch (IOException | IllegalArgumentException e) {
            status.setText("Error");
            dashboardStatus.setText("Error");
            showError("Không thể khởi động listener", e.getMessage());
        }
    }

    private void runSend(IoAction action) {
        sendExecutor.submit(() -> {
            try { action.run(); }
            catch (IOException | IllegalArgumentException | IllegalStateException e) {
                Platform.runLater(() -> showError("Gửi tin thất bại", e.getMessage()));
            }
        });
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "Lỗi không xác định." : message);
        alert.show();
    }

    private VBox page(String title, String subtitle) {
        VBox box = new VBox(16);
        box.getStyleClass().add("content");
        Label heading = new Label(title); heading.getStyleClass().add("page-title");
        Label sub = new Label(subtitle); sub.getStyleClass().add("muted");
        box.getChildren().addAll(heading, sub);
        return box;
    }

    private VBox field(String title, Node control) {
        VBox box = new VBox(6, new Label(title), control);
        if (control instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox statCard(String title, Label value) {
        value.getStyleClass().add("stat");
        return card(new VBox(8, new Label(title), value));
    }

    private VBox card(Node content) {
        VBox box = new VBox(content);
        box.getStyleClass().add("card");
        return box;
    }

    private Button primary(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private Node scroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scroll;
    }

    @FunctionalInterface private interface IoAction { void run() throws IOException; }

    public static void main(String[] args) {
        for (String argument : args) {
            int separator = argument.indexOf('=');
            if (!argument.startsWith("--") || separator < 3) continue;
            String key = argument.substring(2, separator);
            String value = argument.substring(separator + 1);
            switch (key) {
                case "listenPort" -> System.setProperty("netsend.listenPort", value);
                case "displayName" -> System.setProperty("netsend.displayName", value);
                case "dataDir" -> System.setProperty("netsend.dataDir", value);
                case "broadcastAddress" -> System.setProperty("netsend.broadcastAddress", value);
                case "interfaceAddress" -> System.setProperty("netsend.interfaceAddress", value);
                default -> { }
            }
        }
        launch(args);
    }
}
