package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.CompacteurDepot;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.Lot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du service [ServiceLot] de bout en bout sur une base SQLite jetable (`@TempDir` +
/// [MigrationSchema]), avec le vrai moteur [VerificationCoherence] et une [HorlogeFigee]
/// (date de dépôt déterministe). Couvre R14 (refus dur), le refus sur incohérence, et la
/// transition de statut avec horodatage du dépôt.
class ServiceLotTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");
    private static final LocalDateTime INSTANT_DEPOT = LocalDateTime.of(2026, 5, 31, 22, 30, 0);

    @TempDir
    Path dossier;

    private ServiceLot service;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private JournalDuCapteurDao journalDao;
    private final Horloge horloge = new HorlogeFigee(INSTANT_DEPOT);
    private Long idPoint;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        Site site = siteDao.insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        idPoint = pointDao.insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));

        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        journalDao = new JournalDuCapteurDao(source);
        ReleveClimatiqueDao releveDao = new ReleveClimatiqueDao(source);

        VerificationCoherence verification = new VerificationCoherence(
                siteDao, pointDao, sessionDao, originalDao, sequenceDao, journalDao, releveDao);
        service = new ServiceLot(
                passageDao,
                sessionDao,
                sequenceDao,
                verification,
                new MoteurWorkflowPassage(),
                horloge,
                CompacteurDepot::new,
                new DepotUniteDao(source));
    }

    private Passage creerPassage(Verdict verdict) {
        return passageDao.insert(new Passage(
                null,
                1,
                2026,
                "2026-06-20",
                "21:30:00",
                "05:15:00",
                null,
                StatutWorkflow.VERIFIE,
                verdict,
                null,
                null,
                null,
                idPoint,
                SERIE));
    }

    /// Session entièrement cohérente : 2 séquences préfixées issues d'un original + journal.
    private Long creerSessionCoherente(Long idPassage) {
        Long idSession = sessionDao
                .insert(new SessionDEnregistrement(
                        null, dossier.resolve(PREFIXE.nomDossierSession()).toString(), null, 8192L, idPassage))
                .id();
        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null, NOM_ORIGINAL, "bruts/" + NOM_ORIGINAL, 12.0, 384000, null, idSession))
                .id();
        for (int i = 0; i < 2; i++) {
            String nom = PREFIXE.nommerSequence(NOM_ORIGINAL, i);
            sequenceDao.insert(
                    new SequenceDEcoute(null, nom, idOriginal, i, i * 5.0, 5.0, "transformes/" + nom, true, idSession));
        }
        journalDao.insert(new JournalDuCapteur(null, "LogPR" + SERIE + ".txt", null, null, idSession));
        return idSession;
    }

    @Test
    @DisplayName("#142 : sequencesADeposer renvoie les chemins des séquences transformées du passage")
    void sequences_a_deposer_liste_les_transformes() {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());

        List<Path> sequences = service.sequencesADeposer(passage.id());

        assertThat(sequences)
                .hasSize(2)
                .allSatisfy(chemin -> assertThat(chemin.toString()).startsWith("transformes/"));
    }

    @Test
    @DisplayName("#142 : sequencesADeposer sur un passage sans session → liste vide")
    void sequences_a_deposer_sans_session_est_vide() {
        Passage passage = creerPassage(Verdict.OK);

        assertThat(service.sequencesADeposer(passage.id())).isEmpty();
    }

    @Test
    @DisplayName("consulterLot reflète statut/dossier/séquences/volume sans transitionner le passage")
    void consulter_lot_reflete_l_etat() {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());

        EtatLot etat = service.consulterLot(passage.id());

        assertThat(etat.statut()).isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(etat.cheminDossier()).endsWith(PREFIXE.nomDossierSession());
        assertThat(etat.nombreSequences()).isEqualTo(2);
        assertThat(etat.volumeSequencesOctets()).isEqualTo(8192L);
        // #254 : passage cohérent → la checklist n'a aucun contrôle en échec (mais elle n'est pas vide).
        assertThat(etat.aDesEchecs()).isFalse();
        assertThat(etat.controles()).isNotEmpty().allMatch(c -> !c.estBloquant());
        assertThat(etat.deposeLe()).isNull();
        // Lecture pure : le statut n'a pas bougé.
        assertThat(passageDao.findById(passage.id()).orElseThrow().statutWorkflow())
                .isEqualTo(StatutWorkflow.VERIFIE);
    }

    @Test
    @DisplayName("preparerLot assemble le récapitulatif et passe le statut à « Prêt à déposer »")
    void preparer_lot_coherent() {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());

        Lot lot = service.preparerLot(passage.id());

        assertThat(lot.idPassage()).isEqualTo(passage.id());
        assertThat(lot.nombreSequences()).isEqualTo(2);
        assertThat(lot.volumeSequencesOctets()).isEqualTo(8192L);
        assertThat(lot.cheminDossier()).endsWith(PREFIXE.nomDossierSession());
        assertThat(lot.sequences())
                .extracting(SequenceDEcoute::nomFichier)
                .allMatch(nom -> nom.startsWith(PREFIXE.prefixeFichier()));
        assertThat(passageDao.findById(passage.id()).orElseThrow().statutWorkflow())
                .isEqualTo(StatutWorkflow.PRET_A_DEPOSER);
    }

    @Test
    @DisplayName("#110 : sur un lot préparé, genererArchivesDepot produit « <préfixe>-1.zip » avec les séquences")
    void generer_archives_depot() throws IOException {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());
        service.preparerLot(passage.id()); // → Prêt à déposer (préalable exigé par #110)
        // Le DAO ne stocke que les lignes : on crée les vrais fichiers des 2 séquences dans transformes/.
        Path transformes = Files.createDirectories(
                dossier.resolve(PREFIXE.nomDossierSession()).resolve("transformes"));
        for (int i = 0; i < 2; i++) {
            Files.write(transformes.resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, i)), new byte[1024]);
        }

        List<ArchiveDepot> archives = service.genererArchivesDepot(passage.id());

        // Petit lot → une seule archive, nommée d'après le dossier de session (préfixe R6) + « -1 ».
        assertThat(archives).singleElement().satisfies(a -> {
            assertThat(a.chemin().getFileName()).hasToString(PREFIXE.nomDossierSession() + "-1.zip");
            assertThat(a.numero()).isEqualTo(1);
            assertThat(a.nombreFichiers()).isEqualTo(2);
        });
        // Écrite dans le sous-dossier depot/ de la session.
        assertThat(archives.get(0).chemin())
                .exists()
                .hasParent(dossier.resolve(PREFIXE.nomDossierSession()).resolve("depot"));
    }

    @Test
    @DisplayName("#110 : genererArchivesDepot refuse un passage non préparé (statut Vérifié)")
    void generer_archives_avant_preparation_refuse() {
        Passage passage = creerPassage(Verdict.OK); // statut Vérifié : lot pas encore préparé
        creerSessionCoherente(passage.id());

        assertThatThrownBy(() -> service.genererArchivesDepot(passage.id()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Prêt à déposer");
    }

    @Test
    @DisplayName("#… : sur un passage déposé, supprimerArchivesDepot efface les .zip et renvoie l'espace libéré")
    void supprimer_archives_depot_libere_l_espace() throws IOException {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());
        service.preparerLot(passage.id());
        Path transformes = Files.createDirectories(
                dossier.resolve(PREFIXE.nomDossierSession()).resolve("transformes"));
        for (int i = 0; i < 2; i++) {
            Files.write(transformes.resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, i)), new byte[1024]);
        }
        List<ArchiveDepot> archives = service.genererArchivesDepot(passage.id());
        service.marquerDepose(passage.id()); // → Déposé (préalable à la suppression)
        Path depot = dossier.resolve(PREFIXE.nomDossierSession()).resolve("depot");
        assertThat(archives.get(0).chemin()).exists();

        long liberes = service.supprimerArchivesDepot(passage.id());

        assertThat(liberes).isPositive();
        assertThat(archives.get(0).chemin()).doesNotExist();
        try (var flux = Files.list(depot)) {
            assertThat(flux.filter(p -> p.toString().endsWith(".zip"))).isEmpty();
        }
    }

    @Test
    @DisplayName("#… : supprimerArchivesDepot refuse un passage non encore déposé")
    void supprimer_archives_refuse_si_non_depose() {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());
        service.preparerLot(passage.id()); // Prêt à déposer, pas encore déposé

        assertThatThrownBy(() -> service.supprimerArchivesDepot(passage.id()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("déposé");
    }

    @Test
    @DisplayName("#… : archivesDepot liste les .zip présents sur disque (vide si aucun)")
    void archives_depot_liste_les_zip_du_disque() throws IOException {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());
        String cheminDossier = dossier.resolve(PREFIXE.nomDossierSession()).toString();
        assertThat(service.archivesDepot(cheminDossier)).isEmpty();

        service.preparerLot(passage.id());
        Path transformes = Files.createDirectories(
                dossier.resolve(PREFIXE.nomDossierSession()).resolve("transformes"));
        for (int i = 0; i < 2; i++) {
            Files.write(transformes.resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, i)), new byte[1024]);
        }
        service.genererArchivesDepot(passage.id());

        assertThat(service.archivesDepot(cheminDossier)).singleElement().satisfies(a -> {
            assertThat(a.chemin().getFileName().toString()).endsWith(".zip");
            assertThat(a.nombreFichiers()).isEqualTo(2);
            assertThat(a.tailleOctets()).isPositive();
        });
    }

    @Test
    @DisplayName("#… : espaceDisqueDisponible > 0 pour un dossier réel, 0 si chemin nul ; estimation = ratio + marge")
    void espace_disque_et_estimation() {
        assertThat(service.espaceDisqueDisponible(null)).isZero();
        assertThat(service.espaceDisqueDisponible(dossier.toString())).isPositive();
        assertThat(service.estimationTailleDepotOctets(10_000_000_000L))
                .isEqualTo(CompacteurDepot.estimationTailleDepot(10_000_000_000L));
    }

    @Test
    @DisplayName("#984 : fichiersDepotParDefaut privilégie les archives ZIP présentes (dépôt ZIP par défaut)")
    void fichiers_depot_par_defaut_prefere_les_archives() throws IOException {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());
        Path depot = Files.createDirectories(
                dossier.resolve(PREFIXE.nomDossierSession()).resolve("depot"));
        Files.createFile(depot.resolve(PREFIXE.nomDossierSession() + "-1.zip"));

        List<Path> fichiers = service.fichiersDepotParDefaut(passage.id());

        assertThat(fichiers)
                .singleElement()
                .satisfies(p -> assertThat(p.getFileName().toString()).endsWith(".zip"));
    }

    @Test
    @DisplayName("#984 : sans archives mais espace disque suffisant → refus invitant à générer d'abord (étape 2)")
    void fichiers_depot_par_defaut_sans_archives_invite_a_generer() {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id()); // séquences présentes (volume connu), aucune archive générée

        assertThatThrownBy(() -> service.fichiersDepotParDefaut(passage.id()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("étape 2");
    }

    @Test
    @DisplayName("#984 : repli WAV quand la création d'archives ne peut être garantie (volume inconnu)")
    void fichiers_depot_par_defaut_repli_wav() {
        Passage passage = creerPassage(Verdict.OK);
        // Session sans volume calculé : impossible de garantir que les archives ZIP tiendraient sur le
        // disque → repli WAV (dépose les séquences déjà présentes, aucun espace supplémentaire requis).
        Long idSession = sessionDao
                .insert(new SessionDEnregistrement(
                        null, dossier.resolve(PREFIXE.nomDossierSession()).toString(), null, null, passage.id()))
                .id();
        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null, NOM_ORIGINAL, "bruts/" + NOM_ORIGINAL, 12.0, 384000, null, idSession))
                .id();
        String nom = PREFIXE.nommerSequence(NOM_ORIGINAL, 0);
        sequenceDao.insert(
                new SequenceDEcoute(null, nom, idOriginal, 0, 0.0, 5.0, "transformes/" + nom, true, idSession));

        List<Path> fichiers = service.fichiersDepotParDefaut(passage.id());

        assertThat(fichiers)
                .singleElement()
                .satisfies(p -> assertThat(p.toString()).startsWith("transformes/"));
    }

    @Test
    @DisplayName("R14 : preparerLot refuse un passage « À jeter » (RegleMetierException)")
    void preparer_lot_a_jeter_refuse() {
        Passage passage = creerPassage(Verdict.A_JETER);
        creerSessionCoherente(passage.id());

        assertThatThrownBy(() -> service.preparerLot(passage.id()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("À jeter");

        assertThat(passageDao.findById(passage.id()).orElseThrow().statutWorkflow())
                .as("statut inchangé après refus R14")
                .isEqualTo(StatutWorkflow.VERIFIE);
    }

    @Test
    @DisplayName("preparerLot refuse un passage incohérent (originaux non transformés)")
    void preparer_lot_incoherent_refuse() {
        Passage passage = creerPassage(Verdict.OK);
        Long idSession = sessionDao
                .insert(new SessionDEnregistrement(null, "racine", null, null, passage.id()))
                .id();
        originalDao.insert(
                new EnregistrementOriginal(null, NOM_ORIGINAL, "bruts/" + NOM_ORIGINAL, 12.0, 384000, null, idSession));
        journalDao.insert(new JournalDuCapteur(null, "LogPR" + SERIE + ".txt", null, null, idSession));
        // aucune séquence : transformation manquante

        assertThatThrownBy(() -> service.preparerLot(passage.id()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("impossible");

        assertThat(passageDao.findById(passage.id()).orElseThrow().statutWorkflow())
                .isEqualTo(StatutWorkflow.VERIFIE);
    }

    @Test
    @DisplayName("preparerLot sur un passage inconnu est refusé")
    void preparer_lot_passage_inconnu() {
        assertThatThrownBy(() -> service.preparerLot(9999L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    @DisplayName("marquerDepose passe le statut à « Déposé » et horodate la date de dépôt")
    void marquer_depose_pose_statut_et_date() {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());
        service.preparerLot(passage.id());

        Passage depose = service.marquerDepose(passage.id());

        assertThat(depose.statutWorkflow()).isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(depose.deposeLe()).isEqualTo(horloge.maintenant().toString());
        Passage relu = passageDao.findById(passage.id()).orElseThrow();
        assertThat(relu.statutWorkflow()).isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(relu.deposeLe()).isEqualTo(horloge.maintenant().toString());
    }

    @Test
    @DisplayName("R14 : marquerDepose refuse aussi un passage « À jeter »")
    void marquer_depose_a_jeter_refuse() {
        Passage passage = creerPassage(Verdict.A_JETER);
        creerSessionCoherente(passage.id());

        assertThatThrownBy(() -> service.marquerDepose(passage.id())).isInstanceOf(RegleMetierException.class);

        assertThat(passageDao.findById(passage.id()).orElseThrow().deposeLe())
                .as("aucune date de dépôt posée après refus")
                .isNull();
    }
}
