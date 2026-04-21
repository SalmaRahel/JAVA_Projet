package model;

// CallType représente le TYPE d'un appel.
// Il y a seulement deux types possibles dans notre application :
// un appel audio (voix seulement) ou un appel vidéo (voix + image).
//
// Exemple d'utilisation :
//   - L'utilisateur clique sur "Appel Audio" → CallType.AUDIO
//   - L'utilisateur clique sur "Appel Vidéo" → CallType.VIDEO
//
// Ne pas confondre avec CallStatus :
//   - CallType  = QUEL TYPE d'appel ? (audio ou vidéo)
//   - CallStatus = QUEL ÉTAT a l'appel ? (en attente, actif, terminé...)

public enum CallType {

    // Appel voix uniquement (micro seulement, pas de caméra)
    AUDIO,

    // Appel voix + vidéo (micro + caméra)
    VIDEO
}