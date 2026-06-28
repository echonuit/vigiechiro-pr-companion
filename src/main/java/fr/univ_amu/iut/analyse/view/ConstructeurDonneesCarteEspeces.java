package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.view.carte.CarreGeo;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.commun.view.carte.FournisseurEmpriseCarre;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.paint.Color;

/// Traduit l'inventaire **par carré** (`CarreEspeces`) en [DonneesCarte] pour la [CarteSites] : une
/// **choroplèthe de richesse** (couleur du carré ∝ nombre d'espèces distinctes). C'est ici, couche `view`,
/// que la couleur entre en jeu (le ViewModel reste agnostique de JavaFX, ArchUnit `viewmodel_sans_javafx_ui`),
/// exactement comme `multisite/view/ConstructeurDonneesCarte`. Pur et **testable sans IHM**.
///
/// Deux lectures selon `carresSurbrilles` (les carrés où l'espèce sélectionnée est présente) :
/// - **vide** (aucune espèce sélectionnée) → choroplèthe de richesse : vert d'autant plus opaque que le
///   carré est riche ;
/// - **non vide** → **répartition d'une espèce** : les carrés où elle est présente ressortent (vert
///   franc), les autres sont **atténués** (gris très clair).
///
/// L'emprise vient du **carroyage officiel** via [FournisseurEmpriseCarre#parDefaut()] : le seul **numéro
/// de carré** suffit (aucun GPS). Un carré absent du carroyage n'est **pas tracé** (mais reste dans le
/// tableau/inventaire).
final class ConstructeurDonneesCarteEspeces {

    /// Carroyage officiel d'abord, repli autour des points ensuite (ici sans points : numéro seul).
    private static final FournisseurEmpriseCarre EMPRISE = FournisseurEmpriseCarre.parDefaut();

    /// Teinte de la choroplèthe : vert du prisme « Espèces & biodiversité ».
    private static final String VERT_RICHESSE = "#1e8449";

    /// Opacités min/max du remplissage selon la richesse (un carré peu riche reste visible).
    private static final double OPACITE_MIN = 0.15;
    private static final double OPACITE_MAX = 0.60;

    /// Remplissage d'un carré **où l'espèce sélectionnée est absente** (atténué, en mode répartition).
    private static final Color ABSENT = Color.web("#7f8c8d", 0.10);

    private static final String SEPARATEUR = " · ";

    private ConstructeurDonneesCarteEspeces() {}

    /// Construit les données de carte. `carresSurbrilles` = numéros des carrés de l'espèce sélectionnée
    /// (vide = choroplèthe de richesse sur tous les carrés).
    ///
    /// En mode **répartition** (une espèce sélectionnée), les carrés **où elle est présente** gardent leur
    /// **couleur de richesse** (des verts différents selon le nombre d'espèces du carré), seuls les carrés
    /// **où elle est absente** sont atténués. On voit ainsi *où* l'espèce vit **et** la richesse de ces
    /// carrés.
    static DonneesCarte depuis(List<CarreEspeces> carres, Set<String> carresSurbrilles) {
        int richesseMax = carres.stream().mapToInt(CarreEspeces::richesse).max().orElse(0);
        boolean repartition = !carresSurbrilles.isEmpty();
        List<CarreGeo> traces = new ArrayList<>();
        for (CarreEspeces carre : carres) {
            EMPRISE.emprise(carre.numeroCarre(), List.of()).ifPresent(emprise -> {
                Color remplissage = repartition && !carresSurbrilles.contains(carre.numeroCarre())
                        ? ABSENT
                        : couleurRichesse(carre.richesse(), richesseMax);
                traces.add(new CarreGeo(carre.numeroCarre(), emprise, remplissage, infobulle(carre)));
            });
        }
        return new DonneesCarte(traces, List.of());
    }

    /// Vert d'autant plus opaque que le carré est riche (normalisé sur le max ; max nul → opacité min).
    private static Color couleurRichesse(int richesse, int richesseMax) {
        double fraction = richesseMax <= 0 ? 0.0 : (double) richesse / richesseMax;
        return Color.web(VERT_RICHESSE, OPACITE_MIN + fraction * (OPACITE_MAX - OPACITE_MIN));
    }

    /// Info-bulle (survol) : `Carré N · site · X espèces · Y détections · période`.
    private static String infobulle(CarreEspeces carre) {
        StringBuilder texte = new StringBuilder("Carré ").append(carre.numeroCarre());
        if (carre.nomSite() != null && !carre.nomSite().isBlank()) {
            texte.append(SEPARATEUR).append(carre.nomSite());
        }
        texte.append(SEPARATEUR).append(quantite(carre.richesse(), "espèce"));
        texte.append(SEPARATEUR).append(quantite(carre.nbObservations(), "détection"));
        texte.append(SEPARATEUR).append(periode(carre.anneeMin(), carre.anneeMax()));
        return texte.toString();
    }

    private static String periode(int anneeMin, int anneeMax) {
        return anneeMin == anneeMax ? Integer.toString(anneeMin) : anneeMin + "–" + anneeMax;
    }

    private static String quantite(int nombre, String unite) {
        return nombre + " " + unite + (nombre > 1 ? "s" : "");
    }
}
