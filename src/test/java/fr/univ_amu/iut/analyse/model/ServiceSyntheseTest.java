package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.ReferentielActivite;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie ce que [ServiceSyntheseReferentielTest] ne couvre pas : le **contenu** rendu par
/// `milieuxDisponibles()`, `nuitDe()` et `nuitDuPassage()` sur des données non vides, pas seulement leur
/// comportement à vide.
class ServiceSyntheseTest {

    private static LigneObservationAudio ligneAvec(LocalDateTime heureCapture) {
        return new LigneObservationAudio(
                1L,
                1L,
                1L,
                1,
                "2026-07-03",
                "130711",
                "Z41",
                "Test",
                "Pipkuh",
                0.9,
                null,
                null,
                StatutObservation.VALIDEE,
                false,
                null,
                45,
                "Pipistrelle de Kuhl",
                null,
                null,
                "Chiroptères",
                "seqA.wav",
                0.0,
                5.0,
                heureCapture,
                false,
                null,
                null,
                null,
                null,
                0,
                null);
    }

    @Test
    @DisplayName("milieuxDisponibles() rend les déclinaisons « habitat: » triées et sans préfixe")
    void milieux_disponibles_tries_sans_prefixe() throws IOException {
        ServiceSynthese service = new ServiceSynthese(
                mock(ProjectionsAudioDao.class),
                ReferentielActivite.lire(new StringReader(String.join(
                        "\n",
                        "Pipkuh;habitat:Prairie;ete;10;100;1000;400;Bonne",
                        "Pipkuh;habitat:Foret;ete;10;100;1000;400;Bonne",
                        "Pipkuh;national;toutes;5;50;500;9000;Tres bonne"))));

        assertThat(service.milieuxDisponibles())
                .as("triés alphabétiquement, préfixe retiré, et le national (pas un habitat) exclu")
                .containsExactly("Foret", "Prairie");
    }

    @Test
    @DisplayName("nuitDe() ignore les lignes sans horodatage et bascule à midi sur la première horodatée")
    void nuit_de_bascule_a_midi_sur_la_premiere_ligne_horodatee() {
        ServiceSynthese service = new ServiceSynthese(mock(ProjectionsAudioDao.class));

        LocalDate nuit = service.nuitDe(List.of(ligneAvec(null), ligneAvec(LocalDateTime.of(2026, 7, 4, 2, 0))));

        assertThat(nuit)
                .as("02 h du matin appartient à la nuit du soir précédent, pas à la date du jour")
                .isEqualTo(LocalDate.of(2026, 7, 3));
    }

    @Test
    @DisplayName("nuitDuPassage() délègue à nuitDe() sur les lignes lues en base pour ce passage")
    void nuit_du_passage_deduite_des_lignes_lues() {
        ProjectionsAudioDao dao = mock(ProjectionsAudioDao.class);
        when(dao.lignesAudioDuPassage(42L)).thenReturn(List.of(ligneAvec(LocalDateTime.of(2026, 7, 3, 22, 0))));
        ServiceSynthese service = new ServiceSynthese(dao);

        assertThat(service.nuitDuPassage(42L)).isEqualTo(LocalDate.of(2026, 7, 3));
    }
}
