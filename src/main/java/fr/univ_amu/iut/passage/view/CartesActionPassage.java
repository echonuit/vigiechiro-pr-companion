package fr.univ_amu.iut.passage.view;

import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/// Câblage des **cartes d'action** de l'écran passage (#2745), extrait de [PassageController].
///
/// Trois règles s'y répètent, et c'est ce qui en fait une unité :
///
///  1. **Disponibilité** : une carte dont la feature est coupée (feature-flag #1087) est *retirée*,
///     pas laissée grisée sans effet - le contrat d'ouverture est alors absent ;
///  2. **Activation** : ce qui reste se grise selon l'état du passage ;
///  3. **Explication** : chaque grisage porte son motif sur l'**enveloppe** et non sur le bouton, un
///     Button désactivé n'affichant pas de tooltip (#789).
///
/// ⚠️ Pourquoi une classe et non une sous-vue, contrairement aux quatre extractions précédentes du
/// lot : mesuré, le poids de `PassageController` était dans ses **méthodes** (47 points) plus que dans
/// ses champs (32), et son FXML n'offrait aucun bloc cohésif de plus de onze `fx:id` - les actions y
/// sont réparties entre l'en-tête et le bas d'écran. Un découpage de vue aurait déplacé peu de chose
/// en ajoutant une indirection ; l'Extract Class, lui, emporte les cinquante instructions.
final class CartesActionPassage {

    private CartesActionPassage() {}

    /// Les nœuds des cartes, regroupés : un Parameter-Object plutôt que dix-huit arguments du même
    /// type, qui se rempliraient un jour dans le désordre sans que rien ne le dise.
    record Cartes(
            Button verifier,
            Button diagnostic,
            Button activite,
            Button synthese,
            Button validation,
            Button depot,
            StackPane enveloppeDepot,
            Button supprimer,
            StackPane enveloppeSupprimer,
            Button ouvrirPortail,
            StackPane enveloppeOuvrirPortail,
            Button rattachement,
            StackPane enveloppeRattachement,
            Button annulerDepot,
            StackPane enveloppeAnnulerDepot,
            Button reactiver,
            StackPane enveloppeReactiver,
            Label indiceAction) {}

    /// Ce que les feature-flags rendent disponible. Booléens et non `Optional` : la carte n'a pas
    /// besoin du contrat, seulement de savoir s'il existe.
    record Disponibilites(
            boolean verification, boolean diagnostic, boolean activite, boolean synthese, boolean depot) {}

    static void cabler(
            Cartes cartes, PassageViewModel viewModel, Disponibilites dispo, StringProperty lienParticipation) {
        cablerFeatures(cartes, viewModel, dispo);
        cablerDepot(cartes, viewModel, dispo);
        cablerGestionDuPassage(cartes, viewModel, lienParticipation);
        cablerReprises(cartes, viewModel);
        cartes.indiceAction().textProperty().bind(viewModel.motifs().verification());
    }

    /// Les quatre cartes de consultation : présentes seulement si leur feature l'est.
    private static void cablerFeatures(Cartes cartes, PassageViewModel viewModel, Disponibilites dispo) {
        cartes.verifier()
                .disableProperty()
                .bind(viewModel.verificationDisponibleProperty().not());
        afficher(cartes.verifier(), dispo.verification());
        afficher(cartes.diagnostic(), dispo.diagnostic());
        afficher(cartes.activite(), dispo.activite());
        afficher(cartes.synthese(), dispo.synthese());
        cartes.validation().disableProperty().bind(viewModel.validationVerrouilleeProperty());
    }

    /// « Préparer le dépôt » : présente si la feature `lot` l'est, active une fois la nuit vérifiée.
    private static void cablerDepot(Cartes cartes, PassageViewModel viewModel, Disponibilites dispo) {
        cartes.depot()
                .disableProperty()
                .bind(viewModel.depotDisponibleProperty().not());
        IndicateurBlocage.expliquer(
                cartes.enveloppeDepot(),
                Bindings.when(viewModel.depotDisponibleProperty())
                        .then("Préparer le dépôt : constituer le jeu Tadarida à téléverser.")
                        .otherwise(Bindings.when(viewModel.motifs().depot().isEmpty())
                                .then("Le dépôt se prépare une fois la nuit vérifiée.")
                                .otherwise(viewModel.motifs().depot())));
        afficher(cartes.enveloppeDepot(), dispo.depot());
    }

