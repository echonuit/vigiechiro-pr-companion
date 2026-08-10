package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.PropositionCampagne;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    private static final PointDEcoute B2 = new PointDEcoute(11L, "B2", 43.6, 5.5, null, 1L);
    private static final Campagne ENS = new Campagne(7L, "Suivi ENS", 2026, null);
    private static final Campagne THESE = new Campagne(8L, "Thèse Samuel", 2025, null);

    private RattachementImportViewModel vm;

    @BeforeEach
    void preparer() {
        vm = new RattachementImportViewModel(serviceSites, new HorlogeFigee(JOUR), ID_USER, Optional.empty());
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
    @DisplayName("preselectionnerSite sélectionne le site d'id correspondant et recharge ses points")
    void preselection_site_correspondant() {
        when(serviceSites.listerSites(ID_USER)).thenReturn(List.of(ETANG));
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1));
        vm.chargerSites();

        vm.preselectionnerSite(1L);

        assertThat(vm.siteSelectionneProperty().get()).isEqualTo(ETANG);
        assertThat(vm.points()).containsExactly(A1);
    }

    @Test
    @DisplayName("preselectionnerSite ignore un id inconnu ou nul (aucune sélection)")
    void preselection_site_inconnu_ou_nul() {
        when(serviceSites.listerSites(ID_USER)).thenReturn(List.of(ETANG));
        vm.chargerSites();

        vm.preselectionnerSite(999L);
        assertThat(vm.siteSelectionneProperty().get()).isNull();

        vm.preselectionnerSite(null);
        assertThat(vm.siteSelectionneProperty().get()).isNull();
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
    @DisplayName("#2525 : la nature opportuniste est fausse par défaut, cochable, sans effet sur le préfixe")
    void opportuniste_par_defaut_faux_sans_effet_sur_prefixe() {
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1));
        vm.siteSelectionneProperty().set(ETANG);
        vm.pointSelectionneProperty().set(A1);
        String apercuAvant = vm.apercuPrefixeProperty().get();

        assertThat(vm.estOpportuniste()).as("décochée par défaut").isFalse();

        vm.opportunisteProperty().set(true);
        assertThat(vm.estOpportuniste()).isTrue();
        assertThat(vm.apercuPrefixeProperty().get())
                .as("la nature opportuniste n'entre pas dans le préfixe R6")
                .isEqualTo(apercuAvant);
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
        assertThat(vm.avertissementPrefixeProperty().get().present()).isFalse();

        // Déjà préfixés mais concordants avec le rattachement : rien à signaler non plus.
        vm.definirOriginaux(List.of("Car640380-2026-Pass1-A1-PaRecPR1925492_20260422_203922.wav"));
        assertThat(vm.avertissementPrefixeProperty().get().present()).isFalse();
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

        RetourOperation discordance = vm.avertissementPrefixeProperty().get();
        assertThat(discordance.texte())
                .as("discordance détectée sur l'ensemble du dossier")
                .contains("déjà préfixés pour un autre rattachement")
                .contains("Car640380-2026-Pass1-A1-"); // préfixe attendu rappelé
        assertThat(discordance.texte())
                .as("#1493 : le message dit l'impasse ET comment en sortir")
                .contains("bloqué")
                .contains("Corrigez le rattachement");
        assertThat(discordance.severite())
                .as("#1493 : ce n'est plus un avertissement qu'on écarte, c'est un refus")
                .isEqualTo(Severite.ERREUR);
        assertThat(discordance.texte()).doesNotContain("⚠");

        // Corriger le n° de passage vers celui des fichiers ne suffit pas (carré/point/année diffèrent).
        // En revanche, vider le dossier efface l'avertissement.
        vm.definirOriginaux(List.of());
        assertThat(vm.avertissementPrefixeProperty().get().present()).isFalse();
    }

    @Test
    @DisplayName("#111 : sans site/point choisi, aucun avertissement de préfixe (rattachement incomplet)")
    void prefixe_pas_d_avertissement_sans_rattachement() {
        vm.definirOriginaux(List.of("Car999999-2025-Pass3-B2-PaRec_x.wav"));

        assertThat(vm.avertissementPrefixeProperty().get().present()).isFalse();
    }

    /// VM branché sur un port de campagne : `proposerPour` répond ENS sur A1, rien sur B2.
    private RattachementImportViewModel avecCampagnes() {
        PropositionCampagne port = new PropositionCampagne() {
            @Override
            public List<Campagne> campagnes() {
                return List.of(ENS, THESE);
            }

            @Override
            public Optional<Campagne> proposerPour(Long idPoint) {
                return A1.id().equals(idPoint) ? Optional.of(ENS) : Optional.empty();
            }

            @Override
            public void rattacher(long idPassage, Long idCampagne) {
                // sans objet : ce test n'importe rien
            }
        };
        return new RattachementImportViewModel(serviceSites, new HorlogeFigee(JOUR), ID_USER, Optional.of(port));
    }

    @Test
    @DisplayName("#2631 : choisir un point propose la campagne de son dernier passage")
    void propose_la_campagne_du_point() {
        when(serviceSites.listerSites(ID_USER)).thenReturn(List.of(ETANG));
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1, B2));
        RattachementImportViewModel avecCampagnes = avecCampagnes();
        avecCampagnes.chargerSites();
        avecCampagnes.chargerCampagnes();

        avecCampagnes.siteSelectionneProperty().set(ETANG);
        avecCampagnes.pointSelectionneProperty().set(A1);

        assertThat(avecCampagnes.campagnesProposees()).containsExactly(ENS, THESE);
        assertThat(avecCampagnes.campagneSelectionneeProperty().get()).isEqualTo(ENS);
        assertThat(avecCampagnes.idCampagneRetenue()).isEqualTo(7L);
    }

    @Test
    @DisplayName("#2631 : un point sans campagne n'en propose aucune, et efface celle du point précédent")
    void point_sans_campagne_ne_propose_rien() {
        when(serviceSites.listerSites(ID_USER)).thenReturn(List.of(ETANG));
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1, B2));
        RattachementImportViewModel avecCampagnes = avecCampagnes();
        avecCampagnes.chargerSites();
        avecCampagnes.siteSelectionneProperty().set(ETANG);
        avecCampagnes.pointSelectionneProperty().set(A1);

        avecCampagnes.pointSelectionneProperty().set(B2);

        assertThat(avecCampagnes.campagneSelectionneeProperty().get())
                .as("la campagne de A1 n'a aucune raison de s'appliquer à B2")
                .isNull();
        assertThat(avecCampagnes.idCampagneRetenue()).isNull();
    }

    @Test
    @DisplayName("#2631 : un choix fait à la main survit à un changement de point")
    void choix_explicite_non_ecrase() {
        when(serviceSites.listerSites(ID_USER)).thenReturn(List.of(ETANG));
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1, B2));
        RattachementImportViewModel avecCampagnes = avecCampagnes();
        avecCampagnes.chargerSites();
        avecCampagnes.chargerCampagnes();
        avecCampagnes.siteSelectionneProperty().set(ETANG);
        avecCampagnes.pointSelectionneProperty().set(B2); // rien de proposé ici

        // L'utilisateur tranche lui-même, puis change de point : A1 propose ENS.
        avecCampagnes.campagneSelectionneeProperty().set(THESE);
        avecCampagnes.marquerCampagneChoisie();
        avecCampagnes.pointSelectionneProperty().set(A1);

        assertThat(avecCampagnes.campagneSelectionneeProperty().get())
                .as("deviner est un service, écraser une décision est une faute")
                .isEqualTo(THESE);
    }

    @Test
    @DisplayName("#2631 : fonctionnalité coupée, ni liste ni proposition ni campagne retenue")
    void campagne_coupee() {
        when(serviceSites.listerSites(ID_USER)).thenReturn(List.of(ETANG));
        when(serviceSites.listerPoints(1L)).thenReturn(List.of(A1));
        vm.chargerSites();
        vm.chargerCampagnes();

        vm.siteSelectionneProperty().set(ETANG);
        vm.pointSelectionneProperty().set(A1);

        assertThat(vm.campagneActivee()).isFalse();
        assertThat(vm.campagnesProposees()).isEmpty();
        assertThat(vm.idCampagneRetenue()).isNull();
    }
}
