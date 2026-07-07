package fr.univ_amu.iut.commun.view;

import com.google.gson.Gson;
import java.util.Objects;

/// (Dé)sérialisation JSON d'un [DescripteurFiltre] pour la **persistance des vues mémorisées** (#623),
/// via **Gson** (support natif des records). Format déterministe (ordre de déclaration des composants) :
/// `{"texte":"…","criteres":[{"nom":"…","valeurs":["…"]}]}`.
///
/// C'est la **sérialisation canonique** de l'état sémantique des filtres (issu de
/// [GestionnaireFiltres#decrire()]), transportable entre vues (#476) et stockable en base
/// (`vue_sauvegardee.descripteur_json`). Contrairement aux petits parseurs plats faits main du dépôt
/// (`JsonSimple` + lecteurs par feature), le descripteur est **imbriqué** (objet contenant un tableau
/// d'objets), d'où le recours à une bibliothèque JSON dédiée.
public final class DescripteurFiltreJson {

    private static final Gson GSON = new Gson();

    private DescripteurFiltreJson() {}

    /// Sérialise le descripteur en JSON déterministe.
    public static String serialiser(DescripteurFiltre descripteur) {
        Objects.requireNonNull(descripteur, "descripteur");
        return GSON.toJson(descripteur);
    }

    /// Reconstruit le descripteur depuis le JSON produit par [#serialiser(DescripteurFiltre)].
    public static DescripteurFiltre interpreter(String json) {
        Objects.requireNonNull(json, "json");
        return GSON.fromJson(json, DescripteurFiltre.class);
    }
}
