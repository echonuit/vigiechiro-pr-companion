package fr.univ_amu.iut.passage.view;

import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import fr.univ_amu.iut.passage.viewmodel.VerificationDuTraitement;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

/// Câblage du bouton « Vérifier le traitement » de la vue d'un passage (#5862), sorti de
/// [PassageController], qui est au plafond de taille.
///
/// Le bouton n'a de sens que pour une nuit déposée par l'application, et connecté : hors de ces
/// conditions il reste visible, grisé, et son infobulle dit laquelle manque (affordance #789).
final class VerificationDuTraitementUI {

    static final String SANS_PARTICIPATION = "Ce passage n'est pas encore lié à une participation Vigie-Chiro :"
            + " il n'y a pas de traitement à vérifier.";

    static final String HORS_CONNEXION = "Non connecté à Vigie-Chiro : connectez-vous pour vérifier le traitement.";

    static final String CE_QUE_FAIT_LE_GESTE = "Demande à Vigie-Chiro où en est l'analyse de cette nuit, et importe"
            + " ses observations si elle est terminée.";

    private VerificationDuTraitementUI() {}

    /// Le bouton et l'enveloppe qui porte son infobulle.
    record Vue(Button bouton, StackPane enveloppe) {}

    static void cabler(
            Vue vue,
            VerificationDuTraitement verification,
            ReadOnlyStringProperty lienParticipation,
            IndicateurOccupation occupation,
            Supplier<Long> idPassage,
            PassageViewModel viewModel) {
        boolean connecte = verification.disponible();
        if (connecte) {
            vue.bouton().disableProperty().bind(lienParticipation.isEmpty());
        } else {
            vue.bouton().setDisable(true);
        }
        IndicateurBlocage.expliquer(
                vue.enveloppe(),
                Bindings.when(lienParticipation.isEmpty())
                        .then(SANS_PARTICIPATION)
                        .otherwise(connecte ? CE_QUE_FAIT_LE_GESTE : HORS_CONNEXION));
        vue.bouton()
                .setOnAction(evenement -> occupation.occuper(
                        "Vérification du traitement…",
                        () -> verification.verifier(idPassage.get()),
                        viewModel::restituer,
                        echec -> viewModel.restituer(RetourOperation.erreur(echec))));
    }
}
