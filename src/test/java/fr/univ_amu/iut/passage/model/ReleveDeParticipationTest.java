package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.ReleveParticipation;
import fr.univ_amu.iut.commun.model.dao.ReleveParticipationDao;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Le geste qui **note** ce que la plateforme portait ([ReleveDeParticipation], #4706).
///
/// Il ne retient que les champs que le `PATCH` écrit : comparer un champ qu'on n'envoie pas ne
/// servirait à rien, et le relevé n'a pas vocation à recopier la participation.
@ExtendWith(MockitoExtension.class)
class ReleveDeParticipationTest {

    @Mock
    ReleveParticipationDao releves;

    @Test
    @DisplayName("#4706 : noter retient les champs émis, l'objectid, et l'heure de NOTRE lecture")
    void noter_retient_les_champs_emis() {
        ReleveDeParticipation releve =
                new ReleveDeParticipation(releves, new HorlogeFigee(LocalDateTime.of(2026, 8, 29, 9, 30)));

        releve.noter(42L, "part-1", distant());

        ArgumentCaptor<ReleveParticipation> pris = ArgumentCaptor.forClass(ReleveParticipation.class);
        verify(releves).enregistrer(pris.capture());
        ReleveParticipation note = pris.getValue();
        assertThat(note.passageId()).isEqualTo(42L);
        assertThat(note.participationId()).isEqualTo("part-1");
        assertThat(note.dateDebut()).isEqualTo("2026-07-03T19:00:00+00:00");
        assertThat(note.meteo()).isEqualTo(new MeteoDepot("FAIBLE", "0-25"));
        // Le dictionnaire distant est garde ENTIER, cles inconnues comprises : sans elles, le releve
        // ne pourrait pas dire qu'un champ hors de notre portee a bouge.
        assertThat(note.configuration()).containsEntry("micro1_type", "SMX");
        assertThat(note.releveLe()).isEqualTo("2026-08-29T09:30");
    }

    @Test
    @DisplayName("#4707 : base lit le relevé du passage, et rend vide quand il n'y en a pas")
    void base_lit_le_releve() {
        ReleveDeParticipation releve =
                new ReleveDeParticipation(releves, new HorlogeFigee(LocalDateTime.of(2026, 8, 29, 9, 30)));
        ReleveParticipation note = new ReleveParticipation(
                42L, "part-1", null, null, new MeteoDepot("FAIBLE", "0-25"), Map.of(), "2026-08-29T09:00:00");
        when(releves.pour(42L)).thenReturn(Optional.of(note));
        when(releves.pour(7L)).thenReturn(Optional.empty());

        assertThat(releve.base(42L)).contains(note);
        // Un vide n'est pas une anomalie : une nuit anterieure a la migration n'a pas de releve.
        assertThat(releve.base(7L)).isEmpty();
    }

    private static ParticipationDetail distant() {
        return new ParticipationDetail(
                "part-1",
                "e-lu",
                "Z41",
                "2026-07-03T19:00:00+00:00",
                "2026-07-04T04:00:00+00:00",
                new MeteoDepot("FAIBLE", "0-25"),
                Map.of("micro0_type", "ICS", "micro1_type", "SMX"),
                Traitement.absent());
    }
}
