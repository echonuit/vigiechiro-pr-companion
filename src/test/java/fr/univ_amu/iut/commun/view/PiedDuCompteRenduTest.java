package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.PastillesEntieres.Pastille;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import fr.univ_amu.iut.recette.Attente;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le pied d'un compte rendu ne coupe jamais le libellé d'une action (#6013).
///
/// À la fin d'un import qui portait un rejet, le bouton se lisait « Ouvrir le pa… ». Le pied met côte à
/// côte les actions et le résumé des motifs, et ce résumé peut être plus large que la bande : la raison
/// d'un rejet cite le chemin du fichier. Une `HBox` répartit alors le manque sur tout ce qui peut
/// rétrécir, bouton compris. Les bancs passaient, `getText()` gardant le libellé entier.
///
/// Cette classe lit ce que le pied **dessine**, sur une scène habillée. Le compte rendu y est bâti à la
/// main : la lecture du vrai compte rendu d'un import, avec le chemin de la carte du banc, est tenue
/// par `ScenarioRejetsEtArchiveTest`.
@ExtendWith(ApplicationExtension.class)
class PiedDuCompteRenduTest {

    /// La largeur du panneau sur l'écran d'import d'une fenêtre de 1 180, mesurée dans le banc filmé.
    private static final double LARGEUR_DU_PANNEAU = 1118;

    private static final String OUVRIR = "Ouvrir le passage";

    private static final String FICHIER = "PaRecPR1925492_20260422_211500.wav";

    private static final String RAISON =
            "fichier(s) : Original illisible (Fichier WAV invalide (en-tête RIFF/WAVE absent) : ";

    /// Le dossier d'une carte matérialisée par le banc : c'est lui qui occupait la ligne du clip.
    private static final String CARTE_DU_BANC = "/tmp/vc-carte-sd-rejets10769599716138909057/sd-rejets/";

    /// Le dossier d'une carte montée sur le poste d'un observateur.
    private static final String CARTE_MONTEE = "/media/marie/PR1925492/";

    private static final long DELAI_MS = 5_000L;

    private Stage fenetre;
    private PanneauCompteRendu panneau;

    @Start
    void start(Stage stage) {
        fenetre = new Stage();
        fenetre.initOwner(stage);
    }

    @AfterEach
    void fermerLaFenetre(FxRobot robot) {
        robot.interact(fenetre::close);
    }

    @Test
    @DisplayName("#6013 : avec un rejet dont la raison déborde, le bouton se lit en entier et le résumé seul est coupé")
    void le_resume_porte_seul_le_deficit(FxRobot robot) {
        montrer(robot, avecRejet(CARTE_DU_BANC), LARGEUR_DU_PANNEAU);

        assertThat(dessin(OUVRIR))
                .as("le libellé d'une action ne se relit nulle part : il ne porte pas le déficit du pied")
                .matches(Pastille::entiere);
        Pastille resume = dessin(resumeAttendu(CARTE_DU_BANC));
        assertThat(resume.entiere())
                .as("le témoin : à cette largeur le résumé déborde bien, sans quoi ce test ne prouverait rien")
                .isFalse();
        assertThat(texteAuSurvol())
                .as("coupé, le résumé se relit en entier au survol, nom du fichier compris")
                .isEqualTo(resumeAttendu(CARTE_DU_BANC))
                .endsWith(FICHIER + ")");
    }

    @Test
    @DisplayName("#6013 : le fichier rejeté se retrouve par son nom en ouvrant le résumé")
    void le_fichier_rejete_se_retrouve_par_son_nom(FxRobot robot) {
        montrer(robot, avecRejet(CARTE_DU_BANC), LARGEUR_DU_PANNEAU);

        robot.interact(() -> ((Hyperlink) panneau.lookup(".cr-resume-motifs")).fire());

        assertThat(fichiersListes())
                .as("le détail ouvert nomme le fichier, que la ligne du résumé coupait avant son nom")
                .containsExactly(FICHIER);
    }

    @Test
    @DisplayName("#6013 : avec le chemin d'une carte montée, la ligne du rejet tient en entier")
    void une_carte_montee_tient_sur_la_ligne(FxRobot robot) {
        montrer(robot, avecRejet(CARTE_MONTEE), LARGEUR_DU_PANNEAU);

        assertThat(dessin(OUVRIR)).matches(Pastille::entiere);
        assertThat(dessin(resumeAttendu(CARTE_MONTEE)))
                .as("le chemin du banc est plus long que celui d'une vraie carte : celui-ci tient")
                .matches(Pastille::entiere);
    }

    @Test
    @DisplayName("#6013 : sans rejet, le bouton se lit en entier")
    void sans_rejet_le_bouton_se_lit_en_entier(FxRobot robot) {
        montrer(robot, compteRendu(List.of()), LARGEUR_DU_PANNEAU);

        assertThat(dessin(OUVRIR)).matches(Pastille::entiere);
    }

    private void montrer(FxRobot robot, CompteRenduChiffre rendu, double largeur) {
        robot.interact(() -> {
            panneau = new PanneauCompteRendu();
            panneau.afficher(rendu);
            Scene scene = Habillage.scene(panneau, largeur, 400);
            fenetre.setScene(scene);
            fenetre.show();
        });
    }

    /// Ce que le pied dessine pour le libellé `recu`, qui doit y figurer une fois.
    private Pastille dessin(String recu) {
        List<Pastille> lus = PastillesEntieres.lireLesLibelles(panneau).stream()
                .filter(pastille -> pastille.recu().equals(recu))
                .toList();
        assertThat(lus).as("le pied porte une fois « %s »", recu).hasSize(1);
        return lus.get(0);
    }

    private String texteAuSurvol() {
        return Attente.surLeFil(
                () -> {
                    Hyperlink resume = (Hyperlink) panneau.lookup(".cr-resume-motifs");
                    return resume.getTooltip() == null
                            ? null
                            : resume.getTooltip().getText();
                },
                "lire l'infobulle du résumé des motifs",
                DELAI_MS);
    }

    private List<String> fichiersListes() {
        return Attente.surLeFil(
                () -> panneau.lookupAll(".cr-motif-liste").stream()
                        .filter(noeud -> noeud.isVisible()
                                && noeud.getParent().getParent().isVisible())
                        .flatMap(noeud -> ((ListView<?>) noeud).getItems().stream())
                        .map(Object::toString)
                        .toList(),
                "lire les fichiers du détail des motifs",
                DELAI_MS);
    }

    private static String resumeAttendu(String dossier) {
        return "1 " + RAISON + dossier + FICHIER + ")";
    }

    private static CompteRenduChiffre avecRejet(String dossier) {
        return compteRendu(List.of(new Motif(RAISON + dossier + FICHIER + ")", List.of(FICHIER))));
    }

    private static CompteRenduChiffre compteRendu(List<Motif> motifs) {
        return new CompteRenduChiffre(
                "Import terminé - nuit du 22/04/2026, carré 640380 · A1",
                "8 / 9 importés",
                motifs.isEmpty() ? Severite.SUCCES : Severite.AVERTISSEMENT,
                List.of(),
                Ventilation.aucune(),
                motifs,
                List.of(),
                List.of(new Action(OUVRIR, true, () -> {})));
    }
}
