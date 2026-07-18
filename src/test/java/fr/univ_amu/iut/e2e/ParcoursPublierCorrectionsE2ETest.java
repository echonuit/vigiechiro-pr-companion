package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.ResultatEcriture;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// **Test E2E de la publication des corrections** (#723, refondue par #1838) : d'une nuit importée
/// **par CSV** jusqu'au `PATCH` parti vers la plateforme.
///
/// Ce parcours existe pour une **couture**, pas pour un maillon. Depuis #1838, l'import rapide dépose
/// des observations **sans ancrage** (le CSV n'en porte pas) et c'est la **publication** qui va le
/// chercher au moment d'envoyer. Les deux moitiés ont chacune leurs tests unitaires ; aucun ne prouve
/// qu'elles se rejoignent. Or le défaut probable est **entre elles** : l'import qui n'ancre plus, la
/// publication qui croit devoir ancrer, l'aperçu qui conclut « rien à publier » avant que l'ancrage
/// n'existe. C'est exactement ce qu'un utilisateur vit d'un bout à l'autre, et exactement ce que le
/// chantier a failli casser deux fois.
///
/// Câblage **réel** de l'application ([RacineInjecteur]), seule la plateforme est bouchonnée.
@ExtendWith(ApplicationExtension.class)
class ParcoursPublierCorrectionsE2ETest {

    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";
    private static final String SEQUENCE = "seq0";
    private static final String TAXON_RETENU = "Nyclei";
    private static final String OBJECTID_TAXON = "5c9a2ab1f89a0700018cbcf1";

