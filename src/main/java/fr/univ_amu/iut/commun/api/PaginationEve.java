package fr.univ_amu.iut.commun.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

/// Parcours d'une collection **paginée Eve** (`_items` + `?max_results=&page=`) : accumule **toutes**
/// les pages jusqu'à la première page vide, avec un plafond de pages en garde-fou anti-boucle.
///
/// Extrait de [ClientVigieChiro] : la boucle de pagination est une préoccupation à part entière,
/// partagée par `donnees`, `mesSites` et `mesParticipations`. L'y factoriser évite d'alourdir le client
/// (God Class) et supprime la duplication de la boucle.
///
/// **Tout-ou-rien** (#1284) : un échec à la page N rend l'issue de cette page pour la collection
/// entière. Avant, l'échec « terminait » le parcours : une panne à la page 3 rendait les pages 1-2
/// comme si la collection était complète : un préfixe silencieux, la variante pire-que-vide de #1277.
final class PaginationEve {

    /// Taille de page demandée à Eve. **100 est le maximum accepté** : au-delà, le serveur ne tronque pas,
    /// il **rejette la requête** (`422`). Quand le transport dégradait tout échec HTTP en
    /// `Optional.empty()`, un dépassement ne se voyait pas : la collection entière revenait **vide, en
    /// silence** : import des observations, participations et sites (#1277). Le plafond est celui du
    /// `Paginator` du backend (`vigiechiro/xin/snippets.py`) ; le contrat live verrouille désormais
    /// `422 → Refuse`.
    static final int TAILLE_PAGE = 100;

    private PaginationEve() {}

    /// Suffixe de requête d'une page (`?max_results=…&page=…`). Le nombre de **pages** est libre, la
    /// **taille** de page ne l'est pas : cf. [#TAILLE_PAGE].
    static String requete(int page) {
        return "?max_results=" + TAILLE_PAGE + "&page=" + page;
    }

    /// @param pagesMax  plafond de pages (garde-fou anti-boucle)
    /// @param corpsPage renvoie le corps JSON de la page `n`, trié ([ReponseApi])
    /// @param parPage   parse une page en éléments ; une page **sans élément** marque la fin
    /// @return la collection **complète** en cas de succès ; sinon l'issue de la page fautive, ou un
    ///     `Injoignable` si le garde-fou a été atteint (#3046) : jamais un préfixe des pages lues
    static <T> ReponseApi<List<T>> parcourir(
            int pagesMax, IntFunction<ReponseApi<String>> corpsPage, Function<String, List<T>> parPage) {
        return parcourir(pagesMax, corpsPage, parPage, (page, totalPages) -> {});
    }

    /// Variante **suivie** (#1522, #1534) : [SuiviPagination#surPage] est appelé après chaque page non
    /// vide, avant de demander la suivante, avec le numéro de page et le **nombre total de pages** lu sur la
    /// première (`_meta.total`, `0` si le serveur ne l'annonce pas). L'appelant y relaie une progression -
    /// déterminée si le total est connu - et **consulte son jeton d'annulation** ; une exception levée
    /// depuis le suivi interrompt le parcours et remonte telle quelle (annulation d'un long téléchargement,
    /// faute de quoi la barre restait figée et « Annuler » muet).
    static <T> ReponseApi<List<T>> parcourir(
            int pagesMax,
            IntFunction<ReponseApi<String>> corpsPage,
            Function<String, List<T>> parPage,
            SuiviPagination suivi) {
        ReponseApi<LotPagine<T>> issue = parcourirBorne(pagesMax, corpsPage, parPage, suivi);
        if (!(issue instanceof ReponseApi.Succes<LotPagine<T>>(LotPagine<T> lot))) {
            return memeEchec(issue);
        }
        return lot.complet() ? ReponseApi.succes(lot.elements()) : ReponseApi.injoignable(troncature(lot));
    }

