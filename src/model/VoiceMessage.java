package model;

import java.io.*;
import javax.sound.sampled.AudioFormat;

public class VoiceMessage extends Message implements Serializable {

    private static final long serialVersionUID = 3L;

    private byte[] audioData;
    private int durationSeconds;

    private static final float SAMPLE_RATE = 48000;
    private static final int SAMPLE_BITS  = 16;
    private static final int CHANNELS     = 1;

    public VoiceMessage(User sender, User receiver, byte[] audioData, int durationSeconds) {
        super("Message vocal", sender, receiver, MessageType.VOICE);
        this.audioData       = audioData;
        this.durationSeconds = durationSeconds;
    }

    public VoiceMessage(String id, User sender, User receiver,
                        java.util.Date timestamp, byte[] audioData, int durationSeconds) {
        super(id, " Message vocal", sender, receiver, timestamp, MessageType.VOICE);
        this.audioData       = audioData;
        this.durationSeconds = durationSeconds;
    }

    public byte[] getAudioData()      { return audioData; }
    public int    getDurationSeconds() { return durationSeconds; }
    public void   setAudioData(byte[] audioData) { this.audioData = audioData; }

    public AudioFormat getAudioFormat() {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                48000, 16, 1, 2, 48000, false
        );
    }

    /**
     * Convertit les bytes PCM bruts en WAV complet (header RIFF inclus).
     * Sans ce header, le Clip interprète mal la taille → underrun → silence.
     */
    public byte[] toWavBytes() throws IOException {
        int sampleRate    = 48000;
        int channels      = 1;
        int bitsPerSample = 16;
        int byteRate      = 48000 * 1 * 2; // 96000 bytes/sec
        int blockAlign    = channels * bitsPerSample / 8;              // 2
        int dataSize      = audioData.length;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        // Utiliser little-endian DIRECTEMENT avec ByteBuffer
        java.nio.ByteBuffer buf = java.nio.ByteBuffer
                .allocate(44 + dataSize)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // RIFF chunk
        buf.put(new byte[]{'R','I','F','F'});
        buf.putInt(36 + dataSize);       // ChunkSize
        buf.put(new byte[]{'W','A','V','E'});

        // fmt sub-chunk
        buf.put(new byte[]{'f','m','t',' '});
        buf.putInt(16);                  // Subchunk1Size (PCM)
        buf.putShort((short) 1);         // AudioFormat (PCM = 1)
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(byteRate);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);

        // data sub-chunk
        buf.put(new byte[]{'d','a','t','a'});
        buf.putInt(dataSize);
        buf.put(audioData);

        return buf.array();
    }
}