    private Injector injector;
    private ClientVigieChiro plateforme;
    private long idPassage;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-publier");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        plateforme = plateformeBouchonnee();
        injector = Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(liaison -> liaison.bind(ClientVigieChiro.class).toInstance(plateforme)));

        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        semerLaNuit(source);

        LienVigieChiroDao liens = new LienVigieChiroDao(source);
        // La nuit est rattachée : c'est la seule condition pour que la publication sache s'ancrer.
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), PARTICIPATION));
        // Le taxon retenu doit avoir son objectid plateforme, sinon il serait écarté « hors référentiel ».
        // « Nyclei » vient du référentiel semé par les migrations : rien à inventer ici.
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_TAXON, TAXON_RETENU, OBJECTID_TAXON));

        // L'import RAPIDE, celui de l'écran : le CSV d'un coup. Il ne pose aucun ancrage — c'est le point
        // de départ du parcours, pas un raccourci de test.
        injector.getInstance(ImportVigieChiro.class).importerRapide(idPassage, false, (page, total) -> {});

        FXMLLoader loader =
                new FXMLLoader(AudioViewModel.class.getResource("/fr/univ_amu/iut/audio/view/SonsValidation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        fr.univ_amu.iut.audio.view.SonsValidationController controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.ParPassage(
                new ContextePassage(idPassage, 1, new ContexteSite("130711", "Z41", "Étang"))));
        stage.setScene(new Scene(vue, 1400, 800));
        stage.show();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#1838 : nuit importée par CSV — l'écran la montre sans ancrage, et publier va le chercher")
    void publier_une_nuit_importee_par_csv(FxRobot robot) {
        WaitForAsyncUtils.waitForFxEvents();

        TableView<LigneObservationAudio> table =
                robot.lookup("#tableObservations").queryAs(TableView.class);
        assertThat(table.getItems()).as("l'import rapide a bien rempli l'écran").hasSize(1);

        Observation avant = observations().getFirst();
        assertThat(avant.idDonneeVigieChiro())
                .as("le CSV ne porte pas d'ancrage : c'est l'état que #1838 doit savoir publier")
                .isNull();

        // L'observateur fait son travail : il corrige et déclare sa certitude.
        injector.getInstance(ServiceValidation.class).corriger(avant.id(), TAXON_RETENU, 0.9);
        injector.getInstance(SaisieCertitude.class).poser(avant.id(), Certitude.SUR);

        BilanPublication bilan = injector.getInstance(PublicationCorrections.class)
                .publier(idPassage, progres -> {}, JetonAnnulation.neutre());

        assertThat(bilan.poussees())
                .as("la correction est partie, alors qu'aucune observation n'était ancrée au départ")
                .isEqualTo(1);
        assertThat(bilan.sansAncrage())
                .as("plus rien n'est écarté faute d'ancrage : la publication est allée le chercher")
                .isZero();

        // La couture, vue du serveur : on a d'abord tiré les `donnees` (pour l'ancrage), puis poussé le
        // PATCH sur l'identifiant qu'elles ont donné — avec le taxon de l'observateur, pas celui de Tadarida.
        verify(plateforme).donnees(eq(PARTICIPATION), any());
        verify(plateforme).corrigerObservation("d1", 0, OBJECTID_TAXON, Certitude.SUR, true);

        Observation apres = observations().getFirst();
        assertThat(apres.idDonneeVigieChiro())
                .as("l'ancrage rapatrié reste en base : republier ne le repaiera pas")
                .isEqualTo("d1");
        assertThat(apres.taxonObservateur())
                .as("et le ré-import d'ancrage n'a pas coûté sa correction à l'observateur")
                .isEqualTo(TAXON_RETENU);
        assertThat(apres.certitudeObservateur()).isEqualTo(Certitude.SUR);
    }

    private List<Observation> observations() {
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        Long idResultats = new ResultatsIdentificationDao(source)
                .findByPassage(idPassage)
                .orElseThrow()
                .id();
        return new ObservationDao(source).findByResults(idResultats);
    }

    /// Plateforme bouchonnée : le CSV pour l'import rapide, les `donnees` pour l'ancrage, un `PATCH` qui
    /// accepte. Les `donnees` portent l'ancrage `d1`/indice 0 sur **la même séquence** que le CSV : c'est
    /// ce rapprochement par nom de fichier qui fait tenir la chaîne.
    private ClientVigieChiro plateformeBouchonnee() {
        ClientVigieChiro client = mock(ClientVigieChiro.class);
        when(client.csvObservations(PARTICIPATION)).thenReturn(ReponseApi.succes(Optional.of(csvObservations())));
        when(client.donnees(eq(PARTICIPATION), any()))
                .thenReturn(ReponseApi.succes(List.of(new DonneeVigieChiro(
                        "d1",
                        SEQUENCE,
                        List.of(new ObservationVigieChiro(
                                0, "Pippip", 0.8, 45.0, 0.0, 5.0, null, null, null, null, null, List.of()))))));
        when(client.corrigerObservation(anyString(), anyInt(), anyString(), any(), anyBoolean()))
                .thenReturn(ResultatEcriture.reussie());
        return client;
    }

    private static String csvObservations() {
        return ligne(
                        "nom du fichier",
                        "temps_debut",
                        "temps_fin",
                        "frequence_mediane",
                        "tadarida_taxon",
                        "tadarida_probabilite",
                        "tadarida_taxon_autre",
                        "observateur_taxon",
                        "observateur_probabilite",
                        "validateur_taxon",
                        "validateur_probabilite")
                + ligne(SEQUENCE, "0.0", "5.0", "45", "Pippip", "0.80", "", "", "", "", "");
    }

    private static String ligne(String... champs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < champs.length; i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append('"').append(champs[i]).append('"');
        }
        return sb.append('\n').toString();
    }

    /// Topologie de la nuit par la **fixture** partagee ([JeuDeDonneesPassage]) : ce parcours parle de
    /// publication, pas de plomberie relationnelle. Une seule sequence suffit - c'est elle que le CSV et
    /// les `donnees` doivent designer pareil.
    private void semerLaNuit(SourceDeDonnees source) {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .nomSite("Etang")
                .point("Z41")
                .statut(StatutWorkflow.DEPOSE)
                .semer();
        idPassage = jeu.idPassage();
        jeu.ajouterSequence();
    }
}
