package ui;

import client.Client;
import database.DatabaseManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Base64;

public class ProfileWindow extends Application {

    private Client client;
    private Stage stage;
    private ImageView photoView;
    private TextField usernameField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private byte[] selectedPhotoBytes = null; // bytes de la nouvelle photo

    public ProfileWindow(Client client) {
        this.client = client;
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("Mon Profil");

        // ══════════════════════════════════════════
        // PHOTO DE PROFIL
        // ══════════════════════════════════════════
        photoView = new ImageView();
        photoView.setFitWidth(100);
        photoView.setFitHeight(100);
        photoView.setPreserveRatio(false);

        // Charger la photo actuelle si elle existe
        String currentPhoto = client.getUser().getProfilePhoto();
        if (currentPhoto != null && !currentPhoto.isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(currentPhoto);
                photoView.setImage(new Image(
                        new java.io.ByteArrayInputStream(bytes)
                ));
            } catch (Exception e) {
                setDefaultPhoto();
            }
        } else {
            setDefaultPhoto();
        }

        // Rendre la photo circulaire
        Circle clip = new Circle(50, 50, 50);
        photoView.setClip(clip);

        Button changePhotoButton = new Button("📷 Changer la photo");
        changePhotoButton.setStyle(
                "-fx-background-color: #075E54;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 6 12;"
        );
        changePhotoButton.setOnAction(e -> choosePhoto());

        VBox photoSection = new VBox(10);
        photoSection.setAlignment(Pos.CENTER);
        photoSection.getChildren().addAll(photoView, changePhotoButton);

        // ══════════════════════════════════════════
        // FORMULAIRE
        // ══════════════════════════════════════════
        Label usernameLabel = new Label("Nom d'utilisateur :");
        usernameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        usernameField = new TextField(client.getUser().getUsername());
        usernameField.setPrefHeight(40);
        usernameField.setStyle(
                "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 5 10;"
        );

        Label passwordLabel = new Label("Nouveau mot de passe :");
        passwordLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        passwordField = new PasswordField();
        passwordField.setPromptText("Laisser vide pour ne pas changer");
        passwordField.setPrefHeight(40);
        passwordField.setStyle(
                "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 5 10;"
        );

        Label confirmLabel = new Label("Confirmer le mot de passe :");
        confirmLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Répétez le mot de passe");
        confirmPasswordField.setPrefHeight(40);
        confirmPasswordField.setStyle(
                "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 5 10;"
        );

        // ══════════════════════════════════════════
        // BOUTON SAUVEGARDER
        // ══════════════════════════════════════════
        Button saveButton = new Button("💾 Sauvegarder");
        saveButton.setPrefWidth(200);
        saveButton.setPrefHeight(45);
        saveButton.setStyle(
                "-fx-background-color: #25D366;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 10;" +
                        "-fx-cursor: hand;"
        );
        saveButton.setOnAction(e -> saveProfile());

        // ══════════════════════════════════════════
        // ASSEMBLAGE
        // ══════════════════════════════════════════
        VBox form = new VBox(10);
        form.setPadding(new Insets(20));
        form.getChildren().addAll(
                photoSection,
                new Separator(),
                usernameLabel, usernameField,
                passwordLabel, passwordField,
                confirmLabel, confirmPasswordField,
                new Separator(),
                saveButton
        );
        form.setAlignment(Pos.CENTER_LEFT);

        // Titre en haut
        Label title = new Label("👤 Mon Profil");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);
        title.setPadding(new Insets(15));

        VBox header = new VBox(title);
        header.setStyle("-fx-background-color: #075E54;");

        VBox root = new VBox();
        root.getChildren().addAll(header, form);

        Scene scene = new Scene(root, 380, 530);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private void choosePhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir une photo de profil");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
        );
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                selectedPhotoBytes = java.nio.file.Files.readAllBytes(file.toPath());
                Image img = new Image(new java.io.ByteArrayInputStream(selectedPhotoBytes));
                photoView.setImage(img);

                // Remettre le clip circulaire
                Circle clip = new Circle(50, 50, 50);
                photoView.setClip(clip);

            } catch (Exception e) {
                showAlert("Impossible de charger l'image !");
            }
        }
    }

    private void saveProfile() {
        String newUsername = usernameField.getText().trim();
        String newPassword = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validations
        if (newUsername.isEmpty()) {
            showAlert("Le nom d'utilisateur ne peut pas être vide !");
            return;
        }

        if (!newPassword.isEmpty() && !newPassword.equals(confirmPassword)) {
            showAlert("Les mots de passe ne correspondent pas !");
            return;
        }

        String userId = client.getUser().getId();
        String finalPassword = newPassword.isEmpty()
                ? client.getUser().getPassword()
                : newPassword;

        // Sauvegarder en BD
        boolean ok = DatabaseManager.updateUser(userId, newUsername, finalPassword);

        // Sauvegarder la photo si changée
        if (selectedPhotoBytes != null) {
            String base64 = Base64.getEncoder().encodeToString(selectedPhotoBytes);
            DatabaseManager.updateProfilePhoto(userId, base64);
            client.getUser().setProfilePhoto(base64);
        }

        if (ok) {
            // Mettre à jour l'objet User local
            client.getUser().setUsername(newUsername);
            client.getUser().setPassword(finalPassword);

            // Notifier le serveur du changement
            String photoBase64 = selectedPhotoBytes != null
                    ? java.util.Base64.getEncoder().encodeToString(selectedPhotoBytes)
                    : (client.getUser().getProfilePhoto() != null
                    ? client.getUser().getProfilePhoto() : "");

            client.sendCommand("UPDATE_PROFILE:" + userId + ":" + newUsername + ":" + photoBase64);

            showSuccess("Profil mis à jour avec succès !");
            stage.close();
        } else {
            showAlert("Erreur lors de la sauvegarde !");
        }
    }

    private void setDefaultPhoto() {
        // Photo par défaut : cercle avec initiale
        photoView.setImage(null);
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Attention");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showSuccess(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Succès");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}