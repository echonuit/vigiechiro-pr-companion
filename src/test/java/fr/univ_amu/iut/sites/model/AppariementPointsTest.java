package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.LocalitesDuSite;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Apparier les points saisis **avant connexion** avec les localités distantes du même endroit (#3750).
///
/// Le carré d'essai est relié à la plateforme dans chaque cas : sans lien, il n'y a rien à confronter.
class AppariementPointsTest {

    private static final String ID_USER = "u-1";
    private static final String CARRE = "640380";
    private static final String OBJECTID = "6a4961f587bc8dba39481180";

    /// Position de référence du point local. Les écarts des cas sont calculés à partir d'elle :
    /// un degré de latitude vaut ~111 km, donc `1e-4°` ≈ 11 m et `5e-5°` ≈ 5,6 m.
    private static final double LATITUDE = 43.52;

    private static final double LONGITUDE = 5.46;

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ServiceSites service;
    private LienVigieChiroDao liens;
    private ClientVigieChiro client;
    private AppariementPoints appariement;
    private long idSite;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        PointDao pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);
        service = new ServiceSites(
                new SiteDao(source),
                pointDao,
                passageDao,
                new HorlogeFigee(LocalDate.of(2026, 6, 1)),
                new PointCommuneDao(source),
                () -> {});
        liens = new LienVigieChiroDao(source);
        client = mock(ClientVigieChiro.class);
        appariement = new AppariementPoints(client, service, liens, passageDao);
    }

    @Test
    @DisplayName("#3750 : une localité distante au même endroit sous un autre nom est FUSIONNABLE")
    void une_localite_au_meme_endroit_est_fusionnable() {
        semerPoint("A1", LATITUDE, LONGITUDE);
        relier();
        // ~5,6 m : sous le seuil commun, donc le même endroit, mais le nom diffère.
        distantesSont(localite("Z41", LATITUDE + 5e-5, LONGITUDE));

        assertThat(verdicts())
                .singleElement()
                .satisfies(verdict -> assertThat(verdict)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                                AppariementPoints.Verdict.Fusionnable.class))
                        .satisfies(fusionnable -> {
                            assertThat(fusionnable.distante().code())
                                    .as("c'est le nom DISTANT que le point local devra adopter")
                                    .isEqualTo("Z41");
                            assertThat(fusionnable.ecartMetres()).isLessThan(15.0);
                        }));
    }

    @Test
    @DisplayName("#3750 : sans localité au même endroit, le point est NOUVEAU pour la plateforme")
    void sans_localite_proche_aucun_candidat() {
        semerPoint("A1", LATITUDE, LONGITUDE);
        relier();
        // ~2,2 km : très au delà du seuil, et même du seuil de protocole.
        distantesSont(localite("Z41", LATITUDE + 2e-2, LONGITUDE));

        assertThat(verdicts())
                .as("sa suite naturelle est la publication, pas la fusion")
                .singleElement()
                .isInstanceOf(AppariementPoints.Verdict.AucunCandidat.class);
    }

    @Test
    @DisplayName("#3750 : deux candidates dans le rayon, on NE CHOISIT PAS à la place de l'utilisateur")
    void deux_candidates_ne_se_tranchent_pas() {
        semerPoint("A1", LATITUDE, LONGITUDE);
        relier();
        distantesSont(localite("Z41", LATITUDE + 5e-5, LONGITUDE), localite("Z42", LATITUDE - 5e-5, LONGITUDE));

        assertThat(verdicts())
                .singleElement()
                .satisfies(verdict -> assertThat(verdict)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                                AppariementPoints.Verdict.PlusieursCandidats.class))
                        .satisfies(plusieurs -> assertThat(plusieurs.candidates())
                                .as("deux points de protocole si proches méritent d'être regardés, pas résolus")
                                .hasSize(2)));
    }

    @Test
    @DisplayName("#3750 : un point qui porte une nuit DÉPOSÉE est scellé, quoi qu'il y ait en face")
    void un_point_deja_depose_est_scelle() {
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(CARRE)
                .point("A1")
                .position(LATITUDE, LONGITUDE)
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-04-22")
                .heures("21:00:00", "05:00:00")
                .statut(StatutWorkflow.DEPOSE)
                .semerPassage();
        idSite = service.listerSites(ID_USER).getFirst().id();
        relier();
        // Une candidate PARFAITE en face : le verdict doit quand même refuser.
        distantesSont(localite("Z41", LATITUDE, LONGITUDE));

        assertThat(verdicts())
                .singleElement()
                .satisfies(verdict -> assertThat(verdict)
                        .as("renommer ce point le désolidariserait de ses propres participations, sans retour")
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                                AppariementPoints.Verdict.Scelle.class))
                        .satisfies(scelle ->
                                assertThat(scelle.nuitsSurLaPlateforme()).isEqualTo(1)));
    }

    @Test
    @DisplayName("#3750 : une nuit RÉCUPÉRÉE scelle aussi - elle vient de la plateforme")
    void une_nuit_recuperee_scelle_aussi() {
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(CARRE)
                .point("A1")
                .position(LATITUDE, LONGITUDE)
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-04-22")
                .heures("21:00:00", "05:00:00")
                .statut(StatutWorkflow.RECUPERE)
                .semerPassage();
        idSite = service.listerSites(ID_USER).getFirst().id();
        relier();
        distantesSont(localite("Z41", LATITUDE, LONGITUDE));

        // `== DEPOSE` aurait laissé passer ce cas : le nom de la localité d'une nuit récupérée est tout
        // aussi scellé, et `estSurLaPlateforme` existe pour qu'on ne l'oublie pas (#2581).
        assertThat(verdicts()).singleElement().isInstanceOf(AppariementPoints.Verdict.Scelle.class);
    }

    @Test
    @DisplayName("#3750 : un point sans coordonnées ne se compare pas, et le dit")
    void un_point_sans_position_ne_se_compare_pas() {
        semerPoint("A1", null, null);
        relier();
        distantesSont(localite("Z41", LATITUDE, LONGITUDE));

        assertThat(verdicts())
                .as("distinct d'« aucun candidat », qui affirmerait qu'on a cherché")
                .singleElement()
                .isInstanceOf(AppariementPoints.Verdict.SansPosition.class);
    }

    @Test
    @DisplayName("#3750 : un point dont le CODE existe déjà en face n'est pas à décider")
    void un_point_deja_apparie_par_le_code_est_ignore() {
        semerPoint("A1", LATITUDE, LONGITUDE);
        relier();
        distantesSont(localite("A1", LATITUDE + 5e-5, LONGITUDE));

        assertThat(verdicts())
                .as("il est apparié : le proposer à la fusion serait du bruit")
                .isEmpty();
    }

    @Test
    @DisplayName("#3750 : un carré non relié ne se confronte à rien, et n'appelle pas la plateforme")
    void un_carre_non_relie_ne_confronte_rien() {
        semerPoint("A1", LATITUDE, LONGITUDE);

        assertThat(verdicts()).isEmpty();
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never())
                .localitesDuSite(org.mockito.ArgumentMatchers.anyString());
    }

    private List<AppariementPoints.Verdict> verdicts() {
        ReponseApi<List<AppariementPoints.Appariement>> reponse = appariement.apparier(idSite);
        assertThat(reponse).isInstanceOf(ReponseApi.Succes.class);
        return ((ReponseApi.Succes<List<AppariementPoints.Appariement>>) reponse)
                .valeur().stream().map(AppariementPoints.Appariement::verdict).toList();
    }

    private void semerPoint(String code, Double latitude, Double longitude) {
        idSite = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(CARRE)
                .point(code)
                .position(latitude, longitude)
                .semerSiteEtPoint()
                .idSite();
    }

    private void relier() {
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(idSite), OBJECTID, false));
    }

    private void distantesSont(JsonObject... localites) {
        JsonArray brutes = new JsonArray();
        for (JsonObject localite : localites) {
            brutes.add(localite);
        }
        when(client.localitesDuSite(OBJECTID)).thenReturn(ReponseApi.succes(new LocalitesDuSite("etag-1", brutes)));
    }

    /// Une localité au format **exact** de la plateforme, écrite à la main.
    ///
    /// Ne pas la fabriquer avec le code de production : l'ordre `[latitude, longitude]` - à rebours du
    /// GeoJSON - serait alors faux des deux côtés, et le test resterait vert pendant que la plateforme
    /// comprendrait autre chose.
    private static JsonObject localite(String nom, double latitude, double longitude) {
        JsonArray coordonnees = new JsonArray();
        coordonnees.add(latitude);
        coordonnees.add(longitude);
        JsonObject point = new JsonObject();
        point.addProperty("type", "Point");
        point.add("coordinates", coordonnees);
        JsonArray geometries = new JsonArray();
        geometries.add(point);
        JsonObject collection = new JsonObject();
        collection.addProperty("type", "GeometryCollection");
        collection.add("geometries", geometries);
        JsonObject localite = new JsonObject();
        localite.addProperty("nom", nom);
        localite.add("geometries", collection);
        return localite;
    }
}
