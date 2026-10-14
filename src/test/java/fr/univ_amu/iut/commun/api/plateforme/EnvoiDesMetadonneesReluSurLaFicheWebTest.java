package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.model.FuseauDuPoint;
import fr.univ_amu.iut.commun.model.InfosPoint;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.ReferentielPoint;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.EnvoiParticipation;
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.MeteoPassage;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveDeParticipation;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.recette.Attente;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Ce que Companion envoie des métadonnées d'un passage se relit sur la fiche web de Vigie-Chiro (#6062,
/// sous-chantier #5645).
///
/// Les trois défauts d'écriture de l'ADR 0020 réussissaient en `200` : le numéro de série partait sous une
/// clé que le formulaire web ne lit pas (#1844), la sentinelle « INCONNU » était publiée comme une donnée
/// (#1828), et l'envoi effaçait ce que le formulaire gère sans que Companion le modélise. Seule la fiche
/// web les montrait, et aucun test ne la lisait.
///
/// L'envoi passe par [SynchronisationParticipation#pousserVers], le chemin que l'écran, la commande et
/// le rattrapage empruntent, avec un vrai client pointé sur la plateforme de test. Seul le côté local est
/// bouchonné : le passage, son matériel et son relevé. Tout ce qui part de là jusqu'au serveur est le code
/// de production.
///
/// Ce banc ne joue pas le bouton « Envoyer » de l'écran : aucun job de demande ne joue un scénario d'écran
/// sur la plateforme de test. Il ne cite pas non plus S4-70, S4-71 ni S4-72 par `@CasDeRecette`, qui ferait
/// attendre un clip au tournage : les cas gardent leur marqueur « hors-portée », dont le motif le nomme.
///
/// Chaque cas écrit sur une participation à lui, que l'état de départ déclare : deux classes qui
/// écriraient la même se disputeraient son `_etag`, et le second cas relirait le numéro du premier.
@Tag("plateforme-de-test")
@ExtendWith(MockitoExtension.class)
class EnvoiDesMetadonneesReluSurLaFicheWebTest {

    private static final long ID_PASSAGE = 42L;

    private static final long ID_POINT = 7L;

    private static final String NUMERO_DE_SERIE = "1997632";

    private static final String CHAMP_DU_NUMERO_DE_SERIE = "configuration.detecteur_enregistreur_numero_serie";

    private static final String CHAMP_DE_LA_TEMPERATURE_DE_DEBUT = "meteo.temperature_debut";

    private static NavigateurDeLaPlateforme navigateur;

    @Mock
    LienVigieChiroDao liens;

    @Mock
    PassageDao passageDao;

    @Mock
    MaterielMicroDao materielDao;

    @Mock
    EnregistreurDao enregistreurDao;

    @Mock
    ReferentielPoint referentielPoint;

    @Mock
    FenetreObserveeNuit fenetreObservee;

    @Mock
    ReleveDeParticipation releve;

    @BeforeAll
    static void ouvrirLeNavigateur() {
        navigateur = NavigateurDeLaPlateforme.connecteComme("observatrice");
    }

    @AfterAll
    static void fermerLeNavigateur() {
        if (navigateur != null) {
            navigateur.close();
        }
    }

    @Test
    @DisplayName("#6062 : le numéro de série se relit dans le champ du formulaire, et les températures sur la fiche")
    void le_numero_de_serie_et_les_temperatures_se_relisent_sur_la_fiche() {
        String participation = PlateformeDeTest.acces().id("participations:nuit-a-ecrire");

        envoyer(participation, passage("2026-06-17", NUMERO_DE_SERIE));

        navigateur.ouvrir("participations/" + participation + "/edition");
        attendreLeFormulaire();
        assertThat(navigateur.valeurDuChamp(CHAMP_DU_NUMERO_DE_SERIE))
                .as("le formulaire web ne lit que la clé `detecteur_enregistreur_numero_serie` : sous la clé"
                        + " historique, le numéro arrivait sur la plateforme et restait invisible ici (#1844)")
                .isEqualTo(NUMERO_DE_SERIE);

        navigateur.ouvrir("participations/" + participation);
        attendreSurLaFiche("°C au début de la participation");
        assertThat(navigateur.texteDe("body"))
                .as("les deux températures, arrondies au degré comme le serveur les type (#1844)")
                .contains("12°C au début de la participation")
                .contains("8°C à la fin de la participation");
    }

