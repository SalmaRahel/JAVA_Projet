package ui;

import client.Client;
import model.Message;
import model.MessageType;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

// ChatWindow est la fenêtre principale de l'application.
// Elle est affichée après une connexion réussie.
//
// Elle contient :
//   - À gauche : la liste des utilisateurs connectés
//   - Au centre : la zone des messages
//   - En bas : le champ de saisie + boutons

public class ChatWindow extends Application {

    // ============================================================
    // ATTRIBUTS
    // ============================================================

    // Référence au Client
    private Client client;

    // L'utilisateur actuellement sélectionné pour discuter
    private User currentContact;

    // Composants de l'interface
    private TextArea messageArea;
    private TextField inputField;
    private Button sendButton;
    private Button attachButton;
    private ListView<String> userListPanel;
    private Label contactLabel;
    private Stage stage;

    // Format de l'heure pour afficher les messages
    private SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm");

    // ============================================================
    // CONSTRUCTEURS
    // ============================================================

    public ChatWindow() {
        this.client = null;
    }

    public ChatWindow(Client client) {
        this.client = client;
    }

    // ============================================================
    // MÉTHODE start() — Construction de l'interface
    // ============================================================

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("WhatsApp — Chat");

        // ══════════════════════════════════════════
        // PANNEAU GAUCHE — Liste des utilisateurs
        // ══════════════════════════════════════════

        Label usersTitle = new Label("💬 Contacts");
        usersTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        usersTitle.setTextFill(Color.WHITE);
        usersTitle.setPadding(new Insets(15));

        userListPanel = new ListView<>();
        userListPanel.setPrefWidth(220);
        userListPanel.setStyle(
                "-fx-background-color: #f0f0f0;"
        );

        // Quand on clique sur un utilisateur dans la liste
        userListPanel.setOnMouseClicked(event -> {
            String selectedUsername =
                    userListPanel.getSelectionModel().getSelectedItem();
            if (selectedUsername != null) {
                onContactSelected(selectedUsername);
            }
        });

        VBox leftPanel = new VBox();
        leftPanel.setPrefWidth(220);
        leftPanel.setStyle("-fx-background-color: #075E54;");

        VBox.setVgrow(userListPanel, Priority.ALWAYS);
        leftPanel.getChildren().addAll(usersTitle, userListPanel);

        // ══════════════════════════════════════════
        // PANNEAU CENTRE — Zone des messages
        // ══════════════════════════════════════════

        // Barre du haut avec le nom du contact
        contactLabel = new Label("Sélectionnez un contact");
        contactLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        contactLabel.setTextFill(Color.WHITE);
        contactLabel.setPadding(new Insets(0, 0, 0, 15));

        HBox topBar = new HBox();
        topBar.setPrefHeight(60);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #075E54;");
        topBar.getChildren().add(contactLabel);

        // Zone d'affichage des messages
        messageArea = new TextArea();
        messageArea.setEditable(false); // L'utilisateur ne peut pas écrire ici
        messageArea.setWrapText(true);  // Le texte revient à la ligne
        messageArea.setStyle(
                "-fx-background-color: #ECE5DD;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 10;"
        );

        // ── Zone de saisie en bas ──

