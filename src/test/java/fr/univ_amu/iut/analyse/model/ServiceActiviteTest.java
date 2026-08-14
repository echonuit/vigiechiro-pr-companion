package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
import fr.univ_amu.iut.passage.model.NuitsOpportunistes;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// Vérifie que [ServiceActivite] projette les observations d'un passage en [ContactHoraire] (taxon
/// retenu, écart des pseudo-taxons) et délègue la fenêtre nocturne à [PlageNuitPassage].
class ServiceActiviteTest {

    private static final long PASSAGE = 7L;
    private static final LocalDateTime HEURE = LocalDateTime.of(2026, 6, 20, 22, 30);

    private final ProjectionsAudioDao projections = mock(ProjectionsAudioDao.class);
    private final PlageNuitPassage plageNuitPassage = mock(PlageNuitPassage.class);
    private final FenetreObserveeNuit fenetreObservee = mock(FenetreObserveeNuit.class);
    private final NuitsOpportunistes nuitsOpportunistes = mock(NuitsOpportunistes.class);

    private final ServiceActivite service =
            new ServiceActivite(projections, plageNuitPassage, fenetreObservee, nuitsOpportunistes);

    @Test
    void mappe_le_taxon_retenu_avec_l_observateur_prioritaire() {
        when(projections.lignesAudioDuPassage(PASSAGE))
                .thenReturn(List.of(ligne("PIPKUH", "PIPPIP", "Pipistrelle de Kuhl", "Chiroptères", HEURE)));

        assertThat(service.contactsDuPassage(PASSAGE))
                .as("taxon retenu (observateur prioritaire) et contexte commune/carré/point/passage/site portés")
                .containsExactly(new ContactHoraire(
                        "PIPKUH",
                        "Pipistrelle de Kuhl",
                        "Chiroptères",
                        HEURE,
                        "Ahetze",
                        "640380",
                        "A1",
                        PASSAGE,
                        "Mon site"));
    }

    @Test
    void reporte_le_nom_du_site_ou_null_quand_il_n_est_pas_nomme() {
        // #3175 : le nom convivial était disponible sur la ligne source et simplement pas reporté, comme
        // la commune avant #2967. Il est facultatif : son absence est un état normal, pas une anomalie.
        when(projections.lignesAudioDuPassage(PASSAGE))
                .thenReturn(List.of(ligne("PIPKUH", "PIPPIP", "Pipistrelle de Kuhl", "Chiroptères", HEURE, null)));

        assertThat(service.contactsDuPassage(PASSAGE))
                .extracting(ContactHoraire::nomSite)
                .as("un carré que l'utilisateur n'a pas nommé ne porte pas de nom, et cela ne fait pas échouer")
                .containsExactly((String) null);
    }

    @Test
    void retient_le_taxon_tadarida_faute_d_observateur() {
        when(projections.lignesAudioDuPassage(PASSAGE))
                .thenReturn(List.of(ligne(null, "PIPPIP", "Pipistrelle commune", "Chiroptères", HEURE)));

        assertThat(service.contactsDuPassage(PASSAGE))
                .extracting(ContactHoraire::taxon)
                .containsExactly("PIPPIP");
    }

    @Test
    void ecarte_les_pseudo_taxons_noise_et_piaf() {
        when(projections.lignesAudioDuPassage(PASSAGE))
                .thenReturn(List.of(
                        ligne(null, "noise", null, null, HEURE),
                        ligne(null, "piaf", null, "Oiseaux", HEURE),
                        ligne("PIPKUH", "PIPPIP", "Pipistrelle de Kuhl", "Chiroptères", HEURE)));

        assertThat(service.contactsDuPassage(PASSAGE))
                .as("bruit et oiseau générique ne sont pas des espèces de la courbe")
                .extracting(ContactHoraire::taxon)
                .containsExactly("PIPKUH");
    }

