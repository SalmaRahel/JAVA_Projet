package model;

// CallStatus représente l'état actuel d'un appel.
// Un appel passe par ces états dans cet ordre :
//
//   PENDING → ACTIVE → ENDED
//      ↓
//   REFUSED
//
// Exemple :
//   - Alice appelle Bob   → status = PENDING  (en attente de réponse)
//   - Bob accepte         → status = ACTIVE   (appel en cours)
//   - Alice raccroche     → status = ENDED    (appel terminé)
//
//   OU
//
//   - Alice appelle Bob   → status = PENDING
//   - Bob refuse          → status = REFUSED  (appel refusé)

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