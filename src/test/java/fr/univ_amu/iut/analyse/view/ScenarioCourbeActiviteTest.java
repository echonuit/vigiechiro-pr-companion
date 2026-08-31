package fr.univ_amu.iut.analyse.view;

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
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.importation.view.PreambuleImport;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CarteDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.ExecuteurTacheRalenti;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Jugement;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// La courbe d'activité, **premier geste perceptif** du palier 2 (#4800).
///
/// ## Ce que ce banc garde, et ce qu'il ne garde pas
///
/// Les six cas portent `jugement = HUMAIN` : « les cinq couleurs se distinguent », « deux courbes qui
/// se croisent restent suivables à l'œil » - aucune de ces phrases ne se prouve par une assertion. Le
/// banc garde donc le **mécanique** : cinq courbes et pas six, un croisement présent, l'aplat sous les
/// courbes, aucun trou entre les tranches, un axe qui n'excède pas la nuit, aucune étiquette de pic.
/// Le reste est ce que le clip montre, et que le relecteur tranche.
///
/// ## Pourquoi les espèces sont SEMÉES
///
/// Mesuré : la nuit nominale importée rend **zéro série** et l'état vide, les espèces venant de
/// l'analyse Tadarida - un autre geste, qui ne tourne pas sur le banc. Le préambule importe donc pour
/// de vrai, puis le banc pose des détections **sur ce passage-là** et rejoint l'écran par sa carte. Le
/// chemin reste filmé de bout en bout ; ce que le clip ne montre pas, et que la page des clips dit,
/// c'est d'où viennent les contacts.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioCourbeActiviteTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final String FIXTURE = "sd-nominale";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    /// La nuit de la carte nominale, celle que l'import vient de poser.
    private static final LocalDate NUIT = LocalDate.of(2026, 4, 22);

    /// Cinq espèces réelles du référentiel : les codes y sont capitalisés, mesuré - `Pippip` et non
    /// `PIPPIP`, faute de quoi la contrainte de clé étrangère refuse l'observation.
    /// **SIX** espèces pour cinq courbes : la sixième est la moins contactée, et l'écran ne doit pas la
    /// tracer. Sans elle, « les cinq PLUS contactées » ne serait pas éprouvé - mesuré, porter la limite
    /// du produit à six laissait le cas vert.
    private static final List<String> ESPECES = List.of("Pippip", "Pipkuh", "Barbar", "Nyclei", "Myodau", "Pipnat");

    /// Celle que son faible total doit laisser dehors.
    private static final String ESPECE_EN_TROP = "Pipnat";

    /// Le nombre de contacts par tranche horaire, une ligne par espèce.
    ///
    /// Le semis est **délibéré** : la première décroît, la deuxième croît - elles se **croisent**, ce que
    /// `S6-05` demande de pouvoir suivre à l'œil - et la troisième porte une tranche à **zéro**, sans
    /// quoi `S6-07` n'aurait rien à constater.
    private static final int[][] CONTACTS = {
        {6, 5, 4, 3, 2, 1, 1, 1},
        {1, 1, 2, 3, 4, 5, 6, 7},
        {3, 3, 0, 3, 3, 2, 2, 2},
        {2, 4, 2, 4, 2, 4, 2, 4},
        {5, 2, 5, 2, 5, 2, 5, 2},
        {1, 0, 0, 0, 0, 0, 0, 0},
    };

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
            value = {"S6-04", "S6-05", "S6-06", "S6-07", "S6-08", "S6-09"},
            jugement = Jugement.HUMAIN,
            portee = Portee.A_L_ECRAN)
    @DisplayName("S6-04 à S6-09 · lire la courbe d'activité : à regarder, cinq espèces se suivent")
    void lire_la_courbe_d_activite(FxRobot robot) throws TimeoutException, SQLException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);

        semerLesDetections();

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonActivite");
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> robot.lookup("#grapheActivite").query().isVisible(),
                "la courbe d'activité ne s'est pas ouverte : sans elle, aucun des six cas n'a d'écran",
                APPARITION_SECONDES * 1000L);

        XYChart<Number, Number> courbe = courbe(robot);

        // ─── S6-04 · cinq espèces, cinq teintes ─────────────────────────────────────────────────
        // Que les cinq teintes se DISTINGUENT est le verdict du relecteur, et le banc n'en garde rien :
        // JavaFX attribue `default-colorN` par index, donc une assertion sur ces classes serait vraie par
        // construction. Ce qui se tient ici est qu'il y ait cinq courbes, et pas six.
        assertThat(courbe.getData())
                .as("les cinq espèces les plus contactées sont tracées : c'est ce que l'écran promet, et"
                        + " une sixième courbe rendrait le graphe illisible")
                .hasSize(5);

        assertThat(courbe.getData().stream().map(XYChart.Series::getName).toList())
                .as("et c'est bien la MOINS contactée que l'écran laisse dehors. Six espèces sont"
                        + " détectées cette nuit-là : sans ce constat, le cas serait vert sur un produit"
                        + " qui les tracerait toutes")
                .noneMatch(nom -> nom.contains("Pipistrelle de Nathusius") || nom.equals(ESPECE_EN_TROP));

        assertThat(robot.lookup("#lblEtatVide").query().isVisible())
                .as("l'état vide se tait quand il y a de quoi tracer. C'est le contrôle négatif de la"
                        + " courbe : sur un écran qui montrerait les deux, aucun des deux ne dirait rien")
                .isFalse();

        Respiration.leTempsDeLire(robot);

        // ─── S6-05 · deux courbes se CROISENT réellement ─────────────────────────────────────────
        // Le cas demande qu'un croisement reste suivable. Le banc garde qu'il y en a un : sans lui, le
        // relecteur regarderait cinq courbes parallèles et ne pourrait rien juger.
        assertThat(seCroisent(courbe.getData().get(0), courbe.getData().get(1)))
                .as("deux des cinq courbes se croisent : l'une décroît quand l'autre monte. C'est la"
                        + " condition pour que « restent suivables à l'œil » ait un objet")
                .isTrue();

        // ─── S6-06 · l'aplat nocturne est là, et DERRIÈRE les courbes ────────────────────────────
        Node aplat = robot.lookup(".aplat-nuit").query();

        assertThat(aplat.isVisible())
                .as("l'aplat qui matérialise la fenêtre coucher -> lever est à l'écran : c'est lui qui"
                        + " dit ce qui est la nuit, et sans lui l'axe n'est qu'une suite d'heures")
                .isTrue();

        // **Ce que la mutation n'a pas pu montrer.** L'aplat est au rang 0 et les courbes après, dans le
        // même groupe - mesuré. Mais aucune mutation n'a su inverser cet ordre durablement : un
        // `toFront()` posé dans la mise en page ne survit pas à la passe suivante. Ce constat-ci est donc
        // vrai sans avoir été vu rouge, à la différence des autres.
        assertThat(aplat.getParent().getChildrenUnmodifiable().indexOf(aplat))
                .as("et il est posé AVANT les courbes dans le tracé, donc dessous. Au-dessus, il les"
                        + " masquerait - ce que la case interdit en toutes lettres")
                .isZero();

        // ─── S6-07 · une tranche sans contact TOUCHE zéro ────────────────────────────────────────
        // Semé exprès : la troisième espèce a une tranche vide. Un trou dans la ligne se lirait comme
        // une absence de mesure, alors que la mesure dit « aucun contact ».
        XYChart.Series<Number, Number> avecUnCreux = courbe.getData().stream()
                .filter(serie -> valeurs(serie).contains(0.0))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune courbe ne porte de tranche à zéro"));

        assertThat(ecartsEntrePoints(avecUnCreux))
                .as("les tranches se suivent SANS TROU : une tranche sans contact vaut zéro, elle ne"
                        + " manque pas. Une courbe qui sauterait laisserait croire que l'enregistrement"
                        + " s'est interrompu, alors que la mesure dit « aucun contact »")
                .hasSize(1);

        // ─── S6-08 · rien avant le début ni après la fin ─────────────────────────────────────────
        NumberAxis abscisses = (NumberAxis) courbe.getXAxis();
        double etendue = abscisses.getUpperBound() - abscisses.getLowerBound();

        assertThat(etendue)
                .as("l'axe couvre le cadre nocturne fixe - 18 h vers 8 h, soit quatorze heures - et pas"
                        + " davantage. Deux nuits se comparent parce que ce cadre ne bouge pas")
                .isEqualTo(14 * 60);

        assertThat(courbe.getData().stream()
                        .flatMap(serie -> serie.getData().stream())
                        .map(point -> point.getXValue().doubleValue())
                        .filter(x -> x < abscisses.getLowerBound() || x > abscisses.getUpperBound())
                        .toList())
                .as("et aucun point n'est tracé hors de ce cadre : un contact placé avant le début ou"
                        + " après la fin ferait lire une activité qui n'a pas été enregistrée")
                .isEmpty();

        // ─── S6-09 · sans la légende, on ne peut pas nommer une courbe ───────────────────────────
        // Le prix assumé du retrait des étiquettes de pic. Le banc garde qu'aucune n'est revenue ; que
        // le relecteur ne puisse effectivement pas nommer les courbes est ce que le clip montre.
        assertThat(courbe.isLegendVisible())
                .as("la légende est le SEUL endroit qui nomme les courbes, donc elle est à l'écran")
                .isTrue();

        assertThat(etiquettesDansLeTrace(courbe))
                .as("et le tracé ne porte aucune étiquette : les noms d'espèce ne reviennent pas sur les"
                        + " pics. C'est le prix assumé du retrait, et il se vérifie ici pour qu'il ne"
                        + " revienne pas sans qu'on le décide")
                .isEmpty();

        Respiration.leTempsDeLire(robot);
    }

    // --------------------------------------------------------------------------------------------

    /// Pose les détections sur le passage que l'import vient de créer.
    ///
    /// Les trois identifiants sont relus en base plutôt que devinés : c'est l'import qui les a écrits,
    /// et `original_recording` est obligatoire pour une séquence.
    private void semerLesDetections() throws SQLException {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        long idPassage;
        long idSession;
        long idOriginal;
        try (var connexion = source.getConnection();
                Statement requete = connexion.createStatement();
                ResultSet rendu = requete.executeQuery("SELECT p.id, s.id, o.id FROM passage p"
                        + " JOIN recording_session s ON s.passage_id = p.id"
                        + " JOIN original_recording o ON o.session_id = s.id LIMIT 1")) {
            rendu.next();
            idPassage = rendu.getLong(1);
            idSession = rendu.getLong(2);
            idOriginal = rendu.getLong(3);
        }

        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source).surLePassage(idPassage, idSession, idOriginal);
        jeu.ajouterResultats();
        for (int espece = 0; espece < ESPECES.size(); espece++) {
            for (int tranche = 0; tranche < CONTACTS[espece].length; tranche++) {
                for (int contact = 0; contact < CONTACTS[espece][tranche]; contact++) {
                    LocalDateTime quand = NUIT.atTime(21, 0).plusHours(tranche).plusMinutes(contact * 3L);
                    jeu.ajouterObservationA(quand, ESPECES.get(espece));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static XYChart<Number, Number> courbe(FxRobot robot) {
        return (XYChart<Number, Number>) robot.lookup("#grapheActivite").query();
    }

    /// Les écarts distincts entre abscisses consécutives d'une série.
    ///
    /// Un seul écart veut dire que les tranches se suivent sans trou. Deux ou plus signalent un saut -
    /// une tranche vide rendue absente au lieu d'être portée à zéro.
    private static Set<Double> ecartsEntrePoints(XYChart.Series<Number, Number> serie) {
        List<Double> abscisses = serie.getData().stream()
                .map(point -> point.getXValue().doubleValue())
                .sorted()
                .toList();
        Set<Double> ecarts = new HashSet<>();
        for (int i = 1; i < abscisses.size(); i++) {
            ecarts.add(abscisses.get(i) - abscisses.get(i - 1));
        }
        return ecarts;
    }

    /// Ces deux séries se croisent-elles : l'ordre de leurs valeurs s'inverse-t-il en chemin ?
    private static boolean seCroisent(XYChart.Series<Number, Number> une, XYChart.Series<Number, Number> autre) {
        List<Double> gauche = valeurs(une);
        List<Double> droite = valeurs(autre);
        int commun = Math.min(gauche.size(), droite.size());
        boolean uneDevant = false;
        boolean autreDevant = false;
        for (int i = 0; i < commun; i++) {
            uneDevant |= gauche.get(i) > droite.get(i);
            autreDevant |= droite.get(i) > gauche.get(i);
        }
        return uneDevant && autreDevant;
    }

    private static List<Double> valeurs(XYChart.Series<Number, Number> serie) {
        return serie.getData().stream()
                .map(point -> point.getYValue().doubleValue())
                .toList();
    }

    /// Les textes portés par le TRACÉ lui-même, légende exclue.
    private static List<String> etiquettesDansLeTrace(XYChart<Number, Number> courbe) {
        return courbe.getData().stream()
                .flatMap(serie -> serie.getData().stream())
                .map(XYChart.Data::getNode)
                .filter(java.util.Objects::nonNull)
                .flatMap(noeud -> noeud.lookupAll(".text").stream())
                .filter(Node::isVisible)
                .map(Object::toString)
                .toList();
    }
}
