package fr.univ_amu.iut.commun.view.carte;

import java.util.List;

/// Contrat de données de la [CarteSites] : les carrés à tracer et les points à marquer. Listes immuables.
/// L'appelant (p. ex. le ViewModel multisite) assemble ces données ; le composant les rend sans rien
/// savoir du domaine, ce qui le rend **réutilisable** par tout écran qui a des points/carrés à montrer.
///
/// @param carres carrés à tracer (peut être vide)
/// @param points points à marquer (peut être vide)
public record DonneesCarte(List<CarreGeo> carres, List<PointGeo> points) {

    public DonneesCarte {
        carres = List.copyOf(carres);
        points = List.copyOf(points);
    }

    /// Données vides (rien à afficher).
    public static DonneesCarte vide() {
        return new DonneesCarte(List.of(), List.of());
    }
}
