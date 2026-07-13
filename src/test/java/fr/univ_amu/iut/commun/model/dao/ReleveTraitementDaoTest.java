package fr.univ_amu.iut.commun.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Dernier état connu du traitement serveur (table `participation_traitement`, #1262) : un relevé
/// horodaté par passage, **écrasé** à chaque rafraîchissement, lisible hors connexion. Base SQLite
/// jetable (`@TempDir` + [MigrationSchema]).
class ReleveTraitementDaoTest {

    private static final String PARTICIPATION = "6a4961f587bc8dba39481180";

    @TempDir
    Path dossier;

    private ReleveTraitementDao dao;
    private PassageDao passageDao;
    private Long idPassage;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier.resolve("ws")));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", "u-1"));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur("1925492", "V1.01", null));
        passageDao = new PassageDao(source);
        idPassage = passageDao.insert(passage(idPoint)).id();
        dao = new ReleveTraitementDao(source);
    }

    @Test
    @DisplayName("aucun relevé au départ : on ne sait rien du traitement tant qu'on ne l'a pas demandé")
    void aucun_releve_au_depart() {
        assertThat(dao.pour(idPassage)).isEmpty();
    }

    @Test
    @DisplayName("enregistrer puis relire : l'état, les 3 dates, la trace d'erreur et le compteur d'essais")
    void enregistrer_et_relire() {
        Traitement traitement = new Traitement(
                EtatTraitement.RETRY,
                "2026-07-05T08:00:00+00:00",
                "2026-07-05T08:12:00+00:00",
                "2026-07-05T08:20:00+00:00",
                "Traceback: boum",
                1);

        dao.enregistrer(new ReleveTraitement(idPassage, PARTICIPATION, traitement, "2026-07-13T09:30:00"));

        ReleveTraitement relu = dao.pour(idPassage).orElseThrow();
        assertThat(relu.participationId()).isEqualTo(PARTICIPATION);
        assertThat(relu.releveLe()).isEqualTo("2026-07-13T09:30:00");
        assertThat(relu.traitement()).isEqualTo(traitement);
        assertThat(relu.traitement().enAttente()).isTrue();
    }

    @Test
    @DisplayName("le relevé est ÉCRASÉ à chaque rafraîchissement : on retient où en est le calcul, pas son passé")
    void releve_ecrase() {
        dao.enregistrer(new ReleveTraitement(idPassage, PARTICIPATION, enCours(), "2026-07-13T09:30:00"));

        dao.enregistrer(new ReleveTraitement(idPassage, PARTICIPATION, fini(), "2026-07-13T10:15:00"));

        ReleveTraitement relu = dao.pour(idPassage).orElseThrow();
        assertThat(relu.traitement().etat()).isEqualTo(EtatTraitement.FINI);
        assertThat(relu.releveLe()).isEqualTo("2026-07-13T10:15:00");
        assertThat(dao.compter()).as("un seul relevé par passage").isEqualTo(1);
    }

    @Test
    @DisplayName("participation jamais calculée : le relevé « absent » se persiste et se relit tel quel")
    void releve_d_un_traitement_absent() {
        dao.enregistrer(new ReleveTraitement(idPassage, PARTICIPATION, Traitement.absent(), "2026-07-13T09:30:00"));

        Traitement relu = dao.pour(idPassage).orElseThrow().traitement();

        assertThat(relu).isEqualTo(Traitement.absent());
        assertThat(relu.estInconnu()).isTrue();
        assertThat(relu.retry())
                .as("null en base doit rester null, surtout pas 0")
                .isNull();
    }

    @Test
    @DisplayName("supprimer le passage emporte son relevé (cascade) : pas de cache orphelin")
    void cascade_a_la_suppression_du_passage() {
        dao.enregistrer(new ReleveTraitement(idPassage, PARTICIPATION, fini(), "2026-07-13T10:15:00"));

        passageDao.delete(idPassage);

        assertThat(dao.pour(idPassage)).isEmpty();
    }

    private static Traitement enCours() {
        return new Traitement(EtatTraitement.EN_COURS, null, "2026-07-13T09:00:00+00:00", null, null, null);
    }

    private static Traitement fini() {
        return new Traitement(
                EtatTraitement.FINI, null, "2026-07-13T09:00:00+00:00", "2026-07-13T10:05:00+00:00", null, null);
    }

    private static Passage passage(Long idPoint) {
        return new Passage(
                null,
                1,
                2026,
                "2026-04-22",
                "20:25:00",
                "07:47:00",
                null,
                StatutWorkflow.DEPOSE,
                null,
                null,
                null,
                null,
                idPoint,
                "1925492");
    }
}
