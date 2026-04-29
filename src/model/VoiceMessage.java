package model;

import java.io.Serializable;

public class VoiceMessage extends Message implements Serializable {

    private static final long serialVersionUID = 3L;

    private byte[] audioData;      // les bytes de l'enregistrement
    private int durationSeconds;   // durée en secondes (ex: 3s)

    public VoiceMessage(User sender, User receiver, byte[] audioData, int durationSeconds) {
        super("Message vocal", sender, receiver, MessageType.VOICE);
        this.audioData = audioData;
        this.durationSeconds = durationSeconds;
    }

    public byte[] getAudioData() { return audioData; }
    public int getDurationSeconds() { return durationSeconds; }

    public void setAudioData(byte[] audioData) {
        this.audioData = audioData;
    }

    // Constructeur pour charger depuis la BD
    public VoiceMessage(String id, User sender, User receiver,
                        java.util.Date timestamp, byte[] audioData, int durationSeconds) {
        super(id, " Message vocal", sender, receiver, timestamp, model.MessageType.VOICE);
        this.audioData = audioData;
        this.durationSeconds = durationSeconds;
    }
}
