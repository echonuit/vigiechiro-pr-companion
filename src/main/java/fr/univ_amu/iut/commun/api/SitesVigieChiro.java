package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/// Lecteur d'une page du **catalogue des sites** (`GET /sites`), la collection de toute la plateforme.
///
/// ## Pourquoi il ne réutilise pas [ParticipationsVigieChiro#sites]
///
/// Les deux lisent un site, mais pas dans le même contexte, et l'un d'eux **déduit** ce que l'autre
/// doit **lire**. Un site atteint par une participation est nécessairement verrouillé (on ne rejoint
/// pas un carré ouvert, #142), et le lecteur de participations code donc `verrouille = true` : c'est
/// juste là-bas. Appliqué au catalogue, ce raccourci annoncerait **20 517 sites verrouillés**, ce qui
/// est faux pour la plupart d'entre eux.
///
/// Ici, `verrouille` se lit du document, et vaut `false` quand le champ manque : on ne présume pas
/// qu'un carré est fermé sans que le serveur le dise.
///
/// Ce que les deux partagent - deviner le carré du titre, lire les localités dans l'ordre `[lat, lon]` -
/// vit dans [LocalitesVigieChiro], une seule fois.
final class SitesVigieChiro {

    private SitesVigieChiro() {}

    /// Les sites d'une page du catalogue. Document malformé (ni objet, ni identifiant) ignoré ; corps
    /// illisible → liste vide, que la pagination interprète comme une fin de collection.
    static List<SiteVigieChiro> sites(String corps) {
        List<SiteVigieChiro> sites = new ArrayList<>();
        for (JsonElement element : ReponsesVigieChiro.items(corps)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject site = element.getAsJsonObject();
            String id = ReponsesVigieChiro.texte(site, "_id");
            if (id == null) {
                continue;
            }
            String titre = ReponsesVigieChiro.texte(site, "titre");
            sites.add(new SiteVigieChiro(
                    id,
                    titre,
                    verrouille(site),
                    LocalitesVigieChiro.carreDepuisTitre(titre),
                    identifiantObservateur(site),
                    LocalitesVigieChiro.lirePoints(site)));
        }
        return List.copyOf(sites);
    }

    /// L'identifiant du propriétaire, quelle que soit la **forme** sous laquelle il arrive : le
    /// catalogue embarque le **document entier** de l'observateur, là où le site vu depuis une
    /// participation n'en porte que l'identifiant. Relevé sur la collection réelle : une lecture qui
    /// suppose une chaîne casse net (`UnsupportedOperationException: JsonObject`) dès le premier site.
    private static String identifiantObservateur(JsonObject site) {
        JsonElement observateur = site.get("observateur");
        if (observateur == null || observateur.isJsonNull()) {
            return null;
        }
        return observateur.isJsonObject()
                ? ReponsesVigieChiro.texte(observateur.getAsJsonObject(), "_id")
                : ReponsesVigieChiro.texte(site, "observateur");
    }

    /// Le verrouillage **tel que le document le porte**. Absent ou non booléen → `false` : c'est
    /// l'inverse de la déduction faite côté participations, et c'est délibéré (cf. doc de classe).
    private static boolean verrouille(JsonObject site) {
        JsonElement valeur = site.get("verrouille");
        return valeur != null
                        && valeur.isJsonPrimitive()
                        && valeur.getAsJsonPrimitive().isBoolean()
                ? valeur.getAsBoolean()
                : false;
    }
}