        // Bouton pièce jointe 📎
        attachButton = new Button("📎");
        attachButton.setPrefHeight(45);
        attachButton.setPrefWidth(45);
        attachButton.setStyle(
                "-fx-background-color: #075E54;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );
        attachButton.setOnAction(event -> onAttachFile());

        // Champ de saisie du message
        inputField = new TextField();
        inputField.setPromptText("Tapez un message...");
        inputField.setPrefHeight(45);
        inputField.setStyle(
                "-fx-border-radius: 20;" +
                        "-fx-background-radius: 20;" +
                        "-fx-padding: 8 15;" +
                        "-fx-font-size: 13px;"
        );

        // Envoyer avec la touche Enter
        inputField.setOnAction(event -> sendMessage());

        // Bouton Envoyer
        sendButton = new Button("➤");
        sendButton.setPrefHeight(45);
        sendButton.setPrefWidth(45);
        sendButton.setStyle(
                "-fx-background-color: #25D366;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;" +
                        "-fx-border-radius: 22;" +
                        "-fx-background-radius: 22;" +
                        "-fx-cursor: hand;"
        );
        sendButton.setOnAction(event -> sendMessage());

        // Bouton Appel Audio 📞
        Button audioCallButton = new Button("📞");
        audioCallButton.setPrefHeight(45);
        audioCallButton.setPrefWidth(45);
        audioCallButton.setStyle(
                "-fx-background-color: #075E54;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );
        audioCallButton.setOnAction(event -> startAudioCall());

        // Bouton Appel Vidéo 📹
        Button videoCallButton = new Button("📹");
        videoCallButton.setPrefHeight(45);
        videoCallButton.setPrefWidth(45);
        videoCallButton.setStyle(
                "-fx-background-color: #075E54;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );
        videoCallButton.setOnAction(event -> startVideoCall());

        // Barre du haut à droite (appels)
        HBox callButtons = new HBox(10);
        callButtons.setAlignment(Pos.CENTER_RIGHT);
        callButtons.setPadding(new Insets(0, 15, 0, 0));
        callButtons.getChildren().addAll(
                audioCallButton, videoCallButton
        );
        HBox.setHgrow(callButtons, Priority.ALWAYS);
        topBar.getChildren().add(callButtons);

        // Barre de saisie en bas
        HBox inputBar = new HBox(10);
        inputBar.setPadding(new Insets(10));
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setStyle("-fx-background-color: #f0f0f0;");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputBar.getChildren().addAll(
                attachButton, inputField, sendButton
        );

        // Assemblage du panneau centre
        VBox centerPanel = new VBox();
        VBox.setVgrow(messageArea, Priority.ALWAYS);
        centerPanel.getChildren().addAll(
                topBar, messageArea, inputBar
        );

        // ══════════════════════════════════════════
        // ASSEMBLAGE FINAL
        // ══════════════════════════════════════════

        HBox mainLayout = new HBox();
        HBox.setHgrow(centerPanel, Priority.ALWAYS);
        mainLayout.getChildren().addAll(leftPanel, centerPanel);

        Scene scene = new Scene(mainLayout, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(700);
        primaryStage.setMinHeight(500);

        // Quand on ferme la fenêtre → déconnecter du serveur
        primaryStage.setOnCloseRequest(event -> {
            if (client != null) {
                client.disconnect();
            }
        });

        primaryStage.show();
    }

    // ============================================================
    // MÉTHODES
    // ============================================================

    // Appelée quand on clique sur un contact dans la liste
    private void onContactSelected(String username) {
        System.out.println("💬 Contact sélectionné : " + username);

        // Chercher l'utilisateur dans la liste connectés
        if (client != null && client.getConnectedUsers() != null) {
            for (User user : client.getConnectedUsers()) {
                if (user.getUsername().equals(username)) {
                    currentContact = user;
                    break;
                }
            }
        }

        Platform.runLater(() -> {
            contactLabel.setText("💬 " + username);
        });
    }

    // Envoyer un message texte
    private void sendMessage() {
        String content = inputField.getText().trim();

        // Ne pas envoyer si le champ est vide
        if (content.isEmpty()) return;

        // Ne pas envoyer si aucun contact sélectionné
        if (currentContact == null) {
            showAlert("Sélectionnez d'abord un contact !");
            return;
        }

        // Créer l'objet Message
        Message message = new Message(
                content,
                client.getUser(),
                currentContact,
                MessageType.TEXT
        );

        // Envoyer via Client
        client.sendMessage(message);

        // Afficher le message dans notre propre fenêtre
        displayMessage(message);

        // Vider le champ de saisie
        clearInput();
    }

    // Afficher un message dans la zone de chat
    // Appelée pour les messages envoyés ET reçus
    public void displayMessage(Message message) {
        Platform.runLater(() -> {
            String time = timeFormat.format(
                    message.getTimestamp() != null
                            ? message.getTimestamp()
                            : new Date()
            );

            String sender = message.getSender().getUsername();
            String content = message.getContent();

            // Formater l'affichage
            String formatted = "[" + time + "] "
                    + sender + " : " + content + "\n";

            messageArea.appendText(formatted);
        });
    }

    // Mettre à jour la liste des utilisateurs connectés
    // Appelée par Client quand le serveur envoie la liste mise à jour
    public void updateUserList(List<User> users) {
        Platform.runLater(() -> {
            userListPanel.getItems().clear();
            for (User user : users) {
                // Ne pas afficher soi-même dans la liste
                if (client.getUser() != null &&
                        !user.getUsername().equals(
                                client.getUser().getUsername())) {
                    userListPanel.getItems().add(user.getUsername());
                }
            }
        });
    }

    // Vider le champ de saisie
    public void clearInput() {
        Platform.runLater(() -> {
            inputField.clear();
        });
    }

    // Joindre un fichier (bouton 📎)
    public void onAttachFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir un fichier");

        // Filtres de fichiers acceptés
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        "Tous les fichiers", "*.*"),
                new FileChooser.ExtensionFilter(
                        "Images", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter(
                        "Documents", "*.pdf", "*.docx", "*.txt")
        );

        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            sendFile(selectedFile);
        }
    }

