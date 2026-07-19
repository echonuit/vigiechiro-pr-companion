package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import javafx.stage.Window;

/// Entrée ☰ **« Sauvegarde complète (base + audio) »** (#1346), à côté de la sauvegarde de la base seule.
///
/// Le moteur existait depuis #1142, mais **aucun appelant** : ni menu, ni CLI. Or c'est la **seule
/// sauvegarde qui protège vraiment**. La base seule ne garde que les métadonnées et les observations ; si
/// l'audio disparaît du disque, la plateforme ne le rendra **pas** (un dépôt en archives n'en laisse aucun,
/// #1297) — le passage devient *archivé*, consultable mais muet.
///
/// C'est aussi le prérequis déclaré du reset guidé (#1151) : on ne repart d'une base neuve qu'après avoir
/// mis l'audio à l'abri.
public final class ActionSauvegarderComplet implements ActionMenu {

    /// Plomberie de l'entrée : l'unique action, et la fenêtre du clic (#1405).
    private final PorteurSauvegarde porteur;

    @Inject
    ActionSauvegarderComplet(ServiceSauvegarde service, Navigateur navigateur, OccupationChrome occupation) {
        this.porteur = new PorteurSauvegarde(service, navigateur, occupation);
    }

    @Override
    public GroupeMenu groupe() {
        return GroupeMenu.BASE;
    }

    /// Juste après « Sauvegarder la base… » (ordre 10) : les deux se lisent ensemble, la complète en second
    /// car elle est plus lourde et plus rare.
    @Override
    public int ordre() {
        return 15;
    }

    @Override
    public String libelle() {
        return "Sauvegarde complète (base + audio)…";
    }

    @Override
    public String iconeLiteral() {
        return "fas-archive";
    }

    @Override
    public void executer(Window proprietaire) {
        porteur.sous(proprietaire).sauvegarderComplet();
    }

    /// Porteur exposé aux tests (#1405) : `porteur().actions()` porte les trois dialogues.
    PorteurSauvegarde porteur() {
        return porteur;
    }
}
