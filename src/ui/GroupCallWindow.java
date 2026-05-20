package ui;

import client.Client;
import database.GroupDAO;

import com.github.sarxos.webcam.Webcam;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GroupCallWindow {

    private static final String BG_DEEP    = "#080D0C";
    private static final String BG_CARD    = "#0F1A18";
    private static final String BG_SURFACE = "#162420";
    private static final String BG_HOVER   = "#1E302B";
    private static final String ACCENT     = "#00E5A0";
    private static final String ACCENT_DIM = "#00A872";
    private static final String RED_HOT    = "#FF3B5C";
    private static final String AMBER      = "#FFB547";
    private static final String GREY_1     = "#C8D4D1";
    private static final String GREY_2     = "#6B8580";
    private static final String GREY_3     = "#2E4540";

    private static final AudioFormat AUDIO_FMT  = new AudioFormat(48000f, 16, 1, true, false);
    private static final int         AUDIO_CHUNK = 1920;

    private final Client  client;
    private final String  meetingId;
    private final String  groupId;
    private final String  groupName;
    private final boolean isVideo;
    private final boolean isHost;

    private final Map<String, ParticipantTile> tiles = new LinkedHashMap<>();

    private TargetDataLine micLine;

    // FIX ECHO : une SourceDataLine par participant identifié par son username.
    // Le serveur envoie maintenant "MAUDIO:<username>:<pcm>" donc on peut router.
    private final Map<String, SourceDataLine> participantSpeakers = new HashMap<>();

    private volatile boolean micActive      = false;
    private volatile boolean micMuted       = false;
    private volatile boolean camOff         = false;
    private volatile boolean webcamActive   = false;
    private volatile boolean webcamReady    = false;
    private volatile boolean videoSendActive = false;

    private Webcam webcam;
    private final AtomicInteger micLevel = new AtomicInteger(0);

    private Stage    stage;
    private FlowPane tilesPane;
    private Label    statusLabel;
    private Label    durationLabel;
    private Label    participantCount;
    private Button   muteBtn;
    private Button   camBtn;
    private Timeline durationTimer;
    private Timeline vuTimer;
    private long     startTime;
    private final List<Rectangle> vuBars = new ArrayList<>();

    public GroupCallWindow(Client client, String meetingId, String groupId,
                           String groupName, String type, boolean isHost) {
        this.client    = client;
        this.meetingId = meetingId;
        this.groupId   = groupId;
        this.groupName = groupName;
        this.isVideo   = "VIDEO".equals(type);
        this.isHost    = isHost;
    }

    public void show() {
        client.setGroupCallWindow(this);

        stage = new Stage();
        stage.setTitle((isVideo ? "● " : "◉ ") + groupName + " — Réunion");
        stage.setMinWidth(700);
        stage.setMinHeight(520);

        VBox root = buildRoot();
        Scene scene = new Scene(root, 860, 620);
        scene.setFill(Color.web(BG_DEEP));
        stage.setScene(scene);

        root.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(350), root);
        ft.setFromValue(0); ft.setToValue(1); ft.play();

        stage.setOnCloseRequest(e -> { e.consume(); leaveCall(); });
        stage.show();

        addLocalTile();
        startAudio();
        if (isVideo) startWebcam();
        startDurationTimer();
        startVuMeter();
    }

    // ════════════════════════════════════════════════════════════
    // RÉCEPTION AUDIO — appelé depuis Client.java
    // FIX ECHO : le serveur préfixe maintenant "MAUDIO:<username>:<pcm>"
    // On parse le username et on route vers sa ligne dédiée.
    // ════════════════════════════════════════════════════════════
    public void receiveAudio(byte[] data) {
        if (data == null || data.length == 0) return;

        String senderUsername = "__unknown__";
        byte[] pcm = data;

        // Parser le header "MAUDIO:<username>:<pcmbytes>"
        try {
            byte[] prefix = "MAUDIO:".getBytes("UTF-8");
            if (data.length > prefix.length) {
                boolean hasPrefix = true;
                for (int i = 0; i < prefix.length; i++) {
                    if (data[i] != prefix[i]) { hasPrefix = false; break; }
                }
                if (hasPrefix) {
                    int colon2 = -1;
                    for (int i = prefix.length; i < Math.min(data.length, prefix.length + 60); i++) {
                        if (data[i] == ':') { colon2 = i; break; }
                    }
                    if (colon2 != -1) {
                        senderUsername = new String(data, prefix.length, colon2 - prefix.length, "UTF-8");
                        pcm = java.util.Arrays.copyOfRange(data, colon2 + 1, data.length);
                    }
                }
            }
        } catch (Exception ignored) {}

        writeToParticipantLine(senderUsername, pcm);
    }

    private void writeToParticipantLine(String username, byte[] pcm) {
        if (pcm == null || pcm.length == 0) return;
        try {
            SourceDataLine line = participantSpeakers.get(username);
            if (line == null || !line.isOpen()) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FMT);
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(AUDIO_FMT, AUDIO_CHUNK * 4);
                line.start();
                participantSpeakers.put(username, line);
            }
            // Écriture directe — write() bloque quelques ms si plein, sans jamais dropper
            int frameSize = line.getFormat().getFrameSize();
            int validLen  = pcm.length - (pcm.length % frameSize);
            if (validLen > 0) line.write(pcm, 0, validLen);
        } catch (LineUnavailableException e) {
            System.out.println("[GCW] Audio " + username + " : " + e.getMessage());
        }
    }

    // Réception frame vidéo — appelé depuis Client.java
    public void receiveVideoFrame(String senderUsername, byte[] jpegData) {
        // FIX : si le tile n'existe pas encore, l'ajouter automatiquement
        // Cas où PARTICIPANT_JOINED arrive après la première frame vidéo
        if (!tiles.containsKey(senderUsername)) {
            Platform.runLater(() -> addParticipant(senderUsername));
        }
        ParticipantTile tile = tiles.get(senderUsername);
        if (tile != null) tile.showVideoFrame(jpegData);
    }

    // ════════════════════════════════════════════════════════════
    // AUDIO LOCAL (micro)
    // ════════════════════════════════════════════════════════════
    private void startAudio() {
        new Thread(() -> {
            try {
                DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FMT);
                if (!AudioSystem.isLineSupported(micInfo)) {
                    Platform.runLater(() -> statusLabel.setText("⚠ Micro non disponible"));
                    return;
                }
                micLine = (TargetDataLine) AudioSystem.getLine(micInfo);
                micLine.open(AUDIO_FMT, AUDIO_CHUNK * 2);
                micLine.start();
                micActive = true;

                Platform.runLater(() -> statusLabel.setText("🎙 Audio · 48 kHz · 20ms latence"));

                byte[] buf = new byte[AUDIO_CHUNK];
                while (micActive) {
                    int n = micLine.read(buf, 0, buf.length);
                    if (n <= 0) continue;

                    int rms = computeRms(buf, n);
                    micLevel.set(rms);

                    boolean talking = rms > 500;
                    ParticipantTile me = tiles.get(client.getUser().getUsername());
                    if (me != null) me.setSpeaking(talking && !micMuted);

                    if (micMuted) continue;

                    float gain = rms < 2500 ? 3.0f : rms < 6000 ? 2.0f : 1.5f;
                    byte[] amplified = amplify(buf, n, gain);

                    byte[] packet = wrapPacket("MA", amplified);
                    if (packet != null) client.sendRawBytes(packet);
                }
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("⚠ Audio : " + e.getMessage()));
            }
        }, "meeting-audio").start();
    }

    // ════════════════════════════════════════════════════════════
    // VIDÉO
    // ════════════════════════════════════════════════════════════
    private void startWebcam() {
        new Thread(() -> {
            try {
                webcam = Webcam.getDefault();
                if (webcam == null) {
                    Platform.runLater(() -> statusLabel.setText("⚠ Aucune webcam"));
                    return;
                }
                webcam.open();
                webcamActive = true;
                webcamReady  = true;
                Platform.runLater(() -> statusLabel.setText("📷 Vidéo active"));
                startVideoSendLoop();
            } catch (Exception e) {
                System.err.println("[WEBCAM] ERREUR : " + e.getMessage());
                Platform.runLater(() -> statusLabel.setText("⚠ Webcam : " + e.getMessage()));
            }
        }, "meeting-webcam-init").start();
    }

    private void startVideoSendLoop() {
        if (videoSendActive) return;
        videoSendActive = true;
        new Thread(() -> {
            long deadline = System.currentTimeMillis() + 8000;
            while (!webcamReady && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(80); } catch (InterruptedException e) { return; }
            }
            if (!webcamReady || webcam == null) return;

            final long FRAME_MS = 66L;
            long nextAt = System.currentTimeMillis();

            while (webcamActive && !camOff) {
                long sleep = nextAt - System.currentTimeMillis();
                if (sleep > 2) try { Thread.sleep(sleep); } catch (InterruptedException e) { break; }
                nextAt += FRAME_MS;

                try {
                    BufferedImage frame = webcam.getImage();
                    if (frame == null) continue;

                    byte[] jpeg = encodeJpeg(frame, 0.72f);
                    if (jpeg == null) continue;

                    ParticipantTile me = tiles.get(client.getUser().getUsername());
                    if (me != null) me.showVideoFrame(jpeg);

                    byte[] packet = wrapPacket("MV", jpeg);
                    if (packet != null) client.sendRawBytes(packet);
                } catch (Exception ignored) {}
            }
            videoSendActive = false;
        }, "meeting-video-send").start();
    }

    // ════════════════════════════════════════════════════════════
    // LAYOUT
    // ════════════════════════════════════════════════════════════
    private VBox buildRoot() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: " + BG_DEEP + ";");
        root.getChildren().addAll(buildTopBar(), buildTilesArea(), buildStatusStrip(), buildControlsBar());
        return root;
    }

    private HBox buildTopBar() {
        Circle liveDot = new Circle(4.5, Color.web(RED_HOT));
        FadeTransition blink = new FadeTransition(Duration.millis(900), liveDot);
        blink.setFromValue(1); blink.setToValue(0.25); blink.setAutoReverse(true);
        blink.setCycleCount(Animation.INDEFINITE); blink.play();

        Label liveLbl = new Label("EN DIRECT");
        liveLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        liveLbl.setTextFill(Color.web(RED_HOT));
        HBox livePill = new HBox(6, liveDot, liveLbl);
        livePill.setAlignment(Pos.CENTER);
        livePill.setPadding(new Insets(4, 12, 4, 10));
        livePill.setStyle("-fx-background-color:rgba(255,59,92,0.1);-fx-background-radius:20;" +
                "-fx-border-color:rgba(255,59,92,0.3);-fx-border-radius:20;-fx-border-width:1;");

        Label groupLbl = new Label(groupName);
        groupLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
        groupLbl.setTextFill(Color.WHITE);

        Label typeBadge = new Label(isVideo ? "VIDÉO" : "AUDIO");
        typeBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        typeBadge.setTextFill(Color.web(ACCENT));
        typeBadge.setPadding(new Insets(3, 10, 3, 10));
        typeBadge.setStyle("-fx-background-color:rgba(0,229,160,0.08);-fx-background-radius:6;" +
                "-fx-border-color:rgba(0,229,160,0.28);-fx-border-radius:6;-fx-border-width:1;");

        participantCount = new Label("● 1");
        participantCount.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        participantCount.setTextFill(Color.web(ACCENT));

        durationLabel = new Label("00:00");
        durationLabel.setFont(Font.font("'Courier New'", FontWeight.BOLD, 14));
        durationLabel.setTextFill(Color.web(GREY_1));

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox bar = new HBox(14, livePill, groupLbl, typeBadge, sp, participantCount, durationLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 22, 14, 22));
        bar.setStyle("-fx-background-color:" + BG_CARD + ";");

        Region neonLine = new Region(); neonLine.setPrefHeight(1);
        neonLine.setStyle("-fx-background-color:linear-gradient(to right,transparent," + ACCENT + ",transparent);");

        VBox wrapper = new VBox(0, bar, neonLine);
        HBox h = new HBox(wrapper); HBox.setHgrow(wrapper, Priority.ALWAYS);
        return h;
    }

    private VBox buildTilesArea() {
        tilesPane = new FlowPane(20, 20);
        tilesPane.setPadding(new Insets(28, 24, 24, 24));
        tilesPane.setAlignment(Pos.TOP_CENTER);
        tilesPane.setStyle("-fx-background-color:" + BG_DEEP + ";");

        ScrollPane scroll = new ScrollPane(tilesPane);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:" + BG_DEEP + ";-fx-background:" + BG_DEEP
                + ";-fx-border-color:transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox wrapper = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    private HBox buildStatusStrip() {
        HBox vu = new HBox(2); vu.setAlignment(Pos.CENTER);
        for (int i = 0; i < 14; i++) {
            Rectangle bar = new Rectangle(3, 14);
            double frac = (i + 1.0) / 14;
            bar.setFill(Color.web(frac < 0.6 ? ACCENT : frac < 0.85 ? AMBER : RED_HOT, 0.18));
            bar.setArcWidth(2); bar.setArcHeight(2);
            vuBars.add(bar); vu.getChildren().add(bar);
        }

        Label mic = new Label("🎙"); mic.setFont(Font.font("Segoe UI", 11));
        statusLabel = new Label("Initialisation...");
        statusLabel.setFont(Font.font("Segoe UI", 11));
        statusLabel.setTextFill(Color.web(GREY_2));

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Label badge = new Label(isHost ? "HÔTE" : "PARTICIPANT");
        badge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        badge.setTextFill(Color.web(isHost ? AMBER : GREY_2));
        badge.setPadding(new Insets(2, 8, 2, 8));
        badge.setStyle("-fx-background-color:" + (isHost ? "rgba(255,181,71,0.1)" : "rgba(107,133,128,0.1)")
                + ";-fx-background-radius:6;");

        HBox strip = new HBox(10, mic, vu, sp, statusLabel, badge);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.setPadding(new Insets(7, 18, 7, 18));
        strip.setStyle("-fx-background-color:" + BG_CARD + ";");
        return strip;
    }

    private HBox buildControlsBar() {
        muteBtn = buildRoundBtn("🎙", ACCENT, BG_SURFACE, "Micro");
        muteBtn.setOnAction(e -> toggleMute());

        camBtn = buildRoundBtn("📷", GREY_1, BG_SURFACE, "Caméra");
        camBtn.setOnAction(e -> toggleCamera());
        camBtn.setVisible(isVideo); camBtn.setManaged(isVideo);

        Button endBtn = new Button(isHost ? "Terminer" : "Quitter");
        endBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        endBtn.setTextFill(Color.WHITE);
        endBtn.setPrefWidth(110); endBtn.setPrefHeight(48);
        endBtn.setStyle("-fx-background-color:" + RED_HOT + ";-fx-background-radius:24;-fx-cursor:hand;");
        endBtn.setOnAction(e -> leaveCall());

        Region l = new Region(); HBox.setHgrow(l, Priority.ALWAYS);
        Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS);

        HBox bar = new HBox(16, l, muteBtn, camBtn, endBtn, r);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(18, 24, 22, 24));
        bar.setStyle("-fx-background-color:" + BG_CARD + ";");

        Region topLine = new Region(); topLine.setPrefHeight(1);
        topLine.setStyle("-fx-background-color:" + GREY_3 + ";");

        VBox wrapper = new VBox(0, topLine, bar);
        HBox h = new HBox(wrapper); HBox.setHgrow(wrapper, Priority.ALWAYS);
        return h;
    }

    private Button buildRoundBtn(String icon, String iconColor, String bg, String tip) {
        Label ico = new Label(icon);
        ico.setFont(Font.font("Segoe UI", 20));
        ico.setTextFill(Color.web(iconColor));
        ico.setAlignment(Pos.CENTER);

        StackPane sp = new StackPane(ico); sp.setPrefSize(52, 52);
        sp.setStyle("-fx-background-color:" + bg + ";-fx-background-radius:26;" +
                "-fx-border-color:" + GREY_3 + ";-fx-border-radius:26;-fx-border-width:1;");

        Button btn = new Button(); btn.setGraphic(sp); btn.setPrefSize(52, 52);
        btn.setStyle("-fx-background-color:transparent;-fx-padding:0;-fx-border-color:transparent;-fx-cursor:hand;");
        Tooltip.install(btn, new Tooltip(tip));
        return btn;
    }

    // ════════════════════════════════════════════════════════════
    // TUILE PARTICIPANT
    // ════════════════════════════════════════════════════════════
    private class ParticipantTile {
        final String username;
        final boolean isMe;
        final VBox node;
        javafx.scene.canvas.Canvas videoCanvas;
        Circle speakRing;
        Timeline speakAnim;
        boolean isMuted = false;

        ParticipantTile(String username, boolean isMe) {
            this.username = username;
            this.isMe = isMe;
            this.node = buildTile();
        }

        VBox buildTile() {
            StackPane media = new StackPane();
            media.setPrefSize(210, 155);
            media.setStyle("-fx-background-color:" + BG_SURFACE + ";");

            String init = username.isEmpty() ? "?" : username.substring(0, 1).toUpperCase();
            Label avatarLbl = new Label(init);
            avatarLbl.setFont(Font.font("Georgia", FontWeight.BOLD, 34));
            avatarLbl.setTextFill(Color.web(GREY_1));

            Circle avatarBg = new Circle(38);
            avatarBg.setFill(Color.web(BG_HOVER));
            avatarBg.setStroke(Color.web(GREY_3)); avatarBg.setStrokeWidth(1.5);

            speakRing = new Circle(46);
            speakRing.setFill(Color.TRANSPARENT);
            speakRing.setStroke(Color.web(ACCENT, 0.0)); speakRing.setStrokeWidth(2.5);

            media.getChildren().add(new StackPane(speakRing, avatarBg, avatarLbl));

            if (isVideo) {
                videoCanvas = new javafx.scene.canvas.Canvas(210, 155);
                videoCanvas.setVisible(false);
                media.getChildren().add(videoCanvas);
            }

            if (isMe) {
                Label youBadge = new Label("VOUS");
                youBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
                youBadge.setTextFill(Color.web(ACCENT));
                youBadge.setPadding(new Insets(2, 7, 2, 7));
                youBadge.setStyle("-fx-background-color:rgba(0,229,160,0.12);-fx-background-radius:5;" +
                        "-fx-border-color:rgba(0,229,160,0.35);-fx-border-radius:5;-fx-border-width:1;");
                StackPane.setAlignment(youBadge, Pos.TOP_LEFT);
                StackPane.setMargin(youBadge, new Insets(7, 0, 0, 7));
                media.getChildren().add(youBadge);
            }

            Label nameLbl = new Label(username);
            nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            nameLbl.setTextFill(Color.web(GREY_1));
            HBox.setHgrow(nameLbl, Priority.ALWAYS);

            Label muteIco = new Label(""); muteIco.setFont(Font.font("Segoe UI", 12));
            HBox bottom = new HBox(8, nameLbl, muteIco);
            bottom.setAlignment(Pos.CENTER_LEFT);
            bottom.setPadding(new Insets(7, 10, 9, 12));
            bottom.setStyle("-fx-background-color:" + BG_CARD + ";");

            VBox card = new VBox(0, media, bottom);
            card.setMinWidth(210); card.setMaxWidth(210);
            card.setStyle("-fx-background-color:" + BG_CARD + ";-fx-background-radius:14;" +
                    "-fx-border-color:" + (isMe ? ACCENT_DIM : GREY_3) + ";" +
                    "-fx-border-radius:14;-fx-border-width:" + (isMe ? "1.5" : "1") + ";");

            Rectangle clip = new Rectangle(210, 210);
            clip.setArcWidth(28); clip.setArcHeight(28); card.setClip(clip);

            if (isMe) {
                DropShadow glow = new DropShadow();
                glow.setColor(Color.web(ACCENT, 0.25)); glow.setRadius(16);
                card.setEffect(glow);
            }

            card.setOpacity(0); card.setTranslateY(18);
            Platform.runLater(() -> {
                FadeTransition fte = new FadeTransition(Duration.millis(280), card);
                fte.setFromValue(0); fte.setToValue(1);
                TranslateTransition tte = new TranslateTransition(Duration.millis(280), card);
                tte.setFromY(18); tte.setToY(0);
                new ParallelTransition(fte, tte).play();
            });

            return card;
        }

        void showVideoFrame(byte[] jpegData) {
            if (videoCanvas == null) return;
            Platform.runLater(() -> {
                try {
                    javafx.scene.image.Image img =
                            new javafx.scene.image.Image(new java.io.ByteArrayInputStream(jpegData));
                    if (!img.isError()) {
                        videoCanvas.setVisible(true);
                        videoCanvas.getGraphicsContext2D().drawImage(img, 0, 0,
                                videoCanvas.getWidth(), videoCanvas.getHeight());
                    }
                } catch (Exception ignored) {}
            });
        }

        void hideVideo() { if (videoCanvas != null) Platform.runLater(() -> videoCanvas.setVisible(false)); }

        void setSpeaking(boolean speaking) {
            Platform.runLater(() -> {
                if (speaking) {
                    if (speakAnim == null || speakAnim.getStatus() != Animation.Status.RUNNING) {
                        speakRing.setStroke(Color.web(ACCENT, 0.7));
                        speakAnim = new Timeline(
                                new KeyFrame(Duration.ZERO,
                                        new KeyValue(speakRing.strokeWidthProperty(), 2.5),
                                        new KeyValue(speakRing.radiusProperty(), 46.0)),
                                new KeyFrame(Duration.millis(550),
                                        new KeyValue(speakRing.strokeWidthProperty(), 5.5),
                                        new KeyValue(speakRing.radiusProperty(), 54.0))
                        );
                        speakAnim.setAutoReverse(true);
                        speakAnim.setCycleCount(Animation.INDEFINITE);
                        speakAnim.play();
                    }
                } else {
                    if (speakAnim != null) speakAnim.stop();
                    speakRing.setStroke(Color.web(ACCENT, 0.0));
                    speakRing.setStrokeWidth(2.5); speakRing.setRadius(46);
                }
            });
        }

        void setMuted(boolean muted) {
            this.isMuted = muted;
            Platform.runLater(() -> {
                HBox bottom = (HBox) node.getChildren().get(1);
                if (bottom.getChildren().size() >= 2)
                    ((Label) bottom.getChildren().get(1)).setText(muted ? "🔇" : "");
                if (muted) setSpeaking(false);
            });
        }

        VBox getNode() { return node; }
    }

    private void addLocalTile() {
        String me = client.getUser().getUsername();
        ParticipantTile tile = new ParticipantTile(me, true);
        tiles.put(me, tile);
        Platform.runLater(() -> { tilesPane.getChildren().add(tile.getNode()); updateCount(); });
    }

    public void addParticipant(String username) {
        Platform.runLater(() -> {
            if (tiles.containsKey(username)) return;
            ParticipantTile tile = new ParticipantTile(username, false);
            tiles.put(username, tile);
            tilesPane.getChildren().add(tile.getNode());
            updateCount();
            showToast("👋  " + username + " a rejoint");
        });
    }

    public void removeParticipant(String username) {
        Platform.runLater(() -> {
            ParticipantTile tile = tiles.remove(username);
            if (tile == null) return;
            FadeTransition ft = new FadeTransition(Duration.millis(220), tile.getNode());
            ft.setFromValue(1); ft.setToValue(0);
            ft.setOnFinished(e -> tilesPane.getChildren().remove(tile.getNode()));
            ft.play();
            updateCount();
            showToast("  " + username + " a quitté");

            // Libérer la ligne audio du participant
            SourceDataLine line = participantSpeakers.remove(username);
            if (line != null && line.isOpen()) { line.stop(); line.close(); }
        });
    }

    private void updateCount() { participantCount.setText("● " + tiles.size()); }

    private void toggleMute() {
        micMuted = !micMuted;
        ParticipantTile me = tiles.get(client.getUser().getUsername());
        if (me != null) { me.setMuted(micMuted); if (micMuted) me.setSpeaking(false); }

        Platform.runLater(() -> {
            StackPane sp = (StackPane) muteBtn.getGraphic();
            Label ico = (Label) sp.getChildren().get(0);
            if (micMuted) {
                ico.setText("🔇"); ico.setTextFill(Color.web(RED_HOT));
                sp.setStyle("-fx-background-color:rgba(255,59,92,0.15);-fx-background-radius:26;" +
                        "-fx-border-color:rgba(255,59,92,0.4);-fx-border-radius:26;-fx-border-width:1;");
            } else {
                ico.setText("🎙"); ico.setTextFill(Color.web(ACCENT));
                sp.setStyle("-fx-background-color:" + BG_SURFACE + ";-fx-background-radius:26;" +
                        "-fx-border-color:" + GREY_3 + ";-fx-border-radius:26;-fx-border-width:1;");
            }
        });
        client.sendCommand("MEETING_MUTE:" + meetingId + ":" + micMuted);
    }

    private void toggleCamera() {
        camOff = !camOff;
        ParticipantTile me = tiles.get(client.getUser().getUsername());
        if (me != null && camOff) me.hideVideo();

        Platform.runLater(() -> {
            StackPane sp = (StackPane) camBtn.getGraphic();
            Label ico = (Label) sp.getChildren().get(0);
            if (camOff) {
                ico.setText("🚫");
                sp.setStyle("-fx-background-color:rgba(255,59,92,0.15);-fx-background-radius:26;" +
                        "-fx-border-color:rgba(255,59,92,0.4);-fx-border-radius:26;-fx-border-width:1;");
            } else {
                ico.setText("📷"); ico.setTextFill(Color.web(GREY_1));
                sp.setStyle("-fx-background-color:" + BG_SURFACE + ";-fx-background-radius:26;" +
                        "-fx-border-color:" + GREY_3 + ";-fx-border-radius:26;-fx-border-width:1;");
                if (webcamReady) startVideoSendLoop();
            }
        });
    }

    public void leaveCall() {
        micActive = false; webcamActive = false; videoSendActive = false;

        if (micLine != null) try { micLine.stop(); micLine.close(); } catch (Exception ignored) {}

        for (SourceDataLine line : participantSpeakers.values()) {
            try { if (line.isOpen()) { line.stop(); line.close(); } } catch (Exception ignored) {}
        }
        participantSpeakers.clear();

        if (webcam != null) try { webcam.close(); } catch (Exception ignored) {}
        if (durationTimer != null) durationTimer.stop();
        if (vuTimer != null) vuTimer.stop();

        client.sendCommand(isHost
                ? "END_MEETING:"   + meetingId + ":" + groupId
                : "LEAVE_MEETING:" + meetingId + ":" + groupId);
        client.setGroupCallWindow(null);

        Platform.runLater(() -> { if (stage != null) stage.close(); });
    }

    public void onMeetingEnded() {
        Platform.runLater(() -> {
            showToast("La réunion est terminée");
            new Timeline(new KeyFrame(Duration.seconds(2.5), e -> leaveCall())).play();
        });
    }

    public void onParticipantMuteChanged(String username, boolean muted) {
        ParticipantTile tile = tiles.get(username);
        if (tile != null) tile.setMuted(muted);
    }

    // ════════════════════════════════════════════════════════════
    // DSP
    // ════════════════════════════════════════════════════════════
    private byte[] amplify(byte[] buf, int len, float gain) {
        byte[] out = new byte[len];
        for (int i = 0; i < len - 1; i += 2) {
            short s = (short)((buf[i + 1] << 8) | (buf[i] & 0xFF));
            int   a = Math.max(-32768, Math.min(32767, (int)(s * gain)));
            out[i] = (byte)(a & 0xFF); out[i + 1] = (byte)((a >> 8) & 0xFF);
        }
        return out;
    }

    private int computeRms(byte[] buf, int len) {
        long sum = 0; int n = 0;
        for (int i = 0; i < len - 1; i += 2) {
            short s = (short)((buf[i + 1] << 8) | (buf[i] & 0xFF));
            sum += (long)s * s; n++;
        }
        return n > 0 ? (int) Math.sqrt((double) sum / n) : 0;
    }

    private byte[] encodeJpeg(BufferedImage img, float quality) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(12288);
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
            writer.dispose(); ios.close();
            return baos.toByteArray();
        } catch (Exception e) { return null; }
    }

    private byte[] wrapPacket(String type, byte[] data) {
        try {
            byte[] hdr = (type + ":" + meetingId + ":").getBytes("UTF-8");
            byte[] pkt = new byte[hdr.length + data.length];
            System.arraycopy(hdr, 0, pkt, 0, hdr.length);
            System.arraycopy(data, 0, pkt, hdr.length, data.length);
            return pkt;
        } catch (Exception e) { return null; }
    }

    private void startVuMeter() {
        vuTimer = new Timeline(new KeyFrame(Duration.millis(55), e -> {
            double norm = Math.min(1.0, micLevel.get() / 7500.0);
            int active = (int)(norm * vuBars.size());
            for (int i = 0; i < vuBars.size(); i++) {
                double frac = (i + 1.0) / vuBars.size();
                String col = frac < 0.6 ? ACCENT : frac < 0.85 ? AMBER : RED_HOT;
                vuBars.get(i).setFill(Color.web(col, i < active ? (micMuted ? 0.25 : 0.88) : 0.14));
            }
        }));
        vuTimer.setCycleCount(Animation.INDEFINITE); vuTimer.play();
    }

    private void startDurationTimer() {
        startTime = System.currentTimeMillis();
        durationTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long s = (System.currentTimeMillis() - startTime) / 1000;
            durationLabel.setText(String.format("%02d:%02d", s / 60, s % 60));
        }));
        durationTimer.setCycleCount(Animation.INDEFINITE); durationTimer.play();
    }

    private void showToast(String msg) {
        Platform.runLater(() -> {
            Label lbl = new Label(msg);
            lbl.setFont(Font.font("Segoe UI", 12)); lbl.setTextFill(Color.WHITE);
            lbl.setPadding(new Insets(9, 20, 9, 20));
            lbl.setStyle("-fx-background-color:" + BG_SURFACE + ";-fx-background-radius:22;" +
                    "-fx-border-color:" + GREY_3 + ";-fx-border-radius:22;-fx-border-width:1;");
            DropShadow ds = new DropShadow(); ds.setColor(Color.BLACK); ds.setRadius(14);
            lbl.setEffect(ds);

            StackPane overlay = new StackPane(lbl);
            overlay.setAlignment(Pos.BOTTOM_CENTER);
            overlay.setPadding(new Insets(0, 0, 10, 0));
            overlay.setMouseTransparent(true);
            tilesPane.getChildren().add(overlay);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(350), overlay);
            fadeOut.setDelay(Duration.seconds(2.3)); fadeOut.setFromValue(1); fadeOut.setToValue(0);
            fadeOut.setOnFinished(ev -> tilesPane.getChildren().remove(overlay));
            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), overlay);
            fadeIn.setFromValue(0); fadeIn.setToValue(1);
            fadeIn.setOnFinished(ev2 -> fadeOut.play()); fadeIn.play();
        });
    }

    public String  getMeetingId() { return meetingId; }
    public String  getGroupId()   { return groupId; }
    public boolean isVideoCall()  { return isVideo; }
}