package fr.univ_amu.iut.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.DiscussionValidateur;
import fr.univ_amu.iut.audio.viewmodel.RetourOperation;
import fr.univ_amu.iut.bibliotheque.model.ExportBiblioSons;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VM de la vue audio unifiée isolé du modèle (Mockito) : on vérifie, **une source à la fois**, la
/// résolution de la [SourceObservations] vers le bon appel de [ServiceValidation], la conduite de la
/// sélection / du chemin audio, les actions communes (valider / corriger / basculer référence) et les
/// actions propres conditionnées par la source (import CSV / export `_Vu` pour `ParPassage`).
@ExtendWith(MockitoExtension.class)
class AudioViewModelTest {

    @Mock
    ServiceValidation service;

    @Mock
    ProjectionsAudioDao projections;

    @Mock
    PlageNuitPassage plageNuitPassage;

    @Mock
    ValidationManuelle validationManuelle;

    @Mock
    MarquageDouteux marquageDouteux;

    @Mock
    SaisieCertitude saisieCertitude;

    @Mock
    RevueEnLot revueEnLot;

    @Mock
    ServiceBibliotheque bibliotheque;

    @Mock
    ServiceDisponibiliteAudio disponibilite;

    private static final ContextePassage PASSAGE_7 =
            new ContextePassage(7L, 1, new ContexteSite("640380", "A1", "Mon site"));

    private AudioViewModel vm() {
        return vm(p -> true);
    }

    /// Variante à présence de fichier contrôlée (#1301) : `p -> false` simule un audio disparu.
    private AudioViewModel vm(java.util.function.Predicate<Path> fichierPresent) {
        return new AudioViewModel(
                service,
                projections,
                plageNuitPassage,
                validationManuelle,
                marquageDouteux,
                saisieCertitude,
                revueEnLot,
                bibliotheque,
                disponibilite,
                fichierPresent,
                mock(DiscussionValidateur.class));
    }

    private static LigneObservationAudio ligne(
            long idObs, long idSeq, String taxonTadarida, String observateur, StatutObservation statut, boolean ref) {
        return new LigneObservationAudio(
                idObs,
                idSeq,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Mon site",
                taxonTadarida,
                0.9,
                observateur,
                null,
                statut,
                ref,
                null,
                45,
                null,
                null,
                null,
                "Chiroptères",
                "PaRec_" + idSeq + "_000.wav",
                0.20,
                0.32,
                null,
                false,
                null,
                null,
                null,
                null,
                0);
    }

    @Nested
    @DisplayName("Source ParPassage (workflow Tadarida)")
    class ParPassage {

        private SourceObservations source() {
            return new SourceObservations.ParPassage(PASSAGE_7);
        }

        @Test
        @DisplayName(
                "#1214 : chargerOuverture lit sans muter ; appliquerOuverture publie ; signalerErreur route l'erreur")
        void charger_appliquer_signaler_separes() {
            when(service.taxonsDisponibles()).thenReturn(List.of(new Taxon("Pippip", "Pipistrellus", null, 1L)));
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.of(100L));
            when(projections.lignesAudioDuPassage(7L))
                    .thenReturn(List.of(ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false)));
            AudioViewModel vm = vm();

            var donnees = vm.chargerOuverture(source());
            assertThat(donnees.lignes()).hasSize(1);
            assertThat(vm.observationsFiltrees())
                    .as("chargerOuverture ne mute pas l'état observable")
                    .isEmpty();

            vm.appliquerOuverture(source(), donnees);
            assertThat(vm.observationsFiltrees()).hasSize(1);
            assertThat(vm.taxons()).hasSize(1);
            assertThat(vm.resultatsDisponiblesProperty().get()).isTrue();

