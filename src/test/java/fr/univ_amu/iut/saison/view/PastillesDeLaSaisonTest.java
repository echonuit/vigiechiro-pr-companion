package fr.univ_amu.iut.saison.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.PastillesEntieres;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.saison.model.SoldeSaison;
import fr.univ_amu.iut.saison.viewmodel.SaisonViewModel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les trois colonnes à pastille de « Ma saison » montrent leurs libellés en entier (#6101).
///
/// Une case de passage dit un état suivi d'une date. Les états semés sont tous ceux que
/// `SaisonController` sait écrire : les sept statuts, « Inexploitable », « Opportuniste » et
/// « Non planifié ».
///
/// « Hors protocole » n'a pas de plus long libellé : la pastille s'allonge d'une entrée par nuit
/// opportuniste du point. Ce test tient le cas d'une nuit. Mesuré le 2026-10-06, deux nuits sont
/// coupées à 145 px, et aucune largeur ne borne la suite : ce cas-là n'est pas gardé ici.
@ExtendWith(ApplicationExtension.class)
class PastillesDeLaSaisonTest {

    private static final String FXML = "Saison.fxml";
    private static final LocalDate JOUR = LocalDate.of(2026, 8, 28);
    private static final String DATE = " · 28/08";

    private TableView<?> table;

    @Start
    void demarrer(Stage fenetre) throws Exception {
        ServiceSoldeSaison service = mock(ServiceSoldeSaison.class);
        SoldeSaison solde = new SoldeSaison(2026, LocalDate.of(2026, 9, 1), lignes());
        when(service.soldeCourant(anyString(), isNull())).thenReturn(solde);
        when(service.soldePour(anyString(), anyInt())).thenReturn(solde);
        FXMLLoader chargeur = new FXMLLoader(SaisonController.class.getResource(FXML));
        chargeur.setControllerFactory(type -> new SaisonController(
                new SaisonViewModel(service, "u-1"), mock(OuvrirPassage.class), mock(OuvrirSite.class)));
        Parent vue = chargeur.load();
        table = (TableView<?>) vue.lookup("#tableSaison");
        // Habillée, comme dans le chrome qui l'empile : sans la feuille de base, la scène prend la
        // police du système, et la largeur mesurée n'est plus celle de l'application.
        FenetreAjustable.poserHabillee(fenetre, vue, 1400, 600);
        FenetreAjustable.afficher(fenetre);
    }

    private static List<LigneSaison> lignes() {
        List<CasePassage> cases = new ArrayList<>();
        for (StatutWorkflow statut : StatutWorkflow.values()) {
            cases.add(new CasePassage(1L, statut, Verdict.OK, JOUR, false, null));
        }
        cases.add(new CasePassage(1L, StatutWorkflow.VERIFIE, Verdict.A_JETER, JOUR, false, null));
        cases.add(new CasePassage(1L, StatutWorkflow.DEPOSE, Verdict.OK, JOUR, true, null));
        cases.add(CasePassage.absente());
        List<LigneSaison> lignes = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            // Une seule ligne porte une nuit hors protocole, et une seule nuit : voir l'en-tête.
            List<CasePassage> horsProtocole = i == 0
                    ? List.of(new CasePassage(2L, StatutWorkflow.DEPOSE, Verdict.OK, JOUR, true, null))
                    : List.of();
            lignes.add(new LigneSaison(
                    "64000" + i, "A1", (long) i, cases.get(i), cases.get(i), horsProtocole, "", "Étang", "Ahetze"));
        }
        return lignes;
    }

    private static List<String> libellesDesCases() {
        List<String> attendus = new ArrayList<>();
        for (StatutWorkflow statut : StatutWorkflow.values()) {
            attendus.add(statut.libelle() + DATE);
        }
        attendus.add("Inexploitable" + DATE);
        attendus.add("Opportuniste" + DATE);
        attendus.add("Non planifié");
        return attendus;
    }

    private TableColumn<?, ?> colonne(String titre) {
        return table.getColumns().stream()
                .filter(colonne -> titre.equals(colonne.getText()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("#6101 : la colonne « Passage 1 » montre chaque état daté en entier")
    void le_passage_1_est_entier() {
        assertThat(PastillesEntieres.libellesEntiers(table, colonne("Passage 1"), FXML))
                .containsExactlyInAnyOrderElementsOf(libellesDesCases());
    }

    @Test
    @DisplayName("#6101 : la colonne « Passage 2 » montre chaque état daté en entier")
    void le_passage_2_est_entier() {
        assertThat(PastillesEntieres.libellesEntiers(table, colonne("Passage 2"), FXML))
                .containsExactlyInAnyOrderElementsOf(libellesDesCases());
    }

    @Test
    @DisplayName("#6101 : la colonne « Hors protocole » montre une nuit opportuniste datée en entier")
    void une_nuit_hors_protocole_est_entiere() {
        assertThat(PastillesEntieres.libellesEntiers(table, colonne("Hors protocole"), FXML))
                .containsExactly("Opportuniste" + DATE);
    }
}
