package database;

import java.sql.*;

public class MeetingDAO {

    private static Connection getConnection() {
        return DatabaseManager.getConnection();
    }

    // Créer une réunion
    public static boolean createMeeting(String meetingId, String groupId,
                                        String hostId, String type) {
        String sql = "INSERT INTO meetings (id, group_id, host_id, status, type) VALUES (?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, meetingId);
            stmt.setString(2, groupId);
            stmt.setString(3, hostId);
            stmt.setString(4, "PENDING");
            stmt.setString(5, type); // "AUDIO" ou "VIDEO"
            stmt.executeUpdate();
            System.out.println("Réunion créée : " + meetingId);
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur création réunion : " + e.getMessage());
            return false;
        }
    }

    // Mettre à jour le statut d'une réunion
    public static void updateMeetingStatus(String meetingId, String status) {
        String sql = "UPDATE meetings SET status = ? WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, status); // PENDING, ACTIVE, ENDED, REFUSED
            stmt.setString(2, meetingId);
            stmt.executeUpdate();
            System.out.println("Statut réunion mis à jour : " + meetingId + " → " + status);
        } catch (SQLException e) {
            System.out.println("Erreur statut réunion : " + e.getMessage());
        }
    }

    // Récupérer les réunions actives d'un groupe
    public static String[] getActiveMeeting(String groupId) {
        String sql = "SELECT * FROM meetings WHERE group_id = ? AND status = 'ACTIVE'";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                // [meetingId, groupId, hostId, status, type]
                return new String[]{
                        rs.getString("id"),
                        rs.getString("group_id"),
                        rs.getString("host_id"),
                        rs.getString("status"),
                        rs.getString("type")
                };
            }
        } catch (SQLException e) {
            System.out.println("Erreur récupération réunion : " + e.getMessage());
        }
        return null;
    }
}