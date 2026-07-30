package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Les gestes qui produisent une **archive de sons** : « Exporter les observations et les sons (ZIP) »
/// (#2793, EPIC #2790) et « Exporter la bibliothèque de sons de référence » (P10, harmonisé à la
/// clôture de l'EPIC). Tous deux suivent le même enchaînement : désignation de l'archive, préparation
/// sur le fil JavaFX (sonde de la destination, #2426), puis écriture **hors fil** dans la modale de
/// progression annulable - l'annonce du contenu en est la première ligne, la copie fichier par fichier
/// avance la barre, et l'archive partielle ne survit ni à l'annulation ni à l'échec.
///
/// Même patron que [ImportVigieChiroUI] / [PublicationCorrectionsUI] : la modale `WINDOW_MODAL`
/// bloque déjà toute navigation pendant l'écriture, et l'annulation ne corrompt rien - pas
/// d'opération critique à poser en plus.
final class ExportSonsUI {

    private ExportSonsUI() {}

    /// Enchaîne désignation → préparation → modale de progression → restitution (bilan, annulation
    /// ou échec, triés par le dialogue) pour les **observations affichées et leurs sons**.
    static void lancer(
            AudioViewModel viewModel, SelecteurFichier selecteur, SuiviOperation dialogue, Supplier<Window> fenetre) {
        selecteur
                .enregistrerFichier(
                        "Exporter les observations et les sons (ZIP)",
                        "observations-sons.zip",
                        FiltreFichier.archiveZip())
                .ifPresent(destination -> viewModel
                        .exports()
                        .preparer(destination)
                        .ifPresent(lignes -> dialogue.lancer(
                                fenetre.get(),
                                "Export des observations et des sons",
                                (progres, jeton) -> viewModel.exports().exporter(lignes, destination, progres, jeton),
                                viewModel.exports()::confirmer,
                                viewModel.exports()::annulee,
                                viewModel.exports()::echec)));
    }

    /// Même enchaînement pour la **bibliothèque de sons de référence** (P10) : elle emporte les
    /// séquences marquées « référence » de toute la saison, ce qui pèse autant qu'un export
    /// d'observations - elle mérite donc la même progression annulable et le même bilan chiffré.
    static void lancerBibliotheque(
            AudioViewModel viewModel, SelecteurFichier selecteur, SuiviOperation dialogue, Supplier<Window> fenetre) {
        selecteur
                .enregistrerFichier(
                        "Exporter la bibliothèque de sons de référence (ZIP)",
                        "bibliotheque-sons.zip",
                        FiltreFichier.archiveZip())
                .ifPresent(destination -> viewModel
                        .exports()
                        .preparer(destination)
                        .ifPresent(ignore -> dialogue.lancer(
                                fenetre.get(),
                                "Export de la bibliothèque de sons",
                                (progres, jeton) -> viewModel.exports().bibliotheque(destination, progres, jeton),
                                viewModel.exports()::confirmer,
                                viewModel.exports()::annulee,
                                viewModel.exports()::echec)));
    }
}
