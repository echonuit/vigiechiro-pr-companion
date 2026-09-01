package fr.univ_amu.iut.audit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.lot.model.BilanVerification;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.VerificationDepot;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.EmpreinteContenu;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Audit de cohérence disque / base sur une base SQLite jetable et de **vrais fichiers** sous
/// `@TempDir`. Les `file_path` sont stockés en **absolu** (comme en production : `Path.of(cheminFichier())`
/// est résolu tel quel). Chaque test construit son propre graphe site -> point -> passage -> session.
class ServiceAuditCoherenceTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");

    @TempDir
    Path dossier;

    private Path racineSession;
    private SourceDeDonnees source;
    private ServiceAuditCoherence service;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private JournalDuCapteurDao journalDao;
    private ReleveClimatiqueDao releveDao;
    private DepotUniteDao depotDao;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        // La topologie naît du premier `creerPassage`, par trouver-ou-créer.
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);

        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        journalDao = new JournalDuCapteurDao(source);
        releveDao = new ReleveClimatiqueDao(source);
        depotDao = new DepotUniteDao(source);

        racineSession = dossier.resolve(PREFIXE.nomDossierSession());
        service = new ServiceAuditCoherence(source, new Workspace(dossier), Optional.empty(), Optional.empty());
    }

    @Test
    @DisplayName("Audit en ligne : le bilan VerificationDepot est mappé (manquantes -> SERVEUR_MANQUANT)")
    void audit_en_ligne_mappe_le_bilan() {
        Long idPassage = creerPassage(1);
        VerificationDepot moteur = mock(VerificationDepot.class);
        when(moteur.verifier(idPassage))
                .thenReturn(new BilanVerification("part-1", true, 3, List.of("a.wav"), List.of("b.zip", "c.zip")));
        ServiceAuditCoherence enLigne =
                new ServiceAuditCoherence(source, new Workspace(dossier), Optional.of(moteur), Optional.empty());

        List<ConstatAudit> constats = enLigne.auditerEnLigne().constats();

        assertThat(constats)
                .extracting(ConstatAudit::categorie)
                .containsExactly(CategorieConstat.SERVEUR_MANQUANT, CategorieConstat.SERVEUR_MANQUANT);
        assertThat(constats).extracting(ConstatAudit::cible).containsExactly("b.zip", "c.zip");
    }

    @Test
    @DisplayName("Audit en ligne : les constats des points serveur (AuditPointsServeur) sont inclus")
    void audit_en_ligne_inclut_les_points() {
        AuditPointsServeur points = mock(AuditPointsServeur.class);
        when(points.auditer())
                .thenReturn(List.of(new ConstatAudit(
                        Severite.AVERTISSEMENT,
                        CategorieConstat.POINT_DIVERGENT,
                        null,
                        "040962 / A1",
                        "Position différente du serveur.")));
        ServiceAuditCoherence avecPoints =
                new ServiceAuditCoherence(source, new Workspace(dossier), Optional.empty(), Optional.of(points));

        List<ConstatAudit> constats = avecPoints.auditerEnLigne().constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.categorie()).isEqualTo(CategorieConstat.POINT_DIVERGENT);
            assertThat(c.cible()).isEqualTo("040962 / A1");
        });
    }

    @Test
    @DisplayName("Audit en ligne indisponible (Optional vide) : un seul constat INFO SERVEUR_INJOIGNABLE")
    void audit_en_ligne_indisponible() {
        List<ConstatAudit> constats = service.auditerEnLigne().constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(Severite.INFO);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.SERVEUR_INJOIGNABLE);
        });
    }

    @Test
    @DisplayName("Session entièrement cohérente : rapport sain, aucun écart")
    void session_coherente_rapport_sain() throws IOException {
        Long idPassage = creerSessionCoherente(1);

        assertThat(service.auditerPassage(idPassage).sain()).isTrue();
    }

    @Test
    @DisplayName("ADR 0048 : séquence manquante = disponibilité PARTIELLE observée (INFO), pas une erreur")
    void sequence_manquante_est_partielle_observee() throws IOException {
        Long idPassage = creerSessionCoherente(1);
        Files.delete(racineSession.resolve("transformes").resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, 0)));

        RapportAudit rapport = service.auditerPassage(idPassage);

        assertThat(rapport.aDesErreurs())
                .as("une séquence absente n'est pas une corruption : l'utilisateur possède ses fichiers")
                .isFalse();
        assertThat(rapport.constats()).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(Severite.INFO);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.AUDIO_INDISPONIBLE);
            assertThat(c.detail()).contains("PARTIELLE").contains("1/2 séquence(s)");
        });
    }

    @Test
    @DisplayName("ADR 0048 : audio absent SANS marqueur d'archivage = un seul INFO observé, zéro erreur")
    void audio_absent_observe_sans_marqueur_est_un_info() throws IOException {
        Long idPassage = creerSessionCoherente(1);
        // Les séquences sont supprimées, mais AUCUN marqueur n'est posé : dans le modèle « observé »
        // (ADR 0048), l'absence de l'audio est un ÉTAT (l'utilisateur possède ses fichiers), pas une
        // corruption. Les bruts restent en place : seule la disponibilité des séquences est en jeu.
        supprimerSequences();

        RapportAudit rapport = service.auditerPassage(idPassage);

        assertThat(rapport.aDesErreurs())
                .as("audio absent observé n'est pas une corruption : zéro erreur")
                .isFalse();
        assertThat(rapport.constats()).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(Severite.INFO);
            assertThat(c.detail())
                    .as("le constat porte la disponibilité observée et le décompte")
                    .contains("ABSENTE")
                    .contains("0/2 séquence(s)");
        });
    }

    @Test
    @DisplayName("#1719 : squelette synchronisé (0 séquence) est bénin : aucun constat d'audio, zéro erreur")
    void passage_squelette_non_hydrate_est_benin() {
        // Ce que la synchro « mes sites » (#1707) crée : un passage rattaché, SANS séquence ni résultat
        // (« nuit connue, pas encore importée »). Sans séquence, il n'y a pas d'audio à décrire (ADR 0048) :
        // le squelette est bénin par observation, sans qu'aucun marqueur ne soit nécessaire.
        Long idPassage = creerPassage(1);
        sessionDao.insert(new SessionDEnregistrement(null, racineSession.toString(), 0L, 0L, idPassage));

        RapportAudit rapport = service.auditerPassage(idPassage);

        assertThat(rapport.aDesErreurs())
                .as("un squelette (synchronisé, pas encore importé) n'est pas corrompu")
                .isFalse();
        assertThat(rapport.constats())
                .as("sans séquence, aucun constat de disponibilité audio n'est émis (ADR 0048)")
                .noneMatch(c -> c.categorie() == CategorieConstat.AUDIO_INDISPONIBLE);
    }

    @Test
    @DisplayName("ADR 0048 : l'audio absent n'exempte que lui : un journal manquant reste une erreur")
    void audio_absent_journal_manquant_reste_une_erreur() throws IOException {
        Long idPassage = creerSessionCoherente(1);
        supprimerSequences();
        Files.delete(racineSession.resolve("LogPR" + SERIE + ".txt"));

        RapportAudit rapport = service.auditerPassage(idPassage);

        assertThat(rapport.aDesErreurs())
                .as("le journal ne suit pas le sort de l'audio : son absence est un vrai problème")
                .isTrue();
        assertThat(rapport.constats())
                .extracting(ConstatAudit::categorie)
                .containsExactlyInAnyOrder(CategorieConstat.AUDIO_INDISPONIBLE, CategorieConstat.DISQUE_MANQUANT);
    }

    /// Supprime les 2 séquences d'écoute de la session cohérente, en laissant les bruts : l'audio
    /// devient observé ABSENT sans toucher au chemin des originaux (encore gouverné par #1303).
    private void supprimerSequences() throws IOException {
        Files.delete(racineSession.resolve("transformes").resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, 0)));
        Files.delete(racineSession.resolve("transformes").resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, 1)));
    }

    @Test
    @DisplayName("Fichier parasite dans transformes/ : orphelin ; rien sous depot/")
    void fichier_parasite_orphelin_mais_pas_depot() throws IOException {
        Long idPassage = creerSessionCoherente(1);
        Files.write(racineSession.resolve("transformes").resolve("intrus.wav"), new byte[8]);
        Files.createDirectories(racineSession.resolve("depot"));
        Files.write(racineSession.resolve("depot").resolve("Car040962-2026-Pass1-A1-1.zip"), new byte[8]);

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(Severite.AVERTISSEMENT);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.DISQUE_ORPHELIN);
            assertThat(c.cible()).endsWith("intrus.wav");
        });
    }

    @Test
    @DisplayName("Nom de fichier au mauvais préfixe : PREFIXE_NON_CONFORME")
    void prefixe_non_conforme() throws IOException {
        Long idPassage = creerPassage(1);
        Long idSession = creerSession(idPassage, 4096L);
        Path bruts = Files.createDirectories(racineSession.resolve("bruts"));
        Path fichier = Files.write(bruts.resolve("MAUVAIS_NOM.wav"), new byte[16]);
        originalDao.insert(new EnregistrementOriginal(
                null, "MAUVAIS_NOM.wav", fichier.toString(), 12.0, 384_000, null, idSession));

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(Severite.ERREUR);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.PREFIXE_NON_CONFORME);
        });
    }

    @Test
    @DisplayName("#3490 : plusieurs fichiers mal préfixés donnent UN constat chiffré, pas un par fichier")
    void prefixe_non_conforme_agrege_par_passage() throws IOException {
        // Mesuré sur un workspace de recette réel : 4236 constats PREFIXE_NON_CONFORME contre 2
        // DOSSIER_ORPHELIN. Les avertissements utiles y étaient matériellement invisibles, et le seul
        // écran qui confronte disque et base ne confrontait plus rien : il ensevelissait.
        //
        // Le principe est déjà écrit pour AUDIO_INDISPONIBLE : « un seul constat informatif portant le
        // décompte, jamais une erreur par fichier ».
        Long idPassage = creerPassage(1);
        Long idSession = creerSession(idPassage, 4096L);
        Path bruts = Files.createDirectories(racineSession.resolve("bruts"));
        for (int numero = 1; numero <= 3; numero++) {
            String nom = "MAUVAIS_NOM_" + numero + ".wav";
            Path fichier = Files.write(bruts.resolve(nom), new byte[16]);
            originalDao.insert(
                    new EnregistrementOriginal(null, nom, fichier.toString(), 12.0, 384_000, null, idSession));
        }

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats)
                .filteredOn(c -> c.categorie() == CategorieConstat.PREFIXE_NON_CONFORME)
                .as("trois fichiers fautifs, un seul constat")
                .hasSize(1);
    }

    @Test
    @DisplayName("#3490 : le constat agrégé garde de quoi diagnostiquer - le compte, le préfixe attendu, un exemple")
    void prefixe_agrege_garde_de_quoi_diagnostiquer() throws IOException {
        // Agréger ne doit pas revenir à taire : quatre mille exemples n'aident pas, mais zéro non plus.
        Long idPassage = creerPassage(1);
        Long idSession = creerSession(idPassage, 4096L);
        Path bruts = Files.createDirectories(racineSession.resolve("bruts"));
        for (int numero = 1; numero <= 2; numero++) {
            String nom = "MAUVAIS_NOM_" + numero + ".wav";
            Path fichier = Files.write(bruts.resolve(nom), new byte[16]);
            originalDao.insert(
                    new EnregistrementOriginal(null, nom, fichier.toString(), 12.0, 384_000, null, idSession));
        }

        ConstatAudit constat = service.auditerPassage(idPassage).constats().stream()
                .filter(c -> c.categorie() == CategorieConstat.PREFIXE_NON_CONFORME)
                .findFirst()
                .orElseThrow();

        assertThat(constat.detail())
                .as("le compte dit l'ampleur")
                .contains("2")
                .as("le préfixe attendu dit ce qu'il faudrait")
                .contains("Car")
                .as("un exemple dit ce qu'on a")
                .contains("MAUVAIS_NOM_");
    }

    @Test
    @DisplayName("ADR 0048 : les bruts absents ne sont ni une erreur ni un constat (ils sont une option)")
    void bruts_absents_ne_sont_pas_une_erreur() throws IOException {
        Long idPassage = creerSessionCoherente(1);
        // Les bruts sont une OPTION de ré-analyse (ADR 0036) : la plupart des nuits n'en gardent
        // aucun. Leur absence est donc l'état normal, pas une corruption (ADR 0048) - et un état
        // normal reste silencieux (ADR 0012).
        Files.delete(racineSession.resolve("bruts").resolve(NOM_ORIGINAL));

        assertThat(service.auditerPassage(idPassage).constats())
                .as("des bruts absents ne produisent ni erreur ni constat : c'est le cas courant")
                .isEmpty();
    }

    @Test
    @DisplayName("Fichier externe (hors workspace) absent : INFO, pas ERREUR (média peut-être non monté)")
    void fichier_externe_absent_info() {
        Long idPassage = creerPassage(1);
        Long idSession = creerSession(idPassage, 4096L);
        // Le journal du capteur porte la règle depuis que les bruts ne sont plus contrôlés : un chemin
        // hors workspace absent reste un INFO (la carte n'est peut-être pas montée), là où le même
        // fichier sous le workspace serait une ERREUR.
        String cheminExterne =
                Path.of("/media", "carte-sd-absente", "LogPR" + SERIE + ".txt").toString();
        journalDao.insert(new JournalDuCapteur(null, cheminExterne, null, null, Completude.INCONNUE, idSession));

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(Severite.INFO);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.DISQUE_MANQUANT);
        });
    }

    @Test
    @DisplayName("Unité déposée à l'ancien préfixe (Pass2) sur un passage Pass1 : DEPOT_DIVERGENT")
    void depot_divergent() throws IOException {
        Long idPassage = creerSessionCoherente(1);
        String nomDivergent = new Prefixe("040962", 2026, 2, "A1").prefixeFichier() + "1.zip";
        depotDao.synchroniserPlan(
                idPassage,
                List.of(DepotUnite.aDeposer(idPassage, nomDivergent, TypeDepotUnite.ZIP, "2026-07-11T15:00:00")));

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(Severite.ERREUR);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.DEPOT_DIVERGENT);
            assertThat(c.cible()).isEqualTo(nomDivergent);
        });
    }

    @Test
    @DisplayName("Passage sans session : SESSION_ABSENTE en INFO")
    void passage_sans_session_info() {
        Long idPassage = creerPassage(1);

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(Severite.INFO);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.SESSION_ABSENTE);
        });
    }

    @Test
    @DisplayName("auditerTout : dossier de session sur disque sans passage -> DOSSIER_ORPHELIN")
    void dossier_orphelin_via_auditer_tout() throws IOException {
        Files.createDirectories(dossier.resolve("Car040962-2026-Pass9-A1"));

        List<ConstatAudit> constats = service.auditerTout().constats();

        assertThat(constats).singleElement().satisfies(c -> {
            assertThat(c.severite()).isEqualTo(Severite.AVERTISSEMENT);
            assertThat(c.categorie()).isEqualTo(CategorieConstat.DOSSIER_ORPHELIN);
        });
    }

    @Test
    @DisplayName("#2848 : le département divergent sort de l'audit GLOBAL, et pas de l'audit ciblé")
    void departement_divergent_seulement_dans_l_audit_global() {
        // Le carré 040962 est dans l'Allier ; la commune du point le place dans les Bouches-du-Rhône.
        Long idPassage = creerPassage(1);
        new PointCommuneDao(source).definir(pointDuPassage(idPassage), new Commune("Aix-en-Provence", "13001"));

        assertThat(service.auditerTout().constats())
                .as("l'audit global est la porte que prennent l'écran Diagnostic ET `audit-coherence`")
                .extracting(ConstatAudit::categorie)
                .contains(CategorieConstat.DEPARTEMENT_DIVERGENT);
        assertThat(service.auditerPassage(idPassage).constats())
                .as("l'audit ciblé répond à « cette nuit est-elle bien rangée ? » après réparation "
                        + "(#1347) : un département divergent s'y répéterait à chaque nuit du point "
                        + "sans jamais rien apprendre de neuf")
                .extracting(ConstatAudit::categorie)
                .doesNotContain(CategorieConstat.DEPARTEMENT_DIVERGENT);
    }

    // --- Fabriques -------------------------------------------------------------------------------

    private long pointDuPassage(Long idPassage) {
        return passageDao.findById(idPassage).orElseThrow().idPoint();
    }

    private Long creerPassage(int numeroPassage) {
        return JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("040962")
                .nomSite("Étang")
                .point("A1")
                .enregistreur(SERIE)
                .nuit(numeroPassage, 2026, "2026-06-20")
                .statut(StatutWorkflow.VERIFIE)
                .verdict(Verdict.OK)
                .semerPassage()
                .idPassage();
    }

    @Test
    @DisplayName("ADR 0048 : un fichier présent mais d'empreinte divergente est un conflit, pas une absence")
    void fichier_present_mais_divergent_est_un_conflit() throws IOException {
        Long idPassage = creerSessionAvecEmpreintes();
        // Le fichier est toujours là, au même chemin et sous le même nom - mais ce n'est plus le même
        // enregistrement : redécoupe, autre nuit du même carré, sauvegarde restaurée d'une autre version.
        Path sequence = racineSession.resolve("transformes").resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, 0));
        Files.write(sequence, "un tout autre enregistrement".getBytes(StandardCharsets.UTF_8));

        List<ConstatAudit> constats = service.auditerPassage(idPassage).constats();

        assertThat(constats)
                .as("un homonyme divergent ne doit pas passer pour l'audio attendu : on validerait une espèce dessus")
                .anySatisfy(c -> {
                    assertThat(c.categorie()).isEqualTo(CategorieConstat.AUDIO_DIVERGENT);
                    assertThat(c.severite()).isEqualTo(Severite.ERREUR);
                    assertThat(c.cible()).endsWith(PREFIXE.nommerSequence(NOM_ORIGINAL, 0));
                });
    }

    @Test
    @DisplayName("ADR 0048 : contenu différent à taille IDENTIQUE : c'est l'empreinte qui doit l'attraper")
    void divergence_a_taille_identique_est_attrapee_par_l_empreinte() throws IOException {
        Long idPassage = creerSessionAvecEmpreintes();
        Path sequence = racineSession.resolve("transformes").resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, 0));
        long tailleInitiale = Files.size(sequence);
        // Même nombre d'octets, contenu différent : la comparaison de taille passe, seule l'empreinte
        // peut trancher. Sans ce cas, le contrôle serait vert en ne testant jamais que la taille.
        Files.write(sequence, "SEQUENCE-X".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.size(sequence))
                .as("le test ne prouverait rien si la taille avait changé")
                .isEqualTo(tailleInitiale);

        assertThat(service.auditerPassage(idPassage).constats())
                .anySatisfy(c -> assertThat(c.categorie()).isEqualTo(CategorieConstat.AUDIO_DIVERGENT));
    }

    @Test
    @DisplayName("ADR 0048 : des fichiers intacts et porteurs d'empreinte ne produisent aucun conflit")
    void fichiers_intacts_aucun_conflit() throws IOException {
        Long idPassage = creerSessionAvecEmpreintes();

        assertThat(service.auditerPassage(idPassage).constats())
                .as("l'empreinte concorde : rien à signaler")
                .noneMatch(c -> c.categorie() == CategorieConstat.AUDIO_DIVERGENT);
    }

    /// Session cohérente dont les séquences portent leur **vraie** empreinte (#1299) : sans elle, le
    /// contrôle d'identité n'a rien à confronter et le test serait vert sans rien prouver.
    private Long creerSessionAvecEmpreintes() throws IOException {
        Long idPassage = creerPassage(1);
        Long idSession = creerSession(idPassage, 4096L);
        Path bruts = Files.createDirectories(racineSession.resolve("bruts"));
        Path transformes = Files.createDirectories(racineSession.resolve("transformes"));
        Path original = Files.write(bruts.resolve(NOM_ORIGINAL), new byte[16]);
        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null, NOM_ORIGINAL, original.toString(), 12.0, 384_000, null, idSession))
                .id();
        for (int index = 0; index < 2; index++) {
            String nomSequence = PREFIXE.nommerSequence(NOM_ORIGINAL, index);
            Path fichier = Files.write(
                    transformes.resolve(nomSequence), ("sequence-" + index).getBytes(StandardCharsets.UTF_8));
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    nomSequence,
                    idOriginal,
                    index,
                    index * 5.0,
                    5.0,
                    fichier.toString(),
                    true,
                    idSession,
                    null,
                    new EmpreinteContenu(Files.size(fichier), Empreintes.empreinteCourte(fichier))));
        }
        Path journal = Files.write(racineSession.resolve("LogPR" + SERIE + ".txt"), new byte[16]);
        journalDao.insert(new JournalDuCapteur(null, journal.toString(), null, null, Completude.INCONNUE, idSession));
        Path releve = Files.write(racineSession.resolve("PaRecPR" + SERIE + "_THLog.csv"), new byte[16]);
        releveDao.insert(new ReleveClimatique(null, releve.toString(), null, idSession));
        return idPassage;
    }

    private Long creerSession(Long idPassage, Long volumeOriginaux) {
        return sessionDao
                .insert(new SessionDEnregistrement(null, racineSession.toString(), volumeOriginaux, 4096L, idPassage))
                .id();
    }

    private Long creerSessionCoherente(int numeroPassage) throws IOException {
        Long idPassage = creerPassage(numeroPassage);
        Long idSession = creerSession(idPassage, 4096L);
        Path bruts = Files.createDirectories(racineSession.resolve("bruts"));
        Path transformes = Files.createDirectories(racineSession.resolve("transformes"));

        Path original = Files.write(bruts.resolve(NOM_ORIGINAL), new byte[16]);
        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null, NOM_ORIGINAL, original.toString(), 12.0, 384_000, null, idSession))
                .id();
        for (int index = 0; index < 2; index++) {
            String nomSequence = PREFIXE.nommerSequence(NOM_ORIGINAL, index);
            Path sequence = Files.write(transformes.resolve(nomSequence), new byte[16]);
            sequenceDao.insert(new SequenceDEcoute(
                    null, nomSequence, idOriginal, index, index * 5.0, 5.0, sequence.toString(), true, idSession));
        }
        Path journal = Files.write(racineSession.resolve("LogPR" + SERIE + ".txt"), new byte[16]);
        journalDao.insert(new JournalDuCapteur(null, journal.toString(), null, null, Completude.INCONNUE, idSession));
        Path releve = Files.write(racineSession.resolve("PaRecPR" + SERIE + "_THLog.csv"), new byte[16]);
        releveDao.insert(new ReleveClimatique(null, releve.toString(), null, idSession));
        return idPassage;
    }
}
