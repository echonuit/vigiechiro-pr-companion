package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires du sous-VM [RattachementImportViewModel] (étape 3 de M-Import), extrait de
/// [ImportationViewModel] (#183). [ServiceSites] est mocké ; aucune base de données.
@ExtendWith(MockitoExtension.class)
class RattachementImportViewModelTest {

    private static final String ID_USER = "u-1";
    private static final LocalDate JOUR = LocalDate.of(2026, 5, 31);
    private static final Site ETANG = new Site(1L, "640380", "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER);
    private static final PointDEcoute A1 = new PointDEcoute(10L, "A1", 43.5, 5.4, null, 1L);

    @Mock
    private ServiceSites serviceSites;

    private RattachementImportViewModel vm;

    @BeforeEach
    void preparer() {
        vm = new RattachementImportViewModel(serviceSites, new HorlogeFigee(JOUR), ID_USER);
    }

    @Test
    @DisplayName("État initial : année préremplie à l'horloge, n° passage 1, rattachement incomplet, aperçu vide")
    void etat_initial() {
        assertThat(vm.anneeProperty().get()).isEqualTo(2026);
        assertThat(vm.numeroPassageProperty().get()).isEqualTo(1);
        assertThat(vm.estComplet()).isFalse();
        assertThat(vm.apercuPrefixeProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("chargerSites alimente la liste des sites depuis le service")
    void charger_sites() {
        when(serviceSites.listerSites(ID_USER)).thenReturn(List.of(ETANG));

        vm.chargerSites();

        assertThat(vm.sites()).containsExactly(ETANG);
    }

    @Test
    @DisplayName("Choisir un site recharge ses points et réinitialise le point sélectionné")
    void site_recharge_points() {
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1));

        vm.siteSelectionneProperty().set(ETANG);

        assertThat(vm.points()).containsExactly(A1);
        assertThat(vm.pointSelectionneProperty().get()).isNull();
    }

    @Test
    @DisplayName("Rattachement complet : estComplet vrai, idPoint + préfixe + aperçu disponibles")
    void rattachement_complet() {
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1));
        vm.siteSelectionneProperty().set(ETANG);
        vm.pointSelectionneProperty().set(A1);

        assertThat(vm.estComplet()).isTrue();
        assertThat(vm.idPointSelectionne()).isEqualTo(10L);
        assertThat(vm.prefixeCourant()).isNotNull();
        // L'aperçu se recalcule à chaque champ ; non vide dès que site + point sont choisis.
        assertThat(vm.apercuPrefixeProperty().get()).isNotBlank().contains("640380");
    }

    @Test
    @DisplayName("Un n° de passage < 1 rend le rattachement incomplet")
    void numero_passage_invalide() {
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1));
        vm.siteSelectionneProperty().set(ETANG);
        vm.pointSelectionneProperty().set(A1);
        vm.numeroPassageProperty().set(0);

        assertThat(vm.estComplet()).isFalse();
    }

    @Test
    @DisplayName("#111 : des originaux bruts ou concordants ne déclenchent aucun avertissement de préfixe")
    void prefixe_concordant_pas_d_avertissement() {
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1));
        vm.siteSelectionneProperty().set(ETANG);
        vm.pointSelectionneProperty().set(A1); // préfixe attendu : Car640380-2026-Pass1-A1-

        // Fichiers bruts : rien à signaler.
        vm.definirOriginaux(List.of("PaRecPR1925492_20260422_203922.wav"));
        assertThat(vm.avertissementPrefixeProperty().get()).isEmpty();

        // Déjà préfixés mais concordants avec le rattachement : rien à signaler non plus.
        vm.definirOriginaux(List.of("Car640380-2026-Pass1-A1-PaRecPR1925492_20260422_203922.wav"));
        assertThat(vm.avertissementPrefixeProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#111 : un préfixe discordant sur N'IMPORTE QUEL original (pas que le 1er) avertit")
    void prefixe_discordant_avertit_sur_tout_le_dossier() {
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1));
        vm.siteSelectionneProperty().set(ETANG);
        vm.pointSelectionneProperty().set(A1);

        // 1er fichier concordant, 2e discordant : l'avertissement doit quand même apparaître (finding 1).
        vm.definirOriginaux(List.of(
                "Car640380-2026-Pass1-A1-PaRecPR1925492_20260422_203922.wav",
                "Car999999-2025-Pass3-B2-PaRecPR1648011_20260430_210000.wav"));

        assertThat(vm.avertissementPrefixeProperty().get())
                .as("discordance détectée sur l'ensemble du dossier")
                .contains("ne correspondent pas")
                .contains("Car640380-2026-Pass1-A1-"); // préfixe attendu rappelé

        // Corriger le n° de passage vers celui des fichiers ne suffit pas (carré/point/année diffèrent).
        // En revanche, vider le dossier efface l'avertissement.
        vm.definirOriginaux(List.of());
        assertThat(vm.avertissementPrefixeProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#111 : sans site/point choisi, aucun avertissement de préfixe (rattachement incomplet)")
    void prefixe_pas_d_avertissement_sans_rattachement() {
        vm.definirOriginaux(List.of("Car999999-2025-Pass3-B2-PaRec_x.wav"));

        assertThat(vm.avertissementPrefixeProperty().get()).isEmpty();
    }
}
