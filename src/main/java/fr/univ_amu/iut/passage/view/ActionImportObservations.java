package fr.univ_amu.iut.passage.view;

import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.Objects;

/// Action IHM « Importer les observations » (#1350), extraite de [PassageController] (pur câblage, PMD
/// GodClass) : récupère depuis Vigie-Chiro les observations produites par l'analyse Tadarida, **hors du
/// fil JavaFX** (réseau) sous le voile d'occupation de l'écran, puis présente le compte rendu.
///
/// L'action vit sur **M-Passage** et non plus dans la modale « Modifier le passage » : celle-ci est
/// fermée sur un passage déposé (garde de renommage #1134), c'est-à-dire exactement le cas où l'import
/// a le plus de sens. L'import ne renomme rien, il n'a donc pas à subir cette garde.
///
/// Le bouton n'est **pas** gardé par l'état de l'analyse, et c'est délibéré : si elle n'est pas
/// terminée, l'import répond **pourquoi** (analyse en cours, jamais lancée, en échec…, #1264). Un refus
/// qui renseigne vaut mieux qu'un bouton grisé qui laisse deviner — d'où un compte rendu même quand
/// rien n'a été importé.
final class ActionImportObservations {

    private final PassageViewModel viewModel;
    private final IndicateurOccupation occupation;
    private final NotificateurModifiable notificateur;

    /// @param viewModel ViewModel de M-Passage (porte l'import)
    /// @param occupation voile d'occupation de l'écran (#1213) : l'import est un appel réseau
    /// @param notificateur porteur de compte rendu partagé de l'écran (double capturant en test)
    ActionImportObservations(
            PassageViewModel viewModel, IndicateurOccupation occupation, NotificateurModifiable notificateur) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.occupation = Objects.requireNonNull(occupation, "occupation");
        this.notificateur = Objects.requireNonNull(notificateur, "notificateur");
    }

    /// Importe les observations de la nuit, puis rend compte. Rien à recharger sur M-Passage : l'import
    /// écrit des observations, que cet écran n'affiche pas (ses statistiques portent sur l'audio).
    void importer() {
        occupation.occuper(
                "Import des observations…",
                viewModel.importObservations()::importer,
                compteRendu ->
                        notificateur.notifier(NiveauNotification.INFORMATION, "Import des observations", compteRendu),
                echec -> notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Import impossible", message(echec)));
    }

    private static String message(Throwable echec) {
        return echec.getMessage() != null ? echec.getMessage() : echec.toString();
    }
}
