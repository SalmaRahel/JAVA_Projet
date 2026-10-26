package database;

import model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    private static Connection getConnection() {
        return DatabaseManager.getConnection();
    }

    public static boolean addUser(User user) {
        String sql = "INSERT INTO users (id, username, password, is_online, profile_photo) VALUES (?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, user.getId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getPassword());
            stmt.setBoolean(4, user.isOnline());
            stmt.setString(5, user.getProfilePhoto());
            stmt.executeUpdate();
            System.out.println("Utilisateur ajouté : " + user.getUsername());
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur ajout utilisateur : " + e.getMessage());
            return false;
        }
    }

    public static User getUserByLogin(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
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
                System.out.println("Login trouvé : " + username);
                return user;
            }
        } catch (SQLException e) {
            System.out.println("Erreur login : " + e.getMessage());
        }
        return null;
    }

    public static void updateUserOnlineStatus(String userId, boolean isOnline) {
        String sql = "UPDATE users SET is_online = ? WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setBoolean(1, isOnline);
            stmt.setString(2, userId);
            stmt.executeUpdate();
            System.out.println("Statut mis à jour : " + userId + " → " + isOnline);
        } catch (SQLException e) {
            System.out.println("Erreur statut : " + e.getMessage());
        }
    }

    public static List<User> getOnlineUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE is_online = TRUE";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
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
            System.out.println("Erreur liste utilisateurs : " + e.getMessage());
        }
        return users;
    }

    public static boolean usernameExists(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.out.println("Erreur vérification username : " + e.getMessage());
        }
        return false;
    }

    public static boolean updateUser(String userId, String newUsername, String newPassword) {
        String sql = "UPDATE users SET username = ?, password = ? WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, newUsername);
            stmt.setString(2, newPassword);
            stmt.setString(3, userId);
            stmt.executeUpdate();
            System.out.println("Profil mis à jour : " + newUsername);
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur mise à jour profil : " + e.getMessage());
            return false;
        }
    }

    public static boolean updateProfilePhoto(String userId, String photoBase64) {
        String sql = "UPDATE users SET profile_photo = ? WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, photoBase64);
            stmt.setString(2, userId);
            stmt.executeUpdate();
            System.out.println("Photo de profil mise à jour !");
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur photo profil : " + e.getMessage());
            return false;
        }
    }

    public static User searchUserByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                User user = new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password")
                );
                user.setOnline(rs.getBoolean("is_online"));
                user.setProfilePhoto(rs.getString("profile_photo"));
                return user;
            }
        } catch (SQLException e) {
            System.out.println("Erreur recherche user : " + e.getMessage());
        }
        return null;
    }

    public static User getUserById(String userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                User u = new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password")
                );
                u.setOnline(rs.getBoolean("is_online"));
                u.setProfilePhoto(rs.getString("profile_photo"));
                return u;
            }
        } catch (SQLException e) {
            System.out.println("Erreur getUserById : " + e.getMessage());
        }
        return null;
    }


}