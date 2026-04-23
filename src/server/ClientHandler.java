package server;

import database.DatabaseManager;
import model.*;

import java.io.*;
import java.net.Socket;
import java.util.List;

// ClientHandler gère UN client connecté dans son propre Thread.
// Le serveur crée UN ClientHandler par client connecté.
//
// Analogie simple :
//   Si Server est le réceptionniste de l'hôtel,
//   ClientHandler est l'employé assigné à UN client spécifique.
//   Il s'occupe de tout ce que ce client demande :
//   envoyer des messages, passer des appels, etc.
//
// Il implémente Runnable car il tourne dans un Thread séparé.
// La méthode run() est exécutée automatiquement quand
// le Thread démarre.

public class ClientHandler implements Runnable {

    // ============================================================
    // ATTRIBUTS
    // ============================================================

    // Le Socket = le canal de communication avec CE client
    private Socket socket;

    // L'utilisateur que ce ClientHandler représente
    private User user;

    // Référence au serveur principal
    private Server server;

    // ObjectOutputStream = flux pour ENVOYER des objets Java au client
    // On envoie des objets Message, User, List<User> etc.
    private ObjectOutputStream out;

    // ObjectInputStream = flux pour RECEVOIR des objets Java du client
    private ObjectInputStream in;

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.user = null; // Sera défini après le login
    }

    // ============================================================
    // MÉTHODE run() — Cœur du ClientHandler
    // ============================================================

    // Cette méthode est appelée automatiquement quand le Thread démarre
    // Elle tourne en boucle tant que le client est connecté
    @Override
    public void run() {
        try {
            // Étape 1 : Créer les flux d'entrée et de sortie
            // IMPORTANT : out doit être créé AVANT in
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // Vider le buffer immédiatement
            in = new ObjectInputStream(socket.getInputStream());

            System.out.println("🔗 Nouveau client en attente de login...");

            // Étape 2 : Attendre le login du client
            // Le premier objet reçu est toujours un User (login)
            Object firstObject = in.readObject();

            if (firstObject instanceof User) {
                this.user = (User) firstObject;

                // Vérifier le login dans la base de données
                User dbUser = DatabaseManager.getUserByLogin(
                        user.getUsername(),
                        user.getPassword()
                );

                if (dbUser != null) {
                    // Login réussi
                    this.user = dbUser;
                    this.user.setOnline(true);

                    // Mettre à jour le statut dans la BD
                    DatabaseManager.updateUserOnlineStatus(
                            user.getId(), true
                    );

                    // Enregistrer ce client dans le serveur
                    server.addClient(user.getUsername(), this);

                    // Envoyer confirmation au client
                    sendObject("LOGIN_SUCCESS");
                    sendObject(dbUser);
                    sendUserList(server.getConnectedUsers());
                    System.out.println("✅ Login réussi : "
                            + user.getUsername());

                } else {
                    // Login échoué
                    sendObject("LOGIN_FAILED");
                    System.out.println("❌ Login échoué !");
                    closeConnection();
                    return;
                }
            }

            // Étape 3 : Boucle principale — écouter les messages du client
            // On reste dans cette boucle tant que le client est connecté
            while (true) {
                // Lire le prochain objet envoyé par le client
                Object received = in.readObject();

                // Traiter l'objet reçu selon son type
                if (received instanceof Message) {
                    // C'est un message texte ou fichier
                    handleMessage((Message) received);

                } else if (received instanceof String) {
                    // C'est une commande texte
                    handleCommand((String) received);

                } else if (received instanceof call.Call) {
                    // C'est un appel audio ou vidéo
                    handleCall((call.Call) received);
                }
            }

        } catch (EOFException e) {
            // Le client s'est déconnecté normalement
            System.out.println("👤 Client déconnecté : "
                    + (user != null ? user.getUsername() : "inconnu"));

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("❌ Erreur client : " + e.getMessage());

        } finally {
            // Dans tous les cas, on ferme la connexion proprement
            closeConnection();
        }
    }

    // ============================================================
    // MÉTHODES DE TRAITEMENT
    // ============================================================

    // Traiter un message reçu du client
    private void handleMessage(Message message) {
        System.out.println("📨 Message reçu de "
                + message.getSender().getUsername()
                + " → " + message.getReceiver().getUsername()
                + " : " + message.getContent());

        // Transmettre le message au destinataire via le serveur
        server.broadcast(message, this);
    }

    // Traiter une commande reçue du client
    private void handleCommand(String command) {
        System.out.println("⚙️ Commande reçue : " + command);

        if (command.equals("GET_USERS")) {
            // Le client demande la liste des utilisateurs connectés
            sendUserList(server.getConnectedUsers());

        } else if (command.equals("DISCONNECT")) {
            // Le client veut se déconnecter
            closeConnection();
        }
    }

    // Traiter un appel reçu du client
    public void handleCall(call.Call call) {
        System.out.println("📞 Appel de "
                + call.getCaller().getUsername()
                + " → " + call.getCalled().getUsername()
                + " | Statut : " + call.getStatus());

        if (call.getStatus() == model.CallStatus.PENDING) {
            // Nouvel appel → envoyer au receiver
            DatabaseManager.saveCall(
                    call.getId(),
                    call.getCaller().getId(),
                    call.getCalled().getId(),
                    call.getCallType().name(),
                    call.getStatus().name()
            );
            server.forwardCall(call, call.getCalled().getUsername());

        } else if (call.getStatus() == model.CallStatus.ACTIVE) {
            // Appel accepté → notifier l'appelant
            server.forwardCall(call, call.getCaller().getUsername());

        } else {
            // Appel refusé ou terminé → mettre à jour BD + notifier l'appelant
            DatabaseManager.updateCallStatus(
                    call.getId(), call.getStatus().name()
            );
            server.forwardCall(call, call.getCaller().getUsername());
        }
    }

    // Traiter un appel entrant
    public void handleIncomingCall() {
        System.out.println("📞 Appel entrant pour : "
                + user.getUsername());
    }

    // ============================================================
    // MÉTHODES D'ENVOI
    // ============================================================

    // Envoyer un message à CE client
    public void sendMessage(Message message) {
        sendObject(message);
    }

    // Envoyer la liste des utilisateurs connectés à CE client
    public void sendUserList(List<User> users) {
        sendObject(users);
    }

    // Envoyer des données audio à CE client
    public void sendAudioData(byte[] data) {
        sendObject(data);
    }

    // Envoyer des données vidéo à CE client
    public void sendVideoData(byte[] data) {
        sendObject(data);
    }

    // Méthode générique pour envoyer n'importe quel objet
    // Tous les objets envoyés passent par cette méthode
    public void sendObject(Object obj) {
        try {
            if (out != null) {
                out.writeObject(obj);
                out.flush(); // S'assurer que l'objet est envoyé immédiatement
                out.reset(); // Éviter les problèmes de cache Java
            }
        } catch (IOException e) {
            System.out.println("❌ Erreur envoi objet : "
                    + e.getMessage());
        }
    }

    // ============================================================
    // MÉTHODE DE DÉCONNEXION
    // ============================================================

    // Fermer proprement la connexion du client
    public void closeConnection() {
        try {
            // Retirer le client du serveur
            if (user != null) {
                server.removeClient(user);
            }

            // Fermer les flux
            if (in != null) in.close();
            if (out != null) out.close();

            // Fermer le socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            System.out.println("🔌 Connexion fermée !");

        } catch (IOException e) {
            System.out.println("❌ Erreur fermeture : " + e.getMessage());
        }
    }

    // ============================================================
    // GETTER
    // ============================================================

    // Retourner l'utilisateur associé à ce ClientHandler
    // Utilisé par Server pour construire la liste des connectés
    public User getUser() {
        return user;
    }

    // Méthode pour envoyer un message (interface Serializable)
    public void sendMessage(model.Message message,
                            boolean serializable) {
        sendObject(message);
    }
}