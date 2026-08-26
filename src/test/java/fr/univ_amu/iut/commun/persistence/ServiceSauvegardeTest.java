package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.TailleFichier;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Sauvegarde et restauration de la base (#148).
class ServiceSauvegardeTest {

    @TempDir
    Path workspaceDir;

    /// Le nom que porte une sauvegarde EN COURS de constitution, avant son renommage final.
    private static final String NOM_EN_CHANTIER = "vigiechiro-sauvegarde-complete-20260707-143015";

    private SourceDeDonnees source;
    private UtilisateurDao utilisateurDao;
    private ServiceSauvegarde service;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(workspaceDir));
        new MigrationSchema(source).migrer();
        utilisateurDao = new UtilisateurDao(source);
        // Horloge figée → horodatage déterministe dans le nom de fichier.
        service = new ServiceSauvegarde(source, new HorlogeFigee(LocalDateTime.of(2026, 7, 7, 14, 30, 15)), () -> {});
    }

    @Test
    @DisplayName("#3537 : restaurer annonce la mutation, depuis le service et non depuis la vue")
    void restaurer_annonce() throws Exception {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(workspaceDir));
        new MigrationSchema(source).migrer();
        int[] annonces = {0};
        ServiceSauvegarde service = new ServiceSauvegarde(
                source, new HorlogeFigee(LocalDateTime.of(2026, 5, 31, 12, 0)), () -> annonces[0]++);
        Path sauvegarde = service.sauvegarder(workspaceDir.resolve("sauvegardes"));

        service.restaurer(sauvegarde);

        // L'annonce vivait dans PorteurSauvegarde, une classe de VUE : la commande CLI `restaurer`
        // remplaçait donc la base entière sans rien annoncer (passe 2 de la clôture du lot 1).
        assertThat(annonces[0]).isEqualTo(1);
    }

    @Test
    @DisplayName("La sauvegarde crée un fichier horodaté dans le dossier choisi")
    void sauvegarde_cree_un_fichier_horodate() {
        utilisateurDao.insert(new Utilisateur("u1", "Alice"));
        Path dossier = workspaceDir.resolve("mes-sauvegardes");

        Path fichier = service.sauvegarder(dossier);

        assertThat(fichier).exists().hasParent(dossier).hasFileName("vigiechiro-sauvegarde-20260707-143015.db");
    }

    @Test
    @DisplayName("Restaurer revient à l'état de la sauvegarde et met de côté la base courante")
    void restauration_revient_a_l_etat_sauvegarde() {
        utilisateurDao.insert(new Utilisateur("u1", "Alice"));
        Path sauvegarde = service.sauvegarder(workspaceDir.resolve("mes-sauvegardes"));

        // Modification APRÈS la sauvegarde : un second utilisateur.
        utilisateurDao.insert(new Utilisateur("u2", "Bob"));
        assertThat(utilisateurDao.findAll()).hasSize(2);

        service.restaurer(sauvegarde);

        // Retour à l'état sauvegardé : seul u1 subsiste (u2 ajouté après est perdu).
        assertThat(utilisateurDao.findAll()).extracting(Utilisateur::localId).containsExactly("u1");
        // Filet de sécurité : l'état courant a été mis de côté avant écrasement.
        assertThat(workspaceDir.resolve(Workspace.FICHIER_BASE + ".avant-restauration"))
                .exists();
    }

    @Test
    @DisplayName("Restaurer depuis un fichier qui n'est pas une base est refusé")
    void restauration_fichier_invalide_rejetee() throws IOException {
        utilisateurDao.insert(new Utilisateur("u1", "Alice"));
        Path faux = Files.writeString(workspaceDir.resolve("faux.db"), "ceci n'est pas une base SQLite");

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> service.restaurer(faux))
                // Le refus doit venir de la VÉRIFICATION, avant tout remplacement. Sans cette
                // précision, un échec plus tardif (migration impossible sur un fichier illisible,
                // rattrapé par le retour arrière de #2730) passerait pour le même comportement.
                .withMessageContaining("Fichier de sauvegarde illisible");
        assertThat(utilisateurDao.findAll())
                .as("la base courante n'a même pas été touchée")
                .hasSize(1);
        assertThat(workspaceDir.resolve(Workspace.FICHIER_BASE + ".avant-restauration"))
                .as("et le filet n'a pas eu à être posé")
                .doesNotExist();
    }

    @Test
    @DisplayName("Restaurer depuis un fichier absent est refusé")
    void restauration_fichier_absent_rejetee() {
        assertThatThrownBy(() -> service.restaurer(workspaceDir.resolve("absent.db")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Le dossier de sauvegarde par défaut est <workspace>/sauvegardes")
    void dossier_par_defaut() {
        assertThat(service.dossierParDefaut()).isEqualTo(workspaceDir.resolve("sauvegardes"));
    }

    @Test
    @DisplayName("Place insuffisante : refus chiffré AVANT d'écrire le moindre octet (#3572)")
    void place_insuffisante_refuse_avant_d_ecrire() throws IOException {
        seederSession("Car040962-2026-Pass1-A1");
        Path destination = workspaceDir.resolve("cle-usb");
        Files.createDirectories(destination);
        ServiceSauvegarde sansPlace = avecEspace(0);

        assertThatThrownBy(() -> sansPlace.sauvegarderComplet(destination))
                .isInstanceOf(RefusAvantEcriture.class)
                .as("« espace insuffisant » n'aide personne : il faut savoir combien libérer")
                .hasMessageContaining("Libérez")
                .hasMessageContaining("Rien n'a été touché");

        try (var contenu = Files.list(destination)) {
            assertThat(contenu)
                    .as("un garde qui refuse APRÈS avoir créé le dossier n'est pas un garde, c'est un"
                            + " message : rien ne doit subsister")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("#3627 : un fichier illisible ne se compte pas pour zéro, il fait refuser")
    void fichier_illisible_fait_refuser_plutot_que_conclure() throws IOException {
        Path racine = seederSession("Car040962-2026-Pass1-A1");
        Path opaque = racine.resolve("transformes").resolve("seq.wav");
        Path destination = workspaceDir.resolve("cle-usb");
        Files.createDirectories(destination);
        // La place est LARGEMENT suffisante pour ce que la pesée sait compter. C'est tout le piège :
        // le fichier illisible pesait zéro, le total tenait, et la copie partait pour échouer plus loin.
        ServiceSauvegarde avecUnTrou = avecEspaceEtPesee(Long.MAX_VALUE, opaque);

        assertThatThrownBy(() -> avecUnTrou.sauvegarderComplet(destination))
                .isInstanceOf(RefusAvantEcriture.class)
                .as("un refus qui ne dit pas quel fichier débloquer est un mur")
                .hasMessageContaining(opaque.toString())
                .hasMessageContaining("Permission non accordée")
                .hasMessageContaining("Rien n'a été touché");

        try (var contenu = Files.list(destination)) {
            assertThat(contenu)
                    .as("le refus vient AVANT la première copie, sinon il ne protège de rien")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("#3634 : un dossier de nuit illisible est un REFUS, pas un incident")
    void dossier_illisible_est_un_refus_et_non_un_incident() throws IOException {
        Path racineSession = seederSession("Car040962-2026-Pass1-A1");
        Path interdit = Files.createDirectories(racineSession.resolve("cache"));
        Files.writeString(interdit.resolve("dedans.wav"), "hors de portée");
        Path destination = workspaceDir.resolve("cle-usb");
        Files.createDirectories(destination);

        // L'illisibilité est FABRIQUÉE : `File.setReadable(false)` rend `false` sous Windows, et ce
        // test y échouait avant d'éprouver quoi que ce soit (#3526). Le geste `lister` est celui qui
        // échoue quand un dossier ne se laisse pas ouvrir - à l'ouverture, pas pendant l'itération.
        GestesFichiers dossierFerme = new GestesFichiers() {
            @Override
            public java.util.stream.Stream<Path> lister(Path aLister) throws IOException {
                if (aLister.equals(interdit)) {
                    throw new java.nio.file.AccessDeniedException(aLister.toString());
                }
                return Files.list(aLister);
            }
        };

        {
            Throwable echec = catchThrowable(
                    () -> avecEspaceEtGestes(Long.MAX_VALUE, dossierFerme).sauvegarderComplet(destination));

            // Le coeur de #3634 : `UncheckedIOException` n'est PAS une `IOException`. Elle traversait le
            // `catch (IOException | SQLException)` et `VerdictCli` la rangeait dans sa branche par
            // défaut : « Échec », code 1, état incertain. Or le garde tourne AVANT la première copie.
            // La chaîne jusqu'au code de sortie 2 se referme dans `VerdictCliTest`, qui fige déjà
            // « RefusAvantEcriture → REFUS ». `VerdictCli` est de portée paquet : la rappeler d'ici
            // demanderait de l'ouvrir, pour prouver une seconde fois ce qui l'est déjà.
            assertThat(echec)
                    .as("« état incertain » sur une opération qui n'a rien touché envoie l'utilisateur"
                            + " vérifier des données intactes")
                    .isInstanceOf(RefusAvantEcriture.class)
                    .hasMessageContaining(interdit.toString());
        }
    }

    @Test
    @DisplayName("Interrompue, elle ne porte pas le nom d'une sauvegarde complète (#3572)")
    void interrompue_elle_ne_se_fait_pas_passer_pour_complete() throws IOException {
        seederSession("Car040962-2026-Pass1-A1");
        Path destination = workspaceDir.resolve("sauvegardes");

        Path aboutie = service.sauvegarderComplet(destination).dossier();

        assertThat(aboutie.getFileName().toString())
                .as("une sauvegarde qui aboutit porte son nom")
                .startsWith("vigiechiro-sauvegarde-complete-")
                .doesNotStartWith(ServiceSauvegarde.PREFIXE_EN_CHANTIER);
        try (var contenu = Files.list(destination)) {
            assertThat(contenu.map(chemin -> chemin.getFileName().toString()))
                    .as("et rien ne reste sous le nom de chantier")
                    .noneMatch(nom -> nom.startsWith(ServiceSauvegarde.PREFIXE_EN_CHANTIER));
        }

        // Ce qu'une interruption laisse : un dossier jamais renommé. C'est le contrat entre le service
        // qui écrit et l'inventaire qui lit, et c'est lui qui manquait - `natureDe` classe sur le
        // PRÉFIXE, donc un marqueur en suffixe aurait laissé le dossier passer pour complet.
        Files.createDirectories(destination.resolve(
                ServiceSauvegarde.PREFIXE_EN_CHANTIER + "vigiechiro-sauvegarde-complete-20260707-150000"));

        assertThat(InventaireSauvegardes.lire(destination))
                .as("sans quoi une sauvegarde tronquée se restaure en mode dégradé - le manifeste, écrit"
                        + " en dernier, y est absent, ce qui veut dire « ancien format » ailleurs")
                .hasSize(1)
                .allMatch(entree -> entree.nom().equals(aboutie.getFileName().toString()));
    }

    @Test
    @DisplayName("Sauvegarde complète : base + dossiers de session copiés dans un dossier horodaté")
    void sauvegarde_complete_copie_base_et_dossiers() throws IOException {
        utilisateurDao.insert(new Utilisateur("u1", "Alice"));
        seederSession("Car040962-2026-Pass1-A1");
        Path destination = workspaceDir.resolve("mes-sauvegardes");

        BilanSauvegarde bilan = service.sauvegarderComplet(destination);

        assertThat(bilan.dossier()).isDirectory().hasParent(destination);
        assertThat(bilan.dossier().resolve("base").resolve("vigiechiro.db")).isRegularFile();
        // Le dossier porte le nom d'origine suivi d'un condensé du chemin (#2726) : c'est le manifeste
        // qui dit lequel, et c'est lui qu'on interroge plutôt que de recopier le condensé en dur.
        RacineSauvegardee emportee = ManifesteSauvegardeJson.lire(bilan.dossier())
                .orElseThrow()
                .racines()
                .getFirst();
        assertThat(bilan.dossier()
                        .resolve("sessions")
                        .resolve(emportee.identifiant())
                        .resolve("transformes")
                        .resolve("seq.wav"))
                .exists();
        assertThat(emportee.identifiant()).startsWith("Car040962-2026-Pass1-A1-");
        assertThat(bilan.sessionsCopiees()).isEqualTo(1);
        assertThat(bilan.incomplete()).isFalse();
    }

    @Test
    @DisplayName("#1346 : une racine de session non montée (carte SD) est SIGNALÉE, pas sautée en silence")
    void sauvegarde_complete_signale_les_racines_inaccessibles() throws IOException {
        utilisateurDao.insert(new Utilisateur("u1", "Alice"));
        seederSession("Car040962-2026-Pass1-A1");
        // Une session dont la racine n'existe pas sur le disque : carte SD retirée, disque externe débranché.
        Path absente = workspaceDir.resolve("Car040962-2026-Pass2-B2");
        declarerSession(absente, 2);

        BilanSauvegarde bilan = service.sauvegarderComplet(workspaceDir.resolve("mes-sauvegardes"));

        assertThat(bilan.sessionsCopiees())
                .as("ce qui était là a bien été copié : une racine absente ne fait pas échouer la sauvegarde")
                .isEqualTo(1);
        assertThat(bilan.incomplete())
                .as("mais la sauvegarde N'EST PAS complète, et c'est tout ce qui compte avant un reset (#1151)")
                .isTrue();
        assertThat(bilan.racinesInaccessibles())
                .singleElement()
                .satisfies(racine -> assertThat(racine).contains("Car040962-2026-Pass2-B2"));
        assertThat(bilan.enClair())
                .as("le bilan se lit tel quel, en IHM comme en CLI")
                .contains("1 dossier(s) de session copié(s)")
                .contains("1 inaccessible(s)");
    }

    @Test
    @DisplayName("Restauration complète : base et dossiers de session remis en l'état sauvegardé")
    void restauration_complete_remet_base_et_dossiers() throws IOException {
        utilisateurDao.insert(new Utilisateur("u1", "Alice"));
        Path racineSession = seederSession("Car040962-2026-Pass1-A1");
        Path backup = service.sauvegarderComplet(workspaceDir.resolve("mes-sauvegardes"))
                .dossier();

        // Altérations APRÈS la sauvegarde : un second utilisateur en base et le fichier de session supprimé.
        utilisateurDao.insert(new Utilisateur("u2", "Bob"));
        Files.delete(racineSession.resolve("transformes").resolve("seq.wav"));

        service.restaurerComplet(backup);

        assertThat(utilisateurDao.findAll()).extracting(Utilisateur::localId).containsExactly("u1");
        assertThat(racineSession.resolve("transformes").resolve("seq.wav")).exists();
    }

    /// Un service qui croit le disque à `octetsLibres`, quel que soit le dossier.
    /// Un service dont la pesée bute sur `interdit`, et lit normalement tout le reste.
    /// Un service dont le DOSSIER `interdit` ne se laisse pas lister (#3526).
    private ServiceSauvegarde avecEspaceEtGestes(long octetsLibres, GestesFichiers gestes) {
        return new ServiceSauvegarde(
                source,
                new HorlogeFigee(LocalDateTime.of(2026, 7, 7, 14, 30, 15)),
                dossier -> octetsLibres,
                TailleFichier.reelle(),
                gestes,
                () -> {});
    }

    private ServiceSauvegarde avecEspaceEtPesee(long octetsLibres, Path interdit) {
        TailleFichier pesee = fichier -> {
            if (fichier.equals(interdit)) {
                throw new IOException("Permission non accordée");
            }
            return Files.size(fichier);
        };
        return new ServiceSauvegarde(
                source,
                new HorlogeFigee(LocalDateTime.of(2026, 7, 7, 14, 30, 15)),
                dossier -> octetsLibres,
                pesee,
                GestesFichiers.reels(),
                () -> {});
    }

    private ServiceSauvegarde avecEspace(long octetsLibres) {
        return new ServiceSauvegarde(
                source, new HorlogeFigee(LocalDateTime.of(2026, 7, 7, 14, 30, 15)), dossier -> octetsLibres, () -> {});
    }

    /// Crée `<workspace>/<nom>/transformes/seq.wav` et déclare la session en base.
    private Path seederSession(String nom) throws IOException {
        Path racine = workspaceDir.resolve(nom);
        Files.createDirectories(racine.resolve("transformes"));
        Files.writeString(racine.resolve("transformes").resolve("seq.wav"), "audio");
        declarerSession(racine, 1);
        return racine;
    }

    /// Déclare une ligne `recording_session` pointant sur `racine`, **sans rien créer sur le disque** (FK
    /// désactivées : seul `root_path` importe pour la sauvegarde complète). Sert à simuler une racine
    /// inaccessible : une carte SD retirée laisse exactement cette trace en base.
    private void declarerSession(Path racine, int idPassage) throws IOException {
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                PreparedStatement insertion =
                        cx.prepareStatement("INSERT INTO recording_session(root_path, originals_total_bytes,"
                                + " sequences_total_bytes, passage_id) VALUES (?, 0, 0, ?)")) {
            st.execute("PRAGMA foreign_keys = OFF");
            insertion.setString(1, racine.toString());
            insertion.setInt(2, idPassage);
            insertion.executeUpdate();
        } catch (SQLException echec) {
            throw new IOException(echec);
        }
    }
}
