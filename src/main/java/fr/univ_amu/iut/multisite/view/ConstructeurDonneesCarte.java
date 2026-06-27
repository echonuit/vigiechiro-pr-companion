package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.carte.CarreGeo;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.commun.view.carte.EmpriseAutourDesPoints;
import fr.univ_amu.iut.commun.view.carte.EmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.FournisseurEmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.FournisseurEmpriseCarreEnChaine;
import fr.univ_amu.iut.commun.view.carte.FournisseurEmpriseCarreOfficiel;
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

    /// Emprise des carrés : **carroyage officiel** (carrenat national) d'abord, **repli** autour des points
    /// ensuite. Un carré du référentiel est calé sur la grille 2 km ; les autres restent ancrés sur leurs
    /// points géolocalisés (#152/#325).
    private static final FournisseurEmpriseCarre EMPRISE =
            new FournisseurEmpriseCarreEnChaine(new FournisseurEmpriseCarreOfficiel(), new EmpriseAutourDesPoints());

    /// Opacités min/max du remplissage indigo des carrés selon la **densité** de passages (#152).
    private static final double OPACITE_MIN = 0.12;
    private static final double OPACITE_MAX = 0.55;

    /// Séparateur de lignes des info-bulles (mini-stats au survol).
    private static final String SAUT = "\n";

    private ConstructeurDonneesCarte() {}

    /// Construit les données de carte à partir des carrés agrégés : un marqueur par point **géolocalisé**
    /// (coloré par statut dominant) et un tracé d'emprise par carré **traçable** — soit ancré sur ses
    /// points, soit calé sur le carroyage officiel (donc tracé même sans GPS). Le remplissage reflète la
    /// **densité de passages**, normalisée sur **exactement** les carrés tracés.
    static DonneesCarte depuis(List<CarreAgrege> carres) {
        // 1re passe : marqueurs + emprise → on retient les carrés RÉELLEMENT tracés (emprise présente).
        record CarreTrace(CarreAgrege carre, EmpriseCarre emprise, List<PointAgrege> geolocalises) {}
        List<CarreTrace> traces = new ArrayList<>();
        List<PointGeo> tousLesPoints = new ArrayList<>();
        for (CarreAgrege carre : carres) {
            List<PointGeo> pointsDuCarre = new ArrayList<>();
            List<PointAgrege> geolocalises = new ArrayList<>();
            for (PointAgrege point : carre.points()) {
                if (point.estGeolocalise()) {
                    PointGeo marqueur = new PointGeo(
                            carre.numeroCarre() + " / " + point.codePoint(),
                            point.latitude(),
                            point.longitude(),
                            couleurStatut(point.statutDominant()),
                            infobullePoint(carre, point));
                    pointsDuCarre.add(marqueur);
                    tousLesPoints.add(marqueur);
                    geolocalises.add(point);
                }
            }
            EMPRISE.emprise(carre.numeroCarre(), pointsDuCarre)
                    .ifPresent(emprise -> traces.add(new CarreTrace(carre, emprise, geolocalises)));
        }
        // 2e passe : densité normalisée sur les carrés tracés (un carré officiel sans GPS, donc dessiné,
        // compte dans l'échelle ; un carré non dessiné ne la fausse pas).
        int maxPassages = traces.stream()
                .mapToInt(trace -> trace.carre().nombrePassages())
                .max()
                .orElse(0);
        List<CarreGeo> carresGeo = new ArrayList<>();
        for (CarreTrace trace : traces) {
            Color remplissage = couleurDensite(trace.carre().nombrePassages(), maxPassages);
            String infobulle = infobulleCarre(trace.carre(), trace.geolocalises());
            carresGeo.add(new CarreGeo(trace.carre().numeroCarre(), trace.emprise(), remplissage, infobulle));
        }
        return new DonneesCarte(carresGeo, tousLesPoints);
    }

    /// Mini-stats d'un **carré** au survol : nom (et numéro), total de passages, nombre de points
    /// géolocalisés, et répartition des statuts dominants. Lignes jointes par [#SAUT].
    private static String infobulleCarre(CarreAgrege carre, List<PointAgrege> geolocalises) {
        String entete = carre.nomConvivial() != null && !carre.nomConvivial().isBlank()
                ? carre.nomConvivial() + " (" + carre.numeroCarre() + ")"
                : "Carré " + carre.numeroCarre();
        List<String> lignes = new ArrayList<>();
        lignes.add(entete);
        lignes.add(quantite(carre.nombrePassages(), "passage") + " · " + quantite(geolocalises.size(), "point"));
        String repartition = repartitionStatuts(geolocalises);
        if (!repartition.isEmpty()) {
            lignes.add(repartition);
        }
        return String.join(SAUT, lignes);
    }

    /// Mini-stats d'un **point** au survol : son libellé, son total de passages et son statut dominant.
    private static String infobullePoint(CarreAgrege carre, PointAgrege point) {
        String statut = point.statutDominant() == null
                ? "Aucun passage qualifié"
                : "Statut : " + point.statutDominant().libelle();
        return String.join(
                SAUT,
                carre.numeroCarre() + " / " + point.codePoint(),
                quantite(point.nombrePassages(), "passage"),
                statut);
    }

    /// Répartition des statuts dominants des points (ordre du workflow), p. ex. `Vérifié ×2, Déposé ×1`.
    /// Les points sans statut (aucun passage qualifié) sont omis ; chaîne vide si aucun statut.
    private static String repartitionStatuts(List<PointAgrege> points) {
        List<String> parts = new ArrayList<>();
        for (StatutWorkflow statut : StatutWorkflow.values()) {
            long compte =
                    points.stream().filter(p -> p.statutDominant() == statut).count();
            if (compte > 0) {
                parts.add(statut.libelle() + " ×" + compte);
            }
        }
        return String.join(", ", parts);
    }

    /// Accord en nombre : `quantite(3, "passage")` → `3 passages` ; `quantite(1, "point")` → `1 point`.
    private static String quantite(int nombre, String unite) {
        return nombre + " " + unite + (nombre > 1 ? "s" : "");
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
