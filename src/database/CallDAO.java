package database;

import java.sql.*;

public class CallDAO {

    private static Connection getConnection() {
        return DatabaseManager.getConnection();
    }

    public static boolean saveCall(String callId, String callerId,
                                   String calledId, String type, String status) {
        String sql = "INSERT INTO calls (id, caller_id, called_id, type, status, start_time) VALUES (?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, callId);
            stmt.setString(2, callerId);
            stmt.setString(3, calledId);
            stmt.setString(4, type);
            stmt.setString(5, status);
            stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
            System.out.println("Appel sauvegardé !");
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur sauvegarde appel : " + e.getMessage());
            return false;
        }
    }

    public static void updateCallStatus(String callId, String status) {
        String sql = "UPDATE calls SET status = ?, end_time = ? WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setString(3, callId);
            stmt.executeUpdate();
            System.out.println("Statut appel mis à jour !");
        } catch (SQLException e) {
            System.out.println("Erreur statut appel : " + e.getMessage());
        }
    }
}