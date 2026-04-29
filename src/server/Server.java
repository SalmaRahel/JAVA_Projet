package server;

import database.DatabaseManager;
import model.Conversation;
import model.Message;
import model.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Server est le programme principal qui tourne en arrière-plan.
// Il attend les connexions des clients et crée un thread
// (ClientHandler) pour chaque client connecté.
//
// Analogie simple :
//   Le Server est comme un RÉCEPTIONNISTE d'hôtel.
//   Quand un client arrive (se connecte), le réceptionniste
//   lui assigne un employé (ClientHandler) qui s'occupe
//   uniquement de lui pendant tout son séjour.
//
// Architecture :
//   Server (1 seul)
//     ├── ClientHandler pour Alice  (Thread 1)
//     ├── ClientHandler pour Bob    (Thread 2)
//     └── ClientHandler pour Carol  (Thread 3)

public class Server {

    // ============================================================
    // ATTRIBUTS
    // ============================================================

    // Le port sur lequel le serveur écoute les connexions
    private int port;

    // ServerSocket = la "porte d'entrée" du serveur
    private ServerSocket serverSocket;

    // true = le serveur tourne, false = le serveur est arrêté
    private boolean isRunning;

    // Map qui associe chaque username à son ClientHandler
    // Clé = username (String), Valeur = ClientHandler
    private Map<String, ClientHandler> clientMap;

    // Liste de toutes les conversations actives
    private List<Conversation> conversations;

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    public Server(int port) {
        this.port = port;
        this.isRunning = false;
        this.clientMap = new HashMap<>();
        this.conversations = new ArrayList<>();
    }

    // ============================================================
    // MÉTHODES PRINCIPALES
    // ============================================================

    // Démarrer le serveur
    public void start() {
        try {
            // Étape 1 : Connexion à MySQL
            DatabaseManager.connect();
            System.out.println("Base de données connectée !");

            // Étape 2 : Création du ServerSocket
            serverSocket = new ServerSocket(port);
            isRunning = true;
            System.out.println("Serveur démarré sur le port " + port);
            System.out.println("En attente de connexions...");

            // Étape 3 : Boucle infinie d'attente des clients
            // accept() est bloquante : elle attend qu'un client
            // se connecte avant de continuer
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("🔗 Nouveau client connecté : "
                        + clientSocket.getInetAddress());

                // On crée un ClientHandler pour ce client
                // et on le lance dans un nouveau Thread
                ClientHandler handler = new ClientHandler(clientSocket, this);
                Thread thread = new Thread(handler);
                thread.start();
            }

        } catch (IOException e) {
            if (isRunning) {
                System.out.println(" Erreur serveur : " + e.getMessage());
            }
        }
    }

    // Arrêter le serveur proprement
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

    // ============================================================
    // MÉTHODES DE GESTION DES CLIENTS
    // ============================================================

    // Enregistrer un nouveau client connecté
    public synchronized void addClient(String username,
                                       ClientHandler handler) {
        clientMap.put(username, handler);
        System.out.println("Utilisateur connecté : " + username);
        System.out.println("Total connectés : " + clientMap.size());
        notifyUserList();
    }

    // Supprimer un client déconnecté
    public synchronized void removeClient(User user) {
        clientMap.remove(user.getUsername());
        System.out.println(" Déconnecté : " + user.getUsername());
        DatabaseManager.updateUserOnlineStatus(user.getId(), false);
        notifyUserList();
    }

    // Récupérer la liste de tous les utilisateurs connectés
    public synchronized List<User> getConnectedUsers() {
        List<User> users = new ArrayList<>();
        for (ClientHandler handler : clientMap.values()) {
            users.add(handler.getUser());
        }
        return users;
    }

    // Envoyer un message à un destinataire spécifique
    public synchronized void broadcast(Message message,
                                       ClientHandler sender) {
        String receiverUsername = message.getReceiver().getUsername();
        ClientHandler receiverHandler = clientMap.get(receiverUsername);

        if (receiverHandler != null) {
            receiverHandler.sendMessage(message);
            System.out.println(" Message : "
                    + message.getSender().getUsername()
                    + " → " + receiverUsername);
        } else {
            System.out.println("Destinataire non connecté : "
                    + receiverUsername);
        }

        System.out.println("Sauvegarde message : "
                + message.getSender().getId()
                + " → " + message.getReceiver().getId());



        // Sauvegarder dans la base de données
        // Ne pas sauvegarder les FileMessage (trop lourds) ni les commandes
        if (!(message instanceof model.FileMessage) &&
                message.getType() != model.MessageType.FILE) {
            DatabaseManager.saveMessage(message);
        } else if (message instanceof model.FileMessage) {
            model.FileMessage fm = (model.FileMessage) message;
            // Sauvegarder les infos du fichier dans messages
            DatabaseManager.saveMessage(message);
            // Sauvegarder les bytes dans files
            if (fm.getFileData() != null && fm.getFileData().length > 0) {
                DatabaseManager.saveFileData(
                        fm.getId(), fm.getFileData(),
                        fm.getFileName(), fm.getFileType(), fm.getFileSize()
                );
            }
        }
    }

    // Transmettre un appel à un utilisateur cible
    public synchronized void forwardCall(call.Call call, String targetUsername) {
        ClientHandler targetHandler = clientMap.get(targetUsername);

        if (targetHandler != null) {
            targetHandler.sendObject(call);
            System.out.println("Appel transmis à : " + targetUsername
                    + " | Statut : " + call.getStatus());
        } else {
            System.out.println(" Utilisateur cible non connecté : "
                    + targetUsername);
        }
    }

    // Envoyer des données audio à tous sauf l'expéditeur
    public synchronized void broadcastAudio(byte[] data,
                                            ClientHandler sender) {
        for (Map.Entry<String, ClientHandler> entry
                : clientMap.entrySet()) {
            if (!entry.getValue().equals(sender)) {
                entry.getValue().sendAudioData(data);
            }
        }
    }

    // Envoyer des données vidéo à tous sauf l'expéditeur
    public synchronized void broadcastVideo(byte[] data,
                                            ClientHandler sender) {
        for (Map.Entry<String, ClientHandler> entry
                : clientMap.entrySet()) {
            if (!entry.getValue().equals(sender)) {
                entry.getValue().sendVideoData(data);
            }
        }
    }

    // Informer tous les clients de la liste des utilisateurs connectés
    public synchronized void notifyUserList() {
        List<User> connectedUsers = getConnectedUsers();
        for (ClientHandler handler : clientMap.values()) {
            handler.sendUserList(connectedUsers);
        }
    }

    // ============================================================
    // MÉTHODE MAIN — Point d'entrée du serveur
    // ============================================================

    public static void main(String[] args) {
        Server server = new Server(5000);
        server.start();
    }
}