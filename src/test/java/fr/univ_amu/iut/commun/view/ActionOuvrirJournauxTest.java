package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// L'entrée ☰ « Ouvrir le dossier des journaux » (#1523) ouvre `<workspace>/logs/`, et crée le
/// dossier au besoin.
///
/// Le banc éprouve les **deux** sorties. Elle demandait auparavant une URI `file://` au geste du
/// navigateur, qui rendait un listing de répertoire au lieu d'un dossier (#4982) ; et quand le
/// système ne sait pas ouvrir, il reste à **dire le chemin**, faute de quoi le clic ne produit
/// rien du tout et personne ne sait où sont les journaux qu'on demandait de joindre.
class ActionOuvrirJournauxTest {

    /// Ce que le port a reçu, geste par geste, et si le système a su répondre.
    private static final class Ouvreur implements OuvreurDeLien {
        private final List<String> liens = new ArrayList<>();
        private final List<Path> dossiers = new ArrayList<>();
        private final boolean sait;

        Ouvreur(boolean sait) {
            this.sait = sait;
        }

        @Override
        public void ouvrir(String url) {
            liens.add(url);
        }

        @Override
        public boolean ouvrirDossier(Path dossier) {
            dossiers.add(dossier);
            return sait;
        }
    }

    @Test
    @DisplayName("#4982 : demande un DOSSIER, et non une URI au navigateur")
    void ouvre_le_dossier_des_journaux(@TempDir Path racine) {
        Workspace workspace = new Workspace(racine);
        Ouvreur ouvreur = new Ouvreur(true);
        ActionOuvrirJournaux action = new ActionOuvrirJournaux(workspace, ouvreur);
        List<String> annonces = new ArrayList<>();
        action.notificateur().definir((niveau, entete, message) -> annonces.add(entete));

        action.executer(null);

        assertThat(workspace.dossierLogs())
                .as("le dossier des journaux est créé au besoin")
                .isDirectory();
        assertThat(ouvreur.dossiers).containsExactly(workspace.dossierLogs());
        assertThat(ouvreur.liens).as("rien ne part au navigateur").isEmpty();
        assertThat(annonces).as("le système a su ouvrir : rien à dire").isEmpty();
    }

    @Test
    @DisplayName("#4982 : quand le système ne sait pas ouvrir, le chemin est DIT")
    void le_chemin_est_dit_quand_l_ouverture_echoue(@TempDir Path racine) {
        Workspace workspace = new Workspace(racine);
        Ouvreur ouvreur = new Ouvreur(false);
        ActionOuvrirJournaux action = new ActionOuvrirJournaux(workspace, ouvreur);
        List<String> annonces = new ArrayList<>();
        action.notificateur().definir((niveau, entete, message) -> annonces.add(message));

        action.executer(null);

        // Sans cette sortie, l'échec est SILENCIEUX : on demande à quelqu'un de joindre ses journaux
        // à un signalement, le clic ne fait rien, et il n'a aucun moyen de les trouver.
        assertThat(annonces)
                .singleElement()
                .asString()
                .contains(workspace.dossierLogs().toString());
        assertThat(ouvreur.liens).as("on ne se rabat jamais sur le navigateur").isEmpty();
    }
}
