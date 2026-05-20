package database;

import model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ContactDAO {

    private static Connection getConnection() {
        return DatabaseManager.getConnection();
    }

    public static boolean addContact(String userId, String contactId) {
        String check = "SELECT COUNT(*) FROM contacts WHERE user_id = ? AND contact_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(check);
            stmt.setString(1, userId);
            stmt.setString(2, contactId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) return false;

            String sql = "INSERT INTO contacts (user_id, contact_id) VALUES (?, ?)";
            stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, contactId);
            stmt.executeUpdate();
            System.out.println("Contact ajouté !");
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur ajout contact : " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteContact(String userId, String contactId) {
        String sql = "DELETE FROM contacts WHERE user_id = ? AND contact_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, contactId);
            int rows = stmt.executeUpdate();
            System.out.println("Contact supprimé en BD : " + contactId);
            return rows > 0;
        } catch (SQLException e) {
            System.out.println("Erreur suppression contact : " + e.getMessage());
            return false;
        }
    }

    public static List<User> getContacts(String userId) {
        List<User> contacts = new ArrayList<>();
        String sql = "SELECT u.* FROM users u " +
                "JOIN contacts c ON u.id = c.contact_id " +
                "WHERE c.user_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                User user = new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password")
                );
                user.setOnline(rs.getBoolean("is_online"));
                user.setProfilePhoto(rs.getString("profile_photo"));
                user.setContact(true); // c'est un contact connu
                contacts.add(user);
            }
        } catch (SQLException e) {
            System.out.println("Erreur liste contacts : " + e.getMessage());
        }
        return contacts;
    }

    // ── NOUVEAU : vérifie si targetId est dans les contacts de userId ──
    public static boolean isContact(String userId, String targetId) {
        String sql = "SELECT COUNT(*) FROM contacts WHERE user_id = ? AND contact_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, targetId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.out.println("Erreur isContact : " + e.getMessage());
            return false;
        }
    }

    // ── NOUVEAU : retourne les users qui ont envoyé un message à userId
    //             mais qui NE SONT PAS dans ses contacts (les "inconnus") ──
    // APRÈS
    public static List<User> getStrangerPartners(String userId) {
        List<User> strangers = new ArrayList<>();
        // On cherche tous les expéditeurs de messages reçus par userId
        // qui ne sont pas dans la table contacts de userId
        String sql = "SELECT DISTINCT u.* FROM users u " +
                "JOIN messages m ON m.sender_id = u.id " +
                "WHERE m.receiver_id = ? " +
                "AND u.id != ? " +
                "AND u.id NOT IN (" +
                "    SELECT contact_id FROM contacts WHERE user_id = ?" +
                ")";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            stmt.setString(3, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                User user = new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password")
                );
                user.setOnline(rs.getBoolean("is_online"));
                user.setProfilePhoto(rs.getString("profile_photo"));
                user.setContact(false); // ← INCONNU
                strangers.add(user);
            }
        } catch (SQLException e) {
            System.out.println("Erreur getStrangerPartners : " + e.getMessage());
        }
        return strangers;
    }
}