package model;

import java.util.Date;

// FileMessage hérite de Message.
// Cela signifie que FileMessage EST un Message, mais avec
// des informations supplémentaires sur le fichier partagé.
//
// Héritage en Java = mot clé "extends"
//
// Exemple concret :
//   Message      → "Bonjour !"  (juste du texte)
//   FileMessage  → "rapport.pdf" (texte + infos du fichier)
//
// FileMessage hérite AUTOMATIQUEMENT de Message :
//   ✅ id, content, sender, receiver, timestamp, type, isRead
//   ✅ tous les getters et setters de Message
//   ✅ toString() de Message
// Et ajoute EN PLUS ses propres attributs :
//   ✅ fileName, fileSize, fileType, filePath

public class FileMessage extends Message {

    // Nécessaire pour la sérialisation réseau
    private static final long serialVersionUID = 2L;

    // ============================================================
    // ATTRIBUTS SUPPLÉMENTAIRES (propres à FileMessage)
    // ============================================================

    // Le nom du fichier tel qu'il apparaît (ex: "rapport_final.pdf")
    private String fileName;

    // La taille du fichier en octets (ex: 2048000 = 2 MB)
    // On utilise long car les fichiers peuvent être très grands
    private long fileSize;

    // Le type MIME du fichier (ex: "application/pdf", "image/png")
    // Cela permet de savoir quel programme utiliser pour ouvrir le fichier
    private String fileType;

    // Le chemin complet du fichier sur le disque
    // (ex: "C:\Users\Alice\Documents\rapport.pdf")
    private String filePath;

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    // Pour créer un nouveau FileMessage, on a besoin :
    //   - des infos du Message parent (sender, receiver)
    //   - des infos du fichier (fileName, fileSize, fileType, filePath)
    public FileMessage(User sender, User receiver,
                       String fileName, long fileSize,
                       String fileType, String filePath) {

        // "super(...)" appelle le constructeur de la classe parent (Message)
        // On passe le nom du fichier comme "content" du message
        // et on met le type à FILE automatiquement
        super(fileName, sender, receiver, MessageType.FILE);

        // On initialise les attributs propres à FileMessage
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.filePath = filePath;
    }

    // Constructeur secondaire : pour charger depuis la base de données
    public FileMessage(String id, User sender, User receiver, Date timestamp,
                       String fileName, long fileSize,
                       String fileType, String filePath) {

        // On appelle le constructeur parent avec l'id et timestamp existants
        super(id, fileName, sender, receiver, timestamp, MessageType.FILE);

        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.filePath = filePath;
    }

    // ============================================================
    // GETTERS ET SETTERS
    // ============================================================

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    // ============================================================
    // MÉTHODE UTILITAIRE
    // ============================================================

    // Convertit la taille du fichier en format lisible
    // Exemple : 2048000 → "2.0 MB"
    public String getFileSizeFormatted() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
        }
    }

    // ============================================================
    // MÉTHODE toString()
    // ============================================================
    @Override
    public String toString() {
        return "FileMessage{" +
                "id='" + id + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + getFileSizeFormatted() +
                ", fileType='" + fileType + '\'' +
                ", sender=" + sender.getUsername() +
                ", receiver=" + receiver.getUsername() +
                '}';
    }
}