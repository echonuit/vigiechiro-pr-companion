package fr.univ_amu.iut.commun.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

/// Parcours d'une collection **paginée Eve** (`_items` + `?max_results=&page=`) : accumule **toutes**
/// les pages jusqu'à la première page vide, avec un plafond de pages en garde-fou anti-boucle.
///
/// Extrait de [ClientVigieChiro] : la boucle de pagination est une préoccupation à part entière,
/// partagée par `donnees`, `mesSites` et `mesParticipations`. L'y factoriser évite d'alourdir le client
/// (God Class) et supprime la duplication de la boucle.
final class PaginationEve {

    private PaginationEve() {}

    /// @param pagesMax  plafond de pages (garde-fou anti-boucle)
    /// @param corpsPage renvoie le corps JSON de la page `n` ; vide = non connecté / erreur : on s'arrête
    ///                  et on renvoie ce qui a déjà été récupéré
    /// @param parPage   parse une page en éléments ; une page **sans élément** marque la fin
    static <T> List<T> parcourir(
            int pagesMax, IntFunction<Optional<String>> corpsPage, Function<String, List<T>> parPage) {
        List<T> tout = new ArrayList<>();
        for (int page = 1; page <= pagesMax; page++) {
            Optional<String> corps = corpsPage.apply(page);
            if (corps.isEmpty()) {
                break;
            }
            List<T> lot = parPage.apply(corps.get());
            if (lot.isEmpty()) {
                break;
            }
            tout.addAll(lot);
        }
        return tout;
    }
}
