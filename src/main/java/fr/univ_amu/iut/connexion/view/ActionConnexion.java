package fr.univ_amu.iut.connexion.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ActionMenu;
import fr.univ_amu.iut.commun.view.GroupeMenu;
import java.util.Objects;
import javafx.stage.Window;

/// Entrée ☰ **« Se connecter à VigieChiro… »** (#741), contribuée par la feature `connexion` (#931).
///
/// Preuve que le mécanisme d'extension du menu (#930) fonctionne de bout en bout : une feature ajoute
/// une entrée via le `Multibinder<ActionMenu>` de son module, sans que le socle la connaisse (le
/// contrat `OuvrirConnexion` et son défaut inerte, qui n'existaient que pour ça, ont disparu). Le
/// libellé reflète l'état de connexion et est réévalué à chaque ouverture du menu.
public final class ActionConnexion implements ActionMenu {

    private final NavigationConnexion navigation;

    @Inject
    ActionConnexion(NavigationConnexion navigation) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @Override
    public GroupeMenu groupe() {
        return GroupeMenu.COMPTE;
    }

    @Override
    public int ordre() {
        return 10;
    }

    @Override
    public String libelle() {
        return navigation.libelleMenu();
    }

    @Override
    public String iconeLiteral() {
        return navigation.iconeMenu();
    }

    @Override
    public void executer(Window proprietaire) {
        navigation.ouvrir();
    }
}
