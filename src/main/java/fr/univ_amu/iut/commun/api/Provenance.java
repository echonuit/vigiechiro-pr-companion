package fr.univ_amu.iut.commun.api;

/// Qui a répondu à une requête du dépôt : l'API Vigie-Chiro, ou son stockage S3 (#5598).
///
/// Un même statut n'y a pas le même sens. Un `403` de l'API dit que vos droits manquent, et une
/// reconnexion peut les rendre ; un `403` du stockage dit qu'une URL pré-signée n'est pas acceptée,
/// et le jeton de l'API n'y intervient pas. La provenance se décide d'après la requête qui a échoué,
/// jamais d'après le texte de la réponse.
public enum Provenance {
    /// L'API Vigie-Chiro : déclaration d'un fichier, demande d'URL de partie, finalisation.
    API,

    /// Le stockage S3 : le `PUT` des octets vers une URL pré-signée.
    STOCKAGE
}
