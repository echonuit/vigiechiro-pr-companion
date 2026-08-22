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
}
