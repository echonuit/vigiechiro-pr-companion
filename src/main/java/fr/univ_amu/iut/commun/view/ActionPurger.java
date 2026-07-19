package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.persistence.DeclarationPurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import java.util.Objects;
import java.util.Optional;
import javafx.stage.Window;

/// Entrée ☰ **« Purger les originaux importés »**, migrée en [ActionMenu] contribué (#930). Réutilise
/// [ActionsPurge] : annonce de l'espace récupérable, **confirmation** de la suppression destructive,
/// puis retour à l'accueil pour rafraîchir les volumes affichés.
///
/// L'action vit aussi longtemps que l'entrée de menu (#1405), au lieu de naître dans [#executer] avec
/// ses vrais dialogues : c'est ce qui la rend **atteignable** par un test, comme les quatre entrées de
/// sauvegarde via [PorteurSauvegarde].
public final class ActionPurger implements ActionMenu {

    /// Fenêtre propriétaire des dialogues, posée au clic : l'action la lit **paresseusement** (elle
    /// n'existe pas encore quand l'entrée de menu est construite).
    private Window proprietaire;

    private final ActionsPurge actions;

    @Inject
    ActionPurger(
            ServicePurgeOriginaux service,
            Navigateur navigateur,
            OccupationChrome occupation,
            Optional<DeclarationPurgeOriginaux> declaration) {
        Objects.requireNonNull(navigateur, "navigateur");
        this.actions = new ActionsPurge(
                Objects.requireNonNull(service, "service"),
                Objects.requireNonNull(occupation, "occupation"),
                () -> proprietaire,
                navigateur::afficherAccueil,
                Objects.requireNonNull(declaration, "declaration"));
    }

    @Override
    public GroupeMenu groupe() {
        return GroupeMenu.MAINTENANCE;
    }

    @Override
    public int ordre() {
        return 10;
    }

    @Override
    public String libelle() {
        return "Purger les originaux importés…";
    }

    @Override
    public String iconeLiteral() {
        return "fas-broom";
    }

    @Override
    public void executer(Window proprietaire) {
        this.proprietaire = proprietaire;
        actions.purger();
    }

    /// Action exposée aux tests (#1405) : `actions().confirmateur()/notificateur()`.
    ActionsPurge actions() {
        return actions;
    }
}
