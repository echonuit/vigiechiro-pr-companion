package fr.univ_amu.iut.passage.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Ce qu'un paquet d'emport pèsera, **avant** qu'un octet soit écrit (#4625, ADR 4517).
///
/// Le patron vient de `CompacteurDepot`, qui sépare déjà « planifier sans rien écrire » (#1994) de
/// l'écriture, et expose son estimation « avec le même calcul que le garde-fou » (#808). Un plan qui
/// écrirait pour savoir ne serait pas un plan.
///
/// **Ce qu'il n'a pas pu lire, il le dit** (article A3). Une séquence disparue entre la sélection et
/// le plan n'est pas comptée pour zéro en silence : elle est nommée dans [#avertissements], et son
/// poids n'entre pas dans [#octetsEstimes]. Annoncer un volume incomplet comme complet enverrait
/// l'utilisateur libérer une place qui ne suffirait pas.
///
/// @param entrees ce que le paquet portera, dans l'ordre où il l'écrira
/// @param avertissements ce que le plan n'a pas pu lire, nommé
public record PlanDePaquet(List<EntreePrevue> entrees, List<String> avertissements) {

    /// Une entrée du paquet : son nom dans l'archive, sa nature, et son poids en octets.
    ///
    /// @param nomEntree chemin de l'entrée dans l'archive
    /// @param nature ce que cette entrée est, pour ventiler l'estimation
    /// @param octets poids mesuré, jamais estimé : une entrée dont le poids est inconnu ne devient
    ///     pas une entrée à zéro, elle devient un avertissement
    public record EntreePrevue(String nomEntree, NatureDEntree nature, long octets) {}

    /// Nom du manifeste, à la racine de l'archive.
    public static final String NOM_MANIFESTE = "paquet.json";

    public PlanDePaquet {
        entrees = List.copyOf(Objects.requireNonNull(entrees, "entrees"));
        avertissements = List.copyOf(Objects.requireNonNull(avertissements, "avertissements"));
    }

    /// Le plan d'un paquet, **sans rien écrire** : il lit les tailles, il ne crée aucun fichier.
    ///
    /// @param destination l'archive à venir, jamais touchée ici
    /// @param manifeste le texte du manifeste, dont le poids compte comme métadonnées
    /// @param sequences les séquences transformées à emporter
    /// @return le plan, avec ce qu'il a mesuré et ce qu'il n'a pas pu lire
    public static PlanDePaquet pour(Path destination, String manifeste, List<Path> sequences) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(manifeste, "manifeste");
        List<EntreePrevue> entrees = new ArrayList<>();
        List<String> avertissements = new ArrayList<>();

        entrees.add(new EntreePrevue(
                NOM_MANIFESTE, NatureDEntree.METADONNEES, manifeste.getBytes(StandardCharsets.UTF_8).length));

        for (Path sequence : sequences) {
            try {
                entrees.add(new EntreePrevue(
                        "sequences/" + sequence.getFileName(), NatureDEntree.SEQUENCE, Files.size(sequence)));
            } catch (IOException illisible) {
                avertissements.add("séquence illisible, non emportée : " + sequence.getFileName());
            }
        }
        return new PlanDePaquet(entrees, avertissements);
    }

    /// Le poids total de ce que le plan a **mesuré**.
    public long octetsEstimes() {
        return entrees.stream().mapToLong(EntreePrevue::octets).sum();
    }

    /// Le poids par nature de contenu, pour que le choix d'emporter se fasse en connaissance.
    public Map<NatureDEntree, Long> octetsParNature() {
        Map<NatureDEntree, Long> parNature = new EnumMap<>(NatureDEntree.class);
        for (NatureDEntree nature : NatureDEntree.values()) {
            parNature.put(nature, 0L);
        }
        for (EntreePrevue entree : entrees) {
            parNature.merge(entree.nature(), entree.octets(), Long::sum);
        }
        return Map.copyOf(parNature);
    }
}
