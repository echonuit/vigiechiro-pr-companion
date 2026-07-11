package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.view.carte.CarreGeo;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.commun.view.carte.EmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.EventailCentre;
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

    /// Emprise des carrés : **carroyage officiel** (carrenat national) d'abord, **repli** autour des points
    /// ensuite. Un carré du référentiel est calé sur la grille 2 km ; les autres restent ancrés sur leurs
    /// points géolocalisés (#152/#325).
    private static final FournisseurEmpriseCarre EMPRISE = FournisseurEmpriseCarre.parDefaut();

    /// Opacités min/max du remplissage indigo des carrés selon la **densité** de passages (#152).
    private static final double OPACITE_MIN = 0.12;
    private static final double OPACITE_MAX = 0.55;

    /// Séparateur de lignes des info-bulles (mini-stats au survol).
    private static final String SAUT = "\n";

    /// Unité « point » pour les accords en nombre des décomptes ([#quantite]).
    private static final String UNITE_POINT = "point";

    private ConstructeurDonneesCarte() {}

    /// Construit les données de carte à partir des carrés agrégés : un marqueur par point **géolocalisé**
    /// (coloré par statut dominant), un marqueur **approximatif** par point **sans GPS** placé au centre de
    /// son carré (#153), et un tracé d'emprise par carré **traçable** — soit ancré sur ses points, soit calé
    /// sur le carroyage officiel (donc tracé même sans GPS). Le remplissage reflète la **densité de
    /// passages**, normalisée sur **exactement** les carrés tracés.
    static DonneesCarte depuis(List<CarreAgrege> carres) {
        // 1re passe : marqueurs + emprise → on retient les carrés RÉELLEMENT tracés (emprise présente).
        record CarreTrace(
                CarreAgrege carre, EmpriseCarre emprise, List<PointAgrege> geolocalises, List<PointAgrege> sansGps) {}
        List<CarreTrace> traces = new ArrayList<>();
        List<PointGeo> tousLesPoints = new ArrayList<>();
        for (CarreAgrege carre : carres) {
            List<PointGeo> pointsDuCarre = new ArrayList<>();
            List<PointAgrege> geolocalises = new ArrayList<>();
            List<PointAgrege> sansGps = new ArrayList<>();
            for (PointAgrege point : carre.points()) {
                if (point.estGeolocalise()) {
                    pointsDuCarre.add(marqueurReel(carre, point));
                    geolocalises.add(point);
                } else {
                    sansGps.add(point);
                }
            }
            tousLesPoints.addAll(pointsDuCarre);
            // Un point sans GPS est placé au centre de son carré, MAIS seulement si ce centre est connu
            // (emprise présente) : carré officiel, ou repli autour des points géolocalisés. Sans emprise
            // (carré inconnu et aucun point géolocalisé), on ne peut pas le situer → il reste hors carte.
            EMPRISE.emprise(carre.numeroCarre(), pointsDuCarre).ifPresent(emprise -> {
                traces.add(new CarreTrace(carre, emprise, geolocalises, sansGps));
                tousLesPoints.addAll(marqueursApproches(carre, emprise, sansGps));
            });
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
            String infobulle = infobulleCarre(trace.carre(), trace.geolocalises(), trace.sansGps());
            carresGeo.add(new CarreGeo(trace.carre().numeroCarre(), trace.emprise(), remplissage, infobulle));
        }
        return new DonneesCarte(carresGeo, tousLesPoints);
    }

    /// Libellé d'un point sur la carte : `numéroCarré / codePoint` (p. ex. `640380 / A1`).
    private static String libellePoint(CarreAgrege carre, PointAgrege point) {
        return carre.numeroCarre() + " / " + point.codePoint();
    }

    /// Marqueur d'un point **réellement géolocalisé** : à son GPS, coloré par statut dominant.
    private static PointGeo marqueurReel(CarreAgrege carre, PointAgrege point) {
        return new PointGeo(
                libellePoint(carre, point),
                point.latitude(),
                point.longitude(),
                couleurStatut(point.statutDominant()),
                infobullePoint(carre, point));
    }

    /// Marqueurs **approximatifs** des points sans GPS : placés au centre du carré (#153), **désempilés en
    /// éventail** ([EventailCentre]) pour ne pas se superposer. Marqués `approximatif` (rendu pointillé par
    /// la couche) pour ne pas être pris pour des positions réelles.
    private static List<PointGeo> marqueursApproches(
            CarreAgrege carre, EmpriseCarre emprise, List<PointAgrege> sansGps) {
        List<double[]> positions = EventailCentre.positions(emprise, sansGps.size());
        List<PointGeo> resultat = new ArrayList<>();
        for (int i = 0; i < sansGps.size(); i++) {
            PointAgrege point = sansGps.get(i);
            resultat.add(new PointGeo(
                    libellePoint(carre, point),
                    positions.get(i)[0],
                    positions.get(i)[1],
                    couleurStatut(point.statutDominant()),
                    infobullePointApproche(carre, point),
                    true));
        }
        return resultat;
    }

    /// Mini-stats d'un **carré** au survol : nom (et numéro), total de passages, décompte des points
    /// **affichés** (géolocalisés + sans GPS, ces derniers placés au centre — #153), et répartition des
    /// statuts dominants sur **tous** les points affichés (cohérente avec les marqueurs). Lignes jointes
    /// par [#SAUT].
    private static String infobulleCarre(CarreAgrege carre, List<PointAgrege> geolocalises, List<PointAgrege> sansGps) {
        String entete = carre.nomConvivial() != null && !carre.nomConvivial().isBlank()
                ? carre.nomConvivial() + " (" + carre.numeroCarre() + ")"
                : "Carré " + carre.numeroCarre();
        List<PointAgrege> affiches = new ArrayList<>(geolocalises);
        affiches.addAll(sansGps);
        List<String> lignes = new ArrayList<>();
        lignes.add(entete);
        lignes.add(quantite(carre.nombrePassages(), "passage") + " · " + resumePoints(geolocalises, sansGps));
        String repartition = repartitionStatuts(affiches);
        if (!repartition.isEmpty()) {
            lignes.add(repartition);
        }
        return String.join(SAUT, lignes);
    }

    /// Décompte des points **affichés** sur le carré, en distinguant ceux qui ont un GPS de ceux placés au
    /// centre faute de coordonnées : `2 points` (tous GPS), `3 points à localiser (sans GPS)` (aucun GPS),
    /// `2 points GPS · 1 à localiser` (mixte). Évite l'incohérence « 0 point » alors que des marqueurs sont
    /// visibles (#153).
    private static String resumePoints(List<PointAgrege> geolocalises, List<PointAgrege> sansGps) {
        int avecGps = geolocalises.size();
        int aLocaliser = sansGps.size();
        if (aLocaliser == 0) {
            return quantite(avecGps, UNITE_POINT);
        }
        if (avecGps == 0) {
            return quantite(aLocaliser, UNITE_POINT) + " à localiser (sans GPS)";
        }
        return quantite(avecGps, UNITE_POINT) + " GPS · " + aLocaliser + " à localiser";
    }

    /// Mini-stats d'un **point** au survol : son libellé, son total de passages et son statut dominant.
    private static String infobullePoint(CarreAgrege carre, PointAgrege point) {
        String statut = point.statutDominant() == null
                ? "Aucun passage qualifié"
                : "Statut : " + point.statutDominant().libelle();
        return String.join(SAUT, libellePoint(carre, point), quantite(point.nombrePassages(), "passage"), statut);
    }

    /// Info-bulle d'un point **sans GPS** : les mêmes mini-stats, suivies d'un avertissement explicite que
    /// la position affichée est approchée (centre du carré), pour ne pas l'interpréter comme un GPS mesuré.
    private static String infobullePointApproche(CarreAgrege carre, PointAgrege point) {
        return infobullePoint(carre, point) + SAUT + "⚠ Position approximative (centre du carré, GPS manquant)";
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
            case DEPOT_EN_COURS -> Color.web("#148f77"); // entre « prêt » (cyan) et « déposé » (vert)
            case DEPOSE -> Color.web("#1e8449");
        };
    }
}
