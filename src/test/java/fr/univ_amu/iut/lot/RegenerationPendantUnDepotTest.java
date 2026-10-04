package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.CauseRefus;
import fr.univ_amu.iut.lot.model.CompacteurDepot;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ModeDepot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.SuiviDepot;
import fr.univ_amu.iut.lot.model.TeleversementsEnCours;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.lot.model.dao.DepotPlanDao;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le geste que le compte rendu nomme après un contenu refusé, suivi **de bout en bout** (#5599).
///
/// « Régénérez les archives de la nuit, puis relancez » : le test de #3946 le disait vérifié, mais il
/// changeait la réponse simulée au lieu de régénérer. La génération réelle refusait, elle, tout passage
/// dont le dépôt était entamé, c'est-à-dire justement l'état où le conseil tombe. Ici rien n'est simulé
/// entre les deux dépôts : le service de lot et le moteur de dépôt partagent une vraie base, et seul le
/// réseau est simulé.
class RegenerationPendantUnDepotTest {

    private static final String SERIE = "1925492";
    private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");

    /// Deux séquences de 1 Kio sous un plafond de 1 500 octets : deux archives, une par séquence.
    private static final long PLAFOND = 1_500;

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ServiceLot service;
    private DepotVigieChiro depot;
    private ClientVigieChiro client;
    private PassageDao passageDao;

    /// Le registre PARTAGÉ, comme dans l'application : le dépôt s'y inscrit, le service le consulte.
    private final TeleversementsEnCours televersements = new TeleversementsEnCours();

    /// Le réglage du mode, relu à chaque dépôt comme dans l'application : un cas peut le changer en route.
    private final AtomicReference<ModeDepot> mode = new AtomicReference<>(ModeDepot.ARCHIVES_ZIP);

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        passageDao = new PassageDao(source);
        SessionDao sessionDao = new SessionDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        DepotUniteDao depotUnites = new DepotUniteDao(source);
        DepotPlanDao depotPlans = new DepotPlanDao(source);
        HorlogeFigee horloge = new HorlogeFigee(LocalDateTime.of(2026, 9, 14, 23, 10));
        service = new ServiceLot(
                passageDao,
                sessionDao,
                sequenceDao,
                new VerificationCoherence(
                        new SiteDao(source),
                        new PointDao(source),
                        sessionDao,
                        new EnregistrementOriginalDao(source),
                        sequenceDao,
                        new JournalDuCapteurDao(source),
                        new ReleveClimatiqueDao(source)),
                new MoteurWorkflowPassage(),
                horloge,
                () -> new CompacteurDepot(PLAFOND),
                mode::get,
                depotUnites,
                depotPlans,
                televersements);

        SynchronisationParticipation participations = mock(SynchronisationParticipation.class);
        when(participations.participationDe(any())).thenReturn(Optional.of("part-1"));
        client = mock(ClientVigieChiro.class);
        when(client.donnees(anyString())).thenReturn(ReponseApi.succes(List.of()));
        when(client.creerFichier(anyString(), anyString()))
                .thenAnswer(appel -> ReponseApi.succes(new FichierSigne(
                        "f-" + appel.getArgument(0), "https://vigiechiro.s3.amazonaws.com/x?Signature=a")));
        when(client.finaliserFichier(anyString())).thenReturn(ReponseApi.succes("{}"));
        depot = new DepotVigieChiro(
                participations,
                client,
                mock(TraitementVigieChiro.class),
                depotUnites,
                depotPlans,
                passageDao,
                new MoteurWorkflowPassage(),
                horloge,
                televersements);
    }

    @Test
    @DisplayName("#5599 : après un contenu refusé, régénérer puis relancer dépose ce qui manquait, et seulement cela")
    void regenerer_apres_un_contenu_refuse() throws Exception {
        Long id = passagePrepare();
        AtomicBoolean refuserLaSeconde = new AtomicBoolean(true);
        when(client.televerserVersS3(anyString(), any(Path.class), anyString(), any(), any()))
                .thenAnswer(appel -> {
                    Path archive = appel.getArgument(1);
                    boolean seconde = archive.getFileName().toString().endsWith("-2.zip");
                    return seconde && refuserLaSeconde.get()
                            ? ReponseApi.refuse(422, "contenu refusé")
                            : ReponseApi.succes("");
                });

        BilanDepot premier = depot.deposer(id, service.sourceDepotParDefaut(id), () -> false, SuiviDepot.inerte());

        assertThat(premier.deposees()).as("l'archive 1 est en ligne").isEqualTo(1);
        assertThat(premier.echecs())
                .singleElement()
                .satisfies(echec -> assertThat(echec.cause()).isEqualTo(CauseRefus.CONTENU));
        assertThat(statut(id))
                .as("c'est dans cet état que le compte rendu conseille de régénérer")
                .isEqualTo(StatutWorkflow.DEPOT_EN_COURS);

        // Le geste conseillé, par le VRAI service : c'est ici que « préparez-le d'abord » tombait.
        assertThat(service.genererArchivesDepot(id)).hasSize(2);

        refuserLaSeconde.set(false);
        BilanDepot second = depot.deposer(id, service.sourceDepotParDefaut(id), () -> false, SuiviDepot.inerte());

        assertThat(second.deposees()).as("seule l'archive refusée repart").isEqualTo(1);
        assertThat(second.echecs()).isEmpty();
        verify(client, times(1)).creerFichier(eq(PREFIXE.nomDossierSession() + "-1.zip"), anyString());
        assertThat(statut(id)).isEqualTo(StatutWorkflow.DEPOSE);
    }

    /// Le mode n'était pas mémorisé avec le dépôt : relu dans les réglages à chaque tentative, il faisait
    /// reprendre en WAV un dépôt entamé en ZIP. La reprise n'était pas refusée : l'empreinte du lot est la
    /// même dans les deux modes. Elle renvoyait toutes les séquences à côté de l'archive en ligne (#5677).
    @Test
    @DisplayName("#5677 : un dépôt entamé en ZIP se reprend en ZIP, même si le réglage dit désormais WAV")
    void un_depot_entame_garde_son_mode() throws Exception {
        Long id = passagePrepare();
        AtomicBoolean refuserLaSeconde = new AtomicBoolean(true);
        when(client.televerserVersS3(anyString(), any(Path.class), anyString(), any(), any()))
                .thenAnswer(appel -> {
                    Path archive = appel.getArgument(1);
                    boolean seconde = archive.getFileName().toString().endsWith("-2.zip");
                    return seconde && refuserLaSeconde.get()
                            ? ReponseApi.refuse(422, "contenu refusé")
                            : ReponseApi.succes("");
                });
        depot.deposer(id, service.sourceDepotParDefaut(id), () -> false, SuiviDepot.inerte());

        mode.set(ModeDepot.SEQUENCES_WAV);
        refuserLaSeconde.set(false);
        BilanDepot reprise = depot.deposer(id, service.sourceDepotParDefaut(id), () -> false, SuiviDepot.inerte());

        assertThat(reprise.deposees())
                .as("seule l'archive manquante repart, en ZIP")
                .isEqualTo(1);
        assertThat(reprise.echecs()).isEmpty();
        verify(client, never()).creerFichier(endsWith(".wav"), anyString());
        assertThat(statut(id)).isEqualTo(StatutWorkflow.DEPOSE);
    }

    @Test
    @DisplayName("#5677 : un dépôt entamé en WAV se reprend en WAV, même si le réglage dit désormais ZIP")
    void un_depot_entame_en_wav_reste_en_wav() throws Exception {
        Long id = passagePrepare();
        mode.set(ModeDepot.SEQUENCES_WAV);
        AtomicBoolean refuser = new AtomicBoolean(true);
        when(client.televerserVersS3(anyString(), any(Path.class), anyString(), any(), any()))
                .thenAnswer(appel ->
                        refuser.getAndSet(false) ? ReponseApi.refuse(422, "contenu refusé") : ReponseApi.succes(""));
        BilanDepot premier = depot.deposer(id, service.sourceDepotParDefaut(id), () -> false, SuiviDepot.inerte());
        assertThat(premier.echecs())
                .as("une séquence refusée : le dépôt reste entamé")
                .hasSize(1);

        mode.set(ModeDepot.ARCHIVES_ZIP);
        BilanDepot reprise = depot.deposer(id, service.sourceDepotParDefaut(id), () -> false, SuiviDepot.inerte());

        assertThat(reprise.deposees())
                .as("seule la séquence manquante repart, en WAV")
                .isEqualTo(1);
        verify(client, never()).creerFichier(endsWith(".zip"), anyString());
        assertThat(statut(id)).isEqualTo(StatutWorkflow.DEPOSE);
    }

    /// L'écran de lot lit la forme du dépôt pour n'offrir que les étapes qui servent (#5824) : ce doit
    /// être la règle même qui décide de ce qui part.
    @Test
    @DisplayName("#5824 : la forme du dépôt suit le réglage, puis le dépôt entamé quand il y en a un")
    void la_forme_du_depot_suit_le_reglage_puis_le_depot_entame() throws Exception {
        Long id = passagePrepare();
        assertThat(service.formeDuDepot(id)).as("sans dépôt entamé, le réglage").isEqualTo(ModeDepot.ARCHIVES_ZIP);
        when(client.televerserVersS3(anyString(), any(Path.class), anyString(), any(), any()))
                .thenAnswer(appel ->
                        ((Path) appel.getArgument(1)).getFileName().toString().endsWith("-2.zip")
                                ? ReponseApi.refuse(422, "contenu refusé")
                                : ReponseApi.succes(""));
        depot.deposer(id, service.sourceDepotParDefaut(id), () -> false, SuiviDepot.inerte());

        mode.set(ModeDepot.SEQUENCES_WAV);

        assertThat(service.formeDuDepot(id))
                .as("une archive est en ligne : le dépôt reste en ZIP, quoi que dise le réglage")
                .isEqualTo(ModeDepot.ARCHIVES_ZIP);
    }

    @Test
    @DisplayName("#5677 : un dépôt qui commence suit le réglage, WAV compris")
    void un_depot_qui_commence_suit_le_reglage() throws Exception {
        Long id = passagePrepare();
        mode.set(ModeDepot.SEQUENCES_WAV);

        assertThat(service.sourceDepotParDefaut(id).identifiants())
                .isNotEmpty()
                .allSatisfy(identifiant -> assertThat(identifiant).endsWith(".wav"));
    }

    @Test
    @DisplayName("#5599 : un dépôt s'inscrit pendant qu'il tourne, et se retire après, même sur une exception")
    void un_depot_s_inscrit_pendant_qu_il_tourne() throws Exception {
        Long id = passagePrepare();
        AtomicBoolean vuEnCours = new AtomicBoolean();
        when(client.televerserVersS3(anyString(), any(Path.class), anyString(), any(), any()))
                .thenAnswer(appel -> {
                    vuEnCours.set(televersements.enCours(id));
                    return ReponseApi.succes("");
                });

        depot.deposer(id, service.sourceDepotParDefaut(id), () -> false, SuiviDepot.inerte());

        assertThat(vuEnCours).as("pendant l'envoi, le passage est en cours").isTrue();
        assertThat(televersements.enCours(id)).as("après, il ne l'est plus").isFalse();

        // Un passage introuvable fait lever `deposer` : l'inscription ne doit pas lui survivre.
        assertThatThrownBy(
                        () -> depot.deposer(9_999L, service.sourceDepotParDefaut(id), () -> false, SuiviDepot.inerte()))
                .isInstanceOf(RuntimeException.class);
        assertThat(televersements.enCours(9_999L))
                .as("un registre resté armé bloquerait la génération à tort")
                .isFalse();
    }

    @Test
    @DisplayName("#5599 : pas de génération pendant un téléversement, et elle revient dès qu'il se termine")
    void pas_de_generation_pendant_un_televersement() throws Exception {
        Long id = passagePrepare();
        Path depotDuPassage = dossier.resolve(PREFIXE.nomDossierSession()).resolve("depot");

        try (TeleversementsEnCours.Inscription ignore = televersements.inscrire(id)) {
            assertThatThrownBy(() -> service.genererArchivesDepot(id))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("téléversement")
                    .hasMessageContaining("annulez")
                    .hasMessageNotContaining("préparez");
            assertThat(depotDuPassage)
                    .as("la source du téléversement écrit dans ce dossier : rien ne doit y être généré")
                    .doesNotExist();
        }

        assertThat(service.genererArchivesDepot(id))
                .as("le téléversement fini, la génération revient")
                .hasSize(2);
    }

    private StatutWorkflow statut(Long id) {
        return passageDao.findById(id).orElseThrow().statutWorkflow();
    }

    /// Un passage « Prêt à déposer », avec deux séquences réelles sur le disque.
    private Long passagePrepare() throws Exception {
        Passage passage = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("040962")
                .nomSite("Étang")
                .point("A1")
                .enregistreur(SERIE)
                .nuit(1, 2026, "2026-06-20")
                .statut(StatutWorkflow.VERIFIE)
                .verdict(Verdict.OK)
                .semerPassage()
                .lePassage();
        Path racine = dossier.resolve(PREFIXE.nomDossierSession());
        Long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, racine.toString(), null, 2048L, passage.id()))
                .id();
        Long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(
                        null, NOM_ORIGINAL, "bruts/" + NOM_ORIGINAL, 12.0, 384000, null, idSession))
                .id();
        Path transformes = Files.createDirectories(racine.resolve("transformes"));
        SequenceDao sequences = new SequenceDao(source);
        for (int i = 0; i < 2; i++) {
            String nom = PREFIXE.nommerSequence(NOM_ORIGINAL, i);
            sequences.insert(
                    new SequenceDEcoute(null, nom, idOriginal, i, i * 5.0, 5.0, "transformes/" + nom, true, idSession));
            Files.write(transformes.resolve(nom), new byte[1024]);
        }
        new JournalDuCapteurDao(source)
                .insert(new JournalDuCapteur(
                        null, "LogPR" + SERIE + ".txt", null, null, Completude.INCONNUE, idSession));
        service.preparerLot(passage.id());
        return passage.id();
    }
}