    /// Ici, `pagesMax` est un **garde-fou anti-boucle**, jamais une borne choisie (celle-là passe par
    /// [#parcourirBorne]). L'atteindre est donc une anomalie : la collection est plus grande que ce
    /// que ce parcours sait lire, et les éléments déjà lus n'en sont qu'un préfixe.
    ///
    /// Rendre ce préfixe en `Succes` serait exactement le défaut de #1277 : un import qui se présente
    /// comme réussi en ayant laissé des observations derrière lui. Un échec franc est le comportement
    /// **sûr** ; ce n'est pas le comportement idéal, et le jour où ce cas se présenterait vraiment,
    /// il faudrait relever le plafond ou lire par morceaux (#3046).
    private static String troncature(LotPagine<?> lot) {
        return "collection trop grande pour être lue d'un seul tenant : " + lot.pagesLues()
                + " page(s) lues sans atteindre la fin"
                + (lot.totalAnnonce() > 0 ? ", sur " + lot.totalAnnonce() + " élément(s) annoncés" : "")
                + ". Aucun résultat partiel n'est rendu, pour ne pas le faire passer pour un tout.";
    }

    /// Variante qui **dit si elle a tout lu** : même parcours, mais le résultat porte le total annoncé,
    /// le nombre de pages lues et un drapeau [LotPagine#complet].
    ///
    /// C'est la forme à employer quand `pagesMax` n'est plus un garde-fou mais un **choix de
    /// l'appelant** (« lis-moi trois pages ») : sans elle, une liste tronquée est indiscernable d'une
    /// collection épuisée, et l'appelant annonce comme un tout ce qui n'est qu'un échantillon. Le
    /// parcours reste **tout-ou-rien** : un échec en page N rend son issue, jamais les pages
    /// précédentes.
    static <T> ReponseApi<LotPagine<T>> parcourirBorne(
            int pagesMax,
            IntFunction<ReponseApi<String>> corpsPage,
            Function<String, List<T>> parPage,
            SuiviPagination suivi) {
        List<T> tout = new ArrayList<>();
        int total = 0;
        int totalPages = 0;
        int pagesLues = 0;
        boolean complet = false;
        for (int page = 1; page <= pagesMax; page++) {
            ReponseApi<String> reponse = corpsPage.apply(page);
            if (page == 1 && reponse instanceof ReponseApi.Succes<String>(String corps)) {
                total = ReponsesVigieChiro.total(corps);
                totalPages = total <= 0 ? 0 : (total + TAILLE_PAGE - 1) / TAILLE_PAGE;
            }
            ReponseApi<List<T>> lot = reponse.transformer(parPage);
            if (!(lot instanceof ReponseApi.Succes<List<T>>(List<T> elements))) {
                return memeEchec(lot);
            }
            if (elements.isEmpty()) {
                // Sortie par épuisement : c'est le seul cas où l'on tient la collection entière.
                complet = true;
                break;
            }
            tout.addAll(elements);
            pagesLues++;
            suivi.surPage(page, totalPages);
        }
        return ReponseApi.succes(new LotPagine<>(List.copyOf(tout), total, pagesLues, complet));
    }

    /// Re-type une issue d'échec. Les variantes non-`Succes` ne portent aucune valeur : seul leur
    /// paramètre de type change. On l'écrit ici explicitement, plutôt qu'avec un
    /// `transformer(x -> null)` qui marcherait (la transformation n'est jamais appliquée à un échec)
    /// mais laisserait croire au lecteur qu'un succès **nul** est possible.
    private static <A, B> ReponseApi<B> memeEchec(ReponseApi<A> issue) {
        return switch (issue) {
            case ReponseApi.Succes<A> succes ->
                throw new IllegalArgumentException("issue de succès passée à memeEchec : " + succes);
            case ReponseApi.NonConnecte<A> ignore -> ReponseApi.nonConnecte();
            case ReponseApi.Injoignable<A>(String cause) -> ReponseApi.injoignable(cause);
            case ReponseApi.Refuse<A>(int statut, String corps) -> ReponseApi.refuse(statut, corps);
        };
    }
}
