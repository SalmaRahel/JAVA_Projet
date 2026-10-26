package ui;

import client.Client;
import database.UserDAO;
import javafx.animation.*;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.Base64;

public class ProfileWindow extends Application {

    private static final String CREAM      = "#FAFAF7";
    private static final String CREAM_2    = "#F3F3EE";
    private static final String WHITE      = "#FFFFFF";
    private static final String FOREST     = "#1A3C34";
    private static final String FOREST_MID = "#2D5A4F";
    private static final String SAGE       = "#4A7C6F";
    private static final String MINT_PALE  = "#E8F2EF";
    private static final String INK        = "#1C1C1A";
    private static final String INK_LITE   = "#9A9A92";
    private static final String BORDER_W   = "#E8E8E2";

    private Client client;
    private Stage stage;
    private ImageView photoView;
    private TextField usernameField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private byte[] selectedPhotoBytes = null;
    private Label feedbackLabel;
    private Label headerNameLabel;

    public ProfileWindow(Client client) { this.client = client; }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("Mon Profil");
        String username = client.getUser().getUsername();

        // ── Bannière ──
        Region banner = new Region();
        banner.setPrefHeight(100);
        banner.setStyle("-fx-background-color: linear-gradient(to right, #1A3C34, #2D5A4F, #4A7C6F);");

        // ── Photo ──
        photoView = new ImageView();
        photoView.setFitWidth(96); photoView.setFitHeight(96); photoView.setPreserveRatio(false);

