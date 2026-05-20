package database;

import model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MessageDAO {

    private static Connection getConnection() {
        return DatabaseManager.getConnection();
    }

    public static boolean saveMessage(Message message) {
        String sql = "INSERT INTO messages (id, content, type, sender_id, receiver_id, timestamp, delivery_status, reply_to_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, message.getId());
            stmt.setString(2, message.getContent());
            stmt.setString(3, message.getType().name());
            stmt.setString(4, message.getSender().getId());
            stmt.setString(5, message.getReceiver().getId());
            stmt.setTimestamp(6, new Timestamp(message.getTimestamp().getTime()));
            stmt.setString(7, "SENT");
            stmt.setString(8, message.getReplyToId());
            stmt.executeUpdate();
            System.out.println("Message sauvegardé !");
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur sauvegarde message : " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static List<Message> getMessageHistory(String userId1, String userId2) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT m.*, " +
                "u1.username as sender_name, u1.id as sender_id_real, " +
                "u2.username as receiver_name, u2.id as receiver_id_real " +
                "FROM messages m " +
                "JOIN users u1 ON m.sender_id = u1.id " +
                "JOIN users u2 ON m.receiver_id = u2.id " +
                "WHERE (m.sender_id = ? AND m.receiver_id = ?) " +
                "OR (m.sender_id = ? AND m.receiver_id = ?) " +
                "ORDER BY m.timestamp ASC";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId1);
            stmt.setString(2, userId2);
            stmt.setString(3, userId2);
            stmt.setString(4, userId1);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                User sender = new User(rs.getString("sender_id_real"), rs.getString("sender_name"), "");
                User receiver = new User(rs.getString("receiver_id_real"), rs.getString("receiver_name"), "");
                MessageType type = MessageType.valueOf(rs.getString("type"));

                if (type == MessageType.FILE) {
                    if (rs.getBoolean("is_deleted")) continue;
                    FileMessage fm = FileDAO.getFileData(
                            rs.getString("id"), sender, receiver,
                            new Date(rs.getTimestamp("timestamp").getTime())
                    );
                    if (fm != null) {
                        fm.setPinned(rs.getBoolean("is_pinned"));
                        messages.add(fm);
                    }
                } else if (type == MessageType.VOICE) {
                    if (rs.getBoolean("is_deleted")) continue;
                    String base64 = rs.getString("content");
                    byte[] audioData = new byte[0];
                    try {
                        audioData = java.util.Base64.getDecoder().decode(base64);
                    } catch (Exception ignored) {}
                    int duration = Math.max(1, audioData.length / 96000);
                    VoiceMessage vm = new VoiceMessage(
                            rs.getString("id"), sender, receiver,
                            new Date(rs.getTimestamp("timestamp").getTime()),
                            audioData, duration
                    );
                    vm.setPinned(rs.getBoolean("is_pinned"));
                    messages.add(vm);
                } else {
                    Message message = new Message(
                            rs.getString("id"),
                            rs.getString("content"),
                            sender, receiver,
                            new Date(rs.getTimestamp("timestamp").getTime()),
                            type
                    );
                    message.setDeleted(rs.getBoolean("is_deleted"));
                    message.setEdited(rs.getBoolean("is_edited"));
                    message.setPinned(rs.getBoolean("is_pinned"));
                    message.setReplyToId(rs.getString("reply_to_id"));
                    String ds = rs.getString("delivery_status");
                    message.setDeliveryStatus(ds != null ? ds : "SENT");
                    messages.add(message);
                }
            }
        } catch (SQLException e) {
            System.out.println("Erreur historique messages : " + e.getMessage());
        }
        return messages;
    }

    public static boolean deleteMessage(String messageId) {
        String sql = "UPDATE messages SET is_deleted = 1 WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, messageId);
            stmt.executeUpdate();
            System.out.println("Message supprimé en BD : " + messageId);
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur suppression message : " + e.getMessage());
            return false;
        }
    }

    public static boolean editMessage(String messageId, String newContent) {
        String sql = "UPDATE messages SET content = ?, is_edited = 1 WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, newContent);
            stmt.setString(2, messageId);
            stmt.executeUpdate();
            System.out.println("Message modifié en BD : " + messageId);
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur modification message : " + e.getMessage());
            return false;
        }
    }

    public static boolean pinMessage(String messageId, boolean pinned) {
        String sql = "UPDATE messages SET is_pinned = ? WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setBoolean(1, pinned);
            stmt.setString(2, messageId);
            stmt.executeUpdate();
            System.out.println("Message épinglé=" + pinned + " en BD : " + messageId);
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur épinglage message : " + e.getMessage());
            return false;
        }
    }

    public static void updateDeliveryStatus(String messageId, String status) {
        String sql = "UPDATE messages SET delivery_status = ? WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setString(2, messageId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Erreur delivery status : " + e.getMessage());
        }
    }
}