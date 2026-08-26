package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.importation.view.PreambuleImport;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CarteDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.ExecuteurTacheRalenti;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le passage pivot de `S2`, l'écran où une nuit importée devient une chose sur laquelle on agit
/// (#4557).
///
/// Le banc part d'un **vrai import**, par [PreambuleImport]. Bouchonner `ServicePassage` aurait donné un
/// écran nourri d'un détail inventé, et `S2-23` demande justement de lire des volumes, une durée et un
/// nombre de séquences : fabriqués, ils n'auraient aucun rapport avec une nuit, et le clip serait
/// convaincant et creux (ADR 4142). Le préambule coûte une dizaine de secondes ; c'est le prix d'un
/// écran qui montre de vraies données.
///
/// L'exécuteur est freiné comme dans `ScenarioImportNominalTest`, pour la même raison : l'import de
/// fixtures générées dure des millisecondes, et sans frein le préambule traverserait l'écran
/// d'avancement sans qu'aucune image ne sorte.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioPassagePivotTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final String FIXTURE = "sd-nominale";

    /// Les deux étapes acquises à la naissance d'un passage : il naît importé, et la transformation
    /// suit dans le même geste.
    private static final List<String> ACQUISES = List.of("Importé", "Transformé");

    /// Les cartes d'action de l'écran, pour que « une seule est recommandée » se mesure sur TOUTES.
    private static final List<String> CARTES = List.of(
            "#boutonVerifier",
            "#boutonValidation",
            "#boutonDepot",
            "#boutonDiagnostic",
            "#boutonActivite",
            "#boutonSynthese");

    /// Ce que la carte nominale porte, et que le résumé du passage doit retrouver.
    private static final int ORIGINAUX = 6;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    private Path carteSd;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        carteSd = CarteDeRecette.materialiser(FIXTURE);

        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .remplacer(liaison -> liaison.bind(ExecuteurTache.class)
                        .toInstance(new ExecuteurTacheRalenti(new ExecuteurTacheAsynchrone(), PAUSE_PAR_FICHIER_MS)))
                .semer(this::poserLeCarreEtSonPoint)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirDetail(CARRE))
                .montrer(stage);
    }

    private void poserLeCarreEtSonPoint(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Observateur"));
        ServiceSites service = inj.getInstance(ServiceSites.class);
        Site carre = service.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(carre.id(), "A1", 43.42, 5.11, "Près du grand chêne");
    }

    @Test
    @CasDeRecette(
            value = {"S2-18", "S2-19", "S2-20", "S2-21", "S2-22", "S2-23", "S2-24", "S2-25", "S2-26"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-18 à S2-26 · lire le passage pivot : où il en est, et ce qu'on peut y faire")
    void lire_le_passage_pivot(FxRobot robot) throws TimeoutException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);

        Respiration.surLeMomentCle(robot);

        // ─── S2-18 · les deux premières étapes sont ACQUISES ─────────────────────────────────────
        // « Franchi » au sens de la case veut dire « derrière nous », et le produit distingue trois
        // états : franchie, courante, à venir. Un passage naît au statut TRANSFORME, donc « Importé »
        // est franchie et « Transformé » est COURANTE - c'est là qu'on en est.
        //
        // Le premier tir l'a dit, et il avait raison contre moi : exiger `etape-franchie` sur les deux
        // aurait demandé au produit d'oublier où en est le passage.
        assertThat(etapesAcquises(robot))
                .as(
                        "le stepper marque « %s » comme acquises : ni l'une ni l'autre n'est à venir. Un"
                                + " passage qui naîtrait au premier statut laisserait croire qu'il reste à"
                                + " transformer, alors que l'import vient de le faire",
                        String.join(" » et « ", ACQUISES))
                .containsAll(ACQUISES);

        assertThat(etapesAVenir(robot))
                .as("et les trois suivantes restent À VENIR. Sans ce second constat, un stepper qui"
                        + " marquerait TOUT comme acquis passerait : il ne dirait plus où en est le"
                        + " passage, ce qui est précisément ce qu'on vient lire ici")
                .containsExactly("Vérifié", "Prêt à déposer", "Déposé");

        Respiration.leTempsDeLire(robot);

        // ─── S2-19 · UNE SEULE carte recommandée, et c'est Vérifier ──────────────────────────────
        // Le liseré est une pseudo-classe, pas une couleur : on lit ce que le produit a décidé.
        // « Une seule » est le fait, et il ne se constate pas en regardant Vérifier seule.
        assertThat(cartesRecommandees(robot))
                .as("une seule carte porte le liseré « recommandée », et c'est Vérifier. Deux cartes"
                        + " recommandées ne recommanderaient rien, et zéro laisserait l'observateur"
                        + " choisir sans indication après un import")
                .containsExactly("#boutonVerifier");

        // ─── S2-20 et S2-21 · « Sons & validation » est fermée, et le DIT ────────────────────────
        assertThat(robot.lookup("#boutonValidation").query().isDisabled())
                .as("« Sons & validation » est grisée : elle n'a de sens qu'après le dépôt, et l'ouvrir"
                        + " avant mènerait à un écran sans rien à valider")
                .isTrue();

        assertThat(explication(robot, "#enveloppeValidation"))
                .as("et son grisé est EXPLIQUÉ. Un bouton mort sans raison est un défaut à lui seul :"
                        + " l'observateur ne sait pas s'il doit attendre, ou s'il a mal fait")
                .isNotBlank();

        // ─── S2-22 · le bandeau d'identité, ses cinq faits ───────────────────────────────────────
        // Chacun nommé séparément : un bandeau à moitié rempli passerait si on ne regardait que le
        // premier libellé, et c'est l'identité de la nuit qui se lit ici.
        assertThat(texte(robot, "#lblPlageHoraire"))
                .as("la plage horaire vient du journal du capteur : sans elle, rien ne dit sur quelle"
                        + " nuit on travaille")
                .isNotBlank();
        assertThat(texte(robot, "#lblEnregistreur"))
                .as("l'enregistreur est nommé : c'est lui qui rattache la nuit à un matériel, et le"
                        + " numéro de série vient du journal")
                .isNotBlank();
        assertThat(texte(robot, "#lblStatut"))
                .as("le statut est écrit en toutes lettres, en plus du stepper : le stepper montre le"
                        + " chemin, le bandeau dit où on en est")
                .isNotBlank();
        assertThat(texte(robot, "#lblVerdict"))
                .as("le verdict paraît même quand il n'est pas saisi - « non saisi » est un état, et"
                        + " un libellé vide se lirait comme un défaut d'affichage")
                .isNotBlank();

        // ─── S2-23 · le résumé chiffré de la nuit ────────────────────────────────────────────────
        // Ce sont ces quatre-là qui exigent un VRAI import : nourris d'un détail fabriqué, ils
        // n'auraient aucun rapport avec la carte qu'on vient de lire.
        assertThat(texte(robot, "#lblVolBruts"))
                .as("le volume des bruts : ce que la carte pesait")
                .isNotBlank();
        assertThat(texte(robot, "#lblVolTransformes"))
                .as("le volume des transformés : ce que l'import a produit")
                .isNotBlank();
        assertThat(texte(robot, "#lblDureeEnregistree"))
                .as("la durée enregistrée, qui dit si la nuit est complète")
                .isNotBlank();
        assertThat(texte(robot, "#lblNbSequences"))
                .as(
                        "le nombre de séquences : la carte en portait %d, et c'est ce compte qui suit la"
                                + " nuit jusqu'au dépôt",
                        ORIGINAUX)
                .contains(String.valueOf(ORIGINAUX));

        // ─── S2-24 · « Voir la participation » est fermé, et le DIT ──────────────────────────────
        assertThat(robot.lookup("#boutonOuvrirPortail").query().isDisabled())
                .as("le passage n'est lié à aucune participation Vigie-Chiro : le carré 640380 est"
                        + " local et non relié, et c'est le garde-fou de la séance")
                .isTrue();

        assertThat(explication(robot, "#enveloppeOuvrirPortail"))
                .as("et la raison est donnée. « Voir la participation » grisé sans explication ferait"
                        + " croire à une panne, là où c'est un état parfaitement normal")
                .isNotBlank();

        // ─── S2-25 · « Supprimer » est OUVERT ────────────────────────────────────────────────────
        // Le contrôle négatif des deux précédents : si tout était grisé, leur constat ne dirait rien.
        assertThat(robot.lookup("#boutonSupprimer").query().isDisabled())
                .as("« Supprimer » est actif tant que le passage n'est pas déposé. C'est aussi ce qui"
                        + " donne son sens aux deux constats précédents : sur un écran où tout serait"
                        + " grisé, constater un grisé ne prouverait rien")
                .isFalse();

        // ─── S2-26 · « Réactiver » n'a pas lieu d'être ───────────────────────────────────────────
        assertThat(absentOuFerme(robot, "#boutonReactiver"))
                .as("« Réactiver ce passage » est absent ou grisé : l'audio est complet, il n'y a rien"
                        + " à réactiver. Le proposer ici offrirait un geste sans objet")
                .isTrue();

        Respiration.leTempsDeLire(robot);
    }

    // --------------------------------------------------------------------------------------------

    /// Les cartes d'action qui portent le liseré « recommandée ».
    ///
    /// Une pseudo-classe, et non une couleur : `PassageController` la pose sur la carte qu'il conseille,
    /// et c'est cette décision-là que la case demande de lire.
    private static List<String> cartesRecommandees(FxRobot robot) {
        return CARTES.stream()
                .filter(id -> {
                    Node carte = robot.lookup(id).tryQuery().orElse(null);
                    return carte != null
                            && carte.getPseudoClassStates().contains(PseudoClass.getPseudoClass("recommandee"));
                })
                .toList();
    }

    /// Ce que l'écran dit d'un geste fermé, lu sur son ENVELOPPE.
    ///
    /// `Tooltip.install` ne range pas l'infobulle dans une propriété publique - `getTooltip()` n'existe
    /// que sur `Control`, et l'enveloppe est un `StackPane`. `InfobulleDeBlocage` sait la relire, et
    /// c'est lui qu'on emploie : une seconde façon de lire aurait divergé de la première.
    ///
    /// L'enveloppe et non le bouton, parce qu'un nœud désactivé ne reçoit plus le survol : c'est toute
    /// la raison d'être d'`IndicateurBlocage`. Mon premier relevé lisait le bouton, et rendait vide même
    /// une fois l'explication posée.
    private static String explication(FxRobot robot, String idEnveloppe) {
        Node enveloppe = robot.lookup(idEnveloppe).tryQuery().orElse(null);
        return enveloppe == null ? "" : InfobulleDeBlocage.texteDe(enveloppe);
    }

    /// Vrai si le geste est absent de l'écran, ou présent mais fermé.
    ///
    /// La case admet les deux, et le banc ne doit pas trancher à sa place : ce qui compte est qu'on ne
    /// puisse pas le prendre.
    private static boolean absentOuFerme(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        return noeud == null || !noeud.isVisible() || !noeud.isManaged() || noeud.isDisabled();
    }

    /// Les étapes ACQUISES : franchies, ou celle où l'on est.
    ///
    /// `Stepper.reconstruire` pose sur chaque puce `etape` puis `etape-<état>`, parmi trois : franchie,
    /// courante, à venir. Lire la classe plutôt qu'une couleur ou une position dit ce que le produit a
    /// décidé, et non ce que le rendu en a fait.
    private static List<String> etapesAcquises(FxRobot robot) {
        return etapes(robot, "etape-franchie", "etape-courante");
    }

    /// Les étapes qui restent À VENIR, pour que le constat d'acquis ne puisse pas être vrai de tout.
    private static List<String> etapesAVenir(FxRobot robot) {
        return etapes(robot, "etape-a_venir");
    }

    private static List<String> etapes(FxRobot robot, String... classes) {
        List<String> voulues = List.of(classes);
        return robot.lookup("#stepper").query().lookupAll(".etape").stream()
                .filter(puce -> puce.getStyleClass().stream().anyMatch(voulues::contains))
                .filter(Labeled.class::isInstance)
                .map(puce -> ((Labeled) puce).getText())
                .toList();
    }

    private static String texte(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        return noeud instanceof Labeled libelle && libelle.getText() != null ? libelle.getText() : "";
    }
}
