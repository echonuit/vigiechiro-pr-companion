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
import fr.univ_amu.iut.recette.Attente;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les trois colonnes à pastille de « Ma saison » montrent leurs libellés en entier (#6101, #6106).
///
/// Une case de passage dit un état suivi d'une date. Les états semés sont tous ceux que
/// `SaisonController` sait écrire : les sept statuts, « Inexploitable », « Opportuniste » et
/// « Non planifié ».
///
/// « Hors protocole » nomme une nuit opportuniste seule avec sa date, et compte les nuits dès la
/// deuxième (#6106). Jusqu'au 2026-10-06 la pastille joignait une entrée par nuit : deux nuits
/// étaient coupées à 145 px, et aucune largeur ne bornait la suite. Ce test tient le cas d'une
/// nuit, de deux, de cinq, et de deux comptes à trois chiffres, puis l'infobulle qui détaille les
/// nuits que le compte ne nomme plus.
@ExtendWith(ApplicationExtension.class)
class PastillesDeLaSaisonTest {

    private static final String FXML = "Saison.fxml";
    private static final LocalDate JOUR = LocalDate.of(2026, 8, 28);
    private static final String DATE = " · 28/08";
    private static final long DELAI_MS = 5_000L;

    /// Les nuits hors protocole semées sur les cinq premières lignes, une ligne par compte.
    private static final List<Integer> NUITS_HORS_PROTOCOLE = List.of(1, 2, 5, 100, 999);

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
            List<CasePassage> horsProtocole =
                    i < NUITS_HORS_PROTOCOLE.size() ? nuitsHorsProtocole(NUITS_HORS_PROTOCOLE.get(i)) : List.of();
            lignes.add(new LigneSaison(
                    "64000" + i, "A1", (long) i, cases.get(i), cases.get(i), horsProtocole, "", "Étang", "Ahetze"));
        }
        return lignes;
    }

    /// `combien` nuits opportunistes, datées de jours qui se suivent à partir du 28/08.
    ///
    /// Le service n'en range aujourd'hui que deux par point, celles des passages 1 et 2. La liste
    /// du modèle n'a pas cette borne, et la colonne est tenue sans dépendre d'elle.
    private static List<CasePassage> nuitsHorsProtocole(int combien) {
        List<CasePassage> nuits = new ArrayList<>();
        for (int i = 0; i < combien; i++) {
            nuits.add(new CasePassage(2L, StatutWorkflow.DEPOSE, Verdict.OK, JOUR.plusDays(i), true, null));
        }
        return nuits;
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

    /// L'infobulle de la pastille « Hors protocole » qui porte `libelle`, ou `null` sans infobulle.
    private String infobulleDeLaPastille(String libelle) {
        TableColumn<?, ?> horsProtocole = colonne("Hors protocole");
        return Attente.surLeFil(
                () -> {
                    table.applyCss();
                    table.layout();
                    TableCell<?, ?> pastille = table.lookupAll(".table-cell").stream()
                            .filter(TableCell.class::isInstance)
                            .map(noeud -> (TableCell<?, ?>) noeud)
                            .filter(cellule -> cellule.getTableColumn() == horsProtocole)
                            .filter(cellule -> libelle.equals(cellule.getText()))
                            .findFirst()
                            .orElseThrow();
                    return pastille.getTooltip() == null
                            ? null
                            : pastille.getTooltip().getText();
                },
                "lire l'infobulle de la pastille « " + libelle + " »",
                DELAI_MS);
    }

    @Test
    @DisplayName("#6106 : la colonne « Hors protocole » montre en entier une nuit datée, puis un compte")
    void la_colonne_hors_protocole_est_entiere() {
        assertThat(PastillesEntieres.libellesEntiers(table, colonne("Hors protocole"), FXML))
                .containsExactlyInAnyOrder("Opportuniste" + DATE, "2 nuits", "5 nuits", "100 nuits", "999 nuits");
    }

    @Test
    @DisplayName("#6106 : au-delà d'une nuit, l'infobulle de la pastille nomme chaque nuit, une par ligne")
    void plusieurs_nuits_se_detaillent_en_infobulle() {
        assertThat(infobulleDeLaPastille("2 nuits")).isEqualTo("Opportuniste · 28/08\nOpportuniste · 29/08");
        assertThat(infobulleDeLaPastille("5 nuits"))
                .isEqualTo("Opportuniste · 28/08\nOpportuniste · 29/08\nOpportuniste · 30/08\n"
                        + "Opportuniste · 31/08\nOpportuniste · 01/09");
    }

    @Test
    @DisplayName("#6106 : une nuit seule n'a pas d'infobulle, la pastille dit déjà tout")
    void une_nuit_seule_n_a_pas_d_infobulle() {
        assertThat(infobulleDeLaPastille("Opportuniste" + DATE)).isNull();
    }
}
