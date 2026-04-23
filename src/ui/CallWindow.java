package ui;

import call.AudioCall;
import call.Call;
import call.VideoCall;
import client.Client;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

// CallWindow est la fenêtre qui s'affiche pendant un appel
// audio ou vidéo.
//
// Elle contient :
//   - La zone vidéo (pour les appels vidéo)
//   - Le nom de l'interlocuteur
//   - Les boutons : Accepter, Refuser, Raccrocher,
//                   Couper micro, Couper caméra
//
// Elle gère deux situations :
//   1. Appel SORTANT : on a appelé quelqu'un → on attend sa réponse
//   2. Appel ENTRANT : quelqu'un nous appelle → on peut accepter/refuser

public class CallWindow extends Application {

    // ============================================================
    // ATTRIBUTS
    // ============================================================
    private boolean isCallEnded = false;
    // Référence au Client
    private Client client;

    // L'appel en cours (AudioCall ou VideoCall)
    private Call call;

    // La fenêtre principale
    private Stage stage;

    // Composants de l'interface
    private Label statusLabel;      // "Appel en cours...", "Appel entrant..."
    private Label contactLabel;     // Nom de l'interlocuteur
    private Button acceptButton;    // Accepter l'appel
    private Button refuseButton;    // Refuser l'appel
    private Button endCallButton;   // Raccrocher
    private Button muteButton;      // Couper/activer le micro
    private Button camButton;       // Couper/activer la caméra
    private ImageView videoView;    // Affichage de la vidéo

    // État du micro et de la caméra
    private boolean isMuted = false;
    private boolean isCamOff = false;
    private boolean isCallerMode = false;
    // ============================================================
    // CONSTRUCTEURS
    // ============================================================

    // Constructeur par défaut (requis par JavaFX)
    public CallWindow() {
        this.client = null;
        this.call = null;
    }

    // Constructeur avec Client et Call
    public CallWindow(Client client, Call call) {
        this.client = client;
        this.call = call;
    }



    // ============================================================
    // MÉTHODE start() — Construction de l'interface
    // ============================================================

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        // Titre selon le type d'appel
        if (call instanceof VideoCall) {
            primaryStage.setTitle("📹 Appel Vidéo");
        } else {
            primaryStage.setTitle("📞 Appel Audio");
        }

        // ── Zone vidéo (en haut) ──
        videoView = new ImageView();
        videoView.setFitWidth(500);
        videoView.setFitHeight(300);
        videoView.setPreserveRatio(true);

        StackPane videoArea = new StackPane();
        videoArea.setPrefSize(500, 300);
        videoArea.setStyle("-fx-background-color: #1a1a2e;");

        // Icône au centre si pas de vidéo
        Label videoIcon = new Label("📹");
        videoIcon.setFont(Font.font("Arial", 60));

        videoArea.getChildren().addAll(videoIcon, videoView);

        // ── Infos de l'appel ──
        String contactName = "Inconnu";
        if (call != null) {
            // Afficher le nom de l'interlocuteur
            if (client != null && client.getUser() != null) {
                if (call.getCaller().getUsername().equals(
                        client.getUser().getUsername())) {
                    // On est l'appelant → afficher le nom de l'appelé
                    contactName = call.getCalled().getUsername();
                } else {
                    // On est l'appelé → afficher le nom de l'appelant
                    contactName = call.getCaller().getUsername();
                }
            }
        }

        contactLabel = new Label("👤 " + contactName);
        contactLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        contactLabel.setTextFill(Color.WHITE);

        statusLabel = new Label("⏳ Appel en cours...");
        statusLabel.setFont(Font.font("Arial", 14));
        statusLabel.setTextFill(Color.LIGHTGRAY);

        VBox infoBox = new VBox(8);
        infoBox.setAlignment(Pos.CENTER);
        infoBox.getChildren().addAll(contactLabel, statusLabel);

        // ── Boutons d'action ──

        // Bouton Accepter (vert) — visible seulement pour appel entrant
        acceptButton = new Button("✅ Accepter");
        acceptButton.setPrefSize(130, 50);
        acceptButton.setStyle(
                "-fx-background-color: #25D366;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-radius: 25;" +
                        "-fx-background-radius: 25;" +
                        "-fx-cursor: hand;"
        );
        acceptButton.setOnAction(event -> onAcceptCall());

