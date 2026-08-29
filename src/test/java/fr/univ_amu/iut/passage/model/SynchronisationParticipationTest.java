package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.ResultatEcriture;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.FuseauDuPoint;
import fr.univ_amu.iut.commun.model.InfosPoint;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.time.LocalDateTime;
import java.util.List;
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
    private static final String CARRE = "130711";

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

    @Mock
    FenetreObserveeNuit fenetreObservee;

    @Mock
    ReleveDeParticipation releve;

    private SynchronisationParticipation sync;

    @BeforeEach
    void preparer() {
        sync = new SynchronisationParticipation(
                client,
                liens,
                passageDao,
                materielDao,
                enregistreurDao,
                referentielPoint,
                fenetreObservee,
                // Aucune commune resolue : repli metropole, soit le comportement d'avant #3442.
                new FuseauDuPoint(idPoint -> Optional.empty()),
                releve);
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
        // Depuis #3854, le refus interroge la plateforme pour choisir son conseil. Le cas éprouvé ici est
        // le refus lui-même : quelle que soit la réponse, rien n'est créé.
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.injoignable("hors ligne"));

        assertThatThrownBy(() -> sync.creerPour(42L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("non rattaché");
        verify(client, never()).creerParticipation(anyString(), any());
    }

    @Test
    @DisplayName("#3854 : le carré existe en Point Fixe → le refus renvoie vers la RÉCUPÉRATION")
    void refus_conseille_de_recuperer_le_carre() {
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_SITE, "7")).thenReturn(Optional.empty());
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-" + CARRE, true))));

        assertThatThrownBy(() -> sync.creerPour(42L))
                .isInstanceOf(RegleMetierException.class)
                // Le conseil d'avant - « synchronisez vos sites » - ne ramène PAS un carré sans
                // participation : c'est la mesure de #3669, et c'est le parcours qui a produit #3458.
                .hasMessageNotContainingAny("synchronisez")
                .hasMessageContaining("Récupérer ce carré");
        verify(client, never()).creerParticipation(anyString(), any());
    }

    @Test
    @DisplayName("#3854 : le carré n'est pas en Point Fixe → le refus renvoie vers le PORTAIL")
    void refus_renvoie_au_portail_quand_le_carre_n_y_est_pas() {
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_SITE, "7")).thenReturn(Optional.empty());
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.succes(List.of()));

        assertThatThrownBy(() -> sync.creerPour(42L))
                .isInstanceOf(RegleMetierException.class)
                // Récupérer serait un conseil impossible à suivre : il n'y a rien à récupérer.
                .hasMessageNotContainingAny("Récupérer ce carré")
                .hasMessageContaining("portail");
    }

    @Test
    @DisplayName("#3854 : plateforme injoignable → le refus le DIT, sans affirmer que le carré manque")
    void refus_ne_tranche_pas_quand_la_plateforme_est_injoignable() {
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_SITE, "7")).thenReturn(Optional.empty());
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.injoignable("timeout"));

        assertThatThrownBy(() -> sync.creerPour(42L))
                .isInstanceOf(RegleMetierException.class)
                // Ni « récupérez-le » ni « il n'existe pas » : on ne sait pas (ADR 3458).
                .hasMessageNotContainingAny("Récupérer ce carré", "portail")
                .hasMessageContaining("n'a pas pu");
    }

    @Test
    @DisplayName("#3854 : un dépôt qui aboutit n'interroge JAMAIS la recherche de carré")
    void le_chemin_nominal_ne_cherche_aucun_carre() {
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_SITE, "7")).thenReturn(Optional.of(OBJECTID_SITE));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.creerParticipation(eq(OBJECTID_SITE), any())).thenReturn(ResultatEcriture.reussie("part-1"));

        sync.creerPour(42L);

        // Le conseil se paie sur le chemin d'ÉCHEC seulement : sinon chaque dépôt réussi porterait une
        // requête de plus, pour un message que personne ne lira.
        verify(client, never()).chercherCarre(anyString());
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
        when(fenetreObservee.pour(42L)).thenReturn(Optional.empty()); // squelette : rien a prouver
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(client.participation("part-1")).thenReturn(ReponseApi.succes(detail("e-frais")));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.modifierParticipation(eq("part-1"), eq("e-frais"), any()))
                .thenReturn(ResultatEcriture.reussie("part-1"));

        EnvoiParticipation envoi = sync.pousserVers(42L);

        assertThat(envoi).isInstanceOf(EnvoiParticipation.Ecrit.class);
        assertThat(((EnvoiParticipation.Ecrit) envoi).ecriture().id()).contains("part-1");
        verify(client).modifierParticipation(eq("part-1"), eq("e-frais"), any());
    }

    @Test
    @DisplayName("#4552 : un champ que nous écrivons a bougé entre la lecture et l'envoi → aucun PATCH")
    void pousser_vers_renonce_si_le_distant_a_bouge() {
        when(fenetreObservee.pour(42L)).thenReturn(Optional.empty()); // squelette : rien a prouver
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        // Permissif : le chemin du refus ne construit plus le corps, donc ce doublage ne sert pas quand
        // la garde tient. Il sert quand elle ne tient plus, pour que l'echec nomme la garde et non un mock.
        lenient().when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        // Un autre poste a saisi la meteo entre notre lecture et notre envoi : ecrire par-dessus
        // effacerait sa saisie. C'est un champ que le PATCH emet, donc un vrai conflit (#4603).
        when(client.participation("part-1"))
                .thenReturn(ReponseApi.succes(detail("e-lu")))
                .thenReturn(ReponseApi.succes(detailAvecMeteo("e-bouge", new MeteoDepot("FORT", "75-100"))));
        // Permissif : ce doublage sert a laisser le flux aller jusqu'au bout tant que la garde n'existe
        // pas. Une fois la garde posee, plus rien ne l'appelle, et c'est exactement ce qu'on verifie.
        lenient()
                .when(client.modifierParticipation(anyString(), anyString(), any()))
                .thenReturn(ResultatEcriture.reussie("part-1"));

        sync.pousserVers(42L);

        verify(client, never()).modifierParticipation(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("#4552 témoin : la météo a bougé, on renonce, et ce n'est pas un refus de la plateforme")
    void pousser_vers_renoncement_est_un_etat_distinct() {
        when(fenetreObservee.pour(42L)).thenReturn(Optional.empty()); // squelette : rien a prouver
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        // Permissif : le chemin du refus ne construit plus le corps, donc ce doublage ne sert pas quand
        // la garde tient. Il sert quand elle ne tient plus, pour que l'echec nomme la garde et non un mock.
        lenient().when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.participation("part-1"))
                .thenReturn(ReponseApi.succes(detail("e-lu")))
                .thenReturn(ReponseApi.succes(detailAvecMeteo("e-bouge", new MeteoDepot("FORT", "75-100"))));
        lenient()
                .when(client.modifierParticipation(anyString(), anyString(), any()))
                .thenReturn(ResultatEcriture.reussie("part-1"));

        EnvoiParticipation envoi = sync.pousserVers(42L);

        assertThat(envoi).isInstanceOf(EnvoiParticipation.ModifieEntreTemps.class);
    }

    @Test
    @DisplayName("#4603 : seul le traitement a bougé → l'envoi part, il n'écraserait rien")
    void pousser_vers_envoie_quand_seul_le_traitement_a_bouge() {
        when(fenetreObservee.pour(42L)).thenReturn(Optional.empty()); // squelette : rien a prouver
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        // L'ouvrier d'analyse a avance entre nos deux lectures : l'etag bouge, et aucun champ que nous
        // ecrivons ne bouge. Refuser ici priverait l'utilisateur d'un envoi qui n'efface rien.
        when(client.participation("part-1"))
                .thenReturn(ReponseApi.succes(detail("e-lu")))
                .thenReturn(ReponseApi.succes(detailAvecTraitement("e-bouge", traitementEnCours())));
        lenient()
                .when(client.modifierParticipation(anyString(), anyString(), any()))
                .thenReturn(ResultatEcriture.reussie("part-1"));

        EnvoiParticipation envoi = sync.pousserVers(42L);

        assertThat(envoi).isInstanceOf(EnvoiParticipation.Ecrit.class);
        verify(client).modifierParticipation(eq("part-1"), anyString(), any());
    }

    @Test
    @DisplayName("#4603 : la relecture échoue → refus dur, et surtout aucun PATCH à l'aveugle")
    void pousser_vers_refuse_si_la_relecture_echoue() {
        when(fenetreObservee.pour(42L)).thenReturn(Optional.empty()); // squelette : rien a prouver
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        // La coupure tombe ENTRE les deux lectures. Sans relecture, la garde ne sait rien : elle ne doit
        // surtout pas laisser partir l'ecriture au benefice du doute.
        when(client.participation("part-1"))
                .thenReturn(ReponseApi.succes(detail("e-lu")))
                .thenReturn(ReponseApi.injoignable("connexion perdue"));

        assertThatThrownBy(() -> sync.pousserVers(42L)).isInstanceOf(RegleMetierException.class);

        verify(client, never()).modifierParticipation(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("#4603 témoin : la configuration a bougé → on renonce, elle fait partie de ce qu'on écrit")
    void pousser_vers_renonce_quand_la_configuration_a_bouge() {
        when(fenetreObservee.pour(42L)).thenReturn(Optional.empty()); // squelette : rien a prouver
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        // Permissif : le chemin du refus ne construit plus le corps, donc ce doublage ne sert pas quand
        // la garde tient. Il sert quand elle ne tient plus, pour que l'echec nomme la garde et non un mock.
        lenient().when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.participation("part-1"))
                .thenReturn(ReponseApi.succes(detail("e-lu")))
                .thenReturn(ReponseApi.succes(detailAvecConfiguration("e-bouge", Map.of("micro1_type", "SMX"))));
        lenient()
                .when(client.modifierParticipation(anyString(), anyString(), any()))
                .thenReturn(ResultatEcriture.reussie("part-1"));

        EnvoiParticipation envoi = sync.pousserVers(42L);

        assertThat(envoi).isInstanceOf(EnvoiParticipation.ModifieEntreTemps.class);
        verify(client, never()).modifierParticipation(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("#1878 : l'envoi part de ce que la nuit PROUVE, et répare ses heures dérivées en base")
    void pousser_vers_realigne_sur_les_preuves() {
        armerLienEtDistant(detail("part-1", "Z41", "2026-07-03T19:00:00+00:00"));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.modifierParticipation(any(), any(), any())).thenReturn(ResultatEcriture.reussie());
        // Le passage déclare 21:00 → 05:00 ; ses enregistrements attestent 21:30 → 06:15. Ce sont EUX
        // qui font foi.
        when(fenetreObservee.pour(42L))
                .thenReturn(Optional.of(new FenetreObserveeNuit.Bornes(
                        LocalDateTime.of(2026, 7, 3, 21, 30), LocalDateTime.of(2026, 7, 4, 6, 15))));

        sync.pousserVers(42L);

        // Réparé en base : sans cela l'IHM afficherait encore 15:00 pendant que la plateforme afficherait
        // la vérité - l'incohérence serait déplacée, pas résolue.
        ArgumentCaptor<Passage> ecrit = ArgumentCaptor.forClass(Passage.class);
        verify(passageDao).update(ecrit.capture());
        assertThat(ecrit.getValue().heureDebut()).isEqualTo("21:30:00");
        assertThat(ecrit.getValue().heureFin()).isEqualTo("06:15:00");
        assertThat(ecrit.getValue().dateEnregistrement()).isEqualTo("2026-07-03");
    }

    @Test
    @DisplayName("#1878 : des heures déjà conformes aux preuves n'entraînent AUCUNE écriture")
    void pousser_vers_ne_reecrit_pas_sans_derive() {
        armerLienEtDistant(detail("part-1", "Z41", "2026-07-03T19:00:00+00:00"));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.modifierParticipation(any(), any(), any())).thenReturn(ResultatEcriture.reussie());
        // Les preuves confirment ce que le passage déclare (la fixture pose 21:00:00 → 05:00:00).
        when(fenetreObservee.pour(42L))
                .thenReturn(Optional.of(new FenetreObserveeNuit.Bornes(
                        LocalDateTime.of(2026, 7, 3, 21, 0), LocalDateTime.of(2026, 7, 4, 5, 0))));

        sync.pousserVers(42L);

        verify(passageDao, never()).update(any());
    }

    @Test
    @DisplayName("#1878 : une nuit squelette n'a aucune preuve → ses heures déclarées sont respectées")
    void pousser_vers_squelette_intouche() {
        armerLienEtDistant(detail("part-1", "Z41", "2026-07-03T19:00:00+00:00"));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.modifierParticipation(any(), any(), any())).thenReturn(ResultatEcriture.reussie());
        when(fenetreObservee.pour(42L)).thenReturn(Optional.empty());

        sync.pousserVers(42L);

        // On ne fabrique rien : sans preuve, la valeur déclarée reste la seule dont on dispose.
        verify(passageDao, never()).update(any());
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
    @DisplayName("#4706 : tirer NOTE ce que la plateforme portait, pour servir de base au conflit")
    void tirer_depuis_note_le_releve() {
        when(passageDao.findById(42L)).thenReturn(Optional.of(passage(null)));
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        ParticipationDetail distant = detail("e-lu");
        when(client.participation("part-1")).thenReturn(ReponseApi.succes(distant));

        sync.tirerDepuis(42L);

        // Sans base, une modification faite ici ne se distingue pas d'une modification faite la-bas.
        verify(releve).noter(42L, "part-1", distant);
    }

    @Test
    @DisplayName("#4706 : une écriture ACCEPTÉE note la base, sur l'état qu'on vient de valider")
    void pousser_vers_note_apres_une_ecriture_acceptee() {
        when(fenetreObservee.pour(42L)).thenReturn(Optional.empty()); // squelette : rien a prouver
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        ParticipationDetail juste = detail("e-lu");
        when(client.participation("part-1")).thenReturn(ReponseApi.succes(juste));
        when(client.modifierParticipation(anyString(), anyString(), any()))
                .thenReturn(ResultatEcriture.reussie("part-1"));

        sync.pousserVers(42L);

        // C'est la RELECTURE qu'on note : c'est l'etat contre lequel on a valide, et celui qu'on a envoye.
        verify(releve).noter(42L, "part-1", juste);
    }

    @Test
    @DisplayName("#4706 : une écriture REFUSÉE ne note rien, la base décrirait un état qui n'existe pas")
    void pousser_vers_ne_note_rien_si_refuse() {
        when(fenetreObservee.pour(42L)).thenReturn(Optional.empty()); // squelette : rien a prouver
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(materielDao.pour(42L)).thenReturn(MaterielMicro.vide(42L));
        when(client.participation("part-1")).thenReturn(ReponseApi.succes(detail("e-lu")));
        when(client.modifierParticipation(anyString(), anyString(), any()))
                .thenReturn(ResultatEcriture.echouee("422 champ invalide"));

        sync.pousserVers(42L);

        verify(releve, never()).noter(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("#4706 : un RENONCEMENT ne note rien, sinon il effacerait le conflit qu'il vient de voir")
    void pousser_vers_ne_note_rien_quand_il_renonce() {
        when(fenetreObservee.pour(42L)).thenReturn(Optional.empty()); // squelette : rien a prouver
        armerPassageEtPoint();
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of("part-1"));
        when(client.participation("part-1"))
                .thenReturn(ReponseApi.succes(detail("e-lu")))
                .thenReturn(ReponseApi.succes(detailAvecMeteo("e-bouge", new MeteoDepot("FORT", "75-100"))));

        sync.pousserVers(42L);

        // Noter ici rendrait la base EGALE a leur valeur. La tentative suivante conclurait « eux n'ont
        // rien change » et ecraserait leur travail en silence : le conflit se resoudrait tout seul,
        // en notre faveur, sans que personne l'ait tranche.
        verify(releve, never()).noter(anyLong(), anyString(), any());
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
                .as("le bouton « Récupérer depuis Vigie-Chiro » rattrape enfin l'enregistreur")
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
        when(referentielPoint.pour(7L)).thenReturn(Optional.of(new InfosPoint("Z41", 7L, CARRE)));
    }

    private static Traitement traitementEnCours() {
        return new Traitement(EtatTraitement.EN_COURS, null, "2026-07-04T05:00:00+00:00", null, null, null);
    }

    /// Le meme detail que [#detail(String)], dont seul l'etat du traitement differe : c'est ce que
    /// l'ouvrier d'analyse fait bouger tout seul, sans toucher un champ que nous ecrivons.
    private static ParticipationDetail detailAvecTraitement(String etag, Traitement traitement) {
        ParticipationDetail modele = detail(etag);
        return new ParticipationDetail(
                modele.id(),
                etag,
                modele.point(),
                modele.dateDebut(),
                modele.dateFin(),
                modele.meteo(),
                modele.configuration(),
                traitement);
    }

    /// Le meme detail que [#detail(String)], dont seule la meteo differe : un champ que le `PATCH`
    /// ecrit, donc un vrai conflit.
    private static ParticipationDetail detailAvecMeteo(String etag, MeteoDepot meteo) {
        ParticipationDetail modele = detail(etag);
        return new ParticipationDetail(
                modele.id(),
                etag,
                modele.point(),
                modele.dateDebut(),
                modele.dateFin(),
                meteo,
                modele.configuration(),
                modele.traitement());
    }

    /// Le meme detail que [#detail(String)], dont seule la configuration differe.
    private static ParticipationDetail detailAvecConfiguration(String etag, Map<String, String> configuration) {
        ParticipationDetail modele = detail(etag);
        return new ParticipationDetail(
                modele.id(),
                etag,
                modele.point(),
                modele.dateDebut(),
                modele.dateFin(),
                modele.meteo(),
                configuration,
                modele.traitement());
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
                serie,
                null);
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
                "1997632",
                null);
    }
}