    @Test
    void garde_les_sequences_non_identifiees_pour_l_agregation() {
        when(projections.lignesAudioDuPassage(PASSAGE)).thenReturn(List.of(ligne(null, null, null, null, HEURE)));

        assertThat(service.contactsDuPassage(PASSAGE))
                .as("une séquence sans taxon reste, c'est l'agrégation qui décide de l'écarter")
                .singleElement()
                .extracting(ContactHoraire::taxon)
                .isNull();
    }

    @Test
    void delegue_la_plage_nuit_au_calcul_du_passage() {
        PlageNuit nuit = new PlageNuit(21, 6);
        when(plageNuitPassage.pour(PASSAGE)).thenReturn(Optional.of(nuit));

        assertThat(service.plageNuit(PASSAGE)).contains(nuit);
    }

    @Test
    void plage_nuit_vide_quand_le_calcul_ne_conclut_pas() {
        when(plageNuitPassage.pour(PASSAGE)).thenReturn(Optional.empty());

        assertThat(service.plageNuit(PASSAGE)).isEmpty();
    }

    @Test
    void charge_tous_les_contacts_de_l_utilisateur() {
        when(projections.lignesAudioDeLUtilisateur("u-1"))
                .thenReturn(List.of(
                        ligne("PIPKUH", "PIPPIP", "Pipistrelle de Kuhl", "Chiroptères", HEURE),
                        ligne(null, "noise", null, null, HEURE)));

        assertThat(service.contactsDeLUtilisateur("u-1"))
                .as("tous les passages de l'utilisateur, pseudo-taxons écartés comme pour un passage")
                .extracting(ContactHoraire::taxon)
                .containsExactly("PIPKUH");
    }

    @Test
    void delegue_la_fenetre_enregistree_a_fenetre_observee() {
        FenetreObserveeNuit.Bornes bornes = new FenetreObserveeNuit.Bornes(
                LocalDateTime.of(2026, 6, 20, 21, 0), LocalDateTime.of(2026, 6, 21, 6, 30));
        when(fenetreObservee.pour(PASSAGE)).thenReturn(Optional.of(bornes));

        assertThat(service.fenetreEnregistree(PASSAGE)).contains(bornes);
    }

    @Test
    void fenetre_enregistree_vide_quand_la_nuit_n_a_laisse_aucune_trace() {
        when(fenetreObservee.pour(PASSAGE)).thenReturn(Optional.empty());

        assertThat(service.fenetreEnregistree(PASSAGE)).isEmpty();
    }

    @Test
    void delegue_les_nuits_opportunistes_a_leur_source() {
        when(nuitsOpportunistes.identifiants()).thenReturn(java.util.Set.of(PASSAGE, 8L));

        assertThat(service.nuitsOpportunistes())
                .as("un délégué renvoyant du vide par défaut ne dirait rien : il faut un contenu concret à retrouver")
                .containsExactlyInAnyOrder(PASSAGE, 8L);
    }

    private static LigneObservationAudio ligne(
            String taxonObservateur, String taxonTadarida, String nomEspece, String groupe, LocalDateTime heure) {
        return ligne(taxonObservateur, taxonTadarida, nomEspece, groupe, heure, "Mon site");
    }

    private static LigneObservationAudio ligne(
            String taxonObservateur,
            String taxonTadarida,
            String nomEspece,
            String groupe,
            LocalDateTime heure,
            String nomSite) {
        return new LigneObservationAudio(
                1L,
                10L,
                PASSAGE,
                1,
                "2026-06-20",
                "640380",
                "A1",
                nomSite,
                taxonTadarida,
                taxonTadarida == null ? null : 0.9,
                taxonObservateur,
                taxonObservateur == null ? null : 0.95,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                45,
                nomEspece,
                nomEspece,
                null,
                groupe,
                "PaRec_20260620_000.wav",
                0.20,
                0.32,
                heure,
                false,
                null,
                null,
                null,
                null,
                0,
                "Ahetze");
    }
}
