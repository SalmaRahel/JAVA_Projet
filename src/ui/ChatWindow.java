package ui;

import client.Client;
import database.*;
import model.Message;
import model.MessageType;
import model.User;
import model.VoiceMessage;
import model.FileMessage;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import javafx.scene.control.OverrunStyle;


public class ChatWindow extends Application {

    private static final String CREAM      = "#FAFAF7";
    private static final String CREAM_2    = "#F3F3EE";
    private static final String WHITE      = "#FFFFFF";
    private static final String FOREST     = "#1A3C34";
    private static final String FOREST_MID = "#2D5A4F";
    private static final String SAGE       = "#4A7C6F";
    private static final String MINT       = "#7DB5A0";
    private static final String MINT_PALE  = "#E8F2EF";
    private static final String SENT_BG    = "#E2EDE9";
    private static final String INK        = "#1C1C1A";
    private static final String INK_MID    = "#4A4A45";
    private static final String INK_LITE   = "#9A9A92";
    private static final String BORDER_W   = "#E8E8E2";
    private static final String TICK_GREY  = "#B0B0A8";
    private static final String TICK_BLUE  = "#5B9BD5";

    private Client client;
    private User currentContact;
    private TextField inputField;
    private ListView<User> userListPanel;
    private Label contactNameLabel;
    private Label contactStatusLabel;
    private Circle contactOnlineDot;
    private StackPane contactAvatarTop;
    private Stage stage;
    private ScrollPane messageScrollPane;
    private VBox messageContainer;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
    private List<User> onlineUsers = new ArrayList<User>();
    private Button voiceButton;
    private Label clockLabel;
    private javax.sound.sampled.TargetDataLine micLine;
    private java.io.ByteArrayOutputStream voiceBuffer;
    private long recordingStartTime;

    private Message replyingTo = null;
    private VBox replyPreviewBar = null;
    private VBox pinnedBar = null;
    private final List<Message> pinnedMessages = new ArrayList<Message>();
    private final Map<String, Integer> unreadCounts = new HashMap<String, Integer>();

    private String currentGroupId = null;
    private String currentGroupName = null;

    private ListView<String[]> groupListPanel;
    private List<String[]> myGroups = new ArrayList<String[]>();

    private final List<String[]> pendingGroupInvites = new ArrayList<String[]>();
    private String pendingGroupName = "";

    public ChatWindow() { }
    public ChatWindow(Client client) { this.client = client; }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        String username = (client != null && client.getUser() != null)
                ? client.getUser().getUsername() : "Utilisateur";
        primaryStage.setTitle("WhatsApp");

        HBox root = new HBox(buildSidebar(username), buildContactsPane(), buildChatZone());
        HBox.setHgrow(root.getChildren().get(2), Priority.ALWAYS);
        root.setStyle("-fx-background-color: " + CREAM + ";");

        Scene scene = new Scene(root, 1120, 700);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(860);
        primaryStage.setMinHeight(560);
        primaryStage.setOnCloseRequest(e -> { if (client != null) client.disconnect(); });

