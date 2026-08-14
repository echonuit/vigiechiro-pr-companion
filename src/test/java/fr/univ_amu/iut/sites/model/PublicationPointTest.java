package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// Publier un point sur un site distant, **sans effacer ceux des autres** (#3458).
class PublicationPointTest {

    private static final String SITE = "5eb12120cbe7410011f0a97f";
    private static final PointVigieChiro Z42 = new PointVigieChiro("Z42", 43.52, 5.46);

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final PublicationPoint publication = new PublicationPoint(client);

    @Test
    @DisplayName("#3458 : l'envoi contient les localités existantes ET la nouvelle")
    void l_envoi_conserve_les_localites_existantes() {
        LocalitesDuSite existantes = localites("etag-1", "Z1", "Z41");
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.succes(existantes));
        when(client.remplacerLocalites(anyString(), any())).thenReturn(ReponseApi.succes("{}"));

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42);

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

        publication.publier(SITE, Z42);

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

        publication.publier(SITE, Z42);

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

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42);

        assertThat(resultat).isInstanceOf(PublicationPoint.Resultat.ModifieEntreTemps.class);
        verify(client, never()).remplacerLocalites(anyString(), any());
    }

    @Test
    @DisplayName("#3458 : un point déjà présent n'est pas republié")
    void un_point_deja_present_ne_se_republie_pas() {
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.succes(localites("etag-1", "Z42")));

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42);

        assertThat(resultat).isEqualTo(new PublicationPoint.Resultat.DejaPresent("Z42"));
        verify(client, never()).remplacerLocalites(anyString(), any());
    }

    @Test
    @DisplayName("#3458 : un 403 dit ce qui manque, pas seulement que c'est refusé")
    void le_refus_dit_quoi_faire() {
        when(client.localitesDuSite(SITE)).thenReturn(ReponseApi.refuse(403, ""));

        PublicationPoint.Resultat resultat = publication.publier(SITE, Z42);

        assertThat(resultat)
                .asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.type(PublicationPoint.Resultat.Refuse.class))
                .extracting(PublicationPoint.Resultat.Refuse::geste)
                .asString()
                .as("« accès refusé » n'apprend rien : la plateforme exige d'être validé sur le protocole"
                        + " du site, et c'est ça qu'il faut dire")
                .contains("validé sur son protocole");
    }

    private static LocalitesDuSite localites(String etag, String... noms) {
        JsonArray brutes = new JsonArray();
        for (String nom : noms) {
            JsonObject localite = new JsonObject();
            localite.addProperty("nom", nom);
            brutes.add(localite);
        }
        return new LocalitesDuSite(etag, brutes);
    }

    private static java.util.List<String> noms(JsonArray localites) {
        return localites.asList().stream()
                .map(element -> element.getAsJsonObject().get("nom").getAsString())
                .toList();
    }
}
