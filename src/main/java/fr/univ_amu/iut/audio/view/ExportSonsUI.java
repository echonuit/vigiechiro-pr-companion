package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Le geste « **Exporter les observations et les sons (ZIP)** » (#2793, EPIC #2790) : désignation de
/// l'archive, préparation sur le fil JavaFX (sous-ensemble figé + sonde de la destination, #2426),
/// puis écriture **hors fil** dans la modale de progression annulable - l'annonce du contenu
/// (observations · sons · volume) en est la première ligne, la copie fichier par fichier avance la
/// barre, et l'archive partielle ne survit ni à l'annulation ni à l'échec.
///
/// Même patron que [ImportVigieChiroUI] / [PublicationCorrectionsUI] : la modale `WINDOW_MODAL`
/// bloque déjà toute navigation pendant l'écriture, et l'annulation ne corrompt rien - pas
/// d'opération critique à poser en plus.
final class ExportSonsUI {

    private ExportSonsUI() {}

    /// Enchaîne désignation → préparation → modale de progression → restitution (bilan, annulation
    /// ou échec, triés par le dialogue).
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
}
