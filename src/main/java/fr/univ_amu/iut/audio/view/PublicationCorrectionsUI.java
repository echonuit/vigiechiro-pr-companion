package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import fr.univ_amu.iut.commun.view.VueCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.TriPublication;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
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

    /// Nombre de détails montrés par constat avant de résumer. La **vue** en décide, pas le compte rendu
    /// (ADR 0031) : la liste des refus peut être longue, la place sous le menu ne l'est pas.
    private static final int DETAILS_MONTRES = 5;

    /// Câble l'item de menu (désactivé pendant une publication **ou** quand la publication est hors
    /// d'atteinte, #1596) et la zone de restitution.
    ///
    /// Celle-ci reçoit **deux nœuds parce qu'il y a deux natures** (ADR 0028 / 0031) : le **compte rendu**,
    /// une structure de constats que l'on parcourt, et au-dessus le **retour** borné - un refus, une
    /// annulation, une phrase. Les faire tenir dans un même libellé obligeait à tronquer le premier : la
    /// version d'avant n'affichait qu'une cause de refus sur N.
    static void cabler(
            MenuItem item,
            VBox zone,
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
        VBox compteRendu = new VBox();
        zone.getChildren().setAll(libelleDuRetour(publication), compteRendu);
        publication.compteRenduProperty().addListener((observable, avant, rendu) -> afficher(compteRendu, rendu));
        afficher(compteRendu, publication.compteRenduProperty().get());
        // La zone s'efface entièrement quand ses deux canaux se taisent, pour ne pas laisser un blanc.
        BooleanBinding quelqueChoseADire = Bindings.createBooleanBinding(
                () -> publication.retourProperty().get().present()
                        || !publication.compteRenduProperty().get().estVide(),
                publication.retourProperty(),
                publication.compteRenduProperty());
        zone.visibleProperty().bind(quelqueChoseADire);
        zone.managedProperty().bind(quelqueChoseADire);
    }

    /// Le libellé du **retour d'opération** (refus, panne). Ce n'est pas le bandeau de l'écran : celui-ci
    /// porte les retours de la vue audio, et un envoi refusé se lit à l'endroit d'où il a été lancé.
    ///
    /// Fabriqué ici plutôt que déclaré dans le FXML : c'est un détail de rendu de cette restitution, et
    /// un champ `@FXML` de moins dans un contrôleur déjà au plafond NCSS.
    private static Label libelleDuRetour(PublicationCorrectionsViewModel publication) {
        ReadOnlyObjectProperty<RetourOperation> retour = publication.retourProperty();
        Label message = new Label();
        message.getStyleClass().add("audio-message");
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);
        message.textProperty()
                .bind(Bindings.createStringBinding(() -> retour.get().texte(), retour));
        BooleanBinding present =
                Bindings.createBooleanBinding(() -> retour.get().present(), retour);
        message.visibleProperty().bind(present);
        message.managedProperty().bind(present);
        return message;
    }

    /// Remplace le compte rendu affiché. On reconstruit plutôt qu'on ne met à jour : un compte rendu est
    /// immuable et publié d'un bloc, il n'y a rien à rafraîchir en place.
    private static void afficher(VBox zone, CompteRendu rendu) {
        zone.getChildren().setAll(VueCompteRendu.rendre(rendu, DETAILS_MONTRES).getChildren());
        zone.getStyleClass().setAll(VueCompteRendu.CLASSE_RACINE);
        zone.setVisible(!rendu.estVide());
        zone.setManaged(!rendu.estVide());
    }

    /// Le compte rendu affiché, pour les tests de câblage : le second enfant de la zone, le premier
    /// étant le libellé du retour.
    static VBox zoneDuCompteRendu(VBox zone) {
        return (VBox) zone.getChildren().get(1);
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
