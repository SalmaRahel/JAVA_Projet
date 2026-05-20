package model;

import java.io.Serializable;
import java.util.UUID;

public class User implements Serializable {

    protected String id;
    protected String username;
    protected String password;
    protected boolean isOnline;
    protected String profilePhoto;

    // ── NOUVEAU : indique si cet user est dans nos contacts ──────
    // transient = pas envoyé sur le réseau, juste pour l'affichage
    private transient boolean isContact = true;

    public User(String id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.isOnline = false;
        this.profilePhoto = null;
        this.isContact = true;
    }

    public User(String username, String password) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.password = password;
        this.isOnline = false;
        this.profilePhoto = null;
        this.isContact = true;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean isOnline) { this.isOnline = isOnline; }
    public String getProfilePhoto() { return profilePhoto; }
    public void setProfilePhoto(String profilePhoto) { this.profilePhoto = profilePhoto; }

    // ── NOUVEAU getter/setter ────────────────────────────────────
    public boolean isContact() { return isContact; }
    public void setContact(boolean contact) { this.isContact = contact; }

    @Override
    public String toString() {
        return "User{id='" + id + "', username='" + username + "', isOnline=" + isOnline + '}';
    }
}