package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.cli.commande.Restaurer;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Parité CLI de la sauvegarde (#1346, passe 2 du cycle de chantier) : `sauvegarder`, `sauvegarder
/// --complet` et `restaurer`.
///
/// Le cas qui compte est le **bilan amputé** : une racine de session non montée (carte SD retirée) doit
/// donner un code de sortie distinct : une sauvegarde qu'on croit complète et qui ne l'est pas est le pire
/// des deux mondes, surtout juste avant un reset (#1151).
class CliSauvegardeTest {

    @TempDir
    Path workspace;

    /// Un disque externe : la nuit y vit, hors du dossier de travail. C'est ce qui rend une racine
    /// « déplaçable » à la restauration.
    @TempDir
    Path disque;

    private Injector injecteur;
    private Cli cli;

    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        injecteur.getInstance(UtilisateurDao.class).insert(new Utilisateur("u1", "Alice"));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private String texteSortie() {
        return capture.texte();
    }

    @Test
    @DisplayName("sauvegarder : la base est copiée, et l'absence de l'audio est DITE")
    void sauvegarder_base_seule() {
        int code = cli.executer(new String[] {"sauvegarder"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("Base sauvegardée");
        assertThat(texteSortie())
                .as("la base seule ne protège pas l'audio, et la plateforme ne le rendra pas : le taire"
                        + " laisserait croire à une sauvegarde qui n'en est pas une")
                .contains("L'audio n'est PAS dans cette sauvegarde");
    }

    @Test
    @DisplayName("sauvegarder --complet : base + dossiers de session, bilan des dossiers copiés")
    void sauvegarder_complet() throws IOException {
        declarerSession(seederSession("Car040962-2026-Pass1-A1"), 1);

        int code = cli.executer(new String[] {"sauvegarder", "--complet"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("Sauvegarde complète").contains("1 dossier(s) de session copié(s)");
    }

    @Test
    @DisplayName("#3212 : les deux formes disent ce que l'archive emporte (parité avec l'IHM)")
    void sauvegarder_dit_ce_qu_elle_emporte() throws IOException {
        int codeSimple = cli.executer(new String[] {"sauvegarder"}, sortie, erreur);

        assertThat(codeSimple).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie())
                .as("l'ADR 2736 ne chiffre pas : la contrepartie est de dire ce qu'on écrit")
                .contains("localisations")
                .contains("en clair");

        // Les deux invocations écrivent dans le même tampon : l'assertion suivante porte donc sur le
        // cumul, ce qui reste discriminant (seule la branche complète parle d'enregistrements).
        declarerSession(seederSession("Car040962-2026-Pass1-A1"), 1);
        int codeComplet = cli.executer(new String[] {"sauvegarder", "--complet"}, sortie, erreur);

        assertThat(codeComplet).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie())
                .as("la complète emporte aussi les enregistrements")
                .contains("localisations")
                .contains("enregistrements");
    }

    @Test
    @DisplayName("sauvegarder --complet, carte SD non montée : code 2 et dossiers manquants listés")
    void sauvegarder_complet_incomplet() throws IOException {
        declarerSession(seederSession("Car040962-2026-Pass1-A1"), 1);
        // Racine jamais créée sur le disque : la trace exacte que laisse une carte SD retirée.
        declarerSession(workspace.resolve("Car040962-2026-Pass2-B2"), 2);

        int code = cli.executer(new String[] {"sauvegarder", "--complet"}, sortie, erreur);

        assertThat(code)
                .as("code de sortie distinct : un script doit pouvoir refuser de continuer sur ce résultat")
                .isEqualTo(2);
        assertThat(texteSortie())
                .contains("Sauvegarde INCOMPLÈTE")
                .contains("1 inaccessible(s)")
                .contains("Car040962-2026-Pass2-B2");
    }

    @Test
    @DisplayName("restaurer sans --confirmer : refus en 2 sur stderr, rien n'a été fait (#2294)")
    void restaurer_sans_confirmation_refuse() {
        int code = cli.executer(
                new String[] {"restaurer", workspace.resolve("peu-importe.db").toString()}, sortie, erreur);

        // 1 est le code de l'ÉCHEC d'exécution : le rendre ici laissait un script incapable de distinguer
        // « j'ai refusé, l'état local est intact » de « j'ai échoué en route », sur une commande destructive.
        assertThat(code).as("un refus se dit 2 : la commande n'a rien fait").isEqualTo(2);
        assertThat(capture.texteErreur())
                .as("un refus part sur stderr, pour ne pas se mêler au compte rendu")
                .contains("--confirmer");
    }

    @Test
    @DisplayName("restaurer une sauvegarde amputée : code distinct, pas un succès (#3500)")
    void restaurer_une_sauvegarde_amputee() throws IOException {
        declarerSession(seederSession("Car040962-2026-Pass1-A1"), 1);
        // La carte SD retirée : la nuit est connue de la base, sa racine n'existe pas, la sauvegarde
        // ne la contiendra donc pas.
        declarerSession(workspace.resolve("Car040962-2026-Pass2-B2"), 2);
        Path sauvegardes = workspace.resolve("sauvegardes");
        cli.executer(new String[] {"sauvegarder", "--complet", "--dossier", sauvegardes.toString()}, sortie, erreur);
        capture.vider();
        Path source = uniqueSauvegarde(sauvegardes);

        int code =
                cli.executer(new String[] {"restaurer", source.toString(), "--complet", "--confirmer"}, sortie, erreur);

        assertThat(texteSortie())
                .as("garde du dispositif : sans cette phrase la sauvegarde n'est pas amputée, et le test"
                        + " passerait au vert sans rien éprouver")
                .contains("n'étaient pas dans la sauvegarde");
        assertThat(texteSortie())
                .as("un 10 sans phrase renvoie l'utilisateur à la documentation : le compte rendu doit"
                        + " DIRE que la restauration est incomplète, et nommer le code")
                .contains("INCOMPLÈTE")
                .contains("code de sortie 10");
        assertThat(code)
                .as("la restauration a RÉUSSI - ni 1 ni 2 - mais un script qui teste $? doit apprendre"
                        + " qu'il manque une nuit, au lieu de le lire dans un texte qu'il ne lit pas")
                .isEqualTo(Restaurer.CODE_A_REGARDER);
    }

    @Test
    @DisplayName("restaurer sur une autre machine : les dossiers atterrissent ailleurs, et c'est 0 (#3500)")
    void restaurer_des_dossiers_deplaces_reste_un_succes() throws IOException {
        // La nuit vit sur un disque externe, hors du dossier de travail. On sauvegarde, le disque
        // disparaît : la nuit sera replacée dans le dossier de travail, donc « déplacée ».
        Path surLeDisque = disque.resolve("Car040962-2026-Pass1-A1");
        Files.createDirectories(surLeDisque.resolve("transformes"));
        Files.writeString(surLeDisque.resolve("transformes").resolve("seq.wav"), "audio");
        declarerSession(surLeDisque, 1);
        Path sauvegardes = workspace.resolve("sauvegardes");
        cli.executer(new String[] {"sauvegarder", "--complet", "--dossier", sauvegardes.toString()}, sortie, erreur);
        Path source = uniqueSauvegarde(sauvegardes);
        capture.vider();
        supprimerRecursivement(disque);

        int code =
                cli.executer(new String[] {"restaurer", source.toString(), "--complet", "--confirmer"}, sortie, erreur);

        assertThat(texteSortie())
                .as("garde du dispositif : sans cette phrase rien n'a été déplacé, et le test ne dirait"
                        + " rien du cas qu'il prétend éprouver")
                .contains("n'ont pas retrouvé leur emplacement d'origine");
        assertThat(code)
                .as("c'est l'usage PRINCIPAL de la sauvegarde complète : restaurer ailleurs. Rendre 10 ici"
                        + " apprendrait aux scripts à ignorer un 10 permanent, donc aussi celui qui compte")
                .isEqualTo(Cli.CODE_SUCCES);
    }

    private static void supprimerRecursivement(Path racine) throws IOException {
        try (var contenu = Files.walk(racine)) {
            contenu.sorted(java.util.Comparator.reverseOrder()).forEach(chemin -> {
                try {
                    Files.delete(chemin);
                } catch (IOException echec) {
                    throw new java.io.UncheckedIOException(echec);
                }
            });
        }
    }

    private static Path uniqueSauvegarde(Path dossier) throws IOException {
        try (var contenu = Files.list(dossier)) {
            return contenu.filter(Files::isDirectory).findFirst().orElseThrow();
        }
    }

    private Path seederSession(String nom) throws IOException {
        Path racine = workspace.resolve(nom);
        Files.createDirectories(racine.resolve("transformes"));
        Files.writeString(racine.resolve("transformes").resolve("seq.wav"), "audio");
        return racine;
    }

    /// Déclare la session en base sans rien exiger du disque : une carte SD retirée laisse exactement cette
    /// trace (une ligne `recording_session` dont la racine n'existe plus).
    private void declarerSession(Path racine, int idPassage) throws IOException {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            st.execute("INSERT INTO recording_session(root_path, originals_total_bytes, sequences_total_bytes,"
                    + " passage_id) VALUES ('" + racine.toString().replace("'", "''") + "', 0, 0, " + idPassage + ")");
        } catch (SQLException echec) {
            throw new IOException(echec);
        }
    }
}