    /// Supprimer, voir la participation, modifier : trois gestes sur le passage lui-même.
    private static void cablerGestionDuPassage(
            Cartes cartes, PassageViewModel viewModel, StringProperty lienParticipation) {
        // Suppression gatée en amont (#789) : un passage déposé n'est pas supprimable (le service le
        // refuse). Plutôt que de laisser découvrir le refus APRÈS la confirmation, on grise et on explique.
        cartes.supprimer()
                .disableProperty()
                .bind(viewModel.suppressionPossibleProperty().not());
        IndicateurBlocage.expliquer(
                cartes.enveloppeSupprimer(),
                Bindings.when(viewModel.suppressionPossibleProperty())
                        .then("Supprimer définitivement ce passage et toute sa nuit (séquences, relevés).")
                        .otherwise("Suppression impossible : ce passage est déposé sur Vigie-Chiro."
                                + " Annulez d'abord le dépôt."));

        // « Voir la participation » (#1124) : actif seulement quand le passage est lié ; désactivé, il
        // documente ce qui manque (affordance #789) plutôt que de disparaître.
        cartes.ouvrirPortail().disableProperty().bind(lienParticipation.isEmpty());
        IndicateurBlocage.expliquer(
                cartes.enveloppeOuvrirPortail(),
                Bindings.when(lienParticipation.isNotEmpty())
                        .then("Ouvre la participation liée sur le portail Vigie-Chiro (navigateur).")
                        .otherwise("Ce passage n'est pas encore lié à une participation Vigie-Chiro :"
                                + " elle est créée à l'import (connecté) ou au premier dépôt."));

        // « Modifier le passage » ouvre toujours la modale (météo et micro éditables à tout statut). Le
        // renommage (année/n°) est verrouillé sur un passage déposé (#1134), mais ce verrou vit DANS la
        // modale : on ne grise donc que s'il n'y a aucun passage chargé.
        cartes.rattachement().disableProperty().bind(viewModel.statutProperty().isNull());
        IndicateurBlocage.expliquer(
                cartes.enveloppeRattachement(),
                Bindings.when(viewModel.renommagePossibleProperty())
                        .then("Modifier le passage : année, n° (renomme la nuit), météo et micro.")
                        .otherwise("Modifier le passage : météo et micro. L'année et le n° sont verrouillés"
                                + " (passage déposé, identité serveur)."));
    }

    /// Annuler le dépôt et réactiver : les deux marches arrière du workflow.
    private static void cablerReprises(Cartes cartes, PassageViewModel viewModel) {
        // « Annuler le dépôt » n'a de sens que sur un passage déposé : le bouton n'apparaît (et n'occupe
        // de place) que dans ce cas, au lieu de rester grisé en permanence dans la barre d'actions.
        cartes.enveloppeAnnulerDepot().visibleProperty().bind(viewModel.annulationDepotPertinenteProperty());
        cartes.enveloppeAnnulerDepot().managedProperty().bind(viewModel.annulationDepotPertinenteProperty());
        cartes.annulerDepot()
                .disableProperty()
                .bind(viewModel.annulationDepotDisponibleProperty().not());
        IndicateurBlocage.expliquer(
                cartes.enveloppeAnnulerDepot(),
                Bindings.when(viewModel.annulationDepotDisponibleProperty())
                        .then("Ramener ce passage de « Déposé » à « Prêt à déposer », sans toucher aux"
                                + " validations déjà saisies.")
                        .otherwise(viewModel.motifs().annulationDepot()));

        // « Réactiver ce passage » (#1302) : gaté en amont (#789). L'action n'a de sens que s'il manque
        // de l'audio (fichiers déplacés ou supprimés, disque incomplet).
        cartes.reactiver()
                .disableProperty()
                .bind(viewModel.reactivationPossibleProperty().not());
        IndicateurBlocage.expliquer(
                cartes.enveloppeReactiver(),
                Bindings.when(viewModel.reactivationPossibleProperty())
                        .then("Réactiver ce passage : réimporte les fichiers d'origine et les rebranche,"
                                + " après vérification que ce sont bien les mêmes.")
                        .otherwise(viewModel.motifs().reactivation()));
    }

    /// Retire un nœud de la mise en page quand sa feature est coupée : masqué **et** démanagé, pour
    /// qu'il ne laisse pas de trou. Les deux vont toujours de pair.
    private static void afficher(javafx.scene.Node noeud, boolean disponible) {
        noeud.setVisible(disponible);
        noeud.setManaged(disponible);
    }
}
