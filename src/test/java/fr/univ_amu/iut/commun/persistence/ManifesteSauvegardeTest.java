package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
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

/// Ce qu'une **sauvegarde complète** garde de l'endroit d'où venaient les dossiers (#2726, lot 1 du
/// chantier de dette #2720).
///
/// Deux défauts se tenaient ensemble : la destination d'une copie était le **dernier segment** du
/// chemin, donc deux nuits homonymes sur deux disques fusionnaient en silence ; et rien ne
/// conservait le chemin d'origine, donc la restauration ne pouvait ni remettre les dossiers en place
/// ni corriger la base. Le manifeste répond aux deux.
///
/// Les tests placent délibérément des racines **hors du workspace**, sur deux « disques » distincts :
/// c'est la topologie réelle d'un utilisateur, et c'est précisément celle que la suite existante
/// n'exerçait pas.
class ManifesteSauvegardeTest {

    @TempDir
    Path racine;

    private Path workspaceDir;
    private SourceDeDonnees source;
    private ServiceSauvegarde service;
    private int passageSuivant;

    @BeforeEach
    void preparer() {
        workspaceDir = racine.resolve("ws");
        source = new SourceDeDonnees(new Workspace(workspaceDir));
        new MigrationSchema(source).migrer();
        service = new ServiceSauvegarde(source, new HorlogeFigee(LocalDateTime.of(2026, 8, 2, 10, 0, 0)));
    }

    @Test
    @DisplayName("deux nuits homonymes sur deux disques ne fusionnent plus")
    void deux_racines_homonymes_ne_fusionnent_pas() throws IOException {
        // Même arborescence, même nom de fichier : c'est ce qui rend la fusion destructrice. Deux
        // nuits capturées par le même modèle d'enregistreur se ressemblent exactement comme cela.
        Path surDisqueA = seederRacine(racine.resolve("disque-a").resolve("Nuit-01"), "audio du disque A");
        Path surDisqueB = seederRacine(racine.resolve("disque-b").resolve("Nuit-01"), "audio du disque B");

        BilanSauvegarde bilan = service.sauvegarderComplet(racine.resolve("sauvegardes"));

        assertThat(bilan.sessionsCopiees()).isEqualTo(2);
        assertThat(contenusSauvegardes(bilan.dossier()))
                .as("DEUX contenus dans la sauvegarde : la seconde nuit écrasait la première dans la"
                        + " destination commune, et celle-ci partait sans un mot")
                .containsExactlyInAnyOrder("audio du disque A", "audio du disque B");
        assertThat(dossiersDeSession(bilan.dossier()))
                .as("dans deux dossiers distincts, tous deux reconnaissables")
                .hasSize(2)
                .allSatisfy(nom -> assertThat(nom).startsWith("Nuit-01-"));
        assertThat(origines(bilan.dossier()))
                .as("le manifeste dit de quel disque chacune venait, ce que la sauvegarde ne savait" + " pas conserver")
                .containsExactlyInAnyOrder(surDisqueA.toString(), surDisqueB.toString());
    }

    @Test
    @DisplayName("le manifeste décrit ce que la sauvegarde contient vraiment")
    void le_manifeste_decrit_le_contenu() throws IOException {
        Path nuit = seederRacine(racine.resolve("disque-a").resolve("Nuit-01"), "audio de la nuit");
        Files.writeString(nuit.resolve("transformes").resolve("autre.wav"), "encore de l'audio");

        BilanSauvegarde bilan = service.sauvegarderComplet(racine.resolve("sauvegardes"));

        ManifesteSauvegarde manifeste =
                ManifesteSauvegardeJson.lire(bilan.dossier()).orElseThrow();
        assertThat(manifeste.version()).isEqualTo(ManifesteSauvegarde.VERSION_COURANTE);
        RacineSauvegardee emportee = manifeste.racines().getFirst();
        assertThat(emportee.cheminOrigine()).isEqualTo(nuit.toString());
        assertThat(emportee.fichiers()).as("les deux fichiers de la nuit").isEqualTo(2);
        assertThat(emportee.octets())
                .as("la somme de leurs tailles, de quoi voir une copie amputée")
                .isEqualTo(taille(nuit.resolve("transformes").resolve("seq.wav"))
                        + taille(nuit.resolve("transformes").resolve("autre.wav")));
        assertThat(emportee.empreinte()).as("SHA-256 hexadécimal").hasSize(64);
    }

    @Test
    @DisplayName("l'empreinte de l'inventaire change dès qu'un fichier manque")
    void l_empreinte_suit_le_contenu() throws IOException {
        Path nuit = seederRacine(racine.resolve("disque-a").resolve("Nuit-01"), "audio de la nuit");
        String avant = ManifesteSauvegardeJson.lire(
                        service.sauvegarderComplet(racine.resolve("s1")).dossier())
                .orElseThrow()
                .racines()
                .getFirst()
                .empreinte();

        Files.delete(nuit.resolve("transformes").resolve("seq.wav"));
        String apres = ManifesteSauvegardeJson.lire(
                        service.sauvegarderComplet(racine.resolve("s2")).dossier())
                .orElseThrow()
                .racines()
                .getFirst()
                .empreinte();

        assertThat(apres)
                .as("l'empreinte de l'inventaire est ce qui permettra de vérifier une restauration")
                .isNotEqualTo(avant);
    }

