package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.CompacteurDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.Lot;
import fr.univ_amu.iut.lot.model.ModeDepot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.SourceDepot;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.lot.model.dao.DepotPlanDao;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
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
    private SourceDeDonnees source;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        // La topologie naît du premier `creerPassage` : la fixture la sème par trouver-ou-créer.
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);

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
                () -> ModeDepot.ARCHIVES_ZIP,
                new DepotUniteDao(source),
                new DepotPlanDao(source));
    }

    private Passage creerPassage(Verdict verdict) {
        return JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("040962")
                .nomSite("Étang")
                .point("A1")
                .enregistreur(SERIE)
                .nuit(1, 2026, "2026-06-20")
                .statut(StatutWorkflow.VERIFIE)
                .verdict(verdict)
                .semerPassage()
                .lePassage();
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
        journalDao.insert(
                new JournalDuCapteur(null, "LogPR" + SERIE + ".txt", null, null, Completude.INCONNUE, idSession));
        return idSession;
    }

    @Test
    @DisplayName("#142 : sequencesADeposer renvoie les chemins des séquences transformées du passage")
    void sequences_a_deposer_liste_les_transformes() {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());

        List<Path> sequences = service.sequencesADeposer(passage.id());

        // Les chemins sont désormais RÉSOLUS contre la racine de session : `genererArchivesDepot`
        // appliquait déjà cette règle, `sequencesADeposer` la rendait brute. L'écart ne se voyait pas
        // tant qu'on ne faisait que lister ; depuis #1994 la source lit les tailles.
        assertThat(sequences).hasSize(2).allSatisfy(chemin -> {
            assertThat(chemin.isAbsolute()).as("résolu contre la session").isTrue();
            assertThat(chemin.getParent().getFileName().toString()).isEqualTo("transformes");
        });
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
    @DisplayName("#784 : sur un passage déposé, supprimerArchivesDepot efface les .zip et renvoie l'espace libéré")
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
    @DisplayName("#784 : supprimerArchivesDepot refuse un passage non encore déposé")
    void supprimer_archives_refuse_si_non_depose() {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());
        service.preparerLot(passage.id()); // Prêt à déposer, pas encore déposé

        assertThatThrownBy(() -> service.supprimerArchivesDepot(passage.id()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("déposé");
    }

    @Test
    @DisplayName("#784 : archivesDepot liste les .zip présents sur disque (vide si aucun)")
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
    @DisplayName("#808 : espaceDisqueDisponible > 0 pour un dossier réel, 0 si chemin nul ; estimation = ratio + marge")
    void espace_disque_et_estimation() {
        assertThat(service.espaceDisqueDisponible(null)).isZero();
        assertThat(service.espaceDisqueDisponible(dossier.toString())).isPositive();
        assertThat(service.estimationTailleDepotOctets(10_000_000_000L))
                .isEqualTo(CompacteurDepot.estimationTailleDepot(10_000_000_000L));
    }

    @Test
    @DisplayName("#1994 : la source par défaut est régénérable, même sans archive sur le disque")
    void source_par_defaut_est_regenerable() throws IOException {
        // Remplace trois tests de `fichiersDepotParDefaut`, supprimée : elle rendait la liste des ZIP
        // PRÉSENTS, refusait s'il n'y en avait aucun, et se repliait en WAV en silence quand le disque
        // ne garantissait rien. Les trois comportements sont ceux que #1994 et #1997 ont bannis.
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());
        Path transformes = Files.createDirectories(
                dossier.resolve(PREFIXE.nomDossierSession()).resolve("transformes"));
        for (int i = 0; i < 2; i++) {
            Files.write(transformes.resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, i)), new byte[1024]);
        }
        // Séquences présentes sur le disque, AUCUNE archive générée.

        SourceDepot source = service.sourceDepotParDefaut(passage.id());

        assertThat(source.identifiants())
                .as("le mode ZIP par défaut nomme ses archives sans qu'elles existent")
                .isNotEmpty()
                .allSatisfy(id -> assertThat(id).endsWith(".zip"));
    }

    @Test
    @DisplayName("#984 : reinitialiserDepot efface le plan et ramène « Déposé » à « Prêt à déposer »")
    void reinitialiser_depot_efface_et_reprepare() {
        Passage passage = creerPassage(Verdict.OK);
        creerSessionCoherente(passage.id());
        service.preparerLot(passage.id()); // → Prêt à déposer
        DepotUniteDao depotUnites = new DepotUniteDao(new SourceDeDonnees(new Workspace(dossier)));
        depotUnites.synchroniserPlan(
                passage.id(),
                List.of(DepotUnite.aDeposer(passage.id(), "Car-1.zip", TypeDepotUnite.ZIP, "2026-07-01T00:00:00")));
        service.marquerDepose(passage.id()); // → Déposé

        service.reinitialiserDepot(passage.id());

        assertThat(depotUnites.parPassage(passage.id())).isEmpty();
        assertThat(passageDao.findById(passage.id()).orElseThrow().statutWorkflow())
                .isEqualTo(StatutWorkflow.PRET_A_DEPOSER);
    }

    @Test
    @DisplayName("R14 : preparerLot refuse un passage « Inexploitable » (RegleMetierException)")
    void preparer_lot_a_jeter_refuse() {
        Passage passage = creerPassage(Verdict.A_JETER);
        creerSessionCoherente(passage.id());

        assertThatThrownBy(() -> service.preparerLot(passage.id()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Inexploitable")
                .hasMessageContaining("Re-vérifiez"); // lot 7 : oriente vers la requalification

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
        journalDao.insert(
                new JournalDuCapteur(null, "LogPR" + SERIE + ".txt", null, null, Completude.INCONNUE, idSession));
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
    @DisplayName("R14 : marquerDepose refuse aussi un passage « Inexploitable »")
    void marquer_depose_a_jeter_refuse() {
        Passage passage = creerPassage(Verdict.A_JETER);
        creerSessionCoherente(passage.id());

        assertThatThrownBy(() -> service.marquerDepose(passage.id())).isInstanceOf(RegleMetierException.class);

        assertThat(passageDao.findById(passage.id()).orElseThrow().deposeLe())
                .as("aucune date de dépôt posée après refus")
                .isNull();
    }
}
