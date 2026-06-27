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

    /// Opacités min/max du remplissage indigo des carrés selon la **densité** de passages (#152).
    private static final double OPACITE_MIN = 0.12;
    private static final double OPACITE_MAX = 0.55;

    private ConstructeurDonneesCarte() {}

    /// Construit les données de carte à partir des carrés agrégés : un marqueur par point **géolocalisé**
    /// (coloré par statut dominant) et un tracé d'emprise par carré ayant au moins un point géolocalisé.
    /// Le remplissage du carré reflète sa **densité de passages** (plus foncé = plus de passages), relative
    /// au carré le plus actif du jeu de données.
    static DonneesCarte depuis(List<CarreAgrege> carres) {
        // Densité normalisée sur les seuls carrés réellement **traçables** (au moins un point géolocalisé) :
        // un carré très actif mais invisible (sans GPS) ne doit pas pâlir artificiellement ceux qu'on voit.
        int maxPassages = carres.stream()
                .filter(ConstructeurDonneesCarte::estTracable)
                .mapToInt(CarreAgrege::nombrePassages)
                .max()
                .orElse(0);
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
            Color remplissage = couleurDensite(carre.nombrePassages(), maxPassages);
            EMPRISE.emprise(carre.numeroCarre(), pointsDuCarre)
                    .ifPresent(emprise -> carresGeo.add(new CarreGeo(carre.numeroCarre(), emprise, remplissage)));
        }
        return new DonneesCarte(carresGeo, tousLesPoints);
    }

    /// Un carré est **traçable** s'il a au moins un point géolocalisé (donc une emprise dessinable) ; sert
    /// de base de normalisation de la densité (cf. [#depuis]).
    private static boolean estTracable(CarreAgrege carre) {
        return carre.points().stream().anyMatch(PointAgrege::estGeolocalise);
    }

    /// Remplissage indigo translucide d'un carré selon sa **densité** : opacité interpolée entre
    /// [#OPACITE_MIN] (peu de passages) et [#OPACITE_MAX] (carré le plus actif, `nombrePassages == max`).
    /// `max <= 0` (aucun passage nulle part) → opacité minimale.
    static Color couleurDensite(int nombrePassages, int max) {
        double fraction = max <= 0 ? 0.0 : Math.min(1.0, (double) nombrePassages / max);
        return Color.web("#3f51b5", OPACITE_MIN + fraction * (OPACITE_MAX - OPACITE_MIN));
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
