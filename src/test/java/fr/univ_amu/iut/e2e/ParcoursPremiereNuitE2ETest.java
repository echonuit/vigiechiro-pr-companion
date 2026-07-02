package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.lot.model.Lot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Test E2E du parcours fil rouge P0 — « Première nuit de Marie »** : la chaîne complète de
/// bout-en-bout, racontée du point de vue de la persona débutante mono-site, **pilotée uniquement
/// par les services métier réels** (aucune IHM). On part d'une base jetable migrée, on enchaîne les
/// étapes du parcours et on **vérifie le résultat métier à chaque jalon** :
///
/// `Déclarer le site` (P1) → `Importer la nuit` (P2, statut **Transformé**) → `Vérifier`
/// (P3, verdict OK, statut **Vérifié**) → `Préparer le lot` (P4, statut **Prêt à déposer**) →
/// `Confirmer le dépôt` (statut **Déposé**).
///
/// Contrairement aux autres parcours E2E (`ParcoursDepotE2ETest`, `ParcoursSitesVersPassageE2ETest`,
/// …) qui pilotent le **vrai chrome** via TestFX, ce fil rouge n'instancie aucune vue : il sollicite
/// directement [ServiceSites], [ServiceImport], [ServiceQualification] et [ServiceLot] obtenus du
/// **même injecteur applicatif** [RacineInjecteur], et asserte **en base** (le statut du passage).
/// L'IHM de chaque écran est couverte par les tests `…ViewTest` de chaque feature ; ici on valide la
/// **continuité métier** de la déclaration jusqu'au dépôt, et les critères de réussite globaux de P0
/// (statut final `Déposé`, préfixe R6/R7 sur tous les originaux, intégrité de la SD R9).
///
/// Le harnais reprend celui des E2E existants (workspace jetable surchargé via la propriété système
/// `vigiechiro.workspace`, schéma migré par [MigrationSchema], services assemblés par les **mêmes
/// constructeurs** que la production), mais sans `ApplicationExtension` puisqu'aucune fenêtre n'est
/// montée — exactement comme `RacineInjecteurTest` qui résout déjà les services hors toolkit JavaFX.
class ParcoursPremiereNuitE2ETest {

    private static final String ID_USER = "u-e2e-p0";
    private static final String CARRE = "640380";
    private static final String CODE_POINT = "A1";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 384_000; // Hz, multiple de 10 (R10)
    private static final int TRAMES = 576_000;
    private static final String NOM_WAV = "PaRecPR" + SERIE + "_20260422_203922.wav";
    private static final String LOG =
            "22/04/26 - 16:02:20 PR1925492 Demarrage Passive Recorder numero de serie 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "22/04/26 - 16:02:21 PR1925492 Sonde temperature/hygrometrie presente, lecture toutes"
                    + " les 600s\n";

    private Injector injector;
    private SourceDeDonnees source;
    private Path workspace;

