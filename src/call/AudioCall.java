package call;

import model.CallType;
import model.User;

import javax.sound.sampled.*;

// AudioCall hérite de Call (classe abstraite).
// Elle gère spécifiquement les appels AUDIO (voix uniquement).
//
// Pour capturer et jouer l'audio, Java fournit une bibliothèque
// intégrée appelée "javax.sound.sampled" — pas besoin de
// télécharger quoi que ce soit de supplémentaire.
//
// Fonctionnement simplifié :
//   1. On configure le format audio (qualité, fréquence...)
//   2. On capture le son depuis le microphone
//   3. On envoie les données audio via le Socket (géré par Client)
//   4. On reçoit les données audio de l'autre côté
//   5. On joue le son reçu via les haut-parleurs

public class AudioCall extends Call {

    // ============================================================
    // ATTRIBUTS (correspondent exactement au diagramme de classes)
    // ============================================================

    // AudioFormat définit la QUALITÉ du son capturé :
    //   - Fréquence d'échantillonnage (44100 Hz = qualité CD)
    //   - Bits par échantillon (16 bits = bonne qualité)
    //   - Mono ou stéréo (1 = mono, 2 = stéréo)
    private transient AudioFormat audioFormat;

    // TargetDataLine = le "fil" connecté au microphone
    // C'est via cet objet qu'on lit les données du micro
    private transient TargetDataLine audioLine;

    // true = l'utilisateur a coupé son micro, false = micro actif
    private boolean isMuted;

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    public AudioCall(User caller, User called) {
        // On appelle le constructeur de Call (la classe parent)
        super(caller, called);

        // On configure le format audio
        // Paramètres : (fréquence, bits, canaux, signed, bigEndian)
        this.audioFormat = new AudioFormat(44100, 16, 1, true, false);

        this.audioLine = null;  // Sera initialisé dans captureAudio()
        this.isMuted = false;   // Par défaut le micro est actif
    }

    // ============================================================
    // IMPLÉMENTATION DE LA MÉTHODE ABSTRAITE
    // ============================================================

    // On est OBLIGÉ d'implémenter cette méthode car elle est
    // abstraite dans la classe parent Call
    @Override
    public CallType getCallType() {
        return CallType.AUDIO; // Un AudioCall est toujours de type AUDIO
    }

    // ============================================================
    // MÉTHODES AUDIO
    // ============================================================

    // Démarrer la capture audio depuis le microphone
    // Cette méthode ouvre le microphone et commence à écouter
    public void captureAudio() {
        try {
            if (audioFormat == null) {
                audioFormat = new AudioFormat(44100, 16, 1, true, false);
            }
            // DataLine.Info décrit le type de ligne audio qu'on veut
            // ici on veut une ligne de CAPTURE (microphone)
            DataLine.Info info = new DataLine.Info(
                    TargetDataLine.class, audioFormat
            );

            // Vérifier que le système supporte ce format audio
            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("❌ Format audio non supporté !");
                return;
            }

            // Ouvrir la ligne du microphone
            audioLine = (TargetDataLine) AudioSystem.getLine(info);
            audioLine.open(audioFormat);

            // Démarrer la capture
            audioLine.start();
            System.out.println("🎙️ Capture audio démarrée !");

        } catch (LineUnavailableException e) {
            System.out.println("❌ Microphone non disponible : "
                    + e.getMessage());
        }
    }

    // Lire les données audio capturées par le microphone
    // Retourne un tableau d'octets (bytes) représentant le son
    // Ces bytes seront envoyés via le Socket à l'autre utilisateur
    public byte[] readAudioData() {
        if (audioLine == null || !audioLine.isOpen()) {
            return new byte[0]; // Retourne un tableau vide si pas de micro
        }

        // Buffer de 4096 octets (taille standard pour l'audio en temps réel)
        byte[] buffer = new byte[4096];

        // On lit les données du microphone dans le buffer
        int bytesRead = audioLine.read(buffer, 0, buffer.length);

        if (bytesRead > 0) {
            return buffer;
        }
        return new byte[0];
    }

    // Jouer les données audio reçues depuis le réseau
    // data = les bytes reçus de l'autre utilisateur via le Socket
    // Changer la signature pour accepter les données reçues
    public void playAudio(byte[] data) {
        if (data == null || data.length == 0) return;

        try {
            DataLine.Info info = new DataLine.Info(
                    SourceDataLine.class, audioFormat
            );

            SourceDataLine speakerLine =
                    (SourceDataLine) AudioSystem.getLine(info);
            speakerLine.open(audioFormat);
            speakerLine.start();

            // ← écrire les données reçues dans les haut-parleurs
            speakerLine.write(data, 0, data.length);
            speakerLine.drain();
            speakerLine.close();

        } catch (LineUnavailableException e) {
            System.out.println("❌ Haut-parleur non disponible : "
                    + e.getMessage());
        }
    }
    // Couper le microphone (mute)
    public void mute() {
        isMuted = true;
        if (audioLine != null && audioLine.isOpen()) {
            audioLine.stop(); // On arrête la capture du micro
        }
        System.out.println("🔇 Micro coupé !");
    }

    // Réactiver le microphone (unmute)
    public void unmute() {
        isMuted = false;
        if (audioLine != null && audioLine.isOpen()) {
            audioLine.start(); // On reprend la capture du micro
        }
        System.out.println("🎙️ Micro réactivé !");
    }

    // ============================================================
    // OVERRIDE DE end() POUR FERMER LE MICROPHONE
    // ============================================================

    // On surcharge end() pour fermer proprement le microphone
    // quand l'appel se termine
    @Override
    public void end() {
        super.end(); // On appelle end() de Call pour changer le status

        // On ferme la ligne du microphone
        if (audioLine != null && audioLine.isOpen()) {
            audioLine.stop();
            audioLine.close();
            System.out.println("🎙️ Microphone fermé !");
        }
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public boolean isMuted() {
        return isMuted;
    }

    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    // ============================================================
    // MÉTHODE toString()
    // ============================================================
    @Override
    public String toString() {
        return "AudioCall{" +
                "id='" + id + '\'' +
                ", caller=" + caller.getUsername() +
                ", called=" + called.getUsername() +
                ", status=" + status +
                ", isMuted=" + isMuted +
                '}';
    }
}