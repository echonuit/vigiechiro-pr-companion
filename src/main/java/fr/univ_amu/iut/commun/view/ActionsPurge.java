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
/// l'espace récupérable, **confirme** la suppression (destructive) puis présente le résultat, en
/// déléguant tout le travail à [ServicePurgeOriginaux].
final class ActionsPurge {

    private final ServicePurgeOriginaux service;
    private final Supplier<Window> fenetre;
    private final Runnable apresPurge;

    /// @param service service de purge des originaux
    /// @param fenetre fournisseur de la fenêtre propriétaire des dialogues (évalué au clic)
    /// @param apresPurge action à jouer après une purge réussie (ex. retour à l'accueil pour rafraîchir
    ///     les volumes affichés)
    ActionsPurge(ServicePurgeOriginaux service, Supplier<Window> fenetre, Runnable apresPurge) {
        this.service = Objects.requireNonNull(service, "service");
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
        this.apresPurge = Objects.requireNonNull(apresPurge, "apresPurge");
    }

    /// Annonce l'espace **récupérable**, confirme la purge (destructive), puis supprime les `bruts/` de
    /// toutes les nuits. Sans originaux conservés, informe et n'agit pas.
    void purger() {
        long recuperable = service.volumeRecuperable();
        if (recuperable == 0L) {
            alerte(
                    AlertType.INFORMATION,
                    "Rien à purger",
                    "Aucun enregistrement original n'est conservé sur le disque.");
            return;
        }
        if (!confirmer("Supprimer les enregistrements originaux (bruts) de toutes les nuits importées pour"
                + " libérer environ " + Formats.octetsLisibles(recuperable) + " ? Les séquences d'écoute, les"
                + " validations et les dépôts sont conservés ; cette suppression est définitive.")) {
            return;
        }
        try {
            ResultatPurge resultat = service.purgerTout();
            alerte(
                    AlertType.INFORMATION,
                    "Originaux purgés",
                    resultat.nombreSessions()
                            + " nuit(s) purgée(s), "
                            + Formats.octetsLisibles(resultat.octetsLiberes())
                            + " libéré(s).");
            apresPurge.run();
        } catch (RuntimeException echec) {
            alerte(AlertType.ERROR, "Purge impossible", message(echec));
        }
    }

    private boolean confirmer(String message) {
        Alert alerte = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alerte.setTitle("Purger les originaux ?");
        alerte.setHeaderText(null);
        alerte.initOwner(fenetre.get());
        return alerte.showAndWait().filter(bouton -> bouton == ButtonType.OK).isPresent();
    }

    private void alerte(AlertType type, String titre, String message) {
        Alert alerte = new Alert(type, message, ButtonType.OK);
        alerte.setTitle(titre);
        alerte.setHeaderText(null);
        alerte.initOwner(fenetre.get());
        alerte.showAndWait();
    }

    private static String message(RuntimeException echec) {
        return echec.getMessage() != null ? echec.getMessage() : echec.toString();
    }
}
