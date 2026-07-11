package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.LigneSuivi;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// Ligne **observable** de la table de suivi de l'import (#947) : un enregistrement original de la nuit
/// et son avancement. Spécialise le socle [LigneSuivi] avec les colonnes propres à l'import : le **nom
/// du fichier** (fixé dès le plan) et l'**étape en cours** (« Copie » puis « Transformation », vide au
/// repos), qui précise ce que la barre « en cours » est en train de faire.
///
/// Les mutateurs sont réservés au pilote ([SuiviLignesFichiers]), à appeler sur le **fil JavaFX**.
public final class LigneFichierImport extends LigneSuivi {

    private final String nomFichier;
    private final ReadOnlyStringWrapper etape;

    /// Crée une ligne « en attente », sans étape en cours.
    LigneFichierImport(int numero, String nomFichier) {
        super(numero);
        this.nomFichier = nomFichier;
        this.etape = new ReadOnlyStringWrapper(this, "etape", "");
    }

    /// Nom du fichier original tel qu'il apparaît sur la carte SD.
    public String nomFichier() {
        return nomFichier;
    }

    /// Étape en cours (« Copie », « Transformation »), vide en attente comme une fois la ligne terminée
    /// ou en échec.
    public ReadOnlyStringProperty etapeProperty() {
        return etape.getReadOnlyProperty();
    }

    /// Pose l'étape en cours (réservé au pilote, fil JavaFX).
    void poserEtape(String valeur) {
        etape.set(valeur);
    }
}
