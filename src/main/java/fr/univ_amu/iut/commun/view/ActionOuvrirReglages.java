package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.stage.Window;

/// Entrée ☰ **« Réglages… »** (#927), migrée en [ActionMenu] socle (#930) : ouvre l'écran de réglages
/// via [NavigationReglages]. Toujours active (le socle contribue au moins l'onglet « Général »).
public final class ActionOuvrirReglages implements ActionMenu {

    private final NavigationReglages navigation;

    @Inject
    ActionOuvrirReglages(NavigationReglages navigation) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @Override
    public GroupeMenu groupe() {
        return GroupeMenu.PARAMETRES;
    }

    @Override
    public int ordre() {
        return 10;
    }

    @Override
    public String libelle() {
        return "Réglages…";
    }

    @Override
    public String iconeLiteral() {
        return "fas-cog";
    }

    @Override
    public void executer(Window proprietaire) {
        navigation.ouvrir();
    }
}
