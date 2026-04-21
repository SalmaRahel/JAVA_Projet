package database;

import model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// DatabaseManager est la classe qui gère TOUTE la communication
// entre Java et la base de données MySQL.
//
// Elle est organisée en sections :
//   1. Connexion / Déconnexion
//   2. Gestion des Utilisateurs
//   3. Gestion des Messages
//   4. Gestion des Appels
//   5. Gestion des Conversations
//
// Tous les attributs et méthodes sont "static" car on n'a besoin
// que d'UNE SEULE connexion à la base de données pour tout le
// programme. Pas besoin de créer plusieurs objets DatabaseManager.

public class DatabaseManager {

    // ============================================================
    // CONNEXION
    // ============================================================

    // Informations de connexion à MySQL via XAMPP
    private static final String URL =
            "jdbc:mysql://localhost:3306/whatsapp_db";
    private static final String USERNAME = "root";
    private static final String PASSWORD = ""; // Vide par défaut avec XAMPP

    // La connexion unique à MySQL
    private static Connection connection = null;

    // ============================================================
    // MÉTHODES DE CONNEXION
    // ============================================================

    // Ouvrir la connexion à MySQL
    // Appelée UNE SEULE FOIS au démarrage du serveur
    public static void connect() {
        try {
            // Charger le driver MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Créer la connexion
            connection = DriverManager.getConnection(
                    URL, USERNAME, PASSWORD
            );

            System.out.println("✅ MySQL connecté !");

        } catch (ClassNotFoundException e) {
            System.out.println("❌ Driver MySQL introuvable : "
                    + e.getMessage());
        } catch (SQLException e) {
            System.out.println("❌ Erreur connexion MySQL : "
                    + e.getMessage());
        }
    }

