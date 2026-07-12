package fr.univ_amu.iut.audit.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Navigateur;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de la feature `audit` : charge `Audit.fxml` et le publie dans la zone centrale du
/// chrome via le [Navigateur] du socle (même patron que `NavigationAnalyse`). Seul point de la feature qui
/// charge un FXML ; `controllerFactory` branchée sur Guice pour que [AuditController] reçoive son
/// ViewModel. Écran sans paramètre (chargement initial dans `initialize()`).
@Singleton
public class NavigationAudit {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationAudit(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche l'écran **« Audit de cohérence »** dans la zone centrale du chrome (entrée depuis la carte
    /// d'accueil : `ouvrirRacine` réinitialise l'historique).
    public void ouvrir() {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationAudit.class, "Audit.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            AuditController controleur = loader.getController();
            navigateur.ouvrirRacine(vue, "audit", "Audit de cohérence", controleur);
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}
