package fr.univ_amu.iut.commun.view;

/// Hook de **cycle de vie** appelé quand un écran est **définitivement quitté** (#230) : son étape est
/// retirée de l'historique du [Navigateur] (retour, retour à l'accueil, ouverture d'une nouvelle racine
/// ou clic d'un ancêtre du fil), après une éventuelle confirmation de [GardeQuitter].
///
/// Un controller d'écran l'implémente pour **libérer ses ressources** au moment où il n'est plus
/// atteignable (ex. l'import supprime le dossier temporaire d'extraction d'un `.zip` abandonné — sans
/// ce hook, un temporaire de plusieurs Go fuiterait car le ViewModel est non-singleton).
///
/// Appelé sur le **fil JavaFX**. Ne doit jamais lever : un nettoyage en échec ne doit pas casser la
/// navigation.
public interface AuDepartEcran {

    /// Libère les ressources de l'écran qui vient d'être quitté.
    void auDepartEcran();
}
