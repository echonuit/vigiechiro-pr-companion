package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReprefixeurSession;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.RattachementDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du service métier [ServicePassage] sur une base SQLite jetable (`@TempDir` +
/// [MigrationSchema]), même fixture que les `*DaoTest`. L'[HorlogeFigee] rend déterministes
/// l'année déduite à la création et l'horodatage de dépôt.
///
/// Couvre : R5 (unicité dure du quadruplet et son scope), R3 (fenêtre saisonnière soft, dans/hors
/// fenêtre, muette en recherche), R4 (intervalle < 1 mois soft), les transitions de workflow
/// (valides/invalides, horodatage du dépôt) et la pose du verdict.
class ServicePassageTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final LocalDate JOUR_FIXE = LocalDate.of(2026, 5, 31);

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ServicePassage service;
    private PassageDao passageDao;
    private Long idPoint;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));
        passageDao = new PassageDao(source);
        service = new ServicePassage(
                passageDao,
                new MoteurWorkflowPassage(),
                new HorlogeFigee(JOUR_FIXE),
                new SessionDao(source),
                new SequenceDao(source),
                new ReprefixeurSession(),
                new UniteDeTravail(source),
                new RattachementDao(),
                new MaterielMicroDao(source));
    }

    /// Construit un passage candidat (non persisté) pour les vérifications R3/R4.
    private Passage candidat(int numero, String dateEnregistrement) {
        return new Passage(
                null,
                numero,
                LocalDate.parse(dateEnregistrement).getYear(),
                dateEnregistrement,
                "21:30:00",
                "05:15:00",
                null,
                StatutWorkflow.IMPORTE,
                null,
                null,
                null,
                null,
                idPoint,
                SERIE);
    }

    // --- Création + R5 (dure) ---

    @Test
    @DisplayName("Créer un passage l'insère à l'état Importé, année déduite de la date")
    void creer_passage_valide() {
        Passage passage = service.creerPassage(
                idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, "RAS", null);

        assertThat(passage.id()).isNotNull();
        assertThat(passage.annee()).isEqualTo(2026);
        assertThat(passage.dateEnregistrement()).isEqualTo("2026-06-20");
        assertThat(passage.statutWorkflow()).isEqualTo(StatutWorkflow.IMPORTE);
        assertThat(passage.verdictVerification()).isNull();
        assertThat(passageDao.findById(passage.id())).isPresent();
    }

    @Test
    @DisplayName("Date null : l'année et la date sont prises de l'horloge injectée")
    void creer_passage_date_par_defaut_de_l_horloge() {
        Passage passage = service.creerPassage(idPoint, SERIE, 1, null, "21:30:00", "05:15:00", null, null, null);

        assertThat(passage.annee()).isEqualTo(2026);
        assertThat(passage.dateEnregistrement()).isEqualTo("2026-05-31");
    }

    @Test
    @DisplayName("R5 : le même quadruplet (point, année, n°) deux fois est refusé")
    void r5_quadruplet_duplique_refuse() {
        service.creerPassage(idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null);

        assertThatThrownBy(() -> service.creerPassage(
                        idPoint, SERIE, 1, LocalDate.of(2026, 7, 10), "21:30:00", "05:15:00", null, null, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("existe déjà");
        assertThat(passageDao.findByPoint(idPoint)).hasSize(1);
    }

    @Test
    @DisplayName("R5 : un autre n° ou une autre année sur le même point reste autorisé")
    void r5_scope_autre_numero_ou_annee() {
        service.creerPassage(idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null);

        Passage p2 = service.creerPassage(
                idPoint, SERIE, 2, LocalDate.of(2026, 9, 1), "21:30:00", "05:15:00", null, null, null);
        Passage p1AutreAnnee = service.creerPassage(
                idPoint, SERIE, 1, LocalDate.of(2025, 6, 20), "21:30:00", "05:15:00", null, null, null);

        assertThat(p2.id()).isNotNull();
        assertThat(p1AutreAnnee.id()).isNotNull();
    }

    // --- R3 : fenêtre saisonnière (soft) ---

    @Test
    @DisplayName("R3 : passage 1 dans la fenêtre [15 juin - 31 juillet] est conforme")
    void r3_passage1_dans_fenetre() {
        var resultat = service.verifierFenetreSaisonniere(candidat(1, "2026-06-20"), Protocole.STANDARD);

        assertThat(resultat.estConforme()).isTrue();
    }

    @Test
    @DisplayName("R3 : passage 1 hors fenêtre émet une alerte soft non bloquante")
    void r3_passage1_hors_fenetre() {
        var resultat = service.verifierFenetreSaisonniere(candidat(1, "2026-08-01"), Protocole.STANDARD);

        assertThat(resultat.estConforme()).isFalse();
        assertThat(resultat.estBloquant()).as("R3 jamais bloquant").isFalse();
        assertThat(resultat.messages()).hasSize(1);
        assertThat(resultat.messages().get(0)).contains("hors de la fenêtre");
    }

    @Test
    @DisplayName("R3 : passage 2 dans la fenêtre [15 août - 30 septembre] est conforme")
    void r3_passage2_dans_fenetre() {
        var resultat = service.verifierFenetreSaisonniere(candidat(2, "2026-09-01"), Protocole.STANDARD);

        assertThat(resultat.estConforme()).isTrue();
    }

    @Test
    @DisplayName("R3 : passage 2 hors fenêtre émet une alerte soft")
    void r3_passage2_hors_fenetre() {
        var resultat = service.verifierFenetreSaisonniere(candidat(2, "2026-10-05"), Protocole.STANDARD);

        assertThat(resultat.estConforme()).isFalse();
        assertThat(resultat.estBloquant()).isFalse();
    }

    @Test
    @DisplayName("R3 : sur un site PointFixeRecherche, la règle est muette")
    void r3_recherche_muette() {
        var resultat = service.verifierFenetreSaisonniere(candidat(1, "2026-12-25"), Protocole.RECHERCHE);

        assertThat(resultat.estConforme()).isTrue();
    }

    // --- R4 : intervalle conseillé >= 1 mois (soft) ---

    @Test
    @DisplayName("R4 : deux passages à moins d'un mois émettent une alerte soft")
    void r4_intervalle_trop_court() {
        service.creerPassage(idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null);

        var resultat = service.verifierIntervalleEntrePassages(candidat(2, "2026-07-05"), Protocole.STANDARD);

        assertThat(resultat.estConforme()).isFalse();
        assertThat(resultat.estBloquant()).as("R4 jamais bloquant").isFalse();
        assertThat(resultat.messages().get(0)).contains("Moins d'un mois");
    }

    @Test
    @DisplayName("R4 : deux passages espacés d'au moins un mois sont conformes")
    void r4_intervalle_suffisant() {
        service.creerPassage(idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null);

        var resultat = service.verifierIntervalleEntrePassages(candidat(2, "2026-08-25"), Protocole.STANDARD);

        assertThat(resultat.estConforme()).isTrue();
    }

    @Test
    @DisplayName("R4 : sur un site PointFixeRecherche, la règle est muette")
    void r4_recherche_muette() {
        service.creerPassage(idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null);

        var resultat = service.verifierIntervalleEntrePassages(candidat(2, "2026-07-05"), Protocole.RECHERCHE);

        assertThat(resultat.estConforme()).isTrue();
    }

    @Test
    @DisplayName("verifierProtocole accumule les alertes R3 et R4 (deux messages soft)")
    void verifier_protocole_accumule_r3_et_r4() {
        service.creerPassage(idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null);

        // Passage 2 le 5 juillet : hors fenêtre R3 (attendue 15 août - 30 sept) ET < 1 mois après le 1.
        var resultat = service.verifierProtocole(candidat(2, "2026-07-05"), Protocole.STANDARD);

        assertThat(resultat.messages()).hasSize(2);
        assertThat(resultat.estBloquant()).isFalse();
    }

    // --- Transitions de workflow ---

    @Test
    @DisplayName("avancerStatut fait progresser le passage d'une étape et le persiste")
    void avancer_statut_persiste() {
        Passage passage = service.creerPassage(
                idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null);

        Passage transforme = service.avancerStatut(passage);

        assertThat(transforme.statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(passageDao.findById(passage.id()).orElseThrow().statutWorkflow())
                .isEqualTo(StatutWorkflow.TRANSFORME);
    }

    @Test
    @DisplayName("changerStatut refuse un saut d'étape (Importé → Déposé)")
    void changer_statut_saut_interdit() {
        Passage passage = service.creerPassage(
                idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null);

        assertThatThrownBy(() -> service.changerStatut(passage, StatutWorkflow.DEPOSE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("interdite");
        assertThat(passageDao.findById(passage.id()).orElseThrow().statutWorkflow())
                .as("statut inchangé après refus")
                .isEqualTo(StatutWorkflow.IMPORTE);
    }

    @Test
    @DisplayName("Le passage au statut Déposé horodate deposeLe via l'horloge")
    void deposer_horodate_via_horloge() {
        Passage passage = service.creerPassage(
                idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null);
        Passage pret = service.changerStatut(
                service.changerStatut(
                        service.changerStatut(passage, StatutWorkflow.TRANSFORME), StatutWorkflow.VERIFIE),
                StatutWorkflow.PRET_A_DEPOSER);

        Passage depose = service.changerStatut(pret, StatutWorkflow.DEPOSE);

        assertThat(depose.statutWorkflow()).isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(depose.deposeLe()).isEqualTo("2026-05-31T00:00");
    }

    @Test
    @DisplayName("avancerStatut sur un passage déjà Déposé est refusé (statut terminal)")
    void avancer_statut_terminal_refuse() {
        Passage depose = new Passage(
                service.creerPassage(
                                idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null)
                        .id(),
                1,
                2026,
                "2026-06-20",
                null,
                null,
                null,
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                null,
                null,
                "2026-05-31T00:00",
                idPoint,
                SERIE);

        assertThatThrownBy(() -> service.avancerStatut(depose))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("terminal");
    }

    // --- Pose du verdict ---

    @Test
    @DisplayName("poserVerdict enregistre le verdict de vérification et le persiste")
    void poser_verdict_persiste() {
        Passage passage = service.creerPassage(
                idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null);
        Passage verifie = service.changerStatut(passage, StatutWorkflow.TRANSFORME);
        verifie = service.changerStatut(verifie, StatutWorkflow.VERIFIE);

        Passage juge = service.poserVerdict(verifie, Verdict.DOUTEUX);

        assertThat(juge.verdictVerification()).isEqualTo(Verdict.DOUTEUX);
        assertThat(passageDao.findById(passage.id()).orElseThrow().verdictVerification())
                .isEqualTo(Verdict.DOUTEUX);
    }

    @Test
    @DisplayName("poserVerdict est refusé sur un passage déjà déposé (verdict figé)")
    void poser_verdict_apres_depot_refuse() {
        Passage depose = new Passage(
                service.creerPassage(
                                idPoint, SERIE, 1, LocalDate.of(2026, 6, 20), "21:30:00", "05:15:00", null, null, null)
                        .id(),
                1,
                2026,
                "2026-06-20",
                null,
                null,
                null,
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                null,
                null,
                "2026-05-31T00:00",
                idPoint,
                SERIE);

        assertThatThrownBy(() -> service.poserVerdict(depose, Verdict.A_JETER))
                .isInstanceOf(RegleMetierException.class);
    }

    // --- Suppression (un passage déposé est protégé) ---

    @Test
    @DisplayName("Supprimer efface le passage de la base")
    void supprimer_efface_le_passage() {
        long id = passageDao.insert(candidat(1, "2026-06-20")).id();

        service.supprimer(id);

        assertThat(passageDao.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Supprimer un passage efface aussi sa session par cascade")
    void supprimer_cascade_la_session() {
        long id = passageDao.insert(candidat(1, "2026-06-20")).id();
        SessionDao sessionDao = new SessionDao(source);
        sessionDao.insert(new SessionDEnregistrement(null, "/tmp/nuit", null, null, id));

        service.supprimer(id);

        assertThat(sessionDao.trouverParPassage(id)).isEmpty();
    }

    @Test
    @DisplayName("Supprimer refuse un passage déposé (donnée officielle transmise)")
    void supprimer_refuse_un_passage_depose() {
        Passage depose = passageDao.insert(new Passage(
                null,
                1,
                2026,
                "2026-06-20",
                "21:30:00",
                "05:15:00",
                null,
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                null,
                null,
                "2026-06-21T08:00",
                idPoint,
                SERIE));

        assertThatThrownBy(() -> service.supprimer(depose.id()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("déposé");
        assertThat(passageDao.findById(depose.id())).isPresent();
    }

    @Test
    @DisplayName("Supprimer un passage introuvable lève une RegleMetierException")
    void supprimer_passage_introuvable() {
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    // --- Annulation du dépôt (retour Déposé → Prêt à déposer, validations conservées) ---

    @Test
    @DisplayName("annulerDepot ramène un passage déposé à « Prêt à déposer » et efface deposeLe")
    void annuler_depot_ramene_a_pret_a_deposer() {
        Passage depose = passageDao.insert(new Passage(
                null,
                1,
                2026,
                "2026-06-20",
                "21:30:00",
                "05:15:00",
                null,
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                null,
                null,
                "2026-06-21T08:00",
                idPoint,
                SERIE));

        Passage annule = service.annulerDepot(depose.id());

        assertThat(annule.statutWorkflow()).isEqualTo(StatutWorkflow.PRET_A_DEPOSER);
        assertThat(annule.deposeLe()).as("horodatage de dépôt effacé").isNull();
        Passage persiste = passageDao.findById(depose.id()).orElseThrow();
        assertThat(persiste.statutWorkflow()).isEqualTo(StatutWorkflow.PRET_A_DEPOSER);
        assertThat(persiste.deposeLe()).isNull();
    }

    @Test
    @DisplayName("annulerDepot refuse un passage qui n'est pas déposé")
    void annuler_depot_refuse_passage_non_depose() {
        long id = passageDao.insert(candidat(1, "2026-06-20")).id();

        assertThatThrownBy(() -> service.annulerDepot(id))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("pas déposé");
        assertThat(passageDao.findById(id).orElseThrow().statutWorkflow())
                .as("statut inchangé après refus")
                .isEqualTo(StatutWorkflow.IMPORTE);
    }

    @Test
    @DisplayName("annulerDepot sur un passage introuvable lève une RegleMetierException")
    void annuler_depot_passage_introuvable() {
        assertThatThrownBy(() -> service.annulerDepot(999L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    // --- Modifier le rattachement (E2.S8 : année + n° de passage) ---

    @Test
    @DisplayName("Modifier le rattachement renomme les fichiers sur disque et met à jour la base")
    void modifier_rattachement_renomme_et_met_a_jour() throws IOException {
        long id = seederNuit(2026, 1);

        service.modifierRattachement(id, new Prefixe("040962", 2026, 2, "A1"));

        assertThat(dossier.resolve("Car040962-2026-Pass1-A1")).doesNotExist();
        Path nouvelle = dossier.resolve("Car040962-2026-Pass2-A1");
        assertThat(nouvelle.resolve("bruts").resolve("Car040962-2026-Pass2-A1-PaRec.wav"))
                .exists();
        assertThat(nouvelle.resolve("transformes").resolve("Car040962-2026-Pass2-A1-PaRec_000.wav"))
                .exists();
        assertThat(passageDao.findById(id).orElseThrow().numeroPassage()).isEqualTo(2);
        SessionDEnregistrement session =
                new SessionDao(source).trouverParPassage(id).orElseThrow();
        assertThat(session.cheminRacine()).contains("Pass2").doesNotContain("Pass1");
    }

    @Test
    @DisplayName("Modifier vers un quadruplet déjà pris est refusé (R5) sans rien changer")
    void modifier_rattachement_refuse_si_quadruplet_pris() throws IOException {
        long id = seederNuit(2026, 1);
        seederNuit(2026, 2); // occupe le quadruplet cible

        assertThatThrownBy(() -> service.modifierRattachement(id, new Prefixe("040962", 2026, 2, "A1")))
                .isInstanceOf(RegleMetierException.class);
        assertThat(dossier.resolve("Car040962-2026-Pass1-A1")).exists(); // source intacte
        assertThat(passageDao.findById(id).orElseThrow().numeroPassage()).isEqualTo(1);
    }

    @Test
    @DisplayName("Modifier vers le même quadruplet ne touche à rien")
    void modifier_rattachement_sans_changement_est_neutre() throws IOException {
        long id = seederNuit(2026, 1);

        service.modifierRattachement(id, new Prefixe("040962", 2026, 1, "A1"));

        assertThat(dossier.resolve("Car040962-2026-Pass1-A1")).exists();
        assertThat(passageDao.findById(id).orElseThrow().numeroPassage()).isEqualTo(1);
    }

    @Test
    @DisplayName("Une session en base sans dossier sur disque fait échouer la modif sans toucher la base")
    void modifier_rattachement_dossier_absent_echoue() {
        long id = passageDao.insert(candidat(1, "2026-06-20")).id();
        new SessionDao(source)
                .insert(new SessionDEnregistrement(
                        null, dossier.resolve("Car040962-2026-Pass1-A1").toString(), 0L, 0L, id));

        assertThatThrownBy(() -> service.modifierRattachement(id, new Prefixe("040962", 2026, 2, "A1")))
                .isInstanceOf(java.io.UncheckedIOException.class);
        assertThat(passageDao.findById(id).orElseThrow().numeroPassage()).isEqualTo(1);
    }

    /// Seede une nuit (dossier + fichiers préfixés sur disque, lignes passage/session/original/
    /// séquence en base) pour le carré 040962 / point A1, et renvoie l'identifiant du passage.
    private long seederNuit(int annee, int numero) throws IOException {
        String nom = "Car040962-" + annee + "-Pass" + numero + "-A1";
        Path racine = dossier.resolve(nom);
        Files.createDirectories(racine.resolve("bruts"));
        Files.createDirectories(racine.resolve("transformes"));
        String original = nom + "-PaRec.wav";
        String sequence = nom + "-PaRec_000.wav";
        Files.writeString(racine.resolve("bruts").resolve(original), "o");
        Files.writeString(racine.resolve("transformes").resolve(sequence), "s");
        long id = passageDao
                .insert(new Passage(
                        null,
                        numero,
                        annee,
                        "2026-06-20",
                        "21:00:00",
                        "05:00:00",
                        null,
                        StatutWorkflow.TRANSFORME,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, racine.toString(), 100L, 50L, id))
                .id();
        EnregistrementOriginal orig = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(
                        null,
                        original,
                        racine.resolve("bruts").resolve(original).toString(),
                        5.0,
                        384000,
                        null,
                        idSession));
        new SequenceDao(source)
                .insert(new SequenceDEcoute(
                        null,
                        sequence,
                        orig.id(),
                        0,
                        0.0,
                        5.0,
                        racine.resolve("transformes").resolve(sequence).toString(),
                        false,
                        idSession));
        return id;
    }
}
