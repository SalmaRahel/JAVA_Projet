package ui;

import client.Client;
import database.UserDAO;
import model.User;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

public class LoginWindow extends Application {

    private static final String BG_APP    = "#F0F4F3";
    private static final String BG_WHITE  = "#FFFFFF";
    private static final String BG_INPUT  = "#F7FAF9";
    private static final String GREEN     = "#075E54";
    private static final String GREEN_MID = "#128C7E";
    private static final String GREEN_LT  = "#25D366";
    private static final String GREEN_PALE= "#E8F5E9";
    private static final String BORDER    = "#E5E7EB";
    private static final String TEXT_DARK = "#111827";
    private static final String TEXT_MID  = "#4B5563";
    private static final String TEXT_LITE = "#9CA3AF";

    private Client client;
    private TextField usernameField;
    private PasswordField passwordField;
    private Button connectButton;
    private Label statusLabel;
    private Stage stage;

    public LoginWindow() { }
    public LoginWindow(Client client) { this.client = client; }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("WhatsApp — Connexion");
        buildLoginUI();
        primaryStage.show();
    }

    private void buildLoginUI() {
        stage.setTitle("WhatsApp — Connexion");
        VBox leftPanel = buildLeftPanel();

        Label titleLbl = new Label("Bon retour !");
        titleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        titleLbl.setTextFill(Color.web(TEXT_DARK));

        Label subLbl = new Label("Connectez-vous pour continuer");
        subLbl.setFont(Font.font("Segoe UI", 13));
        subLbl.setTextFill(Color.web(TEXT_LITE));

        VBox usernameBox = buildField("NOM D'UTILISATEUR", "ex: salma123", false);
        usernameField = (TextField) usernameBox.lookup(".login-field");

        VBox passwordBox = buildField("MOT DE PASSE", "••••••••", true);
        passwordField = (PasswordField) passwordBox.lookup(".login-pass");

        connectButton = buildPrimaryBtn("Se connecter");
        connectButton.setOnAction(e -> onConnect());
        if (passwordField != null) passwordField.setOnAction(e -> onConnect());

        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: " + BORDER + ";");

        Button switchBtn = new Button("Pas de compte ?   S'inscrire \u2192");
        switchBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + GREEN + "; -fx-cursor: hand; -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-padding: 0;");
        switchBtn.setOnMouseEntered(e -> switchBtn.setTextFill(Color.web(GREEN_LT)));
        switchBtn.setOnMouseExited(e -> switchBtn.setTextFill(Color.web(GREEN)));
        switchBtn.setOnAction(e -> buildRegisterUI());

        statusLabel = new Label("");
        statusLabel.setFont(Font.font("Segoe UI", 12));
        statusLabel.setWrapText(true);

        HBox switchBox = new HBox(switchBtn);
        switchBox.setAlignment(Pos.CENTER);

        VBox form = new VBox(18,
                new VBox(5, titleLbl, subLbl),
                spacer(10),
                usernameBox, passwordBox,
                connectButton, divider,
                switchBox, statusLabel
        );
        form.setPadding(new Insets(50, 52, 44, 52));
        form.setAlignment(Pos.CENTER_LEFT);
        form.setStyle("-fx-background-color: " + BG_WHITE + ";");
        HBox.setHgrow(form, Priority.ALWAYS);

        HBox root = new HBox(leftPanel, form);
        root.setStyle("-fx-background-color: " + BG_APP + ";");
        Scene scene = new Scene(root, 720, 490);
        stage.setScene(scene);
        stage.setResizable(false);

        root.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(500), root);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private void buildRegisterUI() {
        stage.setTitle("WhatsApp — Inscription");
        VBox leftPanel = buildLeftPanel();

        Label titleLbl = new Label("Créer un compte");
        titleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        titleLbl.setTextFill(Color.web(TEXT_DARK));

        Label subLbl = new Label("Rejoignez WhatsApp gratuitement");
        subLbl.setFont(Font.font("Segoe UI", 13));
        subLbl.setTextFill(Color.web(TEXT_LITE));

        VBox usernameBox = buildField("NOM D'UTILISATEUR", "Choisissez un nom", false);
        usernameField = (TextField) usernameBox.lookup(".login-field");

        VBox passwordBox = buildField("MOT DE PASSE", "Min. 4 caracteres", true);
        passwordField = (PasswordField) passwordBox.lookup(".login-pass");

        VBox confirmBox = buildField("CONFIRMER", "Repetez le mot de passe", true);
        PasswordField confirmField = (PasswordField) confirmBox.lookup(".login-pass");

        Button createBtn = buildPrimaryBtn("Creer le compte");
        createBtn.setOnAction(e -> onRegister(confirmField != null ? confirmField.getText() : ""));

        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: " + BORDER + ";");

        Button backBtn = new Button("\u2190  Deja un compte ? Se connecter");
        backBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + GREEN + "; -fx-cursor: hand; -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-padding: 0;");
        backBtn.setOnMouseEntered(e -> backBtn.setTextFill(Color.web(GREEN_LT)));
        backBtn.setOnMouseExited(e -> backBtn.setTextFill(Color.web(GREEN)));
        backBtn.setOnAction(e -> buildLoginUI());

        statusLabel = new Label("");
        statusLabel.setFont(Font.font("Segoe UI", 12));
        statusLabel.setWrapText(true);

        HBox backBox = new HBox(backBtn);
        backBox.setAlignment(Pos.CENTER);

        VBox form = new VBox(14,
                new VBox(5, titleLbl, subLbl),
                spacer(6),
                usernameBox, passwordBox, confirmBox,
                createBtn, divider, backBox, statusLabel
        );
        form.setPadding(new Insets(40, 52, 36, 52));
        form.setAlignment(Pos.CENTER_LEFT);
        form.setStyle("-fx-background-color: " + BG_WHITE + ";");
        HBox.setHgrow(form, Priority.ALWAYS);

        HBox root = new HBox(leftPanel, form);
        root.setStyle("-fx-background-color: " + BG_APP + ";");
        Scene scene = new Scene(root, 720, 560);
        stage.setScene(scene);

        root.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(400), root);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private VBox buildLeftPanel() {
        Circle orbOut = new Circle(52);
        orbOut.setFill(Color.TRANSPARENT);
        orbOut.setStroke(Color.web(GREEN, 0.10));
        orbOut.setStrokeWidth(30);
        Circle orbMid = new Circle(34, Color.web(GREEN_PALE));
        Circle orbCore = new Circle(20, Color.web(GREEN));
        Label logoLbl = new Label("W");
        logoLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 22));
        logoLbl.setTextFill(Color.WHITE);
        javafx.scene.layout.StackPane orb = new javafx.scene.layout.StackPane(orbOut, orbMid, orbCore, logoLbl);
        orb.setPrefSize(130, 130);
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(2.8), orb);
        pulse.setFromX(1); pulse.setToX(1.06); pulse.setFromY(1); pulse.setToY(1.06);
        pulse.setAutoReverse(true); pulse.setCycleCount(Animation.INDEFINITE); pulse.play();

        Label appName = new Label("WhatsApp");
        appName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        appName.setTextFill(Color.web(GREEN));

        Region line = new Region();
        line.setPrefSize(50, 2);
        line.setStyle("-fx-background-color: " + GREEN_LT + "; -fx-background-radius: 1;");
        HBox lineBox = new HBox(line); lineBox.setAlignment(Pos.CENTER);

        Label tagline = new Label("Messagerie simple\net securisee");
        tagline.setFont(Font.font("Segoe UI", 13));
        tagline.setTextFill(Color.web(TEXT_MID));
        tagline.setTextAlignment(TextAlignment.CENTER);

        HBox dots = new HBox(7); dots.setAlignment(Pos.CENTER);
        String[] dColors = {TEXT_LITE, GREEN_LT, TEXT_LITE};
        int[] dSizes = {4, 6, 4};
        for (int i = 0; i < 3; i++) {
            Circle d = new Circle(dSizes[i], Color.web(dColors[i]));
            if (i == 1) { DropShadow g = new DropShadow(); g.setColor(Color.web(GREEN_LT, 0.5)); g.setRadius(6); d.setEffect(g); }
            dots.getChildren().add(d);
        }

        VBox panel = new VBox(18, orb, appName, lineBox, tagline, dots);
        panel.setAlignment(Pos.CENTER);
        panel.setPrefWidth(265);
        panel.setPadding(new Insets(40, 20, 40, 20));
        panel.setStyle("-fx-background-color: " + BG_APP + "; -fx-border-color: transparent " + BORDER + " transparent transparent; -fx-border-width: 0 1.5 0 0;");
        return panel;
    }

    private VBox buildField(String label, String prompt, boolean isPass) {
        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        lbl.setTextFill(Color.web(TEXT_MID));
        String base = "-fx-background-color: " + BG_INPUT + "; -fx-border-color: " + BORDER + "; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 0 16; -fx-font-size: 13px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + TEXT_DARK + "; -fx-prompt-text-fill: " + TEXT_LITE + ";";
        String focused = "-fx-background-color: white; -fx-border-color: " + GREEN + "; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 0 16; -fx-font-size: 13px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + TEXT_DARK + "; -fx-prompt-text-fill: " + TEXT_LITE + ";";
        if (isPass) {
            PasswordField f = new PasswordField();
            f.setPromptText(prompt); f.setPrefHeight(46);
            f.getStyleClass().add("login-pass"); f.setStyle(base);
            f.focusedProperty().addListener((o, old, on) -> { f.setStyle(on ? focused : base); if (on) { DropShadow s = new DropShadow(); s.setColor(Color.web(GREEN, 0.15)); s.setRadius(8); f.setEffect(s); } else f.setEffect(null); });
            return new VBox(6, lbl, f);
        } else {
            TextField f = new TextField();
            f.setPromptText(prompt); f.setPrefHeight(46);
            f.getStyleClass().add("login-field"); f.setStyle(base);
            f.focusedProperty().addListener((o, old, on) -> { f.setStyle(on ? focused : base); if (on) { DropShadow s = new DropShadow(); s.setColor(Color.web(GREEN, 0.15)); s.setRadius(8); f.setEffect(s); } else f.setEffect(null); });
            return new VBox(6, lbl, f);
        }
    }

    private Button buildPrimaryBtn(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE); btn.setPrefHeight(48);
        btn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        btn.setStyle("-fx-background-color: " + GREEN + "; -fx-text-fill: white; -fx-background-radius: 14; -fx-cursor: hand;");
        DropShadow s = new DropShadow(); s.setColor(Color.web(GREEN, 0.30)); s.setRadius(12); s.setOffsetY(4); btn.setEffect(s);
        btn.setOnMouseEntered(e -> { btn.setStyle("-fx-background-color: " + GREEN_MID + "; -fx-text-fill: white; -fx-background-radius: 14; -fx-cursor: hand;"); DropShadow s2 = new DropShadow(); s2.setColor(Color.web(GREEN, 0.45)); s2.setRadius(16); s2.setOffsetY(5); btn.setEffect(s2); });
        btn.setOnMouseExited(e -> { btn.setStyle("-fx-background-color: " + GREEN + "; -fx-text-fill: white; -fx-background-radius: 14; -fx-cursor: hand;"); btn.setEffect(s); });
        return btn;
    }

    private Region spacer(int h) { Region r = new Region(); r.setPrefHeight(h); return r; }

    // ── Logique ──────────────────────────────────────────────────

    public void onConnect() {
        String u = usernameField != null ? usernameField.getText().trim() : "";
        String p = passwordField != null ? passwordField.getText() : "";
        if (u.isEmpty()) { showError("Entrez votre nom d'utilisateur !"); return; }
        if (p.isEmpty()) { showError("Entrez votre mot de passe !"); return; }
        if (connectButton != null) { connectButton.setDisable(true); connectButton.setText("Connexion..."); }
        statusLabel.setText("");
        if (client != null) { client.connect(); client.login(u, p); }
    }

    private void onRegister(String confirm) {
        String u = usernameField != null ? usernameField.getText().trim() : "";
        String p = passwordField != null ? passwordField.getText() : "";
        if (u.isEmpty()) { showError("Entrez un nom d'utilisateur !"); return; }
        if (u.length() < 3) { showError("Minimum 3 caracteres !"); return; }
        if (p.isEmpty()) { showError("Entrez un mot de passe !"); return; }
        if (p.length() < 4) { showError("Minimum 4 caracteres !"); return; }
        if (!p.equals(confirm)) { showError("Les mots de passe ne correspondent pas !"); return; }
        // DAO : UserDAO
        if (UserDAO.usernameExists(u)) { showError("Ce nom est deja pris !"); return; }
        boolean ok = UserDAO.addUser(new User(u, p));
        if (ok) {
            showSuccess("Compte cree !  Connectez-vous.");
            new Thread(() -> { try { Thread.sleep(1500); Platform.runLater(this::buildLoginUI); } catch (Exception e) { e.printStackTrace(); } }).start();
        } else { showError("Erreur lors de la creation !"); }
    }

    public void showError(String msg) {
        Platform.runLater(() -> {
            statusLabel.setText("\u274C  " + msg);
            statusLabel.setTextFill(Color.web("#EF4444"));
            if (connectButton != null) { connectButton.setDisable(false); connectButton.setText("Se connecter"); }
        });
    }

    public void showSuccess(String msg) {
        Platform.runLater(() -> {
            statusLabel.setText("\u2713  " + msg);
            statusLabel.setTextFill(Color.web(GREEN_LT));
        });
    }

    public void openChatWindow() {
        Platform.runLater(() -> {
            try {
                ChatWindow cw = new ChatWindow(client);
                client.setChatWindow(cw);
                cw.start(new Stage());
                stage.close();
            } catch (Exception e) { System.out.println("Erreur : " + e.getMessage()); }
        });
    }

    public void close() { Platform.runLater(() -> { if (stage != null) stage.close(); }); }
    public String getUsername() { return usernameField != null ? usernameField.getText().trim() : ""; }
    public String getPassword() { return passwordField != null ? passwordField.getText() : ""; }
    public static void main(String[] args) { launch(args); }
}