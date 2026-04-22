package ui;

import client.Client;
import database.DatabaseManager;
import model.User;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public class LoginWindow extends Application {

    // ============================================================
    // ATTRIBUTS
    // ============================================================
    private Client client;
    private TextField usernameField;
    private PasswordField passwordField;
    private Button connectButton;
    private Button registerButton;
    private Label statusLabel;
    private Stage stage;

    // true = on affiche le formulaire de login
    // false = on affiche le formulaire d'inscription
    private boolean isLoginMode = true;

    // ============================================================
    // CONSTRUCTEURS
    // ============================================================
    public LoginWindow() {
        this.client = null;
    }

    public LoginWindow(Client client) {
        this.client = client;
    }

    // ============================================================
    // MÉTHODE start()
    // ============================================================
    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("WhatsApp — Connexion");
        buildLoginUI();
        primaryStage.show();
    }

    // ============================================================
    // CONSTRUIRE L'INTERFACE LOGIN
    // ============================================================
    private void buildLoginUI() {
        isLoginMode = true;
        stage.setTitle("WhatsApp — Connexion");

        // ── Titre ──
        Label titleLabel = new Label("💬 WhatsApp");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.web("#25D366"));

        Label subtitleLabel = new Label("Connectez-vous pour continuer");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.setTextFill(Color.GRAY);

        // ── Champ Username ──
        Label usernameLabel = new Label("Nom d'utilisateur");
        usernameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        usernameField = new TextField();
        usernameField.setPromptText("Entrez votre nom d'utilisateur");
        usernameField.setPrefHeight(40);
        usernameField.setStyle(
                "-fx-border-color: #cccccc;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 8;"
        );

        // ── Champ Password ──
        Label passwordLabel = new Label("Mot de passe");
        passwordLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        passwordField = new PasswordField();
        passwordField.setPromptText("Entrez votre mot de passe");
        passwordField.setPrefHeight(40);
        passwordField.setStyle(
                "-fx-border-color: #cccccc;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 8;"
        );

        // ── Bouton Se connecter ──
        connectButton = new Button("Se connecter");
        connectButton.setPrefHeight(45);
        connectButton.setPrefWidth(300);
        connectButton.setStyle(
                "-fx-background-color: #25D366;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );
        connectButton.setOnAction(event -> onConnect());
        passwordField.setOnAction(event -> onConnect());

        // ── Bouton S'inscrire ──
        registerButton = new Button("Pas de compte ? S'inscrire");
        registerButton.setPrefHeight(35);
        registerButton.setPrefWidth(300);
        registerButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #075E54;" +
                        "-fx-font-size: 13px;" +
                        "-fx-cursor: hand;" +
                        "-fx-border-color: #075E54;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;"
        );
        registerButton.setOnAction(event -> buildRegisterUI());

        // ── Label statut ──
        statusLabel = new Label("");
        statusLabel.setTextFill(Color.RED);
        statusLabel.setFont(Font.font("Arial", 12));

        // ── Layout ──
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(40));
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: white;");
        layout.setPrefWidth(380);

        layout.getChildren().addAll(
                titleLabel,
                subtitleLabel,
                new Separator(),
                usernameLabel,
                usernameField,
                passwordLabel,
                passwordField,
                connectButton,
                registerButton,
                statusLabel
        );

        Scene scene = new Scene(layout, 380, 500);
        stage.setScene(scene);
        stage.setResizable(false);
    }

    // ============================================================
    // CONSTRUIRE L'INTERFACE INSCRIPTION
    // ============================================================
    private void buildRegisterUI() {
        isLoginMode = false;
        stage.setTitle("WhatsApp — Inscription");

        // ── Titre ──
        Label titleLabel = new Label("💬 WhatsApp");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.web("#25D366"));

        Label subtitleLabel = new Label("Créez votre compte");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.setTextFill(Color.GRAY);

        // ── Champ Username ──
        Label usernameLabel = new Label("Nom d'utilisateur");
        usernameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        usernameField = new TextField();
        usernameField.setPromptText("Choisissez un nom d'utilisateur");
        usernameField.setPrefHeight(40);
        usernameField.setStyle(
                "-fx-border-color: #cccccc;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 8;"
        );

        // ── Champ Password ──
        Label passwordLabel = new Label("Mot de passe");
        passwordLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        passwordField = new PasswordField();
        passwordField.setPromptText("Choisissez un mot de passe");
        passwordField.setPrefHeight(40);
        passwordField.setStyle(
                "-fx-border-color: #cccccc;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 8;"
        );

        // ── Champ Confirm Password ──
        Label confirmLabel = new Label("Confirmer le mot de passe");
        confirmLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirmez votre mot de passe");
        confirmField.setPrefHeight(40);
        confirmField.setStyle(
                "-fx-border-color: #cccccc;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 8;"
        );

        // ── Bouton S'inscrire ──
        Button createButton = new Button("Créer le compte");
        createButton.setPrefHeight(45);
        createButton.setPrefWidth(300);
        createButton.setStyle(
                "-fx-background-color: #25D366;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );
        createButton.setOnAction(event -> {
            onRegister(confirmField.getText());
        });

        // ── Bouton Retour au login ──
        Button backButton = new Button("Déjà un compte ? Se connecter");
        backButton.setPrefHeight(35);
        backButton.setPrefWidth(300);
        backButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #075E54;" +
                        "-fx-font-size: 13px;" +
                        "-fx-cursor: hand;" +
                        "-fx-border-color: #075E54;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;"
        );
        backButton.setOnAction(event -> buildLoginUI());

        // ── Label statut ──
        statusLabel = new Label("");
        statusLabel.setTextFill(Color.RED);
        statusLabel.setFont(Font.font("Arial", 12));

        // ── Layout ──
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(40));
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: white;");
        layout.setPrefWidth(380);

        layout.getChildren().addAll(
                titleLabel,
                subtitleLabel,
                new Separator(),
                usernameLabel,
                usernameField,
                passwordLabel,
                passwordField,
                confirmLabel,
                confirmField,
                createButton,
                backButton,
                statusLabel
        );

        Scene scene = new Scene(layout, 380, 580);
        stage.setScene(scene);
    }

    // ============================================================
    // MÉTHODES D'ACTION
    // ============================================================

    // Connexion
    public void onConnect() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty()) {
            showError("Veuillez entrer votre nom d'utilisateur !");
            return;
        }
        if (password.isEmpty()) {
            showError("Veuillez entrer votre mot de passe !");
            return;
        }

        connectButton.setDisable(true);
        connectButton.setText("Connexion...");
        statusLabel.setText("");

        if (client != null) {
            client.connect();
            client.login(username, password);
        }
    }

    // Inscription
    private void onRegister(String confirmPassword) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Vérifications
        if (username.isEmpty()) {
            showError("Veuillez entrer un nom d'utilisateur !");
            return;
        }
        if (username.length() < 3) {
            showError("Le nom d'utilisateur doit avoir au moins 3 caractères !");
            return;
        }
        if (password.isEmpty()) {
            showError("Veuillez entrer un mot de passe !");
            return;
        }
        if (password.length() < 4) {
            showError("Le mot de passe doit avoir au moins 4 caractères !");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("Les mots de passe ne correspondent pas !");
            return;
        }

        // Vérifier si le username existe déjà
        if (DatabaseManager.usernameExists(username)) {
            showError("Ce nom d'utilisateur est déjà pris !");
            return;
        }

        // Créer le nouvel utilisateur
        User newUser = new User(username, password);
        boolean success = DatabaseManager.addUser(newUser);

        if (success) {
            showSuccess("✅ Compte créé ! Connectez-vous maintenant.");
            // Retourner au login après 2 secondes
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    Platform.runLater(() -> buildLoginUI());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            showError("Erreur lors de la création du compte !");
        }
    }

    // ============================================================
    // MÉTHODES UTILITAIRES
    // ============================================================

    public void showError(String message) {
        Platform.runLater(() -> {
            statusLabel.setText("❌ " + message);
            statusLabel.setTextFill(Color.RED);
            if (connectButton != null) {
                connectButton.setDisable(false);
                connectButton.setText("Se connecter");
            }
        });
    }

    public void showSuccess(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setTextFill(Color.GREEN);
        });
    }

    public void openChatWindow() {
        Platform.runLater(() -> {
            try {
                ChatWindow chatWindow = new ChatWindow(client);
                client.setChatWindow(chatWindow);
                chatWindow.start(new Stage());
                stage.close();
            } catch (Exception e) {
                System.out.println("❌ Erreur ouverture ChatWindow : "
                        + e.getMessage());
            }
        });
    }

    public void close() {
        Platform.runLater(() -> {
            if (stage != null) stage.close();
        });
    }

    public String getUsername() {
        return usernameField != null
                ? usernameField.getText().trim() : "";
    }

    public String getPassword() {
        return passwordField != null
                ? passwordField.getText() : "";
    }

    public static void main(String[] args) {
        launch(args);
    }
}