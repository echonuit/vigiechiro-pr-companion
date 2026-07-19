package fr.univ_amu.iut.audit.view;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.audit.model.ServiceRecuperabilite;
import fr.univ_amu.iut.audit.model.ServiceReset;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.view.ActionMenu;
import fr.univ_amu.iut.commun.view.GroupeMenu;
import fr.univ_amu.iut.commun.view.OccupationChrome;
import java.util.Objects;
import javafx.application.Platform;
import javafx.stage.Window;

/// Entrée ☰ **« Repartir d'une base neuve… »** (#1419) : la parité IHM de `reset-guide --executer`.
///
/// Placée **en dernier** du groupe « base », après la sauvegarde et la restauration : c'est l'action la
/// plus lourde et la plus rare, et les deux qui la précèdent en sont le prérequis. L'ordre du menu se lit
/// comme la procédure elle-même.
public final class ActionResetGuide implements ActionMenu {

    /// Fenêtre propriétaire des dialogues, posée **au clic** : elle n'existe pas encore quand l'entrée de
    /// menu est construite. Le geste la lit paresseusement (même mécanique que `PorteurSauvegarde`, #1405).
    private Window proprietaire;

    /// Le geste, **détenu** par l'entrée et non recréé à chaque clic. C'est ce qui le rend atteignable :
    /// un test remplace ses dialogues, déclenche l'entrée, et vérifie **quel** geste est parti. Sans cela,
    /// rien ne garantissait que « Repartir d'une base neuve » ne lance pas une simple sauvegarde — et
    /// c'est l'entrée la plus destructrice du menu (#1436).
    private final GesteReset geste;

    @Inject
    ActionResetGuide(
            Provider<ServiceRecuperabilite> recuperabilite,
            Provider<ServiceReset> reset,
            Provider<ServiceSauvegarde> sauvegarde,
            OccupationChrome occupation) {
        this.geste = new GesteReset(
                Objects.requireNonNull(recuperabilite, "recuperabilite")::get,
                Objects.requireNonNull(reset, "reset")::get,
                Objects.requireNonNull(sauvegarde, "sauvegarde")::get,
                Objects.requireNonNull(occupation, "occupation"),
                () -> proprietaire,
                Platform::exit);
    }

    @Override
    public GroupeMenu groupe() {
        return GroupeMenu.BASE;
    }

    /// Après « Restaurer une sauvegarde complète » (ordre 25).
    @Override
    public int ordre() {
        return 35;
    }

    @Override
    public String libelle() {
        return "Repartir d'une base neuve…";
    }

    @Override
    public String iconeLiteral() {
        return "fas-recycle";
    }

    @Override
    public void executer(Window proprietaire) {
        this.proprietaire = proprietaire;
        geste.lancer();
    }

    /// Geste exposé aux tests (#1405) : il porte les trois dialogues et la fermeture de l'application.
    GesteReset geste() {
        return geste;
    }
}
