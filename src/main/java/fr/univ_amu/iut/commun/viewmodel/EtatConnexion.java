package fr.univ_amu.iut.commun.viewmodel;

import javafx.beans.property.ReadOnlyBooleanProperty;

/// Un jeton Vigie-Chiro est-il disponible ? **En observable** (#4205).
///
/// Port du socle, côté lecture : un écran qui ferme un geste faute de jeton (#4194) a besoin de
/// **suivre** cette réponse, pas seulement de la demander. `ClientVigieChiro#estConnecte()` et
/// `StockageConnexion#estConnecte()` se demandent ; ils ne se surveillent pas.
///
/// Même figure que [RevisionDonnees] au-dessus de `JournalMutations` : la feature `connexion` pose
/// l'implémentation ([fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton]), le socle n'en connaît que
/// ce contrat, et les écrans des autres features s'y branchent sans dépendre de la connexion.
///
/// ## Pourquoi ce port est optionnel chez ses consommateurs
///
/// La source de vérité (`FournisseurToken`) n'est liée que par `ConnexionModule`. Un injecteur
/// partiel (outils de capture) n'a donc aucun jeton, et « pas d'état de connexion » y est la réponse
/// juste : un geste qui touche la plateforme s'y ferme, ce qui est prudent.
public interface EtatConnexion {

    /// Un jeton est-il disponible ? **En lecture seule** : un écran observe, il ne décide pas.
    ReadOnlyBooleanProperty connecteProperty();

    /// Relit la source de vérité et publie le résultat (#6151). Appelée **au retour** d'un geste de
    /// plateforme, sur le fil JavaFX : c'est là qu'un écran découvre qu'un jeton a expiré ou vient
    /// d'être refusé, et le geste doit alors se fermer sans attendre une reconnexion.
    ///
    /// Relire ne décide rien : le port reste en lecture seule sur l'état, il demande seulement qu'on le
    /// regarde de nouveau. Un état qui ne dépend d'aucune source - les outils de capture - n'a rien à
    /// relire, d'où ce défaut.
    default void relire() {
        // Rien à relire : cet état ne suit aucune source.
    }
}
