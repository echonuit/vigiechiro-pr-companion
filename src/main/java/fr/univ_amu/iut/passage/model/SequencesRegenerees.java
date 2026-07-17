package fr.univ_amu.iut.passage.model;

import java.nio.file.Path;
import java.util.List;

/// Ce qu'une régénération produit (#1406, #1726) : les **tranches** régénérées (à faire passer par la
/// cascade de vérification) et l'**empreinte SHA-256 du brut source**.
///
/// L'empreinte vient **gratuitement** : la transformation a déjà lu tout le brut pour la calculer.
/// L'hydratation d'un passage reconstruit s'en sert pour inscrire l'empreinte des vrais originaux (#1651)
/// **sans re-lire** les bruts (jusqu'à plusieurs dizaines de Go). Elle vaut ce que vaut la transformation :
/// c'est l'empreinte du brut **désigné** par l'utilisateur, dont les tranches viennent de passer la
/// cascade - donc une preuve d'identité de l'original, comme celle capturée à l'import (#1299).
///
/// @param tranches chemins des tranches régénérées, dans le dossier temporaire
/// @param empreinteSource empreinte SHA-256 hexadécimale du brut source, telle que calculée par la
///     transformation
public record SequencesRegenerees(List<Path> tranches, String empreinteSource) {

    public SequencesRegenerees {
        tranches = List.copyOf(tranches);
    }
}
