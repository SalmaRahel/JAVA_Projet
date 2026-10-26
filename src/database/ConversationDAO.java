package database;

import model.Conversation;

import java.sql.*;

public class ConversationDAO {

    private static Connection getConnection() {
        return DatabaseManager.getConnection();
    }

    public static boolean saveConversation(Conversation conversation) {
        String sql = "INSERT INTO conversations (id, participant1_id, participant2_id) VALUES (?, ?, ?)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, conversation.getId());
            stmt.setString(2, conversation.getParticipant1().getId());
            stmt.setString(3, conversation.getParticipant2().getId());
            stmt.executeUpdate();
            System.out.println("Conversation sauvegardée !");
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur sauvegarde conversation : " + e.getMessage());
            return false;
        }
    }
}