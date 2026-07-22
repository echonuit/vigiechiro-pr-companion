package fr.univ_amu.iut.audio.view;

import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/// Câblage des **encarts qui remplacent le lecteur** quand l'écoute n'a pas de sens, regroupé pour
/// alléger le [SonsValidationController] (pur câblage, seuil PMD NcssCount) comme l'a été
/// [PanneauEcouteAudio].
///
/// Deux situations distinctes, et il importe qu'elles le restent à l'écran :
///
/// - **fichier absent** (#1301) : il n'y a rien à lire ; les observations restent consultables, et
///   réimporter les fichiers d'origine rend l'écoute ;
/// - **fichier présent mais substitué** (#2254) : il y aurait quelque chose à lire, et c'est
///   précisément le piège. Écouter ou valider dessus donnerait un résultat **faux et silencieux**.
///   On n'offre donc pas le lecteur, et on dit **pourquoi** et **quoi faire**.
///
/// Le lecteur n'est visible que si **ni** l'un **ni** l'autre.
final class EncartsEcouteAudio {

    private EncartsEcouteAudio() {}

    static void installer(
            AudioView audioView,
            VBox encartAudioManquant,
            VBox encartAudioDivergent,
            Label lblMotifDivergence,
            AudioViewModel viewModel) {
        encartAudioManquant.visibleProperty().bind(viewModel.etatEcoute().audioManquantProperty());
        encartAudioManquant.managedProperty().bind(viewModel.etatEcoute().audioManquantProperty());

        encartAudioDivergent.visibleProperty().bind(viewModel.etatEcoute().audioDivergentProperty());
        encartAudioDivergent.managedProperty().bind(viewModel.etatEcoute().audioDivergentProperty());
        lblMotifDivergence
                .textProperty()
                .bind(viewModel
                        .etatEcoute()
                        .motifDivergenceProperty()
                        .map(motif -> "Le fichier trouvé au chemin attendu n'est pas celui qui a produit ces"
                                + " observations (" + motif + "). Écouter ou valider dessus donnerait un résultat"
                                + " faux. Réactivez ce passage depuis le dossier qui contient les bons fichiers."));

        audioView
                .visibleProperty()
                .bind(viewModel
                        .etatEcoute()
                        .audioManquantProperty()
                        .or(viewModel.etatEcoute().audioDivergentProperty())
                        .not());
    }
}