            vm.signalerErreur(source(), new IllegalStateException("base indisponible"));
            assertThat(vm.retourProperty().get().severite()).isEqualTo(RetourOperation.Severite.ERREUR);
            assertThat(vm.retourProperty().get().texte()).contains("base indisponible");
        }

        @Test
        @DisplayName("ouvrirSur charge les lignes du passage, les taxons, et active le workflow Tadarida")
        void ouvrir_charge_passage() {
            when(service.taxonsDisponibles()).thenReturn(List.of(new Taxon("Pippip", "Pipistrellus", null, 1L)));
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.of(100L));
            when(projections.lignesAudioDuPassage(7L))
                    .thenReturn(List.of(ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false)));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());

            assertThat(vm.observationsFiltrees()).hasSize(1);
            assertThat(vm.taxons()).hasSize(1);
            assertThat(vm.source().permetWorkflowTadarida()).isTrue();
            assertThat(vm.source().permetExportBibliotheque()).isFalse();
            assertThat(vm.resultatsDisponiblesProperty().get()).isTrue();
        }

        @Test
        @DisplayName("sélectionner une ligne calcule le chemin audio et le détail")
        void selection_calcule_audio_et_detail() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
            LigneObservationAudio l = ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false);
            when(projections.lignesAudioDuPassage(7L)).thenReturn(List.of(l));
            when(service.cheminAudio(10L)).thenReturn(Optional.of(Path.of("/ws/transformes/a.wav")));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());
            vm.selectionProperty().set(l);

            assertThat(vm.etatSelection().avecObservationProperty().get()).isTrue();
            assertThat(vm.cheminAudioCourantProperty().get()).isEqualTo(Path.of("/ws/transformes/a.wav"));
            assertThat(vm.detailProperty().get()).contains("Tadarida : Pippip").contains("À revoir");
        }

        @Test
        @DisplayName("#1301 : passage archivé (ABSENTE) : bandeau explicite, ré-observé à l'ouverture")
        void passage_archive_bandeau_explicite() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
            when(projections.lignesAudioDuPassage(7L))
                    .thenReturn(List.of(ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false)));
            when(disponibilite.decompte(7L)).thenReturn(new fr.univ_amu.iut.passage.model.DecompteAudio(0, 4806));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());

            assertThat(vm.bandeauArchiveProperty().get()).contains("archivé").contains("réimportez");
            verify(disponibilite).invalider(7L); // l'écran reflète le disque au moment de l'ouverture
        }

        @Test
        @DisplayName("#1301 : audio partiel (PARTIELLE) : le bandeau porte le décompte présentes/total")
        void audio_partiel_bandeau_decompte() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
            when(projections.lignesAudioDuPassage(7L)).thenReturn(List.of());
            when(disponibilite.decompte(7L)).thenReturn(new fr.univ_amu.iut.passage.model.DecompteAudio(4230, 4806));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());

            assertThat(vm.bandeauArchiveProperty().get()).contains("4230").contains("4806");
        }

        @Test
        @DisplayName("#1301 : audio complet : aucun bandeau")
        void audio_complet_sans_bandeau() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
            when(projections.lignesAudioDuPassage(7L)).thenReturn(List.of());
            when(disponibilite.decompte(7L)).thenReturn(new fr.univ_amu.iut.passage.model.DecompteAudio(4806, 4806));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());

            assertThat(vm.bandeauArchiveProperty().get()).isEmpty();
        }

        @Test
        @DisplayName("#1301 : fichier de la séquence absent : chemin null (jamais un chemin mort) + audioManquant")
        void selection_sans_fichier_audio_manquant() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
            LigneObservationAudio l = ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false);
            when(projections.lignesAudioDuPassage(7L)).thenReturn(List.of(l));
            when(service.cheminAudio(10L)).thenReturn(Optional.of(Path.of("/ws/transformes/a.wav")));

            AudioViewModel vm = vm(p -> false);
            vm.ouvrirSur(source());
            vm.selectionProperty().set(l);

            assertThat(vm.cheminAudioCourantProperty().get()).isNull();
            assertThat(vm.audioManquantProperty().get()).isTrue();

            vm.selectionProperty().set(null);
            assertThat(vm.audioManquantProperty().get())
                    .as("plus de sélection : plus rien à expliquer")
                    .isFalse();
        }

        @Test
        @DisplayName("valider délègue selon le mode et recharge en conservant la sélection")
        void valider_delegue_et_recharge() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.of(100L));
            LigneObservationAudio avant = ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false);
            LigneObservationAudio apres = ligne(1, 10, "Pippip", "Pippip", StatutObservation.VALIDEE, false);
            when(projections.lignesAudioDuPassage(7L)).thenReturn(List.of(avant), List.of(apres));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());
            vm.selectionProperty().set(avant);
            vm.modeRevueProperty().set(ModeRevue.ACTIVITE);

            assertThat(vm.valider()).isTrue();
            verify(service).validerSelonMode(1L, ModeRevue.ACTIVITE);
            assertThat(vm.comptageProperty().get().validees()).isEqualTo(1);
            assertThat(vm.selectionProperty().get().statut()).isEqualTo(StatutObservation.VALIDEE);
        }

        @Test
        @DisplayName("basculerReference pose is_reference sur une ligne non référencée")
        void basculer_reference_pose() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
            LigneObservationAudio l = ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false);
            when(projections.lignesAudioDuPassage(7L)).thenReturn(List.of(l));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());
            vm.selectionProperty().set(l);

            assertThat(vm.basculerReference()).isTrue();
            verify(service).marquerReference(1L, true);
        }

        @Test
        @DisplayName("commenter délègue au service par identifiant (sans dépendre de la sélection) et recharge")
        void commenter_delegue_par_identifiant() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
            LigneObservationAudio l = ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false);
            when(projections.lignesAudioDuPassage(7L)).thenReturn(List.of(l));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());
            // Volontairement AUCUNE sélection : l'édition inline commente la ligne de la cellule, pas la sélection.

            assertThat(vm.actions().commenter(1L, "beau cri")).isTrue();
            verify(service).commenter(1L, "beau cri");
            // Rechargement post-action : la source est relue une 2e fois (1re à l'ouverture).
            verify(projections, times(2)).lignesAudioDuPassage(7L);
        }

        @Test
        @DisplayName("importer délègue, rafraîchit l'identifiant de résultats et rend un retour de succès")
        void importer_delegue() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
            // Passage vide à l'ouverture, puis 1 observation après l'import (table rechargée).
            when(projections.lignesAudioDuPassage(7L))
                    .thenReturn(List.of(), List.of(ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false)));
            // Bilan d'import : 1 importée, rien d'ignoré, aucun taxon hors référentiel.
            when(service.importer(7L, Path.of("obs.csv")))
                    .thenReturn(new BilanImport(
                            new ResultatsIdentification(100L, "obs.csv", "Brut", "2026-06-30T00:00", 7L), 1, 0, 0));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());

            assertThat(vm.importer(Path.of("obs.csv"), false)).isTrue();
            verify(service).importer(7L, Path.of("obs.csv"));
            assertThat(vm.resultatsDisponiblesProperty().get()).isTrue();
            assertThat(vm.retourProperty().get().severite()).isEqualTo(RetourOperation.Severite.SUCCES);
            assertThat(vm.retourProperty().get().texte())
                    .contains("Import réussi")
                    .contains("1 observation");
        }

        @Test
        @DisplayName("un import qui échoue rend un retour d'ERREUR visible (pas un placeholder silencieux)")
        void importer_echoue_retour_erreur() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.empty());
            when(projections.lignesAudioDuPassage(7L)).thenReturn(List.of());
            when(service.importer(7L, Path.of("obs.csv")))
                    .thenThrow(new RegleMetierException("Séquence d'écoute introuvable en base pour « seq »."));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());

            assertThat(vm.importer(Path.of("obs.csv"), false)).isFalse();
            assertThat(vm.retourProperty().get().severite()).isEqualTo(RetourOperation.Severite.ERREUR);
            assertThat(vm.retourProperty().get().texte()).contains("Séquence d'écoute introuvable");
        }

        @Test
        @DisplayName("importer sans remplacer est refusé si un jeu existe déjà (info), sans toucher au service")
        void importer_refuse_si_deja_importe() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.of(100L)); // un jeu existe déjà
            when(projections.lignesAudioDuPassage(7L)).thenReturn(List.of());

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());

            assertThat(vm.importer(Path.of("obs.csv"), false)).isFalse();
            verify(service, never()).importer(any(), any());
            assertThat(vm.retourProperty().get().severite()).isEqualTo(RetourOperation.Severite.INFO);
        }

        @Test
        @DisplayName("réimporter (remplacer) délègue au remplacement atomique du service")
        void reimporter_remplace() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(service.resultatsDuPassage(7L)).thenReturn(Optional.of(100L)); // un jeu existe déjà
            when(projections.lignesAudioDuPassage(7L))
                    .thenReturn(List.of(ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false)));
            // Bilan de réimport : 2 validations observateur réattachées, 1 perdue (observation disparue).
            when(service.reimporter(7L, Path.of("neuf.csv")))
                    .thenReturn(new BilanImport(
                            new ResultatsIdentification(101L, "neuf.csv", "Brut", "2026-06-30T00:00", 7L),
                            1,
                            0,
                            0,
                            2,
                            1));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());

            assertThat(vm.importer(Path.of("neuf.csv"), true)).isTrue();
            // Remplacement atomique : un seul appel reimporter, pas de suppression hors transaction.
            verify(service).reimporter(7L, Path.of("neuf.csv"));
            verify(service, never()).importer(any(), any());
            assertThat(vm.retourProperty().get().severite()).isEqualTo(RetourOperation.Severite.SUCCES);
            assertThat(vm.retourProperty().get().texte())
                    .contains("2 validation(s) conservée(s)")
                    .contains("1 validation(s) perdue(s)");
        }
    }

    @Nested
    @DisplayName("Source ParPassages (sélection multisite)")
    class ParPassages {

        private SourceObservations source() {
            return new SourceObservations.ParPassages(List.of(7L, 8L), "sélection (2 passages)");
        }

        @Test
        @DisplayName("ouvrirSur charge la sélection et masque le workflow Tadarida ; l'import est ignoré")
        void ouvrir_charge_lot() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(projections.lignesAudioDesPassages(List.of(7L, 8L)))
                    .thenReturn(List.of(ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false)));

            AudioViewModel vm = vm();
            vm.ouvrirSur(source());

            assertThat(vm.observationsFiltrees()).hasSize(1);
            assertThat(vm.source().permetWorkflowTadarida()).isFalse();
            assertThat(vm.source().permetExportBibliotheque()).isFalse();
            assertThat(vm.importer(Path.of("obs.csv"), false)).isFalse();
            verify(service, never()).importer(any(), any());
        }
    }

    @Nested
    @DisplayName("Source ParEspece (analyse)")
    class ParEspece {

        @Test
        @DisplayName("ouvrirSur convertit le filtre de statut texte et délègue à lignesAudioDeLEspece")
        void ouvrir_filtre_statut() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(projections.lignesAudioDeLEspece("u-1", "Pippip", StatutObservation.NON_TOUCHEE))
                    .thenReturn(List.of(ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false)));

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.ParEspece("u-1", "Pippip", "NON_TOUCHEE", "Pipistrelle commune"));

            assertThat(vm.observationsFiltrees()).hasSize(1);
            assertThat(vm.source().permetWorkflowTadarida()).isFalse();
            verify(projections).lignesAudioDeLEspece("u-1", "Pippip", StatutObservation.NON_TOUCHEE);
        }

        @Test
        @DisplayName("statut null = toutes les observations de l'espèce")
        void ouvrir_sans_filtre() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(projections.lignesAudioDeLEspece("u-1", "Pippip", null)).thenReturn(List.of());

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.ParEspece("u-1", "Pippip", null, "Pipistrelle commune"));

            verify(projections).lignesAudioDeLEspece("u-1", "Pippip", null);
            assertThat(vm.messageProperty().get()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Source References (corpus de référence)")
    class References {

        @Test
        @DisplayName("ouvrirSur charge les références et active l'export bibliothèque")
        void ouvrir_charge_references() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(projections.lignesAudioReferences("u-1"))
                    .thenReturn(List.of(ligne(1, 10, "Pippip", "Pippip", StatutObservation.VALIDEE, true)));

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.References("u-1"));

            assertThat(vm.observationsFiltrees()).hasSize(1);
            assertThat(vm.source().permetExportBibliotheque()).isTrue();
            assertThat(vm.source().permetWorkflowTadarida()).isFalse();
        }

        @Test
        @DisplayName("exporterBibliotheque délègue au service bibliothèque et rapporte le bilan")
        void exporter_bibliotheque() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(projections.lignesAudioReferences("u-1")).thenReturn(List.of());
            ExportBiblioSons export = org.mockito.Mockito.mock(ExportBiblioSons.class);
            when(bibliotheque.exporterBibliotheque()).thenReturn(export);
            when(export.exporterVers(Path.of("/sortie"))).thenReturn(3);

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.References("u-1"));

            assertThat(vm.exporterBibliotheque(Path.of("/sortie"))).isTrue();
            verify(bibliotheque).exporterBibliotheque();
            assertThat(vm.retourProperty().get().texte()).contains("3 fichier(s)");
            assertThat(vm.retourProperty().get().severite()).isEqualTo(RetourOperation.Severite.SUCCES);
        }

        @Test
        @DisplayName("si la bascule fait quitter la source, la sélection (détail/audio) est remise à zéro")
        void basculer_reference_fait_disparaitre_la_ligne() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            LigneObservationAudio ligne = ligne(1, 10, "Pippip", "Pippip", StatutObservation.VALIDEE, true);
            // La ligne est référencée au départ, puis quitte le corpus au rechargement (liste vide).
            when(projections.lignesAudioReferences("u-1")).thenReturn(List.of(ligne), List.of());
            when(service.cheminAudio(10L)).thenReturn(Optional.of(Path.of("/ws/p.wav")));

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.References("u-1"));
            vm.selectionProperty().set(ligne);
            assertThat(vm.cheminAudioCourantProperty().get()).isNotNull();

            assertThat(vm.basculerReference()).isTrue();
            verify(service).marquerReference(1L, false);
            assertThat(vm.selectionProperty().get()).isNull();
            assertThat(vm.etatSelection().avecObservationProperty().get()).isFalse();
            assertThat(vm.cheminAudioCourantProperty().get()).isNull();
            assertThat(vm.detailProperty().get()).isEmpty();
        }

        @Test
        @DisplayName("basculerReference retire is_reference d'une ligne référencée")
        void basculer_reference_retire() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            LigneObservationAudio l = ligne(1, 10, "Pippip", "Pippip", StatutObservation.VALIDEE, true);
            when(projections.lignesAudioReferences("u-1")).thenReturn(List.of(l));

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.References("u-1"));
            vm.selectionProperty().set(l);

            assertThat(vm.basculerReference()).isTrue();
            verify(service).marquerReference(1L, false);
        }
    }

    @Nested
    @DisplayName("Comportements transverses")
    class Transverse {

        @Test
        @DisplayName("corriger vers la proposition Tadarida est refusé (c'est une validation)")
        void corriger_vers_tadarida_refuse() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(projections.lignesAudioReferences("u-1")).thenReturn(List.of());
            LigneObservationAudio l = ligne(1, 10, "Pippip", null, StatutObservation.NON_TOUCHEE, false);

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.References("u-1"));
            vm.selectionProperty().set(l);

            assertThat(vm.corriger(new Taxon("Pippip", "Pipistrellus", null, 1L)))
                    .isFalse();
            verify(service, never()).corriger(any(), any(), any());
            assertThat(vm.retourProperty().get().texte()).contains("Valider");
            assertThat(vm.retourProperty().get().severite()).isEqualTo(RetourOperation.Severite.INFO);
        }

        @Test
        @DisplayName("Filtre composable : restreint les lignes ET les compteurs suivent le sous-ensemble (#470)")
        void filtre_suit_le_sous_ensemble() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(projections.lignesAudioReferences("u-1"))
                    .thenReturn(List.of(
                            ligne(1, 10, "Pippip", "Pippip", StatutObservation.VALIDEE, true),
                            ligne(2, 11, "Nyclei", null, StatutObservation.NON_TOUCHEE, false)));

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.References("u-1"));
            vm.filtres().definir("statut", ligne -> ligne.statut() == StatutObservation.VALIDEE);

            assertThat(vm.observationsFiltrees()).hasSize(1);
            // #470 : les compteurs reflètent le sous-ensemble affiché (1 visible, 1 validée), pas le total.
            assertThat(vm.comptageProperty().get().total()).isEqualTo(1);
            assertThat(vm.comptageProperty().get().validees()).isEqualTo(1);

            // Retirer le filtre : on retrouve tout et les compteurs repassent au total.
            vm.filtres().definir("statut", null);
            assertThat(vm.observationsFiltrees()).hasSize(2);
            assertThat(vm.comptageProperty().get().total()).isEqualTo(2);
        }

        @Test
        @DisplayName("Filtre qui masque tout : l'indice d'état vide distingue « filtres » de « source vide » (#506)")
        void filtre_masque_tout_message_dedie() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(projections.lignesAudioReferences("u-1"))
                    .thenReturn(List.of(ligne(1, 10, "Pippip", "Pippip", StatutObservation.VALIDEE, true)));

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.References("u-1"));
            // Source non vide + affichage non vide → aucun indice d'état vide.
            assertThat(vm.messageProperty().get()).isEmpty();

            // Un filtre qui ne laisse rien : message dédié « filtres », pas le message « source vide ».
            vm.filtres().definir("statut", ligne -> ligne.statut() == StatutObservation.NON_TOUCHEE);
            assertThat(vm.observationsFiltrees()).isEmpty();
            assertThat(vm.messageProperty().get()).isEqualTo("Aucun résultat pour les filtres actifs.");

            // Retirer le filtre : l'indice disparaît (rien à signaler).
            vm.filtres().definir("statut", null);
            assertThat(vm.messageProperty().get()).isEmpty();
        }

        @Test
        @DisplayName("#479 : validerLot délègue à RevueEnLot, recharge et rapporte le nombre + un retour de succès")
        void valider_lot_delegue_et_rapporte() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(projections.lignesAudioReferences("u-1"))
                    .thenReturn(List.of(ligne(1, 10, "Pippip", "Pippip", StatutObservation.VALIDEE, true)));
            when(revueEnLot.valider(List.of(1L, 2L))).thenReturn(2);

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.References("u-1"));

            assertThat(vm.actions().validerLot(List.of(1L, 2L))).isEqualTo(2);
            verify(revueEnLot).valider(List.of(1L, 2L));
            // Rechargement post-lot (2e lecture) + retour de succès « N validée(s) ».
            verify(projections, times(2)).lignesAudioReferences("u-1");
            assertThat(vm.retourProperty().get().texte()).contains("2 observation(s) validée(s)");
            assertThat(vm.retourProperty().get().severite()).isEqualTo(RetourOperation.Severite.SUCCES);
        }

        @Test
        @DisplayName("#479 : basculerReferenceLot délègue à RevueEnLot (marquer/retirer) et rapporte le nombre")
        void basculer_reference_lot_delegue() {
            when(service.taxonsDisponibles()).thenReturn(List.of());
            when(projections.lignesAudioReferences("u-1")).thenReturn(List.of());
            when(revueEnLot.marquerReference(List.of(3L, 4L), true)).thenReturn(2);

            AudioViewModel vm = vm();
            vm.ouvrirSur(new SourceObservations.References("u-1"));

            assertThat(vm.actions().marquerReferenceLot(List.of(3L, 4L), true)).isEqualTo(2);
            verify(revueEnLot).marquerReference(List.of(3L, 4L), true);
        }
    }
}
