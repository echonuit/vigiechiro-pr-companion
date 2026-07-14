package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import java.util.Optional;

/// Actions d'**export** de la vue audio (CSV `_Vu`, CSV des observations affichées, bibliothèque de sons de
/// référence) : désignation de la cible puis délégation au [AudioViewModel]. Isolé du
/// [SonsValidationController] (même patron que [ImportTadarida] / [ImportVigieChiroUI]) pour le garder léger
/// (contrainte NCSS).
///
/// La désignation passe par le port [SelecteurFichier] (#1431), porté par l'écran, et non plus par un
/// `FileChooser` / `DirectoryChooser` construit ici : ces trois exports **commencent** par un sélecteur
/// natif, qui fige un test headless. Aucun n'était donc jouable - on ne savait d'eux que leurs items de
/// menu existaient.
final class ExportsAudioUI {

    private ExportsAudioUI() {}

    /// Exporte le CSV `_Vu` réinjectable du jeu de résultats courant (R17, R24).
    static void exporterVu(AudioViewModel viewModel, SelecteurFichier selecteur) {
        selecteur
                .enregistrerFichier("Exporter le fichier _Vu (réinjectable)", "resultats_Vu.csv", FiltreFichier.csv())
                .ifPresent(viewModel::exporterVu);
    }

    /// Exporte les observations **affichées** (après filtres) en CSV.
    static void exporterObservations(AudioViewModel viewModel, SelecteurFichier selecteur) {
        selecteur
                .enregistrerFichier(
                        "Exporter les observations affichées (CSV)", "observations.csv", FiltreFichier.csv())
                .ifPresent(viewModel::exporterObservations);
    }

    /// Exporte la **bibliothèque de sons de référence** vers un dossier choisi (P10).
    static void exporterBibliotheque(AudioViewModel viewModel, SelecteurFichier selecteur) {
        selecteur
                .choisirDossier("Exporter la bibliothèque de sons de référence", Optional.empty())
                .ifPresent(viewModel::exporterBibliotheque);
    }
}
