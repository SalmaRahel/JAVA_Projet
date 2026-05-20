package ui;

import client.Client;
import database.GroupDAO;
import model.User;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import javafx.scene.control.OverrunStyle;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

public class GroupChatWindow extends Application {

    // ── Palette identique à ChatWindow ────────────────────────────
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
    private static final String RED        = "#DC2626";
    private static final String RED_PALE   = "#FDECEA";

    private Client client;
    private String groupId;
    private String groupName;
    private String groupCreatorId = "";
    private boolean isAdmin = false;

    private VBox messageContainer;
    private ScrollPane messageScrollPane;
    private TextField inputField;
    private Label memberCountLabel;
    private Label groupNameTopLabel;
    private VBox membersSidebar;
    private boolean sidebarVisible = false;

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
    private ListView<String[]> membersList;

    public String getGroupName() { return groupName; }

    // Map messageId → HBox wrapper
    private final Map<String, HBox> messageNodes = new HashMap<>();

    // Reply
    private String replyToId   = null;
    private String replyToText = null;
    private HBox   replyBar;
    private Label  replyLabel;

    // Pinned bar (comme ChatWindow)
    private VBox pinnedBar;
    private final List<String[]> pinnedMessages = new ArrayList<>(); // [messageId, content, sender]

    // Enregistrement vocal
    private TargetDataLine micLine    = null;
    private boolean isRecording       = false;
    private Thread  recordThread      = null;
    private ByteArrayOutputStream recordedAudio = null;
    private long    recordingStart    = 0;

    public GroupChatWindow(Client client, String groupId, String groupName) {
        this.client    = client;
        this.groupId   = groupId;
        this.groupName = groupName;
    }
    public GroupChatWindow() {}

    // ═══════════════════════════════════════════════════════════════
    // START
    // ═══════════════════════════════════════════════════════════════
    @Override
    public void start(Stage stage) {
        stage.setTitle("👥 " + groupName);
        stage.setMinWidth(760);
        stage.setMinHeight(560);

        HBox root = new HBox();
        VBox chatZone = buildChatZone();
        membersSidebar = buildMembersSidebar();
        membersSidebar.setVisible(false);
        membersSidebar.setManaged(false);

        HBox.setHgrow(chatZone, Priority.ALWAYS);
        root.getChildren().addAll(chatZone, membersSidebar);
        root.setStyle("-fx-background-color: " + CREAM + ";");

        Scene scene = new Scene(root, 820, 640);
        stage.setScene(scene);

        root.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(350), root);
        ft.setFromValue(0); ft.setToValue(1); ft.play();

        stage.show();

        loadGroupInfo();
        loadGroupMembersWithRoles();
        loadGroupHistory();

