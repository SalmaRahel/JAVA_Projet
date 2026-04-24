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

public class ChatWindow extends Application {

    // ============================================================
    // ATTRIBUTS
    // ============================================================
    private Client client;
    private User currentContact;
    private TextField inputField;
    private Button sendButton;
    private Button attachButton;
    private ListView<String> userListPanel;
    private Label contactLabel;
    private Label windowTitleLabel;
    private Stage stage;
    private ScrollPane messageScrollPane;
    private VBox messageContainer; // Contient les bulles de messages
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

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
    // MÉTHODE start()
    // ============================================================
    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        // Titre de la fenêtre avec le nom de l'utilisateur connecté
        String username = (client != null && client.getUser() != null)
                ? client.getUser().getUsername()
                : "Utilisateur";
        primaryStage.setTitle("WhatsApp — " + username);

        // ══════════════════════════════════════════
        // PANNEAU GAUCHE — Liste des contacts
        // ══════════════════════════════════════════

        // Titre avec nom de l'utilisateur connecté
        windowTitleLabel = new Label("💬 " + username);
        windowTitleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        windowTitleLabel.setTextFill(Color.WHITE);
        windowTitleLabel.setPadding(new Insets(15));

        userListPanel = new ListView<>();
        userListPanel.setPrefWidth(220);
        userListPanel.setStyle("-fx-background-color: #f0f0f0;");

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
        leftPanel.getChildren().addAll(windowTitleLabel, userListPanel);

        // ══════════════════════════════════════════
        // PANNEAU CENTRE — Zone des messages
        // ══════════════════════════════════════════

        // Barre du haut
        contactLabel = new Label("Sélectionnez un contact");
        contactLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        contactLabel.setTextFill(Color.WHITE);
        contactLabel.setPadding(new Insets(0, 0, 0, 15));

        HBox topBar = new HBox();
        topBar.setPrefHeight(60);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #075E54;");

        // Boutons d'appel
        Button audioCallButton = new Button("📞");
        audioCallButton.setPrefSize(45, 45);
        audioCallButton.setStyle(
                "-fx-background-color: #075E54;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;" +
                        "-fx-cursor: hand;"
        );
        audioCallButton.setOnAction(e -> startAudioCall());

        Button videoCallButton = new Button("📹");
        videoCallButton.setPrefSize(45, 45);
        videoCallButton.setStyle(
                "-fx-background-color: #075E54;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;" +
                        "-fx-cursor: hand;"
        );
        videoCallButton.setOnAction(e -> startVideoCall());

        HBox callButtons = new HBox(10);
        callButtons.setAlignment(Pos.CENTER_RIGHT);
        callButtons.setPadding(new Insets(0, 15, 0, 0));
        callButtons.getChildren().addAll(audioCallButton, videoCallButton);
        HBox.setHgrow(callButtons, Priority.ALWAYS);
        topBar.getChildren().addAll(contactLabel, callButtons);

        // Zone des messages avec ScrollPane
        messageContainer = new VBox(8);
        messageContainer.setPadding(new Insets(10));
        messageContainer.setStyle("-fx-background-color: #ECE5DD;");

