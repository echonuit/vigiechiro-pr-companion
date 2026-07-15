package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// L'entrée ☰ « Ouvrir le dossier des journaux » (#1523) ouvre `<workspace>/logs/` via l'[OuvreurDeLien]
/// (un faux enregistre l'URL, sans ouvrir de gestionnaire de fichiers), et crée le dossier au besoin.
class ActionOuvrirJournauxTest {

    @Test
    @DisplayName("Ouvre l'URI file:// du dossier des journaux, créé au passage")
    void ouvre_le_dossier_des_journaux(@TempDir Path racine) {
        Workspace workspace = new Workspace(racine);
        List<String> urlsOuvertes = new ArrayList<>();
        ActionOuvrirJournaux action = new ActionOuvrirJournaux(workspace, urlsOuvertes::add);

        action.executer(null);

        assertThat(workspace.dossierLogs())
                .as("le dossier des journaux est créé au besoin")
                .isDirectory();
        assertThat(urlsOuvertes)
                .singleElement()
                .isEqualTo(workspace.dossierLogs().toUri().toString());
    }
}
