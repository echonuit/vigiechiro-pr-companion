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
                        .then("☁ Publier les corrections vers VigieChiro…"
                                + " (rattachez la nuit à sa participation VigieChiro)")
                        .otherwise("☁ Publier les corrections vers VigieChiro…"));
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
            publication.echec("Rien à publier : " + ecarts(tri, true)
                    + ". Déclarez la certitude des observations corrigées, ou rattachez cette nuit à sa"
                    + " participation VigieChiro pour pouvoir les ancrer.");
            return;
        }
        if (!confirmateur.confirmer(recapitulatif(apercu))) {
            publication.echec(""); // annulé : on efface l'état « en cours »
            return;
        }
        dialogue.lancer(
                proprietaire.get(),
                "Publication des corrections vers VigieChiro",
                (progres, jeton) -> publication.publier(idPassage, progres, jeton),
                publication::appliquerBilan,
                () -> publication.echec(""), // renoncé en cours de route : on efface l'état « en cours »
                erreur -> publication.echec(erreur.getMessage()));
    }

    /// Message de la confirmation : ce qui va partir, ce qui sera d'abord ancré, ce qui restera à quai,
    /// et le caractère définitif (une correction publiée se remplace côté plateforme, elle ne se retire
    /// pas).
    private static String recapitulatif(Apercu apercu) {
        TriPublication tri = apercu.tri();
        boolean ancrage = apercu.ancrageAVenir() && tri.sansAncrage() > 0;
        StringBuilder message = new StringBuilder();
        if (ancrage) {
            // On se garde d'annoncer un total : une observation à ancrer peut aussi manquer de certitude,
            // et ne partirait donc pas non plus. Promettre un compte que l'envoi démentirait serait pire
            // que de ne rien promettre.
            message.append("Publier les corrections de ce passage vers VigieChiro ?")
                    .append("\n\n")
                    .append(tri.publiables().size())
                    .append(" prête(s) à partir, et ")
                    .append(tri.sansAncrage())
                    .append(" à ancrer d'abord : leur ancrage sera rapatrié depuis VigieChiro (vos"
                            + " validations sont préservées), ce qui peut prendre quelques minutes. Seules"
                            + " celles dont la certitude est déclarée partiront ensuite.");
        } else {
            message.append("Publier ")
                    .append(tri.publiables().size())
                    .append(" correction(s) de ce passage vers VigieChiro ?");
        }
        String quai = ecarts(tri, !ancrage);
        if (!quai.isEmpty()) {
            message.append("\n\nResteront à quai : ").append(quai).append('.');
        }
        message.append("\n\nLes valeurs déjà publiées seront réécrites ; une correction publiée ne peut"
                + " pas être retirée de la plateforme.");
        return message.toString();
    }

    /// Détail des écartées, par cause (seules les causes présentes sont citées). `inclureSansAncrage`
    /// est faux quand l'envoi va justement acquérir cet ancrage : ces observations ne restent pas à quai.
    private static String ecarts(TriPublication tri, boolean inclureSansAncrage) {
        StringBuilder ecarts = new StringBuilder();
        if (tri.sansCertitude() > 0) {
            ecarts.append(tri.sansCertitude()).append(" sans certitude déclarée");
        }
        if (inclureSansAncrage && tri.sansAncrage() > 0) {
            separer(ecarts);
            ecarts.append(tri.sansAncrage()).append(" sans ancrage plateforme");
        }
        if (tri.horsReferentiel() > 0) {
            separer(ecarts);
            ecarts.append(tri.horsReferentiel()).append(" hors référentiel");
        }
        return ecarts.toString();
    }

    private static void separer(StringBuilder ecarts) {
        if (!ecarts.isEmpty()) {
            ecarts.append(", ");
        }
    }
}
