package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.PositionMicro;
import fr.univ_amu.iut.passage.model.ServicePassage;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [PassageViewModel] (fiche d'identité + stepper de statut + stats). Le
/// [ServicePassage] est mocké : aucune base de données.
@ExtendWith(MockitoExtension.class)
class PassageViewModelTest {

    private static final long ID_PASSAGE = 42L;
    private static final ContexteSite CONTEXTE = new ContexteSite("640380", "A1", "Étang");

    @Mock
    private ServicePassage service;

    @Mock
    private ServicePurgeOriginaux purge;

    private PassageViewModel viewModel;

    @BeforeEach
    void preparer() {
        viewModel = new PassageViewModel(service, purge);
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
                MeteoReleve.VIDE);
    }

    private static DetailPassage detailAvec(MeteoReleve meteo) {
        return new DetailPassage(
                2,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                "1925492",
                StatutWorkflow.TRANSFORME,
                Verdict.OK,
                null,
                4096L,
                1024L,
                30,
                150.0,
                meteo);
    }

    @Test
    @DisplayName("#106 étendu : ouvrirSur pré-remplit les champs météo depuis le relevé du passage")
    void meteo_affichee() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailAvec(new MeteoReleve(8.5, 4.0, 12.0, 40.0)));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.conditions().temperatureSaisieProperty().get()).isEqualTo("8.5");
        assertThat(viewModel.conditions().temperatureFinSaisieProperty().get()).isEqualTo("4.0");
        assertThat(viewModel.conditions().ventSaisieProperty().get()).isEqualTo("12.0");
        assertThat(viewModel.conditions().couvertureNuageuseSaisieProperty().get())
                .isEqualTo("40.0");
    }

    @Test
    @DisplayName("#106 étendu : relevé météo absent → champs de saisie vides")
    void meteo_absente() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailAvec(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.conditions().temperatureSaisieProperty().get()).isEmpty();
        assertThat(viewModel.conditions().ventSaisieProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#106 étendu : enregistrerMeteo valide délègue le relevé au service, sans message")
    void enregistrer_meteo_valide() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailAvec(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        viewModel.conditions().temperatureSaisieProperty().set("9,0");
        viewModel.conditions().ventSaisieProperty().set("12");

        viewModel.conditions().enregistrerMeteo();

        verify(service).definirMeteo(ID_PASSAGE, new MeteoReleve(9.0, null, 12.0, null));
        assertThat(viewModel.messageProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#106 étendu : une saisie météo invalide publie un message, sans appeler le service")
    void enregistrer_meteo_invalide() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailAvec(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        viewModel.conditions().temperatureSaisieProperty().set("froid");

        viewModel.conditions().enregistrerMeteo();

        assertThat(viewModel.messageProperty().get()).contains("invalide");
        verify(service, never()).definirMeteo(any(), any());
    }

    @Test
    @DisplayName("dépôt : ouvrirSur pré-remplit les champs matériel depuis le passage")
    void materiel_affiche() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailAvec(MeteoReleve.VIDE));
        when(service.materiel(ID_PASSAGE)).thenReturn(new MaterielMicro(ID_PASSAGE, PositionMicro.CANOPEE, 4.0, "SM4"));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.conditions().positionSaisieProperty().get()).isEqualTo(PositionMicro.CANOPEE);
        assertThat(viewModel.conditions().hauteurSaisieProperty().get()).isEqualTo("4.0");
        assertThat(viewModel.conditions().typeMicroSaisieProperty().get()).isEqualTo("SM4");
    }

    @Test
    @DisplayName("dépôt : enregistrerMateriel valide délègue le matériel au service, sans message")
    void enregistrer_materiel_valide() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailAvec(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        viewModel.conditions().positionSaisieProperty().set(PositionMicro.SOL);
        viewModel.conditions().hauteurSaisieProperty().set("2,5");
        viewModel.conditions().typeMicroSaisieProperty().set(" interne ");

        viewModel.conditions().enregistrerMateriel();

        verify(service).definirMateriel(new MaterielMicro(ID_PASSAGE, PositionMicro.SOL, 2.5, "interne"));
        assertThat(viewModel.messageProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("dépôt : une hauteur de fixation invalide publie un message, sans appeler le service")
    void enregistrer_materiel_hauteur_invalide() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailAvec(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        viewModel.conditions().hauteurSaisieProperty().set("haut");

        viewModel.conditions().enregistrerMateriel();

        assertThat(viewModel.messageProperty().get()).contains("invalide");
        verify(service, never()).definirMateriel(any());
    }

    @Test
    @DisplayName("#547 : recupererMeteo délègue au service (relevé remonté tel quel)")
    void recuperer_meteo_delegue_au_service() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailAvec(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        when(service.recupererMeteo(ID_PASSAGE)).thenReturn(Optional.of(new MeteoReleve(9.0, 3.0, 12.0, 40.0)));

        assertThat(viewModel.conditions().recupererMeteo()).contains(new MeteoReleve(9.0, 3.0, 12.0, 40.0));
    }

    @Test
    @DisplayName("#547 : un relevé récupéré pré-remplit les champs météo + message de confirmation")
    void appliquer_meteo_recuperee_prefill() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailAvec(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        viewModel.conditions().appliquerMeteoRecuperee(Optional.of(new MeteoReleve(9.0, 3.0, 12.0, 40.0)));

        assertThat(viewModel.conditions().temperatureSaisieProperty().get()).isEqualTo("9.0");
        assertThat(viewModel.conditions().ventSaisieProperty().get()).isEqualTo("12.0");
        assertThat(viewModel.messageProperty().get()).contains("pré-remplie");
    }

    @Test
    @DisplayName("#547 : météo indisponible → message d'aide, champs inchangés")
    void appliquer_meteo_recuperee_absente() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detailAvec(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        viewModel.conditions().appliquerMeteoRecuperee(Optional.empty());

        assertThat(viewModel.messageProperty().get()).contains("indisponible");
        assertThat(viewModel.conditions().temperatureSaisieProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("ouvrirSur mappe la fiche d'identité (contexte site + données du passage)")
    void ouvrir_mappe_la_fiche() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.TRANSFORME));

        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);

        assertThat(viewModel.titreContexteProperty().get())
                .contains("640380")
                .contains("A1")
                .contains("N° 2")
                .contains("2026");
        // Numéro de passage exposé pour le libellé du fil d'Ariane (« Détails du passage N° 2 »).
        assertThat(viewModel.getNumeroPassage()).isEqualTo(2);
        assertThat(viewModel.plageHoraireProperty().get()).contains("20:25:00").contains("07:47:00");
        assertThat(viewModel.enregistreurProperty().get()).isEqualTo("PR 1925492");
        assertThat(viewModel.statutProperty().get()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(viewModel.nombreSequencesProperty().get()).isEqualTo(30);
        assertThat(viewModel.verdictProperty().get()).isEqualTo(Verdict.OK);
        assertThat(viewModel.volumeBrutsProperty().get()).isEqualTo("4 Ko");
        assertThat(viewModel.volumeTransformesProperty().get()).isEqualTo("1 Ko");
        assertThat(viewModel.dureeAudibleProperty().get()).isEqualTo("2 min 30 s");
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
    }

    @Test
    @DisplayName("Le dépôt est disponible en phase Vérifié / Prêt à déposer, pas avant ni après")
    void depot_disponible_en_phase_de_depot() {
        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.VERIFIE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.depotDisponibleProperty().get()).isTrue();

        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.PRET_A_DEPOSER));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.depotDisponibleProperty().get()).isTrue();

        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.TRANSFORME));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.depotDisponibleProperty().get()).isFalse(); // trop tôt

        when(service.detailPassage(ID_PASSAGE)).thenReturn(detail(StatutWorkflow.DEPOSE));
        viewModel.ouvrirSur(ID_PASSAGE, CONTEXTE);
        assertThat(viewModel.depotDisponibleProperty().get()).isFalse(); // déjà déposé
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
}
