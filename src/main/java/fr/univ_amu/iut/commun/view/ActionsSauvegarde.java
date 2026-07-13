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
/// restauration (destructive) et présente le résultat. La copie de la base (potentiellement volumineuse)
/// tourne **hors du fil JavaFX** sous le voile du chrome ([OccupationChrome], #1215), qui pose aussi
/// l'opération critique (#906) : fermer l'application en pleine copie déclenche l'avertissement du socle.
final class ActionsSauvegarde {

    private final ServiceSauvegarde service;
    private final OccupationChrome occupation;
    private final Supplier<Window> fenetre;
    private final Runnable apresRestauration;

    /// Confirmation d'action destructive : porteur partagé injectable (#1013), stub déterministe en test.
    private final ConfirmateurModifiable confirmateur =
            new ConfirmateurModifiable(new ConfirmationNavigation("Restaurer la base ?"));

    /// @param service service de sauvegarde/restauration
    /// @param occupation voile d'occupation du chrome (#1215)
    /// @param fenetre fournisseur de la fenêtre propriétaire des sélecteurs (évalué au clic)
    /// @param apresRestauration action à jouer après une restauration réussie (ex. retour à l'accueil pour
    ///     relire la base restaurée)
    ActionsSauvegarde(
            ServiceSauvegarde service,
            OccupationChrome occupation,
            Supplier<Window> fenetre,
            Runnable apresRestauration) {
        this.service = Objects.requireNonNull(service, "service");
        this.occupation = Objects.requireNonNull(occupation, "occupation");
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
        this.apresRestauration = Objects.requireNonNull(apresRestauration, "apresRestauration");
    }

    /// Demande un dossier (par défaut `<workspace>/sauvegardes`, emplacement **configurable**), y écrit une
    /// sauvegarde horodatée **hors du fil JavaFX** (#1215) et confirme le chemin obtenu.
    void sauvegarder() {
        DirectoryChooser selecteur = new DirectoryChooser();
        selecteur.setTitle("Dossier où enregistrer la sauvegarde");
        dossierExistant(service.dossierParDefaut()).ifPresent(selecteur::setInitialDirectory);
        File dossier = selecteur.showDialog(fenetre.get());
        if (dossier == null) {
            return;
        }
        occupation.occuper(
                "Sauvegarde de la base…",
                "la sauvegarde de la base",
                () -> service.sauvegarder(dossier.toPath()),
                fichier -> alerte(
                        AlertType.INFORMATION, "Sauvegarde créée", "La base a été sauvegardée dans :\n" + fichier),
                echec -> alerte(AlertType.ERROR, "Sauvegarde impossible", message(echec)));
    }

    /// Demande un fichier de sauvegarde, **confirme** le remplacement (destructif) puis restaure **hors du
    /// fil JavaFX** (#1215). En cas de succès, joue [#apresRestauration] pour relire la base restaurée.
    void restaurer() {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle("Choisir une sauvegarde à restaurer");
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sauvegarde SQLite (*.db)", "*.db"));
        dossierExistant(service.dossierParDefaut()).ifPresent(selecteur::setInitialDirectory);
        File fichier = selecteur.showOpenDialog(fenetre.get());
        if (fichier == null) {
            return;
        }
        if (!confirmateur.confirmer("La base actuelle sera remplacée par « " + fichier.getName() + " ». Son état"
                + " courant est d'abord mis de côté (vigiechiro.db.avant-restauration). Continuer ?")) {
            return;
        }
        occupation.occuper(
                "Restauration de la base…",
                "la restauration de la base",
                () -> {
                    service.restaurer(fichier.toPath());
                    return fichier.getName();
                },
                nom -> {
                    alerte(AlertType.INFORMATION, "Base restaurée", "La base a été restaurée depuis « " + nom + " ».");
                    apresRestauration.run();
                },
                echec -> alerte(AlertType.ERROR, "Restauration impossible", message(echec)));
    }

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    /// Le dossier s'il existe (un `DirectoryChooser`/`FileChooser` refuse un dossier initial inexistant, ce
    /// qui est le cas du dossier de sauvegardes par défaut tant qu'aucune sauvegarde n'a été faite).
    private static Optional<File> dossierExistant(Path dossier) {
        return Files.isDirectory(dossier) ? Optional.of(dossier.toFile()) : Optional.empty();
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
