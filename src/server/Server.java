package server;

import database.*;
import model.Message;
import model.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;


public class Server {

    private int port;
    private ServerSocket serverSocket;
    private boolean isRunning;
    private Map<String, ClientHandler> clientMap;
    private Map<String, Set<String>> activeMeetings = new HashMap<>();

    public Server(int port) {
        this.port = port;
        this.isRunning = false;
        this.clientMap = new HashMap<>();
    }

    public void start() {
        try {
            DatabaseManager.connect();
            System.out.println("Base de données connectée !");

            serverSocket = new ServerSocket(port);
            isRunning = true;
            System.out.println("Serveur démarré sur le port " + port);
            System.out.println("En attente de connexions...");

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nouveau client connecté : " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket, this);
                Thread thread = new Thread(handler);
                thread.start();
            }

        } catch (IOException e) {
            if (isRunning) {
                System.out.println("Erreur serveur : " + e.getMessage());
            }
        }
    }

    public void stop() {
        try {
            isRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            DatabaseManager.disconnect();
            System.out.println("Serveur arrêté !");
        } catch (IOException e) {
            System.out.println("Erreur arrêt serveur : " + e.getMessage());
        }
    }

    public synchronized void addClient(String username, ClientHandler handler) {
        clientMap.put(username, handler);
        System.out.println("Utilisateur connecté : " + username);
        System.out.println("Total connectés : " + clientMap.size());
        notifyUserList();
    }

    public synchronized void removeClient(User user) {
        clientMap.remove(user.getUsername());
        System.out.println("Déconnecté : " + user.getUsername());
        UserDAO.updateUserOnlineStatus(user.getId(), false);
        notifyUserList();
    }

    public synchronized List<User> getConnectedUsers() {
        List<User> users = new ArrayList<>();
        for (ClientHandler handler : clientMap.values()) {
            users.add(handler.getUser());
        }
        return users;
    }

    public synchronized void broadcast(Message message, ClientHandler sender) {
        String receiverUsername = message.getReceiver().getUsername();
        ClientHandler receiverHandler = clientMap.get(receiverUsername);

        if (receiverHandler != null) {
            receiverHandler.sendMessage(message);
            System.out.println("Message : "
                    + message.getSender().getUsername()
                    + " → " + receiverUsername);
        } else {
            System.out.println("Destinataire non connecté : " + receiverUsername);
        }

        if (!(message instanceof model.FileMessage) &&
                message.getType() != model.MessageType.FILE) {
            MessageDAO.saveMessage(message);
        } else if (message instanceof model.FileMessage) {
            model.FileMessage fm = (model.FileMessage) message;
            MessageDAO.saveMessage(message);
            if (fm.getFileData() != null && fm.getFileData().length > 0) {
                FileDAO.saveFileData(
                        fm.getId(), fm.getFileData(),
                        fm.getFileName(), fm.getFileType(), fm.getFileSize()
                );
            }
        }
    }

    public synchronized void forwardCall(call.Call call, String targetUsername) {
        ClientHandler targetHandler = clientMap.get(targetUsername);
        if (targetHandler != null) {
            targetHandler.sendObject(call);
            System.out.println("Appel transmis à : " + targetUsername
                    + " | Statut : " + call.getStatus());
        } else {
            System.out.println("Utilisateur cible non connecté : " + targetUsername);
        }
    }

    public synchronized void broadcastAudio(byte[] data, ClientHandler sender, String targetUsername) {
        ClientHandler target = clientMap.get(targetUsername);
        if (target != null && !target.equals(sender)) {
            target.sendAudioData(data);
        }
    }

    public synchronized void broadcastVideo(byte[] data, ClientHandler sender, String targetUsername) {
        ClientHandler target = clientMap.get(targetUsername);
        if (target != null && !target.equals(sender)) {
            target.sendVideoData(data);
        }
    }

    public synchronized ClientHandler getClientHandler(String username) {
        return clientMap.get(username);
    }

    public synchronized void notifyUserList() {
        List<User> connectedUsers = getConnectedUsers();
        for (ClientHandler handler : clientMap.values()) {
            handler.sendUserList(connectedUsers);
        }
    }

    public void broadcastCommand(String command, String targetUsername) {
        ClientHandler target = clientMap.get(targetUsername);
        if (target != null) {
            target.sendObject(command);
        }
    }

    public boolean isUserConnected(String username) {
        return clientMap.containsKey(username);
    }

    // ═══════════════════════════════════════════════════════════════
// À AJOUTER dans Server.java
// ═══════════════════════════════════════════════════════════════

    // Retrouver le username d'un user par son ID
    // Utilisé pour les invitations de groupe
    public synchronized String getUsernameById(String userId) {
        for (ClientHandler handler : clientMap.values()) {
            if (handler.getUser() != null && handler.getUser().getId().equals(userId)) {
                return handler.getUser().getUsername();
            }
        }
        // Si pas connecté → chercher en BD
        try {
            java.lang.reflect.Field f = database.DatabaseManager.class.getDeclaredField("connection");
            f.setAccessible(true);
            java.sql.Connection conn = (java.sql.Connection) f.get(null);
            if (conn != null) {
                java.sql.PreparedStatement stmt = conn.prepareStatement(
                        "SELECT username FROM users WHERE id = ?"
                );
                stmt.setString(1, userId);
                java.sql.ResultSet rs = stmt.executeQuery();
                if (rs.next()) return rs.getString("username");
            }
        } catch (Exception e) {
            System.out.println("Erreur getUsernameById : " + e.getMessage());
        }
        return null;
    }

    public synchronized void joinMeeting(String meetingId, String username) {
        activeMeetings.computeIfAbsent(meetingId, k -> new HashSet<>()).add(username);
        System.out.println("[MEETING] " + username + " rejoint " + meetingId);
    }

    public synchronized void leaveMeeting(String meetingId, String username) {
        Set<String> p = activeMeetings.get(meetingId);
        if (p != null) { p.remove(username); if (p.isEmpty()) activeMeetings.remove(meetingId); }
    }

    public synchronized Set<String> getMeetingParticipants(String meetingId) {
        Set<String> p = activeMeetings.get(meetingId);
        return p != null ? new HashSet<>(p) : new HashSet<>();
    }

    public synchronized void broadcastMeetingAudio(String meetingId, byte[] audioData, ClientHandler sender) {
        Set<String> participants = activeMeetings.get(meetingId);
        if (participants == null) return;
        for (String username : participants) {
            if (username.equals(sender.getUser().getUsername())) continue;
            ClientHandler target = clientMap.get(username);
            if (target != null) target.sendAudioData(audioData);
        }
    }

    public synchronized void broadcastMeetingVideo(String meetingId, byte[] jpegData, ClientHandler sender) {
        Set<String> participants = activeMeetings.get(meetingId);
        if (participants == null) return;
        String senderName = sender.getUser().getUsername();
        byte[] prefixBytes;
        try { prefixBytes = ("MVFRAME:" + senderName + ":").getBytes("UTF-8"); }
        catch (Exception e) { return; }
        byte[] packet = new byte[prefixBytes.length + jpegData.length];
        System.arraycopy(prefixBytes, 0, packet, 0, prefixBytes.length);
        System.arraycopy(jpegData,   0, packet, prefixBytes.length, jpegData.length);
        for (String username : participants) {
            if (username.equals(senderName)) continue;
            ClientHandler target = clientMap.get(username);
            if (target != null) target.sendVideoData(packet);
        }
    }

    public static void main(String[] args) {
        Server server = new Server(5000);
        server.start();
    }
}