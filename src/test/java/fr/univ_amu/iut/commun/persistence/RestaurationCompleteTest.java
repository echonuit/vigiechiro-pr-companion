package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Ce qu'une **restauration complète** fait des dossiers de son, et de la base qui les désigne
/// (#2727, lot 1 du chantier de dette #2720).
///
/// La restauration déversait les dossiers à la racine du workspace et ne touchait pas aux
/// `root_path` : la base restaurée continuait de pointer vers des chemins qui peuvent ne plus
/// exister. La promesse « la restauration remet la base et les dossiers de son » ne tenait donc que
/// sur la machine d'origine, et rien ne le disait.
///
/// Les nuits vivent ici **hors du workspace**, sur un « disque » séparé : c'est la seule topologie
/// où le défaut se voit, et c'est celle d'un utilisateur qui garde son audio sur un disque externe.
class RestaurationCompleteTest {

    @TempDir
    Path racine;

    private Path workspaceDir;
    private Path disque;
    private SourceDeDonnees source;
    private ServiceSauvegarde service;
    private int passageSuivant;

    @BeforeEach
    void preparer() {
        workspaceDir = racine.resolve("ws");
        disque = racine.resolve("disque-externe");
        source = new SourceDeDonnees(new Workspace(workspaceDir));
        new MigrationSchema(source).migrer();
        service = new ServiceSauvegarde(source, new HorlogeFigee(LocalDateTime.of(2026, 8, 2, 10, 0)));
    }

    @Test
    @DisplayName("sur la machine d'origine, la nuit revient à sa place et la base n'est pas touchée")
    void restauration_sur_la_machine_d_origine() throws IOException {
        Path nuit = seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        Files.delete(nuit.resolve("transformes").resolve("seq.wav"));

        BilanRestauration bilan = service.restaurerComplet(backup);

        assertThat(nuit.resolve("transformes").resolve("seq.wav"))
                .as("le disque est là : la nuit retrouve son emplacement, sans traverser le workspace")
                .exists();
        assertThat(racinesEnBase())
                .as("et la base n'a aucune raison d'être corrigée")
                .containsExactly(nuit.toString());
        assertThat(bilan.appelleUnRegard()).isFalse();
    }

    @Test
    @DisplayName("sans son disque, la nuit atterrit dans le workspace ET la base y est redirigée")
    void restauration_sur_une_autre_machine() throws IOException {
        Path nuit = seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        // Le disque externe n'est pas branché sur la machine où l'on restaure.
        supprimerRecursif(disque);

        BilanRestauration bilan = service.restaurerComplet(backup);

        Path repli = workspaceDir.resolve("Nuit-01");
        assertThat(repli.resolve("transformes").resolve("seq.wav"))
                .as("l'audio doit atterrir quelque part d'accessible")
                .exists();
        assertThat(racinesEnBase())
                .as("et la base doit désigner cet endroit-là : c'est tout le défaut, une base restaurée"
                        + " qui pointait vers un dossier absent")
                .containsExactly(repli.toString());
        assertThat(bilan.placements()).singleElement().satisfies(placement -> {
            assertThat(placement.origine()).isEqualTo(nuit.toString());
            assertThat(placement.destination()).isEqualTo(repli.toString());
            assertThat(placement.deplacee()).isTrue();
        });
        assertThat(bilan.enClair())
                .as("l'utilisateur doit apprendre que ses gigaoctets ont changé de disque")
                .contains("n'ont pas retrouvé leur emplacement d'origine");
    }

    @Test
    @DisplayName("une nuit absente de la sauvegarde est signalée, pas inventée")
    void nuit_absente_de_la_sauvegarde() throws IOException {
        seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        // Une seconde nuit connue de la base, dont la racine n'existe pas : carte SD retirée au moment
        // de la sauvegarde (#1346). Elle n'est donc pas dans la sauvegarde.
        Path jamaisCopiee = disque.resolve("Nuit-02");
        declarerSession(jamaisCopiee);
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();

        BilanRestauration bilan = service.restaurerComplet(backup);

        assertThat(bilan.absentesDeLaSauvegarde())
                .as("lui inventer une place serait pire que de dire qu'elle manque")
                .containsExactly(jamaisCopiee.toString());
        assertThat(bilan.enClair()).contains("n'étaient pas dans la sauvegarde");
    }

