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
    protected boolean isRead;

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
        this.isRead = false; // Par défaut le message n'est pas lu
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
        this.isRead = false;
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

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean isRead) {
        this.isRead = isRead;
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