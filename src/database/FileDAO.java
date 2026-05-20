package database;

import model.FileMessage;
import model.User;

import java.sql.*;
import java.util.Date;

public class FileDAO {

    private static Connection getConnection() {
        return DatabaseManager.getConnection();
    }

    public static boolean saveFileData(String messageId, byte[] fileData,
                                       String fileName, String fileType, long fileSize) {
        String sql = "INSERT INTO files (message_id, file_data, file_name, file_type, file_size) VALUES (?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, messageId);
            stmt.setBytes(2, fileData);
            stmt.setString(3, fileName);
            stmt.setString(4, fileType);
            stmt.setLong(5, fileSize);
            stmt.executeUpdate();
            System.out.println("Fichier sauvegardé en BD !");
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur sauvegarde fichier : " + e.getMessage());
            return false;
        }
    }

    public static FileMessage getFileData(String messageId, User sender,
                                          User receiver, Date timestamp) {
        String sql = "SELECT * FROM files WHERE message_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, messageId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new FileMessage(
                        messageId, sender, receiver, timestamp,
                        rs.getString("file_name"),
                        rs.getLong("file_size"),
                        rs.getString("file_type"),
                        ""
                ) {{
                    setFileData(rs.getBytes("file_data"));
                }};
            }
        } catch (SQLException e) {
            System.out.println("Erreur récupération fichier : " + e.getMessage());
        }
        return null;
    }
}