    // Fermer la connexion à MySQL
    // Appelée quand le serveur s'arrête
    public static void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✅ MySQL déconnecté !");
            }
        } catch (SQLException e) {
            System.out.println("❌ Erreur déconnexion MySQL : "
                    + e.getMessage());
        }
    }

    // ============================================================
    // GESTION DES UTILISATEURS
    // ============================================================

    // Ajouter un nouvel utilisateur dans la BD (inscription)
    public static boolean addUser(User user) {
        String sql = "INSERT INTO users " +
                "(id, username, password, is_online, profile_photo) " +
                "VALUES (?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt =
                    connection.prepareStatement(sql);
            stmt.setString(1, user.getId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getPassword());
            stmt.setBoolean(4, user.isOnline());
            stmt.setString(5, user.getProfilePhoto());
            stmt.executeUpdate();
            System.out.println("✅ Utilisateur ajouté : "
                    + user.getUsername());
            return true;

        } catch (SQLException e) {
            System.out.println("❌ Erreur ajout utilisateur : "
                    + e.getMessage());
            return false;
        }
    }

    // Vérifier le login d'un utilisateur (connexion)
    // Retourne l'objet User si trouvé, null sinon
    public static User getUserByLogin(String username,
                                      String password) {
        String sql = "SELECT * FROM users " +
                "WHERE username = ? AND password = ?";
        try {
            PreparedStatement stmt =
                    connection.prepareStatement(sql);
            stmt.setString(1, username);
            stmt.setString(2, password);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                User user = new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password")
                );
                user.setOnline(rs.getBoolean("is_online"));
                user.setProfilePhoto(rs.getString("profile_photo"));
                System.out.println("✅ Login trouvé : " + username);
                return user;
            }

        } catch (SQLException e) {
            System.out.println("❌ Erreur login : "
                    + e.getMessage());
        }
        return null; // Utilisateur non trouvé
    }

    // Mettre à jour le statut en ligne d'un utilisateur
    public static void updateUserOnlineStatus(String userId,
                                              boolean isOnline) {
        String sql = "UPDATE users SET is_online = ? WHERE id = ?";
        try {
            PreparedStatement stmt =
                    connection.prepareStatement(sql);
            stmt.setBoolean(1, isOnline);
            stmt.setString(2, userId);
            stmt.executeUpdate();
            System.out.println("✅ Statut mis à jour : "
                    + userId + " → " + isOnline);

        } catch (SQLException e) {
            System.out.println("❌ Erreur statut : "
                    + e.getMessage());
        }
    }

    // Récupérer tous les utilisateurs en ligne
    public static List<User> getOnlineUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE is_online = TRUE";

        try {
            PreparedStatement stmt =
                    connection.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                User user = new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password")
                );
                user.setOnline(true);
                user.setProfilePhoto(rs.getString("profile_photo"));
                users.add(user);
            }

        } catch (SQLException e) {
            System.out.println("❌ Erreur liste utilisateurs : "
                    + e.getMessage());
        }
        return users;
    }

    // Vérifier si un username existe déjà dans la BD
    public static boolean usernameExists(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";
        try {
            PreparedStatement stmt =
                    connection.prepareStatement(sql);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next(); // true si une ligne trouvée

        } catch (SQLException e) {
            System.out.println("❌ Erreur vérification username : "
                    + e.getMessage());
        }
        return false;
    }

    // ============================================================
    // GESTION DES MESSAGES
    // ============================================================

    // Sauvegarder un message dans la BD
    public static boolean saveMessage(Message message) {
        String sql = "INSERT INTO messages " +
                "(id, content, type, sender_id, " +
                "receiver_id, timestamp, is_read) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt =
                    connection.prepareStatement(sql);
            stmt.setString(1, message.getId());
            stmt.setString(2, message.getContent());
            stmt.setString(3, message.getType().name());
            stmt.setString(4, message.getSender().getId());
            stmt.setString(5, message.getReceiver().getId());
            stmt.setTimestamp(6,
                    new Timestamp(message.getTimestamp().getTime()));
            stmt.setBoolean(7, false);
            stmt.executeUpdate();
            System.out.println("✅ Message sauvegardé !");
            return true;

        } catch (SQLException e) {
            System.out.println("❌ Erreur sauvegarde message : "
                    + e.getMessage());
            return false;
        }
    }

    // Récupérer l'historique des messages entre deux utilisateurs
    public static List<Message> getMessageHistory(String userId1,
                                                  String userId2) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT m.*, " +
                "u1.username as sender_name, " +
                "u1.id as sender_id_real, " +
                "u2.username as receiver_name, " +
                "u2.id as receiver_id_real " +
                "FROM messages m " +
                "JOIN users u1 ON m.sender_id = u1.id " +
                "JOIN users u2 ON m.receiver_id = u2.id " +
                "WHERE (m.sender_id = ? AND m.receiver_id = ?) " +
                "OR (m.sender_id = ? AND m.receiver_id = ?) " +
                "ORDER BY m.timestamp ASC";
        try {
            PreparedStatement stmt =
                    connection.prepareStatement(sql);
            stmt.setString(1, userId1);
            stmt.setString(2, userId2);
            stmt.setString(3, userId2);
            stmt.setString(4, userId1);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                User sender = new User(
                        rs.getString("sender_id_real"),
                        rs.getString("sender_name"),
                        ""
                );
                User receiver = new User(
                        rs.getString("receiver_id_real"),
                        rs.getString("receiver_name"),
                        ""
                );

                Message message = new Message(
                        rs.getString("id"),
                        rs.getString("content"),
                        sender,
                        receiver,
                        new Date(rs.getTimestamp("timestamp")
                                .getTime()),
                        MessageType.valueOf(rs.getString("type"))
                );
                messages.add(message);
            }

        } catch (SQLException e) {
            System.out.println("❌ Erreur historique messages : "
                    + e.getMessage());
        }
        return messages;
    }

    // ============================================================
    // GESTION DES APPELS
    // ============================================================

    // Sauvegarder un appel dans la BD
    public static boolean saveCall(String callId,
                                   String callerId,
                                   String calledId,
                                   String type,
                                   String status) {
        String sql = "INSERT INTO calls " +
                "(id, caller_id, called_id, " +
                "type, status, start_time) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt =
                    connection.prepareStatement(sql);
            stmt.setString(1, callId);
            stmt.setString(2, callerId);
            stmt.setString(3, calledId);
            stmt.setString(4, type);
            stmt.setString(5, status);
            stmt.setTimestamp(6,
                    new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
            System.out.println("✅ Appel sauvegardé !");
            return true;

        } catch (SQLException e) {
            System.out.println("❌ Erreur sauvegarde appel : "
                    + e.getMessage());
            return false;
        }
    }

    // Mettre à jour le statut d'un appel
    public static void updateCallStatus(String callId,
                                        String status) {
        String sql = "UPDATE calls " +
                "SET status = ?, end_time = ? " +
                "WHERE id = ?";
        try {
            PreparedStatement stmt =
                    connection.prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setTimestamp(2,
                    new Timestamp(System.currentTimeMillis()));
            stmt.setString(3, callId);
            stmt.executeUpdate();
            System.out.println("✅ Statut appel mis à jour !");

        } catch (SQLException e) {
            System.out.println("❌ Erreur statut appel : "
                    + e.getMessage());
        }
    }

    // ============================================================
    // GESTION DES CONVERSATIONS
    // ============================================================

    // Sauvegarder une conversation dans la BD
    public static boolean saveConversation(
            Conversation conversation) {
        String sql = "INSERT INTO conversations " +
                "(id, participant1_id, participant2_id) " +
                "VALUES (?, ?, ?)";
        try {
            PreparedStatement stmt =
                    connection.prepareStatement(sql);
            stmt.setString(1, conversation.getId());
            stmt.setString(2,
                    conversation.getParticipant1().getId());
            stmt.setString(3,
                    conversation.getParticipant2().getId());
            stmt.executeUpdate();
            System.out.println("✅ Conversation sauvegardée !");
            return true;

        } catch (SQLException e) {
            System.out.println("❌ Erreur sauvegarde conversation : "
                    + e.getMessage());
            return false;
        }
    }
}