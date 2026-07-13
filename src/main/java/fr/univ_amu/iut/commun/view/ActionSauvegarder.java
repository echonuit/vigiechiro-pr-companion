package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import java.util.Objects;
import javafx.stage.Window;

/// Entrée ☰ **« Sauvegarder la base »** (#148), migrée en [ActionMenu] contribué (#930). Réutilise la
/// logique de dialogue de [ActionsSauvegarde] (sélecteur de dossier + compte-rendu), instanciée au
/// clic avec la fenêtre propriétaire.
public final class ActionSauvegarder implements ActionMenu {

    private final ServiceSauvegarde service;
    private final Navigateur navigateur;
    private final OccupationChrome occupation;

    @Inject
    ActionSauvegarder(ServiceSauvegarde service, Navigateur navigateur, OccupationChrome occupation) {
        this.service = Objects.requireNonNull(service, "service");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
        this.occupation = Objects.requireNonNull(occupation, "occupation");
    }

    @Override
    public GroupeMenu groupe() {
        return GroupeMenu.BASE;
    }

    @Override
    public int ordre() {
        return 10;
    }

    @Override
    public String libelle() {
        return "💾 Sauvegarder la base…";
    }

    @Override
    public void executer(Window proprietaire) {
        new ActionsSauvegarde(service, occupation, () -> proprietaire, navigateur::afficherAccueil).sauvegarder();
    }
}
