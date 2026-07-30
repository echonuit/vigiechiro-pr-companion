package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.bibliotheque.model.ExportBiblioSons;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.validation.model.ExportObservationsCsv;
import fr.univ_amu.iut.validation.model.ExportObservationsEtSons;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
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
public final class ExporteurAudio {

    /// Réussite d'un export et message à afficher (`message` `null` = rien à dire, ex. appel ignoré).
    record ResultatExport(boolean reussi, String message) {}

    private static final ResultatExport IGNORE = new ResultatExport(false, null);

    private final ServiceValidation validation;
    private final ServiceBibliotheque bibliotheque;
    private final ExportObservationsEtSons exportSons;

    public ExporteurAudio(
            ServiceValidation validation, ServiceBibliotheque bibliotheque, ExportObservationsEtSons exportSons) {
        this.validation = Objects.requireNonNull(validation, "validation");
        this.bibliotheque = Objects.requireNonNull(bibliotheque, "bibliotheque");
        this.exportSons = Objects.requireNonNull(exportSons, "exportSons");
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
            // Le référentiel de conservation vient du service déjà tenu ici : la colonne « Espèce à
            // enjeu » (#2353) doit dire la même chose que le repère de l'écran.
            MarqueurEspecesAEnjeu marqueur = new MarqueurEspecesAEnjeu(validation::especesPrioritaires);
            Path ecrit = ExportObservationsCsv.ecrire(lignes, destination, marqueur::aEnjeu);
            return new ResultatExport(true, lignes.size() + " observation(s) exportée(s) : " + ecrit.getFileName());
        } catch (IOException | RuntimeException echec) {
            return new ResultatExport(false, echec.getMessage());
        }
    }

    /// Exporte les `lignes` affichées **et leurs sons** en archive ZIP vers `destination` (#2793) :
    /// même CSV que [#observations], plus les séquences dédupliquées sous `sons/<session>/`.
    /// **Bloquant** (copie de fichiers) : à appeler hors du fil JavaFX, dans la modale de progression.
    /// Contrairement aux autres exports, les échecs **remontent** (l'annulation vers l'issue « annulé »
    /// de la modale, l'erreur vers son issue « échec ») : le dialogue les trie, pas un booléen.
    ///
    /// @return le message de bilan à restituer en cas de succès
    String observationsEtSons(
            List<LigneObservationAudio> lignes,
            Path destination,
            java.util.function.Consumer<Progression> progres,
            JetonAnnulation jeton)
            throws IOException {
        MarqueurEspecesAEnjeu marqueur = new MarqueurEspecesAEnjeu(validation::especesPrioritaires);
        ExportObservationsEtSons.Bilan bilan =
                exportSons.exporter(lignes, destination, marqueur::aEnjeu, progres, jeton);
        StringBuilder message = new StringBuilder("Archive créée : " + destination.getFileName() + " - "
                + bilan.observations() + " observation(s), " + bilan.sonsCopies() + " son(s), "
                + String.format(java.util.Locale.FRENCH, "%.1f Mo", bilan.octets() / 1_048_576.0) + ".");
        if (!bilan.sonsIntrouvables().isEmpty()) {
            message.append(" ")
                    .append(bilan.sonsIntrouvables().size())
                    .append(" son(s) introuvable(s), resté(s) hors de l'archive (le CSV les nomme).");
        }
        return message.toString();
    }

    /// Exporte la bibliothèque de sons de référence dans l'archive `destination` (P10). **Bloquant** :
    /// à appeler hors du fil JavaFX, dans la modale de progression - une bibliothèque de saison pèse
    /// plusieurs centaines de mégaoctets.
    ///
    /// @return le message de bilan, à restituer par la vue
    String bibliotheque(Path destination, java.util.function.Consumer<Progression> progres, JetonAnnulation jeton)
            throws IOException {
        ExportBiblioSons export = bibliotheque.exporterBibliotheque();
        ExportBiblioSons.Bilan bilan = export.exporterVers(destination, progres, jeton);
        StringBuilder message = new StringBuilder("Bibliothèque exportée : " + destination.getFileName() + " - "
                + export.nombre() + " référence(s), " + bilan.sonsCopies() + " son(s), "
                + String.format(java.util.Locale.FRENCH, "%.1f Mo", bilan.octets() / 1_048_576.0) + ".");
        if (!bilan.sonsIntrouvables().isEmpty()) {
            message.append(" ")
                    .append(bilan.sonsIntrouvables().size())
                    .append(" son(s) introuvable(s), resté(s) hors de l'archive (le CSV les nomme).");
        }
        return message.toString();
    }
}
