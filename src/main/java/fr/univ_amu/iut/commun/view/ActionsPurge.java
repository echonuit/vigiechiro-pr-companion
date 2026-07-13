package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux.ResultatPurge;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/// Action IHM de **purge globale des originaux** (les `bruts/` de toutes les nuits importées),
/// déclenchée depuis le menu « ☰ » du chrome. Extrait de [MainController] (pur câblage) : annonce
/// l'espace récupérable, **confirme** la suppression (destructive) puis présente le résultat.
/// L'inspection de l'espace récupérable comme la suppression elle-même (parcours disque) tournent
/// **hors du fil JavaFX** sous le voile du chrome ([OccupationChrome], #1215), qui pose aussi
/// l'opération critique (#906) pour la phase destructive.
final class ActionsPurge {

    private final ServicePurgeOriginaux service;
    private final OccupationChrome occupation;
    private final Supplier<Window> fenetre;
    private final Runnable apresPurge;

    /// Confirmation d'action destructive : porteur partagé injectable (#1013), stub déterministe en test.
    private final ConfirmateurModifiable confirmateur =
            new ConfirmateurModifiable(new ConfirmationNavigation("Purger les originaux ?"));

    /// @param service service de purge des originaux
    /// @param occupation voile d'occupation du chrome (#1215)
    /// @param fenetre fournisseur de la fenêtre propriétaire des dialogues (évalué au clic)
    /// @param apresPurge action à jouer après une purge réussie (ex. retour à l'accueil pour rafraîchir
    ///     les volumes affichés)
    ActionsPurge(
            ServicePurgeOriginaux service, OccupationChrome occupation, Supplier<Window> fenetre, Runnable apresPurge) {
        this.service = Objects.requireNonNull(service, "service");
        this.occupation = Objects.requireNonNull(occupation, "occupation");
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
        this.apresPurge = Objects.requireNonNull(apresPurge, "apresPurge");
    }

    /// Inspecte l'espace **récupérable** (parcours disque, hors du fil JavaFX, #1215), l'annonce et
    /// confirme la purge (destructive), puis supprime les `bruts/` de toutes les nuits - également sous
    /// le voile. Sans originaux conservés, informe et n'agit pas.
    void purger() {
        occupation.occuper(
                "Inspection de l'espace récupérable…",
                "l'inspection de l'espace disque",
                service::volumeRecuperable,
                this::proposerPurge,
                echec -> alerte(AlertType.ERROR, "Purge impossible", message(echec)));
    }

    /// Sur le fil JavaFX, une fois l'espace récupérable connu : annonce + confirmation, puis purge
    /// hors du fil JavaFX.
    private void proposerPurge(long recuperable) {
        if (recuperable == 0L) {
            alerte(
                    AlertType.INFORMATION,
                    "Rien à purger",
                    "Aucun enregistrement original n'est conservé sur le disque.");
            return;
        }
        if (!confirmateur.confirmer("Supprimer les enregistrements originaux (bruts) de toutes les nuits importées"
                + " pour libérer environ " + Formats.octetsLisibles(recuperable) + " ? Les séquences d'écoute, les"
                + " validations et les dépôts sont conservés ; cette suppression est définitive.")) {
            return;
        }
        occupation.occuper(
                "Purge des originaux…",
                "la purge des originaux",
                service::purgerTout,
                this::restituerPurge,
                echec -> alerte(AlertType.ERROR, "Purge impossible", message(echec)));
    }

    /// Sur le fil JavaFX, après la purge : compte-rendu puis rafraîchissement de l'appelant.
    private void restituerPurge(ResultatPurge resultat) {
        alerte(
                AlertType.INFORMATION,
                "Originaux purgés",
                resultat.nombreSessions()
                        + " nuit(s) purgée(s), "
                        + Formats.octetsLisibles(resultat.octetsLiberes())
                        + " libéré(s).");
        apresPurge.run();
    }

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    private void alerte(AlertType type, String titre, String message) {
        Alert alerte = new Alert(type, message, ButtonType.OK);
        alerte.setTitle(titre);
        alerte.setHeaderText(null);
        alerte.initOwner(fenetre.get());
        alerte.showAndWait();
    }

    private static String message(Throwable echec) {
        return echec.getMessage() != null ? echec.getMessage() : echec.toString();
    }
}
