package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/// Actions IHM de **sauvegarde / restauration** de la base (#148), déclenchées depuis le menu « ☰ » du
/// chrome. Extrait de [MainController] (pur câblage) : ouvre les sélecteurs de dossier/fichier, confirme la
/// restauration (destructive) et présente le résultat, en déléguant tout le travail à [ServiceSauvegarde].
final class ActionsSauvegarde {

    private final ServiceSauvegarde service;
    private final Supplier<Window> fenetre;
    private final Runnable apresRestauration;

    /// @param service service de sauvegarde/restauration
    /// @param fenetre fournisseur de la fenêtre propriétaire des sélecteurs (évalué au clic)
    /// @param apresRestauration action à jouer après une restauration réussie (ex. retour à l'accueil pour
    ///     relire la base restaurée)
    ActionsSauvegarde(ServiceSauvegarde service, Supplier<Window> fenetre, Runnable apresRestauration) {
        this.service = Objects.requireNonNull(service, "service");
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
        this.apresRestauration = Objects.requireNonNull(apresRestauration, "apresRestauration");
    }

    /// Demande un dossier (par défaut `<workspace>/sauvegardes`, emplacement **configurable**), y écrit une
    /// sauvegarde horodatée et confirme le chemin obtenu.
    void sauvegarder() {
        DirectoryChooser selecteur = new DirectoryChooser();
        selecteur.setTitle("Dossier où enregistrer la sauvegarde");
        dossierExistant(service.dossierParDefaut()).ifPresent(selecteur::setInitialDirectory);
        File dossier = selecteur.showDialog(fenetre.get());
        if (dossier == null) {
            return;
        }
        try {
            Path fichier = service.sauvegarder(dossier.toPath());
            alerte(AlertType.INFORMATION, "Sauvegarde créée", "La base a été sauvegardée dans :\n" + fichier);
        } catch (RuntimeException echec) {
            alerte(AlertType.ERROR, "Sauvegarde impossible", message(echec));
        }
    }

    /// Demande un fichier de sauvegarde, **confirme** le remplacement (destructif) puis restaure. En cas de
    /// succès, joue [#apresRestauration] pour relire la base restaurée.
    void restaurer() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Choisir une sauvegarde à restaurer");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sauvegarde SQLite (*.db)", "*.db"));
        dossierExistant(service.dossierParDefaut()).ifPresent(selecteur::setInitialDirectory);
        File fichier = selecteur.showOpenDialog(fenetre.get());
        if (fichier == null) {
            return;
        }
        if (!confirmer("La base actuelle sera remplacée par « " + fichier.getName() + " ». Son état courant est"
                + " d'abord mis de côté (vigiechiro.db.avant-restauration). Continuer ?")) {
            return;
        }
        try {
            service.restaurer(fichier.toPath());
            alerte(
                    AlertType.INFORMATION,
                    "Base restaurée",
                    "La base a été restaurée depuis « " + fichier.getName() + " ».");
            apresRestauration.run();
        } catch (RuntimeException echec) {
            alerte(AlertType.ERROR, "Restauration impossible", message(echec));
        }
    }

    /// Le dossier s'il existe (un `DirectoryChooser`/`FileChooser` refuse un dossier initial inexistant, ce
    /// qui est le cas du dossier de sauvegardes par défaut tant qu'aucune sauvegarde n'a été faite).
    private static Optional<File> dossierExistant(Path dossier) {
        return Files.isDirectory(dossier) ? Optional.of(dossier.toFile()) : Optional.empty();
    }

    private boolean confirmer(String message) {
        Alert alerte = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alerte.setTitle("Restaurer la base ?");
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
