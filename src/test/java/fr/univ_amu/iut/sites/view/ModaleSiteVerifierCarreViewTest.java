package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.commun.viewmodel.EtatConnexion;
import fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.RechercheCarreExistant;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.SiteEditViewModel;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le geste **« Vérifier sur Vigie-Chiro »** dans la modale de déclaration (#3458).
///
/// C'est la question posée mot pour mot par un utilisateur : *« Je n'ai pas trouvé de solution pour
/// vérifier s'il existait déjà »*. Il est allé la poser au portail, a redéclaré le carré ici, et son
/// dépôt a échoué - loin de la cause.
///
/// La modale est montée **avec** la vérification installée, ce qui est le cas de l'application complète.
/// Le montage sans - injecteurs partiels, feature éteinte - est éprouvé par [ModaleSiteViewTest], où le
/// bouton doit être **absent** plutôt que mort.
///
/// L'exécuteur est celui du socle : synchrone en test (`@ImplementedBy`), donc le verdict est lisible
/// dès le retour du clic, sans attente ni `sleep`.
@ExtendWith(ApplicationExtension.class)
class ModaleSiteVerifierCarreViewTest {

    // Cette classe ne cite plus de cas de recette, et ce n'est pas un oubli. Elle monte
    // `ModaleSite.fxml` SEULE : ses clips montraient une modale flottant sur du noir, où « la fenêtre se
    // ferme, la fiche s'ouvre » n'avait aucun écran d'arrivée à montrer (#4180). Les six cas sont joués
    // par `ScenarioModaleCarreTest`, sur la fenêtre réelle et par des gestes.
    //
    // Ses assertions restent : elles gardent le câblage de la modale, ce qui est un autre travail que
    // de le montrer.

    private static final String CARRE = "640380";

    /// Le carré **libre**, celui que le script de `S1-30` nomme.
    ///
    /// Il fallait un numéro à lui. `S1-30` saisissait le même `640380` que `S1-31`, si bien que deux
    /// clips voisins rendaient des verdicts OPPOSÉS sur le même carré - « n'existe pas encore » ici,
    /// « déjà déclaré » là - parce que la plateforme est bouchonnée par test. Vu de la page, c'est le
    /// produit qui se contredit (#4166). Et le script disait `999999` depuis le début.
    private static final String CARRE_LIBRE = "999999";

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final RapatriementCarre rapatriement = mock(RapatriementCarre.class);

    private ModaleSiteController controleur;

    /// Le jeton de ces cas, pilotable : « Vérifier » est fermé sans lui depuis #4210, et un cas d'ici
    /// joue justement l'absence de connexion.
    private final AtomicReference<Optional<String>> jeton = new AtomicReference<>(Optional.of("jeton-de-test"));

    private RefletDuJeton reflet;

    @Start
    void start(Stage stage) throws Exception {
        ServiceSites service = mock(ServiceSites.class);
        LienVigieChiroDao liens = mock(LienVigieChiroDao.class);
        // Le jeton de ces cas, pilotable : « Vérifier » est fermé sans lui depuis #4210, et deux cas
        // d'ici jouent justement l'absence de connexion.
        reflet = new RefletDuJeton(jeton::get, Runnable::run);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                OptionalBinder.newOptionalBinder(binder(), EtatConnexion.class)
                        .setBinding()
                        .toInstance(reflet);
            }

            @Provides
            SiteEditViewModel viewModel() {
                return new SiteEditViewModel(
                        service,
                        liens,
                        "u-1",
                        Optional.of(new RechercheCarreExistant(client)),
                        Optional.of(rapatriement));
            }
        });
        FXMLLoader loader = new FXMLLoader(ModaleSiteController.class.getResource("ModaleSite.fxml"));
        loader.setControllerFactory(DiagnosticGuice.pour(injector));
        Parent vue = loader.load();
        controleur = loader.getController();
        // `Habillage`, et non `new Scene` : les six cas de cette classe sont FILMÉS, et une scène
        // montée sans habillage porte la police de la MACHINE, pas celle du produit (#3773, #4149).
        stage.setScene(Habillage.scene(vue));
        stage.show();
    }

    /// Ce que l'appelant a reçu d'un carré rapatrié : ici on l'enregistre au lieu d'ouvrir une fiche.
    private RapatriementCarre.Resultat.Rapatrie rapatrieRecu;

    private void enCreation(FxRobot robot) {
        robot.interact(() -> controleur.demarrerCreation(() -> {}, rapatrie -> rapatrieRecu = rapatrie));
    }

    private Button recuperer(FxRobot robot) {
        return robot.lookup("#btnRecupererCarre").queryAs(Button.class);
    }

    private void verdictCarrePresent(FxRobot robot) {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-" + CARRE, true))));
        saisirCarre(robot, CARRE);
        verifierLeCarre(robot);
    }

    /// Le numéro se **tape**, chiffre à chiffre, dans le champ qu'on vient de cliquer.
    ///
    /// `setText` posait les six chiffres d'un coup. Ce que ces cas font juger est un enchaînement vu
    /// de l'extérieur - on saisit, on vérifie, l'encart répond - et un champ qui se remplit tout seul
    /// n'en fait pas partie : le clip était incompréhensible (#4149). Même correction que sur `S1-37`.
    private void saisirCarre(FxRobot robot, String carre) {
        TextField champ = robot.lookup("#champCarre").queryAs(TextField.class);
        robot.interact(champ::clear);
        robot.clickOn(champ).write(carre);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Clique « Vérifier », en laissant voir l'écran avant le geste et le verdict après lui.
    private void verifierLeCarre(FxRobot robot) {
        Respiration.avantLeGeste(robot);
        robot.clickOn(verifier(robot));
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);
    }

    private Button verifier(FxRobot robot) {
        return robot.lookup("#btnVerifierCarre").queryAs(Button.class);
    }

    private Label message(FxRobot robot) {
        return robot.lookup("#messageCarreExistant").queryAs(Label.class);
    }

    @Test
    @DisplayName("#3458 : le geste est offert, et grisé tant que le carré n'a pas ses six chiffres")
    void geste_offert_et_grise_tant_que_le_carre_est_incomplet(FxRobot robot) {
        enCreation(robot);
        StackPane enveloppe = robot.lookup("#enveloppeVerifierCarre").queryAs(StackPane.class);

        assertThat(enveloppe.isVisible())
                .as("la vérification est installée : le geste doit être là")
                .isTrue();
        assertThat(verifier(robot).isDisabled())
                .as("formulaire vierge : il n'y a rien à chercher")
                .isTrue();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe))
                .as("un bouton gris sans motif est lui-même un défaut (#789)")
                .contains("6 chiffres");

        saisirCarre(robot, "6403");
        assertThat(verifier(robot).isDisabled())
                .as("quatre chiffres : `$text` cherche des mots entiers, un carré tronqué ne trouverait rien")
                .isTrue();

        saisirCarre(robot, CARRE);
        assertThat(verifier(robot).isDisabled())
                .as("six chiffres : le geste s'ouvre")
                .isFalse();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe))
                .as("le motif cède la place à ce que fait l'action")
                .doesNotContain("6 chiffres");
    }

    @Test
    @DisplayName("#3458 : carré libre : le verdict s'affiche dans la modale, en succès")
    void carre_libre_le_verdict_s_affiche(FxRobot robot) {
        enCreation(robot);
        when(client.chercherCarre(CARRE_LIBRE)).thenReturn(ReponseApi.succes(List.of()));
        saisirCarre(robot, CARRE_LIBRE);

        verifierLeCarre(robot);

        assertThat(message(robot).isVisible()).isTrue();
        assertThat(message(robot).getText()).contains("n'existe pas encore");
        assertThat(message(robot).getStyleClass()).contains("encart-succes");
    }

    @Test
    @DisplayName("#3458 : carré déjà déclaré : l'avertissement nomme le site et dit quoi faire")
    void carre_deja_declare_avertit_dans_la_modale(FxRobot robot) {
        enCreation(robot);
        when(client.chercherCarre(CARRE))
                .thenReturn(
                        ReponseApi.succes(List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-640380", true))));
        saisirCarre(robot, CARRE);

        verifierLeCarre(robot);

        // Redéclarer un carré déjà présent est exactement ce qui a produit le dépôt manqué de #3458 : le
        // message ne se contente donc pas de constater, il nomme le geste qui remplace la déclaration.
        assertThat(message(robot).getText())
                .contains("Vigiechiro - Point Fixe-640380")
                .contains("rattaché");
        assertThat(message(robot).getStyleClass()).contains("encart-avertissement");
    }

    @Test
    @DisplayName("#3458 : une panne technique remet le geste à portée, et ne dit JAMAIS « il est libre »")
    void une_panne_technique_remet_le_geste_a_portee(FxRobot robot) {
        enCreation(robot);
        when(client.chercherCarre(CARRE)).thenThrow(new IllegalStateException("panne"));
        saisirCarre(robot, CARRE);

        verifierLeCarre(robot);

        assertThat(message(robot).getText())
                .as("une absence de réponse n'est pas une réponse")
                .contains("PAS été vérifié");
        assertThat(verifier(robot).isDisabled())
                .as("le bouton se rouvre : sans cela, la panne coûterait la modale entière")
                .isFalse();
    }

    @Test
    @DisplayName("#4210 : hors connexion, « Vérifier » est fermé et nomme le geste qui répare")
    void hors_connexion_le_geste_est_ferme_et_dit_pourquoi(FxRobot robot) {
        enCreation(robot);
        saisirCarre(robot, CARRE);
        assertThat(verifier(robot).isDisabled())
                .as("connecté et six chiffres saisis : le geste est offert")
                .isFalse();

        seDeconnecter();

        // Ce cas jouait autrefois un client qui répond `nonConnecte()` PENDANT que l'application
        // avait un jeton : la fixture se contredisait, et le vert existait quoi qu'il arrive. Depuis
        // #4210, l'absence de jeton se voit AVANT le clic - c'est ce qu'on éprouve ici.
        assertThat(verifier(robot).isDisabled())
                .as("le jeton retiré, le geste se ferme sans qu'on ait quitté la fenêtre (#4205)")
                .isTrue();
        assertThat(InfobulleDeBlocage.texteDe(
                        robot.lookup("#enveloppeVerifierCarre").query()))
                .as("et il nomme ce qui manque, avec le geste qui répare")
                .contains("pas connecté")
                .contains("Se connecter à Vigie-Chiro");

        // Le pendant : seule la VÉRIFICATION se ferme. Déclarer un carré hors ligne reste possible.
        assertThat(robot.lookup("#boutonValider").queryAs(Button.class).isDisabled())
                .as("déclarer reste possible hors connexion")
                .isFalse();
    }

    /// Retire le jeton et publie le reflet, comme la modale de connexion le fait (#4205).
    private void seDeconnecter() {
        jeton.set(Optional.empty());
        reflet.relire();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("#3458 : on peut vérifier, corriger, et vérifier de nouveau")
    void verifier_deux_fois_de_suite(FxRobot robot) {
        enCreation(robot);
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.succes(List.of()));
        when(client.chercherCarre("640381"))
                .thenReturn(
                        ReponseApi.succes(List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-640381", true))));
        saisirCarre(robot, CARRE);
        verifierLeCarre(robot);

        saisirCarre(robot, "640381");
        verifierLeCarre(robot);

        // Le bouton se grise le temps de l'appel pour qu'un double clic ne parte pas deux fois ; s'il ne
        // se rouvrait pas ensuite, la première vérification serait la seule possible - et corriger une
        // faute de frappe coûterait de rouvrir la modale.
        assertThat(message(robot).getText()).contains("Vigiechiro - Point Fixe-640381");
        assertThat(verifier(robot).isDisabled()).isFalse();
    }

    @Test
    @DisplayName("#3458 : corriger le carré après coup efface un verdict qui ne le concerne plus")
    void corriger_le_carre_efface_le_verdict(FxRobot robot) {
        enCreation(robot);
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.succes(List.of()));
        saisirCarre(robot, CARRE);
        verifierLeCarre(robot);
        assertThat(message(robot).isVisible()).isTrue();
        Respiration.leTempsDeLire(robot);

        // On corrige UN CHIFFRE, ce que le script demande : « changer un chiffre du carré ».
        // `saisirCarre` vide le champ et retape tout, si bien que le clip montrait le verdict
        // disparaître au VIDAGE - un champ qui se vide seul, puis un numéro qui se réécrit, et
        // l'encart parti entre les deux. On ne voyait pas la correction (#4166).
        robot.clickOn(robot.lookup("#champCarre").queryAs(TextField.class));
        robot.push(KeyCode.END);
        robot.push(KeyCode.BACK_SPACE);
        robot.write("1");
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        assertThat(robot.lookup("#champCarre").queryAs(TextField.class).getText())
                .as("le champ porte le numéro corrigé, un chiffre de différence")
                .isEqualTo("640381");
        assertThat(message(robot).isVisible())
                .as("un « ce carré est libre » sous un autre numéro serait pire que pas de vérification")
                .isFalse();
        assertThat(message(robot).isManaged())
                .as("et il se retire de la mise en page, sans laisser d'encadré vide")
                .isFalse();
        verify(client, never()).chercherCarre("640381");
    }

    @Test
    @DisplayName("#3806 : « Récupérer ce carré » n'apparaît qu'après un verdict « il existe déjà »")
    void le_geste_recuperer_apparait_avec_le_verdict(FxRobot robot) {
        enCreation(robot);
        assertThat(robot.lookup("#ligneRecupererCarre")
                        .queryAs(javafx.scene.layout.HBox.class)
                        .isVisible())
                .as("rien n'a été cherché : il n'y a rien à récupérer")
                .isFalse();

        verdictCarrePresent(robot);

        assertThat(robot.lookup("#ligneRecupererCarre")
                        .queryAs(javafx.scene.layout.HBox.class)
                        .isVisible())
                .as("le carré est là-bas : le geste s'offre")
                .isTrue();
    }

    @Test
    @DisplayName("#3806 : récupérer ferme la modale et passe le carré à l'appelant")
    void recuperer_ferme_la_modale_et_passe_le_carre(FxRobot robot) {
        enCreation(robot);
        verdictCarrePresent(robot);
        Site site = new Site(7L, CARRE, "Étang", Protocole.STANDARD, null, "2026-08-15", "u-1");
        when(rapatriement.rapatrier(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RapatriementCarre.Resultat.Rapatrie(site, 41));

        Respiration.avantLeGeste(robot);
        robot.clickOn(recuperer(robot));
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        // Le formulaire n'a plus lieu d'être : le site existe désormais, et c'est sa fiche qu'il faut
        // regarder. L'appelant reçoit de quoi l'ouvrir ET de quoi rendre compte des 41 points.
        assertThat(rapatrieRecu).isNotNull();
        assertThat(rapatrieRecu.points()).isEqualTo(41);
        assertThat(robot.lookup("#champCarre").tryQuery())
                .as("la modale s'est fermée")
                .isEmpty();
    }

    @Test
    @DisplayName("#3806 : un rapatriement impossible laisse la modale ouverte, avec son motif")
    void un_rapatriement_impossible_laisse_la_modale_ouverte(FxRobot robot) {
        enCreation(robot);
        verdictCarrePresent(robot);
        when(rapatriement.rapatrier(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RapatriementCarre.Resultat.Indisponible(
                        "Vigie-Chiro est injoignable (bouchon). Réessayez plus tard."));

        Respiration.avantLeGeste(robot);
        robot.clickOn(recuperer(robot));
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);

        // Fermer sur un échec laisserait l'utilisateur devant une liste inchangée, sans savoir pourquoi.
        assertThat(rapatrieRecu).isNull();
        assertThat(message(robot).getText()).contains("Rien n'a été créé");
        assertThat(robot.lookup("#ligneRecupererCarre")
                        .queryAs(javafx.scene.layout.HBox.class)
                        .isVisible())
                .as("la panne peut être passagère : le geste doit rester OFFERT, pas seulement actif")
                .isTrue();
        // Et surtout : on sait toujours que le carré existe là-bas. Rouvrir « Créer » ici inviterait à
        // fabriquer le doublon que tout ce chantier existe pour éviter.
        assertThat(robot.lookup("#boutonValider").queryAs(Button.class).isDisabled())
                .as("une panne de récupération ne rend pas la déclaration légitime")
                .isTrue();
    }

    @Test
    @DisplayName("#3806 : « Créer » se ferme, et son infobulle dit POURQUOI et quoi faire")
    void creer_se_ferme_avec_son_motif(FxRobot robot) {
        enCreation(robot);
        verdictCarrePresent(robot);

        Button creer = robot.lookup("#boutonValider").queryAs(Button.class);
        assertThat(creer.isDisabled())
                .as("déclarer ici referait le doublon local qui a produit le dépôt manqué")
                .isTrue();
        // Un gris sans motif est un défaut ; un gris au MAUVAIS motif en est un pire : le carré a bien
        // ses six chiffres, dire le contraire enverrait l'utilisateur corriger ce qui est déjà juste.
        assertThat(InfobulleDeBlocage.texteDe(robot.lookup("#enveloppeValider").queryAs(StackPane.class)))
                .contains("existe déjà")
                .doesNotContain("6 chiffres");

        // Ce que ce cas fait juger tient à l'écran en même temps : le verdict « il existe déjà », le
        // bouton « Créer » fermé, et le motif porté par son infobulle. On s'y arrête.
        Respiration.surLeMomentCle(robot);
    }
}
