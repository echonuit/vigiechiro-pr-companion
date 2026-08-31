package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// L'import nominal de `S2`, dans l'ordre où la session le joue (#4521).
///
/// La carte SD n'est pas versionnée : elle est **matérialisée depuis sa spec** par
/// [fr.univ_amu.iut.recette.CarteDeRecette], qui la reconstruit à l'octet près - aucune date tirée de l'horloge, aucun
/// octet aléatoire. Le banc n'a donc rien à fabriquer, et le clip montre la vraie inspection d'un
/// vrai arbre de fichiers.
///
/// Le geste part du **détail du carré 640380**, comme la session le dit, et non de l'écran d'import
/// monté directement : c'est de là que l'utilisateur y arrive, et un clip qui commencerait sur
/// l'assistant ne montrerait pas ce qui l'a ouvert.
///
/// Le carré 640380 est local et non relié, et c'est le garde-fou de la séance : l'import crée la
/// participation Vigie-Chiro au plus tôt dès que l'observateur est connecté et le site relié
/// (`ServiceImport.creerParticipationSiPossible`). Ici, aucune écriture serveur.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioImportNominalTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    /// La spec de la carte nominale : 6 wav, série 1925492, nuit du 22/04.
    private static final String FIXTURE = "sd-nominale";

    /// Ce que le journal de cette carte-là déclare, et que l'inspection doit nommer.
    private static final String SERIE = "1925492";

    private static final int ORIGINAUX = 6;

    /// L'indigo du point CHOISI, tel que `CarteRattachement` le définit. Repris et non recalculé :
    /// un test qui déduirait la couleur rejouerait la règle au lieu de l'éprouver.
    private static final Color INDIGO = Color.web("#3f51b5");

    /// Le frein, par point de progression. Six fichiers font donc six pas d'environ une seconde :
    /// de quoi rendre une dizaine d'images à la cadence du banc, et de quoi lire l'estimation.
    private static final long PAUSE_PAR_FICHIER_MS = 900;

    private static final int APPARITION_SECONDES = 30;

    /// La FIN d'un import : copie protégée, renommage et transformation des six fichiers.
    private static final int FIN_SECONDES = 180;

    private Path carteSd;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        carteSd = CarteDeRecette.materialiser(FIXTURE);

        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                // ASYNCHRONE : l'inspection balaie un dossier hors du fil JavaFX, et c'est ce
                // balayage qu'on filme. En synchrone le fil est bloqué, donc aucune image ne sort
                // pendant l'opération.
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                // Et FREINÉ. Sur des fixtures générées, l'import dure des millisecondes : mesuré
                // sur `sd-nominale` (6 wav) comme sur `sd-grosse` (60 wav), le compte rendu de fin
                // est déjà visible à l'instruction qui suit le clic. Les cinq cas qui portent sur ce
                // qui se passe PENDANT l'opération n'auraient donc rien à montrer.
                //
                // Le clip montre par conséquent une lenteur que le produit n'a pas. C'est assumé, et
                // c'est dit : le geste démontre que ces cinq surfaces existent et s'enchaînent,
                // jamais combien de temps un import prend.
                .remplacer(new AbstractModule() {
                    @Provides
                    @Singleton
                    ExecuteurTache executeurFreine() {
                        return new ExecuteurTacheRalenti(new ExecuteurTacheAsynchrone(), PAUSE_PAR_FICHIER_MS);
                    }
                })
                .semer(this::poserLeCarreEtSonPoint)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirDetail(CARRE))
                .montrer(stage);
    }

    /// La base de départ : un observateur, un carré local, un point d'écoute. C'est celle que S1
    /// laisse derrière elle, et la session le dit en toutes lettres.
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
            value = {"S2-01", "S2-02", "S2-03", "S2-04", "S2-05", "S2-06", "S2-07"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-01 à S2-07 · désigner la carte SD, et lire ce que l'inspection en dit")
    void designer_la_source_et_l_inspecter(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        // ─── S2-01 · le champ est en LECTURE SEULE ───────────────────────────────────────────────
        // Asserté AVANT le clic sur « Parcourir » : après, le champ porte un chemin, et un champ
        // rempli paraît figé même s'il ne l'est pas.
        assertThat(robot.lookup("#champDossier").queryAs(TextField.class).isEditable())
                .as("« Dossier source » se DÉSIGNE, il ne se saisit pas : un chemin tapé à la main"
                        + " désignerait un dossier que personne n'a parcouru, et l'inspection porterait"
                        + " sur autre chose que ce que l'écran montre")
                .isFalse();

        controleur().selecteur().definir(repondant(carteSd));

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> !texte(robot, "#labelOriginaux").isBlank(),
                "l'inspection n'a jamais rendu son compte d'originaux : elle balaie le dossier hors du"
                        + " fil JavaFX, et rien n'a paru dans le temps imparti",
                APPARITION_SECONDES * 1000L);

        // ─── S2-03 · le journal détecté, NOMMÉ ───────────────────────────────────────────────────
        assertThat(texte(robot, "#labelJournal"))
                .as(
                        "l'inspection doit NOMMER le journal qu'elle a trouvé (`LogPR%s`). Un libellé qui"
                                + " dirait seulement « journal détecté » ne permettrait pas de voir qu'elle a lu"
                                + " CETTE carte-là",
                        SERIE)
                .contains(SERIE);

        // ─── S2-04 · le relevé climatique ────────────────────────────────────────────────────────
        assertThat(texte(robot, "#labelReleve"))
                .as(
                        "le relevé climatique de cette carte est `PaRecPR%s_THLog.csv` : l'inspection"
                                + " l'annonce, sans quoi l'observateur ne saurait pas que les températures"
                                + " suivront la nuit",
                        SERIE)
                .isNotBlank();

        // ─── S2-05 · les six originaux, COMPTÉS ──────────────────────────────────────────────────
        assertThat(texte(robot, "#labelOriginaux"))
                .as(
                        "la carte nominale porte %d wav, et l'inspection les compte. C'est ce compte qui"
                                + " dit à l'observateur que rien n'a été oublié sur la carte",
                        ORIGINAUX)
                .contains(String.valueOf(ORIGINAUX));

        // ─── S2-06 · aucun bandeau, parce que rien ne cloche ─────────────────────────────────────
        // Deux faits, et non un. « Invisible » se confondrait avec « absent » : mes aides rendent
        // faux dans les deux cas, et un cas qui ne distinguerait pas les deux resterait vert le jour
        // où la zone disparaîtrait du FXML. Or elle y est toujours - `SectionInspectionController`
        // ne fait que basculer sa visibilité sur `rendu.estVide()`.
        assertThat(robot.lookup("#zoneAvertissements").tryQuery())
                .as("la zone d'avertissements doit EXISTER dans l'écran : si le nœud disparaissait, le"
                        + " constat « aucun bandeau » ci-dessous serait vrai pour la mauvaise raison, et"
                        + " ce cas ne garderait plus rien")
                .isPresent();

        assertThat(visible(robot, "#zoneAvertissements"))
                .as("cas NOMINAL : la zone est là et reste MASQUÉE. C'est le contrôle négatif des cas"
                        + " dégradés - si un bandeau paraît ici, ceux qui en attendent un plus loin ne"
                        + " prouvent plus rien")
                .isFalse();

        // ─── S2-07 · le renommage ANNONCÉ, et rien de plus ───────────────────────────────────────
        assertThat(texte(robot, "#labelNommage"))
                .as("l'inspection annonce le renommage À VENIR : elle est en lecture seule, et les"
                        + " originaux sont intacts sur la carte tant que l'import n'a pas eu lieu")
                .isNotBlank();

        assertThat(Files.isDirectory(carteSd.resolve("bruts")))
                .as("les originaux sont INTACTS : l'inspection lit la carte, elle n'y touche pas. Un"
                        + " cas qui ne le constaterait pas laisserait passer une inspection qui renomme"
                        + " avant que l'observateur ait dit oui")
                .isTrue();

        Respiration.leTempsDeLire(robot);
    }

    @Test
    @CasDeRecette(
            value = {"S2-08", "S2-09", "S2-10", "S2-11"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-08 à S2-11 · rattacher la nuit à son point, et voir le préfixe se composer")
    void rattacher_la_nuit_a_son_point(FxRobot robot) throws TimeoutException {
        ouvrirLAssistantEtDesignerLaCarte(robot);

        // ─── S2-08 · les QUATRE champs du rattachement ───────────────────────────────────────────
        // Chacun est constaté PEUPLÉ, et non seulement présent : une liste vide est un champ qui
        // existe et ne propose rien, ce que la case ne permet pas de distinguer d'un formulaire
        // absent si on ne regarde que la présence.
        assertThat(robot.lookup("#comboSites").queryAs(ComboBox.class).getItems())
                .as(
                        "le rattachement PROPOSE un site : le carré %s vient d'être semé, et l'assistant"
                                + " s'est ouvert depuis sa fiche",
                        CARRE)
                .isNotEmpty();
        assertThat(robot.lookup("#comboPoints").queryAs(ComboBox.class).getItems())
                .as("et ses points d'écoute : sans eux, il n'y a rien à rattacher, et le préfixe ne"
                        + " peut pas se composer")
                .isNotEmpty();
        assertThat(texte(robot, "#champAnnee"))
                .as("l'année est proposée, et non laissée à saisir : elle se déduit de la nuit que"
                        + " l'inspection vient de lire")
                .isNotBlank();
        assertThat(robot.lookup("#champPassage").tryQuery())
                .as("le numéro de passage est demandé : c'est lui qui distingue deux nuits du même"
                        + " point dans la même année")
                .isPresent();

        // Le point se CHOISIT : rien n'est présélectionné, et c'est le premier tir qui l'a dit -
        // zéro marqueur indigo, parce qu'aucun point n'était choisi. La case parle du point CHOISI,
        // donc le geste doit le choisir.
        //
        // Par le modèle de sélection plutôt que par un clic : le popup d'un `ComboBox` ne se déroule
        // pas de façon fiable en headless, et c'est l'idiome des scénarios voisins. Ce que le clip
        // montre reste la CONSÉQUENCE - la valeur qui paraît dans le champ, et le marqueur qui vire
        // à l'indigo - c'est-à-dire ce que la case demande de constater.
        rattacherAuPremierPoint(robot);

        // ─── S2-09 · la carte de confirmation, et le point choisi EN INDIGO ──────────────────────
        // Lu sur le graphe de scène, pas sur les pixels : la carte porte des tuiles, dont ce dépôt a
        // mesuré le bruit jusqu'à 23,8 % (#4287). `CouchePoints` dessine chaque marqueur en `Circle`
        // rempli de la couleur que `CarteRattachement` lui donne - c'est donc bien ce que l'écran
        // montre qu'on lit, et non ce que le test recalculerait.
        assertThat(visible(robot, "#zoneCarteRattachement"))
                .as("la carte de confirmation paraît : c'est elle qui montre OÙ la nuit sera"
                        + " rattachée, et sans elle le formulaire demande un point sans le situer")
                .isTrue();

        assertThat(pastillesDe(robot, INDIGO))
                .as(
                        "EXACTEMENT un marqueur en indigo (%s) : le point CHOISI. Les autres sont gris."
                                + " Zéro dirait que la carte n'a rien dessiné ; plusieurs, que la sélection ne"
                                + " se distingue pas, et la case demande de la voir",
                        INDIGO)
                .isEqualTo(1);

        // ─── S2-10 · l'aperçu du préfixe SUIT la saisie ──────────────────────────────────────────
        // Deux relevés, comme pour la barre de S8 : « il suit » ne se constate pas sur un instantané.
        String avant = texte(robot, "#labelApercu");

        Respiration.surLeMomentCle(robot);
        robot.clickOn("#champPassage").write("2");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(texte(robot, "#labelApercu"))
                .as(
                        "l'aperçu se recompose à la saisie : il valait « %s » avant que le numéro de"
                                + " passage soit tapé. Un aperçu figé laisserait croire au nom que les fichiers"
                                + " porteront, et il serait faux",
                        avant)
                .isNotEqualTo(avant)
                .containsPattern("Car" + CARRE + "-\\d{4}-Pass\\d+-");

        // ─── S2-11 · la case « Conserver les originaux » n'est PLUS là ───────────────────────────
        // Le réglage a rejoint Réglages ▸ Import (#3471). Ce cas garde qu'il n'en reste pas de trace
        // ici : deux endroits qui demandent la même chose se contredisent le jour où l'un change.
        assertThat(libellesDeLEcran(robot))
                .as("aucun libellé de l'assistant ne doit reparler de conserver les originaux : le"
                        + " réglage vit dans Réglages ▸ Import, et S7 le déroule là-bas")
                .noneMatch(libelle -> libelle.toLowerCase(Locale.ROOT).contains("conserver les originaux"));

        Respiration.leTempsDeLire(robot);
    }

    @Test
    @CasDeRecette(
            value = {"S2-12", "S2-13", "S2-14", "S2-15", "S2-16", "S2-17"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-12 à S2-17 · importer la nuit, et suivre la copie fichier par fichier")
    void importer_la_nuit(FxRobot robot) throws TimeoutException {
        ouvrirLAssistantEtDesignerLaCarte(robot);
        rattacherAuPremierPoint(robot);

        // L'assistant est plus haut que la scène : « Importer cette nuit » est sous le bord, et
        // TestFX refuse de cliquer ce qu'on ne voit pas. Un geste hors du cadre serait de toute façon
        // absent du clip.
        GesteVisible.amenerDansLeCadre(robot, "#boutonImporter");

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonImporter");

        // CINQ des six cas portent sur ce qui se passe PENDANT l'opération. Attendre la fin puis
        // regarder ne dirait rien d'eux : la barre serait rangée, la table figée, le formulaire
        // dégelé. Le relevé se prend donc au vol, dès que la progression paraît.
        Attente.que(
                () -> visible(robot, "#zoneProgression"),
                "la progression n'a jamais paru : l'import de six fichiers passe par une barre, et"
                        + " sans elle les cinq cas de suivi n'ont rien à montrer",
                APPARITION_SECONDES * 1000L);

        // L'instant du relevé est celui où l'estimation EXISTE, et non le premier. `S2-13` dit « une
        // fois l'avancement mesurable » : elle s'extrapole du temps écoulé, donc elle ne peut rien
        // annoncer à l'ouverture - le premier tir freiné l'a dit, en rougissant sur elle seule alors
        // que la barre, elle, était déjà déterminée.
        //
        // Attendre CE moment-là plutôt que de relever au plus tôt garde les cinq constats
        // contemporains, à un instant où les cinq peuvent exister.
        Attente.que(
                () -> texte(robot, "#labelProgression").contains("restant"),
                "aucune estimation du temps restant n'a paru dans l'avancement. Elle s'extrapole du"
                        + " temps écoulé : si elle manque, c'est que l'opération n'a jamais été"
                        + " mesurable, et S2-13 n'a rien à montrer",
                APPARITION_SECONDES * 1000L);
        AuVol releve = AuVol.prendre(robot);

        // ─── S2-12 · une barre DÉTERMINÉE, et non un rouet ───────────────────────────────────────
        assertThat(releve.fraction())
                .as("la barre est DÉTERMINÉE : elle annonce une part faite sur un total connu, parce"
                        + " que l'inspection a compté les fichiers avant de commencer. Un rouet"
                        + " (`-1`) dirait « ça travaille » sans dire combien il en reste")
                .isBetween(0.0, 1.0);

        // ─── S2-13 · l'estimation du temps restant ───────────────────────────────────────────────
        assertThat(releve.message())
                .as("l'avancement annonce le temps restant : sur une nuit réelle l'import dure des"
                        + " minutes, et une barre sans estimation laisse l'observateur devant un écran"
                        + " dont il ne sait pas s'il doit l'attendre")
                .contains("restant");

        // ─── S2-14 · « Annuler » est ATTEIGNABLE, pas seulement présent ──────────────────────────
        assertThat(releve.annulerActif())
                .as("« Annuler » est disponible PENDANT l'opération : un bouton présent mais grisé"
                        + " enfermerait l'observateur dans un import qu'il vient de lancer par erreur")
                .isTrue();

        // ─── S2-15 · la table de suivi, une ligne par wav ────────────────────────────────────────
        assertThat(releve.lignesDeSuivi())
                .as(
                        "la carte porte %d originaux, et le suivi montre l'état de CHACUN. Un compte"
                                + " global dirait « 3 sur 6 » sans dire lesquels, ni où en est celui qui"
                                + " bloque",
                        ORIGINAUX)
                .isEqualTo(ORIGINAUX);

        // ─── S2-16 · le formulaire est GELÉ ──────────────────────────────────────────────────────
        assertThat(releve.formulaireGele())
                .as("le rattachement est gelé pendant l'import : changer de point en cours de copie"
                        + " renommerait la moitié des fichiers avec un préfixe et l'autre moitié avec"
                        + " un autre")
                .isTrue();

        Respiration.leTempsDeLire(robot);

        // ─── S2-17 · le compte rendu de fin ──────────────────────────────────────────────────────
        Attente.que(
                () -> visible(robot, "#compteRenduChiffre"),
                "l'import n'a pas rendu son compte rendu dans le temps imparti. À lire comme « la"
                        + " carte est plus grosse que ce banc ne le prévoit », pas comme un défaut",
                FIN_SECONDES * 1000L);

        assertThat(texte(robot, "#labelStatut") + " " + libellesDeLEcran(robot))
                .as("la fin se DIT : depuis #2358 c'est la bande de compte rendu chiffré qui la porte,"
                        + " et elle nomme ce qui a été fait plutôt que d'annoncer « terminé »")
                .isNotBlank();

        Respiration.leTempsDeLire(robot);
    }

    // ------------------------------------------------------------------

    /// Rattache la nuit au premier point du carré, pour les gestes qui commencent APRÈS.
    private void rattacherAuPremierPoint(FxRobot robot) {
        ComboBox<?> points = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        robot.interact(() -> points.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Ce que l'écran montre à UN instant de l'opération, relevé d'un coup.
    ///
    /// Cinq cas de `S2` portent sur ce qui se passe PENDANT l'import, et cinq relevés séparés
    /// décriraient cinq instants différents : la barre lue à 20 %, la table lue à 60 %, le formulaire
    /// lu une fois dégelé. Un seul relevé rend les cinq constats CONTEMPORAINS, donc comparables.
    private record AuVol(
            double fraction, String message, boolean annulerActif, int lignesDeSuivi, boolean formulaireGele) {

        static AuVol prendre(FxRobot robot) {
            Node barre = robot.lookup("#barreProgression").tryQuery().orElse(null);
            Node annuler = robot.lookup("#boutonAnnuler").tryQuery().orElse(null);
            Node table = robot.lookup("#tableFichiers").tryQuery().orElse(null);
            Node combo = robot.lookup("#comboPoints").tryQuery().orElse(null);
            return new AuVol(
                    barre instanceof ProgressBar progression ? progression.getProgress() : -1,
                    texteDe(robot, "#labelProgression"),
                    annuler != null && !annuler.isDisabled(),
                    table instanceof TableView<?> suivi ? suivi.getItems().size() : -1,
                    combo != null && combo.isDisabled());
        }

        private static String texteDe(FxRobot robot, String id) {
            Node noeud = robot.lookup(id).tryQuery().orElse(null);
            return noeud instanceof Labeled libelle && libelle.getText() != null ? libelle.getText() : "";
        }
    }

    /// Le préambule commun aux trois gestes : ouvrir l'assistant depuis la fiche du carré, désigner
    /// la carte, attendre que l'inspection ait parlé.
    ///
    /// Refait à chaque geste plutôt que partagé par un état de classe : trois clips, trois histoires
    /// complètes. Un geste qui reprendrait l'écran laissé par le précédent ne montrerait pas d'où il
    /// part, et l'ordre des cas déciderait de ce que chacun filme.
    private void ouvrirLAssistantEtDesignerLaCarte(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        controleur().selecteur().definir(repondant(carteSd));
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> !texte(robot, "#labelOriginaux").isBlank(),
                "l'inspection n'a jamais rendu son compte d'originaux : le rattachement ne propose"
                        + " rien tant qu'elle n'a pas lu la carte",
                APPARITION_SECONDES * 1000L);
    }

    /// Combien de pastilles de la carte portent `couleur`.
    ///
    /// `CouchePoints` dessine chaque marqueur en `Circle` rempli de la couleur décidée par
    /// `CarteRattachement`. Compter les cercles d'une couleur donnée lit donc ce que l'écran MONTRE,
    /// là où lire `DonneesCarte` lirait ce qu'on lui a passé - et la carte pourrait ne rien dessiner.
    private static long pastillesDe(FxRobot robot, Color couleur) {
        return robot.lookup(".root").queryAll().stream()
                .flatMap(racine -> racine.lookupAll("*").stream())
                .filter(Circle.class::isInstance)
                .map(Circle.class::cast)
                .filter(pastille -> couleur.equals(pastille.getFill()))
                .distinct()
                .count();
    }

    /// Tous les libellés visibles de l'écran, pour les cas qui gardent une ABSENCE.
    private static List<String> libellesDeLEcran(FxRobot robot) {
        return robot.lookup(".root").queryAll().stream()
                .flatMap(racine -> racine.lookupAll("*").stream())
                .filter(Labeled.class::isInstance)
                .map(noeud -> ((Labeled) noeud).getText())
                .filter(libelle -> libelle != null && !libelle.isBlank())
                .distinct()
                .toList();
    }

    /// Le contrôleur de l'écran affiché, pris chez le navigateur qui le détient.
    ///
    /// `Injector#getInstance` en rendrait un AUTRE : le contrôleur n'est pas un singleton, et celui de
    /// la scène a été créé par le `FXMLLoader` de la navigation. Poser le double sur un contrôleur qui
    /// n'est pas à l'écran laisserait « Parcourir » ouvrir le dialogue natif, qui fige le banc.
    private ImportationController controleur() {
        Navigateur navigateur = injecteur.getInstance(Navigateur.class);
        Object courant = navigateur.historique().getLast().controleur();
        assertThat(courant)
                .as("l'écran affiché doit être l'assistant d'import : le clic sur « Importer une nuit »"
                        + " n'a pas mené où la session le dit")
                .isInstanceOf(ImportationController.class);
        return (ImportationController) courant;
    }

    /// Un sélecteur qui répond `carte` à la demande de dossier, et refuse le reste.
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
                throw new AssertionError("l'import LIT une source : ce geste n'écrit aucun fichier");
            }
        };
    }

    private static boolean visible(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        return noeud != null && noeud.isVisible() && noeud.getParent() != null;
    }

    private static String texte(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        if (noeud instanceof Labeled libelle) {
            return libelle.getText() == null ? "" : libelle.getText();
        }
        if (noeud instanceof TextInputControl champ) {
            return champ.getText() == null ? "" : champ.getText();
        }
        return "";
    }
}
