package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.TriPublication;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;

/// Câblage et déclenchement de la **publication des corrections vers VigieChiro** (#723) sur la vue
/// audio, jumeau de [ImportVigieChiroUI] : isolé du [SonsValidationController] (contrainte NCSS), le
/// contrôleur ne garde que ses champs `@FXML` et une délégation.
///
/// La publication écrit vers l'extérieur et ne s'annule pas côté plateforme : elle passe par une
/// **confirmation récapitulative** construite sur le tri du service (rien n'est envoyé avant l'accord).
/// Le confirmateur est celui **de l'écran** (contrat neutre [Confirmateur] du socle, #1013) : un
/// `Alert.showAndWait` en dur figerait les tests headless.
final class PublicationCorrectionsUI {

    private PublicationCorrectionsUI() {}

    /// Câble l'item de menu (désactivé pendant une publication) et le libellé de restitution.
    static void cabler(MenuItem item, Label message, PublicationCorrectionsViewModel publication) {
        item.disableProperty().bind(publication.enCoursProperty());
        message.textProperty().bind(publication.messageProperty());
        message.visibleProperty().bind(publication.messageProperty().isNotEmpty());
        message.managedProperty().bind(publication.messageProperty().isNotEmpty());
    }

    /// Lance la publication des corrections du passage de `source` : tri hors fil JavaFX (aperçu),
    /// confirmation récapitulative, envoi hors fil, bilan restitué. Sans passage ciblé, ne fait rien.
    ///
    /// Le `confirmateur` est celui **de l'écran** (#1405). Il y avait ici une surcharge à trois arguments
    /// qui fabriquait un [ConfirmationNavigation] pour la production, une à quatre pour les tests : un
    /// troisième idiome, pour le même besoin que le porteur détenu par les autres écrans. Un écran, une
    /// paire de porteurs, partagée par ses gestes.
    static void lancer(
            PublicationCorrectionsViewModel publication,
            SourceObservations source,
            ExecuteurTache executeur,
            Confirmateur confirmateur) {
        ContextePassage contexte = source.contexteDuPassage();
        if (contexte == null) {
            return;
        }
        Long idPassage = contexte.idPassage();
        publication.marquerEnCours();
        executeur.executer(
                () -> publication.trier(idPassage),
                tri -> confirmerPuisPublier(publication, idPassage, tri, executeur, confirmateur),
                erreur -> publication.echec(erreur.getMessage()));
    }

    /// Au fil JavaFX, l'aperçu en main : rien de publiable → restitution directe des écarts ;
    /// sinon confirmation récapitulative, puis envoi hors fil (ou effacement de l'état si refus).
    private static void confirmerPuisPublier(
            PublicationCorrectionsViewModel publication,
            Long idPassage,
            TriPublication tri,
            ExecuteurTache executeur,
            Confirmateur confirmateur) {
        if (tri.publiables().isEmpty()) {
            publication.echec("Rien à publier : " + ecarts(tri)
                    + ". Déclarez la certitude des observations corrigées, ou réimportez depuis"
                    + " VigieChiro pour les ancrer.");
            return;
        }
        if (!confirmateur.confirmer(recapitulatif(tri))) {
            publication.echec(""); // annulé : on efface l'état « en cours »
            return;
        }
        executeur.executer(
                () -> publication.publier(idPassage),
                publication::appliquerBilan,
                erreur -> publication.echec(erreur.getMessage()));
    }

    /// Message de la confirmation : ce qui va partir, ce qui restera à quai, et le caractère définitif
    /// (une correction publiée se remplace côté plateforme, elle ne se retire pas).
    private static String recapitulatif(TriPublication tri) {
        StringBuilder message = new StringBuilder("Publier ")
                .append(tri.publiables().size())
                .append(" correction(s) de ce passage vers VigieChiro ?");
        if (tri.ecartees() > 0) {
            message.append("\n\nResteront à quai : ").append(ecarts(tri)).append('.');
        }
        message.append("\n\nLes valeurs déjà publiées seront réécrites ; une correction publiée ne peut"
                + " pas être retirée de la plateforme.");
        return message.toString();
    }

    /// Détail des écartées, par cause (seules les causes présentes sont citées).
    private static String ecarts(TriPublication tri) {
        StringBuilder ecarts = new StringBuilder();
        if (tri.sansCertitude() > 0) {
            ecarts.append(tri.sansCertitude()).append(" sans certitude déclarée");
        }
        if (tri.sansAncrage() > 0) {
            if (!ecarts.isEmpty()) {
                ecarts.append(", ");
            }
            ecarts.append(tri.sansAncrage()).append(" sans ancrage plateforme");
        }
        if (tri.horsReferentiel() > 0) {
            if (!ecarts.isEmpty()) {
                ecarts.append(", ");
            }
            ecarts.append(tri.horsReferentiel()).append(" hors référentiel");
        }
        return ecarts.toString();
    }
}
