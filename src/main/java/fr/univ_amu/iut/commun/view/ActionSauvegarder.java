package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import javafx.stage.Window;

/// Entrée ☰ **« Sauvegarder la base »** (#148), migrée en [ActionMenu] contribué (#930). Réutilise la
/// logique de dialogue de [ActionsSauvegarde] (sélecteur de dossier + compte-rendu), instanciée au
/// clic avec la fenêtre propriétaire.
public final class ActionSauvegarder implements ActionMenu {

    /// Plomberie de l'entrée : l'unique action, et la fenêtre du clic (#1405).
    private final PorteurSauvegarde porteur;

    @Inject
    ActionSauvegarder(ServiceSauvegarde service, Navigateur navigateur, OccupationChrome occupation) {
        this.porteur = new PorteurSauvegarde(service, navigateur, occupation);
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
        porteur.sous(proprietaire).sauvegarder();
    }

    /// Porteur exposé aux tests (#1405) : `porteur().actions()` porte les trois dialogues.
    PorteurSauvegarde porteur() {
        return porteur;
    }
}
