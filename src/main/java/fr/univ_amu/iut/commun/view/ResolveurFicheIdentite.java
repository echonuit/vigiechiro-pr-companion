package fr.univ_amu.iut.commun.view;

/// Résolveur **neutre** : renvoie l'URL telle quelle. Défaut d'injection (aucun réseau), et suffisant
/// pour les sources à adresse directe (PNA, Wikipédia).
public final class ResolveurFicheIdentite implements ResolveurFiche {

    @Override
    public String resoudre(String url) {
        return url;
    }
}
