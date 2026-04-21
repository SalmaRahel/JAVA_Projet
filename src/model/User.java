package model;

import java.io.Serializable;
import java.util.UUID;

// Serializable signifie que cet objet peut être envoyé
// à travers le réseau via les Sockets
public class User implements Serializable {

    // ============================================================
    // ATTRIBUTS (correspondent exactement au diagramme de classes)
    // ============================================================

    // Identifiant unique de l'utilisateur (généré automatiquement)
    protected String id;

    // Nom d'utilisateur choisi lors de l'inscription
    protected String username;

    // Mot de passe de l'utilisateur
    protected String password;

    // true = l'utilisateur est connecté, false = déconnecté
    protected boolean isOnline;

    // Chemin vers la photo de profil (peut être null)
    protected String profilePhoto;

    // ============================================================
    // CONSTRUCTEURS
    // ============================================================

    // Constructeur principal : utilisé quand on reçoit un User depuis la BD
    // (l'id existe déjà en base de données)
    public User(String id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.isOnline = false;      // Par défaut déconnecté
        this.profilePhoto = null;   // Par défaut pas de photo
    }

    // Constructeur pour créer un NOUVEL utilisateur (inscription)
    // L'id est généré automatiquement par UUID
    // UUID = Universal Unique Identifier = identifiant unique garanti
    // Exemple : "550e8400-e29b-41d4-a716-446655440000"
    public User(String username, String password) {
        this.id = UUID.randomUUID().toString(); // Génère un id unique
        this.username = username;
        this.password = password;
        this.isOnline = false;
        this.profilePhoto = null;
    }

    // ============================================================
    // GETTERS ET SETTERS
    // (permettent de lire et modifier les attributs depuis l'extérieur)
    // ============================================================

    // Retourne l'id de l'utilisateur
    public String getId() {
        return id;
    }

    // Retourne le username
    public String getUsername() {
        return username;
    }

    // Modifie le username
    public void setUsername(String username) {
        this.username = username;
    }

    // Retourne le password
    public String getPassword() {
        return password;
    }

    // Modifie le password
    public void setPassword(String password) {
        this.password = password;
    }

    // Retourne true si l'utilisateur est en ligne
    public boolean isOnline() {
        return isOnline;
    }

    // Modifie le statut en ligne
    public void setOnline(boolean isOnline) {
        this.isOnline = isOnline;
    }

    // Retourne le chemin de la photo de profil
    public String getProfilePhoto() {
        return profilePhoto;
    }

    // Modifie la photo de profil
    public void setProfilePhoto(String profilePhoto) {
        this.profilePhoto = profilePhoto;
    }

    // ============================================================
    // MÉTHODE toString()
    // ============================================================
    // Utilisée pour afficher un User facilement (ex: dans les logs)
    // Quand on fait System.out.println(user), Java appelle cette méthode
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", isOnline=" + isOnline +
                '}';
    }
}