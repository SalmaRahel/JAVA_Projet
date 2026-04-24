package call;

import model.CallType;
import model.User;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;


import java.awt.image.BufferedImage;

// VideoCall hérite de Call (classe abstraite).
// Elle gère les appels VIDÉO (voix + image).
//
// En plus de l'audio (géré par AudioCall), VideoCall ajoute
// la capture et l'affichage de la vidéo via la webcam.
//
// Pour capturer la vidéo, on utilise OpenCV via JavaCV.
// OpenCV est une bibliothèque très puissante de traitement d'image
// utilisée dans le monde entier (robots, voitures autonomes, etc.)
//
// Fonctionnement simplifié :
//   1. OpenCVFrameGrabber ouvre la webcam
//   2. Il capture les images (frames) une par une
//   3. Chaque frame est convertie en BufferedImage (image Java)
//   4. L'image est envoyée via le Socket à l'autre utilisateur
//   5. L'autre utilisateur affiche l'image reçue dans sa fenêtre

public class VideoCall extends Call {

    // ============================================================
    // ATTRIBUTS (correspondent exactement au diagramme de classes)
    // ============================================================

    // OpenCVFrameGrabber = l'objet qui contrôle la webcam
    // C'est lui qui capture les images depuis la caméra physique
    private transient OpenCVFrameGrabber videoCapture;

    // Largeur de la vidéo en pixels (640 = qualité standard)
    private int width;

    // Hauteur de la vidéo en pixels (480 = qualité standard)
    private int height;

    // Nombre d'images capturées par seconde
    // 30 FPS = fluide, 15 FPS = acceptable pour un appel
    private int frameRate;

    // true = la caméra est active, false = caméra éteinte
    private boolean camOn;

    // Convertisseur : transforme une Frame OpenCV en BufferedImage Java
    // Nécessaire car Java ne comprend pas directement le format OpenCV
    private transient Java2DFrameConverter converter;
    private transient javax.sound.sampled.AudioFormat audioFormat;
    private transient javax.sound.sampled.TargetDataLine audioLine;
    private transient javax.sound.sampled.SourceDataLine speakerLine;

    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    public VideoCall(User caller, User called) {
        // On appelle le constructeur de Call (la classe parent)
        super(caller, called);

        // On initialise le grabber avec la caméra 0
        // (0 = première webcam détectée sur l'ordinateur)
        this.videoCapture = null;

        // Résolution standard 640x480 (suffisant pour un appel vidéo)
        this.width = 640;
        this.height = 480;

        // 30 images par seconde = vidéo fluide
        this.frameRate = 30;

        this.camOn = false; // Par défaut la caméra est éteinte
        this.converter = new Java2DFrameConverter();
    }

    // ============================================================
    // IMPLÉMENTATION DE LA MÉTHODE ABSTRAITE
    // ============================================================

    // On est OBLIGÉ d'implémenter cette méthode car elle est
    // abstraite dans la classe parent Call
    @Override
    public CallType getCallType() {
        return CallType.VIDEO; // Un VideoCall est toujours de type VIDEO
    }

    // ============================================================
    // MÉTHODES VIDÉO
    // ============================================================

    // Démarrer la capture vidéo depuis la webcam
    public void captureVideo() {
        try {
            if (videoCapture == null) {
                videoCapture = new OpenCVFrameGrabber(0);
            }

            // Configurer la résolution
            videoCapture.setImageWidth(width);
            videoCapture.setImageHeight(height);

            // Ouvrir la connexion avec la webcam
            videoCapture.start();

            camOn = true;
            System.out.println("📷 Webcam démarrée !");

        } catch (Exception e) {
            System.out.println("❌ Erreur ouverture webcam : "
                    + e.getMessage());
        }
    }

    // Capturer UNE image depuis la webcam
    // Retourne un BufferedImage (image Java standard)
    // Cette image sera envoyée via le Socket
    public BufferedImage captureFrame() {
        try {
            if (!camOn || videoCapture == null) {
                return null;
            }

            // Capturer une frame depuis la webcam
            Frame frame = videoCapture.grab();

            if (frame != null) {
                // Convertir la Frame OpenCV en BufferedImage Java
                return converter.convert(frame);
            }

        } catch (Exception e) {
            System.out.println("❌ Erreur capture frame : "
                    + e.getMessage());
        }
        return null;
    }

