package fr.univ_amu.iut.commun.view;

import com.google.inject.Singleton;
import java.util.logging.Logger;
import javafx.application.HostServices;

/// Implémentation par défaut de [OuvreurDeLien] : délègue au [HostServices] de l'application
/// JavaFX (`showDocument`), ouvert dans le navigateur par défaut du système.
///
/// Le `HostServices` n'existe qu'à partir d'une [javafx.application.Application] : `App` le
/// branche une fois au démarrage via [#initialiser]. Tant qu'il n'est pas branché (mode
/// CLI, tests, ou avant le `start`), [#ouvrir] ne fait que journaliser — **jamais d'exception**,
/// pour ne pas casser un parcours à cause d'un lien non ouvrable.
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
}
