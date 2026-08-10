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
    /// Volontairement décalé : sans cela le premier passage et la première session porteraient tous
    /// deux l'identifiant 1, et un test ne saurait pas dire si le code cherche par la bonne clé.
    private int passageSuivant = 10;

    @BeforeEach
    void preparer() {
        workspaceDir = racine.resolve("ws");
        disque = racine.resolve("disque-externe");
        source = new SourceDeDonnees(new Workspace(workspaceDir));
        new MigrationSchema(source).migrer();
        service = new ServiceSauvegarde(source, new HorlogeFigee(LocalDateTime.of(2026, 8, 2, 10, 0)));
    }

    @Test
    @DisplayName("place juste insuffisante pour tout étaler : on dégrade, on ne refuse pas (#3563)")
    void place_insuffisante_pour_l_ensemble_degrade_au_lieu_de_refuser() throws IOException {
        seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        seederNuit(disque.resolve("Nuit-02"), "second audio");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        long total = octetsDuManifeste(backup);
        // Un octet de moins que le total : de quoi étaler une nuit, pas les deux.
        ServiceSauvegarde serre = avecEspace(total - 1);

        BilanRestauration bilan = serre.restaurerComplet(backup);

        assertThat(bilan.regime())
                .as("refuser ici serait la rigidité que l'ADR 2727 reprochait déjà à la zone temporaire :"
                        + " la place suffit pour une nuit à la fois, donc la restauration doit aboutir")
                .isEqualTo(RegimeRestauration.RACINE_PAR_RACINE);
        assertThat(bilan.enClair())
                .as("la garantie est moindre, et c'est la contrepartie de la souplesse : il faut le DIRE")
                .contains("une nuit à la fois");
    }

    @Test
    @DisplayName("pas même de quoi étaler une nuit : refus chiffré, et rien n'a été touché (#3563)")
    void place_insuffisante_pour_une_seule_nuit_refuse_sans_rien_toucher() throws IOException {
        Path nuit = seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        Files.writeString(nuit.resolve("transformes").resolve("seq.wav"), "modifié depuis la sauvegarde");
        ServiceSauvegarde sansPlace = avecEspace(0);

        assertThatThrownBy(() -> sansPlace.restaurerComplet(backup))
                .isInstanceOf(RefusAvantEcriture.class)
                .hasMessageContaining("Libérez")
                .hasMessageContaining("Rien n'a été touché");

        assertThat(nuit.resolve("transformes").resolve("seq.wav"))
                .as("le refus doit précéder toute écriture : l'audio local est celui d'avant")
                .hasContent("modifié depuis la sauvegarde");
        assertThat(temporairesResiduels())
                .as("et aucune zone temporaire n'a été ouverte")
                .isEmpty();
    }

    @Test
    @DisplayName("dégradé, une panne après la première bascule n'est plus un refus (#3563)")
    void en_degrade_une_panne_apres_la_premiere_bascule_devient_un_incident() throws IOException {
        seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        Path seconde = seederNuit(disque.resolve("Nuit-02"), "second audio");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        // Un intrus dans la destination de la seconde nuit : la garde de #3514 refuse d'écraser ce que
        // la sauvegarde ne contient pas. Le refus tombera donc APRÈS la bascule de la première.
        Files.writeString(seconde.resolve("transformes").resolve("intrus.wav"), "à ne pas effacer");
        ServiceSauvegarde serre = avecEspace(octetsDuManifeste(backup) - 1);

        assertThatThrownBy(() -> serre.restaurerComplet(backup))
                .as("« rien n'a été touché » serait faux : une nuit est déjà en place, et un script qui"
                        + " lirait un refus conclurait à un état intact")
                .isInstanceOf(DataAccessException.class)
                .isNotInstanceOf(RefusAvantEcriture.class)
                .hasMessageContaining("1 nuit(s) en place");
    }

    /// Un `ServiceSauvegarde` qui croit le disque à `octetsLibres`, quel que soit le dossier.
    private ServiceSauvegarde avecEspace(long octetsLibres) {
        return new ServiceSauvegarde(
                source, new HorlogeFigee(LocalDateTime.of(2026, 8, 2, 10, 0)), dossier -> octetsLibres);
    }

    private static long octetsDuManifeste(Path backup) {
        return ManifesteSauvegardeJson.lire(backup).orElseThrow().racines().stream()
                .mapToLong(RacineSauvegardee::octets)
                .sum();
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
        assertThat(bilan.absentesDeLaSauvegarde())
                .as("la nuit vient d'être restaurée : l'annoncer absente serait une fausse alerte, et"
                        + " c'est ce qui arrivait tant que l'inventaire se faisait APRÈS la réécriture")
                .isEmpty();
    }

    @Test
    @DisplayName("TOUS les chemins de la session suivent le dossier, pas seulement sa racine")
    void tous_les_chemins_suivent_le_dossier() throws IOException {
        Path nuit = seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        // Ce que la base retient d'une nuit, au-delà de root_path : chaque séquence, chaque original,
        // le journal du capteur. Tous en ABSOLU. Plus un original resté sur la carte SD, hors de la
        // racine de la session : celui-là ne doit surtout pas bouger.
        long idSession =
                seederFichiersDeSession(nuit, disque.resolve("carte-sd").resolve("brut.wav"));
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        supprimerRecursif(disque.resolve("Nuit-01"));

        service.restaurerComplet(backup);

        Path repli = workspaceDir.resolve("Nuit-01");
        assertThat(cheminsDe("listening_sequence", idSession))
                .as("une séquence dont le chemin reste sur la machine d'origine est un fichier"
                        + " introuvable, et l'application déclare la nuit perdue")
                .containsExactly(repli.resolve("transformes").resolve("seq.wav").toString());
        assertThat(cheminsDe("original_recording", idSession))
                .as("l'original copié sous la session suit, celui resté sur la carte SD ne bouge pas :"
                        + " le rebaser désignerait un fichier qui n'a jamais été là")
                .containsExactlyInAnyOrder(
                        repli.resolve("bruts").resolve("PaRec.wav").toString(),
                        disque.resolve("carte-sd").resolve("brut.wav").toString());
        assertThat(cheminsDe("sensor_log", idSession))
                .containsExactly(repli.resolve("LogPR.txt").toString());
        assertThat(cheminsParPassage())
                .as("le CSV Tadarida se retrouve par le PASSAGE et non par la session : c'est la seule"
                        + " des six tables à chemin dans ce cas, donc celle qu'on oublie")
                .containsExactly(repli.resolve("resultats.csv").toString());
    }

    @Test
    @DisplayName("un fichier que la sauvegarde n'a pas fait refuser AVANT toute écriture, et il survit")
    void destination_portant_un_fichier_en_trop_refusee_avant_ecriture() throws IOException {
        seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        Path repli = workspaceDir.resolve("Nuit-01");
        Files.createDirectories(repli.resolve("transformes"));
        Files.writeString(repli.resolve("transformes").resolve("intrus.wav"), "fichier en trop");
        supprimerRecursif(disque.resolve("Nuit-01"));

        // Avant #3514, la copie s'écrasait par-dessus : l'intrus survivait, l'inventaire ne
        // correspondait plus, et la restauration échouait APRÈS avoir remplacé la base. La bascule
        // remplace la destination entière, donc l'intrus disparaîtrait : on refuse avant de basculer
        // plutôt que d'effacer en silence un fichier que l'utilisateur avait posé là.
        assertThatThrownBy(() -> service.restaurerComplet(backup))
                .as("un refus, pas une panne : l'état local est intact et la CLI peut en tirer un code 2")
                .isInstanceOf(RefusAvantEcriture.class)
                .hasMessageContaining("intrus.wav")
                .hasMessageContaining("Rien n'a été touché");

        assertThat(repli.resolve("transformes").resolve("intrus.wav"))
                .as("restaurer ne doit pas devenir un moyen détourné d'effacer ce qui traînait là")
                .exists();
        assertThat(racinesEnBase())
                .as("et la base n'a pas bougé")
                .containsExactly(disque.resolve("Nuit-01").toString());
        assertThat(temporairesResiduels()).isEmpty();
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

    @Test
    @DisplayName("un fichier posé là où la nuit doit revenir fait refuser, il n'est pas effacé")
    void un_fichier_occupant_la_destination_fait_refuser() throws IOException {
        seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        supprimerRecursif(disque);
        Files.createDirectories(workspaceDir);
        // La bascule remplace la destination : sans refus, ce fichier disparaîtrait pour laisser la
        // place au dossier. C'est la même perte qu'un fichier en trop DANS la destination.
        Files.writeString(workspaceDir.resolve("Nuit-01"), "un fichier de l'utilisateur, pas une nuit");

        assertThatThrownBy(() -> service.restaurerComplet(backup))
                .isInstanceOf(RefusAvantEcriture.class)
                .hasMessageContaining("est un fichier")
                .hasMessageContaining("Rien n'a été touché");

        assertThat(workspaceDir.resolve("Nuit-01")).hasContent("un fichier de l'utilisateur, pas une nuit");
        assertThat(temporairesResiduels()).isEmpty();
    }

    @Test
    @DisplayName("un temporaire abandonné par une tentative précédente ne contamine pas la suivante")
    void un_temporaire_perime_ne_contamine_pas_la_restauration() throws IOException {
        seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        supprimerRecursif(disque);
        // Ce qu'une machine éteinte en pleine bascule laisse derrière elle : un `.en-cours` incomplet,
        // et de surcroît porteur d'un fichier que la sauvegarde n'a pas.
        Path perime = workspaceDir.resolve("Nuit-01.en-cours");
        Files.createDirectories(perime.resolve("transformes"));
        Files.writeString(perime.resolve("transformes").resolve("reste.wav"), "reste d'une tentative morte");

        service.restaurerComplet(backup);

        Path repli = workspaceDir.resolve("Nuit-01");
        assertThat(repli.resolve("transformes").resolve("reste.wav"))
                .as("sans vidage préalable du temporaire, le reste passait dans la destination : la copie"
                        + " écrase les fichiers homonymes, elle ne retire pas les surnuméraires")
                .doesNotExist();
        assertThat(repli.resolve("transformes").resolve("seq.wav")).hasContent("audio d'origine");
        assertThat(temporairesResiduels()).isEmpty();
    }

    @Test
    @DisplayName("un dossier vide de plus dans la destination n'est pas un motif de refus")
    void un_dossier_vide_en_trop_ne_fait_pas_refuser() throws IOException {
        seederNuit(disque.resolve("Nuit-01"), "audio d'origine");
        Path backup = service.sauvegarderComplet(racine.resolve("sauvegardes")).dossier();
        supprimerRecursif(disque);
        // Un dossier vide n'emporte aucune donnée : refuser sur lui ferait échouer des restaurations
        // légitimes pour rien.
        Files.createDirectories(workspaceDir.resolve("Nuit-01").resolve("dossier-vide"));

        service.restaurerComplet(backup);

        assertThat(workspaceDir.resolve("Nuit-01").resolve("transformes").resolve("seq.wav"))
                .hasContent("audio d'origine");
    }

    /// Les dossiers de bascule laissés derrière : ils vivent à côté de leur destination, dans le
    /// workspace, et aucun ne doit survivre à un échec.
    private List<Path> temporairesResiduels() throws IOException {
        return temporairesResiduelsSous(workspaceDir);
    }

    private static List<Path> temporairesResiduelsSous(Path dossier) throws IOException {
        if (!Files.isDirectory(dossier)) {
            return List.of();
        }
        try (Stream<Path> contenu = Files.list(dossier)) {
            return contenu.filter(chemin -> chemin.getFileName().toString().contains(".en-cours"))
                    .toList();
        }
    }

    private Path seederNuit(Path racineSession, String contenu) throws IOException {
        Files.createDirectories(racineSession.resolve("transformes"));
        Files.writeString(racineSession.resolve("transformes").resolve("seq.wav"), contenu);
        declarerSession(racineSession);
        return racineSession;
    }

    /// Sème dans la session les fichiers que la base retient **en plus** de sa racine : une séquence,
    /// un original copié sous la session, un original resté **hors** de la session (import sans
    /// copie), et le journal du capteur. Renvoie l'identifiant de la session.
    private long seederFichiersDeSession(Path nuit, Path brutHorsSession) throws IOException {
        Files.createDirectories(nuit.resolve("bruts"));
        Files.writeString(nuit.resolve("bruts").resolve("PaRec.wav"), "original");
        Files.writeString(nuit.resolve("LogPR.txt"), "journal");
        Files.createDirectories(brutHorsSession.getParent());
        Files.writeString(brutHorsSession, "resté sur la carte");
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement()) {
            // Aucun passage réel derrière ces lignes : seuls les chemins nous intéressent ici.
            st.execute("PRAGMA foreign_keys = OFF");
            long idSession = identifiantSession(st, nuit);
            st.execute("INSERT INTO original_recording(session_id, file_path, file_name) VALUES (" + idSession + ", '"
                    + echapper(nuit.resolve("bruts").resolve("PaRec.wav")) + "', 'PaRec.wav')");
            long idOriginal = derniereCle(st);
            st.execute("INSERT INTO original_recording(session_id, file_path, file_name) VALUES (" + idSession + ", '"
                    + echapper(brutHorsSession) + "', 'brut.wav')");
            st.execute("INSERT INTO listening_sequence(session_id, original_recording_id, file_path, file_name)"
                    + " VALUES (" + idSession + ", " + idOriginal + ", '"
                    + echapper(nuit.resolve("transformes").resolve("seq.wav")) + "', 'seq.wav')");
            st.execute("INSERT INTO sensor_log(session_id, file_path) VALUES (" + idSession + ", '"
                    + echapper(nuit.resolve("LogPR.txt")) + "')");
            // Le CSV Tadarida est rattaché au PASSAGE, pas à la session : c'est la seule des six
            // tables à chemin qui se retrouve par une autre clé, et donc celle qu'on oublie.
            st.execute("INSERT INTO identification_results(passage_id, file_path, detected_format,"
                    + " imported_at) VALUES (" + passageSuivant + ", '"
                    + echapper(nuit.resolve("resultats.csv")) + "', 'Tadarida', '2026-08-03')");
            return idSession;
        } catch (SQLException echec) {
            throw new IOException(echec);
        }
    }

    private static long derniereCle(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long identifiantSession(Statement st, Path nuit) throws SQLException {
        try (ResultSet rs =
                st.executeQuery("SELECT id FROM recording_session WHERE root_path = '" + echapper(nuit) + "'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private List<String> cheminsDe(String table, long idSession) {
        List<String> chemins = new ArrayList<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("SELECT file_path FROM " + table + " WHERE session_id = " + idSession)) {
            while (rs.next()) {
                chemins.add(rs.getString(1));
            }
        } catch (SQLException echec) {
            throw new IllegalStateException("Lecture de " + table + " impossible", echec);
        }
        return chemins;
    }

    private List<String> cheminsParPassage() {
        List<String> chemins = new ArrayList<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("SELECT file_path FROM identification_results")) {
            while (rs.next()) {
                chemins.add(rs.getString(1));
            }
        } catch (SQLException echec) {
            throw new IllegalStateException("Lecture des résultats d'identification impossible", echec);
        }
        return chemins;
    }

    private static String echapper(Path chemin) {
        return chemin.toString().replace("'", "''");
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
