package fr.univ_amu.iut.perf.outils;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntSupplier;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Banc **micro-benchmark de la couche données** (#29) : peuple une base de mesure
/// ([GenerateurJeuDeDonnees]) puis chronomètre les deux opérations cibles de l'objectif O5 et
/// imprime leur `EXPLAIN QUERY PLAN` :
///
///  - **sélection** des ~4031 observations d'un jeu de résultats
///    ([ObservationDao#findByResults]) — cible < 100 ms ;
///  - **tri/filtre** des ~1000 passages de l'utilisateur
///    ([ServiceMultisite#listerPassages]) — cible < 200 ms.
///
/// Chaque opération est mesurée **à froid** (1ᵉʳ appel, JIT non chauffé — comme le demande le brief)
/// puis **à chaud** (médiane sur [#ITERATIONS] itérations). Chrono simple (`System.nanoTime`), aucune
/// dépendance ajoutée : les cibles du brief sont des ordres de grandeur, le banc sert à les affiner et
/// à comparer **avant / après index** (#28). À relancer sur une **machine IUT, JIT froid** (cf.
/// `docs/benchmarks`). Aucune valeur n'est versionnée ici : seul l'outil l'est.
public final class BancMesure {

    private static final int ITERATIONS = 20;
    private static final String ID_UTILISATEUR = "bench-enseignant";

    private BancMesure() {}

    public static void main(String[] args) throws IOException {
        Path racine = Path.of(System.getProperty(
                "vigiechiro.workspace",
                Path.of(System.getProperty("java.io.tmpdir"), "vigiechiro-bench")
                        .toString()));
        int nbPassages = Integer.getInteger("perf.passages", 1000);
        int nbObservations = Integer.getInteger("perf.observations", 4031);

        Files.createDirectories(racine);
        Files.deleteIfExists(racine.resolve(Workspace.FICHIER_BASE));
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(racine));
        new MigrationSchema(source).migrer();
        GenerateurJeuDeDonnees.peupler(source, nbPassages, nbObservations);

        ObservationDao observationDao = new ObservationDao(source);
        long idResultats = premierResultats(source);
        ServiceMultisite multisite = new ServiceMultisite(
                new SiteDao(source),
                new PointDao(source),
                new PassageDao(source),
                // #1338 : l'état d'analyse est lu en masse à chaque listage. Le banc les branche pour
                // mesurer le coût réel de la vue, pas une version amputée de deux tables.
                new ReleveTraitementDao(source),
                new ResultatsIdentificationDao(source),
                new HorlogeFigee(LocalDate.of(2026, 6, 4)));

        System.out.println("=== Banc de mesure couche données (#29) — base " + racine + " ===");
        System.out.println("Mesures à froid (1er appel) puis à chaud (médiane de " + ITERATIONS + ").");
        mesurer(
                "Sélection observations (findByResults, " + nbObservations + ")",
                () -> observationDao.findByResults(idResultats).size());
        mesurer(
                "Tri/filtre passages (multisite, verdict OK, " + nbPassages + ")",
                () -> multisite
                        .listerPassages(
                                ID_UTILISATEUR, FiltresMultisite.parVerdict(Verdict.OK), TriMultisite.PAR_VERDICT)
                        .size());

        System.out.println();
        System.out.println("=== EXPLAIN QUERY PLAN (un SCAN signale l'absence d'index — cf. #28) ===");
        plan(
                source,
                "observation / results_id",
                "SELECT * FROM observation WHERE results_id = " + idResultats + " ORDER BY id");
        plan(source, "passage / point_id", "SELECT * FROM passage WHERE point_id = 1 ORDER BY year, passage_number");
    }

    /// Chronomètre `operation` (qui renvoie le nombre de lignes, pour empêcher l'élimination du
    /// calcul) à froid puis à chaud, et imprime une ligne de tableau.
    private static void mesurer(String libelle, IntSupplier operation) {
        long debutFroid = System.nanoTime();
        int lignes = operation.getAsInt();
        double froidMs = (System.nanoTime() - debutFroid) / 1e6;

        long[] tempsNs = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long debut = System.nanoTime();
            operation.getAsInt();
            tempsNs[i] = System.nanoTime() - debut;
        }
        Arrays.sort(tempsNs);
        double chaudMs = tempsNs[ITERATIONS / 2] / 1e6;

        System.out.printf(
                Locale.ROOT,
                "  %-52s %5d lignes   froid %8.2f ms   chaud(méd.) %7.3f ms%n",
                libelle,
                lignes,
                froidMs,
                chaudMs);
    }

    /// Imprime le plan d'exécution SQLite de `sql` (colonne `detail` d'`EXPLAIN QUERY PLAN`).
    private static void plan(SourceDeDonnees source, String libelle, String sql) {
        System.out.println("  " + libelle + " :");
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("EXPLAIN QUERY PLAN " + sql)) {
            while (rs.next()) {
                System.out.println("    " + rs.getString("detail"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("EXPLAIN QUERY PLAN impossible : " + sql, e);
        }
    }

    private static long premierResultats(SourceDeDonnees source) {
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("SELECT id FROM identification_results ORDER BY id LIMIT 1")) {
            if (!rs.next()) {
                throw new IllegalStateException("Aucun jeu de résultats généré : lancez d'abord le générateur.");
            }
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new DataAccessException("Lecture du jeu de résultats impossible", e);
        }
    }
}