    @BeforeEach
    void preparerLaBaseJetable() throws Exception {
        workspace = Files.createTempDirectory("vc-e2e-p0");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injector = RacineInjecteur.creer();
        source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // Un seul utilisateur en base → c'est l'utilisateur courant (singleton) auquel on rattache le site.
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Marie"));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Fil rouge P0 : de la déclaration du site jusqu'au dépôt, statut à chaque jalon")
    void premiere_nuit_de_la_declaration_au_depot() throws Exception {
        // ── Étape 1 — Déclarer le site (P1) ────────────────────────────────────────────────────
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        Site site = sites.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = sites.ajouterPoint(site.id(), CODE_POINT, 43.4010, -1.5740, "Près du chêne");
        assertThat(site.id()).isNotNull();
        assertThat(point.id()).isNotNull();
        assertThat(point.code()).isEqualTo(CODE_POINT);

        // ── Étape 3 — Importer la nuit (P2) : copie + renommage + transformation → « Transformé » ─
        Prefixe prefixe = new Prefixe(CARRE, 2026, 1, CODE_POINT);
        Path sd = creerNuitSynthetique(workspace.resolve("sd"));
        ResultatImport resultat = injector.getInstance(ServiceImport.class).importer(sd, point.id(), prefixe);
        long idPassage = resultat.passage().id();
        assertThat(resultat.passage().statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(resultat.nombreSequences()).isGreaterThan(0);
        assertThat(statut(idPassage)).isEqualTo(StatutWorkflow.TRANSFORME);

        // Critère R6/R7 : tous les originaux importés portent le préfixe « Car640380-2026-Pass1-A1- ».
        List<EnregistrementOriginal> originaux = new EnregistrementOriginalDao(source)
                .findBySession(resultat.session().id());
        assertThat(originaux).isNotEmpty();
        assertThat(originaux).allSatisfy(o -> assertThat(o.nomFichier()).startsWith(prefixe.prefixeFichier()));

        // ── Étape 4 — Vérifier l'enregistrement (P3) : sélection + pré-check + verdict OK → « Vérifié » ─
        ServiceQualification qualification = injector.getInstance(ServiceQualification.class);
        assertThat(qualification.ouvrirVerification(idPassage)).isNotNull();
        assertThat(qualification.precheck(idPassage)).isNotNull();
        Passage verifie = qualification.enregistrerVerdict(idPassage, Verdict.OK, "Sound check OK sur la sélection.");
        assertThat(verifie.verdictVerification()).isEqualTo(Verdict.OK);
        assertThat(statut(idPassage)).isEqualTo(StatutWorkflow.VERIFIE);

        // ── Étape 5 — Préparer le lot puis confirmer le dépôt manuel (P4) → « Prêt à déposer » → « Déposé » ─
        ServiceLot lot = injector.getInstance(ServiceLot.class);
        Lot prepare = lot.preparerLot(idPassage);
        assertThat(prepare.idPassage()).isEqualTo(idPassage);
        assertThat(prepare.sequences()).isNotEmpty();
        assertThat(statut(idPassage)).isEqualTo(StatutWorkflow.PRET_A_DEPOSER);

        Passage depose = lot.marquerDepose(idPassage);
        assertThat(depose.deposeLe()).isNotNull();

        // Critère de réussite global P0 : le passage apparaît en base au statut « Déposé ».
        assertThat(statut(idPassage)).isEqualTo(StatutWorkflow.DEPOSE);
    }

    @Test
    @DisplayName("Critère R9 : l'import copie la SD sans jamais modifier les fichiers d'origine")
    void les_fichiers_d_origine_de_la_sd_restent_intacts() throws Exception {
        ServiceSites sites = injector.getInstance(ServiceSites.class);
        Site site = sites.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = sites.ajouterPoint(site.id(), CODE_POINT, 43.4010, -1.5740, null);

        Path sd = creerNuitSynthetique(workspace.resolve("sd"));
        Path wavOrigine = sd.resolve(NOM_WAV);
        byte[] avantImport = Files.readAllBytes(wavOrigine);

        injector.getInstance(ServiceImport.class).importer(sd, point.id(), new Prefixe(CARRE, 2026, 1, CODE_POINT));

        // R9 : la SD n'est jamais altérée — le fichier original est toujours présent, bit pour bit identique.
        assertThat(Files.exists(wavOrigine)).isTrue();
        assertThat(Files.readAllBytes(wavOrigine)).isEqualTo(avantImport);
    }

    /// Relit le statut workflow du passage **en base** (assertion métier de bout-en-bout).
    private StatutWorkflow statut(long idPassage) {
        return new PassageDao(source).findById(idPassage).orElseThrow().statutWorkflow();
    }

    /// Crée un dossier SD minimal (journal LogPR + relevé THLog + un WAV PCM valide à 2 kHz) que
    /// l'import peut traiter de bout en bout jusqu'au dépôt.
    private static Path creerNuitSynthetique(Path sd) throws Exception {
        Files.createDirectories(sd);
        Files.writeString(sd.resolve("LogPR" + SERIE + ".txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR" + SERIE + "_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        ecrireWav(sd.resolve(NOM_WAV));
        return sd;
    }

    private static void ecrireWav(Path fichier) throws Exception {
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (e & 0xFF);
            pcm[2 * i + 1] = (byte) ((e >> 8) & 0xFF);
        }
        ByteBuffer buf = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(FREQUENCE_WAV);
        buf.putInt(FREQUENCE_WAV * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
    }
}
