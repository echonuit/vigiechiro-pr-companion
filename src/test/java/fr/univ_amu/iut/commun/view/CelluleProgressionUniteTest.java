package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.LigneSuivi;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La cellule « Progression » d'une table de suivi par unité (#3521).
///
/// C'était la plus grosse classe de vue que **rien** n'exerçait : 30 mutants sans couverture, et pas
/// une citation dans `src/test`. Elle rend une unité selon son état - barre vive tant qu'elle se
/// traite, icône et libellé sinon - et elle réagit **en place**, le travail amont pouvant être
/// parallèle (#814).
///
/// Ce qu'une absence de test laisse passer ici n'est pas un rendu faux : c'est une cellule qui
/// **cesse de suivre sa ligne**, ou qui suit **celle d'avant**. `TableView` recycle ses cellules ; sans
/// le désabonnement de `updateItem`, une cellule réaffectée continue d'écouter la ligne qu'elle
/// n'affiche plus. Rien à l'écran ne le dit tant que l'ancienne ligne ne bouge pas.
@ExtendWith(ApplicationExtension.class)
class CelluleProgressionUniteTest {

    @Start
    void start(Stage stage) {
        // Toolkit JavaFX initialisé : la cellule construit ProgressBar, FontIcon et Label.
    }

    /// Spécialisation de test : `signalerReprise` est `protected`, réservée aux lignes qui réessaient.
    private static final class LigneQuiReessaie extends LigneSuivi {
        LigneQuiReessaie(int numero) {
            super(numero);
        }

        void reprise(String message) {
            signalerReprise(message);
        }
    }

    private static CelluleProgressionUnite<LigneSuivi> celluleAffichant(LigneSuivi ligne) {
        CelluleProgressionUnite<LigneSuivi> cellule = new CelluleProgressionUnite<>();
        cellule.updateItem(ligne, false);
        return cellule;
    }

    private static List<Node> enfants(CelluleProgressionUnite<?> cellule) {
        return ((HBox) cellule.getGraphic()).getChildren();
    }

    @Test
    @DisplayName("#3521 : « en cours » montre la barre, les autres états une icône et un libellé")
    void la_barre_ne_s_affiche_qu_en_cours() {
        LigneSuivi enCours = new LigneSuivi(1);
        enCours.progresser(0.4);
        CelluleProgressionUnite<LigneSuivi> cellule = celluleAffichant(enCours);

        assertThat(enfants(cellule)).singleElement().isInstanceOf(ProgressBar.class);
        assertThat(((ProgressBar) enfants(cellule).get(0)).getProgress()).isEqualTo(0.4);

        LigneSuivi terminee = new LigneSuivi(2);
        terminee.terminer();
        assertThat(enfants(celluleAffichant(terminee)))
                .extracting(n -> n.getClass().getSimpleName())
                .containsExactly("FontIcon", "Label");
    }

    @Test
    @DisplayName("#3521 : chaque état porte son icône et son libellé")
    void chaque_etat_a_son_icone() {
        LigneSuivi enAttente = new LigneSuivi(1);
        assertThat(iconeDe(celluleAffichant(enAttente))).isEqualTo("fas-clock");
        assertThat(libelleDe(celluleAffichant(enAttente))).isEqualTo("En attente");

        LigneSuivi terminee = new LigneSuivi(2);
        terminee.terminer();
        assertThat(iconeDe(celluleAffichant(terminee))).isEqualTo("fas-check-circle");
        assertThat(libelleDe(celluleAffichant(terminee))).isEqualTo("Terminée");

        LigneSuivi echec = new LigneSuivi(3);
        echec.echouer("Disque plein");
        assertThat(iconeDe(celluleAffichant(echec))).isEqualTo("fas-times-circle");
        assertThat(libelleDe(celluleAffichant(echec))).isEqualTo("Échec");
    }

    @Test
    @DisplayName("#3521 : seul l'échec porte une infobulle, et elle dit la raison")
    void seul_l_echec_explique() {
        LigneSuivi echec = new LigneSuivi(1);
        echec.echouer("Espace disque insuffisant");
        assertThat(celluleAffichant(echec).getTooltip().getText()).isEqualTo("Espace disque insuffisant");

        LigneSuivi terminee = new LigneSuivi(2);
        terminee.terminer();
        assertThat(celluleAffichant(terminee).getTooltip()).isNull();
    }

    @Test
    @DisplayName("#3521 : une reprise réseau s'affiche À CÔTÉ de la barre, et disparaît quand ça repart")
    void la_reprise_reseau_accompagne_la_barre() {
        LigneQuiReessaie ligne = new LigneQuiReessaie(1);
        ligne.progresser(0.3);
        CelluleProgressionUnite<LigneSuivi> cellule = celluleAffichant(ligne);

        assertThat(enfants(cellule)).singleElement().isInstanceOf(ProgressBar.class);

        ligne.reprise("Reprise…");
        assertThat(enfants(cellule)).hasSize(2);
        assertThat(((Label) enfants(cellule).get(1)).getText()).isEqualTo("Reprise…");

        // Les octets repartent : la coupure est absorbée, la mention s'efface d'elle-même.
        ligne.progresser(0.5);
        assertThat(enfants(cellule)).singleElement().isInstanceOf(ProgressBar.class);
    }

