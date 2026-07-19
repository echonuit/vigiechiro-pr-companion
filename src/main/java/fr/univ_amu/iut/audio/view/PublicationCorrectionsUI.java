package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.TriPublication;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.stage.Window;

/// Câblage et déclenchement de la **publication des corrections vers VigieChiro** (#723) sur la vue
/// audio, jumeau de [ImportVigieChiroUI] : isolé du [SonsValidationController] (contrainte NCSS), le
/// contrôleur ne garde que ses champs `@FXML` et une délégation.
///
/// La publication écrit vers l'extérieur et ne s'annule pas côté plateforme : elle passe par une
/// **confirmation récapitulative** construite sur le tri du service (rien n'est envoyé avant l'accord).
/// Le confirmateur est celui **de l'écran** (contrat neutre [Confirmateur] du socle, #1013) : un
/// `Alert.showAndWait` en dur figerait les tests headless.
///
/// Depuis #1838 l'envoi peut être précédé d'un **rapatriement de l'ancrage** (des dizaines de pages) :
/// il passe donc par un [SuiviOperation] (progression + « Annuler ») et non plus par le voile à
/// libellé fixe de [IndicateurOccupation], qui laisserait l'utilisateur devant un écran figé sans
/// recours. L'analyse préalable, elle, reste sous le voile : elle est locale et brève.
final class PublicationCorrectionsUI {

    private PublicationCorrectionsUI() {}

    /// Ce que l'on sait du passage **avant** de demander l'accord, calculé d'un bloc hors fil JavaFX :
    /// le tri des observations revues et le fait que l'ancrage manquant sera acquis par l'envoi.
    private record Apercu(TriPublication tri, boolean ancrageAVenir) {}

    /// Câble l'item de menu (désactivé pendant une publication **ou** quand la publication est hors
    /// d'atteinte, #1596) et le libellé de restitution.
    static void cabler(
            MenuItem item,
            Label message,
            PublicationCorrectionsViewModel publication,
            ObservableBooleanValue publicationImpossible) {
        // Grisé pendant l'envoi, et — proactivement — quand rien n'est publiable ni ne le deviendra :
        // aucune observation ancrée et nuit non rattachée (#1596, précisé par #1838). Le remède annoncé a
        // changé avec la cause : ce n'est plus « réactivez le passage » (la publication sait désormais
        // acquérir l'ancrage elle-même) mais le rattachement, seul verrou restant. Un MenuItem désactivé
        // n'accueille pas de tooltip : on inscrit la cause dans son libellé (patron #789, comme
        // « Exporter les observations »), qui n'apparaît que lorsqu'il est effectivement grisé.
        item.disableProperty().bind(publication.enCoursProperty().or(publicationImpossible));
        item.textProperty()
                .bind(Bindings.when(publicationImpossible)
                        .then("Publier les corrections vers Vigie-Chiro…"
                                + " (rattachez la nuit à sa participation Vigie-Chiro)")
                        .otherwise("Publier les corrections vers Vigie-Chiro…"));
        message.textProperty().bind(publication.messageProperty());
        message.visibleProperty().bind(publication.messageProperty().isNotEmpty());
        message.managedProperty().bind(publication.messageProperty().isNotEmpty());
    }

    /// Lance la publication des corrections du passage de `source` : aperçu hors fil JavaFX,
    /// confirmation récapitulative, envoi suivi et annulable, bilan restitué. Sans passage ciblé, ne
    /// fait rien.
    ///
    /// Le `confirmateur` est celui **de l'écran** (#1405). Il y avait ici une surcharge à trois arguments
    /// qui fabriquait un [ConfirmationNavigation] pour la production, une à quatre pour les tests : un
    /// troisième idiome, pour le même besoin que le porteur détenu par les autres écrans. Un écran, une
    /// paire de porteurs, partagée par ses gestes.
    static void lancer(
            PublicationCorrectionsViewModel publication,
            SourceObservations source,
            IndicateurOccupation occupation,
            SuiviOperation dialogue,
            Supplier<Window> proprietaire,
            Confirmateur confirmateur) {
        ContextePassage contexte = source.contexteDuPassage();
        if (contexte == null) {
            return;
        }
        Long idPassage = contexte.idPassage();
        publication.marquerEnCours();
        // Les deux questions sont locales et se posent ensemble : un seul aller-retour hors fil, pour que
        // la confirmation dise d'un coup ce qui part et ce qui sera d'abord ancré.
        occupation.occuper(
                "Analyse des corrections à publier…",
                () -> new Apercu(publication.trier(idPassage), publication.ancrageAcquerable(idPassage)),
                apercu -> confirmerPuisPublier(publication, idPassage, apercu, dialogue, proprietaire, confirmateur),
                erreur -> publication.echec(erreur.getMessage()));
    }

    /// Au fil JavaFX, l'aperçu en main : rien de publiable **ni d'ancrable** → restitution directe des
    /// écarts ; sinon confirmation récapitulative, puis envoi suivi (ou effacement de l'état si refus).
    private static void confirmerPuisPublier(
            PublicationCorrectionsViewModel publication,
            Long idPassage,
            Apercu apercu,
            SuiviOperation dialogue,
            Supplier<Window> proprietaire,
            Confirmateur confirmateur) {
        TriPublication tri = apercu.tri();
        // Rien de prêt *et* rien à ancrer : c'est un vrai cul-de-sac. Avec un ancrage à venir, en
        // revanche, l'aperçu ne peut pas conclure — les observations non ancrées deviendront publiables.
        if (tri.publiables().isEmpty() && !apercu.ancrageAVenir()) {
            publication.echec("Rien à publier : " + PublicationCorrectionsViewModel.ecarts(tri, true)
                    + ". Déclarez la certitude des observations corrigées, ou rattachez cette nuit à sa"
                    + " participation Vigie-Chiro pour pouvoir les ancrer.");
            return;
        }
        if (!confirmateur.confirmer(
                PublicationCorrectionsViewModel.recapitulatif(apercu.tri(), apercu.ancrageAVenir()))) {
            publication.echec(""); // annulé : on efface l'état « en cours »
            return;
        }
        dialogue.lancer(
                proprietaire.get(),
                "Publication des corrections vers Vigie-Chiro",
                (progres, jeton) -> publication.publier(idPassage, progres, jeton),
                publication::appliquerBilan,
                () -> publication.echec(""), // renoncé en cours de route : on efface l'état « en cours »
                erreur -> publication.echec(erreur.getMessage()));
    }
}