        String currentPhoto = client.getUser().getProfilePhoto();
        if (currentPhoto != null && !currentPhoto.isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(currentPhoto);
                photoView.setImage(new Image(new java.io.ByteArrayInputStream(bytes)));
            } catch (Exception e) { setDefaultPhoto(username); }
        } else { setDefaultPhoto(username); }

        Circle photoClip = new Circle(48, 48, 48);
        photoView.setClip(photoClip);

        Region camOverlay = new Region();
        camOverlay.setPrefSize(96, 96);
        camOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.35); -fx-background-radius: 48;");
        camOverlay.setOpacity(0);

        Label camLabel = new Label("\uD83D\uDCF7");
        camLabel.setFont(Font.font("Segoe UI", 22));
        camLabel.setTextFill(Color.WHITE);
        camLabel.setOpacity(0);

        StackPane photoStack = new StackPane(photoView, camOverlay, camLabel);
        photoStack.setPrefSize(96, 96); photoStack.setMinSize(96, 96); photoStack.setMaxSize(96, 96);
        photoStack.setStyle("-fx-cursor: hand;");
        photoStack.setOnMouseEntered(e -> {
            FadeTransition ft1 = new FadeTransition(Duration.millis(150), camOverlay); ft1.setToValue(1); ft1.play();
            FadeTransition ft2 = new FadeTransition(Duration.millis(150), camLabel);   ft2.setToValue(1); ft2.play();
        });
        photoStack.setOnMouseExited(e -> {
            FadeTransition ft1 = new FadeTransition(Duration.millis(150), camOverlay); ft1.setToValue(0); ft1.play();
            FadeTransition ft2 = new FadeTransition(Duration.millis(150), camLabel);   ft2.setToValue(0); ft2.play();
        });
        photoStack.setOnMouseClicked(e -> choosePhoto());

        DropShadow photoShadow = new DropShadow();
        photoShadow.setColor(Color.web("#000000", 0.30)); photoShadow.setRadius(16); photoShadow.setOffsetY(5);
        photoStack.setEffect(photoShadow);

        Circle statusDot = new Circle(7, Color.web(SAGE));
        statusDot.setStroke(Color.WHITE); statusDot.setStrokeWidth(2.5);
        DropShadow dotGlow = new DropShadow();
        dotGlow.setColor(Color.web(SAGE, 0.6)); dotGlow.setRadius(8);
        statusDot.setEffect(dotGlow);

        StackPane avatarContainer = new StackPane(photoStack, statusDot);
        StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
        statusDot.setTranslateX(-2); statusDot.setTranslateY(-2);

        headerNameLabel = new Label(username);
        headerNameLabel.setFont(Font.font("Georgia", FontWeight.BOLD, 19));
        headerNameLabel.setTextFill(Color.web(INK));

        Label handleLabel = new Label("@" + username.toLowerCase());
        handleLabel.setFont(Font.font("Segoe UI", 12));
        handleLabel.setTextFill(Color.web(INK_LITE));

        Circle badgeDot = new Circle(4, Color.web(SAGE));
        Label badgeTxt = new Label("En ligne");
        badgeTxt.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        badgeTxt.setTextFill(Color.web(FOREST));
        HBox badgeRow = new HBox(5, badgeDot, badgeTxt);
        badgeRow.setAlignment(Pos.CENTER);
        badgeRow.setPadding(new Insets(4, 12, 4, 12));
        badgeRow.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-background-radius: 20; " +
                "-fx-border-color: rgba(74,124,111,0.18); -fx-border-radius: 20; -fx-border-width: 1;");

        VBox nameBox = new VBox(3, headerNameLabel, handleLabel, badgeRow);
        nameBox.setAlignment(Pos.CENTER);
        nameBox.setPadding(new Insets(12, 0, 0, 0));

        avatarContainer.setTranslateY(-48);
        nameBox.setTranslateY(-48);

        VBox headerSection = new VBox(banner, avatarContainer, nameBox);
        headerSection.setAlignment(Pos.TOP_CENTER);
        headerSection.setStyle("-fx-background-color: " + WHITE + ";");

        // ── Stats bar ──
        HBox statsBar = new HBox();
        statsBar.setAlignment(Pos.CENTER);
        statsBar.setTranslateY(-36);
        statsBar.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: #F3F4F6 transparent transparent transparent; " +
                "-fx-border-width: 1 0 0 0; -fx-padding: 14 0 14 0;");
        for (int i = 0; i < 3; i++) {
            String[] labels = {"Contacts", "Messages", "Appels"};
            Label val = new Label("—");
            val.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
            val.setTextFill(Color.web(FOREST));
            Label lbl = new Label(labels[i]);
            lbl.setFont(Font.font("Segoe UI", 11));
            lbl.setTextFill(Color.web(INK_LITE));
            VBox col = new VBox(2, val, lbl);
            col.setAlignment(Pos.CENTER);
            HBox.setHgrow(col, Priority.ALWAYS);
            statsBar.getChildren().add(col);
            if (i < 2) {
                Region div = new Region();
                div.setPrefWidth(1); div.setPrefHeight(36);
                div.setStyle("-fx-background-color: #F3F4F6;");
                statsBar.getChildren().add(div);
            }
        }

        // ── Formulaire ──
        Label formTitle = new Label("INFORMATIONS DU COMPTE");
        formTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        formTitle.setTextFill(Color.web(INK_LITE));

        VBox usernameSection = buildField("NOM D'UTILISATEUR", username, false);
        usernameField = (TextField) usernameSection.lookup(".pf-text");

        VBox passwordSection = buildField("NOUVEAU MOT DE PASSE", "Laisser vide pour ne pas changer", true);
        passwordField = (PasswordField) passwordSection.lookup(".pf-pass");

        VBox confirmSection = buildField("CONFIRMER LE MOT DE PASSE", "Répétez le mot de passe", true);
        confirmPasswordField = (PasswordField) confirmSection.lookup(".pf-pass");

        Button changePhotoBtn = new Button("\uD83D\uDCF7   Changer la photo de profil");
        changePhotoBtn.setMaxWidth(Double.MAX_VALUE);
        changePhotoBtn.setPrefHeight(42);
        changePhotoBtn.setFont(Font.font("Segoe UI", 13));
        changePhotoBtn.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; " +
                "-fx-background-radius: 12; -fx-cursor: hand; -fx-border-color: rgba(74,124,111,0.18); " +
                "-fx-border-radius: 12; -fx-border-width: 1;");
        changePhotoBtn.setOnMouseEntered(e -> changePhotoBtn.setStyle(
                "-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 12; -fx-cursor: hand;"));
        changePhotoBtn.setOnMouseExited(e -> changePhotoBtn.setStyle(
                "-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; -fx-background-radius: 12; " +
                        "-fx-cursor: hand; -fx-border-color: rgba(74,124,111,0.18); -fx-border-radius: 12; -fx-border-width: 1;"));
        changePhotoBtn.setOnAction(e -> choosePhoto());

        // ── Section Confidentialité ──
        Label privacyTitle = new Label("CONFIDENTIALITÉ");
        privacyTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        privacyTitle.setTextFill(Color.web(INK_LITE));

        Label privacyLbl = new Label("Qui peut m'ajouter dans un groupe ?");
        privacyLbl.setFont(Font.font("Segoe UI", 13));
        privacyLbl.setTextFill(Color.web(INK));

        ComboBox<String> groupPrivacyBox = new ComboBox<>();
        groupPrivacyBox.getItems().addAll("Tout le monde", "Mes contacts seulement", "Personne");
        groupPrivacyBox.setValue("Tout le monde");
        groupPrivacyBox.setMaxWidth(Double.MAX_VALUE);
        groupPrivacyBox.setPrefHeight(42);
        groupPrivacyBox.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px;");

        // Charger la valeur actuelle depuis la DB
        new Thread(() -> {
            String current = database.GroupDAO.getGroupAddPermission(client.getUser().getId());
            javafx.application.Platform.runLater(() -> {
                switch (current) {
                    case "contacts_only": groupPrivacyBox.setValue("Mes contacts seulement"); break;
                    case "nobody":        groupPrivacyBox.setValue("Personne"); break;
                    default:              groupPrivacyBox.setValue("Tout le monde");
                }
            });
        }).start();

        groupPrivacyBox.setOnAction(e -> {
            String selected = groupPrivacyBox.getValue();
            if (selected == null) return;
            String dbValue;
            switch (selected) {
                case "Mes contacts seulement": dbValue = "contacts_only"; break;
                case "Personne":               dbValue = "nobody"; break;
                default:                       dbValue = "everyone";
            }
            database.GroupDAO.setGroupAddPermission(client.getUser().getId(), dbValue);
            showFeedback("✓  Confidentialité mise à jour !", SAGE);
        });

        VBox privacySection = new VBox(8, privacyLbl, groupPrivacyBox);

        // ── Bouton sauvegarder ──
        Button saveBtn = new Button("  Sauvegarder les modifications");
        saveBtn.setPrefHeight(50);
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        saveBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 14; -fx-cursor: hand;");
        DropShadow btnShadow = new DropShadow();
        btnShadow.setColor(Color.web(FOREST, 0.35)); btnShadow.setRadius(14); btnShadow.setOffsetY(5);
        saveBtn.setEffect(btnShadow);
        saveBtn.setOnMouseEntered(e -> {
            saveBtn.setStyle("-fx-background-color: " + FOREST_MID + "; -fx-text-fill: white; -fx-background-radius: 14; -fx-cursor: hand;");
            DropShadow s2 = new DropShadow(); s2.setColor(Color.web(FOREST, 0.5)); s2.setRadius(18); s2.setOffsetY(6);
            saveBtn.setEffect(s2);
        });
        saveBtn.setOnMouseExited(e -> {
            saveBtn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 14; -fx-cursor: hand;");
            saveBtn.setEffect(btnShadow);
        });
        saveBtn.setOnAction(e -> saveProfile());

        feedbackLabel = new Label("");
        feedbackLabel.setFont(Font.font("Segoe UI", 12));
        feedbackLabel.setWrapText(true);

        Region spacer4 = new Region(); spacer4.setPrefHeight(4);
        Region spacerBottom = new Region(); spacerBottom.setPrefHeight(20);

        // ── Assembler le formulaire ──
        VBox formBody = new VBox(14,
                formTitle,
                usernameSection,
                new Separator(),
                passwordSection,
                confirmSection,
                changePhotoBtn,
                new Separator(),
                privacyTitle,
                privacySection,
                spacer4,
                saveBtn,
                feedbackLabel,
                spacerBottom
        );
        formBody.setPadding(new Insets(16, 28, 28, 28));
        formBody.setStyle("-fx-background-color: " + WHITE + ";");

        // ── Root avec ScrollPane ──
        VBox root = new VBox(headerSection, statsBar, formBody);
        root.setStyle("-fx-background-color: " + WHITE + ";");

        ScrollPane scrollRoot = new ScrollPane(root);
        scrollRoot.setFitToWidth(true);
        scrollRoot.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollRoot.setStyle("-fx-background-color: " + WHITE + "; -fx-background: " + WHITE + "; -fx-border-color: transparent;");

        root.setOpacity(0);
        Scene scene = new Scene(scrollRoot, 410, 680);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        FadeTransition ftIn = new FadeTransition(Duration.millis(400), root);
        ftIn.setFromValue(0); ftIn.setToValue(1); ftIn.play();
    }

    private VBox buildField(String label, String initialOrPrompt, boolean isPass) {
        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        lbl.setTextFill(Color.web(INK_LITE));

        String base = "-fx-border-color: " + BORDER_W + "; -fx-border-radius: 12; -fx-background-radius: 12; " +
                "-fx-padding: 10 14; -fx-font-size: 13px; -fx-font-family: 'Segoe UI'; " +
                "-fx-background-color: " + CREAM_2 + "; -fx-text-fill: " + INK + "; -fx-prompt-text-fill: " + INK_LITE + ";";
        String focused = "-fx-border-color: " + FOREST + "; -fx-border-width: 1.5; -fx-border-radius: 12; " +
                "-fx-background-radius: 12; -fx-padding: 10 14; -fx-font-size: 13px; -fx-font-family: 'Segoe UI'; " +
                "-fx-background-color: white; -fx-text-fill: " + INK + "; -fx-prompt-text-fill: " + INK_LITE + ";";

        if (isPass) {
            PasswordField f = new PasswordField();
            f.setPromptText(initialOrPrompt); f.setPrefHeight(46);
            f.getStyleClass().add("pf-pass"); f.setStyle(base);
            f.focusedProperty().addListener((o, old, on) -> {
                f.setStyle(on ? focused : base);
                if (on) { DropShadow s = new DropShadow(); s.setColor(Color.web(FOREST, 0.12)); s.setRadius(8); f.setEffect(s); }
                else f.setEffect(null);
            });
            return new VBox(6, lbl, f);
        } else {
            TextField f = new TextField(initialOrPrompt); f.setPrefHeight(46);
            f.getStyleClass().add("pf-text"); f.setStyle(base);
            f.focusedProperty().addListener((o, old, on) -> {
                f.setStyle(on ? focused : base);
                if (on) { DropShadow s = new DropShadow(); s.setColor(Color.web(FOREST, 0.12)); s.setRadius(8); f.setEffect(s); }
                else f.setEffect(null);
            });
            return new VBox(6, lbl, f);
        }
    }

    private void choosePhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choisir une photo de profil");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = fc.showOpenDialog(stage);
        if (file != null) {
            try {
                selectedPhotoBytes = java.nio.file.Files.readAllBytes(file.toPath());
                photoView.setImage(new Image(new java.io.ByteArrayInputStream(selectedPhotoBytes)));
                Circle c = new Circle(48, 48, 48); photoView.setClip(c);
                showFeedback("\u2713  Photo sélectionnée", SAGE);
            } catch (Exception e) { showFeedback("Impossible de charger l'image !", "#DC2626"); }
        }
    }

    private void saveProfile() {
        String newUsername = usernameField != null ? usernameField.getText().trim() : "";
        String newPassword = passwordField != null ? passwordField.getText() : "";
        String confirmPassword = confirmPasswordField != null ? confirmPasswordField.getText() : "";

        if (newUsername.isEmpty()) { showFeedback("Le nom ne peut pas être vide !", "#DC2626"); return; }
        if (!newPassword.isEmpty() && !newPassword.equals(confirmPassword)) {
            showFeedback("Les mots de passe ne correspondent pas !", "#DC2626"); return;
        }

        String userId = client.getUser().getId();
        String finalPassword = newPassword.isEmpty() ? client.getUser().getPassword() : newPassword;

        boolean ok = UserDAO.updateUser(userId, newUsername, finalPassword);
        if (selectedPhotoBytes != null) {
            String b64 = Base64.getEncoder().encodeToString(selectedPhotoBytes);
            UserDAO.updateProfilePhoto(userId, b64);
            client.getUser().setProfilePhoto(b64);
        }
        if (ok) {
            client.getUser().setUsername(newUsername);
            client.getUser().setPassword(finalPassword);
            if (headerNameLabel != null) headerNameLabel.setText(newUsername);
            String photoB64 = selectedPhotoBytes != null
                    ? Base64.getEncoder().encodeToString(selectedPhotoBytes)
                    : (client.getUser().getProfilePhoto() != null ? client.getUser().getProfilePhoto() : "");
            client.sendCommand("UPDATE_PROFILE:" + userId + ":" + newUsername + ":" + photoB64);
            showFeedback("\u2713  Profil mis à jour !", SAGE);
            new Timeline(new KeyFrame(Duration.seconds(1.5), e -> stage.close())).play();
        } else {
            showFeedback("Erreur lors de la sauvegarde !", "#DC2626");
        }
    }

    private void setDefaultPhoto(String username) {
        Canvas canvas = new Canvas(96, 96);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web(FOREST)); gc.fillOval(0, 0, 96, 96);
        gc.setFill(Color.WHITE); gc.setFont(Font.font("Georgia", FontWeight.BOLD, 38));
        String init = (username != null && !username.isEmpty()) ? username.substring(0, 1).toUpperCase() : "?";
        gc.fillText(init, 29, 63);
        photoView.setImage(canvas.snapshot(null, null));
    }

    private void showFeedback(String msg, String color) {
        feedbackLabel.setText(msg);
        feedbackLabel.setTextFill(Color.web(color));
        feedbackLabel.setOpacity(1);
        new Timeline(new KeyFrame(Duration.seconds(3),
                new KeyValue(feedbackLabel.opacityProperty(), 0))).play();
    }
}