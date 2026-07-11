package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.ResultatParticipation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// **Dépôt d'une nuit** ([DepotVigieChiro], #142) refondu en *upload seul* : la participation est déléguée à
/// [SynchronisationParticipation] (réutilisée si le passage y est déjà lié, créée en repli sinon). On teste
/// la réutilisation, le repli lazy, le dépôt partiel et les refus — sur passerelle + client mockés (aucun
/// réseau). Le contenu de la participation (dates/météo/config) est testé côté `passage`
/// (CorrespondanceParticipationTest / SynchronisationParticipationTest).
@ExtendWith(MockitoExtension.class)
class DepotVigieChiroTest {

    @Mock
    SynchronisationParticipation participations;

    @Mock
    ClientVigieChiro client;

    private DepotVigieChiro depot;

    @BeforeEach
    void préparer() {
        depot = new DepotVigieChiro(participations, client);
    }

    @Test
    @DisplayName("participation déjà liée → RÉUTILISÉE (pas de recréation), fichiers déposés")
    void depot_reutilise_la_participation_liee(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "Car130711-2026-Pass1-Z41_000.wav");
        Path b = fichier(dossier, "Car130711-2026-Pass1-Z41_001.wav");
        when(participations.participationDe(42L)).thenReturn(Optional.of("part-1"));
        armerUploadOk();

        BilanDepot bilan = depot.deposer(42L, List.of(a, b));

        assertThat(bilan.participationId()).isEqualTo("part-1");
        assertThat(bilan.deposees()).isEqualTo(2);
        assertThat(bilan.estComplet()).isTrue();
        verify(participations, never()).creerPour(anyLong());
    }

    @Test
    @DisplayName("aucune participation liée → CRÉÉE en repli (lazy), puis fichiers déposés")
    void depot_cree_la_participation_en_repli(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "a.wav");
        when(participations.participationDe(42L)).thenReturn(Optional.empty());
        when(participations.creerPour(42L)).thenReturn(ResultatParticipation.reussie("part-1"));
        armerUploadOk();

        BilanDepot bilan = depot.deposer(42L, List.of(a));

        assertThat(bilan.participationId()).isEqualTo("part-1");
        assertThat(bilan.deposees()).isEqualTo(1);
    }

    @Test
    @DisplayName("dépôt partiel : un fichier en échec est listé, les autres comptés, sans interrompre")
    void depot_partiel(@TempDir Path dossier) throws IOException {
        Path ok = fichier(dossier, "ok.wav");
        Path ko = fichier(dossier, "ko.wav");
        when(participations.participationDe(42L)).thenReturn(Optional.of("part-1"));
        when(client.creerFichier("ok.wav")).thenReturn(Optional.of(new FichierSigne("f", "https://s3/x")));
        when(client.creerFichier("ko.wav")).thenReturn(Optional.empty()); // déclaration refusée
        when(client.televerserVersS3(anyString(), any(), anyString())).thenReturn(true);
        when(client.finaliserFichier(anyString())).thenReturn(true);

        BilanDepot bilan = depot.deposer(42L, List.of(ok, ko));

        assertThat(bilan.deposees()).isEqualTo(1);
        assertThat(bilan.echecs()).containsExactly("ko.wav");
        assertThat(bilan.estComplet()).isFalse();
    }

    @Test
    @DisplayName("création refusée par VigieChiro (repli) → refus dur avec le détail de l'API, aucun upload")
    void participation_refusee() {
        when(participations.participationDe(42L)).thenReturn(Optional.empty());
        when(participations.creerPour(42L))
                .thenReturn(ResultatParticipation.echouee("HTTP 422 — {\"_errors\":{\"numero\":\"invalid field\"}}"));

        assertThatThrownBy(() -> depot.deposer(42L, List.of()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("refusée")
                .hasMessageContaining("422"); // le vrai détail de l'API est remonté, pas un message générique
        verify(client, never()).creerFichier(anyString());
    }

    @Test
    @DisplayName("site non rattaché (repli) → l'exception de la passerelle se propage, aucun upload")
    void site_non_rattache_propage() {
        when(participations.participationDe(42L)).thenReturn(Optional.empty());
        when(participations.creerPour(42L)).thenThrow(new RegleMetierException("Site non rattaché à VigieChiro"));

        assertThatThrownBy(() -> depot.deposer(42L, List.of()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("non rattaché");
        verify(client, never()).creerFichier(anyString());
    }

    private void armerUploadOk() {
        when(client.creerFichier(anyString())).thenReturn(Optional.of(new FichierSigne("f", "https://s3/x")));
        when(client.televerserVersS3(anyString(), any(), anyString())).thenReturn(true);
        when(client.finaliserFichier(anyString())).thenReturn(true);
    }

    private static Path fichier(Path dossier, String nom) throws IOException {
        Path chemin = dossier.resolve(nom);
        Files.write(chemin, new byte[] {1, 2, 3});
        return chemin;
    }
}
