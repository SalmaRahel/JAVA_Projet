package client;

import database.DatabaseManager;
import javafx.application.Application;
import javafx.stage.Stage;
import ui.LoginWindow;

public class Main extends Application {

    private static Client client;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Connecter à la base de données côté client
        DatabaseManager.connect();

        // Créer le client
        client = new Client("localhost", 5000);

        // Créer la fenêtre de login
        LoginWindow loginWindow = new LoginWindow(client);
        client.setLoginWindow(loginWindow);

        // Afficher la fenêtre de login
        loginWindow.start(primaryStage);

        // Connecter au serveur dans un thread séparé
        new Thread(() -> {
            client.connect();
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}