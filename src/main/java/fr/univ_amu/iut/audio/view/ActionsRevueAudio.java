package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.validation.model.Taxon;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.StackPane;

/// Câblage de la barre d'actions de « Sons & validation » (Valider / Corriger / Référence / Douteux).
/// Pour chaque bouton : sa condition de désactivation selon la ligne sélectionnée, l'icône et le libellé
/// des bascules Référence/Douteux, et le tooltip d'explication du blocage posé sur son enveloppe (#789).
///
/// Extrait de [SonsValidationController] : ce bloc formait une unité cohésive, et l'y garder poussait le
/// contrôleur au-dessus du seuil de God Class (NcssCount). Les boutons restent injectés par le contrôleur
/// (FXML) et lui sont passés ici pour câblage.
final class ActionsRevueAudio {

    private ActionsRevueAudio() {}

    static void configurer(
            AudioViewModel viewModel,
            ComboBox<Taxon> choixTaxon,
            Button btnValider,
            StackPane enveloppeValider,
            Button btnCorriger,
            StackPane enveloppeCorriger,
            Button btnReference,
            StackPane enveloppeReference,
            Button btnDouteux,
            StackPane enveloppeDouteux) {
        // Valider = retenir la proposition Tadarida : seulement s'il y en a une.
        btnValider
                .disableProperty()
                .bind(viewModel.etatSelection().avecTadaridaProperty().not());
        // Corriger = affecter un taxon : sur toute ligne sélectionnée (correction d'une observation OU
        // validation manuelle d'une séquence non identifiée), dès qu'un taxon est choisi.
        btnCorriger
                .disableProperty()
                .bind(viewModel
                        .etatSelection()
                        .presenteProperty()
                        .not()
                        .or(choixTaxon.valueProperty().isNull()));
        // Référence = archiver : seulement ce qui est déjà une observation.
        btnReference
                .disableProperty()
                .bind(viewModel.etatSelection().avecObservationProperty().not());
        // Libellé + icône (étoile dorée) de la bascule selon l'état de l'observation sélectionnée.
        btnReference.setGraphic(CellulesAudio.icone(CellulesAudio.ICONE_REFERENCE, CellulesAudio.STYLE_REFERENCE));
        btnReference
                .textProperty()
                .bind(Bindings.when(viewModel.etatSelection().referenceProperty())
                        .then("Retirer la référence")
                        .otherwise("Marquer référence"));
        // Douteux (#160) = « à repasser » : seulement sur une observation (idObservation non nul), comme la
        // référence. Libellé + icône selon l'état de l'observation sélectionnée.
        btnDouteux
                .disableProperty()
                .bind(viewModel.etatSelection().avecObservationProperty().not());
        btnDouteux.setGraphic(CellulesAudio.icone(CellulesAudio.ICONE_DOUTEUX, CellulesAudio.STYLE_DOUTEUX));
        btnDouteux
                .textProperty()
                .bind(Bindings.when(viewModel.etatSelection().douteuxProperty())
                        .then("Retirer le doute")
                        .otherwise("Marquer douteux"));

        // Tooltips d'explication du blocage (#789), posés sur les enveloppes (un Button désactivé n'affiche
        // pas de tooltip). Le texte suit l'état : ce que fait l'action quand elle est possible, la cause du
        // blocage et le remède sinon.
        IndicateurBlocage.expliquer(
                enveloppeValider,
                Bindings.when(viewModel.etatSelection().avecTadaridaProperty())
                        .then("Retenir la proposition Tadarida (taxon proposé) pour la ligne sélectionnée.")
                        .otherwise("Sélectionnez une ligne qui porte une proposition Tadarida pour la valider."));
        IndicateurBlocage.expliquer(
                enveloppeCorriger,
                Bindings.when(viewModel
                                .etatSelection()
                                .presenteProperty()
                                .and(choixTaxon.valueProperty().isNotNull()))
                        .then("Affecter le taxon choisi à la ligne sélectionnée.")
                        .otherwise("Sélectionnez une ligne puis choisissez un taxon dans la liste pour corriger."));
        IndicateurBlocage.expliquer(
                enveloppeReference,
                Bindings.when(viewModel.etatSelection().avecObservationProperty())
                        .then("Archiver cette observation comme son de référence (ou retirer la référence).")
                        .otherwise("Réservé à une observation validée : validez ou corrigez d'abord la ligne."));
        IndicateurBlocage.expliquer(
                enveloppeDouteux,
                Bindings.when(viewModel.etatSelection().avecObservationProperty())
                        .then("Marquer l'observation « à repasser » (douteuse), ou retirer le doute.")
                        .otherwise("Réservé à une observation validée : validez ou corrigez d'abord la ligne."));
    }
}