        startClock();
        root.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(500), root);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
        primaryStage.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // SIDEBAR
    // ═══════════════════════════════════════════════════════════════
    private VBox buildSidebar(String username) {
        Label logoW = new Label("W");
        logoW.setFont(Font.font("Georgia", FontWeight.BOLD, 18));
        logoW.setTextFill(Color.WHITE);
        logoW.setPrefSize(44, 44);
        logoW.setAlignment(Pos.CENTER);
        logoW.setStyle("-fx-background-color: " + FOREST + "; -fx-background-radius: 14;");
        glow(logoW, FOREST, 0.25, 12);

        StackPane myAv = makeAvatarInit(username, 40, FOREST_MID, 15);
        Tooltip.install(myAv, new Tooltip(username));

        Region sep = new Region();
        sep.setPrefSize(32, 1);
        sep.setStyle("-fx-background-color: " + BORDER_W + ";");

        Button btnMsg = navPill("≡", true, "Messages");

        Button btnProf = navPill("\uD83D\uDC64", false, "Mon Profil");
        btnProf.setFont(Font.font("Segoe UI", 18));
        btnProf.setOnAction(e -> {
            try { new ProfileWindow(client).start(new Stage()); }
            catch (Exception ex) { ex.printStackTrace(); }
        });

        Button btnAdd = navPill("+", false, "Nouveau contact");
        btnAdd.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        btnAdd.setOnAction(e -> showAllUsersDialog());

        Button btnGroup = navPill("👥", false, "Créer un groupe");
        btnGroup.setFont(Font.font("Segoe UI", 18));
        btnGroup.setOnAction(e -> showCreateGroupDialog());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Circle dot = new Circle(4, Color.web(SAGE));
        glow(dot, SAGE, 0.6, 7);

        VBox sidebar = new VBox(14, logoW, myAv, sep, btnMsg, btnProf, btnAdd, btnGroup, spacer, dot);
        sidebar.setAlignment(Pos.TOP_CENTER);
        sidebar.setPrefWidth(72);
        sidebar.setPadding(new Insets(20, 0, 22, 0));
        sidebar.setStyle("-fx-background-color: " + CREAM_2 + "; -fx-border-color: transparent " + BORDER_W + " transparent transparent; -fx-border-width: 0 1 0 0;");
        return sidebar;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONTACTS PANE
    // ═══════════════════════════════════════════════════════════════
    private VBox buildContactsPane() {
        Label titleLbl = new Label("Messages");
        titleLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 20));
        titleLbl.setTextFill(Color.web(INK));
        HBox header = new HBox(titleLbl);
        header.setPadding(new Insets(24, 18, 18, 22));
        header.setStyle("-fx-background-color: " + CREAM_2 + ";");

        Label searchIco = new Label("⌕");
        searchIco.setFont(Font.font("Georgia", 15));
        searchIco.setTextFill(Color.web(INK_LITE));
        searchIco.setPrefWidth(26); searchIco.setAlignment(Pos.CENTER);

        TextField searchFld = new TextField();
        searchFld.setPromptText("Rechercher...");
        searchFld.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-font-size: 13px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + INK + "; -fx-prompt-text-fill: " + INK_LITE + ";");
        HBox.setHgrow(searchFld, Priority.ALWAYS);

        HBox searchBox = new HBox(8, searchIco, searchFld);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(0, 12, 0, 12));
        searchBox.setPrefHeight(40);
        searchBox.setStyle("-fx-background-color: " + WHITE + "; -fx-background-radius: 20; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 20; -fx-border-width: 1.2;");

        HBox sw = new HBox(searchBox);
        sw.setPadding(new Insets(0, 14, 14, 14));
        sw.setStyle("-fx-background-color: " + CREAM_2 + ";");
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        Button tabContacts = new Button("Contacts");
        tabContacts.setPrefHeight(32);
        tabContacts.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        tabContacts.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 16; -fx-cursor: hand; -fx-padding: 0 16;");

        Button tabGroupes = new Button("Groupes");
        tabGroupes.setPrefHeight(32);
        tabGroupes.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        tabGroupes.setStyle("-fx-background-color: transparent; -fx-text-fill: " + INK_LITE + "; -fx-background-radius: 16; -fx-cursor: hand; -fx-padding: 0 16; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 16; -fx-border-width: 1;");

        HBox tabs = new HBox(8, tabContacts, tabGroupes);
        tabs.setPadding(new Insets(0, 14, 12, 14));
        tabs.setStyle("-fx-background-color: " + CREAM_2 + ";");

        userListPanel = new ListView<User>();
        userListPanel.setStyle("-fx-background-color: " + CREAM_2 + "; -fx-border-color: transparent; -fx-padding: 0 0 6 0;");
        VBox.setVgrow(userListPanel, Priority.ALWAYS);
        userListPanel.setCellFactory(lv -> new ListCell<User>() {
            @Override protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null); setText(null);
                    setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                    return;
                }

                boolean online = isUserOnline(user);
                boolean isStranger = !user.isContact();

                StackPane av = isStranger
                        ? makeAvatarInit("?", 48, INK_LITE, 18)
                        : buildFullAvatar(user, online, 48);

                Label nm = new Label(isStranger ? "@" + user.getUsername() : user.getUsername());
                nm.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                nm.setTextFill(Color.web(isStranger ? INK_LITE : INK));

                Circle c = new Circle(3.5, online ? Color.web(SAGE) : Color.web(BORDER_W));
                if (online) glow(c, SAGE, 0.5, 4);
                Label st = new Label(online ? "En ligne" : "Hors ligne");
                st.setFont(Font.font("Segoe UI", 11));
                st.setTextFill(online ? Color.web(SAGE) : Color.web(INK_LITE));
                HBox stRow = new HBox(5, c, st);
                stRow.setAlignment(Pos.CENTER_LEFT);

                if (isStranger) {
                    Label unknownBadge = new Label("Inconnu");
                    unknownBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
                    unknownBadge.setTextFill(Color.web("#7A6000"));
                    unknownBadge.setPadding(new Insets(2, 7, 2, 7));
                    unknownBadge.setStyle("-fx-background-color: #FFFBF0; -fx-background-radius: 8; -fx-border-color: #F0E0A0; -fx-border-radius: 8; -fx-border-width: 1;");
                    stRow = new HBox(5, c, st, unknownBadge);
                    stRow.setAlignment(Pos.CENTER_LEFT);
                }

                VBox info = new VBox(3, nm, stRow);
                info.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(info, Priority.ALWAYS);

                int count = unreadCounts.getOrDefault(user.getId(), 0);
                HBox rightCol = new HBox();
                rightCol.setAlignment(Pos.CENTER_RIGHT);
                HBox.setHgrow(rightCol, Priority.ALWAYS);
                if (count > 0) {
                    Label badge = new Label(String.valueOf(count));
                    badge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
                    badge.setTextFill(Color.WHITE);
                    badge.setMinWidth(20); badge.setMinHeight(20);
                    badge.setAlignment(Pos.CENTER);
                    badge.setPadding(new Insets(0, 5, 0, 5));
                    badge.setStyle("-fx-background-color: #25D366; -fx-background-radius: 10;");
                    rightCol.getChildren().add(badge);
                }

                HBox card = new HBox(12, av, info, rightCol);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(10, 14, 10, 16));
                card.setStyle("-fx-background-color: transparent; -fx-background-radius: 16; -fx-cursor: hand;");
                card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: " + WHITE + "; -fx-background-radius: 16; -fx-cursor: hand;"));
                card.setOnMouseExited(e -> card.setStyle("-fx-background-color: transparent; -fx-background-radius: 16; -fx-cursor: hand;"));

                HBox wrap = new HBox(card);
                wrap.setPadding(new Insets(2, 10, 2, 10));

                ContextMenu contactMenu = new ContextMenu();

                if (!isStranger) {
                    MenuItem deleteContactItem = new MenuItem("🗑  Supprimer le contact");
                    deleteContactItem.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-text-fill: #DC2626; -fx-padding: 6 14;");
                    deleteContactItem.setOnAction(e -> {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Supprimer"); confirm.setHeaderText(null);
                        confirm.setContentText("Supprimer " + user.getUsername() + " de vos contacts ?");
                        confirm.showAndWait().ifPresent(r -> {
                            if (r == ButtonType.OK && client != null && client.getUser() != null) {
                                ContactDAO.deleteContact(client.getUser().getId(), user.getId());
                                if (currentContact != null && currentContact.getId().equals(user.getId())) {
                                    currentContact = null;
                                    Platform.runLater(() -> {
                                        contactNameLabel.setText("Sélectionnez une conversation");
                                        contactStatusLabel.setText("");
                                        showWelcomeState();
                                        pinnedMessages.clear();
                                        refreshPinnedBar();
                                    });
                                }
                                updateUserList(onlineUsers);
                                showToast("Contact supprimé !");
                            }
                        });
                    });
                    contactMenu.getItems().add(deleteContactItem);
                } else {
                    MenuItem addContactItem = new MenuItem("➕  Ajouter aux contacts");
                    addContactItem.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-padding: 6 14;");
                    addContactItem.setOnAction(e -> {
                        if (client != null && client.getUser() != null) {
                            boolean ok = ContactDAO.addContact(client.getUser().getId(), user.getId());
                            if (ok) {
                                user.setContact(true);
                                showToast(user.getUsername() + " ajouté aux contacts !");
                                updateUserList(onlineUsers);
                            } else {
                                showToast("Déjà dans vos contacts.");
                            }
                        }
                    });
                    contactMenu.getItems().add(addContactItem);
                }

                setContextMenu(contactMenu);
                setGraphic(wrap);
                setText(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-cursor: hand;");
            }
        });
        userListPanel.setOnMouseClicked(e -> {
            User s = userListPanel.getSelectionModel().getSelectedItem();
            if (s != null) onContactSelected(s.getUsername());
        });

        groupListPanel = new ListView<String[]>();
        groupListPanel.setStyle("-fx-background-color: " + CREAM_2 + "; -fx-border-color: transparent; -fx-padding: 0 0 6 0;");
        VBox.setVgrow(groupListPanel, Priority.ALWAYS);
        groupListPanel.setVisible(false);
        groupListPanel.setManaged(false);

        groupListPanel.setCellFactory(lv -> new ListCell<String[]>() {
            @Override protected void updateItem(String[] group, boolean empty) {
                super.updateItem(group, empty);
                if (empty || group == null) { setGraphic(null); setText(null); setStyle("-fx-background-color: transparent; -fx-padding: 0;"); return; }

                Label groupAvLbl = new Label("👥");
                groupAvLbl.setFont(Font.font("Segoe UI", 20));
                groupAvLbl.setAlignment(Pos.CENTER);
                groupAvLbl.setPrefSize(48, 48);
                groupAvLbl.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 24; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 24; -fx-border-width: 1;");

                Label nm = new Label(group[1]);
                nm.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                nm.setTextFill(Color.web(INK));

                Label sub = new Label("Groupe · Appuyer pour ouvrir");
                sub.setFont(Font.font("Segoe UI", 11));
                sub.setTextFill(Color.web(INK_LITE));

                VBox info = new VBox(3, nm, sub);
                info.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(info, Priority.ALWAYS);

                HBox card = new HBox(12, groupAvLbl, info);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(10, 14, 10, 16));
                card.setStyle("-fx-background-color: transparent; -fx-background-radius: 16; -fx-cursor: hand;");
                card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: " + WHITE + "; -fx-background-radius: 16; -fx-cursor: hand;"));
                card.setOnMouseExited(e -> card.setStyle("-fx-background-color: transparent; -fx-background-radius: 16; -fx-cursor: hand;"));

                HBox wrap = new HBox(card);
                wrap.setPadding(new Insets(2, 10, 2, 10));
                setGraphic(wrap); setText(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-cursor: hand;");
            }
        });

        groupListPanel.setOnMouseClicked(e -> {
            String[] g = groupListPanel.getSelectionModel().getSelectedItem();
            if (g != null) openGroupChatWindow(g[0], g[1]);
        });

        tabContacts.setOnAction(e -> {
            tabContacts.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 16; -fx-cursor: hand; -fx-padding: 0 16;");
            tabGroupes.setStyle("-fx-background-color: transparent; -fx-text-fill: " + INK_LITE + "; -fx-background-radius: 16; -fx-cursor: hand; -fx-padding: 0 16; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 16; -fx-border-width: 1;");
            userListPanel.setVisible(true); userListPanel.setManaged(true);
            groupListPanel.setVisible(false); groupListPanel.setManaged(false);
        });

        tabGroupes.setOnAction(e -> {
            tabGroupes.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 16; -fx-cursor: hand; -fx-padding: 0 16;");
            tabContacts.setStyle("-fx-background-color: transparent; -fx-text-fill: " + INK_LITE + "; -fx-background-radius: 16; -fx-cursor: hand; -fx-padding: 0 16; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 16; -fx-border-width: 1;");
            userListPanel.setVisible(false); userListPanel.setManaged(false);
            groupListPanel.setVisible(true); groupListPanel.setManaged(true);
            loadMyGroups();
        });

        Button addBtn = new Button("+ Nouveau contact");
        addBtn.setMaxWidth(Double.MAX_VALUE); addBtn.setPrefHeight(44);
        addBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        addBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 0;");
        addBtn.setOnMouseEntered(e -> addBtn.setStyle("-fx-background-color: " + FOREST_MID + "; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 0;"));
        addBtn.setOnMouseExited(e -> addBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 0;"));
        addBtn.setOnAction(e -> showAllUsersDialog());

        VBox pane = new VBox(header, sw, tabs, userListPanel, groupListPanel, addBtn);
        pane.setPrefWidth(290);
        pane.setStyle("-fx-background-color: " + CREAM_2 + "; -fx-border-color: transparent " + BORDER_W + " transparent transparent; -fx-border-width: 0 1 0 0;");
        VBox.setVgrow(userListPanel, Priority.ALWAYS);
        VBox.setVgrow(groupListPanel, Priority.ALWAYS);
        return pane;
    }

    // ═══════════════════════════════════════════════════════════════
    // CHAT ZONE
    // ═══════════════════════════════════════════════════════════════
    private VBox buildChatZone() {
        contactAvatarTop = makeAvatarInit("?", 42, INK_LITE, 16);
        contactNameLabel = new Label("Sélectionnez une conversation");
        contactNameLabel.setFont(Font.font("Georgia", FontWeight.BOLD, 16));
        contactNameLabel.setTextFill(Color.web(INK));
        contactOnlineDot = new Circle(4); contactOnlineDot.setFill(Color.web(BORDER_W));
        contactStatusLabel = new Label(""); contactStatusLabel.setFont(Font.font("Segoe UI", 11)); contactStatusLabel.setTextFill(Color.web(INK_LITE));
        HBox stRow = new HBox(5, contactOnlineDot, contactStatusLabel); stRow.setAlignment(Pos.CENTER_LEFT);
        VBox contactInfo = new VBox(2, contactNameLabel, stRow); contactInfo.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(contactInfo, Priority.ALWAYS);
        clockLabel = new Label("00:00"); clockLabel.setFont(Font.font("Segoe UI", 12)); clockLabel.setTextFill(Color.web(INK_LITE)); clockLabel.setPadding(new Insets(0, 8, 0, 0));
        Button audioBtn = topbarBtn("☎", "Appel audio"); audioBtn.setOnAction(e -> startAudioCall());
        Button videoBtn = topbarBtn("▶", "Appel vidéo"); videoBtn.setOnAction(e -> startVideoCall());
        HBox topBar = new HBox(14, contactAvatarTop, contactInfo, clockLabel, audioBtn, videoBtn);
        topBar.setPrefHeight(72); topBar.setAlignment(Pos.CENTER_LEFT); topBar.setPadding(new Insets(0, 20, 0, 24));
        topBar.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: transparent transparent " + BORDER_W + " transparent; -fx-border-width: 0 0 1 0;");
        DropShadow ts = new DropShadow(); ts.setColor(Color.web(FOREST, 0.04)); ts.setRadius(12); ts.setOffsetY(3); topBar.setEffect(ts);

        pinnedBar = new VBox();
        pinnedBar.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-border-color: transparent transparent " + BORDER_W + " transparent; -fx-border-width: 0 0 1 0; -fx-padding: 8 20 8 20;");
        pinnedBar.setVisible(false); pinnedBar.setManaged(false);

        messageContainer = new VBox(12);
        messageContainer.setPadding(new Insets(24, 20, 24, 20));
        messageContainer.setStyle("-fx-background-color: " + CREAM + ";");
        showWelcomeState();

        messageScrollPane = new ScrollPane(messageContainer);
        messageScrollPane.setFitToWidth(true);
        messageScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScrollPane.setStyle("-fx-background-color: " + CREAM + "; -fx-background: " + CREAM + "; -fx-border-color: transparent;");
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);

        Button attachBtn = buildAttachButton();
        attachBtn.setOnAction(e -> onAttachFile());

        voiceButton = buildMicButton();
        voiceButton.setOnMousePressed(e -> startVoiceRecording());
        voiceButton.setOnMouseReleased(e -> stopAndSendVoice());

        inputField = new TextField();
        inputField.setPromptText("Écrivez quelque chose...");
        inputField.setPrefHeight(50);
        inputField.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0 16; -fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + INK + "; -fx-prompt-text-fill: " + INK_LITE + ";");
        inputField.setOnAction(e -> sendMessage());
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Label sendIco = new Label("→"); sendIco.setFont(Font.font("Georgia", FontWeight.BOLD, 17)); sendIco.setTextFill(Color.WHITE); sendIco.setAlignment(Pos.CENTER);
        StackPane sendBtn = new StackPane(sendIco); sendBtn.setPrefSize(38, 38); sendBtn.setMinSize(38, 38); sendBtn.setMaxSize(38, 38);
        sendBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-background-radius: 19; -fx-cursor: hand;");
        glow(sendBtn, FOREST, 0.3, 12);
        sendBtn.setOnMouseClicked(e -> sendMessage());

        HBox pill = new HBox(4, inputField, sendBtn);
        pill.setAlignment(Pos.CENTER); pill.setPadding(new Insets(6, 6, 6, 4));
        pill.setStyle("-fx-background-color: " + WHITE + "; -fx-background-radius: 28; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 28; -fx-border-width: 1.2;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        replyPreviewBar = new VBox();
        replyPreviewBar.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: transparent transparent " + BORDER_W + " transparent; -fx-border-width: 0 0 1 0; -fx-padding: 10 20 8 20;");
        replyPreviewBar.setVisible(false); replyPreviewBar.setManaged(false);

        HBox inputBar = new HBox(10, attachBtn, voiceButton, pill);
        inputBar.setPadding(new Insets(12, 20, 16, 20)); inputBar.setAlignment(Pos.CENTER);
        inputBar.setStyle("-fx-background-color: " + CREAM + ";");
        HBox.setHgrow(pill, Priority.ALWAYS);

        VBox inputZone = new VBox(replyPreviewBar, inputBar);
        inputZone.setStyle("-fx-background-color: " + CREAM + ";");

        VBox zone = new VBox(topBar, pinnedBar, messageScrollPane, inputZone);
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);
        zone.setStyle("-fx-background-color: " + CREAM + ";");
        return zone;
    }

    // ═══════════════════════════════════════════════════════════════
    // V2 : DIALOGUES GROUPE
    // ═══════════════════════════════════════════════════════════════
    private void showCreateGroupDialog() {
        Stage dialog = new Stage();
        dialog.setTitle("Créer un groupe");
        dialog.setResizable(false);

        final String FOREST    = "#1A3C34";
        final String FOREST_M  = "#2D5A4F";
        final String SAGE      = "#4A7C6F";
        final String MINT_PALE = "#E8F2EF";
        final String CREAM     = "#FAFAF7";
        final String CREAM_2   = "#F3F3EE";
        final String WHITE     = "#FFFFFF";
        final String BORDER    = "#E8E8E2";
        final String INK       = "#1C1C1A";
        final String INK_LITE  = "#9A9A92";
        final String PENDING_BG = "#FFF8E1";
        final String PENDING_TX = "#856404";

        Set<String> selectedIds   = new java.util.LinkedHashSet<String>();
        List<model.User> allUsers = new ArrayList<model.User>();
        List<String[]> pendingInvites = new ArrayList<String[]>();

        try {
            java.lang.reflect.Field f = database.DatabaseManager.class.getDeclaredField("connection");
            f.setAccessible(true);
            java.sql.Connection conn = (java.sql.Connection) f.get(null);
            if (conn != null && !conn.isClosed()) {
                java.sql.ResultSet rs = conn.prepareStatement("SELECT * FROM users ORDER BY username ASC").executeQuery();
                while (rs.next()) {
                    String uid = rs.getString("id");
                    if (uid.equals(client.getUser().getId())) continue;
                    model.User u = new model.User(uid, rs.getString("username"), "");
                    u.setOnline(rs.getBoolean("is_online"));
                    u.setProfilePhoto(rs.getString("profile_photo"));
                    allUsers.add(u);
                }
            }
        } catch (Exception e) {
            allUsers.addAll(ContactDAO.getContacts(client.getUser().getId()));
        }

        Label titleLbl = new Label("Créer un groupe");
        titleLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
        titleLbl.setTextFill(Color.web(INK));

        Label subtitleLbl = new Label("Donnez un nom à votre groupe");
        subtitleLbl.setFont(Font.font("Segoe UI", 12));
        subtitleLbl.setTextFill(Color.web(INK_LITE));

        HBox stepsBar = buildStepsBar(FOREST, SAGE, BORDER, INK_LITE);

        VBox header = new VBox(6, titleLbl, subtitleLbl, stepsBar);
        header.setPadding(new Insets(20, 22, 14, 22));
        header.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: transparent transparent " + BORDER + " transparent; -fx-border-width: 0 0 1 0;");

        Label nameLabel = new Label("NOM DU GROUPE");
        nameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        nameLabel.setTextFill(Color.web(INK_LITE));

        Label emojiLbl = new Label("👥");
        emojiLbl.setFont(Font.font("Segoe UI", 22));
        emojiLbl.setAlignment(Pos.CENTER);
        emojiLbl.setPrefSize(52, 52);
        emojiLbl.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 26; -fx-cursor: hand; -fx-border-color: " + BORDER + "; -fx-border-radius: 26; -fx-border-width: 1;");
        String[] emojis = {"👥", "🎓", "💻", "🚀", "📚", "🎯", "⚡", "🌿"};
        final int[] emojiIdx = {0};
        emojiLbl.setOnMouseClicked(e -> { emojiIdx[0] = (emojiIdx[0] + 1) % emojis.length; emojiLbl.setText(emojis[emojiIdx[0]]); });

        TextField groupNameField = new TextField();
        groupNameField.setPromptText("ex: Équipe Projet G5");
        groupNameField.setPrefHeight(44);
        groupNameField.setStyle("-fx-background-color: #F0F8F5; -fx-border-color: " + BORDER + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14; -fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + INK + "; -fx-prompt-text-fill: " + INK_LITE + ";");
        groupNameField.focusedProperty().addListener((o, old, on) -> groupNameField.setStyle(on
                ? "-fx-background-color: " + WHITE + "; -fx-border-color: " + FOREST + "; -fx-border-width: 1.5; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14; -fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + INK + ";"
                : "-fx-background-color: #F0F8F5; -fx-border-color: " + BORDER + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14; -fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + INK + "; -fx-prompt-text-fill: " + INK_LITE + ";"));
        HBox.setHgrow(groupNameField, Priority.ALWAYS);

        HBox nameRow = new HBox(10, emojiLbl, groupNameField);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        Label descLabel = new Label("DESCRIPTION (OPTIONNEL)");
        descLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        descLabel.setTextFill(Color.web(INK_LITE));

        javafx.scene.control.TextArea descField = new javafx.scene.control.TextArea();
        descField.setPromptText("À quoi sert ce groupe ?");
        descField.setPrefRowCount(2);
        descField.setWrapText(true);
        descField.setStyle("-fx-background-color: #F0F8F5; -fx-border-color: " + BORDER + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 10 14; -fx-font-size: 13px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + INK + ";");

        VBox page1 = new VBox(12, nameLabel, nameRow, descLabel, descField);
        page1.setPadding(new Insets(18, 22, 18, 22));
        page1.setStyle("-fx-background-color: " + CREAM + ";");

        FlowPane chipsPane = new FlowPane(6, 6);
        chipsPane.setPadding(new Insets(0, 0, 8, 0));

        TextField searchField = new TextField();
        searchField.setPromptText("Rechercher un utilisateur...");
        searchField.setPrefHeight(38);
        searchField.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: " + BORDER + "; -fx-border-radius: 19; -fx-background-radius: 19; -fx-padding: 0 14; -fx-font-size: 13px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + INK + "; -fx-prompt-text-fill: " + INK_LITE + ";");

        VBox memberListBox = new VBox(0);
        memberListBox.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: " + BORDER + "; -fx-border-radius: 12; -fx-background-radius: 12;");
        ScrollPane memberScroll = new ScrollPane(memberListBox);
        memberScroll.setFitToWidth(true);
        memberScroll.setPrefHeight(200);
        memberScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        memberScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Label countLbl = new Label("0 sélectionné");
        countLbl.setFont(Font.font("Segoe UI", 12));
        countLbl.setTextFill(Color.web(INK_LITE));

        final Runnable[] renderChipsRef   = {null};
        final Runnable[] renderMembersRef = {null};

        renderChipsRef[0] = () -> {
            chipsPane.getChildren().clear();
            for (String id : selectedIds) {
                model.User u = allUsers.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
                if (u == null) continue;
                Label avLbl = new Label(u.getUsername().substring(0, 1).toUpperCase());
                avLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
                avLbl.setTextFill(Color.web(FOREST));
                avLbl.setPrefSize(22, 22); avLbl.setAlignment(Pos.CENTER);
                avLbl.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 11;");
                Label nameLbl2 = new Label(u.getUsername());
                nameLbl2.setFont(Font.font("Segoe UI", 12)); nameLbl2.setTextFill(Color.web(FOREST));
                Label xLbl = new Label("×");
                xLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14)); xLbl.setTextFill(Color.web(SAGE)); xLbl.setStyle("-fx-cursor: hand;");
                final String uid = id;
                xLbl.setOnMouseClicked(e -> { selectedIds.remove(uid); renderChipsRef[0].run(); renderMembersRef[0].run(); countLbl.setText(selectedIds.size() + " sélectionné" + (selectedIds.size() > 1 ? "s" : "")); });
                HBox chip = new HBox(5, avLbl, nameLbl2, xLbl);
                chip.setAlignment(Pos.CENTER_LEFT); chip.setPadding(new Insets(4, 10, 4, 6));
                chip.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-border-color: #b0d4c8; -fx-border-radius: 20; -fx-background-radius: 20; -fx-border-width: 1;");
                chipsPane.getChildren().add(chip);
            }
            countLbl.setText(selectedIds.size() + " sélectionné" + (selectedIds.size() > 1 ? "s" : ""));
        };

        renderMembersRef[0] = () -> {
            memberListBox.getChildren().clear();
            String filter = searchField.getText().toLowerCase();
            List<model.User> filtered = allUsers.stream().filter(u -> u.getUsername().toLowerCase().contains(filter)).collect(java.util.stream.Collectors.toList());
            for (int i = 0; i < filtered.size(); i++) {
                model.User u = filtered.get(i);
                boolean sel = selectedIds.contains(u.getId());
                Label av = new Label(u.getUsername().substring(0, 1).toUpperCase());
                av.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); av.setTextFill(Color.WHITE);
                av.setPrefSize(38, 38); av.setAlignment(Pos.CENTER);
                av.setStyle("-fx-background-color: #4A7C6F; -fx-background-radius: 19;");
                Label uName = new Label(u.getUsername());
                uName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                uName.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 13px;");
                Circle onlineDot = new Circle(4);
                onlineDot.setFill(u.isOnline() ? Color.web(SAGE) : Color.web("#AAAAAA"));
                Label statusLbl = new Label(u.isOnline() ? "En ligne" : "Hors ligne");
                statusLbl.setFont(Font.font("Segoe UI", 11));
                statusLbl.setStyle("-fx-text-fill: " + (u.isOnline() ? "#4A7C6F" : "#444444") + "; -fx-font-size: 11px;");
                HBox statusRow = new HBox(4, onlineDot, statusLbl); statusRow.setAlignment(Pos.CENTER_LEFT);
                VBox info = new VBox(2, uName, statusRow); info.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(info, Priority.ALWAYS);
                StackPane checkCircle = new StackPane(); checkCircle.setPrefSize(24, 24); checkCircle.setMinSize(24, 24); checkCircle.setMaxSize(24, 24);
                if (sel) {
                    checkCircle.setStyle("-fx-background-color: " + FOREST + "; -fx-background-radius: 12;");
                    Label chk = new Label("✓"); chk.setTextFill(Color.WHITE); chk.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
                    checkCircle.getChildren().add(chk);
                } else { checkCircle.setStyle("-fx-background-color: transparent; -fx-border-color: " + BORDER + "; -fx-border-radius: 12; -fx-border-width: 1.5;"); }
                HBox row = new HBox(12, av, info, checkCircle); row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(10, 14, 10, 14));
                boolean isLast = (i == filtered.size() - 1);
                String borderBot = isLast ? "transparent" : "#DDDDDD";
                row.setStyle("-fx-background-color: " + (sel ? "#D8EDE7" : "#FFFFFF") + "; -fx-cursor: hand; -fx-border-color: transparent transparent " + borderBot + " transparent; -fx-border-width: 0 0 1 0;");
                final String uid = u.getId();
                row.setOnMouseClicked(e -> { if (selectedIds.contains(uid)) selectedIds.remove(uid); else selectedIds.add(uid); renderMembersRef[0].run(); renderChipsRef[0].run(); });
                row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #F0F8F5; -fx-cursor: hand; -fx-border-color: transparent transparent " + borderBot + " transparent; -fx-border-width: 0 0 1 0;"));
                row.setOnMouseExited(e -> row.setStyle("-fx-background-color: " + (selectedIds.contains(uid) ? "#D8EDE7" : "#FFFFFF") + "; -fx-cursor: hand; -fx-border-color: transparent transparent " + borderBot + " transparent; -fx-border-width: 0 0 1 0;"));
                memberListBox.getChildren().add(row);
            }
        };

        searchField.textProperty().addListener((o, old, nv) -> renderMembersRef[0].run());
        renderMembersRef[0].run();

        Label inviteIco = new Label("➕"); inviteIco.setFont(Font.font("Segoe UI", 16));
        Label inviteTxt = new Label("Inviter quelqu'un hors de vos contacts"); inviteTxt.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); inviteTxt.setTextFill(Color.web(SAGE));
        Label inviteSub = new Label("Ils recevront une invitation à accepter"); inviteSub.setFont(Font.font("Segoe UI", 11)); inviteSub.setTextFill(Color.web(INK_LITE));
        VBox inviteInfo = new VBox(2, inviteTxt, inviteSub);
        Label inviteArrow = new Label("›"); inviteArrow.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18)); inviteArrow.setTextFill(Color.web(INK_LITE));
        HBox inviteRow = new HBox(10, inviteIco, inviteInfo, inviteArrow); HBox.setHgrow(inviteInfo, Priority.ALWAYS);
        inviteRow.setAlignment(Pos.CENTER_LEFT); inviteRow.setPadding(new Insets(12, 14, 12, 14));
        inviteRow.setStyle("-fx-background-color: transparent; -fx-border-color: " + BORDER + "; -fx-border-radius: 12; -fx-background-radius: 12; -fx-border-width: 1; -fx-cursor: hand;");
        inviteRow.setOnMouseEntered(e -> inviteRow.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-border-color: #b0d4c8; -fx-border-radius: 12; -fx-background-radius: 12; -fx-border-width: 1; -fx-cursor: hand;"));
        inviteRow.setOnMouseExited(e -> inviteRow.setStyle("-fx-background-color: transparent; -fx-border-color: " + BORDER + "; -fx-border-radius: 12; -fx-background-radius: 12; -fx-border-width: 1; -fx-cursor: hand;"));

        VBox page2 = new VBox(10, chipsPane, searchField, memberScroll, inviteRow);
        page2.setPadding(new Insets(16, 22, 16, 22));
        page2.setStyle("-fx-background-color: " + CREAM + ";");

        Label invFieldLabel = new Label("INVITER PAR USERNAME"); invFieldLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); invFieldLabel.setTextFill(Color.web(INK_LITE));
        TextField inviteUsernameField = new TextField(); inviteUsernameField.setPromptText("ex: othmane123"); inviteUsernameField.setPrefHeight(44);
        inviteUsernameField.setStyle("-fx-background-color: #F0F8F5; -fx-border-color: " + BORDER + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14; -fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + INK + ";");
        Button sendInviteBtn = new Button("Envoyer l'invitation"); sendInviteBtn.setPrefHeight(44); sendInviteBtn.setMaxWidth(Double.MAX_VALUE);
        sendInviteBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); sendInviteBtn.setStyle("-fx-background-color: " + SAGE + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;");
        Label pendingLabel = new Label("INVITATIONS EN ATTENTE"); pendingLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); pendingLabel.setTextFill(Color.web(INK_LITE));
        VBox pendingListBox = new VBox(6);
        sendInviteBtn.setOnAction(e -> {
            String uname = inviteUsernameField.getText().trim(); if (uname.isEmpty()) return;
            model.User found = UserDAO.searchUserByUsername(uname);
            if (found == null) { showToast("Utilisateur introuvable !"); return; }
            if (found.getId().equals(client.getUser().getId())) { showToast("C'est vous-même !"); return; }
            pendingInvites.add(new String[]{found.getId(), found.getUsername()}); inviteUsernameField.clear();
            Label pAv = new Label(found.getUsername().substring(0, 1).toUpperCase()); pAv.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); pAv.setTextFill(Color.web(FOREST)); pAv.setPrefSize(38, 38); pAv.setAlignment(Pos.CENTER); pAv.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 19;");
            Label pName = new Label(found.getUsername()); pName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); pName.setTextFill(Color.web(INK));
            Label pSub = new Label("Invitation envoyée"); pSub.setFont(Font.font("Segoe UI", 11)); pSub.setTextFill(Color.web(INK_LITE));
            VBox pInfo = new VBox(2, pName, pSub); HBox.setHgrow(pInfo, Priority.ALWAYS);
            Label badge = new Label("En attente"); badge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); badge.setTextFill(Color.web(PENDING_TX)); badge.setPadding(new Insets(3, 8, 3, 8)); badge.setStyle("-fx-background-color: " + PENDING_BG + "; -fx-background-radius: 10; -fx-border-color: #ffc107; -fx-border-radius: 10; -fx-border-width: 1;");
            HBox pRow = new HBox(10, pAv, pInfo, badge); pRow.setAlignment(Pos.CENTER_LEFT); pRow.setPadding(new Insets(10, 14, 10, 14)); pRow.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: " + BORDER + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-border-width: 1;");
            pendingListBox.getChildren().add(pRow); showToast("Invitation envoyée à " + found.getUsername() + " !");
        });
        ScrollPane pendingScroll = new ScrollPane(pendingListBox); pendingScroll.setFitToWidth(true); pendingScroll.setPrefHeight(160); pendingScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        VBox page3 = new VBox(12, invFieldLabel, inviteUsernameField, sendInviteBtn, pendingLabel, pendingScroll); page3.setPadding(new Insets(16, 22, 16, 22)); page3.setStyle("-fx-background-color: " + CREAM + ";");

        final int[] currentPage = {1};
        StackPane pageContainer = new StackPane();
        pageContainer.getChildren().addAll(page1, page2, page3);
        page1.setVisible(true); page2.setVisible(false); page3.setVisible(false);

        Button backBtn = new Button("← Retour"); backBtn.setPrefHeight(40); backBtn.setFont(Font.font("Segoe UI", 13));
        backBtn.setStyle("-fx-background-color: transparent; -fx-border-color: " + BORDER + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-text-fill: " + INK + "; -fx-cursor: hand; -fx-padding: 0 18;"); backBtn.setVisible(false);
        Button nextBtn = new Button("Suivant →"); nextBtn.setPrefHeight(40); nextBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        nextBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 0 22;");
        nextBtn.setOnMouseEntered(e -> nextBtn.setStyle("-fx-background-color: " + FOREST_M + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 0 22;"));
        nextBtn.setOnMouseExited(e -> nextBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 0 22;"));
        Label[] stepDots = buildStepDotLabels(stepsBar);

        Runnable updatePage = () -> {
            page1.setVisible(currentPage[0] == 1); page2.setVisible(currentPage[0] == 2); page3.setVisible(currentPage[0] == 3);
            backBtn.setVisible(currentPage[0] > 1); updateSteps(stepDots, currentPage[0], FOREST, SAGE, INK_LITE);
            switch (currentPage[0]) {
                case 1: subtitleLbl.setText("Donnez un nom à votre groupe"); nextBtn.setText("Suivant →"); break;
                case 2: subtitleLbl.setText("Choisissez les membres"); nextBtn.setText("Suivant →"); renderChipsRef[0].run(); break;
                case 3: subtitleLbl.setText("Invitez des utilisateurs"); nextBtn.setText("✓  Créer le groupe"); break;
            }
        };

        inviteRow.setOnMouseClicked(e -> { currentPage[0] = 3; updatePage.run(); });
        nextBtn.setOnAction(e -> {
            if (currentPage[0] == 1) { if (groupNameField.getText().trim().isEmpty()) { showToast("Entrez un nom de groupe !"); return; } currentPage[0] = 2; updatePage.run(); }
            else if (currentPage[0] == 2) { currentPage[0] = 3; updatePage.run(); }
            else {
                String groupName = groupNameField.getText().trim();
                StringBuilder memberIds = new StringBuilder();
                for (String id : selectedIds) { if (memberIds.length() > 0) memberIds.append(","); memberIds.append(id); }
                client.sendCommand("CREATE_GROUP:" + groupName + ":" + memberIds);
                if (!pendingInvites.isEmpty()) { pendingGroupInvites.clear(); pendingGroupInvites.addAll(pendingInvites); pendingGroupName = groupName; }
                showToast("Groupe « " + groupName + " » en cours de création..."); dialog.close();
            }
        });
        backBtn.setOnAction(e -> { if (currentPage[0] > 1) { currentPage[0]--; updatePage.run(); } });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, countLbl, spacer, backBtn, nextBtn); footer.setAlignment(Pos.CENTER_LEFT); footer.setPadding(new Insets(14, 22, 16, 22));
        footer.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: " + BORDER + " transparent transparent transparent; -fx-border-width: 1 0 0 0;");
        VBox root = new VBox(header, pageContainer, footer); root.setStyle("-fx-background-color: " + CREAM + ";");
        Scene scene = new Scene(root, 480, 560); dialog.setScene(scene);
        root.setOpacity(0); FadeTransition ft = new FadeTransition(Duration.millis(250), root); ft.setFromValue(0); ft.setToValue(1); ft.play();
        updatePage.run(); dialog.show();
    }

    private HBox buildStepsBar(String forest, String sage, String border, String inkLite) {
        HBox bar = new HBox(6); bar.setAlignment(Pos.CENTER_LEFT); bar.setPadding(new Insets(10, 0, 0, 0));
        String[] labels = {"Infos", "Membres", "Invitations"};
        for (int i = 0; i < 3; i++) {
            Label dot = new Label(String.valueOf(i + 1)); dot.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11)); dot.setTextFill(Color.WHITE); dot.setPrefSize(22, 22); dot.setAlignment(Pos.CENTER);
            dot.setStyle(i == 0 ? "-fx-background-color: " + forest + "; -fx-background-radius: 11;" : "-fx-background-color: #D0D0C8; -fx-background-radius: 11;");
            Label lbl = new Label(labels[i]); lbl.setFont(Font.font("Segoe UI", 12)); lbl.setTextFill(i == 0 ? Color.web(forest) : Color.web(inkLite));
            HBox step = new HBox(5, dot, lbl); step.setAlignment(Pos.CENTER_LEFT); bar.getChildren().add(step);
            if (i < 2) { Region line = new Region(); line.setPrefSize(24, 1); line.setStyle("-fx-background-color: " + border + ";"); bar.getChildren().add(line); }
        }
        return bar;
    }

    private Label[] buildStepDotLabels(HBox stepsBar) {
        Label[] dots = new Label[3]; int idx = 0;
        for (javafx.scene.Node n : stepsBar.getChildren()) {
            if (n instanceof HBox) { HBox step = (HBox) n; if (!step.getChildren().isEmpty() && step.getChildren().get(0) instanceof Label) { dots[idx++] = (Label) step.getChildren().get(0); } }
        }
        return dots;
    }

    private void updateSteps(Label[] dots, int currentPage, String forest, String sage, String inkLite) {
        if (dots == null) return;
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == null) continue;
            javafx.scene.Node parent = dots[i].getParent();
            Label textLbl = (parent instanceof HBox && ((HBox) parent).getChildren().size() > 1) ? (Label) ((HBox) parent).getChildren().get(1) : null;
            if (i + 1 < currentPage) { dots[i].setText("✓"); dots[i].setStyle("-fx-background-color: " + sage + "; -fx-background-radius: 11;"); if (textLbl != null) textLbl.setTextFill(Color.web(sage)); }
            else if (i + 1 == currentPage) { dots[i].setText(String.valueOf(i + 1)); dots[i].setStyle("-fx-background-color: " + forest + "; -fx-background-radius: 11;"); if (textLbl != null) textLbl.setTextFill(Color.web(forest)); }
            else { dots[i].setText(String.valueOf(i + 1)); dots[i].setStyle("-fx-background-color: #D0D0C8; -fx-background-radius: 11;"); if (textLbl != null) textLbl.setTextFill(Color.web(inkLite)); }
        }
    }

    public void openGroupChat(String groupId, String groupName) {
        this.currentGroupId = groupId; this.currentGroupName = groupName; this.currentContact = null;
        Platform.runLater(() -> { contactNameLabel.setText("👥 " + groupName); contactStatusLabel.setText("Groupe"); contactOnlineDot.setFill(Color.web(SAGE)); messageContainer.getChildren().clear(); client.sendCommand("GET_GROUP_HISTORY:" + groupId); });
    }

    public void displayGroupMessage(String groupId, String senderUsername, String content) {
        if (!groupId.equals(currentGroupId)) return;
        Platform.runLater(() -> {
            Label senderLbl = new Label(senderUsername); senderLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); senderLbl.setTextFill(Color.web(SAGE));
            Label contentLbl = new Label(content); contentLbl.setWrapText(true); contentLbl.setMaxWidth(300); contentLbl.setFont(Font.font("Segoe UI", 13.5)); contentLbl.setTextFill(Color.web(INK));
            String time = timeFmt.format(new Date());
            Label timeLbl = new Label(time); timeLbl.setFont(Font.font("Segoe UI", 9.5)); timeLbl.setTextFill(Color.web(INK_LITE));
            HBox timeRow = new HBox(timeLbl); timeRow.setAlignment(Pos.CENTER_RIGHT);
            boolean mine = senderUsername.equals(client.getUser().getUsername());
            VBox bubble = new VBox(4); if (!mine) bubble.getChildren().add(senderLbl); bubble.getChildren().addAll(contentLbl, timeRow);
            bubble.setPadding(new Insets(10, 14, 8, 14)); bubble.setMaxWidth(330);
            String bg = mine ? SENT_BG : WHITE; String br = mine ? "20 20 6 20" : "6 20 20 20";
            bubble.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: " + br + "; -fx-border-color: " + BORDER_W + "; -fx-border-radius: " + br + "; -fx-border-width: 1;");
            HBox wrapper = new HBox(bubble); wrapper.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT); wrapper.setPadding(mine ? new Insets(2, 6, 2, 70) : new Insets(2, 70, 2, 6));
            messageContainer.getChildren().add(wrapper); messageScrollPane.setVvalue(1.0);
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // SEND MESSAGE
    // ═══════════════════════════════════════════════════════════════
    private void sendMessage() {
        String content = inputField.getText().trim();
        if (content.isEmpty()) return;
        if (currentGroupId != null) { client.sendCommand("GROUP_MSG:" + currentGroupId + ":" + content); displayGroupMessage(currentGroupId, client.getUser().getUsername(), content); clearInput(); return; }
        if (currentContact == null) { showAlert("Sélectionnez d'abord un contact !"); return; }
        Message msg = new Message(content, client.getUser(), currentContact, MessageType.TEXT);
        if (replyingTo != null) msg.setReplyToId(replyingTo.getId());
        client.sendMessage(msg); replyingTo = null; hideReplyPreview(); displayMessage(msg); clearInput();
    }

    // ═══════════════════════════════════════════════════════════════
    // LOGIQUE MÉTIER
    // ═══════════════════════════════════════════════════════════════
    private void onContactSelected(String username) {
        currentGroupId = null; currentGroupName = null;

        List<User> contacts = ContactDAO.getContacts(client.getUser().getId());
        for (User u : contacts) { if (u.getUsername().equals(username)) { currentContact = u; break; } }

        if (currentContact == null || !currentContact.getUsername().equals(username)) {
            List<User> strangers = ContactDAO.getStrangerPartners(client.getUser().getId());
            for (User u : strangers) { if (u.getUsername().equals(username)) { currentContact = u; break; } }
        }

        if (currentContact != null) { unreadCounts.put(currentContact.getId(), 0); userListPanel.refresh(); }

        final boolean isStranger = currentContact != null && !currentContact.isContact();

        Platform.runLater(() -> {
            contactNameLabel.setText(username);
            boolean on = currentContact != null && isUserOnline(currentContact);
            contactOnlineDot.setFill(on ? Color.web(SAGE) : Color.web(BORDER_W));
            if (on) glow(contactOnlineDot, SAGE, 0.5, 5); else contactOnlineDot.setEffect(null);
            contactStatusLabel.setText(on ? "En ligne" : "Hors ligne");
            contactStatusLabel.setTextFill(on ? Color.web(SAGE) : Color.web(INK_LITE));

            StackPane newAv = currentContact != null ? buildFullAvatar(currentContact, on, 42) : makeAvatarInit(username, 42, FOREST, 16);
            HBox tb = (HBox) contactAvatarTop.getParent();
            if (tb != null) { int idx = tb.getChildren().indexOf(contactAvatarTop); if (idx >= 0) { tb.getChildren().set(idx, newAv); contactAvatarTop = newAv; } }

            messageContainer.getChildren().clear();
            if (isStranger) showStrangerBanner(currentContact);

            if (client != null && client.getUser() != null && currentContact != null) {
                List<model.Message> history = MessageDAO.getMessageHistory(client.getUser().getId(), currentContact.getId());
                if (history.isEmpty()) { if (!isStranger) showWelcomeState(); }
                else {
                    pinnedMessages.clear();
                    for (model.Message msg : history) { messageContainer.getChildren().add(createMessageBubble(msg)); if (msg.isPinned() && !pinnedMessages.contains(msg)) pinnedMessages.add(msg); }
                    refreshPinnedBar();
                    for (model.Message msg : history) { if (!isMine(msg) && !"SEEN".equals(msg.getDeliveryStatus())) { client.sendCommand("SEEN:" + msg.getId() + ":" + msg.getSender().getUsername()); } }
                }
                messageScrollPane.setVvalue(1.0);
            }
        });
    }

    private void showStrangerBanner(User stranger) {
        final String BANNER_BG     = "#FFFBF0";
        final String BANNER_BORDER = "#F0E0A0";
        final String BANNER_TEXT   = "#7A6000";

        Label icoLbl = new Label("👤"); icoLbl.setFont(Font.font("Segoe UI", 18));
        Label msgLbl = new Label(stranger.getUsername() + " n'est pas dans vos contacts"); msgLbl.setFont(Font.font("Segoe UI", 12)); msgLbl.setTextFill(Color.web(BANNER_TEXT)); HBox.setHgrow(msgLbl, Priority.ALWAYS);

        Button addBtn = new Button("+ Ajouter"); addBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11)); addBtn.setPrefHeight(30);
        addBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 0 12;");
        addBtn.setOnMouseEntered(e -> addBtn.setStyle("-fx-background-color: " + FOREST_MID + "; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 0 12;"));
        addBtn.setOnMouseExited(e -> addBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 0 12;"));
        addBtn.setOnAction(e -> {
            boolean ok = ContactDAO.addContact(client.getUser().getId(), stranger.getId());
            if (ok) { stranger.setContact(true); showToast(stranger.getUsername() + " ajouté aux contacts !"); updateUserList(onlineUsers); messageContainer.getChildren().removeIf(n -> n instanceof HBox && "stranger-banner".equals(n.getUserData())); }
            else { showToast("Déjà dans vos contacts."); }
        });

        Button ignoreBtn = new Button("Ignorer"); ignoreBtn.setFont(Font.font("Segoe UI", 11)); ignoreBtn.setPrefHeight(30);
        ignoreBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + BANNER_TEXT + "; -fx-border-color: " + BANNER_BORDER + "; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 0 10;");
        ignoreBtn.setOnAction(e -> messageContainer.getChildren().removeIf(n -> n instanceof HBox && "stranger-banner".equals(n.getUserData())));

        HBox banner = new HBox(10, icoLbl, msgLbl, addBtn, ignoreBtn);
        banner.setAlignment(Pos.CENTER_LEFT); banner.setPadding(new Insets(10, 16, 10, 16));
        banner.setStyle("-fx-background-color: " + BANNER_BG + "; -fx-border-color: " + BANNER_BORDER + "; -fx-border-radius: 12; -fx-background-radius: 12; -fx-border-width: 1;");
        banner.setUserData("stranger-banner"); banner.setMaxWidth(Double.MAX_VALUE);
        messageContainer.getChildren().add(0, banner);
    }

    public void updateUserList(List<User> users) {
        Platform.runLater(() -> {
            this.onlineUsers = users != null ? users : new ArrayList<User>();
            if (client != null && client.getUser() != null) {
                String myId = client.getUser().getId();
                List<User> contacts = ContactDAO.getContacts(myId);
                List<User> strangers = ContactDAO.getStrangerPartners(myId);
                userListPanel.getItems().clear();
                for (User c : contacts) {
                    c.setOnline(false);
                    for (User o : onlineUsers) { if (o.getId() != null && o.getId().equals(c.getId())) { c.setOnline(true); break; } }
                    userListPanel.getItems().add(c);
                }
                for (User s : strangers) {
                    s.setOnline(false);
                    for (User o : onlineUsers) { if (o.getId() != null && o.getId().equals(s.getId())) { s.setOnline(true); break; } }
                    userListPanel.getItems().add(s);
                }
            }
            if (currentContact != null) {
                boolean on = isUserOnline(currentContact);
                contactOnlineDot.setFill(on ? Color.web(SAGE) : Color.web(BORDER_W));
                contactStatusLabel.setText(on ? "En ligne" : "Hors ligne");
                contactStatusLabel.setTextFill(on ? Color.web(SAGE) : Color.web(INK_LITE));
            }
        });
    }

    private void showAllUsersDialog() {
        Stage d = new Stage(); d.setTitle("Ajouter un contact");
        Label title = new Label("Tous les utilisateurs"); title.setFont(Font.font("Georgia", FontWeight.BOLD, 16)); title.setTextFill(Color.web(INK));
        TextField sf = new TextField(); sf.setPromptText("Rechercher..."); sf.setPrefHeight(40);
        sf.setStyle("-fx-border-color: " + BORDER_W + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14; -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-background-color: " + CREAM + ";");
        ListView<User> allList = new ListView<User>(); allList.setPrefHeight(300);
        List<User> allUsers = loadAllUsers(); allList.getItems().setAll(allUsers);
        sf.textProperty().addListener((obs, old, nv) -> { List<User> f = new ArrayList<User>(); for (User u : allUsers) if (u.getUsername().toLowerCase().contains(nv.toLowerCase())) f.add(u); allList.getItems().setAll(f); });
        allList.setCellFactory(lv -> new ListCell<User>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); setText(null); setStyle("-fx-background-color: transparent;"); return; }
                boolean online = isUserOnline(u);
                StackPane av = buildFullAvatar(u, online, 46);
                Label nm = new Label(u.getUsername()); nm.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); nm.setTextFill(Color.web(INK));
                Circle dot = new Circle(4); dot.setFill(online ? Color.web(SAGE) : Color.web(INK_LITE));
                Label st = new Label(online ? "En ligne" : "Hors ligne"); st.setFont(Font.font("Segoe UI", 11)); st.setTextFill(online ? Color.web(SAGE) : Color.web(INK_LITE));
                HBox sr = new HBox(5, dot, st); sr.setAlignment(Pos.CENTER_LEFT);
                VBox info = new VBox(2, nm, sr); info.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(info, Priority.ALWAYS);
                Button addBtn = new Button("+ Ajouter"); addBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
                addBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 6 12;");
                addBtn.setOnAction(e -> { if (client != null && client.getUser() != null) { boolean ok = ContactDAO.addContact(client.getUser().getId(), u.getId()); if (ok) { addBtn.setText("✓ Ajouté"); addBtn.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; -fx-background-radius: 8; -fx-padding: 6 12;"); updateUserList(onlineUsers); } else { addBtn.setText("Déjà ajouté"); addBtn.setDisable(true); } } });
                HBox row = new HBox(12, av, info, addBtn); row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(10, 14, 10, 14)); row.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
                row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 10;")); row.setOnMouseExited(e -> row.setStyle("-fx-background-color: white; -fx-background-radius: 10;"));
                setGraphic(row); setText(null); setStyle("-fx-background-color: transparent; -fx-padding: 2 6;");
            }
        });
        VBox layout = new VBox(14, title, sf, allList); layout.setPadding(new Insets(24)); layout.setStyle("-fx-background-color: white;");
        d.setScene(new Scene(layout, 380, 460)); d.show();
    }

    private List<User> loadAllUsers() {
        List<User> result = new ArrayList<User>();
        if (client == null || client.getUser() == null) return result;
        String myId = client.getUser().getId();
        Set<String> exclude = new HashSet<String>(); exclude.add(myId);
        // forEach évite le problème de type inféré dans le for-each
        List<User> myContacts = ContactDAO.getContacts(myId);
        for (User c : myContacts) {
            exclude.add(c.getId());
        }
        try {
            java.lang.reflect.Field f = database.DatabaseManager.class.getDeclaredField("connection");
            f.setAccessible(true);
            java.sql.Connection conn = (java.sql.Connection) f.get(null);
            if (conn == null || conn.isClosed()) return result;
            java.sql.ResultSet rs = conn.prepareStatement("SELECT * FROM users ORDER BY username ASC").executeQuery();
            while (rs.next()) {
                String uid = rs.getString("id"); if (exclude.contains(uid)) continue;
                User u = new User(uid, rs.getString("username"), rs.getString("password"));
                u.setOnline(rs.getBoolean("is_online")); u.setProfilePhoto(rs.getString("profile_photo"));
                result.add(u);
            }
        } catch (Exception e) { for (User u : onlineUsers) if (!exclude.contains(u.getId())) result.add(u); }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // SERVER COMMANDS
    // ═══════════════════════════════════════════════════════════════
    public void handleServerCommand(String command) {
        if (command.startsWith("GROUP_MSG:")) {
            String[] parts = command.split(":", 4);
            if (parts.length == 4) displayGroupMessage(parts[1], parts[2], parts[3]);
        } else if (command.startsWith("GROUP_CREATED:")) {
            String[] parts = command.split(":", 4);
            if (parts.length >= 3) {
                String newGroupId = parts[1]; String newGroupName = parts[2];
                Platform.runLater(() -> showToast("Groupe « " + newGroupName + " » créé !"));
                if (!pendingGroupInvites.isEmpty()) { for (String[] inv : pendingGroupInvites) { client.sendCommand("GROUP_INVITE:" + newGroupId + ":" + newGroupName + ":" + inv[0]); } pendingGroupInvites.clear(); pendingGroupName = ""; }
                loadMyGroups();
            }
        } else if (command.startsWith("MEETING_STARTED:")) {
            String[] parts = command.split(":", 5);
            if (parts.length == 5) { Platform.runLater(() -> { Alert alert = new Alert(Alert.AlertType.CONFIRMATION); alert.setTitle("Réunion"); alert.setHeaderText(null); alert.setContentText(parts[4] + " a démarré une réunion " + parts[3] + ". Rejoindre ?"); alert.showAndWait().ifPresent(r -> { if (r == ButtonType.OK) showToast("Réunion rejointe !"); }); }); }
        } else if (command.startsWith("MEETING_ENDED:")) {
            showToast("La réunion a été terminée.");
        } else if (command.startsWith("GROUP_INVITE_NOTIF:")) {
            String[] parts = command.split(":", 4);
            if (parts.length == 4) { Platform.runLater(() -> showGroupInviteDialog(parts[1], parts[2], parts[3])); }
        } else if (command.startsWith("GROUP_JOIN_SUCCESS:")) {
            String groupId = command.split(":")[1];
            Platform.runLater(() -> { showToast("Vous avez rejoint le groupe !"); client.sendCommand("GET_GROUP_HISTORY:" + groupId); });
        } else if (command.startsWith("MEMBER_JOINED:")) {
            String[] parts = command.split(":", 3); if (parts.length == 3) Platform.runLater(() -> showToast(parts[2] + " a rejoint le groupe !"));
        } else if (command.startsWith("INVITE_REFUSED:")) {
            String[] parts = command.split(":", 3); if (parts.length == 3) Platform.runLater(() -> showToast(parts[2] + " a refusé l'invitation."));
        } else if (command.startsWith("MY_GROUPS")) {
            updateGroupList(command);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BULLES & UI
    // ═══════════════════════════════════════════════════════════════
    private void showWelcomeState() {
        messageContainer.getChildren().clear();
        Circle o3 = new Circle(70); o3.setFill(Color.TRANSPARENT); o3.setStroke(Color.web(FOREST, 0.06)); o3.setStrokeWidth(1);
        Circle o2 = new Circle(50); o2.setFill(Color.TRANSPARENT); o2.setStroke(Color.web(FOREST, 0.08)); o2.setStrokeWidth(1.5);
        Circle o1 = new Circle(32, Color.web(MINT_PALE)); Circle core = new Circle(22, Color.web(FOREST));
        Label wLbl = new Label("W"); wLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 20)); wLbl.setTextFill(Color.WHITE);
        StackPane orb = new StackPane(o3, o2, o1, core, wLbl); orb.setPrefSize(150, 150);
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(4), orb); pulse.setFromX(1); pulse.setToX(1.04); pulse.setAutoReverse(true); pulse.setCycleCount(Animation.INDEFINITE); pulse.play();
        Label title = new Label("WhatsApp"); title.setFont(Font.font("Georgia", FontWeight.BOLD, 28)); title.setTextFill(Color.web(INK));
        Label sub = new Label("Sélectionnez une conversation pour commencer"); sub.setFont(Font.font("Segoe UI", 13)); sub.setTextFill(Color.web(INK_LITE));
        VBox content = new VBox(18, orb, title, sub); content.setAlignment(Pos.CENTER);
        StackPane centered = new StackPane(content); centered.setPrefHeight(520); centered.setAlignment(Pos.CENTER);
        messageContainer.getChildren().add(centered);
    }

    private HBox createMessageBubble(Message message) {
        boolean mine = isMine(message);
        if (message.isDeleted()) return createDeletedBubble(message, mine);
        if (message instanceof model.VoiceMessage) return createVoiceBubble((model.VoiceMessage) message, mine);
        if (message instanceof model.FileMessage) {
            model.FileMessage fm = (model.FileMessage) message;
            if (fm.getFileType() != null && fm.getFileType().startsWith("image/") && fm.getFileData() != null && fm.getFileData().length > 0) {
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(fm.getFileData())));
                iv.setFitWidth(260); iv.setPreserveRatio(true); iv.setStyle("-fx-cursor: hand;"); iv.setOnMouseClicked(e -> openFile(fm));
                String t = timeFmt.format(message.getTimestamp() != null ? message.getTimestamp() : new Date());
                VBox b = new VBox(6, iv, timeRow(t, mine, message)); b.setPadding(new Insets(8));
                bubbleStyle(b, mine, true); HBox hb = wrapBubble(b, mine, message); attachCtxMenu(hb, message, b); return hb;
            }
            return createFileBubble(fm, mine);
        }
        String t = timeFmt.format(message.getTimestamp() != null ? message.getTimestamp() : new Date());
        Label msgLbl = new Label(message.getContent()); msgLbl.setWrapText(true); msgLbl.setMaxWidth(340); msgLbl.setFont(Font.font("Segoe UI", 13.5)); msgLbl.setLineSpacing(1.5); msgLbl.setTextFill(Color.web(INK));
        VBox b = new VBox(5, msgLbl, timeRow(t, mine, message)); b.setPadding(new Insets(12, 16, 10, 16)); b.setMaxWidth(370);
        if (!mine) { Label sl = new Label(message.getSender().getUsername()); sl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); sl.setTextFill(Color.web(SAGE)); b.getChildren().add(0, sl); }
        if (message.getReplyToId() != null) { VBox rq = buildReplyQuote(message.getReplyToId()); if (rq != null) b.getChildren().add(1, rq); }
        bubbleStyle(b, mine, false);
        FadeTransition ftB = new FadeTransition(Duration.millis(180), b); ftB.setFromValue(0.5); ftB.setToValue(1); ftB.play();
        HBox hb = wrapBubble(b, mine, message); attachCtxMenu(hb, message, b); return hb;
    }

    private HBox createDeletedBubble(Message message, boolean mine) {
        Label ico = new Label("⊘"); ico.setFont(Font.font("Segoe UI", 13)); ico.setTextFill(Color.web(INK_LITE));
        Label txt = new Label("Ce message a été supprimé."); txt.setFont(Font.font("Segoe UI", javafx.scene.text.FontPosture.ITALIC, 13)); txt.setTextFill(Color.web(INK_LITE));
        HBox inner = new HBox(6, ico, txt); inner.setAlignment(Pos.CENTER_LEFT);
        String t = timeFmt.format(message.getTimestamp() != null ? message.getTimestamp() : new Date());
        Label tl = new Label(t); tl.setFont(Font.font("Segoe UI", 9.5)); tl.setTextFill(Color.web(INK_LITE));
        HBox timeH = new HBox(tl); timeH.setAlignment(Pos.CENTER_RIGHT);
        VBox b = new VBox(4, inner, timeH); b.setPadding(new Insets(10, 16, 8, 16)); b.setMaxWidth(320);
        b.setStyle("-fx-background-color: " + CREAM_2 + "; -fx-background-radius: 16; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 16; -fx-border-width: 1;");
        return wrapBubble(b, mine, message);
    }

    private HBox createVoiceBubble(model.VoiceMessage vm, boolean mine) {
        Label playIco = new Label("▶"); playIco.setFont(Font.font("Georgia", FontWeight.BOLD, 14)); playIco.setTextFill(Color.WHITE); playIco.setAlignment(Pos.CENTER); playIco.setPadding(new Insets(0, 0, 0, 3));
        StackPane playBtn = new StackPane(playIco); playBtn.setPrefSize(46, 46); playBtn.setMinSize(46, 46); playBtn.setMaxSize(46, 46);
        playBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-background-radius: 23; -fx-cursor: hand;");
        int[] hs = {4, 8, 14, 20, 26, 30, 22, 18, 28, 32, 24, 16, 30, 28, 20, 12, 26, 32, 28, 22, 18, 28, 24, 16, 30, 22, 14, 26, 32, 20, 16, 24, 18, 12, 8, 14, 20, 28, 32, 24};
        String barColor = mine ? SAGE : MINT; String barPlayed = mine ? FOREST : FOREST_MID;
        HBox wave = new HBox(3); wave.setAlignment(Pos.CENTER); wave.setFillHeight(true); wave.setPrefHeight(36); wave.setMaxHeight(36); wave.setMinHeight(36); wave.setMinWidth(160); wave.setClip(new Rectangle(160, 36));
        for (int h : hs) { Region bar = new Region(); bar.setPrefWidth(3.5); bar.setMinWidth(3.5); bar.setMaxWidth(3.5); bar.setPrefHeight(h); bar.setMinHeight(4); bar.setMaxHeight(30); bar.setStyle("-fx-background-color: " + barColor + "; -fx-background-radius: 2;"); wave.getChildren().add(bar); }
        int tot = vm.getDurationSeconds();
        Label durLbl = new Label(String.format("%d:%02d", tot / 60, tot % 60)); durLbl.setFont(Font.font("Segoe UI", 11)); durLbl.setTextFill(Color.web(INK_LITE)); durLbl.setMinWidth(32);
        HBox mainRow = new HBox(12, playBtn, wave, durLbl); mainRow.setAlignment(Pos.CENTER_LEFT);
        String t = timeFmt.format(vm.getTimestamp() != null ? vm.getTimestamp() : new Date());
        HBox tr = timeRow(t, mine, vm); tr.setPadding(new Insets(4, 0, 0, 58));
        VBox b = new VBox(6, mainRow, tr); b.setPadding(new Insets(12, 16, 10, 12)); b.setMinWidth(280); b.setMaxWidth(320);
        final boolean[] playing = {false}; final Timeline[] animRef = {null};
        playBtn.setOnMouseClicked(e -> {
            if (playing[0]) { playing[0] = false; if (animRef[0] != null) animRef[0].stop(); playIco.setText("▶"); resetWave(wave, hs, barColor); return; }
            playing[0] = true; playIco.setText("■");
            Timeline anim = new Timeline(); int size = wave.getChildren().size();
            for (int i = 0; i < size; i++) { Region bar = (Region) wave.getChildren().get(i); double delay = (double) vm.getDurationSeconds() * 1000.0 / size * i; anim.getKeyFrames().add(new KeyFrame(Duration.millis(delay), ev -> { bar.setStyle("-fx-background-color: " + barPlayed + "; -fx-background-radius: 2;"); bar.setPrefHeight(4 + Math.random() * 26); })); }
            anim.play(); animRef[0] = anim;
            new Thread(() -> {
                javax.sound.sampled.Clip clip = null;
                try {
                    byte[] wavBytes = vm.toWavBytes();
                    AudioInputStream ais = AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(wavBytes));
                    clip = AudioSystem.getClip(); clip.open(ais);
                    try { FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN); gain.setValue(Math.min(6.0f, gain.getMaximum())); } catch (Exception ignored) {}
                    final javax.sound.sampled.Clip finalClip = clip;
                    clip.addLineListener(event -> { if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) { Platform.runLater(() -> { playing[0] = false; playIco.setText("▶"); if (animRef[0] != null) animRef[0].stop(); resetWave(wave, hs, barColor); }); finalClip.close(); } });
                    clip.start();
                    while (playing[0] && clip.isRunning()) Thread.sleep(100);
                    if (clip.isRunning()) { clip.stop(); clip.close(); Platform.runLater(() -> { playing[0] = false; playIco.setText("▶"); if (animRef[0] != null) animRef[0].stop(); resetWave(wave, hs, barColor); }); }
                } catch (Exception ex) { ex.printStackTrace(); Platform.runLater(() -> { playing[0] = false; playIco.setText("▶"); if (animRef[0] != null) animRef[0].stop(); resetWave(wave, hs, barColor); }); if (clip != null) clip.close(); }
            }, "voice-playback").start();
        });
        bubbleStyle(b, mine, false);
        HBox hb = wrapBubble(b, mine, vm); attachCtxMenu(hb, vm, b); return hb;
    }

    private void resetWave(HBox wave, int[] hs, String color) { for (int i = 0; i < wave.getChildren().size() && i < hs.length; i++) { Region bar = (Region) wave.getChildren().get(i); bar.setPrefHeight(hs[i]); bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;"); } }

    private HBox createFileBubble(model.FileMessage fm, boolean mine) {
        boolean isPdf = fm.getFileName() != null && fm.getFileName().toLowerCase().endsWith(".pdf");
        Runnable openAction = () -> openFile(fm);
        if (isPdf && fm.getFileData() != null && fm.getFileData().length > 0) {
            VBox pdfBox = new VBox(0); pdfBox.setMaxWidth(220);
            pdfBox.setStyle("-fx-cursor: hand; -fx-background-color: " + WHITE + "; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-border-width: 1;");
            StackPane placeholder = new StackPane(); placeholder.setPrefSize(220, 150); placeholder.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 10 10 0 0;");
            Label loadingLbl = new Label("⏳"); loadingLbl.setFont(Font.font("Segoe UI", 22)); placeholder.getChildren().add(loadingLbl); pdfBox.getChildren().add(placeholder);
            Label icoLbl = new Label("📄"); icoLbl.setFont(Font.font("Segoe UI", 14));
            long sz = fm.getFileData().length;
            String szStr = sz > 1024*1024 ? String.format("%.1f Mo", sz / 1048576.0) : String.format("%.0f Ko", sz / 1024.0);
            Label nmLbl = new Label(fm.getFileName()); nmLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11)); nmLbl.setTextFill(Color.web(INK)); nmLbl.setMaxWidth(120); nmLbl.setTextOverrun(OverrunStyle.ELLIPSIS); HBox.setHgrow(nmLbl, Priority.ALWAYS);
            Label szLbl = new Label(szStr); szLbl.setFont(Font.font("Segoe UI", 10)); szLbl.setTextFill(Color.web(INK_LITE));
            Label badge = new Label("PDF"); badge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9)); badge.setTextFill(Color.WHITE); badge.setPadding(new Insets(1, 5, 1, 5)); badge.setStyle("-fx-background-color: #DC2626; -fx-background-radius: 4;");
            VBox nameCol = new VBox(1, nmLbl, szLbl); HBox.setHgrow(nameCol, Priority.ALWAYS);
            HBox footer = new HBox(6, icoLbl, nameCol, badge); footer.setAlignment(Pos.CENTER_LEFT); footer.setPadding(new Insets(7, 10, 7, 10));
            footer.setStyle("-fx-background-color: " + WHITE + "; -fx-background-radius: 0 0 10 10; -fx-border-color: " + BORDER_W + " transparent transparent transparent; -fx-border-width: 1 0 0 0;");
            pdfBox.getChildren().add(footer); pdfBox.setOnMouseClicked(e -> openAction.run());
            final byte[] pdfBytes = fm.getFileData();
            new Thread(() -> {
                try {
                    try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                        PDFRenderer renderer = new PDFRenderer(doc);
                        BufferedImage bimg = renderer.renderImageWithDPI(0, 100);
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        ImageIO.write(bimg, "PNG", baos);
                        javafx.scene.image.Image fxImg = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(baos.toByteArray()));
                        Platform.runLater(() -> {
                            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(fxImg); iv.setFitWidth(220); iv.setPreserveRatio(true);
                            double h = fxImg.getHeight() * (220.0 / fxImg.getWidth());
                            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(220, Math.min(h, 220)); clip.setArcWidth(20); clip.setArcHeight(20); iv.setClip(clip);
                            pdfBox.getChildren().set(0, iv);
                        });
                    }
                } catch (Exception ex) { Platform.runLater(() -> { Label errLbl = new Label("📄"); errLbl.setFont(Font.font("Segoe UI", 42)); errLbl.setAlignment(Pos.CENTER); StackPane errPane = new StackPane(errLbl); errPane.setPrefSize(220, 120); errPane.setStyle("-fx-background-color: #F0F8F5; -fx-background-radius: 10 10 0 0;"); pdfBox.getChildren().set(0, errPane); }); }
            }, "pdf-preview-chat").start();
            String t = timeFmt.format(fm.getTimestamp() != null ? fm.getTimestamp() : new Date());
            VBox b = new VBox(6, pdfBox, timeRow(t, mine, fm)); b.setPadding(new Insets(8)); b.setMaxWidth(240);
            bubbleStyle(b, mine, false); HBox hb = wrapBubble(b, mine, fm); attachCtxMenu(hb, fm, b); return hb;
        } else {
            Label ico = new Label("📎"); ico.setFont(Font.font("Segoe UI", 20)); ico.setTextFill(Color.web(SAGE)); ico.setPrefSize(44, 44); ico.setAlignment(Pos.CENTER); ico.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 10;");
            Label nm = new Label(fm.getFileName()); nm.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12)); nm.setTextFill(Color.web(INK)); nm.setWrapText(true); nm.setMaxWidth(175);
            Label sz = new Label(fm.getFileSizeFormatted()); sz.setFont(Font.font("Segoe UI", 10)); sz.setTextFill(Color.web(INK_LITE));
            HBox row = new HBox(10, ico, new VBox(2, nm, sz)); row.setAlignment(Pos.CENTER_LEFT); row.setStyle("-fx-cursor: hand;"); row.setOnMouseClicked(e -> openFile(fm));
            String t = timeFmt.format(fm.getTimestamp() != null ? fm.getTimestamp() : new Date());
            VBox b = new VBox(8, row, timeRow(t, mine, fm)); b.setPadding(new Insets(12, 14, 10, 12)); b.setMaxWidth(280);
            bubbleStyle(b, mine, false); HBox hb = wrapBubble(b, mine, fm); attachCtxMenu(hb, fm, b); return hb;
        }
    }

    private void attachCtxMenu(HBox wrapper, Message msg, VBox bubble) {
        ContextMenu cm = new ContextMenu();
        MenuItem replyItem = menuItem("↩  Répondre"); replyItem.setOnAction(e -> { replyingTo = msg; showReplyPreview(msg); inputField.requestFocus(); });
        MenuItem copyItem = menuItem("📋  Copier"); copyItem.setOnAction(e -> { javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard(); javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent(); cc.putString(msg.getContent()); cb.setContent(cc); showToast("Copié !"); });
        MenuItem editItem = menuItem("✎  Modifier"); editItem.setVisible(isMine(msg) && msg.getType() == MessageType.TEXT);
        editItem.setOnAction(e -> { TextInputDialog d = new TextInputDialog(msg.getContent()); d.setTitle("Modifier"); d.setHeaderText(null); d.setContentText("Nouveau contenu :"); d.showAndWait().ifPresent(txt -> { if (!txt.trim().isEmpty()) { msg.setContent(txt.trim()); msg.setEdited(true); Platform.runLater(() -> { int idx = messageContainer.getChildren().indexOf(wrapper); if (idx >= 0) messageContainer.getChildren().set(idx, createMessageBubble(msg)); }); if (currentContact != null) client.sendCommand("EDIT_MSG:" + msg.getId() + ":" + txt.trim() + ":" + currentContact.getUsername()); } }); });
        MenuItem pinItem = menuItem("📌  Épingler"); pinItem.setOnAction(e -> { pinMessage(msg); if (currentContact != null) client.sendCommand("PIN_MSG:" + msg.getId() + ":true:" + currentContact.getUsername()); });
        MenuItem fwdItem = menuItem("→  Transférer"); fwdItem.setOnAction(e -> showForwardDialog(msg));
        MenuItem delItem = menuItem("✕  Supprimer"); delItem.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-text-fill: #DC2626;"); delItem.setVisible(isMine(msg));
        delItem.setOnAction(e -> { Alert a = new Alert(Alert.AlertType.CONFIRMATION); a.setHeaderText(null); a.setContentText("Supprimer ce message ?"); a.showAndWait().ifPresent(r -> { if (r == ButtonType.OK) { msg.setDeleted(true); int idx = messageContainer.getChildren().indexOf(wrapper); if (idx >= 0) messageContainer.getChildren().set(idx, createMessageBubble(msg)); if (currentContact != null) client.sendCommand("DELETE_MSG:" + msg.getId() + ":" + currentContact.getUsername()); } }); });
        cm.getItems().addAll(replyItem, copyItem, editItem, pinItem, fwdItem, new SeparatorMenuItem(), delItem);
        wrapper.setOnContextMenuRequested(e -> cm.show(wrapper, e.getScreenX(), e.getScreenY()));
    }

    private void showForwardDialog(Message message) {
        Stage d = new Stage(); d.setTitle("Transférer le message"); d.setResizable(false);
        Label title = new Label("Transférer vers..."); title.setFont(Font.font("Georgia", FontWeight.BOLD, 16)); title.setTextFill(Color.web(INK));
        TextField searchFld = new TextField(); searchFld.setPromptText("Rechercher un contact ou groupe..."); searchFld.setPrefHeight(40);
        searchFld.setStyle("-fx-background-color: " + CREAM + "; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';");
        ListView<String[]> destList = new ListView<String[]>(); destList.setPrefHeight(280); destList.setStyle("-fx-background-color: " + CREAM_2 + "; -fx-border-color: " + BORDER_W + ";"); VBox.setVgrow(destList, Priority.ALWAYS);
        List<String[]> allItems = new ArrayList<String[]>();
        if (client != null && client.getUser() != null) { for (User u : ContactDAO.getContacts(client.getUser().getId())) allItems.add(new String[]{"CONTACT", u.getId(), u.getUsername()}); for (String[] g : myGroups) allItems.add(new String[]{"GROUP", g[0], g[1]}); }
        destList.getItems().setAll(allItems);
        searchFld.textProperty().addListener((obs, oldVal, newVal) -> { String filter = newVal.trim().toLowerCase(); if (filter.isEmpty()) { destList.getItems().setAll(allItems); return; } List<String[]> filtered = new ArrayList<String[]>(); for (String[] item : allItems) if (item[2].toLowerCase().contains(filter)) filtered.add(item); destList.getItems().setAll(filtered); });
        destList.setCellFactory(lv -> new ListCell<String[]>() {
            @Override protected void updateItem(String[] item, boolean empty) {
                super.updateItem(item, empty); if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label ico = new Label("CONTACT".equals(item[0]) ? "👤" : "👥"); ico.setFont(Font.font("Segoe UI", 18)); ico.setPrefSize(38, 38); ico.setAlignment(Pos.CENTER); ico.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 19;");
                Label name = new Label(item[2]); name.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13)); name.setTextFill(Color.web(INK));
                Label typeLbl = new Label("CONTACT".equals(item[0]) ? "Contact" : "Groupe"); typeLbl.setFont(Font.font("Segoe UI", 10)); typeLbl.setTextFill(Color.web(INK_LITE));
                VBox info = new VBox(2, name, typeLbl); HBox row = new HBox(10, ico, info); row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(6, 12, 6, 12));
                setGraphic(row); setText(null); setStyle("-fx-background-color: transparent;");
                setOnMouseEntered(e -> setStyle("-fx-background-color: " + MINT_PALE + ";")); setOnMouseExited(e -> setStyle("-fx-background-color: transparent;"));
            }
        });
        Button sendBtn = new Button("↗  Transférer"); sendBtn.setMaxWidth(Double.MAX_VALUE); sendBtn.setPrefHeight(42); sendBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        sendBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"); sendBtn.setDisable(true);
        destList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> sendBtn.setDisable(n == null));
        destList.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && destList.getSelectionModel().getSelectedItem() != null) sendBtn.fire(); });
        sendBtn.setOnAction(e -> { String[] selected = destList.getSelectionModel().getSelectedItem(); if (selected == null) return; if ("CONTACT".equals(selected[0])) { User target = new User(selected[1], selected[2], ""); client.sendMessage(new Message(message.getContent(), client.getUser(), target, MessageType.TEXT)); } else { client.sendCommand("GROUP_MSG:" + selected[1] + ":" + message.getContent()); } showToast("Transféré à " + selected[2] + " !"); d.close(); });
        VBox layout = new VBox(12, title, searchFld, destList, sendBtn); layout.setPadding(new Insets(20)); layout.setStyle("-fx-background-color: white;"); VBox.setVgrow(destList, Priority.ALWAYS);
        d.setScene(new Scene(layout, 320, 420)); d.show();
    }

    private VBox buildReplyQuote(String replyToId) {
        for (javafx.scene.Node node : messageContainer.getChildren()) { if (node instanceof HBox) { HBox hb = (HBox) node; if (hb.getUserData() instanceof Message) { Message original = (Message) hb.getUserData(); if (original.getId().equals(replyToId)) { return buildQuoteBox(original); } } } }
        return null;
    }

    private VBox buildQuoteBox(Message original) {
        Region bar = new Region(); bar.setPrefWidth(3); bar.setMinWidth(3); bar.setMaxWidth(3); bar.setStyle("-fx-background-color: " + SAGE + "; -fx-background-radius: 2;");
        Label senderLbl = new Label(original.getSender().getUsername()); senderLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); senderLbl.setTextFill(Color.web(SAGE));
        String preview = original.getContent().length() > 50 ? original.getContent().substring(0, 50) + "…" : original.getContent();
        Label previewLbl = new Label(preview); previewLbl.setFont(Font.font("Segoe UI", 11)); previewLbl.setTextFill(Color.web(INK_MID));
        VBox textCol = new VBox(2, senderLbl, previewLbl);
        HBox inner = new HBox(8, bar, textCol); inner.setPadding(new Insets(6, 10, 6, 8)); inner.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 8;");
        VBox box = new VBox(inner); box.setPadding(new Insets(0, 0, 6, 0)); return box;
    }

    private void showReplyPreview(Message msg) {
        if (replyPreviewBar == null) return;
        replyPreviewBar.getChildren().clear();
        Region bar = new Region(); bar.setPrefWidth(3); bar.setMinWidth(3); bar.setMaxWidth(3); bar.setStyle("-fx-background-color: " + SAGE + "; -fx-background-radius: 2;");
        Label senderLbl = new Label(msg.getSender().getUsername()); senderLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11)); senderLbl.setTextFill(Color.web(SAGE));
        String preview = msg.getContent().length() > 60 ? msg.getContent().substring(0, 60) + "…" : msg.getContent();
        Label previewLbl = new Label(preview); previewLbl.setFont(Font.font("Segoe UI", 12)); previewLbl.setTextFill(Color.web(INK_MID));
        VBox textCol = new VBox(2, senderLbl, previewLbl); textCol.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(textCol, Priority.ALWAYS);
        Button closeBtn = new Button("×"); closeBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18)); closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + INK_LITE + "; -fx-cursor: hand; -fx-padding: 0 6;"); closeBtn.setOnAction(ev -> { replyingTo = null; hideReplyPreview(); });
        HBox inner = new HBox(10, bar, textCol, closeBtn); inner.setAlignment(Pos.CENTER_LEFT); inner.setPadding(new Insets(8, 12, 8, 12)); inner.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 10;");
        replyPreviewBar.getChildren().add(inner); replyPreviewBar.setVisible(true); replyPreviewBar.setManaged(true);
    }

    private void hideReplyPreview() { if (replyPreviewBar != null) { replyPreviewBar.setVisible(false); replyPreviewBar.setManaged(false); replyPreviewBar.getChildren().clear(); } }
    private void pinMessage(Message msg) { if (pinnedMessages.contains(msg)) { showToast("Déjà épinglé !"); return; } pinnedMessages.add(msg); refreshPinnedBar(); showToast("Message épinglé 📌"); }

    private void refreshPinnedBar() {
        if (pinnedBar == null) return;
        pinnedBar.getChildren().clear();
        if (pinnedMessages.isEmpty()) { pinnedBar.setVisible(false); pinnedBar.setManaged(false); return; }
        Label title = new Label("📌  Messages épinglés (" + pinnedMessages.size() + ")"); title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11)); title.setTextFill(Color.web(SAGE));
        VBox list = new VBox(4);
        for (Message m : pinnedMessages) {
            String preview = getPinnedPreview(m);
            Label lbl = new Label("• " + preview); lbl.setFont(Font.font("Segoe UI", 11)); lbl.setTextFill(Color.web(INK_MID)); lbl.setStyle("-fx-cursor: hand;");
            lbl.setOnMouseEntered(e -> lbl.setTextFill(Color.web(FOREST))); lbl.setOnMouseExited(e -> lbl.setTextFill(Color.web(INK_MID))); list.getChildren().add(lbl);
        }
        Button clearBtn = new Button("Tout désépingler"); clearBtn.setFont(Font.font("Segoe UI", 11)); clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + SAGE + "; -fx-cursor: hand; -fx-padding: 2 0;"); clearBtn.setOnAction(e -> { pinnedMessages.clear(); refreshPinnedBar(); });
        pinnedBar.getChildren().addAll(title, list, clearBtn); pinnedBar.setVisible(true); pinnedBar.setManaged(true);
    }

    private String getPinnedPreview(Message m) {
        if (m instanceof model.VoiceMessage) { model.VoiceMessage vm = (model.VoiceMessage) m; int dur = vm.getDurationSeconds(); return "🎤 Message vocal (" + String.format("%d:%02d", dur / 60, dur % 60) + ")"; }
        if (m instanceof model.FileMessage) { model.FileMessage fm = (model.FileMessage) m; String name = fm.getFileName() != null ? fm.getFileName() : "Fichier"; return "📎 " + name; }
        String content = m.getContent(); if (content == null || content.isEmpty()) return "(vide)";
        return content.length() > 50 ? content.substring(0, 50) + "…" : content;
    }

    private void bubbleStyle(Region b, boolean mine, boolean img) {
        String bg = mine ? SENT_BG : WHITE; String br = img ? "16" : (mine ? "20 20 6 20" : "6 20 20 20");
        b.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: " + br + "; -fx-border-color: " + (mine ? "rgba(74,124,111,0.15)" : BORDER_W) + "; -fx-border-radius: " + br + "; -fx-border-width: 1;");
        DropShadow sh = new DropShadow(); sh.setColor(Color.web(FOREST, 0.06)); sh.setRadius(8); sh.setOffsetY(2); b.setEffect(sh);
    }

    private HBox timeRow(String time, boolean mine, Message msg) {
        Label tl = new Label(time); tl.setFont(Font.font("Segoe UI", 9.5)); tl.setTextFill(Color.web(INK_LITE));
        HBox row = new HBox(4, tl); row.setAlignment(Pos.CENTER_RIGHT);
        if (mine) {
            String status = msg.getDeliveryStatus(); Label tick;
            if ("SEEN".equals(status)) { tick = new Label("✓✓"); tick.setTextFill(Color.web(TICK_BLUE)); }
            else if ("DELIVERED".equals(status)) { tick = new Label("✓✓"); tick.setTextFill(Color.web(TICK_GREY)); }
            else { tick = new Label("✓"); tick.setTextFill(Color.web(TICK_GREY)); }
            tick.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5)); row.getChildren().add(tick);
        }
        return row;
    }

    private HBox wrapBubble(Region b, boolean mine, Message message) {
        HBox h = new HBox(b); h.setUserData(message); h.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT); h.setPadding(mine ? new Insets(2, 6, 2, 70) : new Insets(2, 70, 2, 6)); return h;
    }

    private StackPane makeAvatarInit(String name, int size, String bg, int fontSize) {
        String init = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "?";
        Label l = new Label(init); l.setFont(Font.font("Georgia", FontWeight.BOLD, fontSize)); l.setTextFill(Color.WHITE);
        StackPane p = new StackPane(l); p.setPrefSize(size, size); p.setMinSize(size, size); p.setMaxSize(size, size);
        p.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: " + (size / 2) + ";"); return p;
    }

    private StackPane buildFullAvatar(User user, boolean online, int size) {
        StackPane av;
        if (user.getProfilePhoto() != null && !user.getProfilePhoto().isEmpty()) {
            try { byte[] bytes = java.util.Base64.getDecoder().decode(user.getProfilePhoto()); javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(); iv.setFitWidth(size); iv.setFitHeight(size); iv.setPreserveRatio(false); iv.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes))); Circle clip = new Circle(size / 2.0, size / 2.0, size / 2.0); iv.setClip(clip); av = new StackPane(iv); }
            catch (Exception ex) { av = makeAvatarInit(user.getUsername(), size, FOREST_MID, size / 3); }
        } else { av = makeAvatarInit(user.getUsername(), size, FOREST_MID, size / 3); }
        av.setPrefSize(size, size); av.setMinSize(size, size); av.setMaxSize(size, size);
        Circle dot = new Circle(5.5); dot.setFill(online ? Color.web(SAGE) : Color.web(BORDER_W)); dot.setStroke(Color.WHITE); dot.setStrokeWidth(1.8); if (online) glow(dot, SAGE, 0.5, 5);
        StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT); av.getChildren().add(dot); return av;
    }

    private Button navPill(String icon, boolean active, String tip) {
        Button btn = new Button(icon); btn.setFont(Font.font("Georgia", 18)); btn.setPrefSize(46, 46);
        btn.setStyle(active ? "-fx-background-color: " + WHITE + "; -fx-text-fill: " + FOREST + "; -fx-background-radius: 12; -fx-cursor: hand;" : "-fx-background-color: transparent; -fx-text-fill: " + INK_LITE + "; -fx-background-radius: 12; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + WHITE + "; -fx-text-fill: " + FOREST + "; -fx-background-radius: 12; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle(active ? "-fx-background-color: " + WHITE + "; -fx-text-fill: " + FOREST + "; -fx-background-radius: 12; -fx-cursor: hand;" : "-fx-background-color: transparent; -fx-text-fill: " + INK_LITE + "; -fx-background-radius: 12; -fx-cursor: hand;"));
        Tooltip.install(btn, new Tooltip(tip)); return btn;
    }

    private Button topbarBtn(String icon, String tip) {
        Button btn = new Button(icon); btn.setFont(Font.font("Georgia", 15)); btn.setPrefSize(38, 38);
        btn.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; -fx-cursor: hand; -fx-background-radius: 19;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 19;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; -fx-cursor: hand; -fx-background-radius: 19;"));
        Tooltip.install(btn, new Tooltip(tip)); return btn;
    }

    private Button buildAttachButton() {
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(26, 26);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(Color.web(INK_LITE)); gc.setLineWidth(2.2); gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND); gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        drawTrombone(gc);
        StackPane iconPane = new StackPane(canvas); iconPane.setPrefSize(48, 48);
        iconPane.setStyle("-fx-background-color: " + WHITE + "; -fx-background-radius: 24; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 24; -fx-border-width: 1.2; -fx-cursor: hand;");
        Button btn = new Button(); btn.setGraphic(iconPane); btn.setPrefSize(48, 48); btn.setMinSize(48, 48); btn.setMaxSize(48, 48);
        btn.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-color: transparent; -fx-cursor: hand;");
        iconPane.setOnMouseEntered(e -> { iconPane.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 24; -fx-border-color: " + SAGE + "; -fx-border-radius: 24; -fx-border-width: 1.2; -fx-cursor: hand;"); gc.clearRect(0, 0, 26, 26); gc.setStroke(Color.web(SAGE)); drawTrombone(gc); });
        iconPane.setOnMouseExited(e -> { iconPane.setStyle("-fx-background-color: " + WHITE + "; -fx-background-radius: 24; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 24; -fx-border-width: 1.2; -fx-cursor: hand;"); gc.clearRect(0, 0, 26, 26); gc.setStroke(Color.web(INK_LITE)); drawTrombone(gc); });
        Tooltip.install(btn, new Tooltip("Joindre un fichier")); return btn;
    }

    private void drawTrombone(javafx.scene.canvas.GraphicsContext gc) {
        gc.beginPath(); gc.moveTo(18, 22); gc.arcTo(22, 22, 22, 10, 6); gc.arcTo(22, 4, 8, 4, 7); gc.arcTo(4, 4, 4, 18, 7); gc.arcTo(4, 24, 14, 24, 6); gc.lineTo(18, 24); gc.stroke();
        gc.beginPath(); gc.moveTo(14, 20); gc.arcTo(18, 20, 18, 10, 4); gc.arcTo(18, 7, 10, 7, 4); gc.arcTo(7, 7, 7, 18, 4); gc.stroke();
    }

    private Button buildMicButton() {
        Label micIco = new Label("\uD83C\uDF99"); micIco.setFont(Font.font("Segoe UI", 20)); micIco.setTextFill(Color.WHITE); micIco.setAlignment(Pos.CENTER);
        StackPane circle = new StackPane(micIco); circle.setPrefSize(48, 48); circle.setMinSize(48, 48); circle.setMaxSize(48, 48);
        circle.setStyle("-fx-background-color: " + SAGE + "; -fx-background-radius: 24; -fx-cursor: hand;");
        Button btn = new Button(); btn.setGraphic(circle); btn.setPrefSize(48, 48); btn.setMinSize(48, 48); btn.setMaxSize(48, 48);
        btn.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-color: transparent; -fx-cursor: hand;");
        Tooltip.install(btn, new Tooltip("Message vocal")); return btn;
    }

    private void glow(javafx.scene.Node n, String hex, double alpha, double radius) { DropShadow s = new DropShadow(); s.setColor(Color.web(hex, alpha)); s.setRadius(radius); s.setSpread(0.08); n.setEffect(s); }
    private void startClock() { Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> clockLabel.setText(new java.text.SimpleDateFormat("HH:mm").format(new Date())))); clock.setCycleCount(Animation.INDEFINITE); clock.play(); clockLabel.setText(new java.text.SimpleDateFormat("HH:mm").format(new Date())); }
    private boolean isUserOnline(User user) { if (user == null) return false; for (User o : onlineUsers) if (o.getId() != null && o.getId().equals(user.getId())) return true; return false; }
    private boolean isMine(Message msg) { return client != null && client.getUser() != null && msg.getSender().getUsername().equals(client.getUser().getUsername()); }
    private MenuItem menuItem(String text) { MenuItem m = new MenuItem(text); m.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-padding: 6 14;"); return m; }

    private void showToast(String msg) {
        Label t = new Label(msg); t.setFont(Font.font("Segoe UI", 12)); t.setTextFill(Color.WHITE); t.setPadding(new Insets(8, 20, 8, 20));
        t.setStyle("-fx-background-color: " + INK + "; -fx-background-radius: 20; -fx-opacity: 0.9;");
        StackPane o = new StackPane(t); o.setAlignment(Pos.BOTTOM_CENTER); o.setMouseTransparent(true);
        messageContainer.getChildren().add(o);
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(2), e -> messageContainer.getChildren().remove(o))); tl.play();
    }

    private void showAlert(String msg) { Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.WARNING); a.setTitle("Attention"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait(); }); }

    public void displayMessage(Message message) {
        Platform.runLater(() -> {
            messageContainer.getChildren().removeIf(n -> n instanceof StackPane && ((StackPane) n).getPrefHeight() > 400);
            boolean isCurrentChat = currentContact != null && currentContact.getId().equals(message.getSender().getId());
            if (!isCurrentChat && !isMine(message)) {
                String senderId = message.getSender().getId();
                unreadCounts.put(senderId, unreadCounts.getOrDefault(senderId, 0) + 1);
                updateUserList(onlineUsers);
            } else {
                messageContainer.getChildren().add(createMessageBubble(message));
                messageScrollPane.setVvalue(1.0);
            }
        });
    }

    public void clearInput() { Platform.runLater(() -> inputField.clear()); }

    private void onAttachFile() { FileChooser fc = new FileChooser(); fc.setTitle("Choisir un fichier"); fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Tous les fichiers", "*.*")); File sel = fc.showOpenDialog(stage); if (sel != null) sendFile(sel); }
    public void sendFile() { onAttachFile(); }
    private void openFile(model.FileMessage fm) { try { java.io.File tmp = new java.io.File(System.getProperty("java.io.tmpdir"), fm.getFileName()); java.nio.file.Files.write(tmp.toPath(), fm.getFileData()); java.awt.Desktop.getDesktop().open(tmp); } catch (java.io.IOException e) { showAlert("Impossible d'ouvrir !"); } }
    private String getFileType(File f) { String n = f.getName().toLowerCase(); if (n.endsWith(".png")) return "image/png"; if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg"; if (n.endsWith(".pdf")) return "application/pdf"; try { String tp = java.nio.file.Files.probeContentType(f.toPath()); return tp != null ? tp : "application/octet-stream"; } catch (java.io.IOException e) { return "application/octet-stream"; } }
    private void sendFile(File file) { if (currentContact == null) { showAlert("Sélectionnez d'abord un contact !"); return; } model.FileMessage fm = new model.FileMessage(client.getUser(), currentContact, file.getName(), file.length(), getFileType(file), file.getAbsolutePath()); client.sendMessage(fm); Platform.runLater(() -> { messageContainer.getChildren().removeIf(n -> n instanceof StackPane && ((StackPane) n).getPrefHeight() > 400); messageContainer.getChildren().add(createMessageBubble(fm)); messageScrollPane.setVvalue(1.0); }); }
    public void displayFile(model.FileMessage fm) { Platform.runLater(() -> { messageContainer.getChildren().add(createFileBubble(fm, false)); messageScrollPane.setVvalue(1.0); }); }
    private void startAudioCall() { if (currentContact == null) { showAlert("Sélectionnez d'abord un contact !"); return; } client.startCall(currentContact, model.CallType.AUDIO); openCallWindow(); }
    private void startVideoCall() { if (currentContact == null) { showAlert("Sélectionnez d'abord un contact !"); return; } client.startCall(currentContact, model.CallType.VIDEO); openCallWindow(); }
    private void openCallWindow() { Platform.runLater(() -> { try { call.Call cur = client.getCurrentCall(); if (cur == null) return; CallWindow cw = new CallWindow(client, cur); cw.setCallerMode(true); client.setCallWindow(cw); cw.start(new Stage()); } catch (Exception e) { e.printStackTrace(); } }); }

    private void startVoiceRecording() {
        try {
            StackPane circle = (StackPane) ((Button) voiceButton).getGraphic();
            circle.setStyle("-fx-background-color: #DC2626; -fx-background-radius: 24; -fx-cursor: hand;");
            javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(48000, 16, 1, true, false);
            javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(javax.sound.sampled.TargetDataLine.class, fmt);
            micLine = (javax.sound.sampled.TargetDataLine) javax.sound.sampled.AudioSystem.getLine(info);
            micLine.open(fmt, 8192); micLine.start();
            voiceBuffer = new java.io.ByteArrayOutputStream(); recordingStartTime = System.currentTimeMillis();
            new Thread(() -> { byte[] buf = new byte[4096]; while (micLine != null && micLine.isOpen()) { int r = micLine.read(buf, 0, buf.length); if (r > 0) voiceBuffer.write(buf, 0, r); } }).start();
        } catch (Exception e) { showAlert("Impossible d'accéder au micro !"); }
    }

    private void stopAndSendVoice() {
        if (micLine == null) return;
        StackPane circle = (StackPane) ((Button) voiceButton).getGraphic();
        circle.setStyle("-fx-background-color: " + SAGE + "; -fx-background-radius: 24; -fx-cursor: hand;");
        micLine.stop(); micLine.close(); micLine = null;
        byte[] audio = voiceBuffer.toByteArray();
        int dur = (int) ((System.currentTimeMillis() - recordingStartTime) / 1000);
        if (audio.length == 0 || dur == 0) return;
        if (currentContact == null) { showAlert("Sélectionnez un contact !"); return; }
        audio = normalizePcm(audio);
        model.VoiceMessage vm = new model.VoiceMessage(client.getUser(), currentContact, audio, dur); client.sendMessage(vm);
        Platform.runLater(() -> { messageContainer.getChildren().removeIf(n -> n instanceof StackPane && ((StackPane) n).getPrefHeight() > 400); messageContainer.getChildren().add(createVoiceBubble(vm, true)); messageScrollPane.setVvalue(1.0); });
    }

    private byte[] normalizePcm(byte[] pcm) {
        if (pcm.length < 2) return pcm;
        int maxAmp = 1;
        for (int i = 0; i + 1 < pcm.length; i += 2) { int sample = (pcm[i] & 0xFF) | (pcm[i + 1] << 8); int abs = Math.abs(sample); if (abs > maxAmp) maxAmp = abs; }
        if (maxAmp == 0) return pcm;
        float gain = (32767 * 0.95f) / maxAmp; if (gain <= 1.0f) return pcm;
        byte[] out = new byte[pcm.length];
        for (int i = 0; i + 1 < pcm.length; i += 2) { int sample = (pcm[i] & 0xFF) | (pcm[i + 1] << 8); int amplified = (int) Math.max(-32768, Math.min(32767, sample * gain)); out[i] = (byte) (amplified & 0xFF); out[i + 1] = (byte) ((amplified >> 8) & 0xFF); }
        return out;
    }

    public void handleRemoteDelete(String messageId) { Platform.runLater(() -> { for (int i = 0; i < messageContainer.getChildren().size(); i++) { javafx.scene.Node node = messageContainer.getChildren().get(i); if (node instanceof HBox) { HBox hb = (HBox) node; if (hb.getUserData() instanceof Message) { Message m = (Message) hb.getUserData(); if (m.getId().equals(messageId)) { m.setDeleted(true); messageContainer.getChildren().set(i, createMessageBubble(m)); break; } } } } }); }
    public void handleRemoteEdit(String messageId, String newContent) { Platform.runLater(() -> { for (int i = 0; i < messageContainer.getChildren().size(); i++) { javafx.scene.Node node = messageContainer.getChildren().get(i); if (node instanceof HBox) { HBox hb = (HBox) node; if (hb.getUserData() instanceof Message) { Message m = (Message) hb.getUserData(); if (m.getId().equals(messageId)) { m.setContent(newContent); m.setEdited(true); messageContainer.getChildren().set(i, createMessageBubble(m)); break; } } } } }); }
    public void handleRemotePin(String messageId, boolean pinned) { Platform.runLater(() -> { for (javafx.scene.Node node : messageContainer.getChildren()) { if (node instanceof HBox) { HBox hb = (HBox) node; if (hb.getUserData() instanceof Message) { Message m = (Message) hb.getUserData(); if (m.getId().equals(messageId)) { m.setPinned(pinned); if (pinned) pinMessage(m); break; } } } } }); }
    public void handleDelivered(String messageId) { Platform.runLater(() -> { for (int i = 0; i < messageContainer.getChildren().size(); i++) { javafx.scene.Node node = messageContainer.getChildren().get(i); if (node instanceof HBox) { HBox hb = (HBox) node; if (hb.getUserData() instanceof Message) { Message m = (Message) hb.getUserData(); if (m.getId().equals(messageId)) { m.setDeliveryStatus("DELIVERED"); messageContainer.getChildren().set(i, createMessageBubble(m)); break; } } } } }); }
    public void handleSeen(String messageId) { Platform.runLater(() -> { for (int i = 0; i < messageContainer.getChildren().size(); i++) { javafx.scene.Node node = messageContainer.getChildren().get(i); if (node instanceof HBox) { HBox hb = (HBox) node; if (hb.getUserData() instanceof Message) { Message m = (Message) hb.getUserData(); if (m.getId().equals(messageId)) { m.setDeliveryStatus("SEEN"); messageContainer.getChildren().set(i, createMessageBubble(m)); break; } } } } }); }

    private void showGroupInviteDialog(String groupId, String groupName, String inviterUsername) {
        Stage dialog = new Stage(); dialog.setTitle("Invitation de groupe"); dialog.setResizable(false);
        final String FOREST = "#1A3C34"; final String SAGE = "#4A7C6F"; final String MINT_PALE = "#E8F2EF";
        final String CREAM = "#FAFAF7"; final String WHITE = "#FFFFFF"; final String BORDER = "#E8E8E2";
        final String INK = "#1C1C1A"; final String INK_LITE = "#9A9A92";
        Label groupIco = new Label("👥"); groupIco.setFont(Font.font("Segoe UI", 36)); groupIco.setAlignment(Pos.CENTER); groupIco.setPrefSize(72, 72); groupIco.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 36;");
        Label titleLbl = new Label("Invitation de groupe"); titleLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 17)); titleLbl.setTextFill(Color.web(INK)); titleLbl.setAlignment(Pos.CENTER);
        Label groupLbl = new Label("« " + groupName + " »"); groupLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 15)); groupLbl.setTextFill(Color.web(FOREST)); groupLbl.setAlignment(Pos.CENTER);
        Label inviterLbl = new Label(inviterUsername + " vous invite à rejoindre ce groupe"); inviterLbl.setFont(Font.font("Segoe UI", 13)); inviterLbl.setTextFill(Color.web(INK_LITE)); inviterLbl.setAlignment(Pos.CENTER); inviterLbl.setWrapText(true);
        VBox infoBox = new VBox(8, groupIco, titleLbl, groupLbl, inviterLbl); infoBox.setAlignment(Pos.CENTER); infoBox.setPadding(new Insets(28, 24, 16, 24));
        Region sep = new Region(); sep.setPrefHeight(1); sep.setStyle("-fx-background-color: " + BORDER + ";");
        Button refuseBtn = new Button("Refuser"); refuseBtn.setPrefHeight(44); refuseBtn.setMaxWidth(Double.MAX_VALUE); refuseBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        refuseBtn.setStyle("-fx-background-color: transparent; -fx-border-color: " + BORDER + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-text-fill: " + INK + "; -fx-cursor: hand;");
        refuseBtn.setOnMouseEntered(e -> refuseBtn.setStyle("-fx-background-color: #FDECEA; -fx-border-color: #DC2626; -fx-border-radius: 10; -fx-background-radius: 10; -fx-text-fill: #DC2626; -fx-cursor: hand;"));
        refuseBtn.setOnMouseExited(e -> refuseBtn.setStyle("-fx-background-color: transparent; -fx-border-color: " + BORDER + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-text-fill: " + INK + "; -fx-cursor: hand;"));
        refuseBtn.setOnAction(e -> { client.sendCommand("GROUP_INVITE_RESPONSE:" + groupId + ":REFUSE:" + inviterUsername); showToast("Invitation refusée."); dialog.close(); });
        Button acceptBtn = new Button("✓  Rejoindre le groupe"); acceptBtn.setPrefHeight(44); acceptBtn.setMaxWidth(Double.MAX_VALUE); acceptBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        acceptBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;");
        acceptBtn.setOnMouseEntered(e -> acceptBtn.setStyle("-fx-background-color: " + SAGE + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));
        acceptBtn.setOnMouseExited(e -> acceptBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));
        acceptBtn.setOnAction(e -> { client.sendCommand("GROUP_INVITE_RESPONSE:" + groupId + ":ACCEPT:" + inviterUsername); dialog.close(); });
        VBox btnBox = new VBox(10, acceptBtn, refuseBtn); btnBox.setPadding(new Insets(16, 24, 24, 24));
        VBox root = new VBox(infoBox, sep, btnBox); root.setStyle("-fx-background-color: " + WHITE + ";");
        root.setOpacity(0); Scene scene = new Scene(root, 340, 320); dialog.setScene(scene); dialog.show();
        FadeTransition ft = new FadeTransition(Duration.millis(200), root); ft.setFromValue(0); ft.setToValue(1); ft.play();
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(1.2), groupIco); pulse.setFromX(1); pulse.setToX(1.06); pulse.setFromY(1); pulse.setToY(1.06); pulse.setAutoReverse(true); pulse.setCycleCount(javafx.animation.Animation.INDEFINITE); pulse.play();
    }

    private void loadMyGroups() { if (client != null && client.getUser() != null) { client.sendCommand("GET_MY_GROUPS:" + client.getUser().getId()); } }

    public void updateGroupList(String serverResponse) {
        Platform.runLater(() -> {
            myGroups.clear(); groupListPanel.getItems().clear();
            if (serverResponse.equals("MY_GROUPS")) return;
            String[] parts = serverResponse.split("\\|");
            for (int i = 1; i < parts.length; i++) { String[] g = parts[i].split(";"); if (g.length >= 2) { myGroups.add(g); groupListPanel.getItems().add(g); } }
        });
    }

    private void openGroupChatWindow(String groupId, String groupName) {
        Platform.runLater(() -> { try { GroupChatWindow gcw = new GroupChatWindow(client, groupId, groupName); gcw.start(new Stage()); } catch (Exception e) { e.printStackTrace(); } });
    }

    public static void main(String[] args) { launch(args); }
}