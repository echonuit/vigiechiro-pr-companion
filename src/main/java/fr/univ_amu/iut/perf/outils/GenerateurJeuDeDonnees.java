package fr.univ_amu.iut.perf.outils;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Générateur de **jeux de données de benchmark** (#29) : peuple une base SQLite avec un grand
/// nombre de **passages** (cible O5 « tri/filtre ~1000 passages ») et d'**observations** (cible O5
/// « sélection ~4031 observations »), pour alimenter le banc de mesure (#29) et mesurer l'effet des
/// index (#28). Mêmes conventions que les outils `Capture*` : DAO réels sur une base de la
/// `SourceDeDonnees`.
///
/// **Déterministe** (aucun aléa) → base reproductible : même nombre de lignes ⇒ même contenu.
///
/// Paramétrable par propriétés système :
///  - `vigiechiro.workspace` : dossier de la base (défaut : `<tmp>/vigiechiro-bench`) ;
///  - `perf.passages` : nombre de passages (défaut 1000) ;
///  - `perf.observations` : nombre d'observations (défaut 4031) ;
///  - `perf.sites` : nombre de **carrés** (défaut 40), chacun portant [#NB_POINTS] points. C'est la
///    dimension qui porte les lectures répétées : la garder à 1 rend le banc aveugle.
///
/// La base du workspace est **réécrite à neuf** au lancement, pour garantir la reproductibilité.
public final class GenerateurJeuDeDonnees {

    private static final String ID_UTILISATEUR = "bench-enseignant";
    private static final String ENREGISTREUR = "9999999";
    private static final String TAXON = "Pippip"; // taxon pré-semé (V02__seed_taxons.sql)
    private static final int PASSAGES_DEFAUT = 1000;
    private static final int OBSERVATIONS_DEFAUT = 4031;
    private static final int NB_POINTS = 10; // points PAR SITE ; les passages s'y répartissent

    /// Nombre de **carrés** du jeu par défaut.
    ///
    /// Il valait **1** jusqu'à la clôture du chantier des lectures répétées, et cette valeur rendait
    /// le banc **aveugle à ce qu'il était censé mesurer**. Les écrans lançaient une requête par site puis
    /// une par point : sur un site et dix points, cela faisait onze requêtes, invisibles. Le relevé
    /// annonçait 18 ms pour mille passages, sous une cible de 200, pendant qu'un coordinateur
    /// départemental - cent cinquante carrés - en payait plus de quatre cents.
    ///
    /// **Le banc faisait varier la mauvaise dimension** : il montait les passages en gardant la
    /// topologie constante, quand le coût vivait dans la topologie. Un jeu d'essai qui ne fait pas
    /// varier ce qui porte le défaut ne peut pas le voir, et son vert se lit comme une garantie.
    private static final int NB_SITES_DEFAUT = 40;
    private static final int ANNEE_BASE = 2020;

    private static final StatutWorkflow[] STATUTS = StatutWorkflow.values();
    private static final Verdict[] VERDICTS = Verdict.values();

    private GenerateurJeuDeDonnees() {}

    /// Bilan d'une génération (nombres de lignes effectivement insérées).
    public record Bilan(int passages, int observations) {}

    /// L'espace de travail du banc : celui qu'on lui impose, ou un dossier temporaire à nous seuls.
    ///
    /// `createTempDirectory` crée en `0700` là où le système le permet, quand `java.io.tmpdir` reste
    /// lisible par tout utilisateur local (#4509). Le nom cesse d'être stable d'une passe à l'autre,
    /// et cela ne coûte rien : le banc réinitialise son dossier à chaque fois. Qui veut un chemin
    /// fixe le donne par `-Dvigiechiro.workspace`.
    private static Path espaceDeTravail() throws IOException {
        String impose = System.getProperty("vigiechiro.workspace");
        return impose != null ? Path.of(impose) : Files.createTempDirectory("vigiechiro-bench-");
    }

    public static void main(String[] args) throws IOException {
        Path racine = espaceDeTravail();
        int nbPassages = Integer.getInteger("perf.passages", PASSAGES_DEFAUT);
        int nbObservations = Integer.getInteger("perf.observations", OBSERVATIONS_DEFAUT);

        // Base réécrite à neuf : reproductibilité (même paramètres ⇒ même base).
        Files.createDirectories(racine);
        Files.deleteIfExists(racine.resolve(Workspace.FICHIER_BASE));
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(racine));
        new MigrationSchema(source).migrer();

        long debut = System.nanoTime();
        Bilan bilan = peupler(source, nbPassages, nbObservations);
        long ms = (System.nanoTime() - debut) / 1_000_000;

        System.out.println("Base de benchmark écrite : " + racine.resolve(Workspace.FICHIER_BASE));
        System.out.println("  passages     : " + bilan.passages());
        System.out.println("  observations : " + bilan.observations());
        System.out.println("  génération   : " + ms + " ms");
    }

    /// Peuple `source` avec `nbPassages` passages et `nbObservations` observations sur un socle de
    /// clés étrangères minimal (un utilisateur, un enregistreur, un site, [#NB_POINTS] points).
    public static Bilan peupler(SourceDeDonnees source, int nbPassages, int nbObservations) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Banc de mesure (demo)"));
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));
        List<Long> points = creerPoints(source);
        int passages = genererPassages(source, points, nbPassages);
        // Les observations se rattachent à l'**un des passages générés** (pas un passage de plus) : le
        // compte rendu reflète ainsi le nombre exact de lignes `passage` de la base.
        long idPassage =
                new PassageDao(source).findByPoint(points.get(0)).get(0).id();
        int observations = genererObservations(source, idPassage, nbObservations);
        return new Bilan(passages, observations);
    }

    /// `perf.sites` carrés de [#NB_POINTS] points chacun ; renvoie les identifiants des points.
    private static List<Long> creerPoints(SourceDeDonnees source) {
        int nbSites = Integer.getInteger("perf.sites", NB_SITES_DEFAUT);
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        List<Long> points = new ArrayList<>(nbSites * NB_POINTS);
        for (int s = 0; s < nbSites; s++) {
            Site site = siteDao.insert(new Site(
                    null,
                    String.format("%06d", 640380 + s),
                    "Carré " + s + " du banc",
                    Protocole.STANDARD,
                    null,
                    "2026-01-01",
                    ID_UTILISATEUR));
            for (int i = 0; i < NB_POINTS; i++) {
                points.add(pointDao.insert(new PointDEcoute(
                                null, "P" + i, 43.5 + s * 0.01 + i * 0.001, 5.4 + i * 0.001, null, site.id()))
                        .id());
            }
        }
        return points;
    }

    /// Insère `nb` passages **en une seule transaction** (JDBC brut, comme `CaptureValidation` pour ses
    /// sites/points) : 1000 `INSERT` auto-commités feraient autant de `fsync`. Les triplets
    /// `(point, année, n° passage)` sont rendus uniques par construction ; statut et verdict tournent
    /// pour donner une distribution filtrable (cible O5 tri/filtre).
    private static int genererPassages(SourceDeDonnees source, List<Long> points, int nb) {
        String sql = "INSERT INTO passage (passage_number, year, recording_date, start_time, end_time,"
                + " acquisition_params, workflow_status, verification_verdict, comment, weather_data,"
                + " deposited_at, point_id, recorder_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection cx = source.getConnection()) {
            cx.setAutoCommit(false);
            try (PreparedStatement ps = cx.prepareStatement(sql)) {
                for (int i = 0; i < nb; i++) {
                    Long point = points.get(i % points.size());
                    int combo = i / points.size();
                    int annee = ANNEE_BASE + combo % 10;
                    int numeroPassage = 1 + combo / 10; // garde (point, année, n°) unique
                    Verdict verdict = VERDICTS[i % VERDICTS.length];
                    ps.setInt(1, numeroPassage);
                    ps.setInt(2, annee);
                    ps.setString(3, annee + "-06-22");
                    ps.setString(4, "20:25:00");
                    ps.setString(5, "07:47:00");
                    ps.setString(6, null);
                    ps.setString(7, STATUTS[i % STATUTS.length].libelle());
                    ps.setString(8, verdict.libelle());
                    ps.setString(9, null);
                    ps.setString(10, null);
                    ps.setString(11, null);
                    ps.setLong(12, point);
                    ps.setString(13, ENREGISTREUR);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            cx.commit();
            return nb;
        } catch (SQLException e) {
            throw new DataAccessException("Échec de la génération des passages de benchmark", e);
        }
    }

    /// Sur le passage `idPassage` (l'un des passages générés), crée la chaîne de clés étrangères d'une
    /// nuit (session → original → séquence) puis un jeu de résultats, et insère `nb` observations qui le
    /// référencent (cible O5 : sélection par `results_id` via `ObservationDao.findByResults`). Insertion
    /// en lot (`insererTout`).
    private static int genererObservations(SourceDeDonnees source, long idPassage, int nb) {
        SessionDEnregistrement session =
                new SessionDao(source).insert(new SessionDEnregistrement(null, "bench/session", null, null, idPassage));
        EnregistrementOriginal original = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(
                        null, "bench.wav", "bench/bruts/bench.wav", 5.0, 384000, null, session.id()));
        SequenceDEcoute sequence = new SequenceDao(source)
                .insert(new SequenceDEcoute(
                        null,
                        "bench_000.wav",
                        original.id(),
                        0,
                        0.0,
                        5.0,
                        "bench/transformes/bench_000.wav",
                        false,
                        session.id()));
        ResultatsIdentification resultats = new ResultatsIdentificationDao(source)
                .insert(new ResultatsIdentification(
                        null, "bench-observations.csv", "Brut", "2026-06-22T10:00:00", idPassage));

        List<Observation> observations = new ArrayList<>(nb);
        for (int i = 0; i < nb; i++) {
            double debut = i % 5; // timings variés, déterministes
            observations.add(new Observation(
                    null,
                    sequence.id(),
                    debut,
                    debut + 0.5,
                    18 + i % 100,
                    TAXON,
                    0.5 + (i % 50) / 100.0,
                    null,
                    null,
                    null,
                    null,
                    false,
                    ModeValidation.NON_VALIDE,
                    resultats.id(),
                    false,
                    null,
                    null,
                    null,
                    null,
                    null));
        }
        return new ObservationDao(source).insererTout(observations);
    }
}
