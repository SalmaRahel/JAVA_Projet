package call;

import model.CallType;
import model.User;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;

public class VideoCall extends Call {

    private transient OpenCVFrameGrabber videoCapture;
    private int width  = 640;
    private int height = 480;
    private int frameRate = 30;
    private boolean camOn;
    private transient Java2DFrameConverter converter;

    // transient → null après désérialisation, toujours recréer via getters
    private transient javax.sound.sampled.AudioFormat audioFormat;
    private transient javax.sound.sampled.TargetDataLine audioLine;
    private transient javax.sound.sampled.SourceDataLine speakerLine;

    public VideoCall(User caller, User called) {
        super(caller, called);
        this.camOn = false;
        this.converter = new Java2DFrameConverter();
    }

    // Jamais accéder à audioFormat directement
    private javax.sound.sampled.AudioFormat getAudioFormat() {
        if (audioFormat == null) {
            audioFormat = new javax.sound.sampled.AudioFormat(
                    javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                    44100f, 16, 1, 2, 44100f, false
            );
        }
        return audioFormat;
    }

    @Override
    public CallType getCallType() { return CallType.VIDEO; }

    public void captureVideo() {
        try {
            if (videoCapture == null) videoCapture = new OpenCVFrameGrabber(0);
            videoCapture.setImageWidth(width);
            videoCapture.setImageHeight(height);
            videoCapture.start();
            camOn = true;
            System.out.println("[VIDEO] Webcam démarrée");
        } catch (Exception e) {
            System.out.println("[VIDEO] Erreur webcam : " + e.getMessage());
        }
    }

    public BufferedImage captureFrame() {
        try {
            if (!camOn || videoCapture == null) return null;
            Frame frame = videoCapture.grab();
            if (frame != null) {
                if (converter == null) converter = new Java2DFrameConverter();
                return converter.convert(frame);
            }
        } catch (Exception e) {
            System.out.println("[VIDEO] Erreur capture : " + e.getMessage());
        }
        return null;
    }

    public void toggleCam() {
        if (camOn) {
            try { videoCapture.stop(); camOn = false; }
            catch (Exception e) { System.out.println("[VIDEO] Erreur arrêt : " + e.getMessage()); }
        } else {
            captureVideo();
        }
    }

    public byte[] captureFrameBytes() {
        try {
            if (converter == null) converter = new Java2DFrameConverter();
            if (videoCapture == null || !camOn) return new byte[0];

            Frame frame = videoCapture.grab();
            if (frame == null) return new byte[0];

            // Flip horizontal
            org.bytedeco.javacv.OpenCVFrameConverter.ToMat matConv =
                    new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
            org.bytedeco.opencv.opencv_core.Mat mat = matConv.convert(frame);
            org.bytedeco.opencv.global.opencv_core.flip(mat, mat, 1);
            frame = matConv.convert(mat);

            BufferedImage img = converter.convert(frame);
            if (img == null) return new byte[0];

            // Redimensionner 320x240
            BufferedImage small = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = small.createGraphics();
            g.drawImage(img, 0, 0, 320, 240, null);
            g.dispose();

            // Compression JPEG 40%
            javax.imageio.ImageWriter writer =
                    javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.4f);

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.stream.ImageOutputStream ios =
                    javax.imageio.ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(small, null, null), param);
            ios.close();
            return baos.toByteArray();

        } catch (Exception e) { return new byte[0]; }
    }

    public void captureAudio() {
        try {
            if (audioLine != null && audioLine.isOpen()) return;
            javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                    javax.sound.sampled.TargetDataLine.class, getAudioFormat()
            );
            audioLine = (javax.sound.sampled.TargetDataLine)
                    javax.sound.sampled.AudioSystem.getLine(info);
            audioLine.open(getAudioFormat());
            audioLine.start();
            System.out.println("[VIDEO] Audio démarré");
        } catch (Exception e) {
            System.out.println("[VIDEO] Erreur audio : " + e.getMessage());
        }
    }

    public byte[] readAudioData() {
        if (audioLine == null || !audioLine.isOpen()) return new byte[0];
        int frameSize = getAudioFormat().getFrameSize();
        int bufSize = 4096 - (4096 % frameSize);
        byte[] buffer = new byte[bufSize];
        int bytesRead = audioLine.read(buffer, 0, bufSize);
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
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                        javax.sound.sampled.SourceDataLine.class, getAudioFormat()
                );
                speakerLine = (javax.sound.sampled.SourceDataLine)
                        javax.sound.sampled.AudioSystem.getLine(info);
                speakerLine.open(getAudioFormat(), 32768); // buffer anti-jitter
                speakerLine.start();
                System.out.println("[VIDEO] Haut-parleur ouvert");
            }
            int frameSize = speakerLine.getFormat().getFrameSize();
            int validLen = data.length - (data.length % frameSize);
            if (validLen > 0) speakerLine.write(data, 0, validLen);
        } catch (Exception e) {
            System.out.println("[VIDEO] Erreur lecture audio : " + e.getMessage());
        }
    }

    @Override
    public void end() {
        super.end();
        try {
            if (camOn && videoCapture != null) {
                videoCapture.stop(); videoCapture.release(); camOn = false;
            }
        } catch (Exception e) { System.out.println("[VIDEO] Erreur fermeture webcam : " + e.getMessage()); }
        if (audioLine != null && audioLine.isOpen()) {
            audioLine.stop(); audioLine.close(); audioLine = null;
        }
        if (speakerLine != null && speakerLine.isOpen()) {
            speakerLine.drain(); speakerLine.stop(); speakerLine.close(); speakerLine = null;
        }
        System.out.println("[VIDEO] Ressources libérées");
    }

    public boolean isCamOn() { return camOn; }
    public int getWidth()    { return width; }
    public int getHeight()   { return height; }
    public int getFrameRate(){ return frameRate; }

    @Override
    public String toString() {
        return "VideoCall{id='" + id + "', caller=" + caller.getUsername()
                + ", called=" + called.getUsername() + ", status=" + status
                + ", camOn=" + camOn + "}";
    }
}