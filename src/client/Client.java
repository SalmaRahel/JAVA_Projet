package client;

import call.AudioCall;
import call.Call;
import call.VideoCall;
import model.*;
import ui.CallWindow;
import ui.ChatWindow;
import ui.LoginWindow;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;

import javafx.application.Platform;
import javafx.stage.Stage;

public class Client implements Runnable {

    private String serverIP;
    private int port;
    private Socket socket;
    private User user;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private LoginWindow loginWindow;
    private ChatWindow chatWindow;
    private CallWindow callWindow;
    private Call currentCall;
    private List<User> connectedUsers = new ArrayList<>();

    public Client(String serverIP, int port) {
        this.serverIP = serverIP;
        this.port = port;
        this.currentCall = null;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Object received = in.readObject();

                if (received instanceof model.FileMessage) {
                    model.FileMessage fm = (model.FileMessage) received;
                    if (chatWindow != null) chatWindow.displayMessage(fm);
                } else if (received instanceof Message) {
                    receiveMessage((Message) received);

                } else if (received instanceof List) {
                    List<User> users = (List<User>) received;
                    this.connectedUsers = users;
                    if (chatWindow != null) {
                        chatWindow.updateUserList(users);
                    }

                } else if (received instanceof Call) {
                    currentCall = (Call) received;
                    receiveCall(currentCall);

                } else if (received instanceof byte[]) {
                    if (currentCall instanceof call.AudioCall) {
                        ((call.AudioCall) currentCall).playAudioData((byte[]) received);
                    } else if (currentCall instanceof call.VideoCall) {
                        byte[] frameData = (byte[]) received;
                        if (frameData.length > 10000) {
                            // C'est une frame vidéo (grande taille)
                            Platform.runLater(() -> {
                                if (callWindow != null) {
                                    javafx.scene.image.Image img = new javafx.scene.image.Image(
                                            new java.io.ByteArrayInputStream(frameData)
                                    );
                                    callWindow.showVideoFeed(img);
                                }
                            });
                        } else {
                            // C'est de l'audio (petite taille)
                            ((call.VideoCall) currentCall).playAudioData(frameData);
                        }
                    }
                }

                else if (received instanceof String) {
                    handleServerCommand((String) received);

                } else if (received instanceof User) {
                    this.user = (User) received;
                    System.out.println("User mis à jour : " + user.getId());
                }
            }

        } catch (EOFException e) {
            System.out.println("Déconnecté du serveur !");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Erreur réception : " + e.getMessage());
        }
    }

    public void connect() {
        try {
            socket = new Socket(serverIP, port);
            System.out.println("Connecté au serveur !");
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

        } catch (IOException e) {
            System.out.println("Impossible de se connecter : "
                    + e.getMessage());
            if (loginWindow != null) {
                loginWindow.showError(
                        "Impossible de se connecter au serveur !"
                );
            }
        }
    }

    public void disconnect() {
        try {
            sendObject("DISCONNECT");
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println(" Erreur déconnexion : "
                    + e.getMessage());
        }
    }

    public void login(String username, String password) {
        this.user = new User(username, password);
        sendObject(this.user);
        System.out.println("Login envoyé : " + username);

        // Démarrer le thread d'écoute IMMÉDIATEMENT après le login
        Thread listenerThread = new Thread(this);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void sendMessage(Message message) {
        sendObject(message);
    }

    public void receiveMessage() {
        System.out.println(" Message reçu !");
    }

    private void receiveMessage(Message message) {
        System.out.println(" Message reçu de "
                + message.getSender().getUsername());
        if (chatWindow != null) {
            if (message instanceof model.FileMessage) {
                chatWindow.displayMessage(message);
            } else {
                chatWindow.displayMessage(message);
            }
        }
    }

    public void startCall(User target, CallType type) {
        if (type == CallType.AUDIO) {
            currentCall = new AudioCall(user, target);
        } else {
            currentCall = new VideoCall(user, target);
        }
        sendObject(currentCall);
        System.out.println(" Appel " + type.name()
                + " vers " + target.getUsername());
    }

    public void startAudioStreaming(AudioCall audioCall) {
        Thread streamThread = new Thread(() -> {
            while (audioCall.getStatus() == model.CallStatus.ACTIVE) {
                byte[] data = audioCall.readAudioData();
                if (data.length > 0) {
                    sendObject(data);
                }
            }
        });
        streamThread.setDaemon(true);
        streamThread.start();
    }

    public void receiveCall(Call incomingCall) {
        System.out.println(" Appel entrant/mis à jour : "
                + incomingCall.getCaller().getUsername()
                + " | Statut : " + incomingCall.getStatus());

        Platform.runLater(() -> {
            if (incomingCall.getStatus() == model.CallStatus.ACTIVE
                    && callWindow != null) {
                callWindow.setCall(incomingCall);
                callWindow.showIncoming();

                // Démarrer le streaming audio côté appelant
                // Démarrer le streaming côté appelant
                if (currentCall instanceof call.AudioCall) {
                    ((call.AudioCall) currentCall).captureAudio();
                    startAudioStreaming((call.AudioCall) currentCall);
                } else if (currentCall instanceof call.VideoCall) {
                    call.VideoCall vc = (call.VideoCall) currentCall;
                    new Thread(() -> {
                        vc.captureVideo();
                        startVideoStreaming(vc);
                    }).start();
                }

            } else if (incomingCall.getStatus() == model.CallStatus.PENDING) {
                CallWindow window = new CallWindow(this, incomingCall);
                window.setCallerMode(false);
                this.callWindow = window;
                try {
                    window.start(new Stage());
                } catch (Exception e) {
                    System.out.println(" Erreur ouverture CallWindow : "
                            + e.getMessage());
                }

            } else if (incomingCall.getStatus() == model.CallStatus.REFUSED
                    || incomingCall.getStatus() == model.CallStatus.ENDED) {
                if (callWindow != null) {
                    callWindow.onEndCall();
                }
            }
        });
    }

    private void handleServerCommand(String command) {
        System.out.println(" Commande serveur : " + command);

        if (command.equals("LOGIN_SUCCESS")) {
            System.out.println("Login accepté !");


            // Ouvrir ChatWindow
            if (loginWindow != null) {
                loginWindow.openChatWindow();
            }

            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    sendObject("GET_USERS");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

        } else if (command.equals("LOGIN_FAILED")) {
            System.out.println(" Login refusé !");
            if (loginWindow != null) {
                loginWindow.showError(
                        "Username ou mot de passe incorrect !"
                );
            }
        }
    }

    private synchronized void sendObject(Object obj) {
        try {
            if (out != null) {
                out.writeObject(obj);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            System.out.println(" Erreur envoi : " + e.getMessage());
        }
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setLoginWindow(LoginWindow loginWindow) {
        this.loginWindow = loginWindow;
    }

    public void setChatWindow(ChatWindow chatWindow) {
        this.chatWindow = chatWindow;
    }

    public void setCallWindow(CallWindow callWindow) {
        this.callWindow = callWindow;
    }

    public Call getCurrentCall() {
        return currentCall;
    }

    public List<User> getConnectedUsers() {
        return connectedUsers;
    }

    public void sendCallResponse(call.Call call) {
        sendObject(call);
    }

    public void startVideoStreaming(VideoCall videoCall) {
        // Thread vidéo
        Thread videoThread = new Thread(() -> {
            while (videoCall.getStatus() == model.CallStatus.ACTIVE) {
                byte[] data = videoCall.captureFrameBytes();
                if (data.length > 0) {
                    sendObject(data);
                }
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }
        });
        videoThread.setDaemon(true);
        videoThread.start();

        // Thread audio
        videoCall.captureAudio();
        Thread audioThread = new Thread(() -> {
            while (videoCall.getStatus() == model.CallStatus.ACTIVE) {
                byte[] data = videoCall.readAudioData();
                if (data.length > 0) {
                    sendObject(data);
                }
                try { Thread.sleep(20); } catch (InterruptedException e) {}
            }
        });
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public void sendCommand(String command) {
        sendObject(command);
    }

    public static void main(String[] args) {
        Client client = new Client("localhost", 5000);
        LoginWindow loginWindow = new LoginWindow(client);
        client.setLoginWindow(loginWindow);
        client.connect();
    }
}