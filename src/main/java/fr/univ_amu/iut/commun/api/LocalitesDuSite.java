package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Les localités d'un site **telles que la plateforme les rend**, et l'`_etag` du site à cet instant.
///
/// ## Pourquoi le JSON brut, et pas des [PointVigieChiro]
///
/// Parce que publier une localité **remplace la liste entière** (`{'$set': {'localites': ...}}`) : tout
/// champ qu'on ne saurait pas relire serait effacé pour toutes les autres localités du site, qui
/// appartiennent souvent à un autre observateur.
///
/// ⚠️ **C'est le défaut du client officiel**, lu dans sa source : il reconstruit chaque localité à partir
/// de `nom`, `geometries` et `representatif`, et **perd `habitats`** - un champ que le schéma du backend
/// porte pourtant. Mesuré sur 6 440 localités réparties dans la collection, aucun n'en portait
/// aujourd'hui : le défaut est donc latent là-bas, et ce n'est pas une raison de le reproduire ici.
///
/// On rend donc **intact** ce qu'on ne comprend pas, et l'on n'ajoute que la localité demandée.
///
/// @param etag l'`_etag` du site au moment de la lecture, seul moyen de voir qu'il a bougé depuis
/// @param brutes les localités **non interprétées**, à renvoyer telles quelles
public record LocalitesDuSite(String etag, JsonArray brutes) {

    public LocalitesDuSite {
        Objects.requireNonNull(etag, "etag");
        Objects.requireNonNull(brutes, "brutes");
    }

    /// La liste **complète** à renvoyer pour ajouter `point` : les localités lues, intactes, plus la
    /// nouvelle.
    ///
    /// Construite ici et non chez l'appelant : la forme du JSON de la plateforme - dont l'ordre
    /// `[latitude, longitude]`, à rebours du GeoJSON - reste la connaissance de ce paquet.
    public JsonArray avecEnPlus(PointVigieChiro point) {
        JsonArray union = brutes.deepCopy();
        union.add(RequetesVigieChiro.localite(point));
        return union;
    }

    /// Vrai si une localité porte déjà ce nom : la plateforme impose leur unicité (`unique_field: nom`).
    ///
    /// ⚠️ Question **strictement nominale**, et qui doit le rester. La déduire de [#localite(String)]
    /// serait un piège : une localité dont la géométrie est illisible rendrait `false` ici, on enverrait
    /// un doublon de nom, et la plateforme le refuserait pour une raison sans rapport avec la cause.
    public boolean contient(String nom) {
        return trouver(nom).isPresent();
    }

    /// La localité de ce nom, **avec sa position**, si elle existe et qu'on sait la lire.
    ///
    /// ⚠️ Le nom ne suffit pas à conclure que c'est le même point. Une localité homonyme peut être posée
    /// **ailleurs**, et la confondre avec la nôtre serait grave : une participation nomme sa localité
    /// (`'point': {'type': 'string'}` au schéma des participations), donc toute nuit déposée sur ce point
    /// se rattacherait à la position distante, pas à la sienne.
    ///
    /// `Optional.empty()` couvre deux cas volontairement confondus - aucune localité de ce nom, ou une
    /// localité de ce nom dont la géométrie est illisible : dans les deux cas on ne sait pas où elle est,
    /// et l'appelant ne doit rien en conclure.
    public Optional<PointVigieChiro> localite(String nom) {
        return trouver(nom).map(LocalitesVigieChiro::lireUnPoint);
    }

    /// Toutes les localités **dont on sait lire la position** (#3750).
    ///
    /// Celles dont la géométrie est absente ou malformée sont écartées : on ne peut rien confronter à une
    /// position qu'on ne connaît pas, et les compter parmi les candidates d'un appariement laisserait
    /// croire qu'on les a examinées.
    public List<PointVigieChiro> positions() {
        List<PointVigieChiro> points = new ArrayList<>();
        for (JsonElement element : brutes) {
            if (!element.isJsonObject()) {
                continue;
            }
            PointVigieChiro point = LocalitesVigieChiro.lireUnPoint(element.getAsJsonObject());
            if (point != null) {
                points.add(point);
            }
        }
        return List.copyOf(points);
    }

    /// L'objet JSON de la localité de ce nom, tel quel.
    private Optional<JsonObject> trouver(String nom) {
        for (JsonElement element : brutes) {
            if (element.isJsonObject()
                    && element.getAsJsonObject().has("nom")
                    && nom.equals(element.getAsJsonObject().get("nom").getAsString())) {
                return Optional.of(element.getAsJsonObject());
            }
        }
        return Optional.empty();
    }
}
