package ui;

import call.AudioCall;
import call.Call;
import call.VideoCall;
import client.Client;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

public class CallWindow extends Application {

    private static final String CREAM     = "#FAFAF7";
    private static final String WHITE     = "#FFFFFF";
    private static final String FOREST    = "#1A3C34";
    private static final String FOREST_MID = "#2D5A4F";
    private static final String SAGE      = "#4A7C6F";
    private static final String MINT_PALE = "#E8F2EF";
    private static final String INK       = "#1C1C1A";
    private static final String INK_LITE  = "#9A9A92";
    private static final String BORDER_W  = "#E8E8E2";
    private static final String RED       = "#DC2626";
    private static final String RED_PALE  = "#FEE2E2";

    private boolean isCallEnded = false;
    private Client client;
    private Call call;
    private Stage stage;

    private Label statusLabel;
    private Label contactLabel;
    private Button acceptButton;
    private Button refuseButton;
    private Button endCallButton;
    private Button muteButton;
    private Button camButton;

    // ── Vidéo distante (flux de l'autre personne) ──
    private ImageView videoView;

    // ── Self view (aperçu de la propre caméra) ──
    private ImageView selfVideoView;
    private StackPane selfViewPane;
    private boolean selfViewExpanded = false;

    // Thread de capture locale pour la self view
    private Thread selfCaptureThread;
    private volatile boolean selfCapturing = false;

    private boolean isMuted = false;
    private boolean isCamOff = false;
    private boolean isCallerMode = false;

    public CallWindow() { this.client = null; this.call = null; }
    public CallWindow(Client client, Call call) { this.client = client; this.call = call; }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle(call instanceof VideoCall ? "Appel Vidéo" : "Appel Audio");

        // ══════ ZONE VIDÉO DISTANTE ══════
        videoView = new ImageView();
        videoView.setFitWidth(520); videoView.setFitHeight(290); videoView.setPreserveRatio(true);

        Region videoBg = new Region();
        videoBg.setPrefSize(520, 290);
        videoBg.setStyle("-fx-background-color: linear-gradient(to bottom right, #0D2218, #1A3C34);");

        String contactName = getContactName();
        String init = contactName.isEmpty() ? "?" : contactName.substring(0, 1).toUpperCase();

