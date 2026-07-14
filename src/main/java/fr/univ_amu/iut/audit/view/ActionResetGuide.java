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

    // Providers, non instances directes : le menu ☰ est bâti au démarrage du chrome, AVANT que quiconque
    // ait ouvert la base. Résoudre ServiceReset ici tirerait les rapprocheurs → `idUtilisateurCourant` →
    // une requête SQL sur une base pas encore migrée. Le reset ne se résout qu'au clic.
    private final Provider<ServiceRecuperabilite> recuperabilite;
    private final Provider<ServiceReset> reset;
    private final Provider<ServiceSauvegarde> sauvegarde;
    private final OccupationChrome occupation;

    @Inject
    ActionResetGuide(
            Provider<ServiceRecuperabilite> recuperabilite,
            Provider<ServiceReset> reset,
            Provider<ServiceSauvegarde> sauvegarde,
            OccupationChrome occupation) {
        this.recuperabilite = Objects.requireNonNull(recuperabilite, "recuperabilite");
        this.reset = Objects.requireNonNull(reset, "reset");
        this.sauvegarde = Objects.requireNonNull(sauvegarde, "sauvegarde");
        this.occupation = Objects.requireNonNull(occupation, "occupation");
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
        return "♻ Repartir d'une base neuve…";
    }

    @Override
    public void executer(Window proprietaire) {
        new GesteReset(
                        recuperabilite.get(),
                        reset.get(),
                        sauvegarde.get(),
                        occupation,
                        () -> proprietaire,
                        Platform::exit)
                .lancer();
    }
}
