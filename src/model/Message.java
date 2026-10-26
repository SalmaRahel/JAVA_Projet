package model;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

// Message est la classe de base pour tous les messages de l'application.
// Elle est Serializable pour pouvoir être envoyée via les Sockets TCP.


public class Message implements Serializable {

    // Nécessaire pour la sérialisation réseau
    // C'est un identifiant de version de la classe
    private static final long serialVersionUID = 1L;

    // ============================================================
    // ATTRIBUTS (correspondent exactement au diagramme de classes)
    // ============================================================

    // Identifiant unique du message
    protected String id;

    // Le contenu textuel du message (ex: "Bonjour !")
    protected String content;

    // L'utilisateur qui envoie le message
    protected User sender;

    // L'utilisateur qui reçoit le message
    protected User receiver;

    // La date et heure d'envoi du message
    protected Date timestamp;

    // Le type du message (TEXT, FILE, PHOTO, AUDIO_CALL, VIDEO_CALL)
    protected MessageType type;

    // true = le destinataire a lu le message, false = non lu
    protected String deliveryStatus = "SENT"; // SENT, DELIVERED, SEEN

    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String status) { this.deliveryStatus = status; }

    // Garder aussi isRead pour compatibilité :
    public boolean isRead() { return "SEEN".equals(deliveryStatus); }
    public void setRead(boolean isRead) { if (isRead) this.deliveryStatus = "SEEN"; }

    // Dans la classe Message, après isRead :
    protected boolean isDeleted = false;
    protected boolean isEdited = false;
    protected boolean isPinned = false;
    protected String replyToId = null; // ID du message auquel on répond

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public boolean isEdited() { return isEdited; }
    public void setEdited(boolean edited) { isEdited = edited; }

    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }

    public String getReplyToId() { return replyToId; }
    public void setReplyToId(String replyToId) { this.replyToId = replyToId; }

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    // Constructeur principal pour créer un nouveau message
    // L'id est généré automatiquement
    public Message(String content, User sender, User receiver, MessageType type) {
        this.id = UUID.randomUUID().toString(); // Id unique généré automatiquement
        this.content = content;
        this.sender = sender;
        this.receiver = receiver;
        this.timestamp = new Date(); // Date et heure actuelles automatiquement
        this.type = type;
    }

    // Constructeur secondaire : utilisé quand on charge un message depuis la BD
    // (l'id et le timestamp existent déjà)
    public Message(String id, String content, User sender, User receiver,
                   Date timestamp, MessageType type) {
        this.id = id;
        this.content = content;
        this.sender = sender;
        this.receiver = receiver;
        this.timestamp = timestamp;
        this.type = type;

    }

    // ============================================================
    // GETTERS ET SETTERS
    // ============================================================

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public void setReceiver(User receiver) {
        this.receiver = receiver;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }



    // ============================================================
    // MÉTHODE toString()
    // ============================================================
    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", content='" + content + '\'' +
                ", sender=" + sender.getUsername() +
                ", receiver=" + receiver.getUsername() +
                ", timestamp=" + timestamp +
                ", type=" + type +
                '}';
    }
}