package call;

import model.CallType;
import model.User;

import javax.sound.sampled.*;

public class AudioCall extends Call {

    // CRITIQUE : audioFormat est transient → devient null après désérialisation réseau.
    // Toujours passer par getAudioFormat() qui reconstruit si null.
    private transient AudioFormat audioFormat;
    private transient TargetDataLine audioLine;
    private boolean isMuted;
    private transient SourceDataLine speakerLine = null;

    public AudioCall(User caller, User called) {
        super(caller, called);
        this.isMuted = false;
    }

    // Jamais accéder à audioFormat directement — toujours via cette méthode
    public AudioFormat getAudioFormat() {
        if (audioFormat == null) {
            audioFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    44100f, 16, 1, 2, 44100f, false
            );
        }
        return audioFormat;
    }

    @Override
    public CallType getCallType() {
        return CallType.AUDIO;
    }

    public void captureAudio() {
        try {
            if (audioLine != null && audioLine.isOpen()) return;
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, getAudioFormat());
            audioLine = (TargetDataLine) AudioSystem.getLine(info);
            audioLine.open(getAudioFormat());
            audioLine.start();
            System.out.println("[AUDIO] Capture démarrée");
        } catch (Exception e) {
            System.out.println("[AUDIO] Erreur capture : " + e.getMessage());
        }
    }

    public byte[] readAudioData() {
        if (audioLine == null || !audioLine.isOpen()) return new byte[0];

        if (isMuted) {
            // Drainer le buffer hardware pour éviter l'accumulation
            int frameSize = getAudioFormat().getFrameSize();
            int bufSize = 4096 - (4096 % frameSize);
            byte[] drain = new byte[bufSize];
            audioLine.read(drain, 0, drain.length);
            return new byte[0];
        }

        int frameSize = getAudioFormat().getFrameSize();
        int bufSize = 4096 - (4096 % frameSize);
        byte[] buffer = new byte[bufSize];
        int bytesRead = audioLine.read(buffer, 0, buffer.length);
        if (bytesRead > 0) {
            byte[] result = new byte[bytesRead];
            System.arraycopy(buffer, 0, result, 0, bytesRead);
            return result;
        }
        return new byte[0];
    }

    public void playAudioData(byte[] data) {
        if (data == null || data.length == 0) return;
        try {
            if (speakerLine == null || !speakerLine.isOpen()) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, getAudioFormat());
                speakerLine = (SourceDataLine) AudioSystem.getLine(info);
                speakerLine.open(getAudioFormat(), 32768); // ~185ms buffer anti-jitter
                speakerLine.start();
                System.out.println("[AUDIO] Haut-parleur ouvert");
            }
            byte[] amplified = amplify(data, 2.5f);
            int frameSize = speakerLine.getFormat().getFrameSize();
            int validLen = amplified.length - (amplified.length % frameSize);
            if (validLen > 0) speakerLine.write(amplified, 0, validLen);
        } catch (LineUnavailableException e) {
            System.out.println("[AUDIO] Erreur lecture : " + e.getMessage());
        }
    }

    private byte[] amplify(byte[] pcmData, float factor) {
        byte[] out = new byte[pcmData.length];
        for (int i = 0; i + 1 < pcmData.length; i += 2) {
            short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            int amp = (int)(sample * factor);
            amp = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, amp));
            out[i]     = (byte)(amp & 0xFF);
            out[i + 1] = (byte)((amp >> 8) & 0xFF);
        }
        return out;
    }

    // NE JAMAIS appeler audioLine.stop() ici → deadlock sur le thread read()
    public void mute()   { isMuted = true;  System.out.println("[AUDIO] Micro coupé"); }
    public void unmute() { isMuted = false; System.out.println("[AUDIO] Micro réactivé"); }

    @Override
    public void end() {
        super.end();
        if (audioLine != null && audioLine.isOpen()) {
            audioLine.stop(); audioLine.close(); audioLine = null;
        }
        if (speakerLine != null && speakerLine.isOpen()) {
            speakerLine.drain(); speakerLine.stop(); speakerLine.close(); speakerLine = null;
        }
        System.out.println("[AUDIO] Lignes fermées");
    }

    public boolean isMuted() { return isMuted; }

    @Override
    public String toString() {
        return "AudioCall{id='" + id + "', caller=" + caller.getUsername()
                + ", called=" + called.getUsername() + ", status=" + status + "}";
    }
}