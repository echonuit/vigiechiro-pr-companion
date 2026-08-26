package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.LocalitesDuSite;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.sites.model.dao.PointPublieDao;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// Publier un point sur un site distant, **sans effacer ceux des autres** (#3458).
class PublicationPointTest {

    private static final String SITE = "5eb12120cbe7410011f0a97f";
    private static final PointVigieChiro Z42 = new PointVigieChiro("Z42", 43.52, 5.46);
    /// Identifiant du point LOCAL : il ne sert qu'à retenir qu'il est en ligne.
    private static final long ID_LOCAL = 7L;

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final PointPublieDao publies = mock(PointPublieDao.class);

    /// Jeton présent par défaut : les tests d'envoi ne parlent pas de connexion. Le cas « pas de jeton »
    /// a son propre test, avec son propre fournisseur.
    private final PublicationPoint publication =
            new PublicationPoint(client, publies, () -> Optional.of("jeton-de-test"));

    @Test
    @DisplayName("#3458 : sans jeton, la publication ne se propose pas - c'est le SEUL refus prévisible")
    void sans_jeton_la_publication_ne_se_propose_pas() {
        PublicationPoint sansJeton = new PublicationPoint(client, publies, Optional::empty);

        assertThat(sansJeton.connecte()).isFalse();
        assertThat(publication.connecte()).isTrue();
    }

