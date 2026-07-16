package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.EtatEtape;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [PassageViewModel] (fiche d'identité + stepper de statut + stats). Le
/// [ServicePassage] est mocké : aucune base de données. Les conditions de dépôt (météo + matériel du
/// micro) ne sont plus portées par cet écran : elles sont testées dans [RattachementViewModelTest]
/// (modale « Modifier le passage »).
@ExtendWith(MockitoExtension.class)
class PassageViewModelTest {

    private static final long ID_PASSAGE = 42L;
    private static final ContexteSite CONTEXTE = new ContexteSite("640380", "A1", "Étang");

    @Mock
    private ServicePassage service;

    @Mock
    private ServicePurgeOriginaux purge;

    @Mock
    private ServiceArchivagePassage archivage;

    @Mock
    private ServiceReactivationPassage reactivation;

    private PassageViewModel viewModel;

    @BeforeEach
    void preparer() {
        viewModel = new PassageViewModel(service, purge, archivage, reactivation);
    }

    private static DetailPassage detail(StatutWorkflow statut) {
        return new DetailPassage(
                2,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                "1925492",
                statut,
                Verdict.OK,
                null,
                4096L,
                1024L,
                30,
                150.0,
                MeteoReleve.VIDE,
                new DecompteAudio(30, 30));
    }

    @Test
    @DisplayName("ouvrirSur mappe la fiche d'identité (contexte site + données du passage)")
    void ouvrir_mappe_la_fiche() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.TRANSFORME));

        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        // Identité unifiée « Carré X · Point · N° Z » (socle #1020, harmonisation #1088) : séparateur
        // « · » et plus de suffixe d'année (l'année reste visible via la plage horaire).
        assertThat(viewModel.titreContexteProperty().get())
                .contains("640380")
                .contains("A1")
                .contains("N° 2")
                .contains("·")
                .doesNotContain("/");
        // Numéro de passage exposé pour le libellé du fil d'Ariane (« Détails du passage N° 2 »).
        assertThat(viewModel.getNumeroPassage()).isEqualTo(2);
        assertThat(viewModel.plageHoraireProperty().get()).contains("20:25:00").contains("07:47:00");
        assertThat(viewModel.enregistreurProperty().get()).isEqualTo("PR 1925492");
        assertThat(viewModel.statutProperty().get()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(viewModel.nombreSequencesProperty().get()).isEqualTo(30);
        assertThat(viewModel.verdictProperty().get()).isEqualTo(Verdict.OK);
        assertThat(viewModel.volumeBrutsProperty().get()).isEqualTo("4 Ko");
        assertThat(viewModel.volumeTransformesProperty().get()).isEqualTo("1 Ko");
        assertThat(viewModel.dureeEnregistreeProperty().get()).isEqualTo("2 min 30 s");
        assertThat(viewModel.messageProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("Le stepper reflète le statut courant (franchies / courante / à venir)")
    void stepper_reflete_le_statut() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.VERIFIE));

        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.etapes()).hasSize(5);
        assertThat(viewModel.etapes().get(0)).isEqualTo(new EtapeWorkflow(StatutWorkflow.IMPORTE, EtatEtape.FRANCHIE));
        assertThat(viewModel.etapes().get(1))
                .isEqualTo(new EtapeWorkflow(StatutWorkflow.TRANSFORME, EtatEtape.FRANCHIE));
        assertThat(viewModel.etapes().get(2)).isEqualTo(new EtapeWorkflow(StatutWorkflow.VERIFIE, EtatEtape.COURANTE));
        assertThat(viewModel.etapes().get(3))
                .isEqualTo(new EtapeWorkflow(StatutWorkflow.PRET_A_DEPOSER, EtatEtape.A_VENIR));
        assertThat(viewModel.etapes().get(4)).isEqualTo(new EtapeWorkflow(StatutWorkflow.DEPOSE, EtatEtape.A_VENIR));
    }

    @Test
    @DisplayName("#980 : « Dépôt en cours » n'est pas un jalon du stepper — le jalon courant reste « Prêt à déposer »")
    void stepper_ignore_le_statut_technique_de_depot() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOT_EN_COURS));

        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.etapes()).hasSize(5);
        assertThat(viewModel.etapes().get(3))
                .isEqualTo(new EtapeWorkflow(StatutWorkflow.PRET_A_DEPOSER, EtatEtape.COURANTE));
        assertThat(viewModel.etapes().get(4)).isEqualTo(new EtapeWorkflow(StatutWorkflow.DEPOSE, EtatEtape.A_VENIR));
    }

    @Test
    @DisplayName("La vérification est indisponible tant que la nuit n'est pas transformée")
    void verification_indisponible_avant_transformation() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.IMPORTE));

        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.verificationDisponibleProperty().get()).isFalse();
    }

    @Test
    @DisplayName("La vérification est disponible dès que la nuit est transformée")
    void verification_disponible_des_transforme() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.TRANSFORME));

        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.verificationDisponibleProperty().get()).isTrue();
        assertThat(viewModel.motifBlocageVerificationProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#1514 : la vérification est verrouillée sur une nuit déposée (verdict figé), avec l'explication")
    void verification_verrouillee_si_deposee() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));

        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.verificationDisponibleProperty().get()).isFalse();
        assertThat(viewModel.motifBlocageVerificationProperty().get()).contains("Verdict figé");
    }

    @Test
    @DisplayName("Le dépôt est accessible dès Vérifié et le reste une fois déposé (retour possible), pas avant")
    void depot_disponible_de_verifie_a_depose() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.VERIFIE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.depotDisponibleProperty().get()).isTrue();

        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.PRET_A_DEPOSER));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.depotDisponibleProperty().get()).isTrue();

        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.TRANSFORME));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.depotDisponibleProperty().get()).isFalse(); // trop tôt (pas encore vérifié)

        // #… : même une fois DÉPOSÉ, on peut revenir sur M-Lot (consulter/supprimer les archives) sans
        // avoir à annuler le dépôt.
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.depotDisponibleProperty().get()).isTrue();
    }

    @Test
    @DisplayName(
            "L'action recommandée suit le statut (Importé→aucune, Transformé→vérifier, Vérifié/Prêt→déposer, Déposé→valider)")
    void action_recommandee_suit_le_statut() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.IMPORTE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.actionRecommandeeProperty().get()).isEqualTo(ActionRecommandee.AUCUNE);

        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.TRANSFORME));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.actionRecommandeeProperty().get()).isEqualTo(ActionRecommandee.VERIFIER);

        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.VERIFIE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.actionRecommandeeProperty().get()).isEqualTo(ActionRecommandee.DEPOSER);

        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.PRET_A_DEPOSER));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.actionRecommandeeProperty().get()).isEqualTo(ActionRecommandee.DEPOSER);

        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.actionRecommandeeProperty().get()).isEqualTo(ActionRecommandee.VALIDER);
    }

    @Test
    @DisplayName("La validation Tadarida est verrouillée tant que le passage n'est pas déposé")
    void validation_verrouillee_avant_depot() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.VERIFIE));

        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.validationVerrouilleeProperty().get()).isTrue();
    }

    @Test
    @DisplayName("La validation Tadarida est déverrouillée une fois le passage déposé")
    void validation_deverrouillee_si_depose() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));

        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.validationVerrouilleeProperty().get()).isFalse();
    }

    @Test
    @DisplayName("Un passage introuvable est restitué dans le message et laisse l'état vide")
    void passage_introuvable() {
        when(service.detailPassage(99L)).thenThrow(new RegleMetierException("Passage introuvable : 99"));

        viewModel.ouvrirSur(99L, CONTEXTE);

        assertThat(viewModel.messageProperty().get()).contains("introuvable");
        assertThat(viewModel.statutProperty().get()).isNull();
        assertThat(viewModel.etapes()).isEmpty();
    }

    @Test
    @DisplayName("supprimer délègue au service avec l'identifiant du passage courant")
    void supprimer_delegue_au_service() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.VERIFIE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        viewModel.supprimer();

        verify(service).supprimer(ID_PASSAGE);
    }

    @Test
    @DisplayName("L'annulation du dépôt n'est disponible qu'une fois le passage déposé")
    void annulation_depot_disponible_si_depose() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.annulationDepotDisponibleProperty().get()).isTrue();

        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.PRET_A_DEPOSER));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.annulationDepotDisponibleProperty().get()).isFalse();
    }

    @Test
    @DisplayName("#789 : la suppression est possible sur tout statut sauf Déposé (gating du bouton Supprimer)")
    void suppression_possible_sauf_si_depose() {
        // Un passage déposé n'est pas supprimable (il faut d'abord annuler le dépôt) : le bouton est grisé.
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.suppressionPossibleProperty().get()).isFalse();

        // Sur les statuts antérieurs (Importé → Prêt à déposer), la suppression reste possible.
        for (StatutWorkflow statut : new StatutWorkflow[] {
            StatutWorkflow.IMPORTE, StatutWorkflow.TRANSFORME, StatutWorkflow.VERIFIE, StatutWorkflow.PRET_A_DEPOSER
        }) {
            when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(statut));
            viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
            assertThat(viewModel.suppressionPossibleProperty().get())
                    .as("suppression possible au statut %s", statut)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Le renommage est possible sur tout statut sauf Déposé ou Dépôt en cours")
    void renommage_possible_sauf_si_depose_ou_en_cours() {
        // Renommer après dépôt divergerait du serveur (le nom des fichiers y est l'identité) : bouton grisé.
        for (StatutWorkflow statut : new StatutWorkflow[] {StatutWorkflow.DEPOSE, StatutWorkflow.DEPOT_EN_COURS}) {
            when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(statut));
            viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
            assertThat(viewModel.renommagePossibleProperty().get())
                    .as("renommage bloqué au statut %s", statut)
                    .isFalse();
        }
        for (StatutWorkflow statut : new StatutWorkflow[] {
            StatutWorkflow.IMPORTE, StatutWorkflow.TRANSFORME, StatutWorkflow.VERIFIE, StatutWorkflow.PRET_A_DEPOSER
        }) {
            when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(statut));
            viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
            assertThat(viewModel.renommagePossibleProperty().get())
                    .as("renommage possible au statut %s", statut)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("annulerDepot délègue au service avec l'identifiant du passage courant")
    void annuler_depot_delegue_au_service() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        viewModel.annulerDepot();

        verify(service).annulerDepot(ID_PASSAGE);
    }

    @Test
    @DisplayName("La purge est proposée tant qu'il reste des originaux (volume bruts > 0)")
    void purge_disponible_si_originaux_presents() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.TRANSFORME)); // volume bruts = 4096
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.purgeDisponibleProperty().get()).isTrue();
    }

    @Test
    @DisplayName("purgerOriginaux supprime les bruts/ de la session puis marque les originaux purgés en base")
    void purger_originaux_supprime_et_marque() {
        Path racineSession = Path.of("/ws/Car640380-2026-Pass2-A1");
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.TRANSFORME));
        when(service.cheminSession(ID_PASSAGE)).thenReturn(Optional.of(racineSession));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        viewModel.purgerOriginaux();

        verify(purge).purgerSession(racineSession);
        verify(service).marquerOriginauxPurges(ID_PASSAGE);
    }

    @Test
    @DisplayName("#1300 : l'archivage est possible sur un passage déposé qui conserve encore de l'audio")
    void archivage_possible_sur_depose_avec_audio() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.archivagePossibleProperty().get()).isTrue();
        assertThat(viewModel.motifBlocageArchivageProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#1300 : l'archivage est bloqué avant le dépôt, avec le motif affiché")
    void archivage_bloque_avant_depot() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.VERIFIE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.archivagePossibleProperty().get()).isFalse();
        assertThat(viewModel.motifBlocageArchivageProperty().get()).contains("pas encore déposé");
    }

    @Test
    @DisplayName("#1300 : un passage déjà archivé (plus d'audio) est bloqué avec le motif « déjà archivé »")
    void archivage_bloque_si_deja_archive() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailSansAudio(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.archivagePossibleProperty().get()).isFalse();
        assertThat(viewModel.motifBlocageArchivageProperty().get()).contains("Déjà archivé");
    }

    @Test
    @DisplayName("#1300 : archiver, volumeArchivable et sequencesSansEmpreinte délèguent au service d'archivage")
    void archiver_delegue_au_service() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));
        when(archivage.volumeRecuperable(ID_PASSAGE)).thenReturn(42L);
        when(archivage.sequencesSansEmpreinte(ID_PASSAGE)).thenReturn(3);
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.volumeArchivable()).isEqualTo(42L);
        assertThat(viewModel.sequencesSansEmpreinte()).isEqualTo(3);
        viewModel.archiver();
        verify(archivage).archiver(ID_PASSAGE);
    }

    @Test
    @DisplayName("#1302 : la réactivation est proposée sur un passage archivé (audio absent du disque)")
    void reactivation_possible_sur_passage_archive() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailSansAudio(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.reactivationPossibleProperty().get()).isTrue();
        assertThat(viewModel.motifBlocageReactivationProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#1302 : rien à réactiver quand l'audio est déjà là, avec le motif affiché")
    void reactivation_bloquee_si_audio_present() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.reactivationPossibleProperty().get()).isFalse();
        assertThat(viewModel.motifBlocageReactivationProperty().get()).contains("déjà sur le disque");
    }

    @Test
    @DisplayName("#1302 : reactiver délègue au service de réactivation avec le dossier choisi")
    void reactiver_delegue_au_service() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailSansAudio(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        Path dossier = Path.of("/sauvegarde/nuit-du-20-juin");

        viewModel.reactiver(dossier, progres -> {});

        verify(reactivation).reactiver(eq(ID_PASSAGE), eq(dossier), any());
    }

    /// Fiche d'un passage dont l'audio n'est plus conservé (volumes bruts et séquences à zéro :
    /// l'état après archivage #1300).
    private static DetailPassage detailSansAudio(StatutWorkflow statut) {
        return new DetailPassage(
                2,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                "1925492",
                statut,
                Verdict.OK,
                null,
                0L,
                0L,
                30,
                150.0,
                MeteoReleve.VIDE,
                new DecompteAudio(0, 30));
    }
}
