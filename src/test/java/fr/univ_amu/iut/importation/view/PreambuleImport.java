package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Respiration;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import org.testfx.api.FxRobot;
import org.testfx.util.NodeQueryUtils;
import org.testfx.util.WaitForAsyncUtils;

/// Amener une nuit **réellement importée** sur le banc, pour les gestes qui commencent après.
///
/// Le passage pivot, son diagnostic, sa vérification : tous supposent une nuit importée, et aucun ne
/// peut la fabriquer. Bouchonner `ServicePassage` donnerait un écran nourri d'un détail inventé - les
/// volumes, la durée et le nombre de séquences que `S2-23` demande de lire n'auraient alors aucun
/// rapport avec une nuit, et le clip serait convaincant et creux (ADR 4142).
///
/// Ce préambule vit dans `importation.view` et non chez `recette` pour une raison de portée :
/// [ImportationController#selecteur()] y est de portée paquet, et c'est par lui que passe la
/// désignation de la source. Un sélecteur natif figerait TestFX (#1431).
public final class PreambuleImport {

    /// L'inspection balaie le dossier hors du fil JavaFX : elle n'est pas instantanée.
    private static final int APPARITION_SECONDES = 30;

    /// La fin d'un import freiné : copie protégée, renommage et transformation, une seconde par fichier.
    private static final int FIN_SECONDES = 180;

    /// L'action suivante du compte rendu de fin d'import, telle que `CompteRenduDeFinImport` la nomme.
    private static final String LIBELLE_SUITE = "Ouvrir le passage";

    private PreambuleImport() {}

    /// Ouvre l'assistant depuis la fiche du carré, désigne `carte`, rattache au premier point, importe,
    /// puis ouvre le passage produit.
    ///
    /// Chaque geste le rejoue en entier plutôt que de reprendre l'écran laissé par le précédent : trois
    /// clips, trois histoires complètes. Un geste qui hériterait de l'état d'un autre ne montrerait pas
    /// d'où il part, et l'ordre des cas déciderait de ce que chacun filme.
    public static void importerUneNuitEtOuvrirSonPassage(FxRobot robot, Navigateur navigateur, Path carte)
            throws TimeoutException {
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        controleur(navigateur).selecteur().definir(repondant(carte));
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> !texte(robot, "#labelOriginaux").isBlank(),
                "l'inspection n'a jamais rendu son compte d'originaux : le rattachement ne propose rien"
                        + " tant qu'elle n'a pas lu la carte");

        ComboBox<?> points = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        robot.interact(() -> points.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        // L'assistant est plus haut que la scène : le bouton est sous le bord, et TestFX refuse de
        // cliquer ce qu'on ne voit pas.
        GesteVisible.amenerDansLeCadre(robot, "#boutonImporter");
        GesteVisible.cliquer(robot, "#boutonImporter");

        attendre(
                FIN_SECONDES,
                () -> estVisible(robot, "#compteRenduChiffre"),
                "l'import n'a pas abouti : sans nuit importée, il n'y a pas de passage à ouvrir, et les"
                        + " gestes qui commencent ici n'ont rien à montrer");

        // Le chemin vers le passage passe par l'ACTION du compte rendu, et non par `#boutonOuvrirNuit`
        // - celui-là appartient à la zone d'avertissement de numéro, et ne paraît que si la nuit était
        // déjà récupérée. Mesuré : après un import nominal il est présent, mais `visible=false` et
        // `managed=false`.
        //
        // `CompteRenduDeFinImport` le dit en toutes lettres : « un compte rendu ne se termine pas sur
        // Fermer ». C'est donc bien ce bouton-là que l'utilisateur prend, et que le clip doit montrer.
        amenerLActionDansLeCadre(robot);
        GesteVisible.cliquer(robot, LIBELLE_SUITE);
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> estVisible(robot, "#stepper"),
                "le passage pivot ne s'est pas ouvert après l'import : c'est par « " + LIBELLE_SUITE
                        + " » que l'utilisateur y arrive, et un banc qui y sauterait ne montrerait pas ce"
                        + " chemin");
    }

    /// Fait venir « Ouvrir le passage » dans le cadre, en REJOUANT le défilement jusqu'à ce qu'elle
    /// soit atteignable au sens de [org.testfx.util.NodeQueryUtils#isVisible()] - le prédicat même
    /// dont `moveTo` se sert.
    ///
    /// Une seule passe ne suffit pas : [GesteVisible#amenerDansLeCadre] calcule la position une fois,
    /// et le compte rendu vient de paraître. Mesurée à cet instant, la cible rend souvent une largeur
    /// nulle au lieu de sa place ; le panneau s'arrête à mi-course et le bouton reste sous le bord.
    /// Vu rouge trois fois en intégration sur la suite complète, jamais seul.
    ///
    /// @throws TimeoutException si l'action ne vient jamais dans le cadre
    private static void amenerLActionDansLeCadre(FxRobot robot) throws TimeoutException {
        attendre(
                APPARITION_SECONDES,
                () -> {
                    GesteVisible.amenerDansLeCadre(robot, LIBELLE_SUITE);
                    return robot.lookup(LIBELLE_SUITE)
                            .match(NodeQueryUtils.isVisible())
                            .tryQuery()
                            .isPresent();
                },
                "« " + LIBELLE_SUITE + " » n'est jamais venue dans le cadre : le compte rendu de fin"
                        + " d'import porte cette action, et c'est par elle que l'utilisateur atteint le"
                        + " passage");
    }

    /// Le contrôleur de l'écran affiché, pris chez le navigateur qui le détient.
    ///
    /// `Injector#getInstance` en rendrait un AUTRE : il n'est pas singleton, et celui de la scène a été
    /// créé par le `FXMLLoader` de la navigation. Poser le double sur un contrôleur absent de l'écran
    /// laisserait « Parcourir » ouvrir le dialogue natif, qui fige le banc.
    private static ImportationController controleur(Navigateur navigateur) {
        Object courant = navigateur.historique().getLast().controleur();
        if (!(courant instanceof ImportationController assistant)) {
            throw new IllegalStateException("L'écran affiché n'est pas l'assistant d'import mais "
                    + (courant == null ? "rien" : courant.getClass().getSimpleName())
                    + " : le clic sur « Importer une nuit » n'a pas mené où la session le dit.");
        }
        return assistant;
    }

    /// Un sélecteur qui répond `carte` à la demande de dossier, et refuse d'écrire.
    private static SelecteurFichier repondant(Path carte) {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.of(carte);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                return Optional.of(carte);
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                throw new AssertionError("l'import LIT une source : ce préambule n'écrit aucun fichier");
            }
        };
    }

    private static void attendre(int secondes, java.util.concurrent.Callable<Boolean> condition, String siJamais)
            throws TimeoutException {
        try {
            WaitForAsyncUtils.waitFor(secondes, TimeUnit.SECONDS, condition);
        } catch (TimeoutException jamais) {
            throw new TimeoutException(siJamais);
        }
    }

    private static boolean estVisible(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        return noeud != null && noeud.isVisible() && noeud.getParent() != null;
    }

    private static String texte(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        return noeud instanceof Labeled libelle && libelle.getText() != null ? libelle.getText() : "";
    }
}
