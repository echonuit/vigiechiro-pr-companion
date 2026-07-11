package fr.univ_amu.iut.commun.view;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Objects;

/// (Dé)sérialisation JSON d'un [DescripteurVue] pour la persistance des vues mémorisées (#623 + #994), via
/// **Gson**. Format courant : `{"filtres":{…},"colonnes":{"<table>":{"colonnes":[…]}}}`.
///
/// **Rétro-compatibilité** : les vues enregistrées avant #994 stockaient directement le descripteur de
/// **filtres** (`{"texte":…,"criteres":[…]}`), sans section colonnes. [#interpreter] reconnaît ce format
/// hérité (absence de la clé `filtres`) et le lit comme une vue **sans** colonnes — aucune migration de
/// base n'est nécessaire (le blob `descriptor_json` est opaque côté SQLite).
public final class DescripteurVueJson {

    private static final Gson GSON = new Gson();

    private DescripteurVueJson() {}

    /// Sérialise la vue au format courant `{filtres, colonnes}`.
    public static String serialiser(DescripteurVue descripteur) {
        Objects.requireNonNull(descripteur, "descripteur");
        return GSON.toJson(descripteur);
    }

    /// Reconstruit la vue depuis son JSON. Tolère le **format hérité** (filtres seuls) : le blob est alors lu
    /// comme un [DescripteurFiltre] et enveloppé dans une vue sans colonnes.
    public static DescripteurVue interpreter(String json) {
        Objects.requireNonNull(json, "json");
        JsonObject objet = GSON.fromJson(json, JsonObject.class);
        if (objet == null || !objet.has("filtres")) {
            return DescripteurVue.sansColonnes(GSON.fromJson(json, DescripteurFiltre.class));
        }
        return GSON.fromJson(json, DescripteurVue.class);
    }
}
