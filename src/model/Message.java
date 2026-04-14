package model;

import java.util.Date;

public class Message {

    // ============ ATTRIBUTS ============

    private String id;
    private String content;
    private User sender;
    private User receiver;
    private Date timestamp;
    private MessageType type;

    // ============ CONSTRUCTEUR ============

    public Message(String id, String content, User sender,
                   User receiver, MessageType type) {
        this.id = id;
        this.content = content;
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
        this.timestamp = new Date(); // enregistre l'heure exacte d'envoi
    }

    // ============ METHODES ============

    public void send() {
        // sera connecte au Client pour l'envoi reel via le reseau
        System.out.println("Message envoye de " + sender.getUsername()
                + " vers " + receiver.getUsername()
                + " : " + content);
    }

    public String getContent() {
        return content;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    // ============ GETTERS ET SETTERS ============

    public String getId() {
        return id;
    }

    public User getSender() {
        return sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // ============ TOSTRING ============

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", de=" + sender.getUsername() +
                ", vers=" + receiver.getUsername() +
                ", contenu='" + content + '\'' +
                ", type=" + type +
                ", heure=" + timestamp +
                '}';
    }
}