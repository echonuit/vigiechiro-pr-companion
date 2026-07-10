package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import java.io.File;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/// Actions d'**export** de la vue audio (CSV `_Vu`, CSV des observations affichées, bibliothèque de sons de
/// référence) : sélection du fichier / dossier puis délégation au [AudioViewModel]. Isolé du
/// [SonsValidationController] (même patron que [ImportTadarida] / [ImportVigieChiroUI]) pour le garder léger
/// (contrainte NCSS). La fenêtre parente des sélecteurs est passée par l'appelant.
final class ExportsAudioUI {

    private ExportsAudioUI() {}

    /// Exporte le CSV `_Vu` réinjectable du jeu de résultats courant (R17, R24).
    static void exporterVu(AudioViewModel viewModel, Window fenetre) {
        File fichier = ChoixFichierCsv.selecteur("Exporter le fichier _Vu (réinjectable)", "resultats_Vu.csv")
                .showSaveDialog(fenetre);
        if (fichier != null) {
            viewModel.exporterVu(fichier.toPath());
        }
    }

    /// Exporte les observations **affichées** (après filtres) en CSV.
    static void exporterObservations(AudioViewModel viewModel, Window fenetre) {
        File fichier = ChoixFichierCsv.selecteur("Exporter les observations affichées (CSV)", "observations.csv")
                .showSaveDialog(fenetre);
        if (fichier != null) {
            viewModel.exporterObservations(fichier.toPath());
        }
    }

    /// Exporte la **bibliothèque de sons de référence** vers un dossier choisi (P10).
    static void exporterBibliotheque(AudioViewModel viewModel, Window fenetre) {
        DirectoryChooser selecteur = new DirectoryChooser();
        selecteur.setTitle("Exporter la bibliothèque de sons de référence");
        File dossier = selecteur.showDialog(fenetre);
        if (dossier != null) {
            viewModel.exporterBibliotheque(dossier.toPath());
        }
    }
}
