package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.SuiviPagination;
import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.InventaireParInspection;
import fr.univ_amu.iut.importation.model.RegenerationParTransformationAudio;
import fr.univ_amu.iut.importation.model.SequenceProduite;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.TransformationOriginal;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Réactivation d'un passage archivé (#1302) sur une base SQLite jetable et de **vrais WAV**
/// synthétiques sous `@TempDir` : les bons fichiers rebranchent, les homonymes de contenu différent
/// sont refusés et motivés, un réimport partiel laisse le passage en `PARTIELLE`, et rejouer
/// l'opération est sans effet.
class ServiceReactivationPassageTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
    private static final String SEQ_1 = "Car040962-2026-Pass1-A1-PaRec_20260620_213000_000.wav";
    private static final String SEQ_2 = "Car040962-2026-Pass1-A1-PaRec_20260620_213005_000.wav";
    private static final double FREQUENCE_REELLE_HZ = 384_000;

    /// Voie « bruts » (#1406) : fréquence d'ACQUISITION du brut (celle du log), en-tête à Fe/10.
    private static final int FREQUENCE_ACQUISITION_HZ = 40_000;

    /// Durée réelle du brut : 12 s → 3 tranches de 5 s (la dernière plus courte).
    private static final double DUREE_BRUT_S = 12.0;

    /// Le brut tel que la base le nomme (R6), et tel qu'une copie de carte SD le nomme (non préfixé).
    private static final String NOM_SD_BRUT = "PaRec_20260620_213000.wav";

    private static final String NOM_R6_BRUT = "Car040962-2026-Pass1-A1-PaRec_20260620_213000.wav";

    /// Certains enregistreurs mettent un tiret dans le nom du fichier : couper au dernier tiret du nom R6
    /// donnerait « 213000.wav », qu'on ne trouverait jamais.
    private static final String NOM_SD_AVEC_TIRET = "PaRec-PR1925492_20260620_213000.wav";
    private static final double DUREE_REELLE_S = 0.5;

    @TempDir
    Path dossier;

    private Path transformes;
    private Path sauvegarde;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private SequenceDao sequenceDao;
    private EnregistrementOriginalDao originalDao;
    private ServiceDisponibiliteAudio disponibilite;
    private RegenerationParTransformationAudio regeneration;
    private AdoptionOriginauxReconstruits adoption;
    private ServiceReactivationPassage service;
    private Long idPoint;
    private Long idPassage;
    private Long idSession;

    @BeforeEach
    void preparer() throws IOException {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, null, null));
        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        sequenceDao = new SequenceDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        transformes = Files.createDirectories(
                dossier.resolve(PREFIXE.nomDossierSession()).resolve("transformes"));
        sauvegarde = Files.createDirectories(dossier.resolve("sauvegarde-utilisateur"));
        disponibilite = new ServiceDisponibiliteAudio(sessionDao, sequenceDao, new Workspace(dossier));
        // Régénération branchée (port #1406) : c'est la VRAIE transformation de l'import, seule capable de
        // reproduire les tranches à l'identique - la faire jouer ici prouve la chaîne de bout en bout.
        regeneration = new RegenerationParTransformationAudio(new TransformationAudio());
        adoption = new AdoptionOriginauxReconstruits(
                originalDao, sequenceDao, sessionDao, new HorlogeFigee(LocalDateTime.of(2026, 7, 16, 20, 0)));
        service = new ServiceReactivationPassage(
                sessionDao,
                sequenceDao,
                originalDao,
                new VerificationIdentiteAudio(),
                disponibilite,
                Optional.empty(), // pas de cris : cascade structurelle (injecteur partiel)
                Optional.of(regeneration),
                Optional.of(new InventaireParInspection(new InspecteurDossier(new AnalyseurLogPR()))),
                adoption,
                Optional.empty()); // pas d'import : pas de phase d'ancrage (comportement historique)
    }

    @Test
    @DisplayName("Les bons fichiers réimportés réactivent le passage : COMPLETE, marqueur d'archivage effacé")
    void bons_fichiers_reactivent() throws IOException {
        archiverAvecSauvegarde(true, true);

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees()).isEqualTo(2);
        assertThat(rapport.divergentes()).isZero();
        assertThat(rapport.manquantes()).isZero();
        assertThat(rapport.complete()).isTrue();
        assertThat(disponibilite.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.COMPLETE);
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().archivee())
                .as("l'audio est revenu : le passage n'est plus archivé")
                .isFalse();
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().volumeSequencesOctets())
                .as("la fiche du passage retrouve son volume")
                .isPositive();
    }

    @Test
    @DisplayName("Passage reconstruit réactivé (#1571) : l'audio revenu, l'ancrage manquant est acquis par ré-import")
    void reactivation_acquiert_l_ancrage_manquant() throws IOException {
        archiverAvecSauvegarde(true, true); // l'audio revient COMPLET
        ImportObservations importObservations = mock(ImportObservations.class);
        when(importObservations.estRattache(idPassage)).thenReturn(true);
        when(importObservations.ancrageManquant(idPassage)).thenReturn(true);
        // Le port promet « un compte rendu prêt à afficher » : un mock nu renverrait null, ce que
        // RapportAncrage refuse à la construction. Honorer le contrat plutôt que tolérer sa violation.
        when(importObservations.importer(eq(idPassage), eq(true), any())).thenReturn("Observations importées.");

        avecImport(importObservations).reactiver(idPassage, sauvegarde, progres -> {});

        // Audio (re)disponible + observations sans ancrage : on le rapatrie par ré-import des donnees
        // (remplacer = true), qui pose idDonneeVigieChiro tout en préservant les validations observateur.
        // Variante suivie (#1597) : la phase d'ancrage passe un suivi (progression + annulation par page).
        verify(importObservations).importer(eq(idPassage), eq(true), any());
    }

    @Test
    @DisplayName("#1904 : le rapport porte ce que la phase d'ancrage a rapatrié, et rien quand elle n'a pas lieu")
    void le_rapport_porte_le_compte_rendu_du_rapatriement() throws IOException {
        archiverAvecSauvegarde(true, true);
        ImportObservations importObservations = mock(ImportObservations.class);
        when(importObservations.estRattache(idPassage)).thenReturn(true);
        when(importObservations.ancrageManquant(idPassage)).thenReturn(true);
        when(importObservations.importer(eq(idPassage), eq(true), any()))
                .thenReturn("Observations importées depuis Vigie-Chiro : 40 observation(s)."
                        + " Le validateur s'est exprimé sur 3 observation(s).");

        RapportReactivation rapport = avecImport(importObservations).reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.rapatriement().texte())
                .as("sans cela, les messages du validateur arrivent en base sans que rien ne le dise")
                .contains("Le validateur s'est exprimé sur 3 observation(s).");
    }

    @Test
    @DisplayName("#1904 : sans phase d'ancrage, le rapport n'a rien à dire du rapatriement")
    void le_rapport_est_muet_sans_phase_d_ancrage() throws IOException {
        archiverAvecSauvegarde(true, true);
        ImportObservations importObservations = mock(ImportObservations.class);
        when(importObservations.estRattache(idPassage)).thenReturn(false); // rien à quoi s'ancrer

        RapportReactivation rapport = avecImport(importObservations).reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.rapatriement().estMuet())
                .as("une réactivation ordinaire ne doit pas afficher de ligne vide ni de « rien à faire »")
                .isTrue();
    }

    @Test
    @DisplayName("Annulation pendant l'ancrage (#1597) : « Annuler » interrompt la phase réseau dès une page")
    void annulation_pendant_l_ancrage() throws IOException {
        archiverAvecSauvegarde(true, true); // l'audio revient complet → la phase d'ancrage se déclenche
        ImportObservations importObservations = mock(ImportObservations.class);
        when(importObservations.estRattache(idPassage)).thenReturn(true);
        when(importObservations.ancrageManquant(idPassage)).thenReturn(true);
        JetonAnnulation jeton = new JetonAnnulation();
        // Le port relaie une page au suivi : c'est là que la réactivation consulte le jeton (annulé juste avant).
        when(importObservations.importer(eq(idPassage), eq(true), any())).thenAnswer(invocation -> {
            jeton.annuler();
            SuiviPagination suivi = invocation.getArgument(2);
            suivi.surPage(1, 48); // -> le suivi lève OperationAnnuleeException
            return "";
        });

        assertThatThrownBy(() -> avecImport(importObservations).reactiver(idPassage, sauvegarde, progres -> {}, jeton))
                .isInstanceOf(OperationAnnuleeException.class);
    }

    @Test
    @DisplayName("Passage déjà ancré réactivé (#1571) : pas de ré-import, l'ancrage est déjà là")
    void reactivation_n_acquiert_pas_un_ancrage_present() throws IOException {
        archiverAvecSauvegarde(true, true);
        ImportObservations importObservations = mock(ImportObservations.class);
        when(importObservations.estRattache(idPassage)).thenReturn(true);
        when(importObservations.ancrageManquant(idPassage)).thenReturn(false);

        avecImport(importObservations).reactiver(idPassage, sauvegarde, progres -> {});

        verify(importObservations, never()).importer(eq(idPassage), eq(true), any());
    }

    @Test
    @DisplayName("Réactivation sans audio (#1571) : pas d'ancrage — on ne corrige pas ce qu'on n'écoute pas")
    void reactivation_sans_audio_n_acquiert_pas_l_ancrage() throws IOException {
        archiverAvecSauvegarde(true, true); // passage archivé, fichiers en sauvegarde
        ImportObservations importObservations = mock(ImportObservations.class);
        Path vide = Files.createDirectories(dossier.resolve("dossier-vide"));

        // Réactiver depuis un dossier VIDE : rien ne rebranche, l'audio reste ABSENTE → pas de phase d'ancrage.
        avecImport(importObservations).reactiver(idPassage, vide, progres -> {});

        verify(importObservations, never()).importer(eq(idPassage), eq(true), any());
    }

    @Test
    @DisplayName("#1780 : deux barres — la régénération et l'ancrage rapportent chacun à son consommateur")
    void deux_progressions_separent_regeneration_et_ancrage() throws IOException {
        archiverAvecSauvegarde(true, true); // audio complet -> la phase d'ancrage se déclenche
        ImportObservations importObservations = mock(ImportObservations.class);
        when(importObservations.estRattache(idPassage)).thenReturn(true);
        when(importObservations.ancrageManquant(idPassage)).thenReturn(true);
        when(importObservations.importer(eq(idPassage), eq(true), any())).thenAnswer(invocation -> {
            SuiviPagination suivi = invocation.getArgument(2);
            suivi.surPage(1, 2);
            suivi.surPage(2, 2);
            return "";
        });
        List<Progression> regeneration = new ArrayList<>();
        List<Progression> ancrage = new ArrayList<>();

        avecImport(importObservations)
                .reactiver(idPassage, sauvegarde, regeneration::add, ancrage::add, JetonAnnulation.neutre());

        assertThat(regeneration)
                .as("la barre de régénération ne reçoit que la phase disque")
                .isNotEmpty()
                .noneMatch(point -> point.libelle().startsWith("Récupération des identifiants"));
        assertThat(ancrage)
                .as("la barre d'ancrage ne reçoit que la phase réseau, et atteint 100 %")
                .isNotEmpty()
                .allMatch(point -> point.libelle().startsWith("Récupération des identifiants"));
        assertThat(ancrage.get(ancrage.size() - 1).fraction()).isEqualTo(1.0);
    }

    /// Service muni du port d'import (phase d'ancrage #1571 active), sur la même base et le même workspace.
    private ServiceReactivationPassage avecImport(ImportObservations importObservations) {
        return new ServiceReactivationPassage(
                sessionDao,
                sequenceDao,
                originalDao,
                new VerificationIdentiteAudio(),
                disponibilite,
                Optional.empty(),
                Optional.of(regeneration),
                Optional.of(new InventaireParInspection(new InspecteurDossier(new AnalyseurLogPR()))),
                adoption,
                Optional.of(importObservations));
    }

    @Test
    @DisplayName("Fichiers homonymes de contenu différent : rien n'est rebranché, chaque écart est motivé")
    void homonymes_differents_refuses() throws IOException {
        archiverAvecSauvegarde(true, true);
        // L'utilisateur désigne une AUTRE nuit : mêmes noms, autre audio (le piège que ferme #1299).
        ecrireWav(sauvegarde.resolve(SEQ_1), 13);
        ecrireWav(sauvegarde.resolve(SEQ_2), 13);

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees()).isZero();
        assertThat(rapport.divergentes()).isEqualTo(2);
        assertThat(rapport.ecarts())
                .allSatisfy(ecart -> assertThat(ecart.motif()).isNotBlank());
        assertThat(disponibilite.disponibilite(idPassage))
                .as("aucun fichier douteux n'est rebranché en silence")
                .isEqualTo(DisponibiliteAudio.ABSENTE);
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().archivee())
                .isTrue();
    }

    @Test
    @DisplayName("Réimport partiel : le passage passe en PARTIELLE, le marqueur d'archivage est conservé")
    void reimport_partiel_reste_partielle() throws IOException {
        archiverAvecSauvegarde(true, false); // une seule séquence dans la sauvegarde

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees()).isEqualTo(1);
        assertThat(rapport.manquantes()).isEqualTo(1);
        assertThat(rapport.decompte()).isEqualTo(new DecompteAudio(1, 2));
        assertThat(disponibilite.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.PARTIELLE);
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().archivee())
                .as("les absences restantes sont toujours expliquées par l'archivage")
                .isTrue();
    }

    @Test
    @DisplayName("Idempotent : rejouer la réactivation sur un passage déjà réactivé est sans effet")
    void idempotent() throws IOException {
        archiverAvecSauvegarde(true, true);
        service.reactiver(idPassage, sauvegarde, progres -> {});

        RapportReactivation second = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(second.dejaPresentes()).isEqualTo(2);
        assertThat(second.reactivees()).isZero();
        assertThat(second.divergentes()).isZero();
        assertThat(second.complete()).isTrue();
    }

    @Test
    @DisplayName("Sans empreinte (import ancien) : la cascade structurelle réactive, en confiance FORTE")
    void sans_empreinte_cascade_structurelle() throws IOException {
        archiverAvecSauvegarde(false, true); // séquences insérées sans taille ni empreinte

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees()).isEqualTo(2);
        assertThat(rapport.confianceMinimale())
                .as("sans empreinte, la preuve structurelle seule vaut FORTE (#1309)")
                .isEqualTo(NiveauConfiance.FORTE);
        assertThat(rapport.complete()).isTrue();
    }

    @Test
    @DisplayName("Avec empreinte : le rebranchement est en CERTITUDE, et la progression est notifiée")
    void avec_empreinte_certitude_et_progression() throws IOException {
        archiverAvecSauvegarde(true, true);
        List<Progression> avancement = new ArrayList<>();

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, avancement::add);

        assertThat(rapport.confianceMinimale()).isEqualTo(NiveauConfiance.CERTITUDE);
        assertThat(avancement).hasSize(2);
        assertThat(avancement.get(avancement.size() - 1).fraction()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("#1309 : les cris attendus de chaque séquence sont demandés au port (vérification acoustique)")
    void cris_attendus_consultes() throws IOException {
        archiverAvecSauvegarde(false, true); // sans empreinte : la cascade descend jusqu'à l'acoustique
        List<Long> sequencesInterrogees = new ArrayList<>();
        ServiceReactivationPassage avecCris = new ServiceReactivationPassage(
                sessionDao,
                sequenceDao,
                originalDao,
                new VerificationIdentiteAudio(),
                disponibilite,
                Optional.of(idSequence -> {
                    sequencesInterrogees.add(idSequence);
                    return List.of(); // aucune observation : rien à corrompre, structurelle seule
                }),
                Optional.of(regeneration),
                Optional.empty(),
                adoption,
                Optional.empty());

        avecCris.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(sequencesInterrogees)
                .as("une interrogation par séquence à rebrancher")
                .hasSize(2);
    }

    @Test
    @DisplayName("Dossier source introuvable : refus net, rien n'est touché")
    void dossier_introuvable_refuse() throws IOException {
        archiverAvecSauvegarde(true, true);

        assertThatThrownBy(() -> service.reactiver(idPassage, dossier.resolve("absent"), progres -> {}))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Dossier introuvable");
    }

    // --- Voie « bruts » (#1406) -------------------------------------------------------------------

    @Test
    @DisplayName("#1406 : seul le brut a été gardé → séquences RÉGÉNÉRÉES, et leur empreinte concorde")
    void bruts_regeneres_et_verifies() throws IOException {
        List<String> noms = archiverAvecBrutSauvegarde(NOM_R6_BRUT, true);

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.voie())
                .as("le dossier ne contenait pas les tranches : elles ont été recalculées")
                .isEqualTo(VoieReactivation.BRUTS);
        assertThat(rapport.reactivees()).isEqualTo(noms.size());
        assertThat(rapport.divergentes()).isZero();
        assertThat(rapport.confianceMinimale())
                .as("la transformation est déterministe (R11) : les tranches régénérées retrouvent"
                        + " l'empreinte capturée avant l'archivage. La reproductibilité est une PREUVE.")
                .isEqualTo(NiveauConfiance.CERTITUDE);
        assertThat(rapport.complete()).isTrue();
        assertThat(disponibilite.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.COMPLETE);
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().archivee())
                .as("l'audio est revenu : le passage n'est plus archivé")
                .isFalse();
        assertThat(noms).allSatisfy(nom -> assertThat(transformes.resolve(nom)).exists());
    }

    @Test
    @DisplayName("#1406 : le brut sauvegardé sous son nom de carte SD (non préfixé) est reconnu")
    void brut_sous_nom_de_carte_sd() throws IOException {
        List<String> noms = archiverAvecBrutSauvegarde(NOM_SD_BRUT, true);

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.voie()).isEqualTo(VoieReactivation.BRUTS);
        assertThat(rapport.reactivees())
                .as("le préfixe R6 est ce que l'import AJOUTE : une copie de carte SD ne le porte pas")
                .isEqualTo(noms.size());
        assertThat(rapport.confianceMinimale()).isEqualTo(NiveauConfiance.CERTITUDE);
    }

    @Test
    @DisplayName("#1406 : un brut au bon nom mais au mauvais contenu ne régénère RIEN")
    void brut_divergent_ne_regenere_rien() throws IOException {
        List<String> noms = archiverAvecBrutSauvegarde(NOM_R6_BRUT, false);

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees())
                .as("un brut douteux ne redonne pas des séquences douteuses : il ne redonne RIEN")
                .isZero();
        assertThat(rapport.divergentes()).isEqualTo(1);
        assertThat(rapport.ecarts())
                .singleElement()
                .satisfies(ecart -> assertThat(ecart.motif()).contains("SHA-256"));
        assertThat(rapport.manquantes()).isEqualTo(noms.size());
        assertThat(disponibilite.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.ABSENTE);
    }

    @Test
    @DisplayName("#1406 : sans la feature « Importation », la voie bruts se refuse en le disant")
    void regeneration_indisponible_refuse() throws IOException {
        archiverAvecBrutSauvegarde(NOM_R6_BRUT, true);
        ServiceReactivationPassage sansRegeneration = new ServiceReactivationPassage(
                sessionDao,
                sequenceDao,
                originalDao,
                new VerificationIdentiteAudio(),
                disponibilite,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                adoption,
                Optional.empty());

        assertThatThrownBy(() -> sansRegeneration.reactiver(idPassage, sauvegarde, progres -> {}))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Importation");
    }

    @Test
    @DisplayName("#1406 : un dossier qui ne contient ni tranches ni bruts est reconnu comme tel")
    void dossier_sans_rien_de_reconnaissable() throws IOException {
        archiverAvecBrutSauvegarde(NOM_R6_BRUT, true);
        Path vide = Files.createDirectories(dossier.resolve("photos-de-vacances"));

        RapportReactivation rapport = service.reactiver(idPassage, vide, progres -> {});

        assertThat(rapport.voie()).isEqualTo(VoieReactivation.AUCUNE);
        assertThat(rapport.reactivees()).isZero();
        assertThat(rapport.manquantes()).isPositive();
    }

    @Test
    @DisplayName("#1648 : passage reconstruit + bruts présents → voie RECONSTRUIT (pas « introuvables » trompeur)")
    void passage_reconstruit_bruts_presents_est_dit_honnetement() throws IOException {
        List<String> sequences = reconstruireAvecBrutsEnSauvegarde();

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.voie())
                .as("les bruts sont là, mais un passage reconstruit n'a pas d'inventaire d'originaux : on le"
                        + " DIT au lieu de prétendre « introuvables »")
                .isEqualTo(VoieReactivation.RECONSTRUIT);
        assertThat(rapport.reactivees()).isZero();
        assertThat(rapport.divergentes()).isZero();
        assertThat(rapport.decompte().total()).isEqualTo(sequences.size());
        assertThat(disponibilite.disponibilite(idPassage))
                .as("rien n'a été rebranché : l'audio reste absent")
                .isEqualTo(DisponibiliteAudio.ABSENTE);
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().archivee())
                .as("le passage reste archivé : l'audio n'est pas revenu")
                .isTrue();
    }

    @Test
    @DisplayName("#1650 : passage reconstruit HYDRATÉ depuis ses bruts + log : séquences régénérées, audio complet")
    void passage_reconstruit_hydrate_depuis_bruts() throws IOException {
        List<String> noms = reconstruireAvecBrutEtLog();

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.voie())
                .as("le dossier portait le log et les bruts : les séquences ont été régénérées")
                .isEqualTo(VoieReactivation.BRUTS);
        assertThat(rapport.reactivees()).isEqualTo(noms.size());
        assertThat(rapport.divergentes()).isZero();
        assertThat(rapport.confianceMinimale())
                .as("sans empreinte ni cris en base (passage reconstruit), la structurelle seule vaut FORTE")
                .isEqualTo(NiveauConfiance.FORTE);
        assertThat(rapport.complete()).isTrue();
        assertThat(disponibilite.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.COMPLETE);
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().archivee())
                .as("l'audio est revenu : le passage n'est plus archivé")
                .isFalse();
        assertThat(noms).allSatisfy(nom -> assertThat(transformes.resolve(nom)).exists());

        // #1651 : le placeholder est remplacé par de vrais originaux (avec fréquence), et les originaux sont
        // déclarés « purgés » (connus, prouvés par régénération, mais non stockés localement).
        assertThat(originalDao.findBySession(idSession))
                .isNotEmpty()
                .allSatisfy(original -> {
                    assertThat(original.frequenceEchantillonnageHz()).isNotNull();
                    assertThat(original.sha256())
                            .as("l'empreinte du brut est capturée à la régénération (#1726), plus déférée à null")
                            .isNotNull()
                            .matches("[0-9a-fA-F]{64}");
                })
                .noneMatch(original -> original.nomFichier().endsWith("reconstruit.wav"));
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().originauxPurges())
                .as("les originaux sont connus mais non stockés localement : déclarés purgés")
                .isTrue();
    }

    @Test
    @DisplayName("#1682 : hydratation même si l'acoustique discorde : structurel suffit, l'acoustique est un indice")
    void passage_reconstruit_hydrate_malgre_acoustique_discordante() throws IOException {
        List<String> noms = reconstruireAvecBrutEtLog();
        // Cris introuvables dans l'audio synthétique (bruit pseudo-aléatoire, sans pic à 8 kHz) : l'acoustique
        // discordera. Avec un veto (ancien comportement), tout serait REFUSÉ ; ici, les tranches sont des
        // extraits verbatim du brut désigné → acceptées sur structurel, l'acoustique n'étant qu'un indice.
        ServiceReactivationPassage avecCris = new ServiceReactivationPassage(
                sessionDao,
                sequenceDao,
                originalDao,
                new VerificationIdentiteAudio(),
                disponibilite,
                Optional.of(idSequence -> List.of(new CriAttendu(0.1, 0.4, 8_000))),
                Optional.of(regeneration),
                Optional.of(new InventaireParInspection(new InspecteurDossier(new AnalyseurLogPR()))),
                adoption,
                Optional.empty());

        RapportReactivation rapport = avecCris.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees())
                .as("l'acoustique discordante NE DOIT PAS refuser une tranche régénérée depuis le brut désigné")
                .isEqualTo(noms.size());
        assertThat(rapport.divergentes())
                .as("aucun refus : le veto acoustique est retiré pour les tranches régénérées")
                .isZero();
        assertThat(rapport.confianceMinimale()).isEqualTo(NiveauConfiance.FORTE);
        assertThat(rapport.indiceAcoustique())
                .as("la concordance acoustique est tout de même mesurée et rapportée en indice")
                .isNotNull();
        assertThat(rapport.indiceAcoustique().mesurees()).isEqualTo(noms.size());
        assertThat(rapport.indiceAcoustique().concordantes())
                .as("cris introuvables dans le bruit synthétique : concordance faible, mais non bloquante")
                .isLessThan(noms.size());
    }

    @Test
    @DisplayName("#1724 : passage reconstruit sur carte SD multi-nuits : seule la nuit du passage est régénérée")
    void hydratation_multi_nuits_ne_regenere_que_la_nuit_du_passage() throws IOException {
        List<String> noms = reconstruireAvecBrutEtLog();
        // La sauvegarde porte aussi un brut d'une AUTRE nuit (même enregistreur, autre horodatage). Le
        // transformer serait du travail perdu : ses tranches ne se rebrancheraient sur aucune séquence.
        ecrireBrut(sauvegarde.resolve("PaRec_20260618_220000.wav"), 7);

        List<String> regenerations = new ArrayList<>();
        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {
            if (progres.libelle().startsWith("Régénération")) {
                regenerations.add(progres.libelle());
            }
        });

        assertThat(rapport.reactivees())
                .as("la nuit du passage reste intégralement régénérée")
                .isEqualTo(noms.size());
        assertThat(rapport.complete()).isTrue();
        assertThat(regenerations)
                .as("le brut de l'autre nuit est écarté AVANT la transformation : une seule régénération, pas deux")
                .hasSize(1);
    }

    @Test
    @DisplayName("#1406 : une carte SD contenant PLUSIEURS nuits ne réactive que celle du passage")
    void carte_sd_multi_nuits() throws IOException {
        List<String> noms = archiverAvecBrutSauvegarde(NOM_SD_BRUT, true);
        // La carte porte aussi les nuits d'avant et d'après : mêmes préfixes d'enregistreur, autres
        // horodatages. Rien ne les rattache à ce passage, et rien ne doit leur arriver.
        Path autreNuit1 = sauvegarde.resolve("PaRec_20260618_220000.wav");
        Path autreNuit2 = sauvegarde.resolve("PaRec_20260622_211500.wav");
        ecrireBrut(autreNuit1, 7);
        ecrireBrut(autreNuit2, 8);

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.voie()).isEqualTo(VoieReactivation.BRUTS);
        assertThat(rapport.reactivees()).isEqualTo(noms.size());
        assertThat(rapport.divergentes())
                .as("les bruts des autres nuits ne correspondent à aucun nom connu : ignorés, pas refusés")
                .isZero();
        assertThat(rapport.complete()).isTrue();
        assertThat(autreNuit1).exists();
        assertThat(autreNuit2)
                .as("la sauvegarde de l'utilisateur n'est jamais touchée")
                .exists();
    }

    @Test
    @DisplayName("#1406 : deux fichiers du même nom (copie tronquée + bonne copie) → la bonne est retrouvée")
    void homonymes_le_bon_fichier_est_retrouve() throws IOException {
        List<String> noms = archiverAvecBrutSauvegarde(NOM_SD_BRUT, true);
        // Une copie interrompue traîne dans la sauvegarde, sous le même nom, dans un dossier qui sort
        // AVANT l'autre dans l'ordre de parcours : s'arrêter au premier trouvé refuserait la nuit entière.
        Path copieTronquee = sauvegarde.resolve("00-copie-interrompue").resolve(NOM_SD_BRUT);
        ecrireBrut(copieTronquee, 123);

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees())
                .as("tous les homonymes sont confrontés au contenu attendu, pas seulement le premier venu")
                .isEqualTo(noms.size());
        assertThat(rapport.divergentes()).isZero();
        assertThat(rapport.confianceMinimale()).isEqualTo(NiveauConfiance.CERTITUDE);
    }

    @Test
    @DisplayName("#1406 : aucun des homonymes n'est le bon → le refus dit combien ont été essayés")
    void homonymes_tous_refuses_le_disent() throws IOException {
        archiverAvecBrutSauvegarde(NOM_SD_BRUT, false); // l'imposteur, à la racine
        ecrireBrut(sauvegarde.resolve("autre-copie").resolve(NOM_SD_BRUT), 77); // un second imposteur

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees()).isZero();
        assertThat(rapport.ecarts())
                .singleElement()
                .satisfies(ecart -> assertThat(ecart.motif()).contains("aucun des 2 fichiers"));
    }

    @Test
    @DisplayName("#1406 : un nom d'enregistreur contenant un tiret est retrouvé quand même")
    void nom_d_enregistreur_avec_tiret() throws IOException {
        List<String> noms = archiverAvecBrutSauvegarde(NOM_SD_AVEC_TIRET, true, NOM_SD_AVEC_TIRET);

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees())
                .as("le nom d'enregistreur s'obtient en retirant le PRÉFIXE, pas en coupant au dernier tiret")
                .isEqualTo(noms.size());
    }

    // --- Fixture ---------------------------------------------------------------------------------

    /// Sème un passage déposé avec deux séquences, écrit leurs WAV, en capture (ou non) l'identité,
    /// place une copie dans la « sauvegarde de l'utilisateur », puis **archive** (supprime les
    /// fichiers du workspace et pose le marqueur).
    ///
    /// @param avecEmpreinte pose taille + empreinte en base (import récent) ou non (import ancien)
    /// @param sauvegardeComplete copie les deux séquences dans la sauvegarde, ou seulement la première
    private void archiverAvecSauvegarde(boolean avecEmpreinte, boolean sauvegardeComplete) throws IOException {
        idPassage = passageDao
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.DEPOSE,
                        Verdict.OK,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Path racineSession = dossier.resolve(PREFIXE.nomDossierSession());
        idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 0L, 0L, idPassage))
                .id();
        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null,
                        "PaRec_20260620_213000.wav",
                        racineSession
                                .resolve("bruts")
                                .resolve("PaRec_20260620_213000.wav")
                                .toString(),
                        5.0,
                        384_000,
                        null,
                        idSession))
                .id();

        int index = 0;
        for (String nom : List.of(SEQ_1, SEQ_2)) {
            Path fichier = transformes.resolve(nom);
            ecrireWav(fichier, 7 + index); // contenus distincts d'une séquence à l'autre
            if (sauvegardeComplete || index == 0) {
                Files.copy(fichier, sauvegarde.resolve(nom));
            }
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    nom,
                    idOriginal,
                    index,
                    index * 5.0,
                    DUREE_REELLE_S,
                    fichier.toString(),
                    false,
                    idSession,
                    null,
                    avecEmpreinte ? Files.size(fichier) : null,
                    avecEmpreinte ? Empreintes.empreinteCourte(fichier) : null));
            Files.delete(fichier); // archivage : l'audio quitte le workspace
            index++;
        }
        sessionDao.marquerArchivee(idSession, LocalDateTime.of(2026, 7, 13, 18, 30));
    }

    /// WAV synthétique écrit comme le pipeline (en-tête à Fe/10, durée réelle [#DUREE_REELLE_S]) ; la
    /// `graine` détermine le contenu : deux graines différentes donnent deux audios différents.
    private void ecrireWav(Path fichier, int graine) throws IOException {
        int echantillons = (int) Math.round(DUREE_REELLE_S * FREQUENCE_REELLE_HZ);
        byte[] pcm = new byte[echantillons * 2];
        int valeur = graine;
        for (int n = 0; n < echantillons; n++) {
            valeur = valeur * 31 + 17;
            short amplitude = (short) (valeur % 8000);
            pcm[2 * n] = (byte) (amplitude & 0xFF);
            pcm[2 * n + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        FichierWav.ecrire(fichier, 1, (int) (FREQUENCE_REELLE_HZ / 10), 16, pcm, 0, pcm.length);
    }

    /// Sème un passage dont **seul le brut** a été sauvegardé : les séquences sont produites par la
    /// **vraie transformation** (c'est le seul moyen de prouver que la régénération les reproduit), leur
    /// identité est capturée en base comme à l'import, puis l'audio transformé est supprimé (archivage).
    ///
    /// @param nomDansLaSauvegarde nom sous lequel l'utilisateur a gardé son brut (R6, ou nom de carte SD)
    /// @param brutIntact `false` pour placer dans la sauvegarde un fichier **du bon nom mais d'un autre
    ///     contenu** : le cas exact que la vérification doit attraper
    /// @return les noms des séquences attendues
    private List<String> archiverAvecBrutSauvegarde(String nomDansLaSauvegarde, boolean brutIntact) throws IOException {
        return archiverAvecBrutSauvegarde(nomDansLaSauvegarde, brutIntact, NOM_SD_BRUT);
    }

    /// Variante où le **nom d'enregistreur** du brut est choisi par le test (il peut contenir un tiret).
    private List<String> archiverAvecBrutSauvegarde(
            String nomDansLaSauvegarde, boolean brutIntact, String nomEnregistreur) throws IOException {
        String nomR6 = PREFIXE.nommerOriginal(nomEnregistreur);
        idPassage = passageDao
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.DEPOSE,
                        Verdict.OK,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Path racineSession = dossier.resolve(PREFIXE.nomDossierSession());
        idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 0L, 0L, idPassage))
                .id();

        // Le brut, tel qu'il sort de l'enregistreur : en-tête à Fe/10, contenu piloté par une graine.
        Path bruts = Files.createDirectories(racineSession.resolve("bruts"));
        Path brut = bruts.resolve(nomR6);
        ecrireBrut(brut, 42);

        // Les séquences, produites par la VRAIE chaîne : ce sont elles que la base connaîtra.
        TransformationOriginal transformation =
                new TransformationAudio().transformer(brut, nomR6, transformes, PREFIXE, FREQUENCE_ACQUISITION_HZ);

        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null,
                        nomR6,
                        brut.toString(),
                        transformation.dureeSourceSecondes(),
                        transformation.frequenceSourceHz(),
                        transformation.sha256(),
                        idSession,
                        transformation.tailleSourceOctets()))
                .id();

        List<String> noms = new ArrayList<>();
        for (SequenceProduite produite : transformation.sequences()) {
            noms.add(produite.nomFichier());
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    produite.nomFichier(),
                    idOriginal,
                    produite.index(),
                    produite.offsetSourceSecondes(),
                    produite.dureeSecondes(),
                    produite.chemin().toString(),
                    false,
                    idSession,
                    null,
                    produite.octets(),
                    produite.empreinte()));
            Files.delete(produite.chemin()); // archivage : les tranches quittent le disque
        }

        // Ce que l'utilisateur a gardé : son brut (intact, ou remplacé par un autre audio du même nom).
        Path garde = sauvegarde.resolve(nomDansLaSauvegarde);
        if (brutIntact) {
            Files.copy(brut, garde);
        } else {
            ecrireBrut(garde, 99); // même nom, autre contenu : l'imposteur
        }
        Files.delete(brut); // archivage : les bruts aussi ont été purgés
        sessionDao.marquerArchivee(idSession, LocalDateTime.of(2026, 7, 13, 18, 30));
        return noms;
    }

    /// Sème un passage **reconstruit** depuis la plateforme (#1305), à l'identique de
    /// `ServiceReconstructionPassages.creerSequences` : un **unique original placeholder**
    /// `<prefixe>reconstruit.wav` **sans empreinte ni fréquence d'acquisition**, et des séquences nommées
    /// d'après les titres CSV distants (noms de tranches), rattachées à ce placeholder, **sans fichier sur
    /// disque**. Puis place les **bruts** de la carte SD (sous leur nom d'enregistreur) dans la sauvegarde :
    /// ils sont là, mais rien en base ne permet de les relier.
    ///
    /// @return les noms des séquences reconstruites
    private List<String> reconstruireAvecBrutsEnSauvegarde() throws IOException {
        idPassage = passageDao
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.DEPOSE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Path racineSession = dossier.resolve(PREFIXE.nomDossierSession());
        idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 0L, 0L, idPassage))
                .id();
        Long idPlaceholder = originalDao
                .insert(new EnregistrementOriginal(
                        null, PREFIXE.prefixeFichier() + "reconstruit.wav", "", null, null, null, idSession))
                .id();

        List<String> noms = List.of(SEQ_1, SEQ_2);
        int index = 0;
        for (String nom : noms) {
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    nom,
                    idPlaceholder,
                    index,
                    null,
                    null,
                    transformes.resolve(nom).toString(),
                    false,
                    idSession,
                    null,
                    null,
                    null));
            index++;
        }
        sessionDao.marquerArchivee(idSession, LocalDateTime.of(2026, 7, 13, 18, 30));

        // Les bruts sont bien là, sous leur nom de carte SD : c'est tout l'objet du bug.
        ecrireBrut(sauvegarde.resolve(NOM_SD_BRUT), 42);
        return noms;
    }

    /// Sème un passage **reconstruit** dont l'utilisateur a gardé, dans sa sauvegarde, **le brut** (sous son
    /// nom de carte SD) **et le log** de l'enregistreur (qui porte la fréquence d'acquisition). Les séquences
    /// sont produites par la VRAIE transformation (ce sont leurs noms exacts que la base connaîtra, comme à
    /// la reconstruction depuis le CSV), puis leur audio est supprimé (archivage) ; les originaux se
    /// réduisent au placeholder de reconstruction.
    ///
    /// @return les noms des séquences reconstruites, que l'hydratation (#1650) doit régénérer
    private List<String> reconstruireAvecBrutEtLog() throws IOException {
        idPassage = passageDao
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.DEPOSE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Path racineSession = dossier.resolve(PREFIXE.nomDossierSession());
        idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 0L, 0L, idPassage))
                .id();
        Long idPlaceholder = originalDao
                .insert(new EnregistrementOriginal(
                        null, PREFIXE.prefixeFichier() + "reconstruit.wav", "", null, null, null, idSession))
                .id();

        // Le brut tel qu'il sort de l'enregistreur, transformé pour connaître les noms EXACTS des tranches.
        Path brut = dossier.resolve("origine").resolve(NOM_SD_BRUT);
        ecrireBrut(brut, 42);
        TransformationOriginal transformation = new TransformationAudio()
                .transformer(brut, NOM_R6_BRUT, transformes, PREFIXE, FREQUENCE_ACQUISITION_HZ);

        List<String> noms = new ArrayList<>();
        int index = 0;
        for (SequenceProduite produite : transformation.sequences()) {
            noms.add(produite.nomFichier());
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    produite.nomFichier(),
                    idPlaceholder,
                    index,
                    null,
                    null,
                    transformes.resolve(produite.nomFichier()).toString(),
                    false,
                    idSession,
                    null,
                    null,
                    null));
            Files.delete(produite.chemin()); // archivage : les tranches quittent le disque
            index++;
        }
        sessionDao.marquerArchivee(idSession, LocalDateTime.of(2026, 7, 13, 18, 30));

        // Ce que l'utilisateur a gardé : son brut (nom de carte SD) et le log de l'enregistreur.
        Files.copy(brut, sauvegarde.resolve(NOM_SD_BRUT));
        ecrireLog(sauvegarde.resolve("LogPR" + SERIE + ".txt"), FREQUENCE_ACQUISITION_HZ / 1000);
        Files.delete(brut);
        return noms;
    }

    /// Journal minimal de l'enregistreur : une ligne « Paramètres » porte la fréquence `Fe…kHz`, la seule
    /// chose que l'inventaire (#1649) y lit pour régénérer à l'identique.
    private void ecrireLog(Path fichier, int frequenceKhz) throws IOException {
        Files.createDirectories(fichier.getParent());
        Files.write(
                fichier,
                List.of(
                        "20/06/26 - 21:30:00 PR" + SERIE + " Démarrage v1.0",
                        "20/06/26 - 21:30:01 PR" + SERIE + " Paramètres : Acquisi. 21:30-05:15, Fe" + frequenceKhz
                                + "kHz, S. R. Med, Bd. Freq. 8-120kHz"),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /// Brut synthétique de [#DUREE_BRUT_S] secondes **réelles** : en-tête à `Fe/10` (comme l'écrit
    /// l'enregistreur PR), contenu déterminé par la `graine`.
    private void ecrireBrut(Path fichier, int graine) throws IOException {
        int echantillons = (int) Math.round(DUREE_BRUT_S * FREQUENCE_ACQUISITION_HZ);
        byte[] pcm = new byte[echantillons * 2];
        int valeur = graine;
        for (int n = 0; n < echantillons; n++) {
            valeur = valeur * 31 + 17;
            short amplitude = (short) (valeur % 8000);
            pcm[2 * n] = (byte) (amplitude & 0xFF);
            pcm[2 * n + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        Files.createDirectories(fichier.getParent());
        FichierWav.ecrire(fichier, 1, FREQUENCE_ACQUISITION_HZ / 10, 16, pcm, 0, pcm.length);
    }
}
