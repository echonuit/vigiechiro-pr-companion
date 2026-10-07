package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.recette.Attente;
import javafx.scene.Node;
import org.testfx.api.FxRobot;

/// L'inspection d'une source désignée a-t-elle **conclu** (#6002) ?
///
/// Douze attentes disaient l'attendre en lisant `#labelOriginaux`, dont le texte n'est jamais vide :
/// il affiche « 0 enregistrement(s) WAV détecté(s) » dès que l'écran est chargé. Elles ne pouvaient
/// pas expirer. Deux cas qui affirment l'**absence** d'un bandeau passaient donc sans inspection, et
/// un cas qui en affirme la présence rougissait au lieu d'attendre, dès que l'inspection tardait.
///
/// Le signal est celui du produit : la section d'inspection n'est visible que pour un dossier
/// inspecté, et le modèle de vue pose ce drapeau en **dernier**, après les bandeaux.
///
/// Il vaut pour la première désignation d'un écran. Une seconde, sur le même écran, trouverait la
/// section visible depuis la première ; aucun banc ne le fait, chacun ouvrant un assistant neuf. Et
/// une inspection qui **échoue** ne la rend jamais visible : elle s'attend par son message d'erreur.
public final class InspectionConclue {

    private static final String SECTION = "#sectionInspection";

    private InspectionConclue() {}

    /// Attend la conclusion, ou expire en disant `sinon` : ce que le cas ne peut pas prouver sans elle.
    public static void attendre(FxRobot robot, String sinon, long delaiMs) {
        Attente.queSurLeFil(
                () -> robot.lookup(SECTION).tryQuery().map(Node::isVisible).orElse(false), sinon, delaiMs);
    }
}
