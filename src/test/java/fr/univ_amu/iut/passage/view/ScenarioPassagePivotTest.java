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
import fr.univ_amu.iut.passage.model.CouvertureNuageuse;
import fr.univ_amu.iut.passage.model.FournisseurMeteo;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.Vent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

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

    /// Le nom du geste, sur son bouton comme sur le titre de sa modale.
    private static final String LIBELLE_MODIFIER = "Modifier le passage";

    /// Ce que le fournisseur de météo substitué rend, et qu'on doit retrouver dans les champs.
    private static final String TEMPERATURE_RELEVEE = "17";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    private Path carteSd;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        carteSd = CarteDeRecette.materialiser(FIXTURE);

        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .remplacer(liaison -> {
                    liaison.bind(ExecuteurTache.class)
                            .toInstance(
                                    new ExecuteurTacheRalenti(new ExecuteurTacheAsynchrone(), PAUSE_PAR_FICHIER_MS));
                    // Le relevé météo est un PORT, et le banc y répond lui-même. Sans cette liaison,
                    // « Récupérer la météo » appellerait Open-Meteo pour de vrai : un banc qui dépend
                    // d'un service tiers rougit le jour où ce service tousse, pour une raison qui n'est
                    // pas le produit. Ce que la case observe est le REMPLISSAGE des champs, et il a bien
                    // lieu.
                    liaison.bind(FournisseurMeteo.class)
                            .toInstance((latitude, longitude, date, debut, fin) ->
                                    Optional.of(new MeteoReleve(17.4, 9.2, Vent.FAIBLE, CouvertureNuageuse.DE_0_A_25)));
                })
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

    @Test
    @CasDeRecette(
            value = {"S2-27", "S2-28", "S2-29", "S2-30", "S2-31", "S2-32"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-27 à S2-32 · modifier le passage : les spinners, la météo, le micro, le récap")
    void modifier_le_passage(FxRobot robot) throws TimeoutException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);

        // ─── S2-27 · le bouton et le titre disent la MÊME chose ──────────────────────────────────
        // La documentation disait « Modifier le rattachement » quand l'écran disait autre chose : deux
        // noms pour un geste obligent le lecteur à deviner que c'est le même.
        assertThat(texte(robot, "#boutonRattachement"))
                .as("le bouton s'appelle « %s »", LIBELLE_MODIFIER)
                .contains(LIBELLE_MODIFIER);

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonRattachement");
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> robot.lookup("#spinnerNumero").tryQuery().isPresent(),
                "la modale d'édition ne s'est pas ouverte : sans elle, aucun des six cas n'a d'écran");

        assertThat(titreDeLaModale(robot))
                .as("et la modale porte le MÊME titre. C'est ce que la case demande de constater :"
                        + " l'écran et son point d'entrée se nomment pareil")
                .isEqualTo(LIBELLE_MODIFIER);

        // ─── S2-28 · les spinners FONCTIONNENT ──────────────────────────────────────────────────
        // « Fonctionnent » ne se constate pas sur leur présence : on les actionne et on compare.
        int numeroAvant = valeurDuSpinner(robot, "#spinnerNumero");
        robot.interact(() -> spinner(robot, "#spinnerNumero").increment());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(valeurDuSpinner(robot, "#spinnerNumero"))
                .as(
                        "le spinner du numéro de passage AVANCE : il valait %d. Un spinner qui paraît sans"
                                + " répondre est le genre de contrôle qu'on croit avoir réglé",
                        numeroAvant)
                .isGreaterThan(numeroAvant);

        int anneeAvant = valeurDuSpinner(robot, "#spinnerAnnee");
        robot.interact(() -> spinner(robot, "#spinnerAnnee").increment());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(valeurDuSpinner(robot, "#spinnerAnnee"))
                .as("et celui de l'année aussi : il valait %d", anneeAvant)
                .isGreaterThan(anneeAvant);

        // ─── S2-29 · la météo se SAISIT ─────────────────────────────────────────────────────────
        robot.clickOn("#champTemperature").write("12,5");
        robot.clickOn("#champTemperatureFin").write("8,5");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(texte(robot, "#champTemperature"))
                .as("la température de début de nuit se saisit à la main : le relevé automatique peut"
                        + " manquer, et l'observateur a son propre thermomètre")
                .contains("12");

        assertThat(robot.lookup("#champVent").tryQuery())
                .as("le vent est proposé, en liste : c'est une grandeur du protocole, pas un texte libre")
                .isPresent();
        assertThat(robot.lookup("#champCouverture").tryQuery())
                .as("la couverture nuageuse aussi")
                .isPresent();

        // ─── S2-30 · « Récupérer la météo » REMPLIT les champs ───────────────────────────────────
        // Le port est substitué : l'appel réseau est ce que le banc ne peut pas faire, le remplissage
        // est ce que la case observe. Les valeurs viennent du répondant, et se retrouvent à l'écran.
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonRecupererMeteo");
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> texte(robot, "#champTemperature").contains(TEMPERATURE_RELEVEE),
                "« Récupérer la météo » n'a rien rempli : le relevé rendu par le fournisseur doit"
                        + " atterrir dans les champs, sinon le geste ne fait qu'un aller-retour muet");

        assertThat(texte(robot, "#champTemperature"))
                .as("la température relevée REMPLACE ce qui était saisi : c'est un relevé, pas une"
                        + " suggestion à côté")
                .contains(TEMPERATURE_RELEVEE);

        // ─── S2-31 · le matériel micro, dont un type en LISTE FERMÉE ────────────────────────────
        assertThat(robot.lookup("#champPosition").tryQuery())
                .as("la position du micro se saisit")
                .isPresent();
        assertThat(robot.lookup("#champHauteur").tryQuery())
                .as("sa hauteur aussi")
                .isPresent();

        Object typeMicro = robot.lookup("#champTypeMicro").query();
        assertThat(typeMicro)
                .as("le type de micro est une liste FERMÉE, et non un champ libre : c'est un"
                        + " référentiel, et deux orthographes du même micro fausseraient l'analyse")
                .isInstanceOf(ComboBox.class);

        assertThat(robot.lookup("#champTypeMicro").queryAs(ComboBox.class).isEditable())
                .as("fermée veut dire non éditable : une liste où l'on peut taper n'en est pas une")
                .isFalse();

        // ─── S2-32 · le récapitulatif suit EN DIRECT ────────────────────────────────────────────
        // Deux relevés, comme partout où « ça suit » est le fait : un instantané ne distingue pas un
        // récap vivant d'un récap figé.
        String recapAvant = texte(robot, "#labelRecap");

        robot.interact(() -> spinner(robot, "#spinnerNumero").increment());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(texte(robot, "#labelRecap"))
                .as(
                        "le récapitulatif se recompose à chaque changement : il disait « %s ». C'est lui"
                                + " qui dit ce que « Appliquer » va faire, et un récap figé annoncerait autre"
                                + " chose que ce qui arrivera",
                        recapAvant)
                .isNotEqualTo(recapAvant);

        Respiration.leTempsDeLire(robot);
    }

    @Test
    @CasDeRecette(value = "S2-33", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-33 · changer le numéro de passage demande confirmation avant de renommer le disque")
    void renommer_le_passage_sur_le_disque(FxRobot robot) throws TimeoutException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);

        GesteVisible.cliquer(robot, "#boutonRattachement");
        WaitForAsyncUtils.waitForFxEvents();

        attendre(
                APPARITION_SECONDES,
                () -> robot.lookup("#spinnerNumero").tryQuery().isPresent(),
                "la modale d'édition ne s'est pas ouverte : sans elle, il n'y a pas de numéro à changer");

        // Le confirmateur est SUBSTITUÉ, et le dialogue réel est ce que le banc ne peut pas filmer :
        // `Alert.showAndWait()` fige TestFX. Ce que la case demande de constater est que la
        // confirmation soit DEMANDÉE, et son message est capturé ici plutôt que montré.
        List<String> demandes = new ArrayList<>();
        controleurDeLaModale(robot).confirmateur().definir(message -> {
            demandes.add(message);
            return false;
        });

        int numeroAvant = valeurDuSpinner(robot, "#spinnerNumero");

        Respiration.surLeMomentCle(robot);
        robot.interact(() -> spinner(robot, "#spinnerNumero").increment());
        WaitForAsyncUtils.waitForFxEvents();

        GesteVisible.cliquer(robot, "#boutonAppliquer");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(demandes)
                .as(
                        "changer le numéro de passage de %d renomme les séquences SUR LE DISQUE, et c'est"
                                + " irréversible : le produit demande avant de le faire. Appliquer sans demander"
                                + " renommerait des fichiers que l'observateur croit intacts",
                        numeroAvant)
                .hasSize(1);

        assertThat(demandes.getFirst())
                .as("et la demande NOMME ce qui va changer : elle reprend le récapitulatif vivant, celui"
                        + " que l'écran affiche. Une question qui dirait seulement « confirmer ? » ne"
                        + " permettrait pas de décider")
                .isNotBlank()
                .contains(String.valueOf(numeroAvant + 1));

        Respiration.leTempsDeLire(robot);
    }

    // --------------------------------------------------------------------------------------------

    /// Le contrôleur de la modale ouverte, posé par `NavigationPassage` sur la fenêtre.
    ///
    /// La navigation le tenait seule et le jetait en sortant : un banc filmé, qui passe par elle comme
    /// l'utilisateur, n'avait aucun moyen d'atteindre les porteurs que ce contrôleur expose pourtant
    /// aux tests. Sans eux, « Appliquer » ouvre un vrai dialogue modal, qui fige TestFX.
    private static RattachementModaleController controleurDeLaModale(FxRobot robot) {
        return robot.listTargetWindows().stream()
                .filter(Stage.class::isInstance)
                .map(Stage.class::cast)
                .map(Stage::getUserData)
                .filter(RattachementModaleController.class::isInstance)
                .map(RattachementModaleController.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune modale d'édition ouverte ne porte son contrôleur. « Modifier le passage »"
                                + " n'a pas ouvert la modale, ou la navigation a cessé de le poser sur la"
                                + " fenêtre."));
    }

    /// Le titre de la fenêtre modale ouverte par-dessus la principale.
    ///
    /// Lu sur la fenêtre et non sur un libellé de la scène : c'est la barre de titre que la case
    /// désigne, et c'est `NavigationPassage` qui la pose.
    private static String titreDeLaModale(FxRobot robot) {
        return robot.listTargetWindows().stream()
                .filter(Stage.class::isInstance)
                .map(Stage.class::cast)
                .filter(fenetre -> fenetre.getOwner() != null)
                .map(Stage::getTitle)
                .filter(titre -> titre != null && !titre.isBlank())
                .findFirst()
                .orElse("");
    }

    private static void attendre(int secondes, java.util.concurrent.Callable<Boolean> condition, String siJamais)
            throws TimeoutException {
        try {
            WaitForAsyncUtils.waitFor(secondes, java.util.concurrent.TimeUnit.SECONDS, condition);
        } catch (TimeoutException jamais) {
            throw new TimeoutException(siJamais);
        }
    }

    private static Spinner<Integer> spinner(FxRobot robot, String id) {
        return robot.lookup(id).queryAs(Spinner.class);
    }

    private static int valeurDuSpinner(FxRobot robot, String id) {
        Object valeur = spinner(robot, id).getValue();
        return valeur instanceof Integer entier ? entier : -1;
    }

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

    /// Ce qu'un nœud AFFICHE, qu'il soit libellé ou champ de saisie.
    ///
    /// Les deux branches, et c'est le premier tir qui l'a exigé : ce banc lit des `Label` (le bandeau,
    /// le résumé) et des `TextField` (la météo, le micro). Une aide qui n'aurait connu que les premiers
    /// aurait rendu la chaîne vide sur les seconds - donc un cas rouge pour une raison qui n'est pas le
    /// produit, ou pire, un cas vert sur une absence.
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
