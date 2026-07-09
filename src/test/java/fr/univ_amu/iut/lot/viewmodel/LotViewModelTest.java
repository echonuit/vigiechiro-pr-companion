package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.ControleCoherence;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutControle;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [LotViewModel] (état, transitions préparer/déposer). Le [ServiceLot] est
/// mocké : aucune base de données.
@ExtendWith(MockitoExtension.class)
class LotViewModelTest {

    private static final long ID_PASSAGE = 42L;

    @Mock
    private ServiceLot service;

    private LotViewModel viewModel;

    @BeforeEach
    void preparer() {
        viewModel = new LotViewModel(service);
    }

    @Test
    @DisplayName("#… : en Déposé, peutSupprimerArchives suit la présence d'archives sur disque")
    void peut_supprimer_en_depose_selon_volume() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.DEPOSE, List.of(), "2026-06-22"));
        when(service.archivesDepot("/ws/session-42"))
                .thenReturn(List.of(new ArchiveDepot(Path.of("/ws/session-42/depot/x-1.zip"), 1, 2048L, 2)), List.of());

        viewModel.ouvrirSur(ID_PASSAGE);
        assertThat(viewModel.peutSupprimerArchivesProperty().get()).isTrue();

        viewModel.ouvrirSur(ID_PASSAGE); // plus d'archives sur disque
        assertThat(viewModel.peutSupprimerArchivesProperty().get()).isFalse();
    }

    @Test
    @DisplayName("#… : avant le dépôt (Prêt à déposer), la suppression des archives reste désactivée")
    void pas_de_suppression_avant_depot() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.peutSupprimerArchivesProperty().get()).isFalse();
    }

    @Test
    @DisplayName("#… : supprimerArchives appelle le service, recharge l'état et annonce l'espace libéré")
    void supprimer_archives_appelle_le_service_et_recharge() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.DEPOSE, List.of(), "2026-06-22"));
        when(service.archivesDepot("/ws/session-42"))
                .thenReturn(List.of(new ArchiveDepot(Path.of("/ws/session-42/depot/x-1.zip"), 1, 2048L, 2)), List.of());
        when(service.supprimerArchivesDepot(ID_PASSAGE)).thenReturn(2048L);
        viewModel.ouvrirSur(ID_PASSAGE);
        assertThat(viewModel.peutSupprimerArchivesProperty().get()).isTrue();

        assertThat(viewModel.supprimerArchives()).isTrue();

        verify(service).supprimerArchivesDepot(ID_PASSAGE);
        assertThat(viewModel.messageProperty().get()).contains("libérés");
        // Après recharge (plus d'archives), le bouton se désactive de lui-même.
        assertThat(viewModel.peutSupprimerArchivesProperty().get()).isFalse();
    }

    private static EtatLot etat(StatutWorkflow statut, List<ControleCoherence> controles, String deposeLe) {
        return new EtatLot(statut, "/ws/session-42", 2, 8192L, controles, deposeLe);
    }

    /// Contrôle de cohérence en échec (bloquant), pour les cas « incohérent ».
    private static ControleCoherence echec(String detail) {
        return new ControleCoherence("Contrôle", StatutControle.ECHEC, detail);
    }

    @Test
    @DisplayName("ouvrirSur (Vérifié, conforme) : récap + dossier, préparation possible")
    void ouvrir_verifie_permet_preparer() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.VERIFIE, List.of(), null));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.statutProperty().get()).isEqualTo("Vérifié");
        assertThat(viewModel.cheminDossierProperty().get()).isEqualTo("/ws/session-42");
        assertThat(viewModel.recapProperty().get()).isEqualTo("2 séquences · 8 Ko");
        assertThat(viewModel.peutPreparerProperty().get()).isTrue();
        assertThat(viewModel.peutDeposerProperty().get()).isFalse();
        assertThat(viewModel.deposeProperty().get()).isFalse();
        assertThat(viewModel.controles()).isEmpty();
        assertThat(viewModel.messageProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("ouvrirSur avec un contrôle en échec : préparation impossible, checklist exposée")
    void ouvrir_avec_controle_echec_bloque_preparer() {
        when(service.consulterLot(ID_PASSAGE))
                .thenReturn(etat(StatutWorkflow.VERIFIE, List.of(echec("Transformation incomplète")), null));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.peutPreparerProperty().get()).isFalse();
        assertThat(viewModel.controles()).hasSize(1);
        assertThat(viewModel.controles().get(0).estBloquant()).isTrue();
        assertThat(viewModel.controles().get(0).detail()).isEqualTo("Transformation incomplète");
        assertThat(viewModel.messageProperty().get()).contains("corrigez");
    }

    @Test
    @DisplayName("ouvrirSur (Prêt à déposer) : dépôt possible, préparation non")
    void ouvrir_pret_a_deposer_permet_deposer() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.peutDeposerProperty().get()).isTrue();
        assertThat(viewModel.peutPreparerProperty().get()).isFalse();
        assertThat(viewModel.deposeProperty().get()).isFalse();
    }

    @Test
    @DisplayName("#251 : une fois préparé, un message confirme ce que l'étape ① a accompli")
    void pret_a_deposer_message_confirme_la_preparation() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.messageProperty().get())
                .contains("Lot préparé")
                .contains("2 séquence")
                .contains("verrouillée");
    }

    @Test
    @DisplayName("ouvrirSur (Déposé) : affiche la date de dépôt")
    void ouvrir_depose_affiche_la_date() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.DEPOSE, List.of(), "2026-06-23T08:00"));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.deposeProperty().get()).isTrue();
        assertThat(viewModel.peutPreparerProperty().get()).isFalse();
        assertThat(viewModel.peutDeposerProperty().get()).isFalse();
        assertThat(viewModel.messageProperty().get()).isEqualTo("Passage déposé le 2026-06-23T08:00.");
    }

    @Test
    @DisplayName("preparer : applique la préparation puis recharge (Prêt à déposer)")
    void preparer_applique_et_recharge() {
        when(service.consulterLot(ID_PASSAGE))
                .thenReturn(
                        etat(StatutWorkflow.VERIFIE, List.of(), null),
                        etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));
        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.preparer()).isTrue();
        verify(service).preparerLot(ID_PASSAGE);
        assertThat(viewModel.peutDeposerProperty().get()).isTrue();
        assertThat(viewModel.peutPreparerProperty().get()).isFalse();
    }

    @Test
    @DisplayName("deposer : marque déposé puis recharge (Déposé)")
    void deposer_applique_et_recharge() {
        when(service.consulterLot(ID_PASSAGE))
                .thenReturn(
                        etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null),
                        etat(StatutWorkflow.DEPOSE, List.of(), "2026-06-23T08:00"));
        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.deposer()).isTrue();
        verify(service).marquerDepose(ID_PASSAGE);
        assertThat(viewModel.deposeProperty().get()).isTrue();
    }

    @Test
    @DisplayName("preparer : une erreur métier est restituée, la vue est préservée")
    void preparer_en_erreur_restitue_le_message() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.VERIFIE, List.of(), null));
        when(service.preparerLot(ID_PASSAGE))
                .thenThrow(new RegleMetierException("Préparation du lot impossible : incohérent"));
        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.preparer()).isFalse();
        assertThat(viewModel.messageProperty().get()).contains("impossible");
        assertThat(viewModel.statutProperty().get()).isEqualTo("Vérifié"); // état non vidé
    }

    @Test
    @DisplayName("#110 : sur un lot prêt, genererArchives publie les archives produites et un message")
    void generer_archives_publie_la_liste() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));
        when(service.genererArchivesDepot(eq(ID_PASSAGE), any()))
                .thenReturn(List.of(
                        new ArchiveDepot(Path.of("/ws/session-42/depot/Car040962-2026-Pass1-A1-1.zip"), 1, 2048L, 2)));
        viewModel.ouvrirSur(ID_PASSAGE);
        assertThat(viewModel.peutGenererArchivesProperty().get()).isTrue();

        assertThat(viewModel.genererArchives()).isTrue();

        assertThat(viewModel.archives()).hasSize(1);
        assertThat(viewModel.archives().get(0))
                .contains("Car040962-2026-Pass1-A1-1.zip")
                .contains("2 fichiers");
        assertThat(viewModel.messageProperty().get()).contains("1 archive");
        assertThat(viewModel.generationEnCoursProperty().get()).isFalse(); // état « en cours » levé en fin
    }

    @Test
    @DisplayName("#251 : génération hors-thread — marquer en cours puis appliquer/échec gère l'état")
    void generation_hors_thread_gere_l_etat_en_cours() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));
        var archive = new ArchiveDepot(Path.of("/ws/session-42/depot/Car040962-2026-Pass1-A1-1.zip"), 1, 2048L, 2);
        when(service.genererArchivesDepot(eq(ID_PASSAGE), any())).thenReturn(List.of(archive));
        viewModel.ouvrirSur(ID_PASSAGE);

        // Étape posée sur le fil JavaFX avant le calcul hors-thread.
        viewModel.marquerGenerationEnCours();
        assertThat(viewModel.generationEnCoursProperty().get()).isTrue();
        assertThat(viewModel.messageProperty().get()).contains("en cours");

        // Calcul hors-thread (aucune mutation observable), puis application sur le fil JavaFX.
        var produites = viewModel.calculerArchivesDepot(progression -> {});
        viewModel.appliquerGeneration(produites);

        assertThat(viewModel.generationEnCoursProperty().get()).isFalse();
        assertThat(viewModel.archives()).hasSize(1);
        assertThat(courante(viewModel)).isEqualTo("3 · Téléverser");
    }

    @Test
    @DisplayName("#251 : un échec de génération hors-thread restitue le message et lève l'état en cours")
    void echec_generation_leve_l_etat_en_cours() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));
        viewModel.ouvrirSur(ID_PASSAGE);
        viewModel.marquerGenerationEnCours();

        viewModel.echecGeneration("Disque plein.");

        assertThat(viewModel.generationEnCoursProperty().get()).isFalse();
        assertThat(viewModel.messageProperty().get()).isEqualTo("Disque plein.");
    }

    @Test
    @DisplayName("#110 : genererArchives restitue l'erreur métier dans le message, liste vide")
    void generer_archives_en_erreur() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));
        when(service.genererArchivesDepot(eq(ID_PASSAGE), any()))
                .thenThrow(new RegleMetierException("Aucune séquence à déposer."));
        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.genererArchives()).isFalse();

        assertThat(viewModel.archives()).isEmpty();
        assertThat(viewModel.messageProperty().get()).contains("Aucune séquence");
    }

    @Test
    @DisplayName("#110 : le titre de la section archives reflète le plafond configuré (réglage applicatif)")
    void titre_archives_reflete_le_plafond_configure() {
        when(service.plafondArchiveOctets()).thenReturn(500_000_000L);

        LotViewModel vm = new LotViewModel(service);

        assertThat(vm.titreArchivesProperty().get()).contains("500 Mo");
    }

    @Test
    @DisplayName("#251 : le chemin de dépôt pointe le sous-dossier depot/ (pas la session entière)")
    void chemin_depot_pointe_le_sous_dossier() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.VERIFIE, List.of(), null));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.cheminDepotProperty().get()).isEqualTo("/ws/session-42/depot");
    }

    @Test
    @DisplayName("#251 : le stepper expose 4 étapes ordonnées, l'étape courante suit le statut")
    void stepper_quatre_etapes_courante_selon_statut() {
        // Vérifié : étape courante = ① Préparer.
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.VERIFIE, List.of(), null));
        viewModel.ouvrirSur(ID_PASSAGE);
        assertThat(viewModel.etapes())
                .extracting(EtapeDepot::libelle)
                .containsExactly("1 · Préparer", "2 · Générer les archives", "3 · Téléverser", "4 · Marquer déposé");
        assertThat(courante(viewModel)).isEqualTo("1 · Préparer");

        // Prêt à déposer, aucune archive générée : étape courante = ② Générer les archives.
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));
        viewModel.ouvrirSur(ID_PASSAGE);
        assertThat(courante(viewModel)).isEqualTo("2 · Générer les archives");
    }

    @Test
    @DisplayName("#251 : générer les archives fait avancer l'étape courante à ③ Téléverser")
    void generer_archives_avance_l_etape_a_televerser() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.PRET_A_DEPOSER, List.of(), null));
        when(service.genererArchivesDepot(eq(ID_PASSAGE), any()))
                .thenReturn(List.of(
                        new ArchiveDepot(Path.of("/ws/session-42/depot/Car040962-2026-Pass1-A1-1.zip"), 1, 2048L, 2)));
        viewModel.ouvrirSur(ID_PASSAGE);
        assertThat(courante(viewModel)).isEqualTo("2 · Générer les archives");

        viewModel.genererArchives();

        assertThat(courante(viewModel)).isEqualTo("3 · Téléverser");
    }

    @Test
    @DisplayName("#251 : passage déposé → toutes les étapes franchies, aucune courante")
    void depose_toutes_etapes_franchies() {
        when(service.consulterLot(ID_PASSAGE)).thenReturn(etat(StatutWorkflow.DEPOSE, List.of(), "2026-06-23T08:00"));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.etapes()).allMatch(e -> e.etat() == EtatEtape.FRANCHIE);
        assertThat(courante(viewModel)).isNull();
    }

    /// Libellé de l'unique étape « courante » du stepper, ou `null` si aucune (tout franchi).
    private static String courante(LotViewModel vm) {
        return vm.etapes().stream()
                .filter(e -> e.etat() == EtatEtape.COURANTE)
                .map(EtapeDepot::libelle)
                .findFirst()
                .orElse(null);
    }
}
