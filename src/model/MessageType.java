package model;



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
    VIDEO_CALL,

    VOICE
}