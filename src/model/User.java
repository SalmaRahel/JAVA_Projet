package model;

public class User {

    // ============ ATTRIBUTS ============

    private String id;
    private String username;
    private String password;
    private boolean isOnline;
    private String profilePhoto;

    // ============ CONSTRUCTEUR ============

    public User(String id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.isOnline = false;
        this.profilePhoto = "";
    }

    // ============ METHODES ============

    public boolean login(String username, String password) {
        if (this.username.equals(username) && this.password.equals(password)) {
            return true;  // connexion acceptee
        } else {
            return false; // connexion refusee
        }
    }

    public void connect() {
        this.isOnline = true;
        System.out.println(username + " est maintenant connecte.");
    }

    public void disconnect() {
        this.isOnline = false;
        System.out.println(username + " est maintenant deconnecte.");
    }

    public void sendMessage() {
        // sera utilise par le Client pour envoyer un message
        System.out.println(username + " envoie un message.");
    }

    // ============ GETTERS ET SETTERS ============
    // Les getters permettent de lire les attributs prives
    // Les setters permettent de les modifier

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }

    public void setProfilePhoto(String profilePhoto) {
        this.profilePhoto = profilePhoto;
    }

    // ============ TOSTRING ============
    // Permet d'afficher un User facilement avec System.out.println

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", isOnline=" + isOnline +
                '}';
    }
}