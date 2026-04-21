package model;

// Une énumération (enum) est une liste de valeurs fixes et prédéfinies.
// MessageType représente les différents types de messages possibles
// dans notre application.
//
// Exemple d'utilisation :
//     MessageType type = MessageType.TEXT;
//     if (type == MessageType.TEXT) { ... }

public enum MessageType {

    // Message texte normal (ex: "Bonjour !")
    TEXT,

    // Message contenant un fichier (ex: un PDF, un ZIP...)
    FILE,

    // Message contenant une photo
    PHOTO,

    // Message indiquant un appel audio
    AUDIO_CALL,

    // Message indiquant un appel vidéo
    VIDEO_CALL
}