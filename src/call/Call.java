package call;

import model.CallStatus;
import model.CallType;
import model.User;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

// Call est une classe ABSTRAITE.


public abstract class Call implements Serializable {

    // Nécessaire pour la sérialisation réseau
    private static final long serialVersionUID = 4L;

    // ============================================================
    // ATTRIBUTS (correspondent exactement au diagramme de classes)
    // ============================================================

    // Identifiant unique de l'appel
    protected String id;

    // L'utilisateur qui passe l'appel (celui qui appelle)
    protected User caller;

    // L'utilisateur qui reçoit l'appel (celui qui est appelé)
    protected User called;

    // L'état actuel de l'appel (PENDING, ACTIVE, ENDED, REFUSED)
    protected CallStatus status;

    // L'heure de début de l'appel (quand il devient ACTIVE)
    protected Date startTime;

    // L'heure de fin de l'appel (quand il devient ENDED ou REFUSED)
    protected Date endTime;

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    // Constructeur de la classe abstraite
    // Appelé par les constructeurs de AudioCall et VideoCall via super()
    public Call(User caller, User called) {
        this.id = UUID.randomUUID().toString(); // Id unique généré automatiquement
        this.caller = caller;
        this.called = called;
        this.status = CallStatus.PENDING; // Au départ l'appel est en attente
        this.startTime = null;  // Pas encore démarré
        this.endTime = null;    // Pas encore terminé
    }

    // ============================================================
    // MÉTHODES IMPORTANTES
    // ============================================================

    // Démarrer l'appel (quand le destinataire accepte)
    // L'appel passe de PENDING à ACTIVE
    public void start() {
        this.status = CallStatus.ACTIVE;
        this.startTime = new Date(); // On enregistre l'heure de début
        System.out.println("Appel démarré entre "
                + caller.getUsername() + " et " + called.getUsername());
    }

    // Accepter l'appel (appelé par le destinataire)
    // Identique à start() mais sémantiquement plus clair
    public void accept() {
        this.status = CallStatus.ACTIVE;
        this.startTime = new Date();
        System.out.println("Appel accepté par " + called.getUsername());
    }

    // Refuser l'appel (appelé par le destinataire)
    // L'appel passe de PENDING à REFUSED
    public void refuse() {
        this.status = CallStatus.REFUSED;
        this.endTime = new Date(); // On enregistre l'heure du refus
        System.out.println("Appel refusé par " + called.getUsername());
    }

    // Terminer l'appel (appelé par n'importe quel participant)
    // L'appel passe à ENDED
    public void end() {
        this.status = CallStatus.ENDED;
        this.endTime = new Date(); // On enregistre l'heure de fin
        System.out.println(" Appel terminé entre "
                + caller.getUsername() + " et " + called.getUsername());
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public String getId() {
        return id;
    }

    public User getCaller() {
        return caller;
    }

    public User getCalled() {
        return called;
    }

    public CallStatus getStatus() {
        return status;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    // ============================================================
    // MÉTHODE ABSTRAITE
    // ============================================================

    // Cette méthode est abstraite : elle n'a PAS de corps ici.
    // Chaque sous-classe (AudioCall, VideoCall) est OBLIGÉE
    // de l'implémenter à sa façon.
    //
    // C'est le principe du POLYMORPHISME en POO :
    //   AudioCall.getCallType() → retourne CallType.AUDIO
    //   VideoCall.getCallType() → retourne CallType.VIDEO
    public abstract CallType getCallType();

    // ============================================================
    // MÉTHODE toString()
    // ============================================================
    @Override
    public String toString() {
        return "Call{" +
                "id='" + id + '\'' +
                ", caller=" + caller.getUsername() +
                ", called=" + called.getUsername() +
                ", status=" + status +
                ", startTime=" + startTime +
                '}';
    }
}