package fr.univ_amu.iut.validation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.VueValidation;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [ValidationViewModel] (liste, sélection, détail, compteurs). Le
/// [ServiceValidation] est mocké : aucune base de données.
@ExtendWith(MockitoExtension.class)
class ValidationViewModelTest {

  private static final long ID_PASSAGE = 42L;
  private static final long ID_RESULTATS = 7L;

  private static final Taxon PIPISTRELLE =
      new Taxon("PIPPIP", "Pipistrellus pipistrellus", "Pipistrelle commune", 1L);
  private static final Taxon NOCTULE =
      new Taxon("NYCNOC", "Nyctalus noctula", "Noctule commune", 1L);

  @Mock private ServiceValidation service;
  private ValidationViewModel viewModel;

  @BeforeEach
  void preparer() {
    viewModel = new ValidationViewModel(service);
    // ouvrirSur charge toujours les taxons ; stub permissif pour ne pas dépendre du test.
    lenient().when(service.taxonsDisponibles()).thenReturn(List.of(PIPISTRELLE, NOCTULE));
  }

  private static Observation observation(Long id, String taxonObservateur, Double probObservateur) {
    return new Observation(
        id,
        100L + id,
        1.0,
        2.0,
        45000,
        "PIPPIP",
        0.92,
        null,
        taxonObservateur,
        probObservateur,
        null,
        false,
        ModeValidation.MANUEL,
        ID_RESULTATS);
  }

  private static VueValidation vueTrois() {
    // une non touchée, une validée (taxon obs = Tadarida), une corrigée (taxon obs différent)
    return new VueValidation(
        ID_RESULTATS,
        List.of(
            new ObservationStatut(observation(1L, null, null), StatutObservation.NON_TOUCHEE),
            new ObservationStatut(observation(2L, "PIPPIP", 0.9), StatutObservation.VALIDEE),
            new ObservationStatut(observation(3L, "NYCNOC", 0.8), StatutObservation.CORRIGEE)));
  }

