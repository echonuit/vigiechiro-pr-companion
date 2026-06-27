package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.carte.CarreGeo;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.commun.view.carte.EmpriseAutourDesPoints;
import fr.univ_amu.iut.commun.view.carte.FournisseurEmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.PointGeo;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.PointAgrege;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.paint.Color;

/// Traduit les agrégats **domaine** (`CarreAgrege`/`PointAgrege`) en [DonneesCarte] pour la [CarteSites] :
/// couleur des points par **statut workflow**, emprise des carrés via [EmpriseAutourDesPoints]. C'est ici
/// (couche `view`) que la couleur entre en jeu : le ViewModel reste agnostique de JavaFX (ArchUnit). Pur
/// et **testable sans IHM**.
final class ConstructeurDonneesCarte {

    /// Fournisseur de repli (emprise autour des points) — le carroyage officiel se branchera ici plus tard.
    private static final FournisseurEmpriseCarre EMPRISE = new EmpriseAutourDesPoints();

    /// Remplissage léger des carrés (indigo translucide) ; la densité/heatmap viendra dans une PR dédiée.
    private static final Color FOND_CARRE = Color.web("#3f51b5", 0.12);

    private ConstructeurDonneesCarte() {}

    /// Construit les données de carte à partir des carrés agrégés : un marqueur par point **géolocalisé**
    /// (coloré par statut dominant) et un tracé d'emprise par carré ayant au moins un point géolocalisé.
    static DonneesCarte depuis(List<CarreAgrege> carres) {
        List<CarreGeo> carresGeo = new ArrayList<>();
        List<PointGeo> tousLesPoints = new ArrayList<>();
        for (CarreAgrege carre : carres) {
            List<PointGeo> pointsDuCarre = new ArrayList<>();
            for (PointAgrege point : carre.points()) {
                if (point.estGeolocalise()) {
                    PointGeo marqueur = new PointGeo(
                            carre.numeroCarre() + " / " + point.codePoint(),
                            point.latitude(),
                            point.longitude(),
                            couleurStatut(point.statutDominant()));
                    pointsDuCarre.add(marqueur);
                    tousLesPoints.add(marqueur);
                }
            }
            EMPRISE.emprise(carre.numeroCarre(), pointsDuCarre)
                    .ifPresent(emprise -> carresGeo.add(new CarreGeo(carre.numeroCarre(), emprise, FOND_CARRE)));
        }
        return new DonneesCarte(carresGeo, tousLesPoints);
    }

    /// Couleur d'un marqueur selon le **statut workflow** dominant du point (progression gris → indigo →
    /// vert). `null` (aucun passage) → gris neutre. Le marqueur porte aussi un libellé (#163 : pas que la
    /// couleur).
    static Color couleurStatut(StatutWorkflow statut) {
        if (statut == null) {
            return Color.web("#6a737d");
        }
        return switch (statut) {
            case IMPORTE -> Color.web("#6a737d");
            case TRANSFORME -> Color.web("#5c6bc0");
            case VERIFIE -> Color.web("#3f51b5");
            case PRET_A_DEPOSER -> Color.web("#00838f");
            case DEPOSE -> Color.web("#1e8449");
        };
    }
}
