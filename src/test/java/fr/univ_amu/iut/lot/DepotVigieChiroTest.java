package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.ParticipationADeposer;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.PositionMicro;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.dao.PointDao;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Orchestration du **dépôt d'une nuit** ([DepotVigieChiro], #142) : construction de la participation
/// (localité, fenêtre nuit franchissant minuit, météo #702, configuration micro #697) et upload des
/// fichiers, sur DAO + client mockés (aucun réseau ni base réels).
@ExtendWith(MockitoExtension.class)
class DepotVigieChiroTest {

    private static final String OBJECTID_SITE = "5eb12120cbe7410011f0a97f";

    @Mock
    PassageDao passageDao;

    @Mock
    PointDao pointDao;

    @Mock
    MaterielMicroDao materielDao;

    @Mock
    LienVigieChiroDao liens;

    @Mock
    ClientVigieChiro client;

    @Captor
    ArgumentCaptor<ParticipationADeposer> participationCaptor;

    private DepotVigieChiro depot;

    @BeforeEach
    void préparer() {
        depot = new DepotVigieChiro(passageDao, pointDao, materielDao, liens, client);
    }

    @Test
    @DisplayName("dépôt complet : participation (localité, nuit +1j, météo, config micro) + fichiers déposés")
    void depot_complet(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "Car130711-2026-Pass1-Z41_000.wav");
        Path b = fichier(dossier, "Car130711-2026-Pass1-Z41_001.wav");
        armerPassageComplet();
        when(client.creerParticipation(eq(OBJECTID_SITE), any())).thenReturn(Optional.of("part-1"));
        when(client.creerFichier(anyString())).thenReturn(Optional.of(new FichierSigne("f", "https://s3/x")));
        when(client.televerserVersS3(anyString(), any(), anyString())).thenReturn(true);
        when(client.finaliserFichier(anyString())).thenReturn(true);

        BilanDepot bilan = depot.deposer(42L, List.of(a, b));

        assertThat(bilan.participationId()).isEqualTo("part-1");
        assertThat(bilan.deposees()).isEqualTo(2);
        assertThat(bilan.estComplet()).isTrue();

        // Lien passage → participation mémorisé (axe 4.2) pour retrouver la participation à l'import.
        verify(liens).upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, "42", "part-1"));

        verify(client).creerParticipation(eq(OBJECTID_SITE), participationCaptor.capture());
        ParticipationADeposer envoyee = participationCaptor.getValue();
        assertThat(envoyee.point()).isEqualTo("Z41");
        assertThat(envoyee.numero()).isEqualTo(1);
        assertThat(envoyee.dateDebut()).isEqualTo("2026-07-03T21:00:00");
        assertThat(envoyee.dateFin()).isEqualTo("2026-07-04T05:00:00"); // fin < début → lendemain
        assertThat(envoyee.meteo().vent()).isEqualTo("FAIBLE");
        assertThat(envoyee.meteo().couverture()).isEqualTo("25-50");
        assertThat(envoyee.configuration())
                .containsEntry("micro0_type", "ICS")
                .containsEntry("micro0_position", "CANOPEE")
                .containsEntry("micro0_hauteur", "4")
                .containsEntry("detecteur_enregistreur_numserie", "1997632");
    }

    @Test
    @DisplayName("dépôt partiel : un fichier en échec est listé, les autres comptés, sans interrompre")
    void depot_partiel(@TempDir Path dossier) throws IOException {
        Path ok = fichier(dossier, "ok.wav");
        Path ko = fichier(dossier, "ko.wav");
        armerPassageComplet();
        when(client.creerParticipation(eq(OBJECTID_SITE), any())).thenReturn(Optional.of("part-1"));
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
    @DisplayName("site non rattaché à VigieChiro → refus dur, aucune participation créée")
    void site_non_rattache() {
        when(passageDao.findById(42L)).thenReturn(Optional.of(passage()));
        when(pointDao.findById(7L)).thenReturn(Optional.of(point()));
        when(liens.objectidPour(LienVigieChiro.ENTITE_SITE, "7")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depot.deposer(42L, List.of()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("non rattaché");
        verify(client, never()).creerParticipation(anyString(), any());
    }

    @Test
    @DisplayName("création de participation refusée par VigieChiro → refus dur, aucun upload")
    void participation_refusee() {
        armerPassageComplet();
        when(client.creerParticipation(eq(OBJECTID_SITE), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depot.deposer(42L, List.of()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("refusée");
        verify(client, never()).creerFichier(anyString());
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private void armerPassageComplet() {
        when(passageDao.findById(42L)).thenReturn(Optional.of(passage()));
        when(pointDao.findById(7L)).thenReturn(Optional.of(point()));
        when(liens.objectidPour(LienVigieChiro.ENTITE_SITE, "7")).thenReturn(Optional.of(OBJECTID_SITE));
        when(materielDao.pour(42L)).thenReturn(new MaterielMicro(42L, PositionMicro.CANOPEE, 4.0, "ICS"));
    }

    private static Passage passage() {
        // Nuit du 3→4 juillet : début 21:00, fin 05:00 (franchit minuit) ; météo vent FAIBLE / couv 25-50.
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
                "{\"vent\":\"FAIBLE\",\"couvertureNuageuse\":\"DE_25_A_50\"}",
                null,
                7L,
                "1997632");
    }

    private static PointDEcoute point() {
        return new PointDEcoute(7L, "Z41", 43.5145, 5.4513, null, 7L);
    }

    private static Path fichier(Path dossier, String nom) throws IOException {
        Path chemin = dossier.resolve(nom);
        Files.write(chemin, new byte[] {1, 2, 3});
        return chemin;
    }
}