  @Test
  @DisplayName("ouvrirSur : charge les observations et calcule les compteurs de revue")
  void ouvrir_charge_et_compte() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());

    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.observations()).hasSize(3);
    assertThat(viewModel.idResultats()).isEqualTo(ID_RESULTATS);
    assertThat(viewModel.nombreTotalProperty().get()).isEqualTo(3);
    assertThat(viewModel.nombreValideesProperty().get()).isEqualTo(1);
    assertThat(viewModel.nombreCorrigeesProperty().get()).isEqualTo(1);
    assertThat(viewModel.progressionProperty().get()).isEqualTo("2 / 3 revues");
    assertThat(viewModel.messageProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("selectionProperty : la sélection alimente le détail, la désélection le vide")
  void selection_alimente_detail() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.detailProperty().get()).isEmpty();

    viewModel.selectionProperty().set(viewModel.observations().get(1));
    assertThat(viewModel.detailProperty().get())
        .contains("Tadarida : PIPPIP")
        .contains("Observateur : PIPPIP")
        .contains("Statut : Validée");

    viewModel.selectionProperty().set(null);
    assertThat(viewModel.detailProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("détail : un taxon observateur absent est affiché « non renseigné »")
  void detail_non_renseigne() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);

    viewModel.selectionProperty().set(viewModel.observations().get(0));
    assertThat(viewModel.detailProperty().get())
        .contains("Observateur : non renseigné")
        .contains("Statut : À revoir");
  }

  @Test
  @DisplayName("ouvrirSur : passage sans CSV importé donne une vue vide et un message d'état")
  void ouvrir_sans_resultats() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(new VueValidation(null, List.of()));

    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.observations()).isEmpty();
    assertThat(viewModel.idResultats()).isNull();
    assertThat(viewModel.progressionProperty().get()).isEmpty();
    assertThat(viewModel.messageProperty().get())
        .isEqualTo("Aucun résultat Tadarida importé pour ce passage.");
  }

  @Test
  @DisplayName("ouvrirSur : un CSV importé sans détection est distingué de l'absence d'import")
  void ouvrir_csv_sans_detection() {
    when(service.chargerValidation(ID_PASSAGE))
        .thenReturn(new VueValidation(ID_RESULTATS, List.of()));

    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.observations()).isEmpty();
    assertThat(viewModel.idResultats()).isEqualTo(ID_RESULTATS);
    assertThat(viewModel.messageProperty().get())
        .isEqualTo("Résultats Tadarida importés, mais aucune détection à valider.");
  }

  @Test
  @DisplayName("ouvrirSur : une erreur de chargement vide l'écran et expose le message")
  void ouvrir_en_erreur() {
    // d'abord un chargement valide, pour vérifier que le second ouvrirSur réinitialise tout
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.selectionProperty().set(viewModel.observations().get(0));

    when(service.chargerValidation(ID_PASSAGE))
        .thenThrow(new RegleMetierException("Passage introuvable : 42"));
    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.observations()).isEmpty();
    assertThat(viewModel.idResultats()).isNull();
    assertThat(viewModel.detailProperty().get()).isEmpty();
    assertThat(viewModel.messageProperty().get()).isEqualTo("Passage introuvable : 42");
  }

  @Test
  @DisplayName("ouvrirSur : charge la liste des taxons pour le sélecteur de correction")
  void ouvrir_charge_les_taxons() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());

    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.taxons()).containsExactly(PIPISTRELLE, NOCTULE);
  }

  @Test
  @DisplayName("selectionPresente suit la présence d'une sélection")
  void selection_presente_suit_la_selection() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.selectionPresenteProperty().get()).isFalse();
    viewModel.selectionProperty().set(viewModel.observations().get(0));
    assertThat(viewModel.selectionPresenteProperty().get()).isTrue();
    viewModel.selectionProperty().set(null);
    assertThat(viewModel.selectionPresenteProperty().get()).isFalse();
  }

  @Test
  @DisplayName("valider : applique la validation sur la sélection puis recharge les compteurs")
  void valider_applique_et_recharge() {
    VueValidation apres =
        new VueValidation(
            ID_RESULTATS,
            List.of(
                new ObservationStatut(observation(1L, "PIPPIP", 0.92), StatutObservation.VALIDEE),
                new ObservationStatut(observation(2L, "PIPPIP", 0.9), StatutObservation.VALIDEE),
                new ObservationStatut(observation(3L, "NYCNOC", 0.8), StatutObservation.CORRIGEE)));
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois(), apres);
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.selectionProperty().set(viewModel.observations().get(0)); // obs 1, non touchée

    boolean ok = viewModel.valider();

    assertThat(ok).isTrue();
    verify(service).valider(1L);
    assertThat(viewModel.nombreValideesProperty().get()).isEqualTo(2);
    assertThat(viewModel.messageProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("corriger : applique le taxon observateur choisi sur la sélection")
  void corriger_applique_le_taxon() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.selectionProperty().set(viewModel.observations().get(0)); // obs 1

    boolean ok = viewModel.corriger(NOCTULE);

    assertThat(ok).isTrue();
    verify(service).corriger(1L, "NYCNOC", null);
  }

  @Test
  @DisplayName("valider sans sélection est ignoré (aucun appel service)")
  void valider_sans_selection_est_ignore() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.valider()).isFalse();
    verify(service, never()).valider(anyLong());
  }

  @Test
  @DisplayName("corriger sans taxon est ignoré (aucun appel service)")
  void corriger_sans_taxon_est_ignore() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.selectionProperty().set(viewModel.observations().get(0));

    assertThat(viewModel.corriger(null)).isFalse();
    verify(service, never()).corriger(anyLong(), any(), any());
  }

  @Test
  @DisplayName("corriger vers le taxon Tadarida lui-même est refusé (ce serait une validation)")
  void corriger_vers_taxon_tadarida_est_refuse() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.selectionProperty().set(viewModel.observations().get(0)); // taxonTadarida = PIPPIP

    assertThat(viewModel.corriger(PIPISTRELLE)).isFalse(); // PIPISTRELLE.code() == "PIPPIP"
    verify(service, never()).corriger(anyLong(), any(), any());
    assertThat(viewModel.messageProperty().get()).contains("Valider");
  }

  @Test
  @DisplayName("valider : une erreur métier est restituée dans le message, la vue est préservée")
  void valider_en_erreur_restitue_le_message() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    when(service.valider(1L)).thenThrow(new RegleMetierException("Observation introuvable : 1"));
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.selectionProperty().set(viewModel.observations().get(0));

    boolean ok = viewModel.valider();

    assertThat(ok).isFalse();
    assertThat(viewModel.messageProperty().get()).isEqualTo("Observation introuvable : 1");
    assertThat(viewModel.observations()).hasSize(3); // la vue n'est pas vidée par l'échec
  }

  @Test
  @DisplayName("resultatsDisponibles suit la présence d'un jeu de résultats chargé")
  void resultats_disponibles_suit_le_chargement() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);
    assertThat(viewModel.resultatsDisponiblesProperty().get()).isTrue();

    when(service.chargerValidation(ID_PASSAGE)).thenReturn(new VueValidation(null, List.of()));
    viewModel.ouvrirSur(ID_PASSAGE);
    assertThat(viewModel.resultatsDisponiblesProperty().get()).isFalse();
  }

  @Test
  @DisplayName("exporter : écrit le _Vu via le service et restitue le chemin dans le message")
  void exporter_ecrit_le_fichier() {
    Path dest = Path.of("resultats_Vu.csv");
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    when(service.exporter(ID_RESULTATS, dest, true)).thenReturn(dest);
    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.exporter(dest)).isTrue();
    verify(service).exporter(ID_RESULTATS, dest, true);
    assertThat(viewModel.messageProperty().get()).contains("exporté").contains("resultats_Vu.csv");
  }

  @Test
  @DisplayName("exporter : la case « inclure le mode » est répercutée sur le service (R24)")
  void exporter_respecte_inclure_mode() {
    Path dest = Path.of("x_Vu.csv");
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    when(service.exporter(ID_RESULTATS, dest, false)).thenReturn(dest);
    viewModel.ouvrirSur(ID_PASSAGE);
    viewModel.inclureModeProperty().set(false);

    assertThat(viewModel.exporter(dest)).isTrue();
    verify(service).exporter(ID_RESULTATS, dest, false);
  }

  @Test
  @DisplayName("exporter sans résultats importés est ignoré (aucun appel service)")
  void exporter_sans_resultats_est_ignore() {
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(new VueValidation(null, List.of()));
    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.exporter(Path.of("x.csv"))).isFalse();
    verify(service, never()).exporter(anyLong(), any(), anyBoolean());
  }

  @Test
  @DisplayName("exporter : une erreur d'écriture est restituée dans le message")
  void exporter_en_erreur_restitue_le_message() {
    Path dest = Path.of("x_Vu.csv");
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(vueTrois());
    when(service.exporter(ID_RESULTATS, dest, true))
        .thenThrow(new RuntimeException("Disque plein"));
    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.exporter(dest)).isFalse();
    assertThat(viewModel.messageProperty().get()).isEqualTo("Disque plein");
  }

  @Test
  @DisplayName("importer : importe le CSV pour le passage puis recharge la vue avec les résultats")
  void importer_charge_les_resultats() {
    Path csv = Path.of("nuit-observations.csv");
    when(service.chargerValidation(ID_PASSAGE))
        .thenReturn(new VueValidation(null, List.of()), vueTrois());
    viewModel.ouvrirSur(ID_PASSAGE);
    assertThat(viewModel.observations()).isEmpty();

    assertThat(viewModel.importer(csv)).isTrue();
    verify(service).importer(ID_PASSAGE, csv);
    assertThat(viewModel.observations()).hasSize(3);
  }

  @Test
  @DisplayName("importer : une erreur d'import (session/séquence/taxon) passe dans le message")
  void importer_en_erreur_restitue_le_message() {
    Path csv = Path.of("nuit-observations.csv");
    when(service.chargerValidation(ID_PASSAGE)).thenReturn(new VueValidation(null, List.of()));
    when(service.importer(ID_PASSAGE, csv))
        .thenThrow(new RegleMetierException("Aucune session d'enregistrement"));
    viewModel.ouvrirSur(ID_PASSAGE);

    assertThat(viewModel.importer(csv)).isFalse();
    assertThat(viewModel.messageProperty().get()).isEqualTo("Aucune session d'enregistrement");
  }
}