        if (client != null) client.setGroupChatWindow(this);
        stage.setOnCloseRequest(e -> { if (client != null) client.setGroupChatWindow(null); });
    }

    // ═══════════════════════════════════════════════════════════════
    // TOPBAR
    // ═══════════════════════════════════════════════════════════════
    private HBox buildTopBar() {
        Label groupAvatar = new Label("👥");
        groupAvatar.setFont(Font.font("Segoe UI", 22));
        groupAvatar.setAlignment(Pos.CENTER);
        groupAvatar.setPrefSize(46, 46);
        groupAvatar.setStyle(
                "-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 23; " +
                        "-fx-border-color: " + BORDER_W + "; -fx-border-radius: 23; -fx-border-width: 1;");

        groupNameTopLabel = new Label(groupName);
        groupNameTopLabel.setFont(Font.font("Georgia", FontWeight.BOLD, 16));
        groupNameTopLabel.setTextFill(Color.web(INK));

        memberCountLabel = new Label("Chargement...");
        memberCountLabel.setFont(Font.font("Segoe UI", 11));
        memberCountLabel.setTextFill(Color.web(INK_LITE));

        VBox info = new VBox(2, groupNameTopLabel, memberCountLabel);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button membersBtn = topBtn("👤", "Membres du groupe");
        membersBtn.setOnAction(e -> toggleMembersSidebar());

        Button editBtn = topBtn("✎", "Modifier le groupe");
        editBtn.setOnAction(e -> showEditGroupDialog());

        Button audioBtn = topBtn("☎", "Réunion audio");
        audioBtn.setOnAction(e -> startMeeting("AUDIO"));

        Button videoBtn = topBtn("▶", "Réunion vidéo");
        videoBtn.setOnAction(e -> startMeeting("VIDEO"));

        HBox topBar = new HBox(14, groupAvatar, info, membersBtn, editBtn, audioBtn, videoBtn);
        topBar.setPrefHeight(72);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 20, 0, 24));
        topBar.setStyle(
                "-fx-background-color: " + WHITE + "; " +
                        "-fx-border-color: transparent transparent " + BORDER_W + " transparent; " +
                        "-fx-border-width: 0 0 1 0;");
        DropShadow ts = new DropShadow();
        ts.setColor(Color.web(FOREST, 0.04)); ts.setRadius(12); ts.setOffsetY(3);
        topBar.setEffect(ts);
        return topBar;
    }

    // ═══════════════════════════════════════════════════════════════
    // PINNED BAR (identique à ChatWindow)
    // ═══════════════════════════════════════════════════════════════
    private VBox buildPinnedBar() {
        VBox bar = new VBox();
        bar.setStyle(
                "-fx-background-color: " + MINT_PALE + "; " +
                        "-fx-border-color: transparent transparent " + BORDER_W + " transparent; " +
                        "-fx-border-width: 0 0 1 0; -fx-padding: 8 20 8 20;");
        bar.setVisible(false);
        bar.setManaged(false);
        return bar;
    }

    private void refreshPinnedBar() {
        if (pinnedBar == null) return;
        pinnedBar.getChildren().clear();
        if (pinnedMessages.isEmpty()) {
            pinnedBar.setVisible(false);
            pinnedBar.setManaged(false);
            return;
        }
        Label title = new Label("📌  Messages épinglés (" + pinnedMessages.size() + ")");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        title.setTextFill(Color.web(SAGE));

        VBox list = new VBox(4);
        for (String[] pm : pinnedMessages) {
            String msgId  = pm[0];
            String sender = pm[2];
            String type   = pm.length > 3 ? pm[3] : null;
            String preview = getPinnedPreview(pm[1], type);

            Label lbl = new Label("• " + sender + ": " + preview);
            lbl.setFont(Font.font("Segoe UI", 11));
            lbl.setTextFill(Color.web(INK_MID));
            lbl.setStyle("-fx-cursor: hand;");
            lbl.setOnMouseEntered(e -> lbl.setTextFill(Color.web(FOREST)));
            lbl.setOnMouseExited(e -> lbl.setTextFill(Color.web(INK_MID)));
            lbl.setOnMouseClicked(e -> {
                HBox wrapper = messageNodes.get(msgId);
                if (wrapper == null) return;
                wrapper.requestFocus();
                double totalH = messageContainer.getBoundsInLocal().getHeight();
                double nodeY  = wrapper.getBoundsInParent().getMinY();
                if (totalH > 0) messageScrollPane.setVvalue(nodeY / totalH);
                String origStyle = wrapper.getStyle();
                wrapper.setStyle(origStyle + "; -fx-background-color: " + MINT_PALE + ";");
                new Timeline(new KeyFrame(Duration.millis(700), ev ->
                        wrapper.setStyle(origStyle))).play();
            });
            list.getChildren().add(lbl);
        }

        Button clearBtn = new Button("Tout désépingler");
        clearBtn.setFont(Font.font("Segoe UI", 11));
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + SAGE + "; -fx-cursor: hand; -fx-padding: 2 0;");
        clearBtn.setOnAction(e -> {
            for (String[] pm : pinnedMessages)
                client.sendCommand("GROUP_PIN_MSG:" + pm[0] + ":" + groupId + ":false");
            pinnedMessages.clear();
            refreshPinnedBar();
        });

        pinnedBar.getChildren().addAll(title, list, clearBtn);
        pinnedBar.setVisible(true);
        pinnedBar.setManaged(true);
    }

    private String getPinnedPreview(String content, String type) {
        if ("VOICE".equals(type)) return "🎤 Message vocal";
        if (type != null && type.startsWith("FILE:")) return "📎 " + type.substring(5);
        if (content == null || content.isEmpty()) return "(vide)";
        return content.length() > 50 ? content.substring(0, 50) + "…" : content;
    }

    // ═══════════════════════════════════════════════════════════════
    // CHAT ZONE
    // ═══════════════════════════════════════════════════════════════
    private VBox buildChatZone() {
        HBox topBar = buildTopBar();

        pinnedBar = buildPinnedBar();

        messageContainer = new VBox(12);
        messageContainer.setPadding(new Insets(24, 20, 24, 20));
        messageContainer.setStyle("-fx-background-color: " + CREAM + ";");
        showWelcomeState();

        messageScrollPane = new ScrollPane(messageContainer);
        messageScrollPane.setFitToWidth(true);
        messageScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScrollPane.setStyle(
                "-fx-background-color: " + CREAM + "; -fx-background: " + CREAM + "; -fx-border-color: transparent;");
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);

        HBox inputBarWrapper = buildInputBar();

        VBox zone = new VBox(topBar, pinnedBar, messageScrollPane, inputBarWrapper);
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);
        zone.setStyle("-fx-background-color: " + CREAM + ";");
        return zone;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT BAR
    // ═══════════════════════════════════════════════════════════════
    private HBox buildInputBar() {
        // Reply bar
        replyLabel = new Label("");
        replyLabel.setFont(Font.font("Segoe UI", 11));
        replyLabel.setTextFill(Color.web(SAGE));
        replyLabel.setWrapText(true);
        HBox.setHgrow(replyLabel, Priority.ALWAYS);

        Button cancelReply = new Button("×");
        cancelReply.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        cancelReply.setStyle("-fx-background-color: transparent; -fx-text-fill: " + INK_LITE + "; -fx-cursor: hand; -fx-padding: 0 6;");
        cancelReply.setOnAction(e -> clearReply());

        Region replyBarSpacer = new Region();
        HBox.setHgrow(replyBarSpacer, Priority.ALWAYS);

        replyBar = new HBox(10, new Label("↩ "), replyLabel, replyBarSpacer, cancelReply);
        replyBar.setAlignment(Pos.CENTER_LEFT);
        replyBar.setPadding(new Insets(8, 12, 8, 20));
        replyBar.setStyle(
                "-fx-background-color: " + WHITE + "; " +
                        "-fx-border-color: transparent transparent " + BORDER_W + " transparent; " +
                        "-fx-border-width: 0 0 1 0;");
        replyBar.setVisible(false);
        replyBar.setManaged(false);

        // Input field
        inputField = new TextField();
        inputField.setPromptText("Message au groupe...");
        inputField.setPrefHeight(50);
        inputField.setStyle(
                "-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0 16; " +
                        "-fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + INK + "; " +
                        "-fx-prompt-text-fill: " + INK_LITE + ";");
        inputField.setOnAction(e -> sendGroupMessage());
        HBox.setHgrow(inputField, Priority.ALWAYS);

        // Attach button (même look que ChatWindow)
        Button fileBtn = buildAttachButton();
        fileBtn.setOnAction(e -> sendGroupFile());

        // Mic button (même look que ChatWindow — hold ou toggle)
        Button voiceBtn = buildMicButton();
        voiceBtn.setOnAction(e -> toggleVoiceRecording(voiceBtn));

        // Send button
        Label sendIco = new Label("→");
        sendIco.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
        sendIco.setTextFill(Color.WHITE);
        sendIco.setAlignment(Pos.CENTER);
        StackPane sendBtn = new StackPane(sendIco);
        sendBtn.setPrefSize(38, 38); sendBtn.setMinSize(38, 38); sendBtn.setMaxSize(38, 38);
        sendBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-background-radius: 19; -fx-cursor: hand;");
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web(FOREST, 0.3)); glow.setRadius(12); glow.setSpread(0.08);
        sendBtn.setEffect(glow);
        sendBtn.setOnMouseClicked(e -> sendGroupMessage());

        HBox pill = new HBox(4, inputField, sendBtn);
        pill.setAlignment(Pos.CENTER);
        pill.setPadding(new Insets(6, 6, 6, 4));
        pill.setStyle(
                "-fx-background-color: " + WHITE + "; -fx-background-radius: 28; " +
                        "-fx-border-color: " + BORDER_W + "; -fx-border-radius: 28; -fx-border-width: 1.2;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        HBox bar = new HBox(10, fileBtn, voiceBtn, pill);
        bar.setPadding(new Insets(12, 20, 16, 20));
        bar.setAlignment(Pos.CENTER);
        bar.setStyle("-fx-background-color: " + CREAM + ";");
        HBox.setHgrow(pill, Priority.ALWAYS);

        VBox inputZone = new VBox(replyBar, bar);
        inputZone.setStyle("-fx-background-color: " + CREAM + ";");

        HBox wrapper = new HBox(inputZone);
        HBox.setHgrow(inputZone, Priority.ALWAYS);
        return wrapper;
    }

    // ═══════════════════════════════════════════════════════════════
    // SIDEBAR MEMBRES
    // ═══════════════════════════════════════════════════════════════
    private VBox buildMembersSidebar() {
        Label title = new Label("Membres");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 15));
        title.setTextFill(Color.web(INK));

        Button addMemberBtn = new Button("+ Ajouter");
        addMemberBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        addMemberBtn.setStyle(
                "-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + FOREST + "; " +
                        "-fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 4 12;");
        addMemberBtn.setOnAction(e -> {
            if (isAdmin) showAddMemberDialog();
            else showToast("Seuls les admins peuvent ajouter des membres !");
        });

        HBox header = new HBox(title, new Region(), addMemberBtn);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 14, 12, 14));
        header.setStyle(
                "-fx-background-color: " + WHITE + "; " +
                        "-fx-border-color: transparent transparent " + BORDER_W + " transparent; " +
                        "-fx-border-width: 0 0 1 0;");

        membersList = new ListView<>();
        membersList.setStyle("-fx-background-color: " + CREAM_2 + "; -fx-border-color: transparent;");
        VBox.setVgrow(membersList, Priority.ALWAYS);

        membersList.setCellFactory(lv -> new ListCell<String[]>() {
            @Override protected void updateItem(String[] m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) { setGraphic(null); setText(null); setStyle("-fx-background-color: transparent;"); return; }
                String userId = m[0], username = m[1], role = m[2];
                boolean online = "true".equals(m[3]);

                Label av = new Label(username.substring(0, 1).toUpperCase());
                av.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                av.setTextFill(Color.WHITE);
                av.setPrefSize(36, 36); av.setAlignment(Pos.CENTER);
                av.setStyle("-fx-background-color: " + ("admin".equals(role) ? FOREST : FOREST_MID) + "; -fx-background-radius: 18;");

                Label nm = new Label(username);
                nm.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                nm.setTextFill(Color.web(INK));

                boolean isMe = client != null && client.getUser() != null && userId.equals(client.getUser().getId());
                String roleText = "admin".equals(role) ? (isMe ? "Admin · Vous" : "Admin") : (isMe ? "Vous" : "Membre");
                Label roleLbl = new Label(roleText);
                roleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
                roleLbl.setTextFill(Color.web("admin".equals(role) ? FOREST : INK_LITE));
                roleLbl.setPadding(new Insets(1, 6, 1, 6));
                roleLbl.setStyle("-fx-background-color: " + ("admin".equals(role) ? MINT_PALE : CREAM_2) + "; -fx-background-radius: 8;");

                Circle dot = new Circle(4);
                dot.setFill(online ? Color.web(SAGE) : Color.web(BORDER_W));

                VBox info = new VBox(3, nm, roleLbl);
                info.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(info, Priority.ALWAYS);

                HBox row = new HBox(10, av, info, dot);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8, 12, 8, 12));
                row.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                row.setOnMouseEntered(ev -> row.setStyle("-fx-background-color: " + WHITE + "; -fx-cursor: hand;"));
                row.setOnMouseExited(ev -> row.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"));

                if (isAdmin && !isMe) {
                    ContextMenu cm = new ContextMenu();
                    if ("member".equals(role)) {
                        MenuItem promoteItem = new MenuItem("⭐  Nommer admin");
                        promoteItem.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 12px; -fx-padding: 5 12;");
                        promoteItem.setOnAction(ev -> { client.sendCommand("PROMOTE_ADMIN:" + groupId + ":" + userId); showToast(username + " est maintenant admin !"); loadGroupMembersWithRoles(); });
                        cm.getItems().add(promoteItem);
                    } else {
                        MenuItem demoteItem = new MenuItem("↓  Retirer le rôle admin");
                        demoteItem.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 12px; -fx-padding: 5 12;");
                        demoteItem.setOnAction(ev -> { client.sendCommand("DEMOTE_MEMBER:" + groupId + ":" + userId); showToast(username + " n'est plus admin."); loadGroupMembersWithRoles(); });
                        cm.getItems().add(demoteItem);
                    }
                    MenuItem removeItem = new MenuItem("🗑  Retirer du groupe");
                    removeItem.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 12px; -fx-text-fill: " + RED + "; -fx-padding: 5 12;");
                    removeItem.setOnAction(ev -> {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Retirer membre"); confirm.setHeaderText(null);
                        confirm.setContentText("Retirer " + username + " du groupe ?");
                        confirm.showAndWait().ifPresent(r -> { if (r == ButtonType.OK) { client.sendCommand("REMOVE_MEMBER:" + groupId + ":" + userId); showToast(username + " retiré du groupe."); loadGroupMembersWithRoles(); } });
                    });
                    cm.getItems().addAll(new SeparatorMenuItem(), removeItem);
                    setContextMenu(cm);
                }
                setGraphic(row); setText(null); setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            }
        });

        Button leaveBtn = new Button("Quitter le groupe");
        leaveBtn.setMaxWidth(Double.MAX_VALUE); leaveBtn.setPrefHeight(38);
        leaveBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        leaveBtn.setStyle("-fx-background-color: " + RED_PALE + "; -fx-text-fill: " + RED + "; -fx-cursor: hand; -fx-background-radius: 0;");
        leaveBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Quitter"); confirm.setHeaderText(null);
            confirm.setContentText("Quitter le groupe « " + groupName + " » ?");
            confirm.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    client.sendCommand("REMOVE_MEMBER:" + groupId + ":" + client.getUser().getId());
                    showToast("Vous avez quitté le groupe.");
                    new Timeline(new KeyFrame(Duration.seconds(1.5), ev -> {
                        Stage s = (Stage) leaveBtn.getScene().getWindow(); s.close();
                    })).play();
                }
            });
        });

        VBox sidebar = new VBox(header, membersList, leaveBtn);
        sidebar.setPrefWidth(230);
        sidebar.setStyle("-fx-background-color: " + CREAM_2 + "; -fx-border-color: " + BORDER_W + " transparent transparent transparent; -fx-border-width: 0 0 0 1;");
        VBox.setVgrow(membersList, Priority.ALWAYS);
        return sidebar;
    }

    // ═══════════════════════════════════════════════════════════════
    // AJOUTER UNE BULLE — logique principale
    // ═══════════════════════════════════════════════════════════════
    private void addMessageBubble(String messageId, String senderUsername, String content,
                                  String timestamp, String type, boolean deleted,
                                  boolean edited, boolean pinned, String replyToId) {
        boolean mine = client != null && client.getUser() != null
                && senderUsername.equals(client.getUser().getUsername());

        // ── Reply preview ──
        VBox replyPreviewNode = null;
        if (replyToId != null && !replyToId.isEmpty() && messageNodes.containsKey(replyToId)) {
            HBox orig = messageNodes.get(replyToId);
            String origText  = (String) orig.getProperties().get("content");
            String origSender = (String) orig.getProperties().get("sender");
            if (origText != null) {
                Region barLine = new Region();
                barLine.setPrefWidth(3); barLine.setMinWidth(3); barLine.setMaxWidth(3);
                barLine.setStyle("-fx-background-color: " + SAGE + "; -fx-background-radius: 2;");

                Label sLbl = new Label(origSender != null ? origSender : "");
                sLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
                sLbl.setTextFill(Color.web(SAGE));

                String preview = origText.length() > 45 ? origText.substring(0, 45) + "…" : origText;
                Label pLbl = new Label(preview);
                pLbl.setFont(Font.font("Segoe UI", 11));
                pLbl.setTextFill(Color.web(INK_MID));

                VBox textCol = new VBox(2, sLbl, pLbl);
                HBox inner = new HBox(8, barLine, textCol);
                inner.setPadding(new Insets(6, 10, 6, 8));
                inner.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 8;");
                replyPreviewNode = new VBox(inner);
                replyPreviewNode.setPadding(new Insets(0, 0, 6, 0));
            }
        }

        // ── Sender label ──
        Label senderLbl = new Label(senderUsername);
        senderLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        senderLbl.setTextFill(Color.web(SAGE));

        // ── Content node ──
        javafx.scene.Node contentNode;

        if (deleted) {
            Label ico = new Label("⊘");
            ico.setFont(Font.font("Segoe UI", 13)); ico.setTextFill(Color.web(INK_LITE));
            Label txt = new Label("Ce message a été supprimé.");
            txt.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 13));
            txt.setTextFill(Color.web(INK_LITE));
            HBox inner = new HBox(6, ico, txt); inner.setAlignment(Pos.CENTER_LEFT);
            contentNode = inner;
        } else if ("VOICE".equals(type)) {
            // ── Waveform player identique à ChatWindow ──
            contentNode = buildWaveformPlayer(content, mine);
        } else if (type != null && type.startsWith("FILE:")) {
            String fileName = type.substring(5);
            String lower = fileName.toLowerCase();
            boolean isImage = lower.endsWith(".png") || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                    || lower.endsWith(".webp") || lower.endsWith(".bmp");
            boolean isPdf   = lower.endsWith(".pdf");

            if (isImage && content != null && !content.isEmpty()) {
                // ── Aperçu image inline ──
                try {
                    byte[] bytes = Base64.getDecoder().decode(content);
                    ImageView iv = new ImageView(new Image(new ByteArrayInputStream(bytes)));
                    iv.setFitWidth(240); iv.setPreserveRatio(true);
                    iv.setStyle("-fx-cursor: hand;");
                    final String b64 = content;
                    iv.setOnMouseClicked(e -> openFileFromBase64(fileName, b64));
                    // Nom du fichier en dessous
                    Label nmLbl = new Label(fileName);
                    nmLbl.setFont(Font.font("Segoe UI", 10));
                    nmLbl.setTextFill(Color.web(INK_LITE));
                    VBox imgBox = new VBox(4, iv, nmLbl);
                    imgBox.setStyle("-fx-cursor: hand;");
                    imgBox.setOnMouseClicked(e -> openFileFromBase64(fileName, content));
                    contentNode = imgBox;
                } catch (Exception ex) {
                    contentNode = buildFileBubbleContent(fileName, content, isPdf);
                }
            } else {
                // ── Fichier générique / PDF avec icône stylée ──
                contentNode = buildFileBubbleContent(fileName, content, isPdf);
            }
        } else {
            // ── Texte standard ──
            Label contentLbl = new Label(content);
            contentLbl.setWrapText(true);
            contentLbl.setMaxWidth(320);
            contentLbl.setFont(Font.font("Segoe UI", 13.5));
            contentLbl.setTextFill(Color.web(INK));
            contentLbl.setLineSpacing(1.5);
            contentNode = contentLbl;
        }

        // ── Timestamp + edited + pinned ──
        String time;
        try {
            time = (timestamp != null && timestamp.length() >= 16)
                    ? timestamp.substring(11, 16) : timeFmt.format(new Date());
        } catch (Exception e) { time = timeFmt.format(new Date()); }

        String suffix = (edited && !deleted ? "  ✏" : "") + (pinned ? "  📌" : "");
        Label timeLbl = new Label(time + suffix);
        timeLbl.setFont(Font.font("Segoe UI", 9.5));
        timeLbl.setTextFill(Color.web(INK_LITE));
        HBox timeRow = new HBox(timeLbl);
        timeRow.setAlignment(Pos.CENTER_RIGHT);

        // ── Bubble ──
        VBox bubble = new VBox(4);
        if (!mine && !deleted) bubble.getChildren().add(senderLbl);
        if (replyPreviewNode != null) bubble.getChildren().add(replyPreviewNode);
        bubble.getChildren().addAll(contentNode, timeRow);
        bubble.setPadding(new Insets(10, 14, 8, 14));
        bubble.setMaxWidth(360);

        String bg = mine ? SENT_BG : WHITE;
        String br = mine ? "20 20 6 20" : "6 20 20 20";
        bubble.setStyle(
                "-fx-background-color: " + bg + "; -fx-background-radius: " + br + "; " +
                        "-fx-border-color: " + (mine ? "rgba(74,124,111,0.15)" : BORDER_W) + "; " +
                        "-fx-border-radius: " + br + "; -fx-border-width: 1;");
        DropShadow sh = new DropShadow();
        sh.setColor(Color.web(FOREST, 0.06)); sh.setRadius(8); sh.setOffsetY(2);
        bubble.setEffect(sh);

        FadeTransition ftAnim = new FadeTransition(Duration.millis(180), bubble);
        ftAnim.setFromValue(0.4); ftAnim.setToValue(1); ftAnim.play();

        // ── Wrapper ──
        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        wrapper.setPadding(mine ? new Insets(2, 6, 2, 70) : new Insets(2, 70, 2, 6));

        // Stocker métadonnées
        if (messageId != null && !messageId.isEmpty()) {
            wrapper.getProperties().put("content", content);
            wrapper.getProperties().put("type",    type);
            wrapper.getProperties().put("sender",  senderUsername);
            messageNodes.put(messageId, wrapper);
        }

        // ── Context menu ──
        if (!deleted && messageId != null && !messageId.isEmpty()) {
            ContextMenu cm = new ContextMenu();

            MenuItem replyItem = menuItem("↩  Répondre");
            replyItem.setOnAction(e -> {
                this.replyToId   = messageId;
                this.replyToText = (type != null && type.startsWith("FILE:")) ? type.substring(5)
                        : ("VOICE".equals(type) ? "🎤 Message vocal" : content);
                replyLabel.setText(senderUsername + " : " + this.replyToText);
                replyBar.setVisible(true);
                replyBar.setManaged(true);
                inputField.requestFocus();
            });
            cm.getItems().add(replyItem);

            // Copier (texte seulement)
            if (!"VOICE".equals(type) && (type == null || !type.startsWith("FILE:"))) {
                MenuItem copyItem = menuItem("📋  Copier");
                copyItem.setOnAction(e -> {
                    javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
                    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                    cc.putString(content);
                    cb.setContent(cc);
                    showToast("Copié !");
                });
                cm.getItems().add(copyItem);
            }

            MenuItem forwardItem = menuItem("↗  Transférer");
            forwardItem.setOnAction(e -> showForwardDialog(content, type));
            cm.getItems().add(forwardItem);

            if (mine) {
                // Modifier (texte seulement)
                if (!"VOICE".equals(type) && (type == null || !type.startsWith("FILE:"))) {
                    MenuItem editItem = menuItem("✎  Modifier");
                    editItem.setOnAction(e -> showEditDialog(messageId, content));
                    cm.getItems().add(editItem);
                }

                // Épingler / Désépingler
                MenuItem pinItem = menuItem(pinned ? "📌  Désépingler" : "📌  Épingler");
                pinItem.setOnAction(e -> {
                    boolean newPinned = !pinned;
                    client.sendCommand("GROUP_PIN_MSG:" + messageId + ":" + groupId + ":" + newPinned);
                    // Mise à jour locale immédiate
                    handlePinMessage(messageId, newPinned);
                    if (newPinned) {
                        String c = content != null ? content : "";
                        pinnedMessages.removeIf(pm -> pm[0].equals(messageId));
                        pinnedMessages.add(new String[]{ messageId, c, senderUsername, type });
                    } else {
                        pinnedMessages.removeIf(pm -> pm[0].equals(messageId));
                    }
                    refreshPinnedBar();
                    showToast(newPinned ? "Message épinglé 📌" : "Message désépinglé");
                });
                cm.getItems().add(pinItem);

                // Supprimer
                MenuItem deleteItem = menuItem("✕  Supprimer");
                deleteItem.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-text-fill: " + RED + "; -fx-padding: 6 14;");
                deleteItem.setOnAction(e -> {
                    Alert a = new Alert(Alert.AlertType.CONFIRMATION);
                    a.setHeaderText(null);
                    a.setContentText("Supprimer ce message ?");
                    a.showAndWait().ifPresent(r -> {
                        if (r == ButtonType.OK)
                            client.sendCommand("GROUP_DELETE_MSG:" + messageId + ":" + groupId);
                    });
                });
                cm.getItems().addAll(new SeparatorMenuItem(), deleteItem);
            }

            bubble.setOnContextMenuRequested(e -> cm.show(bubble, e.getScreenX(), e.getScreenY()));
        }

        // Supprimer l'état vide si présent
        messageContainer.getChildren().removeIf(n ->
                n instanceof StackPane && ((StackPane) n).getPrefHeight() > 200);
        messageContainer.getChildren().add(wrapper);
        messageScrollPane.setVvalue(1.0);
    }

    // ═══════════════════════════════════════════════════════════════
    // WAVEFORM PLAYER (identique à ChatWindow)
    // ═══════════════════════════════════════════════════════════════
    private javafx.scene.Node buildWaveformPlayer(String base64, boolean mine) {
        // Bouton play
        Label playIco = new Label("▶");
        playIco.setFont(Font.font("Georgia", FontWeight.BOLD, 14));
        playIco.setTextFill(Color.WHITE);
        playIco.setAlignment(Pos.CENTER);
        playIco.setPadding(new Insets(0, 0, 0, 3));
        StackPane playBtn = new StackPane(playIco);
        playBtn.setPrefSize(46, 46); playBtn.setMinSize(46, 46); playBtn.setMaxSize(46, 46);
        playBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-background-radius: 23; -fx-cursor: hand;");

        // Waveform
        int[] hs = {4,8,14,20,26,30,22,18,28,32,24,16,30,28,20,12,26,32,28,22,18,28,24,16,30,22,14,26,32,20,16,24,18,12,8,14,20,28,32,24};
        String barColor   = mine ? SAGE : MINT;
        String barPlayed  = mine ? FOREST : FOREST_MID;
        HBox wave = new HBox(3);
        wave.setAlignment(Pos.CENTER);
        wave.setFillHeight(true);
        wave.setPrefHeight(36); wave.setMaxHeight(36); wave.setMinHeight(36);
        wave.setMinWidth(160);
        wave.setClip(new Rectangle(160, 36));
        for (int h : hs) {
            Region bar = new Region();
            bar.setPrefWidth(3.5); bar.setMinWidth(3.5); bar.setMaxWidth(3.5);
            bar.setPrefHeight(h); bar.setMinHeight(4); bar.setMaxHeight(30);
            bar.setStyle("-fx-background-color: " + barColor + "; -fx-background-radius: 2;");
            wave.getChildren().add(bar);
        }

        Label durLbl = new Label("0:00");
        durLbl.setFont(Font.font("Segoe UI", 11));
        durLbl.setTextFill(Color.web(INK_LITE));
        durLbl.setMinWidth(32);

        // Estimer durée depuis base64
        try {
            int totalSec = Math.max(1, Base64.getDecoder().decode(base64).length / 88200);
            durLbl.setText(String.format("%d:%02d", totalSec / 60, totalSec % 60));
        } catch (Exception ignored) {}

        HBox mainRow = new HBox(12, playBtn, wave, durLbl);
        mainRow.setAlignment(Pos.CENTER_LEFT);

        final boolean[] playing = {false};
        final Timeline[] animRef = {null};

        playBtn.setOnMouseClicked(e -> {
            if (playing[0]) {
                playing[0] = false;
                if (animRef[0] != null) animRef[0].stop();
                playIco.setText("▶");
                resetWave(wave, hs, barColor);
                return;
            }
            playing[0] = true;
            playIco.setText("■");

            // Animation waveform
            Timeline anim = new Timeline();
            int size = wave.getChildren().size();
            byte[] audioBytes;
            try { audioBytes = Base64.getDecoder().decode(base64); } catch (Exception ex) { audioBytes = new byte[0]; }
            int totalMs = Math.max(1, audioBytes.length / 88);
            for (int i = 0; i < size; i++) {
                Region b = (Region) wave.getChildren().get(i);
                double delay = (double) totalMs / size * i;
                anim.getKeyFrames().add(new KeyFrame(Duration.millis(delay), ev -> {
                    b.setStyle("-fx-background-color: " + barPlayed + "; -fx-background-radius: 2;");
                    b.setPrefHeight(4 + Math.random() * 26);
                }));
            }
            anim.play();
            animRef[0] = anim;

            final byte[] finalAudio = audioBytes;
            new Thread(() -> {
                SourceDataLine line = null;
                try {
                    AudioFormat fmt = new AudioFormat(44100, 16, 1, true, false);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                    line = (SourceDataLine) AudioSystem.getLine(info);
                    line.open(fmt); line.start();
                    line.write(finalAudio, 0, finalAudio.length);
                    line.drain();
                    line.close();
                } catch (Exception ex) { ex.printStackTrace(); }
                final SourceDataLine finalLine = line;
                Platform.runLater(() -> {
                    playing[0] = false;
                    playIco.setText("▶");
                    if (animRef[0] != null) animRef[0].stop();
                    resetWave(wave, hs, barColor);
                    if (finalLine != null && finalLine.isOpen()) finalLine.close();
                });
            }, "group-voice-play").start();
        });

        return mainRow;
    }

    private void resetWave(HBox wave, int[] hs, String color) {
        for (int i = 0; i < wave.getChildren().size() && i < hs.length; i++) {
            Region bar = (Region) wave.getChildren().get(i);
            bar.setPrefHeight(hs[i]);
            bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FICHIER — icône stylée (PDF ou générique)
    // ═══════════════════════════════════════════════════════════════
    private javafx.scene.Node buildFileBubbleContent(String fileName, String base64, boolean isPdf) {

        // Clic = ouvrir avec le système (pas télécharger)
        Runnable openAction = () -> openFileFromBase64(fileName, base64);

        if (isPdf && base64 != null && !base64.isEmpty()) {
            // ── Aperçu PDF : miniature page 1 via PDFBox ─────────────
            VBox pdfBox = new VBox(0);
            pdfBox.setMaxWidth(220);
            pdfBox.setStyle(
                    "-fx-cursor: hand; -fx-background-color: " + WHITE + "; " +
                            "-fx-border-color: " + BORDER_W + "; -fx-border-radius: 10; " +
                            "-fx-background-radius: 10; -fx-border-width: 1;");

            // Placeholder pendant le rendu
            StackPane placeholder = new StackPane();
            placeholder.setPrefSize(220, 150);
            placeholder.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 10 10 0 0;");
            Label loadingLbl = new Label("⏳");
            loadingLbl.setFont(Font.font("Segoe UI", 22));
            placeholder.getChildren().add(loadingLbl);
            pdfBox.getChildren().add(placeholder);

            // Barre bas : icône + nom + badge PDF
            Label icoLbl2 = new Label("📄");
            icoLbl2.setFont(Font.font("Segoe UI", 14));

            long sz = 0;
            try { sz = Base64.getDecoder().decode(base64).length; } catch (Exception ignored) {}
            String szStr = sz > 1024*1024
                    ? String.format("%.1f Mo", sz / 1048576.0)
                    : String.format("%.0f Ko", sz / 1024.0);

            Label nmLbl2 = new Label(fileName);
            nmLbl2.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            nmLbl2.setTextFill(Color.web(INK));
            nmLbl2.setMaxWidth(120);
            nmLbl2.setTextOverrun(OverrunStyle.ELLIPSIS);
            HBox.setHgrow(nmLbl2, Priority.ALWAYS);

            Label szLbl2 = new Label(szStr);
            szLbl2.setFont(Font.font("Segoe UI", 10));
            szLbl2.setTextFill(Color.web(INK_LITE));

            Label badge = new Label("PDF");
            badge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
            badge.setTextFill(Color.WHITE);
            badge.setPadding(new Insets(1, 5, 1, 5));
            badge.setStyle("-fx-background-color: #DC2626; -fx-background-radius: 4;");

            VBox nameCol = new VBox(1, nmLbl2, szLbl2);
            HBox.setHgrow(nameCol, Priority.ALWAYS);

            HBox footer = new HBox(6, icoLbl2, nameCol, badge);
            footer.setAlignment(Pos.CENTER_LEFT);
            footer.setPadding(new Insets(7, 10, 7, 10));
            footer.setStyle(
                    "-fx-background-color: " + WHITE + "; -fx-background-radius: 0 0 10 10; " +
                            "-fx-border-color: " + BORDER_W + " transparent transparent transparent; " +
                            "-fx-border-width: 1 0 0 0;");
            pdfBox.getChildren().add(footer);

            // Clic = ouvrir
            pdfBox.setOnMouseClicked(e -> openAction.run());

            // Rendu miniature en background thread
            new Thread(() -> {
                try {
                    byte[] bytes = Base64.getDecoder().decode(base64);
                    try (PDDocument doc = Loader.loadPDF(bytes)) {
                        PDFRenderer renderer = new PDFRenderer(doc);
                        BufferedImage bimg = renderer.renderImageWithDPI(0, 100);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(bimg, "PNG", baos);
                        javafx.scene.image.Image fxImg =
                                new javafx.scene.image.Image(new ByteArrayInputStream(baos.toByteArray()));
                        Platform.runLater(() -> {
                            ImageView iv = new ImageView(fxImg);
                            iv.setFitWidth(220);
                            iv.setPreserveRatio(true);
                            // Clip arrondi haut
                            double h = fxImg.getHeight() * (220.0 / fxImg.getWidth());
                            Rectangle clip = new Rectangle(220, Math.min(h, 220));
                            clip.setArcWidth(20); clip.setArcHeight(20);
                            iv.setClip(clip);
                            pdfBox.getChildren().set(0, iv);
                        });
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        Label errLbl = new Label("📄");
                        errLbl.setFont(Font.font("Segoe UI", 42));
                        errLbl.setAlignment(Pos.CENTER);
                        StackPane errPane = new StackPane(errLbl);
                        errPane.setPrefSize(220, 120);
                        errPane.setStyle("-fx-background-color: #F0F8F5; -fx-background-radius: 10 10 0 0;");
                        pdfBox.getChildren().set(0, errPane);
                    });
                }
            }, "pdf-preview").start();

            return pdfBox;

        } else {
            // ── Fichier générique ─────────────────────────────────────
            Label icoLbl = new Label("📎");
            icoLbl.setFont(Font.font("Segoe UI", 20));
            icoLbl.setTextFill(Color.web(SAGE));
            icoLbl.setPrefSize(44, 44);
            icoLbl.setAlignment(Pos.CENTER);
            icoLbl.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 10;");

            Label nmLbl = new Label(fileName);
            nmLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            nmLbl.setTextFill(Color.web(INK));
            nmLbl.setWrapText(true);
            nmLbl.setMaxWidth(175);

            long sizeBytes = 0;
            try { sizeBytes = Base64.getDecoder().decode(base64).length; } catch (Exception ignored) {}
            String sizeStr = sizeBytes > 1024*1024
                    ? String.format("%.1f Mo", sizeBytes / 1048576.0)
                    : String.format("%.0f Ko", sizeBytes / 1024.0);

            Label szLbl = new Label(sizeStr);
            szLbl.setFont(Font.font("Segoe UI", 10));
            szLbl.setTextFill(Color.web(INK_LITE));

            VBox infoBox = new VBox(2, nmLbl, szLbl);

            HBox row = new HBox(10, icoLbl, infoBox);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-cursor: hand;");
            row.setOnMouseClicked(e -> openAction.run());

            return row;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC : afficher un message reçu du serveur
    // ═══════════════════════════════════════════════════════════════
    public void displayMessage(String groupId, String messageId, String senderUsername,
                               String content, String timestamp, String replyToId) {
        if (!this.groupId.equals(groupId)) return;
        Platform.runLater(() -> addMessageBubble(
                messageId, senderUsername, content,
                timestamp, "TEXT", false, false, false, replyToId));
    }

    public void loadHistoryMessages(String groupId, List<String[]> messages) {
        if (!this.groupId.equals(groupId)) return;
        Platform.runLater(() -> {
            messageContainer.getChildren().clear();
            messageNodes.clear();
            pinnedMessages.clear();
            if (messages.isEmpty()) { showWelcomeState(); return; }
            for (String[] msg : messages) {
                String type    = msg.length > 4 ? msg[4] : "TEXT";
                boolean del    = msg.length > 5 && "true".equals(msg[5]);
                boolean edited = msg.length > 6 && "true".equals(msg[6]);
                boolean pinned = msg.length > 7 && "true".equals(msg[7]);
                String  reply  = msg.length > 8 ? msg[8] : "";
                String  content = msg[2];

                if ("FILE".equals(type) && content.contains("||")) {
                    String fileName = content.substring(0, content.indexOf("||"));
                    content = content.substring(content.indexOf("||") + 2);
                    type    = "FILE:" + fileName;
                }

                addMessageBubble(msg[0], msg[1], content, msg[3],
                        type, del, edited, pinned, reply);

                // Reconstituer la liste des épinglés
                if (pinned && !del) {
                    String msgId = msg[0], sender = msg[1], c = content;
                    pinnedMessages.removeIf(pm -> pm[0].equals(msgId));
                    pinnedMessages.add(new String[]{ msgId, c, sender, type });
                }
            }
            refreshPinnedBar();
            messageScrollPane.setVvalue(1.0);
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // ENVOYER
    // ═══════════════════════════════════════════════════════════════
    private void sendGroupMessage() {
        String content = inputField.getText().trim();
        if (content.isEmpty()) return;
        String tempId = UUID.randomUUID().toString();
        addMessageBubble(tempId, client.getUser().getUsername(), content,
                timeFmt.format(new Date()), "TEXT", false, false, false, replyToId);
        String cmd = "GROUP_MSG:" + groupId + ":" + content;
        if (replyToId != null) cmd += ":" + replyToId;
        client.sendCommand(cmd);
        inputField.clear();
        clearReply();
    }

    // ═══════════════════════════════════════════════════════════════
    // CHARGER
    // ═══════════════════════════════════════════════════════════════
    private void loadGroupHistory() {
        if (client != null) client.sendCommand("GET_GROUP_HISTORY:" + groupId);
    }

    private void loadGroupInfo() {
        new Thread(() -> {
            String[] info = GroupDAO.getGroupInfo(groupId);
            if (info != null) {
                boolean admin = GroupDAO.isAdmin(groupId, client.getUser().getId());
                Platform.runLater(() -> {
                    isAdmin = admin;
                    groupCreatorId = info[2];
                    groupName = info[1];
                    if (groupNameTopLabel != null) groupNameTopLabel.setText(info[1]);
                });
            }
        }).start();
    }

    private void loadGroupMembersWithRoles() {
        new Thread(() -> {
            List<String[]> members = GroupDAO.getGroupMembersWithRoles(groupId);
            Platform.runLater(() -> {
                if (membersList != null) membersList.getItems().setAll(members);
                if (memberCountLabel != null)
                    memberCountLabel.setText(members.size() + " membre" + (members.size() > 1 ? "s" : ""));
            });
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLERS DISTANTS
    // ═══════════════════════════════════════════════════════════════
    public void handleDeleteMessage(String messageId) {
        Platform.runLater(() -> {
            HBox wrapper = messageNodes.get(messageId);
            if (wrapper == null) return;
            VBox bubble = (VBox) wrapper.getChildren().get(0);
            bubble.getChildren().clear();

            Label ico = new Label("⊘");
            ico.setFont(Font.font("Segoe UI", 13)); ico.setTextFill(Color.web(INK_LITE));
            Label txt = new Label("Ce message a été supprimé.");
            txt.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 13));
            txt.setTextFill(Color.web(INK_LITE));
            HBox inner = new HBox(6, ico, txt); inner.setAlignment(Pos.CENTER_LEFT);
            bubble.getChildren().add(inner);
            bubble.setOnContextMenuRequested(null);

            // Retirer des épinglés si applicable
            pinnedMessages.removeIf(pm -> pm[0].equals(messageId));
            refreshPinnedBar();
        });
    }

    public void handleEditMessage(String messageId, String newContent) {
        Platform.runLater(() -> {
            HBox wrapper = messageNodes.get(messageId);
            if (wrapper == null) return;
            wrapper.getProperties().put("content", newContent);
            VBox bubble = (VBox) wrapper.getChildren().get(0);
            for (javafx.scene.Node n : bubble.getChildren()) {
                if (n instanceof Label) {
                    Label l = (Label) n;
                    // Le label de contenu principal = pas SAGE, pas INK_LITE, pas petit
                    if (l.getFont().getSize() >= 13 && !l.getTextFill().equals(Color.web(SAGE))
                            && !l.getTextFill().equals(Color.web(INK_LITE))) {
                        l.setText(newContent);
                        break;
                    }
                }
            }
            // Ajouter ✏ dans le timeRow
            for (javafx.scene.Node n : bubble.getChildren()) {
                if (n instanceof HBox) {
                    HBox row = (HBox) n;
                    if (!row.getChildren().isEmpty() && row.getChildren().get(0) instanceof Label) {
                        Label tl = (Label) row.getChildren().get(0);
                        if (!tl.getText().contains("✏")) tl.setText(tl.getText() + "  ✏");
                    }
                }
            }
        });
    }

    public void handlePinMessage(String messageId, boolean pinned) {
        Platform.runLater(() -> {
            HBox wrapper = messageNodes.get(messageId);
            if (wrapper == null) return;
            VBox bubble = (VBox) wrapper.getChildren().get(0);
            for (javafx.scene.Node n : bubble.getChildren()) {
                if (n instanceof HBox) {
                    HBox row = (HBox) n;
                    if (!row.getChildren().isEmpty() && row.getChildren().get(0) instanceof Label) {
                        Label tl = (Label) row.getChildren().get(0);
                        String txt = tl.getText().replace("  📌", "");
                        if (pinned) txt += "  📌";
                        tl.setText(txt);
                    }
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // FICHIER
    // ═══════════════════════════════════════════════════════════════
    private void sendGroupFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Envoyer un fichier");
        File f = fc.showOpenDialog(inputField.getScene().getWindow());
        if (f == null) return;
        new Thread(() -> {
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                String base64 = Base64.getEncoder().encodeToString(bytes);
                String tempId = UUID.randomUUID().toString();
                Platform.runLater(() -> addMessageBubble(
                        tempId, client.getUser().getUsername(), base64,
                        timeFmt.format(new Date()), "FILE:" + f.getName(), false, false, false, null));
                client.sendCommand("GROUP_FILE:" + groupId + ":" + f.getName() + ":" + base64);
            } catch (Exception e) {
                showToast("Erreur envoi fichier !");
            }
        }).start();
    }

    public void displayFileMessage(String groupId, String messageId, String senderUsername,
                                   String fileName, String base64) {
        if (!this.groupId.equals(groupId)) return;
        Platform.runLater(() -> addMessageBubble(
                messageId, senderUsername, base64,
                timeFmt.format(new Date()), "FILE:" + fileName, false, false, false, null));
    }

    private void downloadFile(String fileName, String base64) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Enregistrer le fichier");
        fc.setInitialFileName(fileName);
        File dest = fc.showSaveDialog(inputField.getScene().getWindow());
        if (dest == null) return;
        new Thread(() -> {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64);
                Files.write(dest.toPath(), bytes);
                showToast("Fichier enregistré !");
            } catch (Exception e) {
                showToast("Erreur téléchargement !");
            }
        }).start();
    }

    private void openFileFromBase64(String fileName, String base64) {
        new Thread(() -> {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64);
                File tmp = new File(System.getProperty("java.io.tmpdir"), fileName);
                Files.write(tmp.toPath(), bytes);
                java.awt.Desktop.getDesktop().open(tmp);
            } catch (Exception e) {
                showToast("Impossible d'ouvrir !");
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════
    // VOCAL
    // ═══════════════════════════════════════════════════════════════
    private void toggleVoiceRecording(Button voiceBtn) {
        if (!isRecording) {
            try {
                AudioFormat fmt = new AudioFormat(44100, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                micLine = (TargetDataLine) AudioSystem.getLine(info);
                micLine.open(fmt); micLine.start();
                isRecording     = true;
                recordedAudio   = new ByteArrayOutputStream();
                recordingStart  = System.currentTimeMillis();
                voiceBtn.setStyle("-fx-background-color: " + RED + "; -fx-background-radius: 24; -fx-cursor: hand; -fx-padding: 0; -fx-border-color: transparent;");
                showToast("Enregistrement...");
                recordThread = new Thread(() -> {
                    byte[] buf = new byte[4096];
                    while (isRecording) {
                        int n = micLine.read(buf, 0, buf.length);
                        if (n > 0) recordedAudio.write(buf, 0, n);
                    }
                });
                recordThread.setDaemon(true);
                recordThread.start();
            } catch (Exception e) {
                showToast("Micro indisponible !");
            }
        } else {
            isRecording = false;
            if (micLine != null) { micLine.stop(); micLine.close(); }
            voiceBtn.setStyle("-fx-background-color: " + SAGE + "; -fx-background-radius: 24; -fx-cursor: hand; -fx-padding: 0; -fx-border-color: transparent;");
            byte[] audio = recordedAudio.toByteArray();
            if (audio.length == 0) return;
            String base64 = Base64.getEncoder().encodeToString(audio);
            String tempId = UUID.randomUUID().toString();
            addMessageBubble(tempId, client.getUser().getUsername(), base64,
                    timeFmt.format(new Date()), "VOICE", false, false, false, null);
            client.sendCommand("GROUP_VOICE:" + groupId + ":" + base64);
            showToast("Message vocal envoyé !");
        }
    }

    public void displayVoiceMessage(String groupId, String messageId,
                                    String senderUsername, String base64) {
        if (!this.groupId.equals(groupId)) return;
        Platform.runLater(() -> addMessageBubble(
                messageId, senderUsername, base64,
                timeFmt.format(new Date()), "VOICE", false, false, false, null));
    }

    // ═══════════════════════════════════════════════════════════════
    // DIALOG MODIFIER GROUPE
    // ═══════════════════════════════════════════════════════════════
    private void showEditGroupDialog() {
        if (!isAdmin) { showToast("Seuls les admins peuvent modifier le groupe !"); return; }

        Stage d = new Stage();
        d.setTitle("Modifier le groupe");
        d.setResizable(false);

        Label nameLbl = new Label("NOM DU GROUPE");
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        nameLbl.setTextFill(Color.web(INK_LITE));

        TextField nameField = new TextField(groupName);
        nameField.setPrefHeight(44);
        nameField.setStyle("-fx-background-color: #F0F8F5; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14; -fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + INK + ";");

        Label descLbl = new Label("DESCRIPTION");
        descLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        descLbl.setTextFill(Color.web(INK_LITE));

        TextArea descField = new TextArea();
        descField.setPromptText("Description du groupe..."); descField.setPrefRowCount(3); descField.setWrapText(true);
        descField.setStyle("-fx-background-color: #F0F8F5; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 10 14; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';");

        Label photoLbl = new Label("PHOTO DU GROUPE");
        photoLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        photoLbl.setTextFill(Color.web(INK_LITE));

        Label photoPreview = new Label("👥");
        photoPreview.setFont(Font.font("Segoe UI", 28)); photoPreview.setAlignment(Pos.CENTER);
        photoPreview.setPrefSize(60, 60);
        photoPreview.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 30; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 30; -fx-border-width: 1;");

        final String[] photoBase64 = {""};

        Button choosePhotoBtn = new Button("Choisir une photo");
        choosePhotoBtn.setFont(Font.font("Segoe UI", 12));
        choosePhotoBtn.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + FOREST + "; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 6 14;");
        choosePhotoBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png","*.jpg","*.jpeg"));
            File f = fc.showOpenDialog(d);
            if (f != null) {
                try {
                    byte[] bytes = Files.readAllBytes(f.toPath());
                    photoBase64[0] = Base64.getEncoder().encodeToString(bytes);
                    ImageView iv = new ImageView(new Image(f.toURI().toString()));
                    iv.setFitWidth(60); iv.setFitHeight(60); iv.setPreserveRatio(false);
                    javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(30, 30, 30);
                    iv.setClip(clip);
                    photoPreview.setGraphic(iv); photoPreview.setText("");
                } catch (Exception ex) { showToast("Erreur lecture photo !"); }
            }
        });

        HBox photoRow = new HBox(14, photoPreview, choosePhotoBtn);
        photoRow.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("✓  Sauvegarder");
        saveBtn.setMaxWidth(Double.MAX_VALUE); saveBtn.setPrefHeight(44);
        saveBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        saveBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) { showToast("Le nom ne peut pas être vide !"); return; }
            GroupDAO.updateGroupInfo(groupId, newName, descField.getText().trim(), photoBase64[0]);
            client.sendCommand("UPDATE_GROUP:" + groupId + ":" + newName + ":" + descField.getText().trim());
            groupName = newName;
            if (groupNameTopLabel != null) groupNameTopLabel.setText(newName);
            showToast("Groupe mis à jour !"); d.close();
        });

        Separator sep = new Separator();
        Label dangerLbl = new Label("ZONE DANGER");
        dangerLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10)); dangerLbl.setTextFill(Color.web(RED));

        Button deleteGroupBtn = new Button("🗑  Supprimer le groupe");
        deleteGroupBtn.setMaxWidth(Double.MAX_VALUE); deleteGroupBtn.setPrefHeight(40);
        deleteGroupBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        deleteGroupBtn.setStyle("-fx-background-color: " + RED_PALE + "; -fx-text-fill: " + RED + "; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: #FCA5A5; -fx-border-radius: 10; -fx-border-width: 1;");
        deleteGroupBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Supprimer le groupe"); confirm.setHeaderText(null);
            confirm.setContentText("Supprimer définitivement « " + groupName + " » ?\nTous les messages seront perdus.");
            confirm.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    client.sendCommand("DELETE_GROUP:" + groupId);
                    showToast("Groupe supprimé !"); d.close();
                    new Timeline(new KeyFrame(Duration.seconds(1.5), ev -> {
                        Stage s = (Stage) inputField.getScene().getWindow(); s.close();
                    })).play();
                }
            });
        });

        VBox layout = new VBox(12, nameLbl, nameField, descLbl, descField, photoLbl, photoRow, saveBtn, sep, dangerLbl, deleteGroupBtn);
        layout.setPadding(new Insets(24)); layout.setStyle("-fx-background-color: white;");
        ScrollPane sp = new ScrollPane(layout);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: white; -fx-background: white; -fx-border-color: transparent;");
        d.setScene(new Scene(sp, 380, 500));

        new Thread(() -> {
            String[] info = GroupDAO.getGroupInfo(groupId);
            if (info != null) Platform.runLater(() -> descField.setText(info[3]));
        }).start();
        d.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // DIALOG AJOUTER MEMBRE
    // ═══════════════════════════════════════════════════════════════
    private void showAddMemberDialog() {
        Stage d = new Stage(); d.setTitle("Ajouter un membre");
        Label title = new Label("Ajouter au groupe");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 15)); title.setTextFill(Color.web(INK));
        TextField searchFld = new TextField(); searchFld.setPromptText("Username..."); searchFld.setPrefHeight(40);
        searchFld.setStyle("-fx-background-color: " + CREAM + "; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';");
        Label resultLabel = new Label(""); resultLabel.setFont(Font.font("Segoe UI", 11)); resultLabel.setWrapText(true);
        Button addBtn = new Button("Ajouter"); addBtn.setMaxWidth(Double.MAX_VALUE); addBtn.setPrefHeight(42);
        addBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        addBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;");
        addBtn.setOnAction(e -> {
            String username = searchFld.getText().trim();
            if (username.isEmpty()) return;
            model.User found = database.UserDAO.searchUserByUsername(username);
            if (found == null) { resultLabel.setText("❌ Introuvable !"); resultLabel.setTextFill(Color.web(RED)); return; }
            String result = database.GroupDAO.tryAddMemberToGroup(groupId, client.getUser().getId(), found.getId());
            switch (result) {
                case "ADDED":
                    client.sendCommand("ADD_MEMBER:" + groupId + ":" + found.getId());
                    showToast(username + " ajouté !"); d.close(); loadGroupMembersWithRoles(); break;
                case "INVITED":
                    String[] gInfo = database.GroupDAO.getGroupInfo(groupId);
                    String gName = gInfo != null ? gInfo[1] : groupId;
                    client.sendCommand("GROUP_INVITE:" + groupId + ":" + gName + ":" + found.getId());
                    resultLabel.setText("📨 Invitation envoyée à " + username + "."); resultLabel.setTextFill(Color.web(SAGE)); break;
                case "ALREADY_MEMBER":
                    resultLabel.setText("⚠️ Déjà dans le groupe !"); resultLabel.setTextFill(Color.web(INK_MID)); break;
                case "ALREADY_INVITED":
                    resultLabel.setText("⏳ Invitation déjà en attente."); resultLabel.setTextFill(Color.web(INK_MID)); break;
                default:
                    resultLabel.setText("❌ Erreur."); resultLabel.setTextFill(Color.web(RED));
            }
        });
        VBox layout = new VBox(14, title, searchFld, addBtn, resultLabel);
        layout.setPadding(new Insets(24)); layout.setStyle("-fx-background-color: white;");
        d.setScene(new Scene(layout, 300, 220)); d.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // EDIT DIALOG
    // ═══════════════════════════════════════════════════════════════
    private void showEditDialog(String messageId, String currentContent) {
        Stage d = new Stage(); d.setTitle("Modifier le message");
        TextField tf = new TextField(currentContent); tf.setPrefHeight(42);
        tf.setStyle("-fx-background-color: #F0F8F5; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14; -fx-font-size: 13px;");
        Button save = new Button("Sauvegarder"); save.setMaxWidth(Double.MAX_VALUE); save.setPrefHeight(42);
        save.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        save.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;");
        save.setOnAction(e -> {
            String nc = tf.getText().trim();
            if (!nc.isEmpty()) { client.sendCommand("GROUP_EDIT_MSG:" + messageId + ":" + groupId + ":" + nc); d.close(); }
        });
        VBox layout = new VBox(12, new Label("Nouveau message :"), tf, save);
        layout.setPadding(new Insets(20)); layout.setStyle("-fx-background-color: white;");
        d.setScene(new Scene(layout, 320, 150)); d.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════
    private void toggleMembersSidebar() {
        sidebarVisible = !sidebarVisible;
        membersSidebar.setVisible(sidebarVisible);
        membersSidebar.setManaged(sidebarVisible);
        if (sidebarVisible) loadGroupMembersWithRoles();
    }

    private void startMeeting(String type) {
        client.sendCommand("START_MEETING:" + groupId + ":" + type);
        showToast((type.equals("VIDEO") ? "📹" : "📞") + "  Réunion " + type + " lancée !");
    }


    private void showWelcomeState() {
        messageContainer.getChildren().clear();
        Label emoji = new Label("👥"); emoji.setFont(Font.font("Segoe UI", 42)); emoji.setAlignment(Pos.CENTER);
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(3), emoji);
        pulse.setFromX(1); pulse.setToX(1.08); pulse.setAutoReverse(true); pulse.setCycleCount(Animation.INDEFINITE); pulse.play();
        Label title = new Label(groupName); title.setFont(Font.font("Georgia", FontWeight.BOLD, 22)); title.setTextFill(Color.web(INK));
        Label sub = new Label("Aucun message pour l'instant.\nSoyez le premier à écrire !");
        sub.setFont(Font.font("Segoe UI", 13)); sub.setTextFill(Color.web(INK_LITE)); sub.setAlignment(Pos.CENTER); sub.setWrapText(true);
        VBox content = new VBox(16, emoji, title, sub); content.setAlignment(Pos.CENTER);
        StackPane centered = new StackPane(content); centered.setPrefHeight(400); centered.setAlignment(Pos.CENTER);
        messageContainer.getChildren().add(centered);
    }

    private void clearReply() {
        replyToId = null; replyToText = null;
        replyBar.setVisible(false); replyBar.setManaged(false);
        replyLabel.setText("");
    }

    private Button topBtn(String icon, String tip) {
        Button btn = new Button(icon); btn.setFont(Font.font("Georgia", 15)); btn.setPrefSize(38, 38);
        btn.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; -fx-cursor: hand; -fx-background-radius: 19;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 19;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; -fx-cursor: hand; -fx-background-radius: 19;"));
        Tooltip.install(btn, new Tooltip(tip)); return btn;
    }

    private MenuItem menuItem(String text) {
        MenuItem m = new MenuItem(text);
        m.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-padding: 6 14;");
        return m;
    }

    // ── Bouton attachement identique à ChatWindow ──────────────────
    private Button buildAttachButton() {
        Canvas canvas = new Canvas(26, 26);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(Color.web(INK_LITE)); gc.setLineWidth(2.2);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        drawTrombone(gc);

        StackPane iconPane = new StackPane(canvas); iconPane.setPrefSize(48, 48);
        iconPane.setStyle("-fx-background-color: " + WHITE + "; -fx-background-radius: 24; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 24; -fx-border-width: 1.2; -fx-cursor: hand;");

        Button btn = new Button(); btn.setGraphic(iconPane);
        btn.setPrefSize(48, 48); btn.setMinSize(48, 48); btn.setMaxSize(48, 48);
        btn.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-color: transparent; -fx-cursor: hand;");

        iconPane.setOnMouseEntered(e -> {
            iconPane.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 24; -fx-border-color: " + SAGE + "; -fx-border-radius: 24; -fx-border-width: 1.2; -fx-cursor: hand;");
            gc.clearRect(0, 0, 26, 26); gc.setStroke(Color.web(SAGE)); drawTrombone(gc);
        });
        iconPane.setOnMouseExited(e -> {
            iconPane.setStyle("-fx-background-color: " + WHITE + "; -fx-background-radius: 24; -fx-border-color: " + BORDER_W + "; -fx-border-radius: 24; -fx-border-width: 1.2; -fx-cursor: hand;");
            gc.clearRect(0, 0, 26, 26); gc.setStroke(Color.web(INK_LITE)); drawTrombone(gc);
        });
        Tooltip.install(btn, new Tooltip("Joindre un fichier"));
        return btn;
    }

    private void drawTrombone(GraphicsContext gc) {
        gc.beginPath(); gc.moveTo(18, 22); gc.arcTo(22, 22, 22, 10, 6); gc.arcTo(22, 4, 8, 4, 7);
        gc.arcTo(4, 4, 4, 18, 7); gc.arcTo(4, 24, 14, 24, 6); gc.lineTo(18, 24); gc.stroke();
        gc.beginPath(); gc.moveTo(14, 20); gc.arcTo(18, 20, 18, 10, 4); gc.arcTo(18, 7, 10, 7, 4);
        gc.arcTo(7, 7, 7, 18, 4); gc.stroke();
    }

    // ── Bouton micro identique à ChatWindow ────────────────────────
    private Button buildMicButton() {
        Label micIco = new Label("🎙");
        micIco.setFont(Font.font("Segoe UI", 20)); micIco.setTextFill(Color.WHITE); micIco.setAlignment(Pos.CENTER);
        StackPane circle = new StackPane(micIco);
        circle.setPrefSize(48, 48); circle.setMinSize(48, 48); circle.setMaxSize(48, 48);
        circle.setStyle("-fx-background-color: " + SAGE + "; -fx-background-radius: 24; -fx-cursor: hand;");
        Button btn = new Button(); btn.setGraphic(circle);
        btn.setPrefSize(48, 48); btn.setMinSize(48, 48); btn.setMaxSize(48, 48);
        btn.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-color: transparent; -fx-cursor: hand;");
        Tooltip.install(btn, new Tooltip("Message vocal"));
        return btn;
    }

    private void showToast(String msg) {
        Platform.runLater(() -> {
            Label t = new Label(msg); t.setFont(Font.font("Segoe UI", 12)); t.setTextFill(Color.WHITE);
            t.setPadding(new Insets(8, 20, 8, 20));
            t.setStyle("-fx-background-color: " + INK + "; -fx-background-radius: 20; -fx-opacity: 0.9;");
            StackPane o = new StackPane(t); o.setAlignment(Pos.BOTTOM_CENTER); o.setMouseTransparent(true);
            messageContainer.getChildren().add(o);
            new Timeline(new KeyFrame(Duration.seconds(2), e -> messageContainer.getChildren().remove(o))).play();
        });
    }

    private void showForwardDialog(String content, String type) {
        Stage d = new Stage();
        d.setTitle("Transférer le message");
        d.setResizable(false);

        Label title = new Label("Transférer vers...");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 15));
        title.setTextFill(Color.web(INK));

        // Champ recherche
        TextField searchFld = new TextField();
        searchFld.setPromptText("Rechercher un contact ou groupe...");
        searchFld.setPrefHeight(40);
        searchFld.setStyle(
                "-fx-background-color: " + CREAM + "; -fx-border-color: " + BORDER_W + "; " +
                        "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 0 14; " +
                        "-fx-font-size: 13px; -fx-font-family: 'Segoe UI';");

        // Liste des destinations
        ListView<String[]> destList = new ListView<>();
        destList.setPrefHeight(260);
        destList.setStyle("-fx-background-color: " + CREAM_2 + "; -fx-border-color: " + BORDER_W + ";");
        VBox.setVgrow(destList, Priority.ALWAYS);

        // Charger contacts + groupes en background
        new Thread(() -> {
            List<String[]> items = new ArrayList<>();
            // Contacts → [type, id, displayName]  type = "CONTACT"
            List<model.User> contacts = database.ContactDAO.getContacts(client.getUser().getId());
            for (model.User u : contacts) {
                items.add(new String[]{"CONTACT", u.getId(), u.getUsername()});
            }
            // Groupes → type = "GROUP"
            List<String[]> groups = database.GroupDAO.getGroupsOfUser(client.getUser().getId());
            for (String[] g : groups) {
                if (!g[0].equals(groupId)) // ne pas proposer le groupe actuel
                    items.add(new String[]{"GROUP", g[0], g[1]});
            }
            Platform.runLater(() -> destList.getItems().setAll(items));
        }).start();

        // CellFactory
        destList.setCellFactory(lv -> new ListCell<String[]>() {
            @Override protected void updateItem(String[] item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                String icon = "CONTACT".equals(item[0]) ? "👤" : "👥";
                Label ico = new Label(icon);
                ico.setFont(Font.font("Segoe UI", 16));
                Label name = new Label(item[2]);
                name.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                name.setTextFill(Color.web(INK));
                Label typeLbl = new Label("CONTACT".equals(item[0]) ? "Contact" : "Groupe");
                typeLbl.setFont(Font.font("Segoe UI", 10));
                typeLbl.setTextFill(Color.web(INK_LITE));
                VBox info = new VBox(2, name, typeLbl);
                HBox row = new HBox(10, ico, info);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 12, 6, 12));
                setGraphic(row);
                setText(null);
                setStyle("-fx-background-color: transparent;");
                setOnMouseEntered(e -> setStyle("-fx-background-color: " + MINT_PALE + ";"));
                setOnMouseExited(e -> setStyle("-fx-background-color: transparent;"));
            }
        });

        // Filtrage recherche
        searchFld.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal.trim().toLowerCase();
            destList.getItems().filtered(item -> item[2].toLowerCase().contains(filter));
            // Recharger avec filtre
            new Thread(() -> {
                List<String[]> allItems = new ArrayList<>();
                List<model.User> contacts = database.ContactDAO.getContacts(client.getUser().getId());
                for (model.User u : contacts)
                    allItems.add(new String[]{"CONTACT", u.getId(), u.getUsername()});
                List<String[]> groups = database.GroupDAO.getGroupsOfUser(client.getUser().getId());
                for (String[] g : groups)
                    if (!g[0].equals(groupId))
                        allItems.add(new String[]{"GROUP", g[0], g[1]});
                List<String[]> filtered = new ArrayList<>();
                for (String[] item : allItems)
                    if (item[2].toLowerCase().contains(filter)) filtered.add(item);
                Platform.runLater(() -> destList.getItems().setAll(filtered));
            }).start();
        });

        Button sendBtn = new Button("↗  Transférer");
        sendBtn.setMaxWidth(Double.MAX_VALUE);
        sendBtn.setPrefHeight(42);
        sendBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        sendBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;");
        sendBtn.setDisable(true);

        destList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) ->
                sendBtn.setDisable(n == null));

        sendBtn.setOnAction(e -> {
            String[] selected = destList.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            forwardMessage(content, type, selected);
            d.close();
            showToast("Message transféré à " + selected[2] + " !");
        });

        // Double-clic = transfert direct
        destList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) sendBtn.fire();
        });

        VBox layout = new VBox(12, title, searchFld, destList, sendBtn);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: white;");
        VBox.setVgrow(destList, Priority.ALWAYS);

        d.setScene(new Scene(layout, 320, 420));
        d.show();
    }

    private void forwardMessage(String content, String type, String[] destination) {
        if ("CONTACT".equals(destination[0])) {
            // Transférer vers contact privé
            if ("VOICE".equals(type)) {
                client.sendCommand("FORWARD_VOICE:" + destination[1] + ":" + content);
            } else if (type != null && type.startsWith("FILE:")) {
                String fileName = type.substring(5);
                client.sendCommand("FORWARD_FILE:" + destination[1] + ":" + fileName + ":" + content);
            } else {
                // Message texte → utiliser le canal message normal
                model.User receiver = new model.User(destination[1], destination[2], "");
                model.Message msg = new model.Message(
                        java.util.UUID.randomUUID().toString(),
                        "↗ " + content,
                        client.getUser(),
                        receiver,
                        new java.util.Date(),
                        model.MessageType.TEXT
                );
                client.sendMessage(msg);
            }
        } else {
            // Transférer vers groupe
            if ("VOICE".equals(type)) {
                client.sendCommand("GROUP_VOICE:" + destination[1] + ":" + content);
            } else if (type != null && type.startsWith("FILE:")) {
                String fileName = type.substring(5);
                client.sendCommand("GROUP_FILE:" + destination[1] + ":" + fileName + ":" + content);
            } else {
                client.sendCommand("GROUP_MSG:" + destination[1] + ":↗ " + content);
            }
        }
    }

    public String getGroupId() { return groupId; }

    public static void main(String[] args) { launch(args); }
}