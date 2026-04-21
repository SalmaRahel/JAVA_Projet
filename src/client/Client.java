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

                if (received instanceof Message) {
                    receiveMessage((Message) received);

                } else if (received instanceof List) {
                    List<User> users = (List<User>) received;
                    if (chatWindow != null) {
                        chatWindow.updateUserList(users);
                    }

                } else if (received instanceof Call) {
                    receiveCall();

                } else if (received instanceof String) {
                    handleServerCommand((String) received);
                }
            }

        } catch (EOFException e) {
            System.out.println("🔌 Déconnecté du serveur !");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("❌ Erreur réception : " + e.getMessage());
        }
    }

    public void connect() {
        try {
            socket = new Socket(serverIP, port);
            System.out.println("✅ Connecté au serveur !");
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

        } catch (IOException e) {
            System.out.println("❌ Impossible de se connecter : "
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
            System.out.println("❌ Erreur déconnexion : "
                    + e.getMessage());
        }
    }

    public void login(String username, String password) {
        this.user = new User(username, password);
        sendObject(this.user);
        System.out.println("📤 Login envoyé : " + username);
    }

    public void sendMessage(Message message) {
        sendObject(message);
    }

    public void receiveMessage() {
        System.out.println("📨 Message reçu !");
    }

    private void receiveMessage(Message message) {
        System.out.println("📨 Message reçu de "
                + message.getSender().getUsername());
        if (chatWindow != null) {
            chatWindow.displayMessage(message);
        }
    }

    public void startCall(User target, CallType type) {
        if (type == CallType.AUDIO) {
            currentCall = new AudioCall(user, target);
        } else {
            currentCall = new VideoCall(user, target);
        }
        sendObject(currentCall);
        System.out.println("📞 Appel " + type.name()
                + " vers " + target.getUsername());
    }

    public void receiveCall() {
        System.out.println("📞 Appel entrant !");
        if (callWindow != null) {
            callWindow.showIncoming();
        }
    }

    private void handleServerCommand(String command) {
        System.out.println("⚙️ Commande serveur : " + command);

        if (command.equals("LOGIN_SUCCESS")) {
            System.out.println("✅ Login accepté !");

            // Démarrer le thread d'écoute
            Thread listenerThread = new Thread(this);
            listenerThread.setDaemon(true);
            listenerThread.start();

            // Ouvrir ChatWindow
            if (loginWindow != null) {
                loginWindow.openChatWindow();
            }

        } else if (command.equals("LOGIN_FAILED")) {
            System.out.println("❌ Login refusé !");
            if (loginWindow != null) {
                loginWindow.showError(
                        "Username ou mot de passe incorrect !"
                );
            }
        }
    }

    private void sendObject(Object obj) {
        try {
            if (out != null) {
                out.writeObject(obj);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            System.out.println("❌ Erreur envoi : " + e.getMessage());
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

    public static void main(String[] args) {
        Client client = new Client("localhost", 5000);
        LoginWindow loginWindow = new LoginWindow(client);
        client.setLoginWindow(loginWindow);
        client.connect();
    }
}