        messageScrollPane = new ScrollPane(messageContainer);
        messageScrollPane.setFitToWidth(true);
        messageScrollPane.setStyle("-fx-background-color: #ECE5DD;");
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);

        // Barre de saisie en bas
        attachButton = new Button("📎");
        attachButton.setPrefSize(45, 45);
        attachButton.setStyle(
                "-fx-background-color: #075E54;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;"
        );
        attachButton.setOnAction(e -> onAttachFile());

        inputField = new TextField();
        inputField.setPromptText("Tapez un message...");
        inputField.setPrefHeight(45);
        inputField.setStyle(
                "-fx-border-radius: 20;" +
                        "-fx-background-radius: 20;" +
                        "-fx-padding: 8 15;" +
                        "-fx-font-size: 13px;"
        );
        inputField.setOnAction(e -> sendMessage());

        sendButton = new Button("➤");
        sendButton.setPrefSize(45, 45);
        sendButton.setStyle(
                "-fx-background-color: #25D366;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16px;" +
                        "-fx-border-radius: 22;" +
                        "-fx-background-radius: 22;" +
                        "-fx-cursor: hand;"
        );
        sendButton.setOnAction(e -> sendMessage());

        HBox inputBar = new HBox(10);
        inputBar.setPadding(new Insets(10));
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setStyle("-fx-background-color: #f0f0f0;");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputBar.getChildren().addAll(attachButton, inputField, sendButton);

        VBox centerPanel = new VBox();
        centerPanel.getChildren().addAll(topBar, messageScrollPane, inputBar);

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

        primaryStage.setOnCloseRequest(event -> {
            if (client != null) client.disconnect();
        });

        primaryStage.show();
    }

    // ============================================================
    // CRÉER UNE BULLE DE MESSAGE
    // ============================================================
    private HBox createMessageBubble(Message message) {
        boolean isSentByMe = client != null
                && client.getUser() != null
                && message.getSender().getUsername()
                .equals(client.getUser().getUsername());

        // Si c'est un fichier → bulle fichier
        if (message instanceof model.FileMessage) {
            model.FileMessage fm = (model.FileMessage) message;

            // Si c'est une image → afficher inline
            if (fm.getFileType() != null && fm.getFileType().startsWith("image/")
                    && fm.getFileData() != null && fm.getFileData().length > 0) {

                javafx.scene.image.Image img = new javafx.scene.image.Image(
                        new java.io.ByteArrayInputStream(fm.getFileData())
                );
                javafx.scene.image.ImageView imageView =
                        new javafx.scene.image.ImageView(img);
                imageView.setFitWidth(250);
                imageView.setPreserveRatio(true);

                String time = timeFormat.format(
                        message.getTimestamp() != null
                                ? message.getTimestamp() : new Date()
                );
                Label timeLabel = new Label(time + (isSentByMe ? " ✓" : ""));
                timeLabel.setFont(Font.font("Arial", 10));
                timeLabel.setTextFill(Color.GRAY);

                VBox bubbleContent = new VBox(3);
                bubbleContent.getChildren().addAll(imageView, timeLabel);
                bubbleContent.setPadding(new Insets(6));
                bubbleContent.setStyle(
                        "-fx-background-color: " + (isSentByMe ? "#DCF8C6" : "white") + ";" +
                                "-fx-background-radius: 10;"
                );

                HBox hbox = new HBox();
                hbox.setAlignment(isSentByMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                hbox.setPadding(isSentByMe
                        ? new Insets(2, 10, 2, 50)
                        : new Insets(2, 50, 2, 10));
                hbox.getChildren().add(bubbleContent);
                return hbox;
            }

            // Sinon → bulle fichier normale
            return createFileBubble(fm, isSentByMe);
        }

        // Message texte normal
        String time = timeFormat.format(
                message.getTimestamp() != null
                        ? message.getTimestamp() : new Date()
        );
        String readIcon = isSentByMe ? " ✓" : "";

        Label messageLabel = new Label(message.getContent());
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(350);
        messageLabel.setFont(Font.font("Arial", 13));

        Label timeLabel = new Label(time + readIcon);
        timeLabel.setFont(Font.font("Arial", 10));
        timeLabel.setTextFill(Color.GRAY);

        VBox bubbleContent = new VBox(3);
        bubbleContent.getChildren().addAll(messageLabel, timeLabel);
        bubbleContent.setPadding(new Insets(8, 12, 8, 12));
        bubbleContent.setMaxWidth(360);

        if (isSentByMe) {
            bubbleContent.setStyle(
                    "-fx-background-color: #DCF8C6;" +
                            "-fx-background-radius: 15 15 0 15;"
            );
            timeLabel.setAlignment(Pos.CENTER_RIGHT);
            HBox hbox = new HBox();
            hbox.setAlignment(Pos.CENTER_RIGHT);
            hbox.setPadding(new Insets(2, 10, 2, 50));
            hbox.getChildren().add(bubbleContent);
            return hbox;
        } else {
            Label senderLabel = new Label(message.getSender().getUsername());
            senderLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            senderLabel.setTextFill(Color.web("#075E54"));
            bubbleContent.getChildren().add(0, senderLabel);
            bubbleContent.setStyle(
                    "-fx-background-color: white;" +
                            "-fx-background-radius: 15 15 15 0;"
            );
            HBox hbox = new HBox();
            hbox.setAlignment(Pos.CENTER_LEFT);
            hbox.setPadding(new Insets(2, 50, 2, 10));
            hbox.getChildren().add(bubbleContent);
            return hbox;
        }
    }

    // ============================================================
    // MÉTHODES
    // ============================================================

    private void onContactSelected(String username) {
        System.out.println("💬 Contact sélectionné : " + username);

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

            // Vider les messages actuels
            messageContainer.getChildren().clear();

            // Charger l'historique depuis la BD
            if (client != null && client.getUser() != null
                    && currentContact != null) {
                List<model.Message> history =
                        database.DatabaseManager.getMessageHistory(
                                client.getUser().getId(),
                                currentContact.getId()
                        );
                for (model.Message msg : history) {
                    HBox bubble = createMessageBubble(msg);
                    messageContainer.getChildren().add(bubble);
                }
                messageScrollPane.setVvalue(1.0);
            }
        });
    }

    private void sendMessage() {
        String content = inputField.getText().trim();
        if (content.isEmpty()) return;

        if (currentContact == null) {
            showAlert("Sélectionnez d'abord un contact !");
            return;
        }

        Message message = new Message(
                content,
                client.getUser(),
                currentContact,
                MessageType.TEXT
        );

        client.sendMessage(message);
        displayMessage(message);
        clearInput();
    }

    // Afficher un message avec bulle gauche/droite
    public void displayMessage(Message message) {
        Platform.runLater(() -> {
            HBox bubble = createMessageBubble(message);
            messageContainer.getChildren().add(bubble);

            // Scroll automatique vers le bas
            messageScrollPane.setVvalue(1.0);
        });
    }

    public void updateUserList(List<User> users) {
        Platform.runLater(() -> {
            userListPanel.getItems().clear();
            for (User user : users) {
                if (client.getUser() != null &&
                        !user.getUsername().equals(
                                client.getUser().getUsername())) {
                    userListPanel.getItems().add(user.getUsername());
                }
            }
        });
    }

    public void clearInput() {
        Platform.runLater(() -> inputField.clear());
    }

    private void onAttachFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir un fichier");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg"),
                new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.docx")
        );
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            sendFile(selectedFile);
        }
    }

    public void sendFile() { onAttachFile(); }

    private void openFile(model.FileMessage fm) {
        try {
            // Sauvegarder les bytes dans un fichier temporaire
            java.io.File temp = new java.io.File(
                    System.getProperty("java.io.tmpdir"), fm.getFileName()
            );
            java.nio.file.Files.write(temp.toPath(), fm.getFileData());

            // Ouvrir avec le programme par défaut du système
            java.awt.Desktop.getDesktop().open(temp);
        } catch (java.io.IOException e) {
            showAlert("Impossible d'ouvrir le fichier !");
        }
    }

    private HBox createFileBubble(model.FileMessage fm, boolean isSent) {
        Button fileBtn = new Button("📎 " + fm.getFileName()
                + " (" + fm.getFileSizeFormatted() + ")");
        fileBtn.setStyle(
                "-fx-background-color: " + (isSent ? "#DCF8C6" : "white") + ";" +
                        "-fx-padding: 8 12;" +
                        "-fx-background-radius: 10;" +
                        "-fx-cursor: hand;"
        );
        fileBtn.setOnAction(e -> openFile(fm));

        HBox hbox = new HBox();
        hbox.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        hbox.setPadding(isSent
                ? new Insets(2, 10, 2, 50)
                : new Insets(2, 50, 2, 10));
        hbox.getChildren().add(fileBtn);
        return hbox;
    }

    private String getFileType(File file) {
        try {
            String type = java.nio.file.Files.probeContentType(file.toPath());
            return type != null ? type : "application/octet-stream";
        } catch (java.io.IOException e) {
            return "application/octet-stream";
        }
    }

    private void sendFile(File file) {
        if (currentContact == null) {
            showAlert("Sélectionnez d'abord un contact !");
            return;
        }

        model.FileMessage fileMessage = new model.FileMessage(
                client.getUser(),
                currentContact,
                file.getName(),
                file.length(),
                getFileType(file), // type MIME
                file.getAbsolutePath()
        );

        client.sendMessage(fileMessage); // envoyer via socket

        // Afficher la bulle côté envoyeur
        Platform.runLater(() -> {
            HBox bubble = createFileBubble(fileMessage, true);
            messageContainer.getChildren().add(bubble);
            messageScrollPane.setVvalue(1.0);
        });
    }

    public void displayFile(model.FileMessage fm) {
        Platform.runLater(() -> {
            HBox bubble = createFileBubble(fm, false);
            messageContainer.getChildren().add(bubble);
            messageScrollPane.setVvalue(1.0);
        });
    }

    private void startAudioCall() {
        if (currentContact == null) {
            showAlert("Sélectionnez d'abord un contact !");
            return;
        }
        client.startCall(currentContact, model.CallType.AUDIO);
        openCallWindow();
    }

    private void startVideoCall() {
        if (currentContact == null) {
            showAlert("Sélectionnez d'abord un contact !");
            return;
        }
        client.startCall(currentContact, model.CallType.VIDEO);
        openCallWindow();
    }

    private void openCallWindow() {
        Platform.runLater(() -> {
            try {
                call.Call currentCall = client.getCurrentCall();
                if (currentCall == null) return;

                // L'appelant voit "En attente de réponse..."
                // On crée sa fenêtre avec un statut spécial
                CallWindow callWindow = new CallWindow(client, currentCall);
                callWindow.setCallerMode(true); // ← mode appelant
                client.setCallWindow(callWindow);
                callWindow.start(new Stage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Attention");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void displayGroupMessages() {
        System.out.println("Affichage messages groupe...");
    }

    public void sendGroupMessage() {
        System.out.println("Envoi message groupe...");
    }

    public void showGroupMembers() {
        System.out.println("Affichage membres groupe...");
    }

    public static void main(String[] args) {
        launch(args);
    }
}