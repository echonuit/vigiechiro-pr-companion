package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// Messagerie de l'écran de dépôt : sépare deux canaux jusque-là confondus dans une seule propriété
/// (#1890), sur le patron de `MessagesAudio` qui avait démêlé la même confusion côté audio.
///
/// - l'**état du lot**, dérivé du statut workflow (« Passage déposé le… », « Cohérence : corrigez les
///   contrôles… »), reposé à chaque chargement et rendu en ligne permanente ;
/// - le **retour d'opération** (préparer, générer, supprimer les archives), avec sa sévérité, rendu
///   dans un bandeau fermable.
///
/// Les confondre obligeait le code à compenser par l'**ordre des appels** : `supprimerArchives`
/// reposait l'état via `appliquer` puis l'écrasait aussitôt avec son bilan. Inverser ces deux lignes
/// changeait ce que voyait l'utilisateur. Un état n'est pas un compte rendu : il ne se ferme pas, et il
/// ne doit ni recouvrir le résultat de l'action en cours ni s'en faire recouvrir.
///
/// Extrait de [LotViewModel] pour lui rendre sa seule responsabilité d'orchestrer le dépôt (cohésion,
/// et plafond `NcssCount` / `GodClass` du portail qualité). Agnostique de l'IHM (seuls `javafx.beans`).
final class MessagesLot {

    private final ReadOnlyStringWrapper etatLot = new ReadOnlyStringWrapper(this, "etatLot", "");
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// État du lot en prose (version lisible du stepper), vide quand le statut n'appelle aucun commentaire.
    ReadOnlyStringProperty etatLotProperty() {
        return etatLot.getReadOnlyProperty();
    }

    /// Retour de la dernière opération (avec sévérité), [RetourOperation#AUCUN] en nominal.
    ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    void etat(String texte) {
        etatLot.set(texte);
    }

    void succes(String texte) {
        retour.set(RetourOperation.succes(texte));
    }

    void erreur(String texte) {
        retour.set(RetourOperation.erreur(texte));
    }

    /// Échec **remonté d'une exception** : le retour y gagne le **geste attendu** (#2635) et une longueur
    /// **bornée** (#2076) - deux choses que `erreur(refus.getMessage())` perdait en chemin.
    void erreur(Throwable refus) {
        retour.set(RetourOperation.erreur(refus));
    }

    /// Efface le seul retour : l'état du lot, lui, décrit la nuit et survit à la fermeture du bandeau.
    void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }

    /// Remet les deux canaux à zéro (changement de passage).
    void reinitialiser() {
        etatLot.set("");
        retour.set(RetourOperation.AUCUN);
    }
}
