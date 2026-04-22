package client;

import javafx.application.Application;
import javafx.stage.Stage;
import ui.LoginWindow;

public class Main extends Application {

    // Client partagé entre toutes les fenêtres
    private static Client client;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Créer le client
        client = new Client("localhost", 5000);

        // Créer la fenêtre de login
        LoginWindow loginWindow = new LoginWindow(client);
        client.setLoginWindow(loginWindow);

        // Afficher la fenêtre de login
        loginWindow.start(primaryStage);

        // Connecter au serveur
        client.connect();
    }

    public static void main(String[] args) {
        // Lance JavaFX correctement
        launch(args);
    }
}