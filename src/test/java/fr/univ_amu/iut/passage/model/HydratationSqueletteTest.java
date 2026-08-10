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
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
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

    private static final String CARRE = "130711";
    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";
    private static final String DOSSIER_SESSION = "Car130711-2026-Pass1-Z41";
    private static final String SEQ_1 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000";
    private static final String SEQ_2 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220534_000";

    private static final String PARTICIPATION_2 = "6a53f5faae21902a597394e7";
    private static final String DOSSIER_SESSION_2 = "Car130711-2026-Pass2-Z41";
    private static final String SEQ_2_1 = "Car130711-2026-Pass2-Z41-PaRec_20260704_221030_000";

    private static final String CSV_OBSERVATIONS = "\"nom du fichier\";\"temps_debut\";\"tadarida_taxon\"\n"
            + "\"" + SEQ_1 + "\";0.1;\"Pippip\"\n"
            + "\"" + SEQ_2 + "\";0.0;\"Pippip\"\n";

    private static final String PARTICIPATION_3 = "6a53f5faae21902a597394f1";
    private static final String DOSSIER_SESSION_3 = "Car130711-2026-Pass3-Z41";
    private static final String SEQ_3_1 = "Car130711-2026-Pass3-Z41-PaRec_20260705_222030_000";

    private static final String CSV_OBSERVATIONS_3 =
            "\"nom du fichier\";\"temps_debut\";\"tadarida_taxon\"\n" + "\"" + SEQ_3_1 + "\";0.3;\"Pippip\"\n";

    private static final String CSV_OBSERVATIONS_2 =
            "\"nom du fichier\";\"temps_debut\";\"tadarida_taxon\"\n" + "\"" + SEQ_2_1 + "\";0.2;\"Pippip\"\n";

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
                Optional.of(importObservations),
                () -> {});
    }

    /// Une nuit **rapatriée par la synchro** : passage + session archivée, aucune séquence, aucun original,
    /// et le lien vers sa participation. Le dossier de session porte le préfixe R6 réel, seul endroit d'où
    /// `passage` peut le relire.
    private long semerSquelette() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre(CARRE)
                .point("Z41")
                .cheminSession(dossier.resolve(DOSSIER_SESSION).toString())
                .semerSquelette();
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_PASSAGE, String.valueOf(jeu.idPassage()), PARTICIPATION));
        return jeu.idPassage();
    }

    /// Une nuit **déjà pourvue** de ses séquences : ni squelette, ni candidate à l'hydratation.
    private long semerNuitComplete() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre(CARRE)
                .point("Z41")
                .nuit(2, 2026, "2026-07-04")
                .cheminSession(dossier.resolve(DOSSIER_SESSION_2).toString())
                .semer();
        jeu.ajouterSequence();
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_PASSAGE, String.valueOf(jeu.idPassage()), PARTICIPATION_2));
        return jeu.idPassage();
    }

    /// Une **seconde** nuit rapatriée, sur le même point : de quoi exercer le balayage sur plus d'une nuit.
    private long semerSecondSquelette() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre(CARRE)
                .point("Z41")
                .nuit(2, 2026, "2026-07-04")
                .cheminSession(dossier.resolve(DOSSIER_SESSION_2).toString())
                .semerSquelette();
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_PASSAGE, String.valueOf(jeu.idPassage()), PARTICIPATION_2));
        return jeu.idPassage();
    }

    private void csvDisponible() {
        when(client.csvObservations(PARTICIPATION)).thenReturn(new ReponseApi.Succes<>(Optional.of(CSV_OBSERVATIONS)));
        when(importObservations.nomsSequencesCsv(CSV_OBSERVATIONS)).thenReturn(List.of(SEQ_1, SEQ_2));
    }

    /// Une **troisième** nuit : il en faut plus de deux pour que le parallélisme des téléchargements se
    /// distingue franchement d'un hasard d'ordonnancement.
    private long semerTroisiemeSquelette() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre(CARRE)
                .point("Z41")
                .nuit(3, 2026, "2026-07-05")
                .cheminSession(dossier.resolve(DOSSIER_SESSION_3).toString())
                .semerSquelette();
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_PASSAGE, String.valueOf(jeu.idPassage()), PARTICIPATION_3));
        return jeu.idPassage();
    }

    private void csvDisponiblePourLaTroisieme() {
        when(client.csvObservations(PARTICIPATION_3))
                .thenReturn(new ReponseApi.Succes<>(Optional.of(CSV_OBSERVATIONS_3)));
        when(importObservations.nomsSequencesCsv(CSV_OBSERVATIONS_3)).thenReturn(List.of(SEQ_3_1));
    }

    /// Le CSV correspondant à une participation, pour les montages qui répondent à plusieurs nuits.
    private static String csvPour(String idParticipation) {
        return switch (idParticipation) {
            case PARTICIPATION_2 -> CSV_OBSERVATIONS_2;
            case PARTICIPATION_3 -> CSV_OBSERVATIONS_3;
            default -> CSV_OBSERVATIONS;
        };
    }

    private void csvDisponiblePourLaSeconde() {
        when(client.csvObservations(PARTICIPATION_2))
                .thenReturn(new ReponseApi.Succes<>(Optional.of(CSV_OBSERVATIONS_2)));
        when(importObservations.nomsSequencesCsv(CSV_OBSERVATIONS_2)).thenReturn(List.of(SEQ_2_1));
    }

    private HydratationSquelette.BilanCompletion completer(List<Long> nuits, JetonAnnulation jeton) {
        return hydratation.completerLesSquelettes(nuits, progres -> {}, jeton);
    }

    private List<SequenceDEcoute> sequencesDe(long idPassage) {
        return sequenceDao.findBySession(
                sessionDao.trouverParPassage(idPassage).orElseThrow().id());
    }

    /// Branche une sonde sur le journal de [HydratationSquelette] le temps d'une action, et rend ce qui y a
    /// été écrit. Même idiome que `JournalisationTacheTest` : le niveau exact fait partie du contrat
    /// (ADR 0008), pas seulement le fait qu'une trace parte.
    private List<LogRecord> capturerLeJournal(Runnable action) {
        List<LogRecord> captures = new ArrayList<>();
        Logger logger = Logger.getLogger(HydratationSquelette.class.getName());
        Level niveauInitial = logger.getLevel();
        boolean parentInitial = logger.getUseParentHandlers();
        Handler sonde = new Handler() {
            @Override
            public void publish(LogRecord enregistrement) {
                captures.add(enregistrement);
            }

            @Override
            public void flush() {
                // Rien à vider : la sonde accumule en mémoire.
            }

            @Override
            public void close() {
                // Rien à fermer : aucune ressource système.
            }
        };
        sonde.setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(sonde);
        try {
            action.run();
        } finally {
            logger.removeHandler(sonde);
            logger.setLevel(niveauInitial);
            logger.setUseParentHandlers(parentInitial);
        }
        return captures;
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
                .carre(CARRE)
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
    @DisplayName("L'hydratation dit où elle en est : trois étapes, fractions croissantes")
    void progression_des_trois_etapes() {
        idPassage = semerSquelette();
        csvDisponible();
        List<Progression> points = new ArrayList<>();

        hydratation.hydraterSiSquelette(
                idPassage, HydratationSquelette.Source.CSV_SEULEMENT, points::add, JetonAnnulation.neutre());

        // Les TROIS étapes se disent, réseau compris : sans les nommer toutes, supprimer un relais ne se
        // verrait pas.
        assertThat(points)
                .extracting(Progression::libelle)
                .contains("Téléchargement des observations…", "Création des séquences…", "Import des observations…");
        // Et elles AVANCENT. La barre appartient ici à l'hydratation elle-même : c'est le cas quand elle est
        // toute l'opération (« Compléter cette nuit »). L'aplatissement est le choix de l'appelant pour qui
        // elle n'est qu'une sous-étape, pas une propriété de l'hydratation (#2554, passe 7).
        assertThat(points)
                .extracting(Progression::fraction)
                .isSorted()
                .anySatisfy(fraction -> assertThat(fraction).isGreaterThan(0.0));
    }

    @Test
    @DisplayName("Un appelant qui possède la barre aplatit les fractions et ne garde que le libellé")
    void libelle_seul_aplatit_les_fractions() {
        List<Progression> points = new ArrayList<>();
        Consumer<Progression> aplati = HydratationSquelette.libelleSeul(points::add);

        aplati.accept(new Progression("Import des observations…", 0.85));

        // Ce que fait la phase 0 d'une réactivation, et chaque nuit d'un balayage : ProgressionOperation
        // garde la fraction MONOTONE (#814), donc laisser passer celle-ci épinglerait la barre de la phase
        // SUIVANTE au plus haut atteint, et cette phase resterait invisible jusqu'à l'avoir dépassé.
        assertThat(points).singleElement().satisfies(point -> {
            assertThat(point.libelle()).isEqualTo("Import des observations…");
            assertThat(point.fraction()).isZero();
        });
    }

    @Test
    @DisplayName("Annuler avant le téléchargement arrête la phase 0 sans rien demander au réseau")
    void annulation_avant_le_telechargement() {
        idPassage = semerSquelette();
        csvDisponible();
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();

        assertThatThrownBy(() -> hydratation.hydraterSiSquelette(
                        idPassage, HydratationSquelette.Source.CSV_SEULEMENT, progres -> {}, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        verify(client, never()).csvObservations(anyString());
    }

    @Test
    @DisplayName("Fonctionnalité « Import Vigie-Chiro » éteinte : refus qui dit où la rallumer")
    void import_desactive_refuse_en_mode_complet() {
        idPassage = semerSquelette();
        HydratationSquelette sansImport = new HydratationSquelette(
                source,
                client,
                new Workspace(dossier),
                new HorlogeFigee(LocalDateTime.of(2026, 7, 26, 2, 0)),
                Optional.empty(),
                () -> {});

        assertThatThrownBy(() -> sansImport.hydraterSiSquelette(
                        idPassage, HydratationSquelette.Source.COMPLETE, progres -> {}, JetonAnnulation.neutre()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Import Vigie-Chiro");
    }

    @Test
    @DisplayName("Dossier de session renommé à la main : refus qui dit quoi corriger")
    void dossier_renomme_refuse_en_mode_complet() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre(CARRE)
                .point("Z41")
                .cheminSession(dossier.resolve("nuit-du-3-juillet").toString())
                .semerSquelette();
        idPassage = jeu.idPassage();
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), PARTICIPATION));

        assertThatThrownBy(() -> hydrater(HydratationSquelette.Source.COMPLETE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("renommé");

        // En balayage, le même empêchement ne dit rien mais reste COMPTÉ.
        assertThat(completer(List.of(idPassage), JetonAnnulation.neutre()))
                .isEqualTo(new HydratationSquelette.BilanCompletion(0, 1, 0));
    }

    @Test
    @DisplayName("Un CSV qu'on n'a pas pu LIRE est compté non lu, et tracé comme un incident")
    void lecture_ratee_comptee_non_lue_et_tracee() {
        long squelette = semerSquelette();
        when(client.csvObservations(PARTICIPATION)).thenThrow(new IllegalStateException("réseau coupé"));

        AtomicReference<HydratationSquelette.BilanCompletion> bilan = new AtomicReference<>();
        List<LogRecord> journal =
                capturerLeJournal(() -> bilan.set(completer(List.of(squelette), JetonAnnulation.neutre())));

        // « non lue », PAS « en attente d'analyse » : la nuit peut être analysée depuis des jours, c'est la
        // lecture qui a manqué. C'est le défaut que la passe 1 de #2554 a corrigé.
        assertThat(bilan.get()).isEqualTo(new HydratationSquelette.BilanCompletion(0, 0, 1));
        assertThat(journal)
                .singleElement()
                .satisfies(trace -> assertThat(trace.getLevel()).isEqualTo(Level.WARNING));
    }

    // --- Le balayage de la synchro (completerLesSquelettes) ------------------------------------------
    // Ces cinq tests viennent de la passe 6 de #2554 : PIT y a fait survivre cinq mutants, chacun sur une
    // garantie que le code ÉNONCE en commentaire sans que rien ne la tienne.

    @Test
    @DisplayName("Le dénominateur du bilan ne compte que les nuits réellement en squelette")
    void bilan_ne_compte_que_les_squelettes() {
        long squelette = semerSquelette();
        long deja = semerNuitComplete();
        csvDisponible();

        // Neutraliser le filtre estSquelette (le rendre toujours vrai) laissait la suite verte : la nuit
        // déjà pourvue n'était pas retouchée pour autant, mais elle entrait dans le dénominateur et
        // ressortait en « en attente d'analyse ». Soit exactement le défaut que la passe 1 a corrigé.
        assertThat(completer(List.of(squelette, deja), JetonAnnulation.neutre()))
                .isEqualTo(new HydratationSquelette.BilanCompletion(1, 0, 0));
    }

    @Test
    @DisplayName("Un balayage sans rien à faire rend un bilan vide, pas une absence de bilan")
    void rien_a_completer_rend_un_bilan_vide() {
        long deja = semerNuitComplete();

        assertThat(completer(List.of(deja), JetonAnnulation.neutre()))
                .isEqualTo(new HydratationSquelette.BilanCompletion(0, 0, 0));

        // C'est le cas de TOUTES les synchros suivant la première : le chemin le plus fréquent était le
        // seul que rien n'exerçait.
        verify(client, never()).csvObservations(anyString());
    }

    @Test
    @DisplayName("Une nuit qu'on ne sait pas traiter reste comptée incomplète, jamais tue")
    void nuit_non_traitable_reste_comptee() {
        long orpheline = JeuDeDonneesPassage.dans(source)
                .carre(CARRE)
                .point("Z41")
                .cheminSession(dossier.resolve(DOSSIER_SESSION).toString())
                .semerSquelette()
                .idPassage();

        assertThat(completer(List.of(orpheline), JetonAnnulation.neutre()))
                .isEqualTo(new HydratationSquelette.BilanCompletion(0, 1, 0));
    }

    @Test
    @DisplayName("Annuler pendant les écritures arrête le balayage : la nuit suivante n'est pas écrite")
    void annulation_pendant_les_ecritures() {
        long premiere = semerSquelette();
        long seconde = semerSecondSquelette();
        csvDisponible();
        csvDisponiblePourLaSeconde();
        JetonAnnulation jeton = new JetonAnnulation();
        when(importObservations.importerCsv(premiere, CSV_OBSERVATIONS, false)).thenAnswer(appel -> {
            jeton.annuler(); // l'utilisateur clique « Annuler » pendant que la première nuit s'écrit
            return "";
        });

        assertThatThrownBy(() -> completer(List.of(premiere, seconde), jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        // Le jeton n'est consulté qu'ENTRE deux écritures : ce qui était fait reste fait, ce qui suivait
        // n'est pas commencé. Sans cette consultation, un « Annuler » sur cent nuits ne coûtait rien à
        // demander et ne changeait rien - la boucle allait jusqu'au bout.
        assertThat(sequencesDe(premiere)).hasSize(2);
        assertThat(sequencesDe(seconde)).isEmpty();
    }

    @Test
    @DisplayName("Une nuit qui échoue est comptée non lue, rendue à son squelette, et laisse une trace")
    void nuit_en_echec_comptee_rendue_et_consignee() {
        long premiere = semerSquelette();
        long seconde = semerSecondSquelette();
        csvDisponible();
        csvDisponiblePourLaSeconde();
        when(importObservations.importerCsv(seconde, CSV_OBSERVATIONS_2, false))
                .thenThrow(new RegleMetierException("Analyse non terminée"));

        AtomicReference<HydratationSquelette.BilanCompletion> bilan = new AtomicReference<>();
        List<LogRecord> journal =
                capturerLeJournal(() -> bilan.set(completer(List.of(premiere, seconde), JetonAnnulation.neutre())));

        assertThat(bilan.get()).isEqualTo(new HydratationSquelette.BilanCompletion(1, 0, 1));
        assertThat(sequencesDe(premiere)).hasSize(2);
        assertThat(sequencesDe(seconde)).isEmpty(); // compensée, donc reprenable telle quelle
        // ADR 0008 : la trace part, et son NIVEAU dit la nature. Une analyse non terminée est une issue
        // normale : en WARNING elle noierait les vrais bugs qu'on cherche dans ce journal.
        assertThat(journal)
                .singleElement()
                .satisfies(trace -> assertThat(trace.getLevel()).isEqualTo(Level.FINE));
        assertThat(journal.getFirst().getMessage()).contains(String.valueOf(seconde));
    }

    @Test
    @DisplayName("#2606 : le balayage télécharge en parallèle mais écrit EN SÉRIE, sur un seul fil")
    void telecharge_en_parallele_ecrit_en_serie() {
        List<Long> nuits = new ArrayList<>();
        nuits.add(semerSquelette());
        nuits.add(semerSecondSquelette());
        nuits.add(semerTroisiemeSquelette());
        csvDisponible();
        csvDisponiblePourLaSeconde();
        csvDisponiblePourLaTroisieme();
        // L'IDENTIFIANT du fil, pas son nom : un fil virtuel n'en porte pas par défaut, et trois threads
        // distincts se seraient tous présentés sous la même chaîne vide.
        Set<Long> filsDeTelechargement = ConcurrentHashMap.newKeySet();
        Set<Long> filsDEcriture = ConcurrentHashMap.newKeySet();
        when(client.csvObservations(anyString())).thenAnswer(appel -> {
            filsDeTelechargement.add(Thread.currentThread().threadId());
            return new ReponseApi.Succes<>(Optional.of(csvPour(appel.getArgument(0))));
        });
        when(importObservations.importerCsv(anyLong(), anyString(), eq(false))).thenAnswer(appel -> {
            filsDEcriture.add(Thread.currentThread().threadId());
            return "";
        });

        completer(nuits, JetonAnnulation.neutre());

        // SQLite est mono-écrivain : huit transactions concurrentes s'attendraient au mieux, se
        // disputeraient le verrou au pire. La décision vit en commentaire depuis #2557 et rien ne la
        // tenait : rendre la boucle parallèle ne faisait rougir aucune assertion fonctionnelle, jusqu'au
        // premier SQLITE_BUSY sur un gros compte.
        assertThat(filsDEcriture).as("toutes les écritures sur le même fil").hasSize(1);
        assertThat(filsDeTelechargement)
                .as("les téléchargements, eux, partent en parallèle")
                .hasSizeGreaterThan(1);
        assertThat(filsDeTelechargement)
                .as("et hors du fil appelant, qui reste celui des écritures")
                .doesNotContainAnyElementsOf(filsDEcriture);
    }
}
