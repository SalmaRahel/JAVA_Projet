package ui;

import client.Client;
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

// LoginWindow est la première fenêtre affichée quand
// l'utilisateur lance l'application.
//
// Elle contient :
//   - Un champ pour le nom d'utilisateur
//   - Un champ pour le mot de passe
//   - Un bouton "Se connecter"
//   - Un label pour afficher les erreurs
//
// Quand l'utilisateur clique sur "Se connecter" :
//   1. On récupère le username et le password
//   2. On appelle client.login(username, password)
//   3. Le serveur vérifie les informations
//   4. Si OK → on ouvre ChatWindow
//   5. Si erreur → on affiche un message d'erreur

public class LoginWindow extends Application {

    // ============================================================
    // ATTRIBUTS
    // ============================================================

    // Référence au Client pour pouvoir appeler ses méthodes
    private Client client;

    // Les composants de l'interface graphique
    private TextField usernameField;
    private PasswordField passwordField;
    private Button connectButton;
    private Label statusLabel;

    // La fenêtre principale JavaFX
    private Stage stage;

    // ============================================================
    // CONSTRUCTEURS
    // ============================================================

    // Constructeur par défaut (requis par JavaFX Application)
    public LoginWindow() {
        this.client = null;
    }

    // Constructeur avec Client
    public LoginWindow(Client client) {
        this.client = client;
    }

    // ============================================================
    // MÉTHODE start() — Point d'entrée JavaFX
    // ============================================================

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("WhatsApp — Connexion");

        // ── Titre de l'application ──
        Label titleLabel = new Label("💬 WhatsApp");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.web("#25D366")); // Vert WhatsApp

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

        // ── Bouton Connecter ──
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

        // ── Label de statut (erreurs) ──
        statusLabel = new Label("");
        statusLabel.setTextFill(Color.RED);
        statusLabel.setFont(Font.font("Arial", 12));

        // ── Action du bouton Connecter ──
        connectButton.setOnAction(event -> onConnect());

        // Permettre de se connecter en appuyant sur Enter
        passwordField.setOnAction(event -> onConnect());

        // ── Mise en page (Layout) ──
        VBox layout = new VBox(15); // 15 = espacement entre les éléments
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
                statusLabel
        );

        // ── Création de la scène ──
        Scene scene = new Scene(layout, 380, 450);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    // ============================================================
    // MÉTHODES
    // ============================================================

    // Appelée quand l'utilisateur clique sur "Se connecter"
    public void onConnect() {
        String username = getUsername();
        String password = getPassword();

        // Vérification locale : les champs ne doivent pas être vides
        if (username.isEmpty()) {
            showError("Veuillez entrer votre nom d'utilisateur !");
            return;
        }

        if (password.isEmpty()) {
            showError("Veuillez entrer votre mot de passe !");
            return;
        }

        // Désactiver le bouton pendant la connexion
        connectButton.setDisable(true);
        connectButton.setText("Connexion...");
        statusLabel.setText("");

        // Envoyer le login au serveur via Client
        if (client != null) {
            client.connect();
            client.login(username, password);
        }
    }

    // Afficher un message d'erreur dans la fenêtre
    // Appelée par Client quand le login échoue
    public void showError(String message) {
        // Platform.runLater() est nécessaire car cette méthode
        // peut être appelée depuis un Thread différent (le Thread Client)
        // et JavaFX ne permet de modifier l'interface que depuis
        // le Thread principal (JavaFX Application Thread)
        Platform.runLater(() -> {
            statusLabel.setText("❌ " + message);
            statusLabel.setTextFill(Color.RED);
            connectButton.setDisable(false);
            connectButton.setText("Se connecter");
        });
    }

    // Afficher un message de succès
    public void showSuccess(String message) {
        Platform.runLater(() -> {
            statusLabel.setText("✅ " + message);
            statusLabel.setTextFill(Color.GREEN);
        });
    }

    // Ouvrir la fenêtre de chat et fermer la fenêtre de login
    public void openChatWindow() {
        Platform.runLater(() -> {
            try {
                // Créer et afficher ChatWindow
                ChatWindow chatWindow = new ChatWindow(client);
                client.setChatWindow(chatWindow);
                chatWindow.start(new Stage());

                // Fermer la fenêtre de login
                stage.close();

            } catch (Exception e) {
                System.out.println("❌ Erreur ouverture ChatWindow : "
                        + e.getMessage());
            }
        });
    }

    // Fermer la fenêtre de login
    public void close() {
        Platform.runLater(() -> {
            if (stage != null) stage.close();
        });
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public String getUsername() {
        return usernameField != null
                ? usernameField.getText().trim()
                : "";
    }

    public String getPassword() {
        return passwordField != null
                ? passwordField.getText()
                : "";
    }

    // ============================================================
    // MÉTHODE MAIN — Pour tester LoginWindow seule
    // ============================================================

    public static void main(String[] args) {
        launch(args);
    }
}