    @Test
    @DisplayName("une sauvegarde abîmée est refusée AVANT que rien ne soit touché")
    void sauvegarde_abimee_refusee_sans_rien_toucher() throws IOException {
        seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        // La base évolue APRÈS la sauvegarde : c'est cet état-là qui ne doit pas être détruit.
        declarerSession(disque.resolve("Nuit-99"));
        retirerUnFichierDeLaSauvegarde(backup);

        assertThatThrownBy(() -> service.restaurerComplet(backup))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ne correspond pas à ce que la sauvegarde annonce");

        assertThat(racinesEnBase())
                .as("la base courante est INTACTE : la vérification passe avant la moindre écriture")
                .contains(disque.resolve("Nuit-99").toString());
        assertThat(workspaceDir.resolve(Workspace.FICHIER_BASE + ".avant-restauration"))
                .as("le filet de la restauration de base n'a même pas eu à être posé")
                .doesNotExist();
    }

    @Test
    @DisplayName("une sauvegarde sans manifeste se restaure comme avant, et le bilan le dit")
    void sauvegarde_sans_manifeste() throws IOException {
        seederNuit(workspaceDir.resolve("Nuit-01"), "audio d'origine");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        renommerCommeAvant(backup);
        Files.delete(backup.resolve(ManifesteSauvegarde.NOM_FICHIER));
        Files.delete(workspaceDir.resolve("Nuit-01").resolve("transformes").resolve("seq.wav"));

        BilanRestauration bilan = service.restaurerComplet(backup);

        assertThat(workspaceDir.resolve("Nuit-01").resolve("transformes").resolve("seq.wav"))
                .as("le produit continue à restaurer ce qu'il savait restaurer hier")
                .exists();
        assertThat(bilan.manifestePresent()).isFalse();
        assertThat(bilan.enClair())
                .as("et il annonce ce qu'il n'a PAS pu faire, plutôt que de laisser croire à mieux")
                .contains("antérieure au format actuel");
    }

    private Path seederNuit(Path racineSession, String contenu) throws IOException {
        Files.createDirectories(racineSession.resolve("transformes"));
        Files.writeString(racineSession.resolve("transformes").resolve("seq.wav"), contenu);
        declarerSession(racineSession);
        return racineSession;
    }

    private void declarerSession(Path racineSession) throws IOException {
        passageSuivant++;
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            st.execute("INSERT INTO recording_session(root_path, originals_total_bytes,"
                    + " sequences_total_bytes, passage_id) VALUES ('"
                    + racineSession.toString().replace("'", "''") + "', 0, 0, " + passageSuivant + ")");
        } catch (SQLException echec) {
            throw new IOException(echec);
        }
    }

    private List<String> racinesEnBase() {
        List<String> racines = new ArrayList<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("SELECT root_path FROM recording_session ORDER BY id")) {
            while (rs.next()) {
                racines.add(rs.getString(1));
            }
        } catch (SQLException echec) {
            throw new IllegalStateException("Lecture des racines impossible", echec);
        }
        return racines;
    }

    /// Retire un fichier du dossier `sessions/` de la sauvegarde : elle ne correspond plus à son
    /// manifeste, exactement comme une sauvegarde tronquée par un disque plein ou une copie partielle.
    private static void retirerUnFichierDeLaSauvegarde(Path backup) throws IOException {
        try (Stream<Path> arbre = Files.walk(backup.resolve("sessions"))) {
            Files.delete(arbre.filter(Files::isRegularFile).findFirst().orElseThrow());
        }
    }

    /// Renomme l'unique dossier de session en son nom d'avant #2726 (sans condensé).
    private static void renommerCommeAvant(Path backup) throws IOException {
        Path sessions = backup.resolve("sessions");
        try (Stream<Path> dossiers = Files.list(sessions)) {
            Files.move(dossiers.findFirst().orElseThrow(), sessions.resolve("Nuit-01"));
        }
    }

    private static void supprimerRecursif(Path racine) throws IOException {
        try (Stream<Path> arbre = Files.walk(racine)) {
            for (Path chemin :
                    arbre.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.delete(chemin);
            }
        }
    }
}
