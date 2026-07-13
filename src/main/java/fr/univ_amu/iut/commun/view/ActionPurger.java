package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import java.util.Objects;
import javafx.stage.Window;

/// Entrée ☰ **« Purger les originaux importés »**, migrée en [ActionMenu] contribué (#930). Réutilise
/// [ActionsPurge] : annonce de l'espace récupérable, **confirmation** de la suppression destructive,
/// puis retour à l'accueil pour rafraîchir les volumes affichés.
public final class ActionPurger implements ActionMenu {

    private final ServicePurgeOriginaux service;
    private final Navigateur navigateur;
    private final OccupationChrome occupation;

    @Inject
    ActionPurger(ServicePurgeOriginaux service, Navigateur navigateur, OccupationChrome occupation) {
        this.service = Objects.requireNonNull(service, "service");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
        this.occupation = Objects.requireNonNull(occupation, "occupation");
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
        return "🧹 Purger les originaux importés…";
    }

    @Override
    public void executer(Window proprietaire) {
        new ActionsPurge(service, occupation, () -> proprietaire, navigateur::afficherAccueil).purger();
    }
}
