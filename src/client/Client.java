package client;

import call.AudioCall;
import call.Call;
import call.VideoCall;
import model.*;
import ui.CallWindow;
import ui.ChatWindow;
import ui.LoginWindow;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;

import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.Arrays;

public class Client implements Runnable {

    private String serverIP;
    private int port;
    private Socket socket;
    private User user;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private LoginWindow loginWindow;
    private ChatWindow chatWindow;
    private CallWindow callWindow;
    private Call currentCall;
    private List<User> connectedUsers = new ArrayList<>();
    private ui.GroupChatWindow groupChatWindow;
    private ui.GroupCallWindow groupCallWindow = null;

    public void setGroupCallWindow(ui.GroupCallWindow w) { this.groupCallWindow = w; }
    public ui.GroupCallWindow getGroupCallWindow() { return groupCallWindow; }

    public Client(String serverIP, int port) {
        this.serverIP = serverIP;
        this.port = port;
        this.currentCall = null;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Object received = in.readObject();

                if (received instanceof model.FileMessage) {
                    model.FileMessage fm = (model.FileMessage) received;
                    if (chatWindow != null) chatWindow.displayMessage(fm);

                } else if (received instanceof Message) {
                    receiveMessage((Message) received);

                } else if (received instanceof List) {
                    List<User> users = (List<User>) received;
                    this.connectedUsers = users;
                    if (chatWindow != null) chatWindow.updateUserList(users);

                } else if (received instanceof Call) {
                    currentCall = (Call) received;
                    receiveCall(currentCall);

                } else if (received instanceof byte[]) {
                    byte[] data = (byte[]) received;
                    handleIncomingBytes(data);

                } else if (received instanceof String) {
                    handleServerCommand((String) received);

                } else if (received instanceof User) {
                    this.user = (User) received;
                    System.out.println("User mis à jour : " + user.getId());
                }
            }

        } catch (EOFException e) {
            System.out.println("Déconnecté du serveur !");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Erreur réception : " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GESTION BYTES ENTRANTS — centralisée ici pour éviter les conflits
    // ═══════════════════════════════════════════════════════════════
    private void handleIncomingBytes(byte[] data) {
        if (data == null || data.length == 0) return;

        // ── Appels de groupe : toujours prioritaires ──────────────
        // Le serveur envoie soit du raw audio PCM, soit "MVFRAME:sender:jpeg"
        if (groupCallWindow != null) {
            try {
                // Vérifier si c'est une frame vidéo de groupe
                // Format serveur : "MVFRAME:<username>:<jpegbytes>"
                if (data.length > 8) {
                    byte[] prefix = "MVFRAME:".getBytes("UTF-8");
                    if (bytesStartWith(data, prefix)) {
                        // Extraire le sender et les bytes JPEG
                        int colon2 = -1;
                        for (int i = prefix.length; i < Math.min(data.length, prefix.length + 50); i++) {
                            if (data[i] == ':') { colon2 = i; break; }
                        }
                        if (colon2 != -1) {
                            String sender = new String(data, prefix.length, colon2 - prefix.length, "UTF-8");
                            byte[] jpeg = Arrays.copyOfRange(data, colon2 + 1, data.length);
                            groupCallWindow.receiveVideoFrame(sender, jpeg);
                        }
                        return;
                    }
                }
                // Sinon c'est de l'audio de groupe (raw PCM envoyé par broadcastMeetingAudio)
                // FIX ECHO : on passe le username de l'émetteur pour que chaque participant
                // ait sa propre SourceDataLine. Sans ça, tout le monde partage une ligne
                // → echo catastrophique.
                // Problème : le serveur envoie du raw PCM sans identifier l'émetteur.
                // Solution : utiliser receiveAudio() qui crée une ligne par "session".
                groupCallWindow.receiveAudio(data);
                return;
            } catch (Exception ignored) {}
        }

        // ── Appels 1-1 ────────────────────────────────────────────
        if (currentCall instanceof call.AudioCall) {
            // FIX : le sender envoie maintenant "A:<pcmbytes>" via startAudioStreaming
            // On strip le header avant de jouer
            try {
                byte[] audioPfx = "A:".getBytes("UTF-8");
                if (bytesStartWith(data, audioPfx)) {
                    byte[] pcm = Arrays.copyOfRange(data, audioPfx.length, data.length);
                    ((call.AudioCall) currentCall).playAudioData(pcm);
                } else {
                    // Compatibilité : pas de header → jouer directement
                    ((call.AudioCall) currentCall).playAudioData(data);
                }
            } catch (Exception e) {
                ((call.AudioCall) currentCall).playAudioData(data);
            }

        } else if (currentCall instanceof call.VideoCall) {
            try {
                byte[] videoPfx = "V:".getBytes("UTF-8");
                byte[] audioPfx = "A:".getBytes("UTF-8");

                if (bytesStartWith(data, videoPfx)) {
                    // Frame JPEG vidéo
                    final byte[] jpeg = Arrays.copyOfRange(data, videoPfx.length, data.length);
                    Platform.runLater(() -> {
                        if (callWindow != null) {
                            javafx.scene.image.Image img = new javafx.scene.image.Image(
                                    new java.io.ByteArrayInputStream(jpeg));
                            callWindow.showVideoFeed(img);
                        }
                    });
                } else if (bytesStartWith(data, audioPfx)) {
                    // Audio PCM de l'appel vidéo
                    byte[] pcm = Arrays.copyOfRange(data, audioPfx.length, data.length);
                    ((call.VideoCall) currentCall).playAudioData(pcm);
                } else {
                    // Compatibilité : détecter par magic bytes JPEG (0xFF 0xD8)
                    if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
                        final byte[] frameCopy = data;
                        Platform.runLater(() -> {
                            if (callWindow != null) {
                                javafx.scene.image.Image img = new javafx.scene.image.Image(
                                        new java.io.ByteArrayInputStream(frameCopy));
                                callWindow.showVideoFeed(img);
                            }
                        });
                    } else {
                        ((call.VideoCall) currentCall).playAudioData(data);
                    }
                }
            } catch (Exception e) {
                System.out.println("[CLIENT] Erreur bytes : " + e.getMessage());
            }
        }
    }

    private boolean bytesStartWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    public void connect() {
        try {
            socket = new Socket(serverIP, port);
            System.out.println("Connecté au serveur !");
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            System.out.println("Impossible de se connecter : " + e.getMessage());
            if (loginWindow != null) loginWindow.showError("Impossible de se connecter au serveur !");
        }
    }

    public void disconnect() {
        try {
            sendObject("DISCONNECT");
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("Erreur déconnexion : " + e.getMessage());
        }
    }

    public void login(String username, String password) {
        this.user = new User(username, password);
        sendObject(this.user);
        System.out.println("Login envoyé : " + username);
        Thread listenerThread = new Thread(this);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void sendMessage(Message message) { sendObject(message); }

    private void receiveMessage(Message message) {
        System.out.println("Message reçu de " + message.getSender().getUsername());
        if (chatWindow != null) chatWindow.displayMessage(message);
    }

    public void startCall(User target, CallType type) {
        if (type == CallType.AUDIO) { currentCall = new AudioCall(user, target); }
        else { currentCall = new VideoCall(user, target); }
        sendObject(currentCall);
        System.out.println("Appel " + type.name() + " vers " + target.getUsername());
    }

    // FIX : envoyer avec header "A:" pour que le receiver sache que c'est de l'audio
    public void startAudioStreaming(AudioCall audioCall) {
        Thread streamThread = new Thread(() -> {
            try {
                byte[] prefix = "A:".getBytes("UTF-8");
                while (audioCall.getStatus() == model.CallStatus.ACTIVE) {
                    byte[] data = audioCall.readAudioData();
                    if (data.length > 0) {
                        byte[] packet = new byte[prefix.length + data.length];
                        System.arraycopy(prefix, 0, packet, 0, prefix.length);
                        System.arraycopy(data, 0, packet, prefix.length, data.length);
                        sendObject(packet);
                    }
                }
            } catch (Exception e) {
                System.out.println("[AUDIO] Erreur streaming : " + e.getMessage());
            }
        }, "audio-stream");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    public void receiveCall(Call incomingCall) {
        System.out.println("Appel entrant/mis à jour : "
                + incomingCall.getCaller().getUsername()
                + " | Statut : " + incomingCall.getStatus());

        Platform.runLater(() -> {
            if (incomingCall.getStatus() == model.CallStatus.ACTIVE && callWindow != null) {
                callWindow.setCall(incomingCall);
                callWindow.showIncoming();
                // Le caller démarre le streaming quand l'appel devient ACTIVE
                if (currentCall instanceof call.AudioCall) {
                    ((call.AudioCall) currentCall).captureAudio();
                    startAudioStreaming((call.AudioCall) currentCall);
                } else if (currentCall instanceof call.VideoCall) {
                    call.VideoCall vc = (call.VideoCall) currentCall;
                    new Thread(() -> { vc.captureVideo(); startVideoStreaming(vc); }).start();
                }
            } else if (incomingCall.getStatus() == model.CallStatus.PENDING) {
                CallWindow window = new CallWindow(this, incomingCall);
                window.setCallerMode(false);
                this.callWindow = window;
                try {
                    window.start(new Stage());
                } catch (Exception e) {
                    System.out.println("Erreur ouverture CallWindow : " + e.getMessage());
                }
            } else if (incomingCall.getStatus() == model.CallStatus.REFUSED
                    || incomingCall.getStatus() == model.CallStatus.ENDED) {
                if (callWindow != null) callWindow.onEndCall();
            }
        });
    }

    // FIX : envoyer vidéo avec header "V:" et audio avec header "A:"
    // Comme ça le receiver peut distinguer les deux sur le même socket
    public void startVideoStreaming(VideoCall videoCall) {
        // Thread vidéo
        Thread videoThread = new Thread(() -> {
            try {
                byte[] vPrefix = "V:".getBytes("UTF-8");
                while (videoCall.getStatus() == model.CallStatus.ACTIVE) {
                    long start = System.currentTimeMillis();
                    byte[] jpeg = videoCall.captureFrameBytes();
                    if (jpeg.length > 0) {
                        byte[] packet = new byte[vPrefix.length + jpeg.length];
                        System.arraycopy(vPrefix, 0, packet, 0, vPrefix.length);
                        System.arraycopy(jpeg, 0, packet, vPrefix.length, jpeg.length);
                        sendObject(packet);
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    try { Thread.sleep(Math.max(0, 66 - elapsed)); } catch (InterruptedException ex) { break; }
                }
            } catch (Exception e) {
                System.out.println("[VIDEO] Erreur streaming : " + e.getMessage());
            }
        }, "video-stream");
        videoThread.setDaemon(true);
        videoThread.start();

        // Thread audio vidéo
        videoCall.captureAudio();
        Thread audioThread = new Thread(() -> {
            try {
                byte[] aPrefix = "A:".getBytes("UTF-8");
                while (videoCall.getStatus() == model.CallStatus.ACTIVE) {
                    byte[] data = videoCall.readAudioData();
                    if (data.length > 0) {
                        byte[] packet = new byte[aPrefix.length + data.length];
                        System.arraycopy(aPrefix, 0, packet, 0, aPrefix.length);
                        System.arraycopy(data, 0, packet, aPrefix.length, data.length);
                        sendObject(packet);
                    }
                }
            } catch (Exception e) {
                System.out.println("[VIDEO-AUDIO] Erreur streaming : " + e.getMessage());
            }
        }, "video-audio-stream");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    private void handleServerCommand(String command) {
        System.out.println("Commande serveur : " + command);

        if (command.equals("LOGIN_SUCCESS")) {
            System.out.println("Login accepté !");
            if (loginWindow != null) loginWindow.openChatWindow();
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    sendObject("GET_USERS");
                    sendObject("GET_PENDING_INVITATIONS");
                } catch (InterruptedException e) { e.printStackTrace(); }
            }).start();

        } else if (command.equals("LOGIN_FAILED")) {
            System.out.println("Login refusé !");
            if (loginWindow != null) loginWindow.showError("Username ou mot de passe incorrect !");

        } else if (command.startsWith("DELIVERED:")) {
            String messageId = command.split(":")[1];
            if (chatWindow != null) chatWindow.handleDelivered(messageId);

        } else if (command.startsWith("SEEN:")) {
            String messageId = command.split(":")[1];
            if (chatWindow != null) chatWindow.handleSeen(messageId);

        } else if (command.startsWith("DELETE_MSG:")) {
            String messageId = command.split(":")[1];
            if (chatWindow != null) chatWindow.handleRemoteDelete(messageId);

        } else if (command.startsWith("EDIT_MSG:")) {
            String[] parts = command.split(":", 4);
            if (parts.length >= 3 && chatWindow != null) chatWindow.handleRemoteEdit(parts[1], parts[2]);

        } else if (command.startsWith("PIN_MSG:")) {
            String[] parts = command.split(":");
            if (parts.length >= 3 && chatWindow != null)
                chatWindow.handleRemotePin(parts[1], Boolean.parseBoolean(parts[2]));

        } else if (command.startsWith("GROUP_HISTORY:")) {
            int colonIdx = command.indexOf(':');
            int sep1 = command.indexOf('\u0001');
            String groupId = sep1 == -1
                    ? command.substring(colonIdx + 1)
                    : command.substring(colonIdx + 1, sep1);
            List<String[]> messages = new ArrayList<>();
            if (sep1 != -1) {
                String[] entries = command.substring(sep1 + 1).split("\u0001");
                for (String entry : entries) {
                    String[] fields = entry.split("\u0002", 9);
                    if (fields.length >= 4) messages.add(fields);
                }
            }
            if (groupChatWindow != null)
                groupChatWindow.loadHistoryMessages(groupId, messages);

        } else if (command.startsWith("GROUP_MSG:")) {
            String[] parts = command.split(":", 6);
            if (parts.length >= 5 && groupChatWindow != null) {
                String replyToId = parts.length == 6 ? parts[5] : "";
                groupChatWindow.displayMessage(parts[1], parts[2], parts[3], parts[4], "", replyToId);
            }
            if (chatWindow != null) chatWindow.handleServerCommand(command);

        } else if (command.startsWith("GROUP_DELETE_MSG:")) {
            String messageId = command.split(":")[1];
            if (groupChatWindow != null) groupChatWindow.handleDeleteMessage(messageId);

        } else if (command.startsWith("GROUP_EDIT_MSG:")) {
            String[] parts = command.split(":", 3);
            if (parts.length == 3 && groupChatWindow != null)
                groupChatWindow.handleEditMessage(parts[1], parts[2]);

        } else if (command.startsWith("GROUP_PIN_MSG:")) {
            String[] parts = command.split(":");
            if (parts.length == 3 && groupChatWindow != null)
                groupChatWindow.handlePinMessage(parts[1], Boolean.parseBoolean(parts[2]));

        } else if (command.startsWith("GROUP_FILE:")) {
            int p1 = command.indexOf(':');
            int p2 = command.indexOf(':', p1 + 1);
            int p3 = command.indexOf(':', p2 + 1);
            int p4 = command.indexOf(':', p3 + 1);
            int p5 = command.indexOf(':', p4 + 1);
            if (p5 != -1 && groupChatWindow != null) {
                groupChatWindow.displayFileMessage(
                        command.substring(p1 + 1, p2),
                        command.substring(p2 + 1, p3),
                        command.substring(p3 + 1, p4),
                        command.substring(p4 + 1, p5),
                        command.substring(p5 + 1));
            }

        } else if (command.startsWith("GROUP_VOICE:")) {
            int p1 = command.indexOf(':');
            int p2 = command.indexOf(':', p1 + 1);
            int p3 = command.indexOf(':', p2 + 1);
            int p4 = command.indexOf(':', p3 + 1);
            if (p4 != -1 && groupChatWindow != null) {
                groupChatWindow.displayVoiceMessage(
                        command.substring(p1 + 1, p2),
                        command.substring(p2 + 1, p3),
                        command.substring(p3 + 1, p4),
                        command.substring(p4 + 1));
            }

        } else if (command.startsWith("GROUP_CREATED:")
                || command.startsWith("MEMBER_ADDED:")
                || command.startsWith("MEMBER_REMOVED:")
                || command.startsWith("MY_GROUPS")) {
            if (chatWindow != null) chatWindow.handleServerCommand(command);

        } else if (command.startsWith("MEETING_STARTED:")) {
            String[] p = command.split(":");
            if (p.length == 4) {
                // Hôte : la fenêtre est déjà ouverte, on envoie juste JOIN
                String meetingId = p[1], grpId = p[2], type = p[3];
                Platform.runLater(() -> {
                    ui.GroupCallWindow gcw = new ui.GroupCallWindow(
                            this, meetingId, grpId, resolveGroupName(grpId), type, true);
                    gcw.show();
                    sendCommand("JOIN_MEETING:" + meetingId + ":" + grpId);
                });
            } else if (p.length == 5) {
                // Participant invité
                String meetingId = p[1], grpId = p[2], type = p[3], host = p[4];
                Platform.runLater(() -> {
                    javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.CONFIRMATION);
                    a.setTitle((type.equals("VIDEO") ? "📹" : "📞") + " Réunion de groupe");
                    a.setHeaderText(null);
                    a.setContentText(host + " a démarré une réunion " + type + ".\nRejoindre ?");
                    a.getButtonTypes().setAll(
                            new javafx.scene.control.ButtonType("  Rejoindre",
                                    javafx.scene.control.ButtonBar.ButtonData.OK_DONE),
                            new javafx.scene.control.ButtonType("Ignorer",
                                    javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
                    a.showAndWait().ifPresent(r -> {
                        if (r.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.OK_DONE) {
                            ui.GroupCallWindow gcw = new ui.GroupCallWindow(
                                    this, meetingId, grpId, resolveGroupName(grpId), type, false);
                            gcw.show();
                            sendCommand("JOIN_MEETING:" + meetingId + ":" + grpId);
                        }
                    });
                });
            }

        } else if (command.startsWith("MEETING_ENDED:")) {
            String mid = command.split(":")[1];
            if (groupCallWindow != null && groupCallWindow.getMeetingId().equals(mid))
                groupCallWindow.onMeetingEnded();

        } else if (command.startsWith("PARTICIPANT_JOINED:")) {
            String[] p = command.split(":");
            if (p.length == 3 && groupCallWindow != null
                    && groupCallWindow.getMeetingId().equals(p[1]))
                groupCallWindow.addParticipant(p[2]);

        } else if (command.startsWith("PARTICIPANT_LEFT:")) {
            String[] p = command.split(":");
            if (p.length == 3 && groupCallWindow != null
                    && groupCallWindow.getMeetingId().equals(p[1]))
                groupCallWindow.removeParticipant(p[2]);

        } else if (command.startsWith("JOINED_MEETING:")) {
            System.out.println("[MEETING] Rejoint : " + command.split(":")[1]);

        } else if (command.startsWith("MEETING_MUTE_NOTIF:")) {
            String[] p = command.split(":");
            if (p.length == 4 && groupCallWindow != null
                    && groupCallWindow.getMeetingId().equals(p[1]))
                groupCallWindow.onParticipantMuteChanged(p[2], Boolean.parseBoolean(p[3]));

        } else if (command.startsWith("GROUP_INVITE_NOTIF:")) {
            String[] parts = command.split(":", 4);
            if (parts.length == 4) {
                final String groupId     = parts[1];
                final String groupName   = parts[2];
                final String inviterName = parts[3];
                Platform.runLater(() -> showGroupInvitationDialog(groupId, groupName, inviterName));
            }

        } else if (command.startsWith("GROUP_JOIN_SUCCESS:")
                || command.startsWith("MEMBER_JOINED:")
                || command.startsWith("INVITE_REFUSED:")) {
            if (chatWindow != null) chatWindow.handleServerCommand(command);
        }
    }

    private synchronized void sendObject(Object obj) {
        try {
            if (out != null) { out.writeObject(obj); out.flush(); out.reset(); }
        } catch (IOException e) {
            System.out.println("Erreur envoi : " + e.getMessage());
        }
    }

    public void sendCommand(String command) { sendObject(command); }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public void setLoginWindow(LoginWindow loginWindow) { this.loginWindow = loginWindow; }
    public void setChatWindow(ChatWindow chatWindow) { this.chatWindow = chatWindow; }
    public void setCallWindow(CallWindow callWindow) { this.callWindow = callWindow; }
    public Call getCurrentCall() { return currentCall; }
    public List<User> getConnectedUsers() { return connectedUsers; }
    public void sendCallResponse(call.Call call) { sendObject(call); }
    public void setGroupChatWindow(ui.GroupChatWindow w) { this.groupChatWindow = w; }
    public ui.GroupChatWindow getGroupChatWindow() { return groupChatWindow; }
    public void sendRawBytes(byte[] data) { sendObject(data); }

    private void showGroupInvitationDialog(String groupId, String groupName, String inviterUsername) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.setTitle("Invitation au groupe");
        dialog.setResizable(false);

        javafx.scene.control.Label icon = new javafx.scene.control.Label("👥");
        icon.setFont(javafx.scene.text.Font.font("Segoe UI", 38));
        icon.setAlignment(javafx.geometry.Pos.CENTER);

        javafx.scene.control.Label title = new javafx.scene.control.Label("Invitation au groupe");
        title.setFont(javafx.scene.text.Font.font("Georgia",
                javafx.scene.text.FontWeight.BOLD, 16));
        title.setTextFill(javafx.scene.paint.Color.web("#1C1C1A"));

        javafx.scene.control.Label sub = new javafx.scene.control.Label(
                inviterUsername + " vous invite à rejoindre :");
        sub.setFont(javafx.scene.text.Font.font("Segoe UI", 13));
        sub.setTextFill(javafx.scene.paint.Color.web("#9A9A92"));

        javafx.scene.control.Label groupLbl = new javafx.scene.control.Label(groupName);
        groupLbl.setFont(javafx.scene.text.Font.font("Georgia",
                javafx.scene.text.FontWeight.BOLD, 18));
        groupLbl.setTextFill(javafx.scene.paint.Color.web("#1A3C34"));
        groupLbl.setPadding(new javafx.geometry.Insets(8, 20, 8, 20));
        groupLbl.setStyle(
                "-fx-background-color: #E8F2EF; -fx-background-radius: 12;" +
                        "-fx-border-color: rgba(74,124,111,0.2); -fx-border-radius: 12; -fx-border-width: 1;");

        javafx.scene.control.Button acceptBtn = new javafx.scene.control.Button("✓  Rejoindre le groupe");
        acceptBtn.setMaxWidth(Double.MAX_VALUE);
        acceptBtn.setPrefHeight(44);
        acceptBtn.setFont(javafx.scene.text.Font.font("Segoe UI",
                javafx.scene.text.FontWeight.BOLD, 13));
        acceptBtn.setStyle(
                "-fx-background-color: #1A3C34; -fx-text-fill: white;" +
                        "-fx-background-radius: 10; -fx-cursor: hand;");
        acceptBtn.setOnAction(e -> {
            sendCommand("GROUP_INVITE_RESPONSE:" + groupId + ":ACCEPT:" + inviterUsername);
            dialog.close();
            Platform.runLater(() -> {
                ui.GroupChatWindow gcw = new ui.GroupChatWindow(this, groupId, groupName);
                try { gcw.start(new javafx.stage.Stage()); }
                catch (Exception ex) { System.out.println("Erreur GroupChatWindow : " + ex.getMessage()); }
            });
        });

        javafx.scene.control.Button refuseBtn = new javafx.scene.control.Button("✕  Refuser");
        refuseBtn.setMaxWidth(Double.MAX_VALUE);
        refuseBtn.setPrefHeight(40);
        refuseBtn.setStyle(
                "-fx-background-color: #FDECEA; -fx-text-fill: #DC2626;" +
                        "-fx-background-radius: 10; -fx-cursor: hand;");
        refuseBtn.setOnAction(e -> {
            sendCommand("GROUP_INVITE_RESPONSE:" + groupId + ":REFUSE:" + inviterUsername);
            dialog.close();
        });

        javafx.scene.layout.VBox layout = new javafx.scene.layout.VBox(14,
                icon, title, sub, groupLbl, acceptBtn, refuseBtn);
        layout.setAlignment(javafx.geometry.Pos.CENTER);
        layout.setPadding(new javafx.geometry.Insets(28, 28, 24, 28));
        layout.setStyle("-fx-background-color: white;");

        dialog.setScene(new javafx.scene.Scene(layout, 320, 310));
        dialog.setAlwaysOnTop(true);
        dialog.show();
    }

    private String resolveGroupName(String groupId) {
        if (groupChatWindow != null && groupId.equals(groupChatWindow.getGroupId()))
            return groupChatWindow.getGroupName();
        String[] info = database.GroupDAO.getGroupInfo(groupId);
        return (info != null && info.length > 1) ? info[1] : groupId;
    }

    public static void main(String[] args) {
        Client client = new Client("localhost", 5000);
        LoginWindow loginWindow = new LoginWindow(client);
        client.setLoginWindow(loginWindow);
        client.connect();
    }
}