        // Bouton Refuser (rouge) — visible seulement pour appel entrant
        refuseButton = new Button("❌ Refuser");
        refuseButton.setPrefSize(130, 50);
        refuseButton.setStyle(
                "-fx-background-color: #FF3B30;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-radius: 25;" +
                        "-fx-background-radius: 25;" +
                        "-fx-cursor: hand;"
        );
        refuseButton.setOnAction(event -> onRefuseCall());

        // Bouton Raccrocher (rouge) — visible pendant l'appel
        endCallButton = new Button("🔴 Raccrocher");
        endCallButton.setPrefSize(160, 50);
        endCallButton.setStyle(
                "-fx-background-color: #FF3B30;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-radius: 25;" +
                        "-fx-background-radius: 25;" +
                        "-fx-cursor: hand;"
        );
        endCallButton.setOnAction(event -> onEndCall());

        // Bouton Micro
        muteButton = new Button("🎙️ Micro");
        muteButton.setPrefSize(120, 45);
        muteButton.setStyle(
                "-fx-background-color: #444444;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 13px;" +
                        "-fx-border-radius: 22;" +
                        "-fx-background-radius: 22;" +
                        "-fx-cursor: hand;"
        );
        muteButton.setOnAction(event -> onToggleMute());

        // Bouton Caméra (seulement pour VideoCall)
        camButton = new Button("📷 Caméra");
        camButton.setPrefSize(120, 45);
        camButton.setStyle(
                "-fx-background-color: #444444;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 13px;" +
                        "-fx-border-radius: 22;" +
                        "-fx-background-radius: 22;" +
                        "-fx-cursor: hand;"
        );
        camButton.setOnAction(event -> onToggleCam());

        // Cacher le bouton caméra si c'est un appel audio
        if (call instanceof AudioCall) {
            camButton.setVisible(false);
        }

        // ── Ligne de boutons principaux ──
        HBox mainButtons = new HBox(20);
        mainButtons.setAlignment(Pos.CENTER);
        mainButtons.getChildren().addAll(
                acceptButton, refuseButton, endCallButton
        );

        // ── Ligne de boutons secondaires ──
        HBox secondaryButtons = new HBox(15);
        secondaryButtons.setAlignment(Pos.CENTER);
        secondaryButtons.getChildren().addAll(muteButton, camButton);

        // ── Assemblage final ──
        VBox mainLayout = new VBox(20);
        mainLayout.setPadding(new Insets(20));
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setStyle("-fx-background-color: #2d2d2d;");
        mainLayout.getChildren().addAll(
                videoArea,
                infoBox,
                mainButtons,
                secondaryButtons
        );

