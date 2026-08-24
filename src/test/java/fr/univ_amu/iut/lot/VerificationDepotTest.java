package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.BilanVerification;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.VerificationDepot;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Vérification a posteriori d'un dépôt** ([VerificationDepot], #1132) sur collaborateurs mockés
/// (aucun réseau ni base) : appariement du plan local avec le journal de traitement (WAV nommés par
/// TadaridaD, ZIP nommés à l'extraction) et les titres des `donnees` (sans extension), refus durs
/// quand il n'y a rien à vérifier. Extrait de journal calqué sur le réel (participation `6a4961f5…`).
class VerificationDepotTest {

    private static final String JOURNAL = """
            [{'level': 'info', 'message': 'Extracting Car-1.zip'}, {'level': 'info', 'message': \
            "Archive contained: {'application/zip': 1, 'audio/wav': 231}"}, {'level': 'info', \
            'message': ' ---- TadaridaD ----\\nDeb:seq_000.wav :  r:.'}]\
            """;

    private final SynchronisationParticipation participations = mock(SynchronisationParticipation.class);
    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final DepotUniteDao depotUnites = mock(DepotUniteDao.class);
    private VerificationDepot verification;

    @BeforeEach
    void preparer() {
        // Par défaut : le serveur répond et n'a encore aucune donnée (Mockito ne fabrique pas de
        // ReponseApi tout seul, contrairement aux List d'avant #1284).
        when(client.donnees(org.mockito.ArgumentMatchers.anyString())).thenReturn(ReponseApi.succes(List.of()));
        when(client.journalTraitement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(ReponseApi.succes(Optional.empty()));
        verification = new VerificationDepot(participations, client, depotUnites);
    }

    @Test
    @DisplayName("WAV nommé par TadaridaD et ZIP nommé à l'extraction → retrouvés, bilan complet")
    void journal_apparie_wav_et_zip() {
        when(participations.participationDe(42L)).thenReturn(Optional.of("part-1"));
        when(depotUnites.parPassage(42L))
                .thenReturn(List.of(unite("seq_000.wav", TypeDepotUnite.WAV), unite("Car-1.zip", TypeDepotUnite.ZIP)));
        when(client.journalTraitement("part-1")).thenReturn(ReponseApi.succes(Optional.of(JOURNAL)));

        BilanVerification bilan = verification.verifier(42L);

        assertThat(bilan.estComplet()).isTrue();
        assertThat(bilan.journalDisponible()).isTrue();
        assertThat(bilan.retrouvees()).containsExactly("seq_000.wav", "Car-1.zip");
        assertThat(bilan.manquantes()).isEmpty();
    }

    @Test
    @DisplayName("journal indisponible (pas encore traité) : les WAV se rabattent sur les titres des donnees")
    void donnees_en_recoupement_sans_journal() {
        when(participations.participationDe(42L)).thenReturn(Optional.of("part-1"));
        when(depotUnites.parPassage(42L))
                .thenReturn(List.of(unite("seq_000.wav", TypeDepotUnite.WAV), unite("Car-1.zip", TypeDepotUnite.ZIP)));
        when(client.donnees("part-1"))
                .thenReturn(ReponseApi.succes(List.of(new DonneeVigieChiro("d1", "seq_000", List.of()))));

        BilanVerification bilan = verification.verifier(42L);

        assertThat(bilan.journalDisponible()).isFalse();
        assertThat(bilan.retrouvees()).containsExactly("seq_000.wav");
        assertThat(bilan.manquantes())
                .as("un ZIP n'est pas appariable sans journal")
                .containsExactly("Car-1.zip");
        assertThat(bilan.estComplet()).isFalse();
        assertThat(bilan.nombreDonnees()).isEqualTo(1);
    }

    @Test
    @DisplayName("#1284 : hors ligne → « vérification impossible », plus jamais un faux « tout manquant »")
    void verification_impossible_hors_ligne() {
        // Avant, une panne rendait journal vide + donnees vides : chaque unité passait « manquante »,
        // et un dépôt parfaitement sain ressemblait à un dépôt perdu.
        when(participations.participationDe(42L)).thenReturn(Optional.of("part-1"));
        when(depotUnites.parPassage(42L)).thenReturn(List.of(unite("seq_000.wav", TypeDepotUnite.WAV)));
        when(client.journalTraitement("part-1")).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));

        assertThatThrownBy(() -> verification.verifier(42L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Vérification impossible")
                .hasMessageContaining("injoignable");
    }

    @Test
    @DisplayName("passage non lié à une participation → refus dur, aucun appel réseau")
    void passage_non_lie() {
        when(participations.participationDe(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verification.verifier(42L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("aucune participation");
    }

    @Test
    @DisplayName("aucun plan de dépôt local (dépôt fait hors application) → refus dur explicite")
    void plan_local_vide() {
        when(participations.participationDe(42L)).thenReturn(Optional.of("part-1"));
        when(depotUnites.parPassage(42L)).thenReturn(List.of());

        assertThatThrownBy(() -> verification.verifier(42L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("plan de dépôt");
    }

    private static DepotUnite unite(String identifiant, TypeDepotUnite type) {
        return new DepotUnite(1L, 42L, identifiant, type, StatutDepotUnite.DEPOSE, "f", null, "2026-07-12");
    }
}