        Circle o3 = new Circle(60); o3.setFill(Color.TRANSPARENT); o3.setStroke(Color.web(SAGE, 0.15)); o3.setStrokeWidth(1);
        Circle o2 = new Circle(44); o2.setFill(Color.TRANSPARENT); o2.setStroke(Color.web(SAGE, 0.20)); o2.setStrokeWidth(1.5);
        Circle o1 = new Circle(30, Color.web(FOREST_MID));
        Label initLbl = new Label(init); initLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 26)); initLbl.setTextFill(Color.WHITE);
        StackPane avatar = new StackPane(o3, o2, o1, initLbl); avatar.setPrefSize(130, 130);

        ScaleTransition pulse = new ScaleTransition(Duration.seconds(2.5), avatar);
        pulse.setFromX(1); pulse.setToX(1.05); pulse.setFromY(1); pulse.setToY(1.05);
        pulse.setAutoReverse(true); pulse.setCycleCount(Animation.INDEFINITE); pulse.play();

        // ══════ SELF VIEW (aperçu caméra locale) ══════
        // Uniquement visible pour les appels vidéo
        selfVideoView = new ImageView();
        selfVideoView.setPreserveRatio(false);

        // Fond de la self view (affiché quand pas encore de frame)
        Region selfBg = new Region();
        selfBg.setStyle("-fx-background-color: #0A1A12;");

        // Initiales de l'utilisateur courant dans la self view
        String myInit = (client != null && client.getUser() != null)
                ? client.getUser().getUsername().substring(0, 1).toUpperCase() : "?";
        Label selfInitLbl = new Label(myInit);
        selfInitLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 14));
        selfInitLbl.setTextFill(Color.web(INK_LITE));

        selfViewPane = new StackPane(selfBg, selfInitLbl, selfVideoView);
        selfViewPane.setAlignment(Pos.CENTER);

        // Taille réduite par défaut (petit cadre en bas à droite)
        setSelfViewSize(120, 90);

        // Bord arrondi + ombre
        Rectangle selfClip = new Rectangle(120, 90);
        selfClip.setArcWidth(12); selfClip.setArcHeight(12);
        selfViewPane.setClip(selfClip);

        DropShadow selfShadow = new DropShadow();
        selfShadow.setColor(Color.color(0, 0, 0, 0.5)); selfShadow.setRadius(12);
        selfViewPane.setEffect(selfShadow);

        // Label "Vous" au-dessus
        Label youLabel = new Label("Vous");
        youLabel.setFont(Font.font("Segoe UI", 9));
        youLabel.setTextFill(Color.web(INK_LITE));
        youLabel.setPadding(new Insets(2, 6, 2, 6));
        youLabel.setStyle("-fx-background-color: rgba(0,0,0,0.4); -fx-background-radius: 4;");
        StackPane.setAlignment(youLabel, Pos.TOP_LEFT);

        selfViewPane.getChildren().add(youLabel);
        selfViewPane.setStyle("-fx-cursor: hand;");
        selfViewPane.setVisible(call instanceof VideoCall);
        selfViewPane.setManaged(false); // Positionnement absolu dans le StackPane

        // Clic → agrandir / réduire la self view
        selfViewPane.setOnMouseClicked(e -> toggleSelfView(selfClip));

        // Hover effect
        selfViewPane.setOnMouseEntered(e -> {
            DropShadow hoverShadow = new DropShadow();
            hoverShadow.setColor(Color.web(SAGE, 0.6)); hoverShadow.setRadius(16);
            selfViewPane.setEffect(hoverShadow);
        });
        selfViewPane.setOnMouseExited(e -> selfViewPane.setEffect(selfShadow));

        // Zone vidéo principale avec self view superposée en bas à droite
        StackPane videoArea = new StackPane(videoBg, avatar, videoView, selfViewPane);
        videoArea.setPrefSize(520, 290);
        StackPane.setAlignment(selfViewPane, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(selfViewPane, new Insets(0, 10, 10, 0));

        // ══════ INFOS CONTACT ══════
        contactLabel = new Label(contactName.isEmpty() ? "Inconnu" : contactName);
        contactLabel.setFont(Font.font("Georgia", FontWeight.BOLD, 22));
        contactLabel.setTextFill(Color.web(INK));

        statusLabel = new Label("⏳  En attente...");
        statusLabel.setFont(Font.font("Segoe UI", 13));
        statusLabel.setTextFill(Color.web(INK_LITE));

        Region lineL = new Region(); lineL.setPrefSize(24, 1); lineL.setStyle("-fx-background-color: " + BORDER_W + ";");
        Region lineR = new Region(); lineR.setPrefSize(24, 1); lineR.setStyle("-fx-background-color: " + BORDER_W + ";");
        Label diamond = new Label("◆"); diamond.setFont(Font.font("Georgia", 8)); diamond.setTextFill(Color.web(SAGE));
        HBox deco = new HBox(6, lineL, diamond, lineR); deco.setAlignment(Pos.CENTER);

        VBox infoBox = new VBox(6, contactLabel, deco, statusLabel);
        infoBox.setAlignment(Pos.CENTER);
        infoBox.setPadding(new Insets(20, 0, 10, 0));

        // ══════ BOUTONS ══════
        acceptButton = buildCallBtn("✓  Accepter", FOREST, WHITE, 140, 50);
        acceptButton.setOnAction(e -> onAcceptCall());

        refuseButton = buildCallBtn("✕  Refuser", RED, WHITE, 140, 50);
        refuseButton.setOnAction(e -> onRefuseCall());

        endCallButton = buildCallBtn("✕  Raccrocher", RED, WHITE, 160, 50);
        endCallButton.setOnAction(e -> onEndCall());

        muteButton = buildSecondaryBtn("🎙  Micro");
        muteButton.setOnAction(e -> onToggleMute());

        camButton = buildSecondaryBtn("◉  Caméra");
        camButton.setOnAction(e -> onToggleCam());
        if (call instanceof AudioCall) camButton.setVisible(false);

        HBox mainBtns = new HBox(16, acceptButton, refuseButton, endCallButton);
        mainBtns.setAlignment(Pos.CENTER);
        HBox secBtns = new HBox(12, muteButton, camButton);
        secBtns.setAlignment(Pos.CENTER);

        // ══════ ASSEMBLAGE ══════
        VBox mainLayout = new VBox(0, videoArea, infoBox, mainBtns, secBtns);
        mainLayout.setAlignment(Pos.TOP_CENTER);
        mainLayout.setSpacing(16);
        mainLayout.setPadding(new Insets(0, 24, 28, 24));
        mainLayout.setStyle("-fx-background-color: " + CREAM + ";");

        Scene scene = new Scene(mainLayout, 568, 560);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(e -> { e.consume(); onEndCall(); });

        mainLayout.setOpacity(0);
        primaryStage.show();
        FadeTransition ft = new FadeTransition(Duration.millis(350), mainLayout);
        ft.setFromValue(0); ft.setToValue(1); ft.play();

        showIncoming();
    }

    /**
     * Démarre la capture locale pour la self view.
     * Appelé dès que l'appel devient ACTIVE et que la caméra est disponible.
     */
    private void startSelfCapture(VideoCall vc) {
        if (selfCapturing) return;
        selfCapturing = true;

        selfCaptureThread = new Thread(() -> {
            org.bytedeco.javacv.Java2DFrameConverter conv = new org.bytedeco.javacv.Java2DFrameConverter();
            try {
                while (selfCapturing && vc.getStatus() == model.CallStatus.ACTIVE) {
                    byte[] imgBytes = vc.captureFrameBytes(); // ← utilise le grabber existant
                    if (imgBytes != null && imgBytes.length > 0) {
                        javafx.scene.image.Image fxImg = new javafx.scene.image.Image(
                                new java.io.ByteArrayInputStream(imgBytes)
                        );
                        Platform.runLater(() -> {
                            if (selfVideoView != null) selfVideoView.setImage(fxImg);
                        });
                    }
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                System.out.println("Erreur self view : " + e.getMessage());
            } finally {
                selfCapturing = false;
            }
        }, "self-view-capture");
        selfCaptureThread.setDaemon(true);
        selfCaptureThread.start();
    }

    /**
     * Bascule entre taille réduite et taille étendue pour la self view.
     */
    private void toggleSelfView(Rectangle clip) {
        selfViewExpanded = !selfViewExpanded;

        if (selfViewExpanded) {
            // Mode étendu : grande taille
            setSelfViewSize(260, 195);
            clip.setWidth(260); clip.setHeight(195);
            clip.setArcWidth(16); clip.setArcHeight(16);
            // Animation
            ScaleTransition st = new ScaleTransition(Duration.millis(200), selfViewPane);
            st.setFromX(120.0 / 260.0); st.setFromY(90.0 / 195.0);
            st.setToX(1.0); st.setToY(1.0); st.play();
        } else {
            // Mode réduit
            setSelfViewSize(120, 90);
            clip.setWidth(120); clip.setHeight(90);
            clip.setArcWidth(12); clip.setArcHeight(12);
            ScaleTransition st = new ScaleTransition(Duration.millis(200), selfViewPane);
            st.setFromX(260.0 / 120.0); st.setFromY(195.0 / 90.0);
            st.setToX(1.0); st.setToY(1.0); st.play();
        }
    }

    private void setSelfViewSize(double w, double h) {
        selfViewPane.setPrefSize(w, h);
        selfViewPane.setMinSize(w, h);
        selfViewPane.setMaxSize(w, h);
        selfVideoView.setFitWidth(w);
        selfVideoView.setFitHeight(h);
    }

    private String getContactName() {
        if (call == null || client == null || client.getUser() == null) return "";
        return call.getCaller().getUsername().equals(client.getUser().getUsername())
                ? call.getCalled().getUsername()
                : call.getCaller().getUsername();
    }

    private Button buildCallBtn(String text, String bg, String fg, int w, int h) {
        Button btn = new Button(text); btn.setPrefSize(w, h);
        btn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        btn.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-background-radius: 26; -fx-cursor: hand;");
        DropShadow sh = new DropShadow(); sh.setColor(Color.web(bg, 0.4)); sh.setRadius(14); sh.setOffsetY(4); btn.setEffect(sh);
        btn.setOnMouseEntered(e -> { ScaleTransition st = new ScaleTransition(Duration.millis(100), btn); st.setToX(1.04); st.setToY(1.04); st.play(); });
        btn.setOnMouseExited(e -> { ScaleTransition st = new ScaleTransition(Duration.millis(100), btn); st.setToX(1.0); st.setToY(1.0); st.play(); });
        return btn;
    }

    private Button buildSecondaryBtn(String text) {
        Button btn = new Button(text); btn.setPrefSize(130, 44);
        btn.setFont(Font.font("Segoe UI", 12));
        btn.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; -fx-background-radius: 22; -fx-cursor: hand; -fx-border-color: rgba(74,124,111,0.2); -fx-border-radius: 22; -fx-border-width: 1;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + FOREST + "; -fx-text-fill: white; -fx-background-radius: 22; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; -fx-background-radius: 22; -fx-cursor: hand; -fx-border-color: rgba(74,124,111,0.2); -fx-border-radius: 22; -fx-border-width: 1;"));
        return btn;
    }

    // ══════ LOGIQUE ══════
    public void showIncoming() {
        Platform.runLater(() -> {
            if (call == null) return;
            switch (call.getStatus()) {
                case PENDING:
                    if (isCallerMode) {
                        statusLabel.setText("⏳  En attente de réponse...");
                        acceptButton.setVisible(false); refuseButton.setVisible(false);
                        endCallButton.setVisible(true); muteButton.setVisible(false); camButton.setVisible(false);
                    } else {
                        statusLabel.setText("📞  Appel entrant...");
                        acceptButton.setVisible(true); refuseButton.setVisible(true);
                        endCallButton.setVisible(false); muteButton.setVisible(false); camButton.setVisible(false);
                    }
                    // Cacher la self view tant que l'appel n'est pas actif
                    if (selfViewPane != null) { selfViewPane.setVisible(false); }
                    break;

                case ACTIVE:
                    statusLabel.setText("●  En communication");
                    statusLabel.setTextFill(Color.web(SAGE));
                    acceptButton.setVisible(false); refuseButton.setVisible(false);
                    endCallButton.setVisible(true); muteButton.setVisible(true);
                    camButton.setVisible(call instanceof VideoCall);

                    // Afficher la self view uniquement pour les appels vidéo
                    if (call instanceof VideoCall && selfViewPane != null) {
                        selfViewPane.setVisible(true);
                        // Démarrer la capture locale pour la self view
                        startSelfCapture((VideoCall) call);
                    }
                    break;

                case ENDED:
                case REFUSED:
                    statusLabel.setText("Appel terminé");
                    stopSelfCapture();
                    if (!isCallEnded) { isCallEnded = true; closeWindow(); }
                    break;
            }
        });
    }

    private void onAcceptCall() {
        if (call != null) {
            call.accept();
            if (client != null) client.sendCallResponse(call);
            if (call instanceof VideoCall) {
                VideoCall vc = (VideoCall) call;
                new Thread(() -> { vc.captureVideo(); client.startVideoStreaming(vc); }).start();
            }
            if (call instanceof AudioCall) {
                ((AudioCall) call).captureAudio();
                client.startAudioStreaming((AudioCall) call);
            }
            showIncoming();
        }
    }

    private void onRefuseCall() {
        if (call != null) { call.refuse(); if (client != null) client.sendCallResponse(call); }
        stopSelfCapture();
        closeWindow();
    }

    public void onEndCall() {
        if (isCallEnded) return;
        isCallEnded = true;
        stopSelfCapture();
        if (call != null) { call.end(); if (client != null) client.sendCallResponse(call); }
        closeWindow();
    }

    private void stopSelfCapture() {
        selfCapturing = false;
        if (selfCaptureThread != null) {
            selfCaptureThread.interrupt();
            selfCaptureThread = null;
        }
    }

    private void onToggleMute() {
        if (call instanceof AudioCall) {
            AudioCall ac = (AudioCall) call;
            if (isMuted) { ac.unmute(); muteButton.setText("🎙  Micro"); muteButton.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; -fx-background-radius: 22; -fx-cursor: hand; -fx-border-color: rgba(74,124,111,0.2); -fx-border-radius: 22; -fx-border-width: 1;"); }
            else { ac.mute(); muteButton.setText("🔇  Muet"); muteButton.setStyle("-fx-background-color: " + RED_PALE + "; -fx-text-fill: " + RED + "; -fx-background-radius: 22; -fx-cursor: hand; -fx-border-color: rgba(220,38,38,0.2); -fx-border-radius: 22; -fx-border-width: 1;"); }
            isMuted = !isMuted;
        }
    }

    private void onToggleCam() {
        if (call instanceof VideoCall) {
            ((VideoCall) call).toggleCam();
            if (isCamOff) {
                camButton.setText("◉  Caméra");
                camButton.setStyle("-fx-background-color: " + MINT_PALE + "; -fx-text-fill: " + SAGE + "; -fx-background-radius: 22; -fx-cursor: hand; -fx-border-color: rgba(74,124,111,0.2); -fx-border-radius: 22; -fx-border-width: 1;");
                // Reprendre la self view
                if (selfViewPane != null) { selfViewPane.setVisible(true); startSelfCapture((VideoCall) call); }
            } else {
                camButton.setText("✕  Cam off");
                camButton.setStyle("-fx-background-color: " + RED_PALE + "; -fx-text-fill: " + RED + "; -fx-background-radius: 22; -fx-cursor: hand; -fx-border-color: rgba(220,38,38,0.2); -fx-border-radius: 22; -fx-border-width: 1;");
                // Cacher la self view quand caméra coupée
                stopSelfCapture();
                if (selfViewPane != null) selfViewPane.setVisible(false);
            }
            isCamOff = !isCamOff;
        }
    }

    // Met à jour la vidéo distante (flux reçu de l'autre personne)
    public void showVideoFeed(Image frame) {
        Platform.runLater(() -> { if (videoView != null && frame != null) videoView.setImage(frame); });
    }

    private void closeWindow() { Platform.runLater(() -> { if (stage != null) stage.close(); }); }
    public void setCall(Call call) { this.call = call; }
    public void setCallerMode(boolean isCallerMode) { this.isCallerMode = isCallerMode; }
    public static void main(String[] args) { launch(args); }
}