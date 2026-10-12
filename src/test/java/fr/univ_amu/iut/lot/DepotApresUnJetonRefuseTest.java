package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientSurReseauSimule;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.CompacteurDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ModeDepot;
import fr.univ_amu.iut.lot.model.RearmementDepotUnites;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.SuiviDepot;
import fr.univ_amu.iut.lot.model.TeleversementsEnCours;
import fr.univ_amu.iut.lot.model.UniteDeDepot;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.lot.model.dao.DepotPlanDao;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.lot.viewmodel.CompteRenduChiffreDepot;
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
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Un `401` de l'API au milieu d'un dépôt de plusieurs unités (#6151).
///
/// Depuis #6151 ce `401` efface le jeton enregistré : les unités qui partent ensuite ne trouvent plus de
/// jeton et reviennent « non connecté », sans requête. Ce banc vérifie qu'elles restent ce qu'elles
/// auraient été avec le `401` : des refus de **droits**, que la reconnexion réarme, et pour lesquels le
/// compte rendu dit « Reconnectez-vous ». Sans ce classement, elles seraient comptées comme refus de
/// contenu, avec le conseil de régénérer.
///
/// Rien n'est simulé entre le dépôt et le réseau : le vrai client, le vrai transport et le vrai
/// stockage de connexion, sur une vraie base.
class DepotApresUnJetonRefuseTest {

    private static final String SERIE = "1925492";
    private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");
    private static final int SEQUENCES = 10;

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ServiceLot service;
    private DepotVigieChiro depot;
    private DepotUniteDao depotUnites;
    private StockageConnexion stockage;
    private HorlogeFigee horloge;

    /// Les déclarations de fichier qui ont VRAIMENT atteint le réseau.
    private final AtomicInteger declarations = new AtomicInteger();

    @BeforeEach
    void preparer() throws Exception {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        PassageDao passageDao = new PassageDao(source);
        SessionDao sessionDao = new SessionDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        depotUnites = new DepotUniteDao(source);
        DepotPlanDao depotPlans = new DepotPlanDao(source);
        horloge = new HorlogeFigee(LocalDateTime.of(2026, 9, 14, 23, 10));
        TeleversementsEnCours televersements = new TeleversementsEnCours();
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
                () -> new CompacteurDepot(1_500),
                () -> ModeDepot.SEQUENCES_WAV,
                depotUnites,
                depotPlans,
                televersements);

        stockage = new StockageConnexion(new Workspace(dossier), horloge);
        stockage.enregistrer("jeton-mort", new ProfilVigieChiro("u-1", "chiro", "observateur"));
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(appel -> repondre(appel.getArgument(0)));

        SynchronisationParticipation participations = mock(SynchronisationParticipation.class);
        when(participations.participationDe(any())).thenReturn(Optional.of("part-1"));
        depot = new DepotVigieChiro(
                participations,
                ClientSurReseauSimule.sur(http, stockage),
                mock(TraitementVigieChiro.class),
                depotUnites,
                depotPlans,
                passageDao,
                new MoteurWorkflowPassage(),
                horloge,
                televersements);
    }

    @Test
    @DisplayName(
            "#6151 : après un 401 au milieu d'un dépôt, toutes les unités se réarment et le compte rendu dit de se reconnecter")
    void apres_un_401_toutes_les_unites_se_rearment() throws Exception {
        Long id = passagePrepare();

        BilanDepot bilan = depot.deposer(id, service.sourceDepotParDefaut(id), () -> false, SuiviDepot.inerte());

        assertThat(stockage.token())
                .as("le 401 de l'API a effacé le jeton enregistré")
                .isEmpty();
        assertThat(declarations.get())
                .as("passé le refus, les unités suivantes ne repartent pas avec un jeton mort")
                .isLessThan(SEQUENCES);
        assertThat(bilan.echecs())
                .hasSize(SEQUENCES)
                .allSatisfy(echec -> assertThat(echec.seRearmeParUneReconnexion())
                        .as(
                                "unité %s : un jeton refusé ou absent se répare en se reconnectant",
                                echec.identifiantUnite())
                        .isTrue());

        String compteRendu = CompteRenduChiffreDepot.de(
                        bilan, new CompteRenduChiffreDepot.Plan(SEQUENCES, 0, false, UniteDeDepot.SEQUENCE), List.of())
                .toString();
        assertThat(compteRendu).contains("Reconnectez-vous").doesNotContain("Régénérez");

        new RearmementDepotUnites(depotUnites, horloge).reconnexionReussie();
        assertThat(depotUnites.parPassage(id))
                .as("la reconnexion les rend toutes à déposer")
                .hasSize(SEQUENCES)
                .extracting(DepotUnite::statut)
                .containsOnly(StatutDepotUnite.A_DEPOSER);
    }

    /// Un passage « Prêt à déposer », avec dix séquences réelles sur le disque : assez pour que des unités
    /// partent après le premier refus, le dépôt n'en envoyant que cinq à la fois.
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
        for (int i = 0; i < SEQUENCES; i++) {
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

    /// Le serveur simulé : toute déclaration de fichier est refusée en `401`, le reste répond vide.
    private HttpResponse<Object> repondre(HttpRequest requete) {
        if ("POST".equals(requete.method()) && requete.uri().getPath().endsWith("/fichiers")) {
            declarations.incrementAndGet();
            return reponse(401, "{\"_status\":\"ERR\",\"_error\":{\"code\":401}}");
        }
        return reponse(200, "{\"_meta\":{\"total\":0},\"_items\":[]}");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<Object> reponse(int statut, String corps) {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        HttpResponse<Object> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        when(reponse.body()).thenAnswer(appel -> new ByteArrayInputStream(octets));
        when(reponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (nom, valeur) -> true));
        return reponse;
    }
}