    @Test
    @DisplayName("#6062 : un enregistreur inconnu ne publie pas sa sentinelle, le champ du formulaire reste vide")
    void un_enregistreur_inconnu_laisse_le_champ_vide() {
        String participation = PlateformeDeTest.acces().id("participations:nuit-sans-enregistreur");

        envoyer(participation, passage("2026-06-18", Enregistreur.INCONNU));

        navigateur.ouvrir("participations/" + participation + "/edition");
        attendreLeFormulaire();
        assertThat(navigateur.valeurDuChamp(CHAMP_DU_NUMERO_DE_SERIE))
                .as("« INCONNU » n'est pas un numéro : le publier fabriquerait une donnée que le prochain"
                        + " poste relirait comme vraie (#1828)")
                .isEmpty();

        navigateur.ouvrir("participations/" + participation);
        // Les températures prouvent que l'envoi est arrivé. Le type ne le prouve plus : cette nuit n'a pas
        // de paramètres d'acquisition, son appareil est inconnu du poste, et rien ne s'en publie (#6233).
        attendreSurLaFiche("°C au début de la participation");
        assertThat(navigateur.texteDe("body"))
                .as("ni numéro de série ni type d'appareil : le poste ne sait pas quel enregistreur a produit"
                        + " la nuit, et l'affirmer serait inventer (#1828, #6233)")
                .doesNotContain("Numéro de série du détecteur enregistreur")
                .doesNotContain("Type du détecteur enregistreur");
    }

    @Test
    @DisplayName("#6063 : un envoi n'efface pas ce que la fiche portait, et que Companion ne modélise pas")
    void un_envoi_n_efface_pas_ce_que_la_fiche_portait() {
        String participation = PlateformeDeTest.acces().id("participations:nuit-preparee-sur-le-web");

        envoyer(participation, passage("2026-06-19", NUMERO_DE_SERIE));

        navigateur.ouvrir("participations/" + participation);
        // Le numéro de l'enregistreur vient de l'envoi : il dit que la fiche relue est celle d'après.
        attendreSurLaFiche("Numéro de série du détecteur enregistreur : " + NUMERO_DE_SERIE);
        assertThat(navigateur.texteDe("body"))
                .as("le PATCH remplace la configuration entière : partie de la seule configuration locale,"
                        + " elle effaçait le micro et le canal que le formulaire web gère (ADR 0020)")
                .contains("Numéro de série du micro 0 : SMU-0417")
                .contains("Canal en expansion de temps : DROITE");
    }

    /// Envoie les métadonnées du passage vers la participation, par le chemin de production.
    private void envoyer(String participation, Passage passage) {
        PlateformeDeTest.Acces acces = PlateformeDeTest.acces();
        ClientVigieChiro client =
                new ClientVigieChiro(acces.urlDeBase(), () -> Optional.of(acces.jeton("observatrice")));
        when(passageDao.findById(ID_PASSAGE)).thenReturn(Optional.of(passage));
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(ID_PASSAGE)))
                .thenReturn(Optional.of(participation));
        when(referentielPoint.pour(ID_POINT)).thenReturn(Optional.of(new InfosPoint("Z1", 1L, "130711")));
        when(materielDao.pour(ID_PASSAGE)).thenReturn(MaterielMicro.vide(ID_PASSAGE));
        when(fenetreObservee.pour(ID_PASSAGE)).thenReturn(Optional.empty());
        when(releve.base(ID_PASSAGE)).thenReturn(Optional.empty());
        SynchronisationParticipation synchronisation = new SynchronisationParticipation(
                client,
                liens,
                passageDao,
                materielDao,
                enregistreurDao,
                referentielPoint,
                fenetreObservee,
                new FuseauDuPoint(idPoint -> Optional.empty()),
                releve);

        EnvoiParticipation envoi = synchronisation.pousserVers(ID_PASSAGE);

        assertThat(envoi)
                .as("l'envoi doit être écrit, et non abandonné pour une modification entre-temps")
                .isInstanceOf(EnvoiParticipation.Ecrit.class);
        EnvoiParticipation.Ecrit ecrit = (EnvoiParticipation.Ecrit) envoi;
        assertThat(ecrit.ecriture().estReussie())
                .as(
                        "la plateforme doit accepter l'écriture : %s",
                        ecrit.ecriture().echec())
                .isTrue();
    }

    /// Un passage de la nuit donnée, aux deux températures saisies, enregistré par cet appareil.
    private static Passage passage(String date, String enregistreur) {
        String meteo = MeteoPassage.definirReleve(null, new MeteoReleve(12.4, 7.6, null, null));
        return new Passage(
                ID_PASSAGE,
                1,
                2026,
                date,
                "21:00:00",
                "06:00:00",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                meteo,
                null,
                ID_POINT,
                enregistreur,
                null);
    }

    /// Le front dessine le formulaire après ses requêtes : la température de début, que les deux cas
    /// envoient, dit que la participation y est arrivée.
    private static void attendreLeFormulaire() {
        Attente.que(
                () -> "12".equals(navigateur.valeurDuChamp(CHAMP_DE_LA_TEMPERATURE_DE_DEBUT)),
                () -> "le formulaire d'édition n'a pas reçu la participation. La page disait : "
                        + navigateur.texteDe("body"),
                NavigateurDeLaPlateforme.DELAI_D_UNE_PAGE_MS);
    }

    private static void attendreSurLaFiche(String texte) {
        Attente.que(
                () -> navigateur.texteDe("body").contains(texte),
                () -> "« " + texte + " » n'a pas paru sur la fiche. La page disait : " + navigateur.texteDe("body"),
                NavigateurDeLaPlateforme.DELAI_D_UNE_PAGE_MS);
    }
}
