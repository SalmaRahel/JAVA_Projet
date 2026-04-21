package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Conversation représente l'historique des messages
// entre DEUX utilisateurs spécifiques.
//
// Exemple concret :
//   Alice et Bob ont une conversation.
//   Cette conversation contient tous les messages
//   échangés entre eux depuis le début.
//
// C'est exactement comme dans WhatsApp :
//   quand tu cliques sur le nom d'un contact,
//   tu ouvres la "Conversation" avec lui.

public class Conversation implements Serializable {

    // Nécessaire pour la sérialisation réseau
    private static final long serialVersionUID = 3L;

    // ============================================================
    // ATTRIBUTS (correspondent exactement au diagramme de classes)
    // ============================================================

    // Identifiant unique de la conversation
    private String id;

    // Premier participant (celui qui a démarré la conversation)
    private User participant1;

    // Deuxième participant
    private User participant2;

    // La liste de tous les messages échangés dans cette conversation
    // ArrayList est une liste dynamique qui grandit automatiquement
    private List<Message> messages;

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    // Pour créer une nouvelle conversation entre deux utilisateurs
    public Conversation(User participant1, User participant2) {
        this.id = UUID.randomUUID().toString(); // Id unique généré automatiquement
        this.participant1 = participant1;
        this.participant2 = participant2;
        this.messages = new ArrayList<>(); // Liste vide au départ
    }

    // Constructeur secondaire : pour charger depuis la base de données
    // (l'id existe déjà)
    public Conversation(String id, User participant1, User participant2) {
        this.id = id;
        this.participant1 = participant1;
        this.participant2 = participant2;
        this.messages = new ArrayList<>();
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public String getId() {
        return id;
    }

    public User getParticipant1() {
        return participant1;
    }

    public User getParticipant2() {
        return participant2;
    }

    public List<Message> getMessages() {
        return messages;
    }

    // ============================================================
    // MÉTHODES IMPORTANTES
    // ============================================================

    // Ajouter un message à la conversation
    // Appelée chaque fois qu'un message est envoyé ou reçu
    public void addMessage(Message message) {
        messages.add(message);
    }

    // Retourner l'AUTRE participant par rapport à un utilisateur donné
    // Exemple : si on passe Alice, on obtient Bob
    // Utilisé dans l'interface pour afficher "avec qui je discute"
    public User getOtherParticipant(User user) {
        // Si l'utilisateur donné est participant1, on retourne participant2
        if (participant1.getId().equals(user.getId())) {
            return participant2;
        }
        // Sinon on retourne participant1
        return participant1;
    }

    // Vérifier si un utilisateur fait partie de cette conversation
    // Utile pour retrouver la bonne conversation dans une liste
    public boolean involves(User user) {
        return participant1.getId().equals(user.getId()) ||
                participant2.getId().equals(user.getId());
    }

    // Vérifier si cette conversation est entre deux utilisateurs spécifiques
    // Utile pour éviter de créer des doublons de conversations
    public boolean isBetween(User userA, User userB) {
        return (participant1.getId().equals(userA.getId()) &&
                participant2.getId().equals(userB.getId()))
                ||
                (participant1.getId().equals(userB.getId()) &&
                        participant2.getId().equals(userA.getId()));
    }

    // ============================================================
    // MÉTHODE toString()
    // ============================================================
    @Override
    public String toString() {
        return "Conversation{" +
                "id='" + id + '\'' +
                ", participant1=" + participant1.getUsername() +
                ", participant2=" + participant2.getUsername() +
                ", messages=" + messages.size() + " message(s)" +
                '}';
    }
}