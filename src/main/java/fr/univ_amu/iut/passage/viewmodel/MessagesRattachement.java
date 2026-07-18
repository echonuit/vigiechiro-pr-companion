package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/// Canal de retour de la modale « Modifier le passage » (#1917), partagé par [RattachementViewModel] et
/// ses deux collaborateurs de saisie ([SaisieHorairesNuit], [SaisiePassageConditions]).
///
/// Il remplace un `ReadOnlyStringWrapper messageErreur` que le ViewModel passait **par référence** à ses
/// collaborateurs. Son nom portait la sévérité, et l'interdisait donc : « Métadonnées récupérées depuis
/// Vigie-Chiro. » et « Météo pré-remplie : vérifiez puis appliquez. » sont des **succès** qui
/// s'affichaient dans un canal appelé « erreur », au même titre qu'un échec réseau.
///
/// Les collaborateurs choisissent eux-mêmes la sévérité, contrairement au patron retenu au Lot 2 pour
/// `PositionsEnAttente` : celui-là n'émettait que des échecs, il pouvait rester agnostique. Ceux-ci
/// émettent des guidages de saisie **et** des succès de pré-remplissage - le leur faire décider ailleurs
/// obligerait le point de jonction à deviner ce qu'ils voulaient dire.
///
/// Agnostique de l'IHM (seuls `javafx.beans`), comme le veut la règle ArchUnit `viewmodel_sans_javafx_ui`.
final class MessagesRattachement {

    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Retour de la dernière opération (avec sévérité), [RetourOperation#AUCUN] en nominal.
    ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Opération réussie : métadonnées récupérées, météo pré-remplie, envoi accepté.
    void succes(String texte) {
        retour.set(RetourOperation.succes(texte));
    }

    /// **Guidage** : une saisie n'est pas encore exploitable, ou une source de données manque sans que
    /// rien n'ait échoué. L'utilisateur a quelque chose à faire, ce n'est pas une panne.
    void info(String texte) {
        retour.set(RetourOperation.info(texte));
    }

    /// Échec ou refus : le service a dit non, ou l'appel n'a pas abouti.
    void erreur(String texte) {
        retour.set(RetourOperation.erreur(texte));
    }

    /// Publie un retour déjà constitué (le compte rendu d'envoi porte le sien).
    void publier(RetourOperation retourConstitue) {
        retour.set(retourConstitue);
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme, ou une nouvelle saisie repart).
    void effacer() {
        retour.set(RetourOperation.AUCUN);
    }
}
