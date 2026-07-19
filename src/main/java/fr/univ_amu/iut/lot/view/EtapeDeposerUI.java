package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.IconeSelonEtat;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.lot.viewmodel.TraitementViewModel;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/// Câblage du **bouton de l'étape ④** de M-Lot, extrait de [LotController] (#1263) : à lui seul, il porte
/// trois règles qui ont chacune leur histoire, et le contrôleur n'a pas à les héberger.
///
/// 1. **Son libellé change de sens** (#984) : « Marquer déposé » quand le dépôt est manuel, « Lancer la
///    participation » dès qu'une participation est liée — l'application ayant alors déjà déposé la nuit.
/// 2. **Il reste cliquable après un dépôt partiel** : c'est justement le moment de lancer le calcul.
/// 3. **Il se verrouille quand la nuit a déjà été analysée** (#1261/#1263) : relancer supprimerait les
///    observations du serveur, qui ne pourraient pas être recalculées (l'audio n'est pas conservé après un
///    dépôt en archives, #1244).
///
/// Et il **dit toujours pourquoi** il est désactivé (#789) : un bouton muet est une impasse.
final class EtapeDeposerUI {

    private EtapeDeposerUI() {}

    static void cabler(
            Button bouton,
            FontIcon icone,
            StackPane enveloppe,
            LotViewModel lot,
            DepotViewModel depot,
            TraitementViewModel traitement) {
        Objects.requireNonNull(bouton, "bouton");
        Objects.requireNonNull(icone, "icone");
        Objects.requireNonNull(enveloppe, "enveloppe");
        // L'icône suit le sens du bouton : une fusée quand il lance la participation, une coche quand il
        // marque le dépôt à la main. Une icône figée dirait le contraire du mot une fois sur deux.
        IconeSelonEtat.lier(icone, depot.participationLieeProperty(), FontAwesomeSolid.ROCKET, FontAwesomeSolid.CHECK);
        bouton.disableProperty()
                .bind(Bindings.when(depot.participationLieeProperty())
                        // Mode « Lancer la participation » : cliquable quel que soit le statut de dépôt (même
                        // « Dépôt en cours » après une annulation ou un dépôt partiel), sauf pendant une
                        // opération en cours — et sauf si la nuit a déjà été analysée.
                        .then(depot.enCoursProperty()
                                .or(lot.generationEnCoursProperty())
                                .or(traitement.relanceBloqueeProperty()))
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
                        .then("Cette nuit a déjà été analysée par Vigie-Chiro. La relancer effacerait ses"
                                + " observations côté serveur, qui ne pourraient pas être recalculées (l'audio"
                                + " n'est pas conservé après un dépôt en archives). Importez-les plutôt dans"
                                + " « Sons & validation ». Pour forcer malgré tout — après un échec, par"
                                + " exemple : lancer-traitement-vigiechiro --forcer.")
                        .otherwise(Bindings.when(lot.peutDeposerProperty()
                                        .and(lot.generationEnCoursProperty().not()))
                                .then("Marquer le passage comme déposé sur Vigie-Chiro.")
                                .otherwise("À faire une fois la nuit téléversée sur Vigie-Chiro.")));
    }
}
