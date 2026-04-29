package model;



public enum CallStatus {

    // L'appel a été lancé mais pas encore accepté
    // Bob voit "Alice vous appelle..."
    PENDING,

    // L'appel est en cours (les deux utilisateurs sont connectés)
    ACTIVE,

    // L'appel s'est terminé normalement
    ENDED,

    // L'appel a été refusé par le destinataire
    REFUSED
}