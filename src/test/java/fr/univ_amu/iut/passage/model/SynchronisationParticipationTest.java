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
import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.ResultatEcriture;
import fr.univ_amu.iut.commun.api.Traitement;
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
    fr.univ_amu.iut.passage.model.dao.EnregistreurDao enregistreurDao;

    @Mock
    fr.univ_amu.iut.commun.model.ReferentielPoint referentielPoint;

    private SynchronisationParticipation sync;

    @BeforeEach
    void preparer() {
        sync = new SynchronisationParticipation(
                client, liens, passageDao, materielDao, enregistreurDao, referentielPoint);
    }

    @Test
    @DisplayName("creerPour : crée la participation sur le site rattaché et mémorise le lien ENTITE_PASSAGE")
    void creer_pour_pose_le_lien() {
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_SITE, "7")).thenReturn(Optional.of(OBJECTID_SITE));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.creerParticipation(eq(OBJECTID_SITE), any())).thenReturn(ResultatEcriture.reussie("part-1"));

        ResultatEcriture resultat = sync.creerPour(42L);

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
        when(client.participation("part-1")).thenReturn(ReponseApi.succes(detail("e-frais")));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.modifierParticipation(eq("part-1"), eq("e-frais"), any()))
                .thenReturn(ResultatEcriture.reussie("part-1"));

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
        when(client.participation("part-1")).thenReturn(ReponseApi.succes(detail("e1")));

        sync.tirerDepuis(42L);

        verify(passageDao).update(any());
        ArgumentCaptor<MaterielMicro> micro = ArgumentCaptor.forClass(MaterielMicro.class);
        verify(materielDao).definir(micro.capture());
        assertThat(micro.getValue().typeMicro()).isEqualTo("ICS");
    }

    @Test
    @DisplayName("#1828 tirerDepuis : le n° de série de la participation est rapatrié quand le passage l'ignore")
    void tirer_depuis_rapatrie_le_numero_de_serie() {
        when(passageDao.findById(42L)).thenReturn(Optional.of(passageAvecEnregistreur(Enregistreur.INCONNU)));
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(client.participation("part-1")).thenReturn(ReponseApi.succes(detailAvecSerie("1925492")));
        when(enregistreurDao.findById("1925492")).thenReturn(Optional.empty());

        sync.tirerDepuis(42L);

        verify(enregistreurDao)
                .insert(new Enregistreur("1925492", null, null)); // clé étrangère garantie avant l'accroche
        ArgumentCaptor<Passage> ecrit = ArgumentCaptor.forClass(Passage.class);
        verify(passageDao).update(ecrit.capture());
        assertThat(ecrit.getValue().idEnregistreur())
                .as("le bouton « Récupérer depuis VigieChiro » rattrape enfin l'enregistreur")
                .isEqualTo("1925492");
    }

    @Test
    @DisplayName("#1828 tirerDepuis : un « INCONNU » distant n'écrase JAMAIS un n° de série local réel")
    void tirer_depuis_ne_degrade_pas_un_numero_reel() {
        when(passageDao.findById(42L)).thenReturn(Optional.of(passage(null))); // porte déjà 1997632
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(client.participation("part-1")).thenReturn(ReponseApi.succes(detailAvecSerie(Enregistreur.INCONNU)));

        sync.tirerDepuis(42L);

        ArgumentCaptor<Passage> ecrit = ArgumentCaptor.forClass(Passage.class);
        verify(passageDao).update(ecrit.capture());
        assertThat(ecrit.getValue().idEnregistreur())
                .as("le n° lu du journal à l'import prime sur la sentinelle publiée par erreur")
                .isEqualTo("1997632");
        verify(enregistreurDao, never()).insert(any());
    }

    @Test
    @DisplayName("participationDe : délègue au lien ENTITE_PASSAGE")
    void participation_de_delegue() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        assertThat(sync.participationDe(42L)).contains("part-1");
    }

    @Test
    @DisplayName("ecartsAvecDistant : passage non lié → rien à vérifier (liste vide, aucun appel réseau)")
    void ecarts_non_lie() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.empty());

        assertThat(sync.ecartsAvecDistant(42L)).isEmpty();
        verify(client, never()).participation(anyString());
    }

    @Test
    @DisplayName("ecartsAvecDistant : même point, même nuit (date UTC de date_debut) → aucun écart")
    void ecarts_concordants() {
        armerLienEtDistant(detail("part-1", "Z41", "2026-07-03T19:00:00+00:00"));

        assertThat(sync.ecartsAvecDistant(42L)).isEmpty();
    }

    @Test
    @DisplayName("ecartsAvecDistant : point différent → écart nommant les deux codes")
    void ecarts_point_different() {
        armerLienEtDistant(detail("part-1", "Z12", "2026-07-03T19:00:00+00:00"));

        assertThat(sync.ecartsAvecDistant(42L))
                .singleElement()
                .asString()
                .contains("Z41")
                .contains("Z12");
    }

    @Test
    @DisplayName("ecartsAvecDistant : nuit différente → écart nommant les deux dates")
    void ecarts_nuit_differente() {
        armerLienEtDistant(detail("part-1", "Z41", "2026-07-04T19:00:00+00:00"));

        assertThat(sync.ecartsAvecDistant(42L))
                .singleElement()
                .asString()
                .contains("2026-07-03")
                .contains("2026-07-04");
    }

    @Test
    @DisplayName("ecartsAvecDistant : participation liée injoignable → écart explicite (pas un silence)")
    void ecarts_distant_injoignable() {
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(client.participation("part-1")).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));

        assertThat(sync.ecartsAvecDistant(42L)).singleElement().asString().contains("injoignable");
    }

    @Test
    @DisplayName("ecartsAvecDistant : date_debut absente ou illisible → écart explicite")
    void ecarts_date_illisible() {
        armerLienEtDistant(detail("part-1", "Z41", "pas-une-date"));

        assertThat(sync.ecartsAvecDistant(42L)).singleElement().asString().contains("illisible");
    }

    private void armerLienEtDistant(ParticipationDetail distant) {
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(client.participation("part-1")).thenReturn(ReponseApi.succes(distant));
    }

    /// Participation distante minimale pour le pré-vol (météo/config sans objet ici).
    private static ParticipationDetail detail(String id, String point, String dateDebut) {
        return new ParticipationDetail(id, "e1", point, dateDebut, null, null, Map.of(), traitementFini());
    }

    /// Nuit déjà analysée côté serveur : le pré-vol et la synchronisation n'en dépendent pas, mais le
    /// détail distant en porte toujours un (#1260).
    private static Traitement traitementFini() {
        return new Traitement(EtatTraitement.FINI, null, null, "2026-07-04T06:00:00+00:00", null, null);
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
                traitementFini());
    }

    /// Détail portant un n° de série sous la clé **canonique** (celle du formulaire web), plus le micro.
    private static ParticipationDetail detailAvecSerie(String serie) {
        return new ParticipationDetail(
                "part-1",
                "e1",
                "Z41",
                "2026-07-03T19:00:00+00:00",
                "2026-07-04T04:00:00+00:00",
                new MeteoDepot("FAIBLE", "0-25"),
                Map.of("micro0_type", "ICS", "detecteur_enregistreur_numero_serie", serie),
                traitementFini());
    }

    /// Le même passage que [#passage], mais dont l'enregistreur est celui qu'on veut éprouver.
    private static Passage passageAvecEnregistreur(String serie) {
        Passage modele = passage(null);
        return new Passage(
                modele.id(),
                modele.numeroPassage(),
                modele.annee(),
                modele.dateEnregistrement(),
                modele.heureDebut(),
                modele.heureFin(),
                modele.parametresAcquisition(),
                modele.statutWorkflow(),
                modele.verdictVerification(),
                modele.commentaire(),
                modele.donneesMeteo(),
                modele.deposeLe(),
                modele.idPoint(),
                serie);
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
