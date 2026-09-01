package fr.univ_amu.iut.commun.view;

import com.google.inject.Singleton;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.HostServices;

/// Implémentation par défaut de [OuvreurDeLien], au-dessus de deux mécanismes distincts.
///
/// Une **URL** part au [HostServices] de JavaFX (`showDocument`), qui l'ouvre dans le navigateur.
/// Un **dossier** part à [Desktop#open], qui le montre dans le gestionnaire de fichiers.
///
/// Les deux ne se remplacent pas l'un l'autre : passer un `file://` à `showDocument` rend un listing
/// de répertoire dans le navigateur, ce qui ressemble à un dossier ouvert sans en être un (#4982).
///
/// Le `HostServices` n'existe qu'à partir d'une [javafx.application.Application] : `App` le branche
/// une fois au démarrage via [#initialiser]. Tant qu'il n'est pas branché (mode CLI, tests, ou avant
/// le `start`), [#ouvrir] ne fait que journaliser : **jamais d'exception**, pour ne pas casser un
/// parcours à cause d'un lien non ouvrable.
@Singleton
public class OuvreurDeLienSysteme implements OuvreurDeLien {

    private static final Logger LOG = Logger.getLogger(OuvreurDeLienSysteme.class.getName());

    private HostServices hostServices;

    /// Branche le `HostServices` de l'application (appelé une fois par `App.start`).
    public void initialiser(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    @Override
    public void ouvrir(String url) {
        if (hostServices == null) {
            LOG.warning(() -> "Aucun navigateur disponible (HostServices non branché) : " + url);
            return;
        }
        hostServices.showDocument(url);
    }

    @Override
    public boolean ouvrirDossier(Path dossier) {
        if (!Files.isDirectory(dossier)) {
            LOG.warning(() -> "Dossier introuvable, rien à ouvrir : " + dossier);
            return false;
        }
        // `Desktop` n'existe pas en headless, et `Action.OPEN` peut manquer même quand il existe. Les
        // deux se demandent : conclure sur le premier laisserait passer le second.
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            LOG.info(() -> "Le système n'expose pas d'ouverture de dossier : " + dossier);
            return false;
        }
        try {
            Desktop.getDesktop().open(dossier.toFile());
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException echec) {
            LOG.log(Level.INFO, echec, () -> "Ouverture du dossier refusée par le système : " + dossier);
            return false;
        }
    }
}
