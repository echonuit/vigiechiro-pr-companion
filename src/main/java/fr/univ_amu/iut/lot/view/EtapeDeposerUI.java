package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.IconeSelonEtat;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.LibelleRetour;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.lot.viewmodel.TraitementViewModel;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/// Câblage du **bouton de l'étape ④** de M-Lot, extrait de [LotController] (#1263) : à lui seul, il porte
/// trois règles qui ont chacune leur histoire, et le contrôleur n'a pas à les héberger.
///
/// 1. **Son libellé change de sens** (#984) : « Marquer déposé » quand le dépôt est manuel, « Lancer la
///    participation » dès qu'une participation est liée : l'application ayant alors déjà déposé la nuit.
/// 2. **Il reste cliquable après un dépôt partiel** : c'est justement le moment de lancer le calcul.
/// 3. **Il se verrouille quand la nuit a déjà été analysée** (#1261/#1263) : relancer supprimerait les
///    observations du serveur, qui ne pourraient pas être recalculées (l'audio n'est pas conservé après un
///    dépôt en archives, #1244).
///
/// Et il **dit toujours pourquoi** il est désactivé (#789) : un bouton muet est une impasse.
final class EtapeDeposerUI {

    /// Ce qu'on fait d'une nuit déjà analysée, quelle que soit la forme de son dépôt.
    private static final String SUITE_D_UN_BLOCAGE = " Importez-les plutôt dans « Sons & validation ». Pour forcer"
            + " malgré tout, après un échec, par exemple : lancer-traitement-vigiechiro --forcer.";

    private EtapeDeposerUI() {}

    /// Les nœuds de l'étape ④ : son bouton, son icône, l'enveloppe qui porte l'explication d'un blocage,
    /// le titre et la consigne qui disent le même geste que le bouton (#5676), et le résultat du
    /// lancement, sous le bouton qui l'a demandé (#5682).
    record Vue(Button bouton, FontIcon icone, StackPane enveloppe, Label titre, Label consigne, Label retour) {

        Vue {
            Objects.requireNonNull(bouton, "bouton");
            Objects.requireNonNull(icone, "icone");
            Objects.requireNonNull(enveloppe, "enveloppe");
            Objects.requireNonNull(titre, "titre");
            Objects.requireNonNull(consigne, "consigne");
            Objects.requireNonNull(retour, "retour");
        }
    }

    static void cabler(Vue vue, LotViewModel lot, DepotViewModel depot, TraitementViewModel traitement) {
        LibelleRetour.installer(vue.retour(), depot.retourLancementProperty());
        Button bouton = vue.bouton();
        FontIcon icone = vue.icone();
        StackPane enveloppe = vue.enveloppe();
        // Le titre de l'étape suit le bouton : resté « Marquer le passage déposé » au-dessus de « Lancer la
        // participation », il laissait croire à deux gestes (#5676).
        vue.titre()
                .textProperty()
                // Le numéro suit l'étape des archives (#5824) : sans elle, celle-ci est la troisième.
                .bind(Bindings.when(lot.etapeArchivesOfferteProperty())
                        .then("4. ")
                        .otherwise("3. ")
                        .concat(Bindings.when(depot.participationLieeProperty())
                                .then("Lancer la participation")
                                .otherwise("Marquer le passage déposé")));
        vue.consigne()
                .textProperty()
                .bind(Bindings.when(depot.participationLieeProperty())
                        .then("Demandez à Vigie-Chiro d'analyser la nuit déposée : ses observations arriveront"
                                + " une fois l'analyse terminée.")
                        .otherwise("Une fois le téléversement effectué sur Vigie-Chiro, enregistrez le dépôt."));
        // L'icône suit le sens du bouton : une fusée quand il lance la participation, une coche quand il
        // marque le dépôt à la main. Une icône figée dirait le contraire du mot une fois sur deux.
        IconeSelonEtat.lier(icone, depot.participationLieeProperty(), FontAwesomeSolid.ROCKET, FontAwesomeSolid.CHECK);
        bouton.disableProperty()
                .bind(Bindings.when(depot.participationLieeProperty())
                        // Mode « Lancer la participation » : cliquable quel que soit le statut de dépôt (même
                        // « Dépôt en cours » après une annulation ou un dépôt partiel), sauf pendant une
                        // opération en cours, et sauf si la nuit a déjà été analysée.
                        // Une analyse demandée ne s'offre pas à être relancée : deux clics de plus avaient
                        // reçu « Already PLANIFIE » pendant la recette de #5597 (#5682).
                        .then(depot.enCoursProperty()
                                .or(lot.generationEnCoursProperty())
                                .or(traitement.relanceBloqueeProperty())
                                .or(traitement.analyseDemandeeProperty()))
                        // Mode « Marquer déposé » : garde d'origine (« Prêt à déposer », hors génération/dépôt).
                        .otherwise(lot.peutDeposerProperty()
                                .not()
                                .or(lot.generationEnCoursProperty())
                                .or(depot.enCoursProperty())));
        bouton.textProperty()
                .bind(Bindings.when(depot.participationLieeProperty())
                        .then("Lancer la participation")
                        .otherwise("Marquer déposé"));
        IndicateurBlocage.expliquer(
                enveloppe,
                Bindings.when(traitement.relanceBloqueeProperty())
                        // Le blocage vaut pour les deux formes ; sa raison, non (#5824). Connecté, l'étape des
                        // archives n'est offerte qu'en forme ZIP : elle dit donc laquelle est en jeu.
                        .then(Bindings.when(lot.etapeArchivesOfferteProperty())
                                .then("Cette nuit a déjà été analysée par Vigie-Chiro. La relancer effacerait ses"
                                        + " observations côté serveur, qui ne pourraient pas être recalculées"
                                        + " (l'audio n'est pas conservé après un dépôt en archives)."
                                        + SUITE_D_UN_BLOCAGE)
                                .otherwise("Cette nuit a déjà été analysée par Vigie-Chiro. La relancer effacerait"
                                        + " ses observations côté serveur avant de les recalculer."
                                        + SUITE_D_UN_BLOCAGE))
                        .otherwise(Bindings.when(traitement.analyseDemandeeProperty())
                                .then("L'analyse de cette nuit est demandée à Vigie-Chiro : suivez-la dans la carte"
                                        + " « Traitement Vigie-Chiro » ci-dessous.")
                                .otherwise(Bindings.when(lot.peutDeposerProperty()
                                                .and(lot.generationEnCoursProperty()
                                                        .not()))
                                        .then("Marquer le passage comme déposé sur Vigie-Chiro.")
                                        .otherwise("À faire une fois la nuit téléversée sur Vigie-Chiro."))));
    }
}