    @Test
    @DisplayName("#3458 : l'envoi contient les localités existantes ET la nouvelle")
    void l_envoi_conserve_les_localites_existantes() {
        LocalitesDuSite existantes = localites("etag-1", "Z1", "Z41");
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.succes(existantes));
        when(client.remplacerLocalites(anyString(), any())).thenReturn(ReponseApi.succes("{}"));

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42, ID_LOCAL);

        ArgumentCaptor<JsonArray> envoye = ArgumentCaptor.forClass(JsonArray.class);
        verify(client).remplacerLocalites(anyString(), envoye.capture());
        assertThat(noms(envoye.getValue()))
                .as("cette route REMPLACE la liste entière : n'envoyer que le point neuf effacerait les"
                        + " quarante et un autres, sur un carré dont l'observateur est quelqu'un d'autre")
                .containsExactly("Z1", "Z41", "Z42");
        assertThat(resultat).isInstanceOf(PublicationPoint.Resultat.Publie.class);
    }

    @Test
    @DisplayName("#3458 : les champs qu'on ne sait pas lire sont renvoyés INTACTS")
    void les_champs_inconnus_survivent() {
        // `habitats` est au schéma du backend et le client officiel le perd à chaque enregistrement,
        // parce qu'il reconstruit chaque localité sur trois champs. On ne reproduit pas ce défaut.
        JsonArray brutes = new JsonArray();
        brutes.add(JsonParser.parseString("{\"nom\":\"Z1\",\"habitats\":[{\"date\":\"2020-01-01\"}],\"inconnu\":42}")
                .getAsJsonObject());
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.succes(new LocalitesDuSite("etag-1", brutes)));
        when(client.remplacerLocalites(anyString(), any())).thenReturn(ReponseApi.succes("{}"));

        publication.publier(SITE, Z42, ID_LOCAL);

        ArgumentCaptor<JsonArray> envoye = ArgumentCaptor.forClass(JsonArray.class);
        verify(client).remplacerLocalites(anyString(), envoye.capture());
        JsonObject z1 = envoye.getValue().get(0).getAsJsonObject();
        assertThat(z1.has("habitats"))
                .as("normaliser au passage effacerait, pour toutes les localités du site, ce qu'on n'a"
                        + " pas compris")
                .isTrue();
        assertThat(z1.get("inconnu").getAsInt()).isEqualTo(42);
    }

    @Test
    @DisplayName("#3458 : les coordonnées partent en [latitude, longitude], à rebours du GeoJSON")
    void l_ordre_des_coordonnees_est_celui_de_la_plateforme() {
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.succes(localites("etag-1")));
        when(client.remplacerLocalites(anyString(), any())).thenReturn(ReponseApi.succes("{}"));

        publication.publier(SITE, Z42, ID_LOCAL);

        ArgumentCaptor<JsonArray> envoye = ArgumentCaptor.forClass(JsonArray.class);
        verify(client).remplacerLocalites(anyString(), envoye.capture());
        JsonArray coordonnees = envoye.getValue()
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("geometries")
                .getAsJsonArray("geometries")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("coordinates");
        // Une inversion rend un point PARFAITEMENT PLAUSIBLE, ailleurs sur la carte. #1277 l'a payé une
        // fois : personne ne s'en aperçoit avant le terrain.
        assertThat(coordonnees.get(0).getAsDouble()).as("latitude d'abord").isEqualTo(43.52);
        assertThat(coordonnees.get(1).getAsDouble()).as("longitude ensuite").isEqualTo(5.46);
    }

    @Test
    @DisplayName("#3458 : le site modifié entre la lecture et l'envoi fait RENONCER, rien n'est écrit")
    void un_site_modifie_entre_temps_fait_renoncer() {
        // Le serveur ne protège pas cette écriture : `set_localite` n'exige aucun If-Match. La garde est
        // donc entièrement ici, et sans elle on écraserait ce qui s'est ajouté depuis notre lecture.
        when(client.localitesDuSite(SITE))
                .thenReturn(ReponseApi.succes(localites("etag-1", "Z1")))
                .thenReturn(ReponseApi.succes(localites("etag-2", "Z1", "Z50")));

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42, ID_LOCAL);

        assertThat(resultat).isInstanceOf(PublicationPoint.Resultat.ModifieEntreTemps.class);
        verify(client, never()).remplacerLocalites(anyString(), any());
    }

    @Test
    @DisplayName("#3458 : un point déjà présent au même endroit n'est pas republié")
    void un_point_deja_present_ne_se_republie_pas() {
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.succes(localiteA("etag-1", "Z42", 43.52, 5.46)));

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42, ID_LOCAL);

        assertThat(resultat).isEqualTo(new PublicationPoint.Resultat.DejaPresent("Z42"));
        verify(client, never()).remplacerLocalites(anyString(), any());
    }

    @Test
    @DisplayName("#3458 : un 403 nomme SES DEUX causes, la plus probable d'abord")
    void le_refus_dit_quoi_faire() {
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.refuse(403, ""));

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42, ID_LOCAL);

        assertThat(resultat)
                .asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.type(PublicationPoint.Resultat.Refuse.class))
                .extracting(PublicationPoint.Resultat.Refuse::geste)
                .asString()
                // `set_localite` refuse dans DEUX cas que rien ne distingue dans la réponse : le
                // propriétaire d'un carré VERROUILLÉ, et le non-propriétaire non validé sur le protocole.
                // La première version ne nommait que le second : pour son propre carré verrouillé - le cas
                // le plus courant - elle était fausse, et envoyait vérifier une inscription hors sujet.
                .as("le cas le plus courant est son PROPRE carré, déjà verrouillé")
                .contains("verrouillé")
                .as("l'autre cause reste possible : un carré de tiers, sans validation sur le protocole")
                .contains("validé sur son protocole");
    }

    @Test
    @DisplayName("#3458 : une publication réussie est RETENUE, pour ne pas reproposer le geste")
    void une_publication_reussie_est_retenue() {
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.succes(localites("etag-1")));
        when(client.remplacerLocalites(anyString(), any())).thenReturn(ReponseApi.succes("{}"));

        publication.publier(SITE, Z42, ID_LOCAL);

        verify(publies).marquer(ID_LOCAL);
    }

    @Test
    @DisplayName("#3458 : un point déjà présent AU MÊME ENDROIT est retenu : il est bien en ligne")
    void un_point_deja_present_est_retenu() {
        // Trois mètres : sous le seuil, donc le même point relevé deux fois.
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.succes(localiteA("etag-1", "Z42", 43.52003, 5.46001)));

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42, ID_LOCAL);

        assertThat(resultat).isInstanceOf(PublicationPoint.Resultat.DejaPresent.class);
        // Sans cela, un point publié depuis un autre poste - ou avant cette mémoire - se reproposerait
        // indéfiniment, et chaque clic rendrait « déjà présent » sans que l'écran n'apprenne rien.
        verify(publies).marquer(ID_LOCAL);
    }

    @Test
    @DisplayName("#3458 : un HOMONYME posé ailleurs n'est ni publié, ni retenu, ni écrasé")
    void un_homonyme_ailleurs_n_est_pas_le_notre() {
        // ~1,2 km : très au dessus du seuil, et même au dessus du seuil de protocole.
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.succes(localiteA("etag-1", "Z42", 43.531, 5.46)));

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42, ID_LOCAL);

        assertThat(resultat)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        PublicationPoint.Resultat.AilleursSurLaPlateforme.class))
                .extracting(PublicationPoint.Resultat.AilleursSurLaPlateforme::distanceMetres)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE)
                .as("la distance est dite, pour que l'utilisateur juge lui-même")
                .isGreaterThan(1000.0);
        // Une participation NOMME sa localité : écraser la position distante déplacerait toutes les
        // nuits qui s'y rattachent, y compris celles d'autres observateurs.
        verify(client, never()).remplacerLocalites(anyString(), any());
        // Et le marquer publié figerait la confusion : le geste ne serait plus jamais reproposé.
        verify(publies, never()).marquer(anyLong());
    }

    @Test
    @DisplayName("#3458 : position distante illisible, on ne conclut PAS que c'est le même point")
    void une_position_illisible_ne_conclut_pas() {
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.succes(localites("etag-1", "Z42")));

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42, ID_LOCAL);

        // Le verdict prudent : rendre « déjà présent » marquerait le point publié sur la foi de son seul
        // nom, c'est-à-dire exactement le raccourci que ce chantier retire.
        assertThat(resultat).isInstanceOf(PublicationPoint.Resultat.AilleursSurLaPlateforme.class);
        verify(publies, never()).marquer(anyLong());
    }

    @Test
    @DisplayName("#3458 : un refus ou un renoncement ne retient RIEN")
    void un_echec_ne_retient_rien() {
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.refuse(403, ""));

        publication.publier(SITE, Z42, ID_LOCAL);

        // Retenir sur un échec serait pire que ne rien retenir : l'écran annoncerait un point en ligne
        // qui n'y est pas, et le dépôt qui s'y rattache échouerait plus tard, loin de la cause.
        verify(publies, never()).marquer(anyLong());
    }

    /// Localités **sans position** : la forme qu'avaient tous ces tests avant #3458. Elle reste utile,
    /// parce que c'est aussi ce que rend une géométrie absente ou malformée en vrai.
    private static LocalitesDuSite localites(String etag, String... noms) {
        JsonArray brutes = new JsonArray();
        for (String nom : noms) {
            JsonObject localite = new JsonObject();
            localite.addProperty("nom", nom);
            brutes.add(localite);
        }
        return new LocalitesDuSite(etag, brutes);
    }

    /// Une localité **posée quelque part**, dans la forme exacte de la plateforme.
    ///
    /// Le JSON est écrit **à la main**, et non fabriqué par `LocalitesDuSite#avecEnPlus`. Le construire
    /// avec le code de production ferait un aller-retour qui se refermerait sur lui-même : l'ordre
    /// `[latitude, longitude]` - à rebours du GeoJSON - serait faux des deux côtés, et le test resterait
    /// vert pendant que la plateforme comprendrait autre chose.
    private static LocalitesDuSite localiteA(String etag, String nom, double latitude, double longitude) {
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
        JsonArray brutes = new JsonArray();
        brutes.add(localite);
        return new LocalitesDuSite(etag, brutes);
    }

    private static java.util.List<String> noms(JsonArray localites) {
        return localites.asList().stream()
                .map(element -> element.getAsJsonObject().get("nom").getAsString())
                .toList();
    }
}
