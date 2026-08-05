package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.List;
import java.util.function.Function;

/// Restreindre des lignes à un **taxon parent** (Chiroptères, Oiseaux, Orthoptères et cigales…), pour
/// n'importe quel type de ligne qui sait dire le sien.
///
/// ## Pourquoi cette classe existe
///
/// La règle était écrite **trois fois** - `FiltresActivite`, `FiltresAnalyse`, `FiltresRevue` - au
/// caractère près : même garde de nullité, même normalisation, même correspondance partielle, même refus
/// sur ensemble vide. Elles ne différaient que par le **type de ligne** et le **nom de l'entité** dans le
/// message, l'accesseur portant jusqu'au même nom (`groupe()`) dans les trois types.
///
/// Ce n'est pas le nombre de lignes qui a motivé la fusion, mais **ce que la duplication a coûté** : un
/// défaut trouvé par PIT dans une copie (le refus énumérait un taxon parent **vide**, « Taxons parents
/// présents : , Chiroptères ») a été corrigé là, et a survécu dans les deux autres - qu'aucun test ne
/// couvrait sur ce point, et qu'aucune analyse de mutation n'avait ciblées. La duplication avait caché le
/// défaut à l'outil même qui venait de le trouver.
///
/// Le patron est celui de [FiltresLieu#parLieu], générique avec une fonction d'extraction : il sert déjà
/// cinq commandes sans que rien ne soit réécrit.
///
/// ## Ce que le message dit, et pourquoi il le dit ainsi
///
/// « **parmi celles retenues** » figure désormais dans les trois cas. Ce n'était le cas que d'un seul, et
/// c'était une imprécision des deux autres, pas une nuance : les trois appelants exécutent ce filtre
/// **après** un `parLieu`, donc l'ensemble reçu est toujours déjà restreint. Annoncer « taxons parents
/// présents » sans dire « parmi celles retenues » laissait croire à un inventaire de toute la base.
public final class FiltresTaxonParent {

    private FiltresTaxonParent() {}

    /// Les lignes dont le taxon parent **contient** `groupe` (insensible à la casse et aux accents, comme
    /// `--lieu`). `groupe` nul ou vide n'écarte rien.
    ///
    /// @param groupeDe ce qu'on lit sur une ligne pour la comparer ; une valeur nulle ou vide n'est
    ///     jamais retenue, et ne paraît pas dans le message de refus
    /// @param aucuneEntite le début du refus, accordé au nom de l'entité (« Aucun contact »,
    ///     « Aucune observation ») : c'est ce que l'appelant sait et que cette classe ignore
    /// @throws RegleMetierException si aucune ligne ne relève de ce taxon parent
    public static <T> List<T> parTaxonParent(
            List<T> lignes, String groupe, Function<T, String> groupeDe, String aucuneEntite) {
        if (groupe == null || groupe.isBlank()) {
            return lignes;
        }
        String demande = NormalisationTexte.normaliser(groupe);
        List<T> retenues = lignes.stream()
                .filter(ligne -> {
                    String valeur = groupeDe.apply(ligne);
                    return valeur != null
                            && NormalisationTexte.normaliser(valeur).contains(demande);
                })
                .toList();
        if (retenues.isEmpty()) {
            throw new RegleMetierException(aucuneEntite + " pour le taxon parent « " + groupe
                    + " » parmi celles retenues. Taxons parents présents : " + resumer(lignes, groupeDe) + ".");
        }
        return retenues;
    }

    /// Les taxons parents présents, pour que le refus nomme ce qui existe plutôt que de laisser chercher.
    ///
    /// Les valeurs **vides** sont écartées autant que les nulles : sans cela, le message se terminait par
    /// « présents : , Chiroptères », une virgule qui ne désigne rien dans la phrase même censée aider.
    /// C'est le défaut que PIT avait montré sur une seule des trois copies.
    private static <T> String resumer(List<T> lignes, Function<T, String> groupeDe) {
        List<String> presents = lignes.stream()
                .map(groupeDe)
                .filter(groupe -> groupe != null && !groupe.isBlank())
                .distinct()
                .sorted()
                .toList();
        return presents.isEmpty() ? "aucun" : String.join(", ", presents);
    }
}