    @Test
    @DisplayName("#3521 : la cellule suit sa ligne EN PLACE, sans attendre un nouveau rendu de la table")
    void la_cellule_suit_les_changements_de_sa_ligne() {
        // Le travail amont peut être parallèle (#814) : une ligne évolue pendant qu'elle est affichée.
        LigneSuivi ligne = new LigneSuivi(1);
        ligne.progresser(0.2);
        CelluleProgressionUnite<LigneSuivi> cellule = celluleAffichant(ligne);

        ligne.terminer();

        assertThat(enfants(cellule))
                .extracting(n -> n.getClass().getSimpleName())
                .containsExactly("FontIcon", "Label");
        assertThat(libelleDe(cellule)).isEqualTo("Terminée");
    }

    @Test
    @DisplayName("#3521 : une cellule recyclée cesse d'écouter la ligne qu'elle n'affiche plus")
    void une_cellule_recyclee_oublie_l_ancienne_ligne() {
        // `TableView` recycle ses cellules. Sans le désabonnement de `updateItem`, l'ancienne ligne
        // continuerait de piloter l'affichage : la cellule montrerait l'état d'une ligne qui n'est plus
        // la sienne, et seulement quand celle-ci bouge. Aucun rendu ne le révèle.
        LigneSuivi ancienne = new LigneSuivi(1);
        ancienne.progresser(0.2);
        CelluleProgressionUnite<LigneSuivi> cellule = celluleAffichant(ancienne);

        LigneSuivi nouvelle = new LigneSuivi(2);
        nouvelle.echouer("Fichier illisible");
        cellule.updateItem(nouvelle, false);

        ancienne.terminer(); // l'ancienne bouge : la cellule ne doit pas broncher

        assertThat(libelleDe(cellule)).isEqualTo("Échec");
        assertThat(cellule.getTooltip().getText()).isEqualTo("Fichier illisible");
    }

    @Test
    @DisplayName("#3521 : un échec sans changement de fraction s'affiche quand même")
    void un_changement_d_etat_seul_suffit_a_re_rendre() {
        // Deux abonnements couvrent la ligne (état et fraction) et se masquent l'un l'autre : tant qu'un
        // changement touche les deux, perdre l'un ne se voit pas. `echouer()` ne touche PAS la fraction,
        // donc seul l'abonnement à l'état peut rattraper le coup. Sans lui, une unité qui échoue reste
        // affichée « en cours », indéfiniment.
        LigneSuivi ligne = new LigneSuivi(1);
        ligne.progresser(0.5);
        CelluleProgressionUnite<LigneSuivi> cellule = celluleAffichant(ligne);

        ligne.echouer("Fichier illisible");

        assertThat(libelleDe(cellule)).isEqualTo("Échec");
    }

    @Test
    @DisplayName("#3521 : un avancement sans changement d'état fait bouger la barre")
    void un_changement_de_fraction_seul_suffit_a_re_rendre() {
        // Symétrique du précédent : `progresser` sur une unité DÉJÀ en cours ne change que la fraction,
        // l'état étant réécrit à sa valeur courante. Sans l'abonnement à la fraction, la barre resterait
        // figée pendant tout le traitement.
        LigneSuivi ligne = new LigneSuivi(1);
        ligne.progresser(0.2);
        CelluleProgressionUnite<LigneSuivi> cellule = celluleAffichant(ligne);

        ligne.progresser(0.75);

        assertThat(((ProgressBar) enfants(cellule).get(0)).getProgress()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("#3521 : repartir « en cours » retire l'infobulle de l'échec précédent")
    void reprendre_efface_l_explication_de_l_echec() {
        // Une unité qu'on relance après un échec garderait sinon l'infobulle de la fois d'avant : une
        // explication qui ne vaut plus, sur une barre qui tourne.
        LigneSuivi ligne = new LigneSuivi(1);
        ligne.echouer("Disque plein");
        CelluleProgressionUnite<LigneSuivi> cellule = celluleAffichant(ligne);
        assertThat(cellule.getTooltip()).isNotNull();

        ligne.demarrer();

        assertThat(cellule.getTooltip()).isNull();
    }

    @Test
    @DisplayName("#3521 : la cellule tient le contrat de TableCell, pas seulement son graphique")
    void la_cellule_tient_le_contrat_de_table_cell() {
        // `updateItem` doit déléguer à `super` : sans cela, `getItem()` et `isEmpty()` mentent, et tout
        // ce que `TableView` décide à partir d'eux - sélection, édition, styles de ligne - part de faux.
        LigneSuivi ligne = new LigneSuivi(1);
        CelluleProgressionUnite<LigneSuivi> cellule = celluleAffichant(ligne);

        assertThat(cellule.getItem()).isSameAs(ligne);
        assertThat(cellule.isEmpty()).isFalse();

        cellule.updateItem(null, true);

        assertThat(cellule.getItem()).isNull();
        assertThat(cellule.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("#3521 : une cellule vide n'affiche rien et ne garde pas d'infobulle")
    void une_cellule_vide_se_nettoie() {
        LigneSuivi echec = new LigneSuivi(1);
        echec.echouer("Disque plein");
        CelluleProgressionUnite<LigneSuivi> cellule = celluleAffichant(echec);

        cellule.updateItem(null, true);

        assertThat(cellule.getGraphic()).isNull();
        assertThat(cellule.getTooltip()).isNull();
    }

    private static String iconeDe(CelluleProgressionUnite<?> cellule) {
        return ((FontIcon) enfants(cellule).get(0)).getIconLiteral();
    }

    private static String libelleDe(CelluleProgressionUnite<?> cellule) {
        return ((Label) enfants(cellule).get(1)).getText();
    }
}
