package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.stage.Window;

/// Entrée ☰ **« Ouvrir le dossier des journaux »** (#1523) : ouvre `<workspace>/logs/` dans le
/// gestionnaire de fichiers du système, pour retrouver la trace d'un incident et la joindre à un
/// signalement. [ActionMenu] socle (une implémentation + une ligne de binding, cf. #930).
///
/// Le dossier est créé au besoin - il l'est déjà si l'application a démarré normalement
/// ([fr.univ_amu.iut.commun.model.ConfigurationJournalisation]), mais l'ouvrir même vide vaut mieux que
/// n'ouvrir rien du tout.
public final class ActionOuvrirJournaux implements ActionMenu {

    private static final Logger LOG = Logger.getLogger(ActionOuvrirJournaux.class.getName());

    /// Le libellé de l'entrée, exposé parce qu'un **autre texte y renvoie** : l'alerte d'incident dit
    /// où regarder, et elle doit nommer le geste tel qu'il s'écrit dans le menu (#3470).
    ///
    /// Le citer plutôt que le recopier applique l'**ADR 3854** (« un refus ne conseille que ce qu'il a
    /// vérifié applicable ») par construction : une copie aurait dérivé le jour où l'entrée est
    /// renommée, en laissant un message qui envoie chercher une ligne de menu qui n'existe plus.
    static final String LIBELLE = "Ouvrir le dossier des journaux";

    private final Workspace workspace;
    private final OuvreurDeLien ouvreurDeLien;

    @Inject
    ActionOuvrirJournaux(Workspace workspace, OuvreurDeLien ouvreurDeLien) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.ouvreurDeLien = Objects.requireNonNull(ouvreurDeLien, "ouvreurDeLien");
    }

    @Override
    public GroupeMenu groupe() {
        return GroupeMenu.MAINTENANCE;
    }

    @Override
    public int ordre() {
        return 20;
    }

    @Override
    public String libelle() {
        return LIBELLE;
    }

    @Override
    public String iconeLiteral() {
        return "fas-folder-open";
    }

    @Override
    public void executer(Window proprietaire) {
        Path dossier = workspace.dossierLogs();
        try {
            Files.createDirectories(dossier);
        } catch (IOException echec) {
            LOG.log(Level.WARNING, echec, () -> "Dossier des journaux impossible à créer : " + dossier);
        }
        ouvreurDeLien.ouvrir(dossier.toUri().toString());
    }
}
