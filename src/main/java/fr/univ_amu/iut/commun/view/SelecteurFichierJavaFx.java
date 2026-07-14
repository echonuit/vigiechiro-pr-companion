package fr.univ_amu.iut.commun.view;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/// Implémentation **réelle** de [SelecteurFichier] : les sélecteurs natifs de JavaFX. Jumelle de
/// [NotificationDialogue] et de [ConfirmationNavigation], et seul endroit du socle qui construise un
/// `DirectoryChooser` ou un `FileChooser` de sauvegarde.
public final class SelecteurFichierJavaFx implements SelecteurFichier {

    /// Fenêtre propriétaire des sélecteurs, évaluée **au moment de choisir** (l'action est construite
    /// avant que l'écran ne soit attaché à une fenêtre). Peut rendre `null` : JavaFX l'accepte.
    private final Supplier<Window> fenetre;

    public SelecteurFichierJavaFx(Supplier<Window> fenetre) {
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
    }

    @Override
    public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
        DirectoryChooser selecteur = new DirectoryChooser();
        selecteur.setTitle(titre);
        dossierExistant(dossierInitial).ifPresent(selecteur::setInitialDirectory);
        return chemin(selecteur.showDialog(fenetre.get()));
    }

    @Override
    public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
        FileChooser selecteur = new FileChooser();
        selecteur.setTitle(titre);
        selecteur.getExtensionFilters().add(new FileChooser.ExtensionFilter(filtre.libelle(), filtre.motif()));
        dossierExistant(dossierInitial).ifPresent(selecteur::setInitialDirectory);
        return chemin(selecteur.showOpenDialog(fenetre.get()));
    }

    /// Le dossier initial **s'il existe** : les sélecteurs de JavaFX refusent un dossier inexistant, ce
    /// qui est le cas du dossier de sauvegardes par défaut tant qu'aucune sauvegarde n'a été faite.
    private static Optional<File> dossierExistant(Optional<Path> dossier) {
        return dossier.filter(Files::isDirectory).map(Path::toFile);
    }

    /// Un sélecteur annulé rend `null` : c'est l'utilisateur qui renonce, pas une erreur.
    private static Optional<Path> chemin(File choisi) {
        return Optional.ofNullable(choisi).map(File::toPath);
    }
}
