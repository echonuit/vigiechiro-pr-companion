package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **Le cycle de vie du traitement serveur, de bout en bout** (clôture de l'EPIC #1259).
///
/// Les tests unitaires éprouvent chaque brique isolément ; celui-ci vérifie qu'elles s'emboîtent, sur la
/// **vraie base** (SQLite jetable) et avec le **vrai parseur** (les réponses du serveur sont du JSON, pas
/// des objets fabriqués) : une nuit passe de « jamais calculée » à « en cours », puis « terminée », et le
/// relevé la suit.
///
/// Seul le transport HTTP est simulé — c'est la frontière de ce qu'on peut tester sans réseau. Le refus
/// « Already » du serveur, lui, est éprouvé là où vit le transport (`TraitementVigieChiroTest`) : sa
/// visibilité reste volontairement confinée au paquet `commun.api`.
class CycleTraitementIntegrationTest {

    private static final String PARTICIPATION = "6a4961f587bc8dba39481180";

    @TempDir
    Path racine;

    private ClientVigieChiro client;
    private SuiviTraitement suivi;
    private ReleveTraitementDao releves;

    private Long idPassage;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(racine.resolve("ws")));
        new MigrationSchema(source).migrer();
        // Le relevé est rattaché au passage (clé étrangère, cascade à la suppression) : il faut donc une
        // vraie nuit en base — site, point, enregistreur, passage.
        idPassage = seedPassage(source);
        LienVigieChiroDao liens = new LienVigieChiroDao(source);
        // La nuit a été déposée par l'application : son passage est lié à une participation.
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), PARTICIPATION));
        releves = new ReleveTraitementDao(source);
        client = mock(ClientVigieChiro.class);
        suivi = new SuiviTraitement(
                new TraitementVigieChiro(client), liens, releves, Horloge.figeeAu(LocalDate.of(2026, 7, 13)));
    }

    /// Une nuit déposée : site, point, enregistreur, passage. (Fixture à mutualiser un jour : #1258.)
    private static Long seedPassage(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", "u-1"));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "Z41", 43.5, 5.4, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur("1925492", "V1.01", null));
        return new PassageDao(source)
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-07-12",
                        "20:25:00",
                        "07:47:00",
                        null,
                        StatutWorkflow.DEPOSE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        "1925492"))
                .id();
    }

    @Test
    @DisplayName("une nuit jamais calculée : le relevé le dit, et le cache s'en souvient (affichable hors ligne)")
    void jamais_calculee() {
        serveurRepond(Traitement.absent());

        Traitement releve = suivi.relever(idPassage);

        assertThat(releve.estInconnu()).isTrue();
        assertThat(suivi.dernierReleve(idPassage))
                .as("le cache retient même l'absence de traitement : l'écran n'a pas à rester muet")
                .isPresent();
    }

    @Test
    @DisplayName("le cycle complet : PLANIFIE → EN_COURS → FINI, chaque relevé écrasant le précédent")
    void cycle_complet() {
        serveurRepond(new Traitement(EtatTraitement.PLANIFIE, "2026-07-13T08:00:00+00:00", null, null, null, null));
        assertThat(suivi.relever(idPassage).enAttente()).isTrue();

        serveurRepond(new Traitement(EtatTraitement.EN_COURS, null, "2026-07-13T08:10:00+00:00", null, null, null));
        assertThat(suivi.relever(idPassage).etat()).isEqualTo(EtatTraitement.EN_COURS);

        serveurRepond(new Traitement(
                EtatTraitement.FINI, null, "2026-07-13T08:10:00+00:00", "2026-07-13T09:05:00+00:00", null, null));
        Traitement fin = suivi.relever(idPassage);

        assertThat(fin.resultatsDisponibles())
                .as("FINI : c'est le seul état où les observations existent")
                .isTrue();
        ReleveTraitement dernier = suivi.dernierReleve(idPassage).orElseThrow();
        assertThat(dernier.traitement().etat()).isEqualTo(EtatTraitement.FINI);
        assertThat(dernier.releveLe()).isNotBlank();
        assertThat(releves.compter())
                .as("un seul relevé par passage : on retient où on en est")
                .isEqualTo(1);
    }

    /// Le serveur répond ce bloc `traitement` sur `GET /participations/{id}` — parsé pour de vrai.
    private void serveurRepond(Traitement traitement) {
        when(client.participation(PARTICIPATION))
                .thenReturn(Optional.of(
                        new ParticipationDetail(PARTICIPATION, "e1", "Z41", null, null, null, Map.of(), traitement)));
    }
}