        Scene scene = new Scene(mainLayout, 550, 550);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);

        // Fermer proprement quand on ferme la fenêtre
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            onEndCall();
        });

        primaryStage.show();

        // Afficher l'interface selon l'état de l'appel
        showIncoming();
    }

    // ============================================================
    // MÉTHODES D'ACTION
    // ============================================================

    // Afficher l'interface selon l'état de l'appel
    // Appelée au démarrage et quand l'état change
    public void showIncoming() {
        Platform.runLater(() -> {
            if (call == null) return;

            switch (call.getStatus()) {
                case PENDING:
                    if (isCallerMode) {
                        // On est l'appelant → on attend la réponse
                        statusLabel.setText("⏳ En attente de réponse...");
                        acceptButton.setVisible(false);
                        refuseButton.setVisible(false);
                        endCallButton.setVisible(true);
                        muteButton.setVisible(false);
                        camButton.setVisible(false);
                    } else {
                        // On est le receiver → afficher Accepter/Refuser
                        statusLabel.setText("📞 Appel entrant...");
                        acceptButton.setVisible(true);
                        refuseButton.setVisible(true);
                        endCallButton.setVisible(false);
                        muteButton.setVisible(false);
                        camButton.setVisible(false);
                    }
                    break;

                case ACTIVE:
                    // Appel actif → montrer Raccrocher + Micro + Cam
                    statusLabel.setText("🟢 Appel en cours...");
                    acceptButton.setVisible(false);
                    refuseButton.setVisible(false);
                    endCallButton.setVisible(true);
                    muteButton.setVisible(true);
                    camButton.setVisible(call instanceof VideoCall);
                    break;

                case ENDED:
                case REFUSED:
                    // Appel terminé → fermer la fenêtre
                    statusLabel.setText("🔴 Appel terminé !");
                    if (!isCallEnded) { // ← ne pas re-envoyer si on a déjà raccroché
                        isCallEnded = true;
                        closeWindow();
                    }
                    break;
            }
        });
    }

    // Accepter l'appel
    private void onAcceptCall() {
        if (call != null) {
            call.accept();
            System.out.println("✅ Appel accepté !");

            // Notifier l'appelant que l'appel est accepté
            if (client != null) {
                client.sendCallResponse(call);
            }

            // Si c'est un appel vidéo → démarrer la caméra
            if (call instanceof VideoCall) {
                ((VideoCall) call).captureVideo();
            }

            // Si c'est un appel audio → démarrer le micro
            if (call instanceof AudioCall) {
                ((AudioCall) call).captureAudio();
            }

            // Mettre à jour l'interface
            showIncoming();
        }
    }

    // Refuser l'appel
    private void onRefuseCall() {
        if (call != null) {
            call.refuse();
            if (client != null) {
                client.sendCallResponse(call); // ← ajouter ça
            }
        }
        closeWindow();
    }

    // Raccrocher
    public void onEndCall() {
        if (isCallEnded) return; // ← guard
        isCallEnded = true;

        if (call != null) {
            call.end();
            if (client != null) {
                client.sendCallResponse(call);
            }
        }
        closeWindow();
    }

    // Couper/activer le micro
    private void onToggleMute() {
        if (call instanceof AudioCall) {
            AudioCall audioCall = (AudioCall) call;
            if (isMuted) {
                audioCall.unmute();
                muteButton.setText("🎙️ Micro");
                muteButton.setStyle(
                        "-fx-background-color: #444444;" +
                                "-fx-text-fill: white;" +
                                "-fx-font-size: 13px;" +
                                "-fx-border-radius: 22;" +
                                "-fx-background-radius: 22;"
                );
            } else {
                audioCall.mute();
                muteButton.setText("🔇 Muet");
                muteButton.setStyle(
                        "-fx-background-color: #FF3B30;" +
                                "-fx-text-fill: white;" +
                                "-fx-font-size: 13px;" +
                                "-fx-border-radius: 22;" +
                                "-fx-background-radius: 22;"
                );
            }
            isMuted = !isMuted;
        }
    }

    // Couper/activer la caméra
    private void onToggleCam() {
        if (call instanceof VideoCall) {
            VideoCall videoCall = (VideoCall) call;
            videoCall.toggleCam();

            if (isCamOff) {
                camButton.setText("📷 Caméra");
                camButton.setStyle(
                        "-fx-background-color: #444444;" +
                                "-fx-text-fill: white;" +
                                "-fx-font-size: 13px;" +
                                "-fx-border-radius: 22;" +
                                "-fx-background-radius: 22;"
                );
            } else {
                camButton.setText("🚫 Cam off");
                camButton.setStyle(
                        "-fx-background-color: #FF3B30;" +
                                "-fx-text-fill: white;" +
                                "-fx-font-size: 13px;" +
                                "-fx-border-radius: 22;" +
                                "-fx-background-radius: 22;"
                );
            }
            isCamOff = !isCamOff;
        }
    }

    // Afficher une frame vidéo reçue
    public void showVideoFeed(Image frame) {
        Platform.runLater(() -> {
            if (videoView != null && frame != null) {
                videoView.setImage(frame);
            }
        });
    }

    // Fermer la fenêtre d'appel
    private void closeWindow() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
            }
        });
    }

    // ============================================================
    // MÉTHODE MAIN — Pour tester CallWindow seule
    // ============================================================

    public void setCall(Call call) {
        this.call = call;
    }

    public void setCallerMode(boolean isCallerMode) {
        this.isCallerMode = isCallerMode;
    }

    public static void main(String[] args) {
        launch(args);
    }
}