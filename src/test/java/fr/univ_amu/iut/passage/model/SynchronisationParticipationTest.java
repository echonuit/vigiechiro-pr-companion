package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ResultatParticipation;
import fr.univ_amu.iut.commun.model.InfosPoint;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Passerelle passage ↔ participation ([SynchronisationParticipation], axe 4) sur DAO/client/port **mockés**
/// (aucun réseau ni base) : création (lien posé, garde site), push (PATCH avec etag frais), pull (écrit
/// météo + micro localement), et refus durs quand le passage n'est pas lié.
@ExtendWith(MockitoExtension.class)
class SynchronisationParticipationTest {

    private static final String OBJECTID_SITE = "5eb12120cbe7410011f0a97f";

    @Mock
    ClientVigieChiro client;

    @Mock
    LienVigieChiroDao liens;

    @Mock
    PassageDao passageDao;

    @Mock
    MaterielMicroDao materielDao;

    @Mock
    fr.univ_amu.iut.commun.model.ReferentielPoint referentielPoint;

    private SynchronisationParticipation sync;

    @BeforeEach
    void preparer() {
        sync = new SynchronisationParticipation(client, liens, passageDao, materielDao, referentielPoint);
    }

    @Test
    @DisplayName("creerPour : crée la participation sur le site rattaché et mémorise le lien ENTITE_PASSAGE")
    void creer_pour_pose_le_lien() {
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_SITE, "7")).thenReturn(Optional.of(OBJECTID_SITE));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.creerParticipation(eq(OBJECTID_SITE), any())).thenReturn(ResultatParticipation.reussie("part-1"));

        ResultatParticipation resultat = sync.creerPour(42L);

        assertThat(resultat.id()).contains("part-1");
        verify(liens).upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, "42", "part-1"));
    }

    @Test
    @DisplayName("creerPour : site non rattaché → refus dur, aucune participation créée")
    void creer_pour_site_non_rattache() {
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_SITE, "7")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sync.creerPour(42L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("non rattaché");
        verify(client, never()).creerParticipation(anyString(), any());
    }

    @Test
    @DisplayName("creerPour : passage introuvable → refus dur")
    void creer_pour_passage_introuvable() {
        when(passageDao.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sync.creerPour(99L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    @DisplayName("pousserVers : PATCH avec l'etag frais relu ; refus si le passage n'est pas lié")
    void pousser_vers_patch_avec_etag() {
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(client.participation("part-1")).thenReturn(Optional.of(detail("e-frais")));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.modifierParticipation(eq("part-1"), eq("e-frais"), any()))
                .thenReturn(ResultatParticipation.reussie("part-1"));

        assertThat(sync.pousserVers(42L).id()).contains("part-1");
        verify(client).modifierParticipation(eq("part-1"), eq("e-frais"), any());
    }

    @Test
    @DisplayName("pousserVers : passage non lié à une participation → refus dur, aucun PATCH")
    void pousser_vers_non_lie() {
        when(passageDao.findById(42L)).thenReturn(Optional.of(passage(null)));
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sync.pousserVers(42L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("pas encore lié");
        verify(client, never()).modifierParticipation(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("tirerDepuis : écrit la météo (préservée) et la config micro du distant dans le passage local")
    void tirer_depuis_ecrit_localement() {
        when(passageDao.findById(42L)).thenReturn(Optional.of(passage(null)));
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(client.participation("part-1")).thenReturn(Optional.of(detail("e1")));

        sync.tirerDepuis(42L);

        verify(passageDao).update(any());
        ArgumentCaptor<MaterielMicro> micro = ArgumentCaptor.forClass(MaterielMicro.class);
        verify(materielDao).definir(micro.capture());
        assertThat(micro.getValue().typeMicro()).isEqualTo("ICS");
    }

    @Test
    @DisplayName("participationDe : délègue au lien ENTITE_PASSAGE")
    void participation_de_delegue() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        assertThat(sync.participationDe(42L)).contains("part-1");
    }

    private void armerPassageEtPoint() {
        when(passageDao.findById(42L)).thenReturn(Optional.of(passage(null)));
        when(referentielPoint.pour(7L)).thenReturn(Optional.of(new InfosPoint("Z41", 7L)));
    }

    private static ParticipationDetail detail(String etag) {
        return new ParticipationDetail(
                "part-1",
                etag,
                "Z41",
                "2026-07-03T19:00:00+00:00",
                "2026-07-04T04:00:00+00:00",
                new MeteoDepot("FAIBLE", "0-25"),
                Map.of("micro0_type", "ICS", "micro0_position", "CANOPEE"),
                "FINI");
    }

    private static Passage passage(String donneesMeteo) {
        return new Passage(
                42L,
                1,
                2026,
                "2026-07-03",
                "21:00:00",
                "05:00:00",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                donneesMeteo,
                null,
                7L,
                "1997632");
    }
}
