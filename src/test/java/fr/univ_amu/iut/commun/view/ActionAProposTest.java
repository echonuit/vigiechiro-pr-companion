package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import fr.univ_amu.iut.commun.model.VersionApplication;
import fr.univ_amu.iut.commun.model.Workspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.javafx.FontIcon;

/// L'entrée « À propos » sert à renseigner un signalement d'anomalie : ces tests vérifient qu'elle
/// dit bien ce qu'on ira y chercher, plutôt que de constater qu'un dialogue s'ouvre.
///
/// Le compte rendu est capté par un double posé sur [NotificateurModifiable] : c'est la raison d'être
/// de ce port (un `showAndWait` figerait un test headless, cf. ADR 0010).
class ActionAProposTest {

    /// Capte ce qui a été dit, pour l'examiner.
    private static final class NotificateurEspion implements Notificateur {
        private final List<String> messages = new ArrayList<>();
        private final List<String> entetes = new ArrayList<>();
        private final List<NiveauNotification> niveaux = new ArrayList<>();

        @Override
        public void notifier(NiveauNotification niveau, String entete, String message) {
            niveaux.add(niveau);
            entetes.add(entete);
            messages.add(message);
        }
    }

    private static ActionAPropos action(Path racine, VersionApplication version, NotificateurEspion espion) {
        ActionAPropos action = new ActionAPropos(version, new Workspace(racine));
        action.notificateur().definir(espion);
        return action;
    }

    @Test
    @DisplayName("annonce la version empaquetée et le dossier de travail")
    void annonceLaVersionEtLeDossier(@TempDir Path racine) {
        NotificateurEspion espion = new NotificateurEspion();

        action(racine, new VersionApplication(), espion).executer(null);

        assertThat(espion.niveaux).containsExactly(NiveauNotification.INFORMATION);
        assertThat(espion.entetes).allSatisfy(e -> assertThat(e).contains("VigieChiro"));
        assertThat(espion.messages.getLast())
                .as("le message doit porter ce qu'on ira chercher pour un signalement")
                .contains("Version :")
                .contains("Java ")
                .contains("Système :")
                .contains(racine.toString());
    }

    @Test
    @DisplayName("hors d'un jar, dit « version de développement » plutôt que rien")
    void horsJarLeMessageResteLisible(@TempDir Path racine) {
        NotificateurEspion espion = new NotificateurEspion();

        // La suite tourne sur les classes Maven : aucun manifeste, donc le repli s'applique.
        action(racine, new VersionApplication(), espion).executer(null);

        assertThat(espion.messages.getLast())
                .as("un « Version : » suivi de rien ne dirait pas si l'information manque")
                .contains("Version : " + VersionApplication.INCONNUE);
    }

    @Test
    @DisplayName("porte les mentions conventionnelles : éditeur, licence, code source")
    void porteLesMentionsConventionnelles(@TempDir Path racine) {
        NotificateurEspion espion = new NotificateurEspion();

        action(racine, new VersionApplication(), espion).executer(null);

        // La licence n'est pas une politesse : la GPLv3 impose que le destinataire d'un binaire sache
        // sous quelles conditions il le reçoit, et où en obtenir les sources.
        assertThat(espion.messages.getLast())
                .contains("Sébastien Nedjar")
                .contains("GNU General Public License v3.0")
                .contains("github.com/IUTInfoAix-S201/vigiechiro-pr-companion");
    }

    @Test
    @DisplayName("se range près des journaux, qu'on cherche au même moment")
    void seRangePresDesJournaux(@TempDir Path racine) {
        ActionAPropos action = action(racine, new VersionApplication(), new NotificateurEspion());

        assertThat(action.groupe()).isEqualTo(GroupeMenu.MAINTENANCE);
        assertThat(action.libelle()).isEqualTo("À propos");
        assertThat(action.iconeLiteral())
                .as("un pictogramme est une icône, jamais un caractère (ADR 0035)")
                .isNotBlank();
    }

    @Test
    @DisplayName("l'icône existe dans le pack embarqué, faute de quoi l'écran ne se charge plus")
    void iconeExistanteDansLePack(@TempDir Path racine) {
        ActionAPropos action = action(racine, new VersionApplication(), new NotificateurEspion());

        // Un nom d'icône absent du pack ne fait PAS rougir la compilation : il lève au chargement du
        // FXML, donc à l'ouverture de l'écran. C'est ce qui est arrivé avec `fas-circle-info`, nom
        // FontAwesome 6 alors que le dépôt embarque le pack 5 - seule la régénération des captures
        // l'a attrapé, en CI.
        assertThatCode(() -> new FontIcon(action.iconeLiteral()))
                .as("le nom d'icône doit exister dans le pack Ikonli embarqué")
                .doesNotThrowAnyException();
    }
}