    @Test
    @DisplayName("deux sauvegardes complètes dans la même seconde ne s'écrasent pas")
    void deux_sauvegardes_dans_la_meme_seconde() throws IOException {
        seederRacine(racine.resolve("disque-a").resolve("Nuit-01"), "audio de la nuit");
        Path destination = racine.resolve("sauvegardes");

        Path premiere = service.sauvegarderComplet(destination).dossier();
        Path seconde = service.sauvegarderComplet(destination).dossier();

        assertThat(seconde)
                .as("l'horodatage est à la seconde et l'horloge est figée : sans suffixe, la seconde"
                        + " sauvegarde écraserait la première, qu'on venait peut-être de faire exprès")
                .isNotEqualTo(premiere);
        assertThat(premiere.resolve("base").resolve("vigiechiro.db")).exists();
        assertThat(seconde.resolve("base").resolve("vigiechiro.db")).exists();
    }

    @Test
    @DisplayName("l'empreinte distingue deux arbres de même compte et de même poids total")
    void l_empreinte_distingue_un_echange_de_noms() throws IOException {
        Path premiere = seederRacine(racine.resolve("disque-a").resolve("Nuit-01"), "court");
        Files.writeString(premiere.resolve("transformes").resolve("autre.wav"), "beaucoup plus long");
        String avant =
                empreinteUnique(service.sauvegarderComplet(racine.resolve("s1")).dossier());

        // Mêmes noms, mêmes tailles, mais échangées : même nombre de fichiers et même poids total.
        Files.writeString(premiere.resolve("transformes").resolve("seq.wav"), "beaucoup plus long");
        Files.writeString(premiere.resolve("transformes").resolve("autre.wav"), "court");

        assertThat(empreinteUnique(
                        service.sauvegarderComplet(racine.resolve("s2")).dossier()))
                .as("une empreinte qui ne regarde que le compte et le poids ne verrait pas l'échange,"
                        + " et une restauration qui a mélangé deux fichiers passerait pour fidèle")
                .isNotEqualTo(avant);
    }

    @Test
    @DisplayName("une sauvegarde sans manifeste se restaure comme avant")
    void sauvegarde_heritee_sans_manifeste() throws IOException {
        Path nuit = seederRacine(workspaceDir.resolve("Nuit-01"), "audio de la nuit");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        // Une sauvegarde d'avant ce format : dossiers nommés par leur dernier segment, sans manifeste.
        renommerCommeAvant(backup, "Nuit-01");
        Files.delete(backup.resolve(ManifesteSauvegarde.NOM_FICHIER));
        Files.delete(nuit.resolve("transformes").resolve("seq.wav"));

        service.restaurerComplet(backup);

        assertThat(nuit.resolve("transformes").resolve("seq.wav"))
                .as("le produit doit continuer à restaurer ce qu'il savait restaurer hier")
                .exists();
    }

    @Test
    @DisplayName("un manifeste présent mais abîmé est refusé, pas ignoré")
    void manifeste_abime_refuse() throws IOException {
        seederRacine(workspaceDir.resolve("Nuit-01"), "audio de la nuit");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        Files.writeString(backup.resolve(ManifesteSauvegarde.NOM_FICHIER), "{ ceci n'est pas du JSON");

        assertThatThrownBy(() -> service.restaurerComplet(backup))
                .as("le traiter comme absent ferait silencieusement moins bien que promis, sur la"
                        + " seule sauvegarde dont on sait qu'elle a un problème")
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("manifeste");
    }

    /// Crée `<racineSession>/transformes/seq.wav` avec `contenu`, et déclare la session en base. Le
    /// nom du fichier est **volontairement le même** d'une racine à l'autre : c'est la topologie
    /// réelle, et c'est elle qui rend une fusion destructrice.
    private Path seederRacine(Path racineSession, String contenu) throws IOException {
        Files.createDirectories(racineSession.resolve("transformes"));
        Files.writeString(racineSession.resolve("transformes").resolve("seq.wav"), contenu);
        declarerSession(racineSession);
        return racineSession;
    }

    /// Déclare une ligne `recording_session` pointant sur `racineSession` (FK désactivées : seul
    /// `root_path` importe ici). `passage_id` est UNIQUE, d'où le compteur.
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

    /// Renomme l'unique dossier de session en son nom d'avant #2726 (sans condensé).
    private static void renommerCommeAvant(Path backup, String nomHerite) throws IOException {
        Path sessions = backup.resolve("sessions");
        try (Stream<Path> dossiers = Files.list(sessions)) {
            Path unique = dossiers.findFirst().orElseThrow();
            Files.move(unique, sessions.resolve(nomHerite));
        }
    }

    private static List<String> dossiersDeSession(Path backup) throws IOException {
        try (Stream<Path> dossiers = Files.list(backup.resolve("sessions"))) {
            return dossiers.map(dossier -> dossier.getFileName().toString()).toList();
        }
    }

    /// Contenu de chaque fichier de `sessions/`, tous dossiers confondus : c'est là qu'une fusion se
    /// voit, un fichier écrasé ne laissant ni trace ni message.
    private static List<String> contenusSauvegardes(Path backup) throws IOException {
        try (Stream<Path> arbre = Files.walk(backup.resolve("sessions"))) {
            List<String> contenus = new ArrayList<>();
            for (Path fichier : (Iterable<Path>) arbre.filter(Files::isRegularFile)::iterator) {
                contenus.add(Files.readString(fichier));
            }
            return contenus;
        }
    }

    private static String empreinteUnique(Path backup) {
        return ManifesteSauvegardeJson.lire(backup)
                .orElseThrow()
                .racines()
                .getFirst()
                .empreinte();
    }

    private static List<String> origines(Path backup) {
        return ManifesteSauvegardeJson.lire(backup).orElseThrow().racines().stream()
                .map(RacineSauvegardee::cheminOrigine)
                .toList();
    }

    private static long taille(Path fichier) throws IOException {
        return Files.size(fichier);
    }
}