    // Afficher une image reçue depuis le réseau
    // frame = l'image reçue de l'autre utilisateur via le Socket
    public void displayVideo() {
        // L'affichage réel se fait dans CallWindow (interface graphique)
        // Cette méthode sera appelée par CallWindow
        System.out.println("🖥️ Affichage vidéo en cours...");
    }

    // Activer/désactiver la caméra
    public void toggleCam() {
        if (camOn) {
            // Éteindre la caméra
            try {
                videoCapture.stop();
                camOn = false;
                System.out.println("📷 Caméra éteinte !");
            } catch (Exception e) {
                System.out.println("❌ Erreur arrêt caméra : "
                        + e.getMessage());
            }
        } else {
            // Rallumer la caméra
            captureVideo();
        }
    }

    // ============================================================
    // OVERRIDE DE end() POUR FERMER LA WEBCAM
    // ============================================================

    // On surcharge end() pour fermer proprement la webcam
    // quand l'appel vidéo se termine
    @Override
    public void end() {
        super.end(); // On appelle end() de Call pour changer le status

        // On ferme la webcam
        try {
            if (camOn && videoCapture != null) {
                videoCapture.stop();
                videoCapture.release();
                camOn = false;
                System.out.println("📷 Webcam fermée !");
            }
        } catch (Exception e) {
            System.out.println("❌ Erreur fermeture webcam : "
                    + e.getMessage());
        }
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public boolean isCamOn() {
        return camOn;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFrameRate() {
        return frameRate;
    }

    // Capturer une frame et la retourner en bytes
    public byte[] captureFrameBytes() {
        try {
            if (converter == null) {
                converter = new Java2DFrameConverter();
            }
            if (videoCapture == null || !camOn) {
                return new byte[0];
            }
            Frame frame = videoCapture.grab();
            if (frame == null) return new byte[0];

            BufferedImage img = converter.convert(frame);
            if (img == null) return new byte[0];

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            return new byte[0];
        }
    }
    // ============================================================
    // MÉTHODE toString()
    // ============================================================

    public void captureAudio() {
        try {
            if (audioFormat == null) {
                audioFormat = new javax.sound.sampled.AudioFormat(
                        javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                        44100, 16, 1, 2, 44100, false
                );
            }
            javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                    javax.sound.sampled.TargetDataLine.class, audioFormat
            );
            audioLine = (javax.sound.sampled.TargetDataLine) javax.sound.sampled.AudioSystem.getLine(info);
            audioLine.open(audioFormat);
            audioLine.start();
            System.out.println("🎙️ Audio vidéo démarré !");
        } catch (Exception e) {
            System.out.println("❌ Erreur audio : " + e.getMessage());
        }
    }

    public byte[] readAudioData() {
        if (audioLine == null || !audioLine.isOpen()) return new byte[0];
        int frameSize = audioLine.getFormat().getFrameSize();
        int bufferSize = 4096 - (4096 % frameSize);
        byte[] buffer = new byte[bufferSize];
        int bytesRead = audioLine.read(buffer, 0, bufferSize);
        if (bytesRead > 0) {
            byte[] result = new byte[bytesRead];
            System.arraycopy(buffer, 0, result, 0, bytesRead);
            return result;
        }
        return new byte[0];
    }

    public void playAudioData(byte[] data) {
        try {
            if (audioFormat == null) {
                audioFormat = new javax.sound.sampled.AudioFormat(
                        javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                        44100, 16, 1, 2, 44100, false
                );
            }
            if (speakerLine == null || !speakerLine.isOpen()) {
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                        javax.sound.sampled.SourceDataLine.class, audioFormat
                );
                speakerLine = (javax.sound.sampled.SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                speakerLine.open(audioFormat);
                speakerLine.start();
            }
            int frameSize = speakerLine.getFormat().getFrameSize();
            int validLength = data.length - (data.length % frameSize);
            if (validLength > 0) {
                speakerLine.write(data, 0, validLength);
            }
        } catch (Exception e) {
            System.out.println("❌ Erreur lecture audio : " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "VideoCall{" +
                "id='" + id + '\'' +
                ", caller=" + caller.getUsername() +
                ", called=" + called.getUsername() +
                ", status=" + status +
                ", camOn=" + camOn +
                ", resolution=" + width + "x" + height +
                '}';
    }
}