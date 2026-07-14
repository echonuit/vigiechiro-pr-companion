package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.CouvertureNuageuse;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.PositionMicro;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceRattachement;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.Vent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [RattachementViewModel] (modale E2.S8). Le [ServicePassage] est mocké :
/// aucune base de données ni disque.
@ExtendWith(MockitoExtension.class)
class RattachementViewModelTest {

    private static final long ID = 7L;

    @Mock
    private ServicePassage service;

    @Mock
    ServiceRattachement rattachement;

    @Mock
    ServiceConditionsPassage conditionsPassage;

    private RattachementViewModel viewModel;

    @BeforeEach
    void preparer() {
        viewModel = new RattachementViewModel(service, rattachement, conditionsPassage, Optional.empty());
    }

    private static DetailPassage detail(int numero, int annee, int nombreSequences) {
        return new DetailPassage(
                numero,
                annee,
                "2026-06-20",
                "21:00:00",
                "05:00:00",
                "1925492",
                StatutWorkflow.TRANSFORME,
                Verdict.OK,
                null,
                0L,
                0L,
                nombreSequences,
                0.0,
                null,
                new DecompteAudio(0, 0));
    }

    private static DetailPassage detailMeteo(MeteoReleve meteo) {
        return new DetailPassage(
                1,
                2026,
                "2026-06-20",
                "21:00:00",
                "05:00:00",
                "1925492",
                StatutWorkflow.TRANSFORME,
                Verdict.OK,
                null,
                0L,
                0L,
                30,
                0.0,
                meteo,
                new DecompteAudio(0, 0));
    }

