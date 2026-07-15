package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.MethodeSelection;
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
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.qualification.model.ContexteVerification;
import fr.univ_amu.iut.qualification.model.GenerateurSelection;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Feu;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du service [ServiceQualification] de bout en bout sur une base SQLite jetable
/// (`@TempDir` + [MigrationSchema]), avec les vrais DAO des features `passage` et `sites`.
/// Couvre R12 (sélection 10-30 déterministe), le marquage écouté via la jonction, R13
/// (verdict sans seuil d'écoute + transition vers `Vérifié`) et le pré-check 3 feux.
class ServiceQualificationTest {

    private static final String ID_USER = "u-1";
    private final Prefixe prefixe = new Prefixe("040962", 2026, 1, "A1");

    @TempDir
    Path dossier;

    private SiteDao siteDao;
    private PointDao pointDao;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private SelectionDao selectionDao;
    private ServiceQualification service;

    private long idPassage;
    private long idSession;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));

        siteDao = new SiteDao(source);
        pointDao = new PointDao(source);
        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        selectionDao = new SelectionDao(source);
        EnregistreurDao enregistreurDao = new EnregistreurDao(source);

        Site site = siteDao.insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        PointDEcoute point = pointDao.insert(new PointDEcoute(null, "A1", 43.5, 5.4, null, site.id()));
        enregistreurDao.insert(new Enregistreur("1925492", "V1.01", null));
        Passage passage = passageDao.insert(new Passage(
                null,
                1,
                2026,
                "2026-06-20",
                "20:00:00",
                "06:00:00",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                null,
                null,
                point.id(),
                "1925492"));
        idPassage = passage.id();
        SessionDEnregistrement session = sessionDao.insert(
                new SessionDEnregistrement(null, "/ws/" + prefixe.nomDossierSession(), null, null, idPassage));
        idSession = session.id();

        service = new ServiceQualification(
                selectionDao,
                sequenceDao,
                sessionDao,
                originalDao,
                passageDao,
                pointDao,
                siteDao,
                new GenerateurSelection(),
                new PreCheckNuit(),
                new UniteDeTravail(source));
    }

    /// Insère un original + sa séquence d'écoute, horodatés par `suffixe` (R7).
    private SequenceDEcoute insererSequence(String suffixeEnregistreur) {
        String nomOriginal = prefixe.nommerOriginal(suffixeEnregistreur);
        EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                null, nomOriginal, "/ws/bruts/" + nomOriginal, 5.0, 384000, null, idSession));
        String nomSequence = prefixe.nommerSequence(nomOriginal, 0);
        return sequenceDao.insert(new SequenceDEcoute(
                null, nomSequence, original.id(), 0, 0.0, 5.0, "/ws/transformes/" + nomSequence, false, idSession));
    }

    /// Crée `n` séquences chronologiquement ordonnées (suffixe d'horodatage croissant).
    private List<SequenceDEcoute> creerNuit(int n) {
        List<SequenceDEcoute> sequences = new ArrayList<>();
        for (int t = 0; t < n; t++) {
            sequences.add(insererSequence("PaRecPR1925492_20260620_" + String.format("%06d", t) + ".wav"));
        }
        return sequences;
    }

    private EnregistrementOriginal insererOriginal(String nomComplet) {
        return originalDao.insert(
                new EnregistrementOriginal(null, nomComplet, "/ws/bruts/" + nomComplet, 5.0, 384000, null, idSession));
    }

    private List<Long> idsSequencesDansLOrdre(Long idSelection) {
        return service.sequencesDeLaSelection(idSelection).stream()
                .map(SequenceSelectionnee::idSequence)
                .toList();
    }

    // --- R12 : constitution de la sélection ----------------------------------

    @Test
    @DisplayName("R12 : à l'ouverture, sélection RéparTemporel de taille 10-30 répartie sur la nuit")
    void ouvrir_constitue_une_selection_repartie() {
        List<SequenceDEcoute> nuit = creerNuit(50);

        SelectionDEcoute selection = service.ouvrirVerification(idPassage);

        assertThat(selection.id()).isNotNull();
        assertThat(selection.methode()).isEqualTo(MethodeSelection.REPARTITION_TEMPORELLE);
        List<SequenceSelectionnee> jonction = service.sequencesDeLaSelection(selection.id());
        assertThat(jonction.size())
                .as("taille dans la fourchette 10-30")
                .isBetween(GenerateurSelection.TAILLE_MIN, GenerateurSelection.TAILLE_MAX);
        assertThat(jonction)
                .extracting(SequenceSelectionnee::position)
                .as("positions ordonnées à partir de 0")
                .startsWith(0, 1, 2);
        assertThat(jonction.get(0).idSequence())
                .as("première séquence de la nuit retenue")
                .isEqualTo(nuit.get(0).id());
        assertThat(jonction.get(jonction.size() - 1).idSequence())
                .as("dernière séquence de la nuit retenue")
                .isEqualTo(nuit.get(nuit.size() - 1).id());
    }

    @Test
    @DisplayName("R12 : la constitution est déterministe (mêmes séquences à chaque (re)génération)")
    void selection_deterministe() {
        creerNuit(50);

        SelectionDEcoute selection1 = service.creerSelection(idPassage, MethodeSelection.REPARTITION_TEMPORELLE, 20);
        List<Long> run1 = idsSequencesDansLOrdre(selection1.id());

        SelectionDEcoute selection2 = service.creerSelection(idPassage, MethodeSelection.REPARTITION_TEMPORELLE, 20);
        List<Long> run2 = idsSequencesDansLOrdre(selection2.id());

        assertThat(run1).hasSize(20);
        assertThat(run2).isEqualTo(run1);
    }

    @Test
    @DisplayName("L'ouverture est idempotente (une seule sélection par passage)")
    void ouverture_idempotente() {
        creerNuit(40);

        SelectionDEcoute premiere = service.ouvrirVerification(idPassage);
        SelectionDEcoute seconde = service.ouvrirVerification(idPassage);

        assertThat(seconde.id()).isEqualTo(premiere.id());
        assertThat(selectionDao.findByPassage(idPassage)).isPresent();
    }

    @Test
    @DisplayName("La taille est configurable (15 séquences demandées → 15 retenues)")
    void taille_configurable() {
        creerNuit(50);

        SelectionDEcoute selection = service.creerSelection(idPassage, MethodeSelection.REPARTITION_TEMPORELLE, 15);

        assertThat(selection.taille()).isEqualTo(15);
        assertThat(service.sequencesDeLaSelection(selection.id())).hasSize(15);
    }

    @Test
    @DisplayName("Marquer une séquence écoutée bascule son flag dans la jonction")
    void marquer_sequence_ecoutee() {
        creerNuit(40);
        SelectionDEcoute selection = service.ouvrirVerification(idPassage);
        Long premiere = service.sequencesDeLaSelection(selection.id()).get(0).idSequence();

        service.marquerSequenceEcoutee(selection.id(), premiere);

        SequenceSelectionnee relue = service.sequencesDeLaSelection(selection.id()).stream()
                .filter(s -> s.idSequence().equals(premiere))
                .findFirst()
                .orElseThrow();
        assertThat(relue.ecoutee()).isTrue();
    }

    @Test
    @DisplayName("Constituer une sélection sur un passage sans séquence est refusé")
    void selection_sans_sequence_refusee() {
        assertThatThrownBy(() -> service.ouvrirVerification(idPassage)).isInstanceOf(RegleMetierException.class);
    }

    @Test
    @DisplayName("Constituer une sélection sur un passage inconnu est refusé")
    void selection_passage_inconnu_refusee() {
        assertThatThrownBy(() -> service.creerSelection(9999L, MethodeSelection.REPARTITION_TEMPORELLE, 20))
                .isInstanceOf(RegleMetierException.class);
    }

    // --- R13 : verdict global + transition de statut --------------------------

    @Test
    @DisplayName("R13 : enregistrer un verdict le persiste et fait transiter le passage vers Vérifié")
    void verdict_persiste_et_transite() {
        Passage verifie = service.enregistrerVerdict(idPassage, Verdict.OK, "vent faible vers 02:00");

        assertThat(verifie.verdictVerification()).isEqualTo(Verdict.OK);
        assertThat(verifie.statutWorkflow()).isEqualTo(StatutWorkflow.VERIFIE);
        Passage relu = passageDao.findById(idPassage).orElseThrow();
        assertThat(relu.verdictVerification()).isEqualTo(Verdict.OK);
        assertThat(relu.statutWorkflow()).isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(relu.commentaire()).isEqualTo("vent faible vers 02:00");
    }

    @Test
    @DisplayName("R13 : aucun seuil d'écoute obligatoire (verdict accepté sans aucune écoute)")
    void verdict_sans_seuil_d_ecoute() {
        creerNuit(40);
        SelectionDEcoute selection = service.ouvrirVerification(idPassage);
        // Aucune séquence marquée écoutée : le verdict doit néanmoins être accepté (R13).
        assertThat(service.sequencesDeLaSelection(selection.id()))
                .allSatisfy(s -> assertThat(s.ecoutee()).isFalse());

        Passage verifie = service.enregistrerVerdict(idPassage, Verdict.DOUTEUX, null);

        assertThat(verifie.statutWorkflow()).isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(verifie.verdictVerification()).isEqualTo(Verdict.DOUTEUX);
    }

    @Test
    @DisplayName("Verdict À jeter mémorisé et restitué par estAJeter (prépare R14)")
    void verdict_a_jeter_prepare_r14() {
        service.enregistrerVerdict(idPassage, Verdict.A_JETER, null);

        assertThat(service.estAJeter(idPassage)).isTrue();
        assertThat(passageDao.findById(idPassage).orElseThrow().statutWorkflow())
                .isEqualTo(StatutWorkflow.VERIFIE);
    }

    @Test
    @DisplayName("Un verdict null ou À vérifier (sentinelle) est refusé")
    void verdict_sentinelle_refuse() {
        assertThatThrownBy(() -> service.enregistrerVerdict(idPassage, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enregistrerVerdict(idPassage, Verdict.A_VERIFIER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("#1514 : une nuit déposée refuse tout nouveau verdict (verdict figé, pas de régression)")
    void verdict_fige_sur_passage_depose() {
        // La nuit est déposée sur Vigie-Chiro (statut Déposé + date de dépôt), comme après un dépôt réel.
        Passage passage = passageDao.findById(idPassage).orElseThrow();
        passageDao.update(new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                passage.commentaire(),
                passage.donneesMeteo(),
                "2026-06-23T08:00",
                passage.idPoint(),
                passage.idEnregistreur()));

        assertThatThrownBy(() -> service.enregistrerVerdict(idPassage, Verdict.A_JETER, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Verdict figé");

        // Aucune régression : la nuit reste Déposée, avec son verdict et sa date de dépôt intacts.
        Passage relu = passageDao.findById(idPassage).orElseThrow();
        assertThat(relu.statutWorkflow()).isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(relu.verdictVerification()).isEqualTo(Verdict.OK);
        assertThat(relu.deposeLe()).isEqualTo("2026-06-23T08:00");
    }

    // --- P3 étape 1 : pré-check 3 feux ----------------------------------------

    @Test
    @DisplayName("Pré-check : nuit creuse bien nommée et bien couverte → orange/vert/vert")
    void precheck_nuit_creuse() {
        // 3 originaux bien nommés couvrant la fenêtre 20:00 → 06:00 (écarts < 30 min).
        insererSequence("PaRecPR1925492_20260620_200500.wav");
        insererSequence("PaRecPR1925492_20260621_030000.wav");
        insererSequence("PaRecPR1925492_20260621_055500.wav");

        PreCheckNuit.Diagnostic diagnostic = service.precheck(idPassage);

        assertThat(diagnostic.nombreFichiers()).as("3 fichiers < 50 → creux").isEqualTo(Feu.ORANGE);
        assertThat(diagnostic.coherenceRenommage()).isEqualTo(Feu.VERT);
        assertThat(diagnostic.couvertureHoraire()).isEqualTo(Feu.VERT);
    }

    @Test
    @DisplayName("Pré-check : un fichier au préfixe divergent → renommage rouge (R6)")
    void precheck_renommage_rouge() {
        insererSequence("PaRecPR1925492_20260620_220000.wav");
        insererSequence("PaRecPR1925492_20260620_230000.wav");
        insererOriginal("MAUVAIS-PaRecPR1925492_20260620_233000.wav");

        PreCheckNuit.Diagnostic diagnostic = service.precheck(idPassage);

        assertThat(diagnostic.coherenceRenommage()).isEqualTo(Feu.ROUGE);
    }

    @Test
    @DisplayName("Pré-check : une moitié de nuit manquante → couverture rouge")
    void precheck_couverture_rouge() {
        // Tous les enregistrements concentrés en fin de nuit : la première moitié manque.
        insererSequence("PaRecPR1925492_20260621_020000.wav");
        insererSequence("PaRecPR1925492_20260621_023000.wav");
        insererSequence("PaRecPR1925492_20260621_030000.wav");

        PreCheckNuit.Diagnostic diagnostic = service.precheck(idPassage);

        assertThat(diagnostic.couvertureHoraire()).isEqualTo(Feu.ROUGE);
    }

    @Test
    @DisplayName("Pré-check : aucun fichier importé → nombre rouge")
    void precheck_aucun_fichier() {
        PreCheckNuit.Diagnostic diagnostic = service.precheck(idPassage);

        assertThat(diagnostic.nombreFichiers()).isEqualTo(Feu.ROUGE);
    }

    // --- Projections de lecture (bandeau + liste) ----------------------------

    @Test
    @DisplayName("chargerContexte renvoie identité, plage horaire, totaux et statut du passage")
    void charger_contexte() {
        creerNuit(20);

        ContexteVerification contexte = service.chargerContexte(idPassage);

        assertThat(contexte.numeroCarre()).isEqualTo("040962");
        assertThat(contexte.codePoint()).isEqualTo("A1");
        assertThat(contexte.numeroPassage()).isEqualTo(1);
        assertThat(contexte.annee()).isEqualTo(2026);
        assertThat(contexte.date()).isEqualTo("2026-06-20");
        assertThat(contexte.heureDebut()).isEqualTo("20:00:00");
        assertThat(contexte.sequencesTotales()).isEqualTo(20);
        assertThat(contexte.dureeEnregistreeSecondes()).isEqualTo(100.0); // 20 × 5,0 s
        assertThat(contexte.statut()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(contexte.verdict()).isNull();
    }

    @Test
    @DisplayName("chargerContexte sur un passage introuvable lève RegleMetierException")
    void charger_contexte_passage_introuvable() {
        assertThatThrownBy(() -> service.chargerContexte(9999L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    @DisplayName("detaillerSelection joint les séquences (ordre position) et reflète le flag écouté")
    void detailler_selection() {
        List<SequenceDEcoute> nuit = creerNuit(50);
        SelectionDEcoute selection = service.ouvrirVerification(idPassage);

        List<SequenceEnSelection> lignes = service.detaillerSelection(selection.id());

        assertThat(lignes).extracting(SequenceEnSelection::position).startsWith(0, 1, 2);
        assertThat(lignes).allSatisfy(ligne -> assertThat(ligne.ecoutee()).isFalse());
        assertThat(lignes.get(0).sequence().id()).isEqualTo(nuit.get(0).id());

        Long premiere = lignes.get(0).sequence().id();
        service.marquerSequenceEcoutee(selection.id(), premiere);
        SequenceEnSelection rechargee = service.detaillerSelection(selection.id()).stream()
                .filter(ligne -> ligne.sequence().id().equals(premiere))
                .findFirst()
                .orElseThrow();
        assertThat(rechargee.ecoutee()).isTrue();
    }
}
