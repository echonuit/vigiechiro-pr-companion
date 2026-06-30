package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.validation.model.BilanImport;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// Messagerie de la vue audio : sépare deux canaux jusque-là confondus dans une seule propriété.
///
/// - l'**indice d'état vide** de la table (« aucune observation… »), rendu dans le placeholder gris ;
/// - le **retour d'opération** (import / export / valider / corriger), avec une [RetourOperation] portant
///   sa sévérité, rendu dans un bandeau **toujours visible**.
///
/// Les confondre faisait afficher une erreur d'import dans le placeholder gris, indistinguable de « pas
/// de données » (incident « For input string: SUR » invisible). Extrait du [AudioViewModel] pour qu'il
/// garde la seule responsabilité d'orchestrer la revue (cohésion, même patron que [ComptageAudio] /
/// [ExporteurAudio]). Agnostique de l'IHM (seuls `javafx.beans`).
final class MessagesAudio {

    private final ReadOnlyStringWrapper etatVide = new ReadOnlyStringWrapper(this, "etatVide", "");
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Indice d'état vide de la table (placeholder gris), vide en nominal.
    ReadOnlyStringProperty etatVideProperty() {
        return etatVide.getReadOnlyProperty();
    }

    /// Retour de la dernière opération (avec sévérité), [RetourOperation#AUCUN] en nominal.
    ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Met à jour l'indice d'état vide : `texteSiVide` quand la table est vide, rien sinon.
    void majEtatVide(boolean vide, String texteSiVide) {
        etatVide.set(vide ? texteSiVide : "");
    }

    void succes(String texte) {
        retour.set(RetourOperation.succes(texte));
    }

    /// Retour de succès d'un import tolérant : nombre d'observations chargées, et le cas échéant le
    /// nombre de lignes ignorées (audio absent) et de taxons hors référentiel auto-créés.
    void succesImport(BilanImport bilan) {
        StringBuilder resume = new StringBuilder("Import réussi : " + bilan.importees() + " observation(s) chargée(s)");
        if (bilan.ignorees() > 0) {
            resume.append(" · ").append(bilan.ignorees()).append(" ignorée(s) (audio absent)");
        }
        if (bilan.taxonsHorsReferentiel() > 0) {
            resume.append(" · ").append(bilan.taxonsHorsReferentiel()).append(" taxon(s) hors référentiel");
        }
        succes(resume.append('.').toString());
    }

    void info(String texte) {
        retour.set(RetourOperation.info(texte));
    }

    void erreur(String texte) {
        retour.set(RetourOperation.erreur(texte));
    }

    /// Retour d'un export : bilan (succès) ou erreur d'écriture selon `reussi`, ignoré si `message` nul.
    void export(boolean reussi, String message) {
        if (message != null) {
            retour.set(reussi ? RetourOperation.succes(message) : RetourOperation.erreur(message));
        }
    }

    /// Efface le retour d'opération (après une action réussie qui n'a pas de bilan à afficher).
    void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }

    /// Remet les deux canaux à zéro (réouverture de la vue).
    void reinitialiser() {
        etatVide.set("");
        retour.set(RetourOperation.AUCUN);
    }
}
