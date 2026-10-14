package fr.univ_amu.iut.perf.outils;

import static fr.univ_amu.iut.perf.outils.EcranDuBanc.RACINE;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.IMAGES_DE_GARDE;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.attendreLesImages;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.supprimer;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.surLeFil;
import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.view.ReactivationDuBanc;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.Gel;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.Releve;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.stream.Collectors;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le banc de l'IHM pendant la réactivation d'une nuit hydratée, menée par sa modale (#6256).
///
/// La nuit est importée, archivée et réduite à des placeholders avant que l'écran ne se monte, comme
/// une nuit reconstruite : la réactivation prend alors la voie d'hydratation, qui régénère ses bruts
/// en parallèle. Puis le banc ouvre la fiche du passage, désigne le dossier sans dialogue, clique
/// « Réactiver ce passage », et date chaque écart entre deux images parmi les jalons de la modale.
///
/// Dimensions par `perf.ihm.reactivation.fichiers` (défaut 1572) et `perf.ihm.reactivation.secondes`
/// (défaut 5). Comme les autres bancs de l'IHM : affichage réel, tag `banc-ihm`, jamais en CI.
@Tag("banc-ihm")
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class BancIhmReactivationTest {

    private static final long DELAI_MS = 60 * 60_000L;
    private static final int FICHIERS = Integer.getInteger("perf.ihm.reactivation.fichiers", 1572);
    private static final double SECONDES =
            Double.parseDouble(System.getProperty("perf.ihm.reactivation.secondes", "5.0"));
    private static final String CARRE = "640380";
    private static final String POINT = "A1";
    private static final String TITRE_DE_LA_MODALE = "Réactiver ce passage";

    private EcranDuBanc ecran;
    private Long idPassage;

    @Start
    void start(Stage stage) {
        ecran = new EcranDuBanc(stage);
    }

    @AfterEach
    void ranger() throws Exception {
        ecran.arreterLaSonde();
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Réactivation d'une nuit hydratée par la modale : la sonde date chaque écart")
    void reactivation_d_une_nuit_hydratee() throws Exception {
        Path carte = RACINE.resolve("carte-reactivation");
        supprimer(carte);
        BancImport.genererNuit(carte, FICHIERS, 384_000, SECONDES, LocalDate.of(2026, 4, 22));
        ecran.monter(EcranDuBanc.espace("reactivation"), inj -> {
            Long idPoint = BancImport.seeder(inj);
            ResultatImport importe = inj.getInstance(ServiceImport.class)
                    .importer(carte, idPoint, new Prefixe(CARRE, 2026, 1, POINT), progres -> {});
            Long idSession = importe.session().id();
            BancReactivation.archiver(inj, idSession);
            BancReactivation.oublierLesOriginaux(inj.getInstance(EnregistrementOriginalDao.class), idSession);
            idPassage = importe.passage().id();
        });
        Releve releve = ecran.releve(String.format(
                Locale.ROOT, "Réactivation d'une nuit hydratée (%d bruts de %.0f s)", FICHIERS, SECONDES));
        ecran.controlerLaDatation(releve);
        releve.noterTexte(
                "nuit préparée", "importée, archivée, originaux réduits à des placeholders : voie d'hydratation");

        Button reactiver = ouvrirLaFiche(carte);
        ecran.reinitialiserLaSonde();
        long debut = System.nanoTime();
        JalonsDeLaReactivation jalons = surLeFil(() -> {
            JalonsDeLaReactivation j = new JalonsDeLaReactivation();
            j.marquerLeClic();
            reactiver.fire();
            j.poser(modale());
            return j;
        });
        Attente.queSurLeFil(() -> jalons.conclue() || intrus() != null, "la réactivation n'a pas conclu", DELAI_MS);
        assertThat(surLeFil(this::intrus))
                .as("la réactivation d'une nuit de bruts ne doit rien demander à personne")
                .isNull();
        double dureeS = (System.nanoTime() - debut) / 1e9;
        attendreLesImages(ecran.sonde(), ecran.sonde().relever().images() + IMAGES_DE_GARDE);
        Gel pendant = ecran.sonde().relever();
        releve.noterReactivation(dureeS, pendant, jalons);
        releve.noterTexte("compte rendu de la modale", surLeFil(() -> texteDe(modale(), "#compteRenduChiffre")));
        surLeFil(() -> {
            ((Button) modale().getScene().lookup("#boutonFermer")).fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        supprimer(carte);
        releve.ecrire();
    }

    /// La fiche du passage réactivable, le dossier désigné : il ne reste qu'à cliquer.
    private Button ouvrirLaFiche(Path carte) throws Exception {
        surLeFil(() -> {
            ecran.injecteur().getInstance(OuvrirPassage.class).ouvrir(idPassage, new ContexteSite(CARRE, POINT, null));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        ReactivationDuBanc.designer(surLeFil(ecran::controleurAffiche), carte);
        Button reactiver = surLeFil(() -> ecran.noeud("#boutonReactiver", Button.class));
        assertThat(surLeFil(reactiver::isDisabled))
                .as("« Réactiver ce passage » doit être actif sur une nuit archivée")
                .isFalse();
        return reactiver;
    }

    /// La modale de réactivation, la seule fenêtre que ce banc attend en plus de la sienne.
    private static Stage modale() {
        return Window.getWindows().stream()
                .filter(Stage.class::isInstance)
                .map(Stage.class::cast)
                .filter(fenetre -> fenetre.isShowing() && TITRE_DE_LA_MODALE.equals(fenetre.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("la modale de réactivation ne s'est pas ouverte"));
    }

    /// Ce qui doit arrêter le banc : une question posée, ou une fenêtre autre que la sienne et la modale.
    private String intrus() {
        String intrus = ecran.intrus();
        if (intrus == null || intrus.contains("« " + TITRE_DE_LA_MODALE + " »")) {
            String autre = Window.getWindows().stream()
                    .filter(f -> f.isShowing() && f != ecran.fenetre())
                    .filter(f -> !(f instanceof Stage s && TITRE_DE_LA_MODALE.equals(s.getTitle())))
                    .findFirst()
                    .map(Object::toString)
                    .orElse(null);
            return autre == null ? ecran.questions().premiere() : "une fenêtre s'est ouverte : " + autre;
        }
        return intrus;
    }

    /// Les libellés que la modale affiche sous `id`, dans l'ordre du graphe de scène.
    private static String texteDe(Stage fenetre, String id) {
        return fenetre.getScene().lookup(id).lookupAll(".label").stream()
                .filter(Labeled.class::isInstance)
                .map(noeud -> ((Labeled) noeud).getText())
                .filter(texte -> texte != null && !texte.isBlank())
                .map(texte -> texte.replace('\n', ' '))
                .collect(Collectors.joining(" · "));
    }
}
