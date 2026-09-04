package fr.univ_amu.iut.diagnostic.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.importation.view.PreambuleImport;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CarteDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.ExecuteurTacheRalenti;
import fr.univ_amu.iut.recette.GesteVisible;
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
import java.util.regex.Pattern;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le diagnostic d'un passage, l'écran où l'on va voir **pourquoi** une nuit est ce qu'elle est
/// (#4718).
///
/// Le banc part d'un vrai import, par [PreambuleImport], et rejoint le diagnostic par la carte du
/// passage - mesuré ouverte sur un passage fraîchement importé. Bouchonner `ServiceDiagnostic` aurait
/// donné un écran nourri d'un relevé inventé, alors que `S2-34` et `S2-39` demandent justement de lire
/// des mesures et d'en compter (ADR 4142).
///
/// Le chrome entier est monté, et ce n'est pas un confort : `S2-39` lit la **barre de statut**, qui
/// n'appartient pas à cet écran-ci.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioDiagnosticPassageTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final String FIXTURE = "sd-nominale";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    /// Une étiquette d'axe horaire : `22` sur un pas d'heure pleine, `22:45` sur un pas intercalaire.
    private static final Pattern HEURE = Pattern.compile("\\d{2}(:\\d{2})?");

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
            // S2-66 rejoint la liste : ce banc éprouvait déjà l'avertissement et ce qu'il dit, sans
            // que la session porte une case pour lui. S2-67, lui, demande une nuit qui COUVRE la
            // fenêtre, que cette carte n'a pas : il reste à jouer à la main, et c'est dit dans la
            // session (#4984).
            value = {"S2-34", "S2-35", "S2-36", "S2-37", "S2-38", "S2-39", "S2-66"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-34 à S2-39 et S2-66 · lire le diagnostic : la courbe, les listes, la nuit, le GPS, le pied")
    void lire_le_diagnostic_d_un_passage(FxRobot robot) throws TimeoutException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);

        // La carte « Diagnostic » est OUVERTE sur un passage fraîchement importé, et c'est structurel :
        // elle ne porte aucune liaison de grisage, sa présence tenant à celle du port dans l'injecteur.
        // Une carte de consultation s'ouvre dès que son écran existe ; ce sont les cartes d'ACTION qui
        // se ferment selon l'avancement.
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonDiagnostic");
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> robot.lookup("#listeAnomalies").tryQuery().isPresent(),
                "le diagnostic ne s'est pas ouvert depuis le passage : c'est par sa carte que"
                        + " l'observateur y arrive, et sans l'écran aucun des six cas n'a de quoi se lire",
                APPARITION_SECONDES * 1000L);
        // L'écran de diagnostic vient de s'ouvrir, et c'est LUI que le cas donne à lire. Sans arrêt,
        // il paraît et le clip s'arrête : retour de la revue du 2026-09-04, « la fenêtre diagnostique
        // apparaît trop vite et on n'a pas le temps de lire ». Quatre scénarios l'ouvraient, aucun ne
        // le tenait.
        Respiration.leTempsDeLire(robot);

        // ─── S2-34 · la courbe climatique, et son axe GRADUÉ EN HEURES ───────────────────────────
        XYChart<?, ?> graphe = robot.lookup("#grapheClimat").queryAs(XYChart.class);

        assertThat(graphe.getData())
                .as("la courbe porte ses DEUX séries - température et hygrométrie. Une seule tracée"
                        + " laisserait l'écran paraître complet en ne disant que la moitié de la nuit")
                .hasSize(2);

        assertThat(graphe.getData().getFirst().getData())
                .as("et elle a des points : ils viennent du relevé de la nuit importée, pas d'un détail"
                        + " fabriqué. Une courbe vide se lirait comme une nuit sans climat")
                .isNotEmpty();

        // ─── S2-79 · le survol dit ce que la légende ne dit plus ────────────────────────────────
        // Depuis #5205 il n'y a plus de légende : l'infobulle est le SEUL endroit qui nomme la série.
        // Le clip doit donc la montrer, sans quoi il donne à voir une perte.
        //
        // Le point est pris au MILIEU de la courbe : près du bord, la bulle sortirait du cadre. La
        // caméra la composerait quand même, et le clip aurait l'air juste en étant illisible.
        List<? extends XYChart.Data<?, ?>> points = graphe.getData().getFirst().getData();
        Attente.que(
                () -> points.stream().anyMatch(donnee -> donnee.getNode() != null),
                "les points de la courbe ont pris un nœud : sans lui, rien à survoler",
                APPARITION_SECONDES * 1000L);
        GesteVisible.survoler(robot, points.get(points.size() / 2).getNode());

        assertThat(robot.lookup("#lblReleveAbsent").query().isVisible())
                .as("le signalement d'absence de relevé se TAIT quand le relevé est là. C'est le contrôle"
                        + " négatif de la courbe : sur un écran qui montrerait les deux, aucun des deux ne"
                        + " dirait rien")
                .isFalse();

        // L'axe est gradué en MINUTES depuis une origine et ÉTIQUETÉ en heures : c'est ce que la case
        // demande de lire, et c'est le convertisseur qui le porte. Lire l'étiquette, et non la valeur,
        // parce qu'un axe qui afficherait « 0, 60, 120 » satisferait un contrôle sur les bornes.
        NumberAxis abscisses = (NumberAxis) graphe.getXAxis();
        assertThat(abscisses.getTickLabelFormatter())
                .as("l'axe porte un convertisseur d'étiquettes : sans lui il montrerait des minutes"
                        + " brutes, que personne ne lit comme une heure de nuit")
                .isNotNull();

        String premiereEtiquette = abscisses.getTickLabelFormatter().toString(abscisses.getLowerBound());
        assertThat(premiereEtiquette)
                .as("et son étiquette est une HEURE, pas un nombre de minutes : « %s »", premiereEtiquette)
                .matches(HEURE);

        Respiration.leTempsDeLire(robot);

        // ─── S2-35 · les anomalies, ou leur substitut ────────────────────────────────────────────
        // La case admet les DEUX formes, et le banc ne tranche pas à la place du produit : sur la carte
        // nominale il n'y a aucune anomalie, donc c'est le substitut qui parle. Exiger une liste
        // peuplée demanderait à une nuit saine d'avoir des défauts.
        assertThat(listeOuSubstitut(robot, "#listeAnomalies"))
                .as("les anomalies s'affichent, ou leur substitut le dit. Une liste vide SANS substitut"
                        + " laisserait l'observateur devant un cadre blanc, sans savoir si le diagnostic"
                        + " n'a rien trouvé ou n'a pas tourné")
                .isTrue();

        // ─── S2-36 · les évènements du journal ───────────────────────────────────────────────────
        assertThat(listeOuSubstitut(robot, "#listeEvenements"))
                .as("les évènements du journal aussi : ce sont eux qui racontent ce que le capteur a fait"
                        + " pendant la nuit")
                .isTrue();

        assertThat(robot.lookup("#listeEvenements").queryAs(ListView.class).getItems())
                .as("et la carte nominale en porte : son journal n'est pas vide, donc c'est bien la liste"
                        + " qui parle ici, et non son substitut")
                .isNotEmpty();

        // ─── S2-37 · la fenêtre nocturne, et l'écart au protocole ────────────────────────────────
        // #5204 : la ligne dit la fenêtre en une seule grammaire, « A à B ». Les mots « coucher » et
        // « lever » ont quitté CETTE ligne pour l'alerte, qui les porte ; éprouver les HEURES plutôt
        // que les mots, c'est éprouver ce que la ligne apporte et non sa formulation du jour.
        assertThat(texte(robot, "#lblFenetreNuit"))
                .as("la cohérence horaire nomme la fenêtre nocturne. Sans elle, un verdict de couverture"
                        + " serait rendu sans son barème")
                .containsPattern("Nuit : \\d{2}:\\d{2} à \\d{2}:\\d{2}");

        // #5204 : les deux plages sont deux labels, pour que les séparateurs de la ligne soient tous
        // des nœuds frères et s'espacent pareil. Les éprouver séparément dit aussi que chacune est
        // là : une seule assertion sur la ligne entière laisserait passer la disparition de l'une.
        assertThat(texte(robot, "#lblPlageExigee"))
                .as("ce que le protocole exigeait. Un verdict sans elle se croit sur parole (#4988)")
                .contains("Protocole");

        assertThat(texte(robot, "#lblPlageEnregistree"))
                .as("et ce qui a été enregistré, en face")
                .contains("Enregistré");

        // La nuit semée ne couvre pas la fenêtre exigée : mesuré, et c'est ce qui rend le cas jouable
        // sans fabriquer une seconde carte. L'avertissement est donc attendu visible ET parlant.
        assertThat(robot.lookup("#lblAlerteHorsNuit").query().isVisible())
                .as("l'avertissement paraît, parce que cet enregistrement ne couvre pas toute la fenêtre"
                        + " que le protocole demande. Une alerte qui se tairait ici laisserait passer une"
                        + " nuit incomplète")
                .isTrue();

        assertThat(texte(robot, "#lblAlerteHorsNuit"))
                .as("elle DIT ce qui manque, et ne se contente pas de signaler")
                .isNotBlank();

        Respiration.leTempsDeLire(robot);

        // ─── S2-38 · l'état GPS du point, TOUJOURS visible ───────────────────────────────────────
        // « Toujours » est le fait : la ligne ne se masque pas selon l'état. Le point semé porte des
        // coordonnées, donc c'est « disponible » qui s'affiche ; ce que la case garde est que la ligne
        // soit là et qu'elle TRANCHE, au lieu de laisser deviner.
        assertThat(robot.lookup("#ligneGps").query().isVisible())
                .as("la ligne GPS est visible. Masquée quand tout va bien, elle n'apprendrait son"
                        + " existence à l'observateur que le jour où quelque chose manque")
                .isTrue();

        assertThat(texte(robot, "#lblGps"))
                .as("et elle tranche entre les deux états que la case nomme, au lieu d'un libellé vide"
                        + " qui se lirait comme un défaut d'affichage")
                .containsAnyOf("disponible", "non renseigné");

        // ─── S2-39 · la barre de statut, qui n'appartient PAS à cet écran ────────────────────────
        // Le seul des six cas à traverser deux écrans : `DiagnosticController` publie un `ZonesStatut`
        // (#693, #3548) et c'est le chrome qui le rend. Un banc monté sur la seule vue du diagnostic
        // n'aurait rien à lire ici.
        assertThat(robot.lookup("#barreStatut").query().isVisible())
                .as("la barre de statut est montrée : le chrome la masque tant qu'aucune zone n'a de"
                        + " contenu, donc sa présence dit déjà que le diagnostic a rendu son résumé")
                .isTrue();

        String pied = texte(robot, "#piedCentre");

        assertThat(pied)
                .as(
                        "la zone centre porte l'enregistreur diagnostiqué et le nombre de mesures"
                                + " climatiques : « %s »",
                        pied)
                .isNotBlank()
                .contains("mesures");

        // Le compte annoncé et la courbe tracée sont le MÊME relevé, vus de deux écrans. Comparer les
        // deux plutôt que d'attendre un nombre écrit en dur : un littéral suivrait la fixture sans rien
        // garder, alors que cette égalité-ci rougirait si l'un des deux cessait de lire l'autre.
        int pointsTraces = graphe.getData().getFirst().getData().size();

        assertThat(pied)
                .as(
                        "et ce compte est celui de la courbe - %d points tracés. Le pied et le graphe lisent"
                                + " le même relevé ; deux nombres différents diraient que l'un des deux ment",
                        pointsTraces)
                .contains(String.valueOf(pointsTraces));

        Respiration.leTempsDeLire(robot);
    }

    // --------------------------------------------------------------------------------------------

    /// La liste dit-elle quelque chose : ses entrées, ou le substitut qui explique leur absence ?
    ///
    /// La case admet les deux formes, et le banc ne tranche pas à la place du produit. Ce qu'elle
    /// refuse est le troisième cas - une liste vide devant laquelle rien n'explique le vide.
    private static boolean listeOuSubstitut(FxRobot robot, String id) {
        ListView<?> liste = robot.lookup(id).queryAs(ListView.class);
        if (!liste.getItems().isEmpty()) {
            return true;
        }
        Node substitut = liste.getPlaceholder();
        return substitut instanceof Labeled libelle
                && libelle.getText() != null
                && !libelle.getText().isBlank();
    }

    private static String texte(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        return noeud instanceof Labeled libelle && libelle.getText() != null ? libelle.getText() : "";
    }
}
