package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.bibliotheque.model.ExportBiblioSons;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.validation.model.ExportObservationsCsv;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Exports de la vue audio unifiée, sortis du [AudioViewModel] : le CSV `_Vu` réinjectable d'un passage
/// (source `ParPassage`, via `validation`) et la **bibliothèque de sons de référence** (source
/// `References`, via `bibliotheque`). Regroupés ici pour que le ViewModel reste cohésif (PMD GodClass) et
/// pour matérialiser que la feature `audio` réutilise les **modèles** de `validation` **et** `bibliotheque`
/// (puits : aucun retour vers `audio`, graphe de slices acyclique).
///
/// Chaque méthode renvoie un [ResultatExport] (réussite + message d'état) que le ViewModel restitue tel
/// quel ; les erreurs d'écriture sont capturées et transformées en message, jamais propagées.
final class ExporteurAudio {

    /// Réussite d'un export et message à afficher (`message` `null` = rien à dire, ex. appel ignoré).
    record ResultatExport(boolean reussi, String message) {}

    private static final ResultatExport IGNORE = new ResultatExport(false, null);

    private final ServiceValidation validation;
    private final ServiceBibliotheque bibliotheque;

    ExporteurAudio(ServiceValidation validation, ServiceBibliotheque bibliotheque) {
        this.validation = Objects.requireNonNull(validation, "validation");
        this.bibliotheque = Objects.requireNonNull(bibliotheque, "bibliotheque");
    }

    /// Exporte le CSV `_Vu` du jeu de résultats `idResultats` vers `destination` (R17, R24). Ignoré si
    /// l'un des deux est nul (pas de résultats chargés ou aucun fichier choisi).
    ResultatExport vu(Long idResultats, Path destination, boolean inclureMode) {
        if (idResultats == null || destination == null) {
            return IGNORE;
        }
        try {
            Path ecrit = validation.exporter(idResultats, destination, inclureMode);
            return new ResultatExport(true, "Fichier _Vu exporté : " + ecrit.getFileName());
        } catch (RuntimeException echec) {
            return new ResultatExport(false, echec.getMessage());
        }
    }

    /// Exporte les `lignes` d'observations (typiquement le **sous-ensemble filtré** affiché) en **CSV**
    /// vers `destination` (#149), pour l'analyse/interop hors application. Ignoré si `destination` est nul.
    ResultatExport observations(List<LigneObservationAudio> lignes, Path destination) {
        if (destination == null) {
            return IGNORE;
        }
        try {
            Path ecrit = ExportObservationsCsv.ecrire(lignes, destination);
            return new ResultatExport(true, lignes.size() + " observation(s) exportée(s) : " + ecrit.getFileName());
        } catch (IOException | RuntimeException echec) {
            return new ResultatExport(false, echec.getMessage());
        }
    }

    /// Exporte la bibliothèque de sons de référence vers le dossier `destination` (P10) : récapitulatif
    /// CSV + copie des fichiers son existants. Ignoré si `destination` est nul.
    ResultatExport bibliotheque(Path destination) {
        if (destination == null) {
            return IGNORE;
        }
        try {
            ExportBiblioSons export = bibliotheque.exporterBibliotheque();
            int copies = export.exporterVers(destination);
            return new ResultatExport(
                    true,
                    "Bibliothèque exportée vers " + destination + " : " + copies + " fichier(s) son + le récapitulatif "
                            + ExportBiblioSons.NOM_CSV + ".");
        } catch (RuntimeException echec) {
            return new ResultatExport(false, echec.getMessage());
        }
    }
}