    // Envoyer un fichier sélectionné
    public void sendFile() {
        onAttachFile();
    }

    // Envoyer un fichier spécifique
    private void sendFile(File file) {
        if (currentContact == null) {
            showAlert("Sélectionnez d'abord un contact !");
            return;
        }

        System.out.println("📎 Fichier sélectionné : "
                + file.getName());

        // Afficher dans la zone de chat
        Platform.runLater(() -> {
            messageArea.appendText(
                    "[" + timeFormat.format(new Date()) + "] "
                            + client.getUser().getUsername()
                            + " : 📎 " + file.getName() + "\n"
            );
        });
    }

    // Afficher le fichier reçu
    public void displayFile() {
        System.out.println("📎 Affichage fichier reçu !");
    }

    // Démarrer un appel audio
    private void startAudioCall() {
        if (currentContact == null) {
            showAlert("Sélectionnez d'abord un contact !");
            return;
        }

        System.out.println("📞 Appel audio vers : "
                + currentContact.getUsername());

        client.startCall(currentContact, model.CallType.AUDIO);

        // Ouvrir CallWindow
        openCallWindow();
    }

    // Démarrer un appel vidéo
    private void startVideoCall() {
        if (currentContact == null) {
            showAlert("Sélectionnez d'abord un contact !");
            return;
        }

        System.out.println("📹 Appel vidéo vers : "
                + currentContact.getUsername());

        client.startCall(currentContact, model.CallType.VIDEO);

        // Ouvrir CallWindow
        openCallWindow();
    }

    // Ouvrir la fenêtre d'appel
    private void openCallWindow() {
        Platform.runLater(() -> {
            try {
                CallWindow callWindow = new CallWindow(
                        client, client.getCurrentCall()
                );
                client.setCallWindow(callWindow);
                callWindow.start(new Stage());
            } catch (Exception e) {
                System.out.println("❌ Erreur CallWindow : "
                        + e.getMessage());
            }
        });
    }

    // Afficher une alerte simple
    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Attention");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Afficher les messages de groupe (Version 2)
    public void displayGroupMessages() {
        System.out.println("Affichage messages groupe...");
    }

    // Envoyer un message de groupe (Version 2)
    public void sendGroupMessage() {
        System.out.println("Envoi message groupe...");
    }

    // Afficher les membres du groupe (Version 2)
    public void showGroupMembers() {
        System.out.println("Affichage membres groupe...");
    }

    // ============================================================
    // MÉTHODE MAIN — Pour tester ChatWindow seule
    // ============================================================

    public static void main(String[] args) {
        launch(args);
    }
}