    @Test
    @DisplayName("ouvrirSur pré-remplit l'année et le n° ; le récap est neutre tant que rien ne change")
    void ouvrir_pre_remplit_et_recap_neutre() {
        when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));

        viewModel.ouvrirSur(ID, "040962", "A1");

        assertThat(viewModel.anneeProperty().get()).isEqualTo(2026);
        assertThat(viewModel.numeroPassageProperty().get()).isEqualTo(1);
        assertThat(viewModel.recapProperty().get()).contains("Aucun changement");
    }

    @Test
    @DisplayName("Changer le n° met à jour le récap (quadruplet X → Y + nombre de séquences)")
    void changer_numero_met_a_jour_le_recap() {
        when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
        viewModel.ouvrirSur(ID, "040962", "A1");

        viewModel.numeroPassageProperty().set(2);

        assertThat(viewModel.recapProperty().get())
                .contains("Car040962-2026-Pass1-A1")
                .contains("Car040962-2026-Pass2-A1")
                .contains("30");
    }

    @Test
    @DisplayName("valider délègue à modifierRattachement avec le nouveau préfixe et réussit")
    void valider_delegue_et_reussit() {
        when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.numeroPassageProperty().set(2);

        boolean ok = viewModel.valider();

        assertThat(ok).isTrue();
        verify(rattachement).modifierRattachement(ID, new Prefixe("040962", 2026, 2, "A1"));
    }

    @Test
    @DisplayName("valider restitue l'erreur métier (R5) et renvoie false")
    void valider_restitue_l_erreur() {
        when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.numeroPassageProperty().set(2);
        doThrow(new RegleMetierException("R5 : un passage n°2 existe déjà"))
                .when(rattachement)
                .modifierRattachement(eq(ID), any());

        boolean ok = viewModel.valider();

        assertThat(ok).isFalse();
        assertThat(viewModel.messageErreurProperty().get()).contains("R5");
    }

    @Test
    @DisplayName("valider refuse un n° de passage < 1 sans appeler le service")
    void valider_refuse_numero_invalide() {
        when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.numeroPassageProperty().set(0);

        boolean ok = viewModel.valider();

        assertThat(ok).isFalse();
        assertThat(viewModel.messageErreurProperty().get()).contains("numéro de passage");
        verify(rattachement, never()).modifierRattachement(any(), any());
    }

    @Test
    @DisplayName("valider surface une défaillance disque/base dans le message au lieu de la propager")
    void valider_surface_une_defaillance_operationnelle() {
        when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.numeroPassageProperty().set(2);
        doThrow(new UncheckedIOException("Déplacement du dossier impossible", new IOException()))
                .when(rattachement)
                .modifierRattachement(eq(ID), any());

        boolean ok = viewModel.valider();

        assertThat(ok).isFalse();
        assertThat(viewModel.messageErreurProperty().get()).contains("Déplacement");
    }

    // --- Conditions de dépôt (météo + matériel du micro), désormais éditées dans cette modale ---

    @Test
    @DisplayName("#106 étendu : ouvrirSur pré-remplit les champs météo depuis le relevé du passage")
    void meteo_affichee() {
        when(service.detailPassage(ID))
                .thenReturn(detailMeteo(new MeteoReleve(8.5, 4.0, Vent.FAIBLE, CouvertureNuageuse.DE_25_A_50)));
        viewModel.ouvrirSur(ID, "040962", "A1");
        assertThat(viewModel.conditions().temperatureSaisieProperty().get()).isEqualTo("8.5");
        assertThat(viewModel.conditions().temperatureFinSaisieProperty().get()).isEqualTo("4.0");
        assertThat(viewModel.conditions().ventSaisieProperty().get()).isEqualTo(Vent.FAIBLE);
        assertThat(viewModel.conditions().couvertureNuageuseSaisieProperty().get())
                .isEqualTo(CouvertureNuageuse.DE_25_A_50);
    }

    @Test
    @DisplayName("#106 étendu : relevé météo absent → champs de saisie vides")
    void meteo_absente() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID, "040962", "A1");
        assertThat(viewModel.conditions().temperatureSaisieProperty().get()).isEmpty();
        assertThat(viewModel.conditions().ventSaisieProperty().get()).isNull();
    }

    @Test
    @DisplayName("#106 étendu : enregistrerMeteo valide délègue le relevé au service, sans message")
    void enregistrer_meteo_valide() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.conditions().temperatureSaisieProperty().set("9,0");
        viewModel.conditions().ventSaisieProperty().set(Vent.FAIBLE);

        assertThat(viewModel.conditions().enregistrerMeteo()).isTrue();

        verify(conditionsPassage).definirMeteo(ID, new MeteoReleve(9.0, null, Vent.FAIBLE, null));
        assertThat(viewModel.messageErreurProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#106 étendu : une saisie météo invalide publie un message, sans appeler le service")
    void enregistrer_meteo_invalide() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.conditions().temperatureSaisieProperty().set("froid");

        assertThat(viewModel.conditions().enregistrerMeteo()).isFalse();

        assertThat(viewModel.messageErreurProperty().get()).contains("invalide");
        verify(conditionsPassage, never()).definirMeteo(any(), any());
    }

    @Test
    @DisplayName("dépôt : ouvrirSur pré-remplit les champs matériel depuis le passage")
    void materiel_affiche() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        when(conditionsPassage.materiel(ID)).thenReturn(new MaterielMicro(ID, PositionMicro.CANOPEE, 4.0, "SMX-U1"));
        viewModel.ouvrirSur(ID, "040962", "A1");
        assertThat(viewModel.conditions().positionSaisieProperty().get()).isEqualTo(PositionMicro.CANOPEE);
        assertThat(viewModel.conditions().hauteurSaisieProperty().get()).isEqualTo("4.0");
        assertThat(viewModel.conditions().typeMicroSaisieProperty().get()).isEqualTo("SMX-U1");
    }

    @Test
    @DisplayName("dépôt : enregistrerMateriel valide délègue le matériel au service, sans message")
    void enregistrer_materiel_valide() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.conditions().positionSaisieProperty().set(PositionMicro.SOL);
        viewModel.conditions().hauteurSaisieProperty().set("2,5");
        viewModel.conditions().typeMicroSaisieProperty().set(" Micro interne ");

        assertThat(viewModel.conditions().enregistrerMateriel()).isTrue();

        verify(conditionsPassage).definirMateriel(new MaterielMicro(ID, PositionMicro.SOL, 2.5, "Micro interne"));
        assertThat(viewModel.messageErreurProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("dépôt : une hauteur de fixation invalide publie un message, sans appeler le service")
    void enregistrer_materiel_hauteur_invalide() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.conditions().hauteurSaisieProperty().set("haut");

        assertThat(viewModel.conditions().enregistrerMateriel()).isFalse();

        assertThat(viewModel.messageErreurProperty().get()).contains("invalide");
        verify(conditionsPassage, never()).definirMateriel(any());
    }

    @Test
    @DisplayName("#547 : recupererMeteo délègue au service (relevé remonté tel quel)")
    void recuperer_meteo_delegue_au_service() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID, "040962", "A1");
        when(conditionsPassage.recupererMeteo(ID))
                .thenReturn(Optional.of(new MeteoReleve(9.0, 3.0, Vent.FAIBLE, CouvertureNuageuse.DE_25_A_50)));

        assertThat(viewModel.conditions().recupererMeteo())
                .contains(new MeteoReleve(9.0, 3.0, Vent.FAIBLE, CouvertureNuageuse.DE_25_A_50));
    }

    @Test
    @DisplayName("#547 : un relevé récupéré pré-remplit les champs météo + message de confirmation")
    void appliquer_meteo_recuperee_prefill() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID, "040962", "A1");

        viewModel
                .conditions()
                .appliquerMeteoRecuperee(
                        Optional.of(new MeteoReleve(9.0, 3.0, Vent.FAIBLE, CouvertureNuageuse.DE_25_A_50)));

        assertThat(viewModel.conditions().temperatureSaisieProperty().get()).isEqualTo("9.0");
        assertThat(viewModel.conditions().ventSaisieProperty().get()).isEqualTo(Vent.FAIBLE);
        assertThat(viewModel.messageErreurProperty().get()).contains("pré-remplie");
    }

    @Test
    @DisplayName("#547 : météo indisponible → message d'aide, champs inchangés")
    void appliquer_meteo_recuperee_absente() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID, "040962", "A1");

        viewModel.conditions().appliquerMeteoRecuperee(Optional.empty());

        assertThat(viewModel.messageErreurProperty().get()).contains("indisponible");
        assertThat(viewModel.conditions().temperatureSaisieProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("appliquer enregistre d'un bloc le rattachement, la météo et le matériel du micro")
    void appliquer_enregistre_tout() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.numeroPassageProperty().set(2);
        viewModel.conditions().temperatureSaisieProperty().set("9,0");
        viewModel.conditions().positionSaisieProperty().set(PositionMicro.SOL);

        boolean ok = viewModel.appliquer();

        assertThat(ok).isTrue();
        verify(conditionsPassage).definirMeteo(ID, new MeteoReleve(9.0, null, null, null));
        verify(conditionsPassage).definirMateriel(new MaterielMicro(ID, PositionMicro.SOL, null, null));
        verify(rattachement).modifierRattachement(ID, new Prefixe("040962", 2026, 2, "A1"));
    }

    @Test
    @DisplayName("appliquer avec une météo invalide n'enregistre rien et ne renomme pas les séquences")
    void appliquer_meteo_invalide_ne_renomme_pas() {
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        viewModel.ouvrirSur(ID, "040962", "A1");
        viewModel.numeroPassageProperty().set(2);
        viewModel.conditions().temperatureSaisieProperty().set("froid");

        boolean ok = viewModel.appliquer();

        assertThat(ok).isFalse();
        assertThat(viewModel.messageErreurProperty().get()).contains("invalide");
        verify(rattachement, never()).modifierRattachement(any(), any());
    }

    // --- Phase 2 : pousser les métadonnées vers la participation VigieChiro (à la validation) ---

    @Test
    @DisplayName("Phase 2 : pousserVersVigieChiro délègue à la passerelle pour le passage ouvert")
    void pousser_vers_vigiechiro_delegue() {
        SynchronisationParticipation sync = mock(SynchronisationParticipation.class);
        RattachementViewModel avecSync =
                new RattachementViewModel(service, rattachement, conditionsPassage, Optional.of(sync));
        when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
        avecSync.ouvrirSur(ID, "040962", "A1");

        avecSync.pousserVersVigieChiro();

        verify(sync).pousserVers(ID);
    }

    @Test
    @DisplayName("Phase 2 : passage non lié (RegleMetierException) → push silencieux, ne lève pas")
    void pousser_vers_vigiechiro_silencieux_si_non_lie() {
        SynchronisationParticipation sync = mock(SynchronisationParticipation.class);
        when(sync.pousserVers(ID)).thenThrow(new RegleMetierException("pas encore lié"));
        RattachementViewModel avecSync =
                new RattachementViewModel(service, rattachement, conditionsPassage, Optional.of(sync));
        when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
        avecSync.ouvrirSur(ID, "040962", "A1");

        avecSync.pousserVersVigieChiro(); // best-effort : n'échoue pas
        verify(sync).pousserVers(ID);
    }

    @Test
    @DisplayName("Phase 2 : sans passerelle (hors connexion) → pousserVersVigieChiro ne fait rien")
    void pousser_vers_vigiechiro_sans_passerelle() {
        when(service.detailPassage(ID)).thenReturn(detail(1, 2026, 30));
        viewModel.ouvrirSur(ID, "040962", "A1"); // viewModel construit avec Optional.empty()

        viewModel.pousserVersVigieChiro(); // no-op, ne lève pas
    }

    // --- Phase 2b : tirer les métadonnées depuis la participation VigieChiro ---

    @Test
    @DisplayName("Phase 2b : tirerDepuisVigieChiro délègue puis rechargerApresTir recharge les champs")
    void tirer_depuis_vigiechiro_recupere() {
        SynchronisationParticipation sync = mock(SynchronisationParticipation.class);
        RattachementViewModel avecSync =
                new RattachementViewModel(service, rattachement, conditionsPassage, Optional.of(sync));
        when(service.detailPassage(ID))
                .thenReturn(detailMeteo(new MeteoReleve(9.0, 3.0, Vent.FORT, CouvertureNuageuse.DE_75_A_100)));
        avecSync.ouvrirSur(ID, "040962", "A1");

        boolean recupere = avecSync.tirerDepuisVigieChiro();
        avecSync.rechargerApresTir(recupere);

        assertThat(recupere).isTrue();
        verify(sync).tirerDepuis(ID);
        assertThat(avecSync.conditions().ventSaisieProperty().get()).isEqualTo(Vent.FORT);
        assertThat(avecSync.messageErreurProperty().get()).contains("récupérées");
    }

    @Test
    @DisplayName("Phase 2b : passage non lié → recupere=false + message d'aide")
    void tirer_depuis_vigiechiro_non_lie() {
        SynchronisationParticipation sync = mock(SynchronisationParticipation.class);
        doThrow(new RegleMetierException("pas lié")).when(sync).tirerDepuis(ID);
        RattachementViewModel avecSync =
                new RattachementViewModel(service, rattachement, conditionsPassage, Optional.of(sync));
        when(service.detailPassage(ID)).thenReturn(detailMeteo(MeteoReleve.VIDE));
        avecSync.ouvrirSur(ID, "040962", "A1");

        boolean recupere = avecSync.tirerDepuisVigieChiro();
        avecSync.rechargerApresTir(recupere);

        assertThat(recupere).isFalse();
        assertThat(avecSync.messageErreurProperty().get()).contains("Aucune participation");
    }

    @Test
    @DisplayName("Phase 2b : peutSynchroniser reflète la présence de la passerelle")
    void peut_synchroniser() {
        assertThat(viewModel.peutSynchroniser()).isFalse(); // @BeforeEach : Optional.empty()
        assertThat(new RattachementViewModel(
                                service,
                                rattachement,
                                conditionsPassage,
                                Optional.of(mock(SynchronisationParticipation.class)))
                        .peutSynchroniser())
                .isTrue();
    }

    // « Importer les observations » a quitté cette modale (#1350) : ses tests vivent désormais dans
    // PassageViewModelTest, avec l'action.
}
