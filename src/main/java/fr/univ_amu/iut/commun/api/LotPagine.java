package fr.univ_amu.iut.commun.api;

import java.util.List;

/// Ce qu'un parcours de collection paginée a **réellement lu**, et de quoi savoir si c'en est la
/// totalité.
///
/// ## Pourquoi ce type existe
///
/// [PaginationEve#parcourir] rend une liste, et une liste ne dit pas d'où elle vient : une collection
/// **épuisée** et un parcours **arrêté au plafond** produisent le même `Succes(List)`. Tant que le
/// plafond n'est qu'un garde-fou anti-boucle que rien n'atteint, la confusion est sans effet. Elle
/// devient un mensonge dès qu'un appelant choisit combien de pages lire : il croirait tenir la
/// collection entière là où il n'a qu'un échantillon.
///
/// C'est exactement le **préfixe silencieux de #1277** (une collection tronquée qu'on prend pour
/// complète), qu'on refabriquerait à la main après l'avoir corrigé au prix fort. [#complet] est là
/// pour que l'appelant n'ait pas à le déduire, et [#totalAnnonce] pour qu'il puisse **dire** ce qui
/// manque plutôt que de le taire.
///
/// @param elements ce qui a été lu, dans l'ordre des pages
/// @param totalAnnonce ce que le serveur annonce (`_meta.total`), `0` s'il ne l'annonce pas
/// @param pagesLues nombre de pages effectivement demandées et non vides
/// @param complet vrai si le parcours s'est arrêté faute d'éléments, faux s'il a atteint son plafond
public record LotPagine<T>(List<T> elements, int totalAnnonce, int pagesLues, boolean complet) {

    public LotPagine {
        elements = List.copyOf(elements);
    }

    /// Nombre de pages que le total annoncé représente, `0` si le serveur ne l'annonce pas : de quoi
    /// dire « 3 pages sur 206 » plutôt que « 3 pages ».
    public int pagesAnnoncees() {
        return totalAnnonce <= 0 ? 0 : (totalAnnonce + PaginationEve.TAILLE_PAGE - 1) / PaginationEve.TAILLE_PAGE;
    }
}
