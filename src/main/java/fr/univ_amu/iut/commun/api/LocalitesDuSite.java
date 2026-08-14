package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonArray;
import java.util.Objects;

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
    public boolean contient(String nom) {
        for (var element : brutes) {
            if (element.isJsonObject()
                    && element.getAsJsonObject().has("nom")
                    && nom.equals(element.getAsJsonObject().get("nom").getAsString())) {
                return true;
            }
        }
        return false;
    }
}
