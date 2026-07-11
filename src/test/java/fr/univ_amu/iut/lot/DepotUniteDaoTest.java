package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Suivi du dépôt par unité (table `depot_unite`, #981) : pose **idempotente** du plan (clé de la
/// reprise #982), avancement au fil de l'eau, unités restantes, condition « toutes déposées » et
/// cascade à la suppression du passage. Base SQLite jetable (`@TempDir` + [MigrationSchema]).
class DepotUniteDaoTest {

    private static final String MAINTENANT = "2026-07-11T12:00:00";

    @TempDir
    Path dossier;

    private DepotUniteDao dao;
    private PassageDao passageDao;
    private Long idPassage;
    private Long idPoint;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        // Chaîne de parents requise par les FK : user -> site -> point, l'enregistreur, puis le passage.
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", "u-1"));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur("1925492", "V1.01", null));
        passageDao = new PassageDao(source);
        idPassage = insererPassage(2).id();
        dao = new DepotUniteDao(source);
    }

    private Passage insererPassage(int numero) {
        return passageDao.insert(new Passage(
                null,
                numero,
                2026,
                "2026-04-22",
                "20:25:00",
                "07:47:00",
                null,
                StatutWorkflow.PRET_A_DEPOSER,
                null,
                null,
                null,
                null,
                idPoint,
                "1925492"));
    }

    @Test
    @DisplayName("synchroniserPlan() pose le plan « à déposer », dans l'ordre, sans doublon au re-appel")
    void plan_pose_puis_idempotent() {
        List<DepotUnite> plan = List.of(
                DepotUnite.aDeposer(idPassage, "Car-1.zip", TypeDepotUnite.ZIP, MAINTENANT),
                DepotUnite.aDeposer(idPassage, "Car-2.zip", TypeDepotUnite.ZIP, MAINTENANT));

        dao.synchroniserPlan(idPassage, plan);
        dao.synchroniserPlan(idPassage, plan); // reprise : même plan, aucune duplication

        List<DepotUnite> suivies = dao.parPassage(idPassage);
        assertThat(suivies).extracting(DepotUnite::identifiantUnite).containsExactly("Car-1.zip", "Car-2.zip");
        assertThat(suivies).allSatisfy(u -> {
            assertThat(u.statut()).isEqualTo(StatutDepotUnite.A_DEPOSER);
            assertThat(u.type()).isEqualTo(TypeDepotUnite.ZIP);
        });
    }

    @Test
    @DisplayName("Reprise : re-poser le plan conserve le statut des unités déjà suivies (déposées comprises)")
    void replanifier_conserve_les_statuts_existants() {
        dao.synchroniserPlan(
                idPassage,
                List.of(
                        DepotUnite.aDeposer(idPassage, "Car-1.zip", TypeDepotUnite.ZIP, MAINTENANT),
                        DepotUnite.aDeposer(idPassage, "Car-2.zip", TypeDepotUnite.ZIP, MAINTENANT)));
        DepotUnite premiere = dao.parPassage(idPassage).getFirst();
        dao.mettreAJour(premiere.id(), StatutDepotUnite.DEPOSE, "obj-123", null, MAINTENANT);

        // Interruption puis reprise : le même plan est re-posé.
        dao.synchroniserPlan(
                idPassage,
                List.of(
                        DepotUnite.aDeposer(idPassage, "Car-1.zip", TypeDepotUnite.ZIP, MAINTENANT),
                        DepotUnite.aDeposer(idPassage, "Car-2.zip", TypeDepotUnite.ZIP, MAINTENANT)));

        List<DepotUnite> suivies = dao.parPassage(idPassage);
        assertThat(suivies.getFirst().statut()).isEqualTo(StatutDepotUnite.DEPOSE);
        assertThat(suivies.getFirst().fichierIdDistant()).isEqualTo("obj-123");
        assertThat(dao.restantes(idPassage))
                .extracting(DepotUnite::identifiantUnite)
                .containsExactly("Car-2.zip");
    }

    @Test
    @DisplayName("Une unité sortie du plan (archives régénérées autrement) est retirée du suivi")
    void replanifier_retire_les_unites_hors_plan() {
        dao.synchroniserPlan(
                idPassage,
                List.of(
                        DepotUnite.aDeposer(idPassage, "Car-1.zip", TypeDepotUnite.ZIP, MAINTENANT),
                        DepotUnite.aDeposer(idPassage, "Car-2.zip", TypeDepotUnite.ZIP, MAINTENANT)));

        dao.synchroniserPlan(
                idPassage, List.of(DepotUnite.aDeposer(idPassage, "Car-1.zip", TypeDepotUnite.ZIP, MAINTENANT)));

        assertThat(dao.parPassage(idPassage))
                .extracting(DepotUnite::identifiantUnite)
                .containsExactly("Car-1.zip");
    }

    @Test
    @DisplayName("restantes() : tout sauf « depose » — une unité laissée « en_cours » est à re-tenter")
    void restantes_couvre_en_cours_et_echec() {
        dao.synchroniserPlan(
                idPassage,
                List.of(
                        DepotUnite.aDeposer(idPassage, "a.wav", TypeDepotUnite.WAV, MAINTENANT),
                        DepotUnite.aDeposer(idPassage, "b.wav", TypeDepotUnite.WAV, MAINTENANT),
                        DepotUnite.aDeposer(idPassage, "c.wav", TypeDepotUnite.WAV, MAINTENANT),
                        DepotUnite.aDeposer(idPassage, "d.wav", TypeDepotUnite.WAV, MAINTENANT)));
        List<DepotUnite> suivies = dao.parPassage(idPassage);
        dao.mettreAJour(suivies.get(0).id(), StatutDepotUnite.DEPOSE, "obj-1", null, MAINTENANT);
        dao.mettreAJour(suivies.get(1).id(), StatutDepotUnite.EN_COURS, null, null, MAINTENANT);
        dao.mettreAJour(suivies.get(2).id(), StatutDepotUnite.ECHEC, null, "coupure réseau", MAINTENANT);

        assertThat(dao.restantes(idPassage))
                .extracting(DepotUnite::identifiantUnite)
                .containsExactly("b.wav", "c.wav", "d.wav");
        assertThat(dao.toutesDeposees(idPassage)).isFalse();
    }

    @Test
    @DisplayName("toutesDeposees() : vrai seulement avec un plan entièrement « depose », faux sans plan")
    void toutes_deposees() {
        assertThat(dao.toutesDeposees(idPassage))
                .as("aucun plan : aucun dépôt entamé")
                .isFalse();

        dao.synchroniserPlan(
                idPassage, List.of(DepotUnite.aDeposer(idPassage, "a.wav", TypeDepotUnite.WAV, MAINTENANT)));
        assertThat(dao.toutesDeposees(idPassage)).isFalse();

        dao.mettreAJour(dao.parPassage(idPassage).getFirst().id(), StatutDepotUnite.DEPOSE, "obj-1", null, MAINTENANT);
        assertThat(dao.toutesDeposees(idPassage)).isTrue();
    }

    @Test
    @DisplayName("mettreAJour() consigne l'échec (message) puis l'efface au succès suivant")
    void mise_a_jour_du_message_d_erreur() {
        dao.synchroniserPlan(
                idPassage, List.of(DepotUnite.aDeposer(idPassage, "a.wav", TypeDepotUnite.WAV, MAINTENANT)));
        long id = dao.parPassage(idPassage).getFirst().id();

        dao.mettreAJour(id, StatutDepotUnite.ECHEC, null, "HTTP 503", MAINTENANT);
        assertThat(dao.parPassage(idPassage).getFirst().messageErreur()).isEqualTo("HTTP 503");

        dao.mettreAJour(id, StatutDepotUnite.DEPOSE, "obj-9", null, "2026-07-11T12:05:00");
        DepotUnite deposee = dao.parPassage(idPassage).getFirst();
        assertThat(deposee.messageErreur()).isNull();
        assertThat(deposee.fichierIdDistant()).isEqualTo("obj-9");
        assertThat(deposee.majLe()).isEqualTo("2026-07-11T12:05:00");
    }

    @Test
    @DisplayName("Supprimer le passage supprime son suivi de dépôt (cascade)")
    void cascade_a_la_suppression_du_passage() {
        dao.synchroniserPlan(
                idPassage, List.of(DepotUnite.aDeposer(idPassage, "a.wav", TypeDepotUnite.WAV, MAINTENANT)));

        passageDao.delete(idPassage);

        assertThat(dao.parPassage(idPassage)).isEmpty();
    }

    @Test
    @DisplayName("Les plans de deux passages sont indépendants")
    void plans_independants_par_passage() {
        Long autre = insererPassage(3).id();
        dao.synchroniserPlan(
                idPassage, List.of(DepotUnite.aDeposer(idPassage, "a.wav", TypeDepotUnite.WAV, MAINTENANT)));
        dao.synchroniserPlan(autre, List.of(DepotUnite.aDeposer(autre, "b.wav", TypeDepotUnite.WAV, MAINTENANT)));

        assertThat(dao.parPassage(idPassage))
                .extracting(DepotUnite::identifiantUnite)
                .containsExactly("a.wav");
        assertThat(dao.parPassage(autre))
                .extracting(DepotUnite::identifiantUnite)
                .containsExactly("b.wav");
    }
}
