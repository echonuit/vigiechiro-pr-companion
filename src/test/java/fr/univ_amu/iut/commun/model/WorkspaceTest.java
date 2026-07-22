package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests unitaires du socle [Workspace] : résolution des chemins (base SQLite R21, dossiers de
/// session `bruts`/`transformes` R22), workspace par défaut, racine rendue absolue, et emplacement
/// de la base rendu choisissable (#1038).
class WorkspaceTest {

    private static final String PREFIXE = "Car640380-2026-Pass1-A1";
    private static final String PROP_WORKSPACE = "vigiechiro.workspace";
    private final Workspace ws = new Workspace(Path.of("relatif/ws"));

    @Test
    @DisplayName("La racine fournie est rendue absolue")
    void racine_absolue() {
        assertThat(ws.racine().isAbsolute()).isTrue();
        assertThat(ws.racine()).isEqualTo(Path.of("relatif/ws").toAbsolutePath());
    }

    @Test
    @DisplayName("Le chemin de la base est <racine>/vigiechiro.db")
    void chemin_base() {
        assertThat(ws.cheminBaseDeDonnees()).isEqualTo(ws.racine().resolve("vigiechiro.db"));
    }

    @Test
    @DisplayName("Les dossiers d'une session : <racine>/<prefixe>/{bruts,transformes}")
    void dossiers_session() {
        Path session = ws.racine().resolve(PREFIXE);
        assertThat(ws.dossierSession(PREFIXE)).isEqualTo(session);
        assertThat(ws.dossierBruts(PREFIXE)).isEqualTo(session.resolve("bruts"));
        assertThat(ws.dossierTransformes(PREFIXE)).isEqualTo(session.resolve("transformes"));
    }

    @Test
    @DisplayName("Le dossier des journaux est <racine>/logs (#1523)")
    void dossier_logs() {
        assertThat(ws.dossierLogs()).isEqualTo(ws.racine().resolve("logs"));
    }

    @Test
    @DisplayName("Le workspace par défaut est sous ~/Documents/VigieChiro-Companion (R21)")
    void par_defaut() {
        Path racine = Workspace.parDefaut().racine();
        assertThat(racine.isAbsolute()).isTrue();
        // On compare via java.nio.file.Path.endsWith (purement lexical) et non via
        // AssertJ assertThat(path).endsWith(...), qui canonicalise le chemin reel
        // (toRealPath) et leve NoSuchFileException quand le dossier n'existe pas encore
        // sur le disque - typiquement sur un runner CI neuf.
        assertThat(racine.endsWith(Path.of("Documents", "VigieChiro-Companion")))
                .as("le workspace par defaut est sous ~/Documents/VigieChiro-Companion")
                .isTrue();
    }

    @Test
    @DisplayName("toString mentionne la racine")
    void to_string() {
        assertThat(ws.toString()).startsWith("Workspace[").contains("ws");
    }

    @Test
    @DisplayName("#1038 : la base peut vivre ailleurs, sans que le reste du workspace bouge")
    void base_deplacee() {
        Workspace deplace = new Workspace(Path.of("relatif/ws"), Path.of("/coffre/vigiechiro.db"));

        assertThat(deplace.cheminBaseDeDonnees()).isEqualTo(Path.of("/coffre/vigiechiro.db"));
        assertThat(deplace.racine())
                .as("déplacer la base ne déménage pas l'audio : c'est tout l'intérêt du recadrage")
                .isEqualTo(Path.of("relatif/ws").toAbsolutePath());
        assertThat(deplace.dossierSession(PREFIXE))
                .isEqualTo(Path.of("relatif/ws").toAbsolutePath().resolve(PREFIXE));
    }

    @Test
    @DisplayName("toString ne nomme la base que si elle a quitté la racine")
    void to_string_nomme_la_base_deplacee() {
        assertThat(ws.toString())
                .as("base à sa place : la répéter dans chaque message d'erreur serait du bruit")
                .doesNotContain("base=");
        assertThat(new Workspace(Path.of("relatif/ws"), Path.of("/coffre/vigiechiro.db")).toString())
                .contains("base=")
                .contains("coffre");
    }

    @Test
    @DisplayName("#1038 : la configuration d'amorçage porte les deux emplacements")
    void resolu_lit_la_configuration_persistee(@TempDir Path configuration) throws IOException {
        new ConfigurationAmorcage(Optional.of(Path.of("/donnees/nuits")), Optional.of(Path.of("/coffre/vc.db")))
                .enregistrerDans(configuration);

        Workspace resolu = sansSurcharge(configuration, Workspace::resolu);

        assertThat(resolu.racine()).isEqualTo(Path.of("/donnees/nuits"));
        assertThat(resolu.cheminBaseDeDonnees()).isEqualTo(Path.of("/coffre/vc.db"));
    }

    @Test
    @DisplayName("#1038 : espace de travail choisi seul, la base le suit")
    void resolu_base_suit_l_espace_de_travail(@TempDir Path configuration) throws IOException {
        new ConfigurationAmorcage(Optional.of(Path.of("/donnees/nuits")), Optional.empty())
                .enregistrerDans(configuration);

        Workspace resolu = sansSurcharge(configuration, Workspace::resolu);

        assertThat(resolu.cheminBaseDeDonnees())
                .as("ne choisir que le dossier de travail garde la base dedans, comme avant")
                .isEqualTo(Path.of("/donnees/nuits", "vigiechiro.db"));
    }

    @Test
    @DisplayName("#1038 : la propriété système garde la priorité sur la configuration persistée")
    void surcharge_systeme_prioritaire(@TempDir Path configuration) throws IOException {
        new ConfigurationAmorcage(Optional.of(Path.of("/donnees/nuits")), Optional.of(Path.of("/coffre/vc.db")))
                .enregistrerDans(configuration);

        Workspace resolu = avecProprietes(
                configuration.toString(), Path.of("ponctuel").toAbsolutePath().toString(), Workspace::resolu);

        assertThat(resolu.racine())
                .as("un emplacement demandé pour CETTE exécution ne doit pas être repris par un réglage"
                        + " persisté : c'est ce qui isole les tests et les lancements ponctuels")
                .isEqualTo(Path.of("ponctuel").toAbsolutePath());
        assertThat(resolu.cheminBaseDeDonnees())
                .isEqualTo(Path.of("ponctuel").toAbsolutePath().resolve("vigiechiro.db"));
    }

    private static Workspace sansSurcharge(Path configuration, Supplier<Workspace> action) {
        return avecProprietes(configuration.toString(), null, action);
    }

    /// Pose les deux propriétés d'amorçage le temps de l'action, puis restaure exactement ce qui était
    /// là. Surefire pose `vigiechiro.config` pour toute la suite : l'oublier contaminerait les tests
    /// suivants du même fork.
    private static Workspace avecProprietes(String dossierConfiguration, String surcharge, Supplier<Workspace> action) {
        String configAvant = System.getProperty(ConfigurationAmorcage.PROP_DOSSIER);
        String surchargeAvant = System.getProperty(PROP_WORKSPACE);
        try {
            System.setProperty(ConfigurationAmorcage.PROP_DOSSIER, dossierConfiguration);
            if (surcharge == null) {
                System.clearProperty(PROP_WORKSPACE);
            } else {
                System.setProperty(PROP_WORKSPACE, surcharge);
            }
            return action.get();
        } finally {
            restaurer(ConfigurationAmorcage.PROP_DOSSIER, configAvant);
            restaurer(PROP_WORKSPACE, surchargeAvant);
        }
    }

    private static void restaurer(String cle, String valeur) {
        if (valeur == null) {
            System.clearProperty(cle);
        } else {
            System.setProperty(cle, valeur);
        }
    }
}
