package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests de [HydratationSquelette] (#2555) : amener **en place** une nuit rapatriée de Vigie-Chiro au
/// niveau « contenu ».
///
/// Le client et le port d'import sont mockés, la base est jetable. On vérifie surtout ce qui distingue
/// cette hydratation de la reconstruction : le passage **survit** (même identifiant, mêmes saisies), une
/// nuit déjà pourvue n'est **pas retouchée**, et une écriture interrompue **rend** la nuit à son état de
/// squelette au lieu de la laisser à moitié faite.
class HydratationSqueletteTest {

    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";
    private static final String DOSSIER_SESSION = "Car130711-2026-Pass1-Z41";
    private static final String SEQ_1 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000";
    private static final String SEQ_2 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220534_000";

    private static final String CSV_OBSERVATIONS = "\"nom du fichier\";\"temps_debut\";\"tadarida_taxon\"\n"
            + "\"" + SEQ_1 + "\";0.1;\"Pippip\"\n"
            + "\"" + SEQ_2 + "\";0.0;\"Pippip\"\n";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ClientVigieChiro client;
    private ImportObservations importObservations;
    private SequenceDao sequenceDao;
    private SessionDao sessionDao;
    private long idPassage;
    private HydratationSquelette hydratation;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        sequenceDao = new SequenceDao(source);
        sessionDao = new SessionDao(source);

        client = mock(ClientVigieChiro.class);
        importObservations = mock(ImportObservations.class);
        hydratation = new HydratationSquelette(
                source,
                client,
                new Workspace(dossier),
                new HorlogeFigee(LocalDateTime.of(2026, 7, 26, 2, 0)),
                Optional.of(importObservations));
    }

    /// Une nuit **rapatriée par la synchro** : passage + session archivée, aucune séquence, aucun original,
    /// et le lien vers sa participation. Le dossier de session porte le préfixe R6 réel, seul endroit d'où
    /// `passage` peut le relire.
    private long semerSquelette() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .cheminSession(dossier.resolve(DOSSIER_SESSION).toString())
                .semerSquelette();
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_PASSAGE, String.valueOf(jeu.idPassage()), PARTICIPATION));
        return jeu.idPassage();
    }

    private void csvDisponible() {
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.of(CSV_OBSERVATIONS)));
        when(importObservations.nomsSequencesCsv(CSV_OBSERVATIONS)).thenReturn(List.of(SEQ_1, SEQ_2));
    }

    private Optional<HydratationSquelette.BilanHydratation> hydrater(HydratationSquelette.Source mode) {
        return hydratation.hydraterSiSquelette(idPassage, mode, progres -> {}, JetonAnnulation.neutre());
    }

    @Test
    @DisplayName("Une nuit rapatriée reçoit ses séquences et ses observations, sans changer d'identifiant")
    void hydrate_en_place() {
        idPassage = semerSquelette();
        csvDisponible();

        Optional<HydratationSquelette.BilanHydratation> bilan = hydrater(HydratationSquelette.Source.CSV_SEULEMENT);

        assertThat(bilan)
                .hasValueSatisfying(resultat -> assertThat(resultat.sequences()).isEqualTo(2));
        // Le point qui distingue cette hydratation de la reconstruction : LE PASSAGE SURVIT. La
        // reconstruction, elle, supprime et recrée - ce qui changerait l'identifiant sous l'écran ouvert
        // et effacerait les saisies manuelles de la nuit.
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession))
                .extracting(SequenceDEcoute::nomFichier)
                .containsExactlyInAnyOrder(SEQ_1 + ".wav", SEQ_2 + ".wav");
        verify(importObservations).importerCsv(idPassage, CSV_OBSERVATIONS, false);
    }

    @Test
    @DisplayName(
            "Les séquences recréées portent leur horodatage : c'est lui qui reconnaît la bonne nuit d'une carte SD")
    void sequences_horodatees() {
        idPassage = semerSquelette();
        csvDisponible();

        hydrater(HydratationSquelette.Source.CSV_SEULEMENT);

        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession))
                .allSatisfy(sequence -> assertThat(sequence.horodatageCapture()).isNotNull());
    }

    @Test
    @DisplayName("CSV_SEULEMENT sans CSV : la nuit reste squelette, sans erreur - elle attend son tour")
    void csv_absent_laisse_la_nuit_en_squelette() {
        idPassage = semerSquelette();
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.empty()));

        assertThat(hydrater(HydratationSquelette.Source.CSV_SEULEMENT)).isEmpty();

        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession)).isEmpty();
        // Le repli `donnees` (une cinquantaine de pages par nuit) n'a PAS lieu : c'est tout l'objet du mode,
        // et ce serait le coût qui a fait écarter « tout rapatrier à la synchro » (ADR 0016).
        verify(client, never()).donnees(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Une nuit qui a déjà ses séquences n'est pas retouchée, et ne coûte aucun appel réseau")
    void nuit_deja_hydratee_ignoree() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .cheminSession(dossier.resolve(DOSSIER_SESSION).toString())
                .semer();
        jeu.ajouterSequence();
        idPassage = jeu.idPassage();
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), PARTICIPATION));

        assertThat(hydrater(HydratationSquelette.Source.CSV_SEULEMENT)).isEmpty();

        verify(client, never()).csvObservations(anyString());
        verify(importObservations, never()).importerCsv(anyLong(), anyString(), eq(false));
    }

    @Test
    @DisplayName("Un import qui échoue rend la nuit à son état de squelette : rien à moitié fait")
    void echec_de_l_import_compense() {
        idPassage = semerSquelette();
        csvDisponible();
        when(importObservations.importerCsv(idPassage, CSV_OBSERVATIONS, false))
                .thenThrow(new RegleMetierException("Analyse non terminée"));

        assertThatThrownBy(() -> hydrater(HydratationSquelette.Source.CSV_SEULEMENT))
                .isInstanceOf(RegleMetierException.class);

        // Sans cette vérification, le test serait vert même si l'hydratation n'avait RIEN écrit : « la base
        // est propre » ne prouve la compensation que si l'on est allé jusqu'à l'écriture. L'import n'est
        // appelé qu'APRÈS la création des séquences.
        verify(importObservations).importerCsv(idPassage, CSV_OBSERVATIONS, false);
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession)).isEmpty();
        assertThat(new EnregistrementOriginalDao(source).findBySession(idSession))
                .isEmpty();
    }

    @Test
    @DisplayName("COMPLETE sur une nuit non rattachée : refus motivé, là où la synchro se serait tue")
    void nuit_non_rattachee_refusee_en_mode_complet() {
        idPassage = JeuDeDonneesPassage.dans(source)
                .cheminSession(dossier.resolve(DOSSIER_SESSION).toString())
                .semerSquelette()
                .idPassage();

        assertThatThrownBy(() -> hydrater(HydratationSquelette.Source.COMPLETE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("rattachée à aucune participation");

        // Le même empêchement, en balayage de compte, ne dit rien et n'écarte pas les autres nuits.
        assertThat(hydrater(HydratationSquelette.Source.CSV_SEULEMENT)).isEmpty();
    }

    @Test
    @DisplayName("La phase 0 ne relaie que le LIBELLÉ : sa fraction reste à zéro, la barre appartient à la suite")
    void progression_sans_fraction() {
        idPassage = semerSquelette();
        csvDisponible();
        List<Progression> points = new ArrayList<>();
        Consumer<Progression> journal = points::add;

        hydratation.hydraterSiSquelette(
                idPassage, HydratationSquelette.Source.CSV_SEULEMENT, journal, JetonAnnulation.neutre());

        // ProgressionOperation garde la fraction MONOTONE (#814) : laisser passer les fractions du
        // téléchargement épinglerait la barre au plus haut atteint, et le rebranchement qui suit resterait
        // invisible jusqu'à l'avoir dépassé.
        assertThat(points)
                .isNotEmpty()
                .allSatisfy(point -> assertThat(point.fraction()).isZero());
        assertThat(points).extracting(Progression::libelle).contains("Import des observations…");
    }
}
