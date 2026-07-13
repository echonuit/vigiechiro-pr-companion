package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.IssueLancement;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.ResultatLancement;
import fr.univ_amu.iut.commun.api.ResultatParticipation;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.SuiviDepot;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **Dépôt d'une nuit** ([DepotVigieChiro]) **reprenable par unité** (#982) : participation réutilisée ou
/// créée en repli ([SynchronisationParticipation] mockée), plan `depot_unite` **réel** (SQLite jetable,
/// #981), statuts honnêtes (#980 : « Dépôt en cours » à l'entame, « Déposé » seulement quand tout est en
/// ligne), reprise (les unités déjà déposées ne sont pas re-téléversées), upload **en flux** (le client
/// mocké reçoit le `Path`, jamais un tableau d'octets), annulation coopérative.
class DepotVigieChiroTest {

    private static final long ID_INEXISTANT = 999L;

    @TempDir
    Path racine;

    private SynchronisationParticipation participations;
    private ClientVigieChiro client;
    private TraitementVigieChiro traitementServeur;
    private DepotUniteDao depotUnites;
    private PassageDao passageDao;
    private DepotVigieChiro depot;
    private Long idPassage;

    @BeforeEach
    void preparer() {
        participations = mock(SynchronisationParticipation.class);
        client = mock(ClientVigieChiro.class);
        // Par défaut : le serveur répond et n'a encore aucune donnée (Mockito ne fabrique pas de
        // ReponseApi tout seul, contrairement aux List d'avant #1284).
        when(client.donnees(org.mockito.ArgumentMatchers.anyString())).thenReturn(ReponseApi.succes(List.of()));
        traitementServeur = mock(TraitementVigieChiro.class);
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(racine.resolve("ws")));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", "u-1"));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur("1925492", "V1.01", null));
        passageDao = new PassageDao(source);
        idPassage = passageDao
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-04-22",
                        "20:25:00",
                        "07:47:00",
                        null,
                        StatutWorkflow.PRET_A_DEPOSER,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        "1925492"))
                .id();
        depotUnites = new DepotUniteDao(source);
        depot = new DepotVigieChiro(
                participations,
                client,
                traitementServeur,
                depotUnites,
                passageDao,
                new MoteurWorkflowPassage(),
                new HorlogeFigee(LocalDate.of(2026, 7, 11)));
    }

    @Test
    @DisplayName("dépôt complet : plan persisté, unités « depose » avec id distant, passage « Déposé »")
    void depot_complet_bascule_depose(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "Car-1.zip");
        Path b = fichier(dossier, "seq_000.wav");
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        armerUploadOk();

        BilanDepot bilan = depot.deposer(idPassage, List.of(a, b));

        assertThat(bilan.participationId()).isEqualTo("part-1");
        assertThat(bilan.deposees()).isEqualTo(2);
        assertThat(bilan.estComplet()).isTrue();
        assertThat(depotUnites.parPassage(idPassage)).allSatisfy(unite -> {
            assertThat(unite.statut()).isEqualTo(StatutDepotUnite.DEPOSE);
            assertThat(unite.fichierIdDistant()).isEqualTo("f");
        });
        assertThat(statutPassage()).isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(passage().deposeLe()).isNotNull();
        verify(participations, never()).creerPour(anyLong());
    }

    @Test
    @DisplayName("#982 : upload EN FLUX — le client reçoit le Path du fichier, pas ses octets en mémoire")
    void upload_en_flux(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "Car-1.zip");
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        armerUploadOk();

        depot.deposer(idPassage, List.of(a));

        verify(client).televerserVersS3(anyString(), eq(a), eq("application/zip"), any());
        verify(client, never()).televerserVersS3(anyString(), any(byte[].class), anyString());
        // #984 : le fichier est déclaré AVEC son lien de participation (sinon orphelin côté serveur → le
        // compute « extrait 0 fichier »).
        verify(client).creerFichier(eq("Car-1.zip"), eq("part-1"));
    }

    @Test
    @DisplayName("#984 : dépôt ZIP pur → réconciliation par titre sautée (aucun GET /donnees, source du stall)")
    void depot_zip_saute_la_reconciliation(@TempDir Path dossier) throws IOException {
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        armerUploadOk();

        depot.deposer(idPassage, List.of(fichier(dossier, "Car-1.zip"), fichier(dossier, "Car-2.zip")));

        verify(client, never()).donnees(anyString());
    }

    @Test
    @DisplayName("#984 : dès qu'une unité WAV est présente, la réconciliation par titre s'exécute (GET /donnees)")
    void depot_wav_declenche_la_reconciliation(@TempDir Path dossier) throws IOException {
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        armerUploadOk();

        depot.deposer(idPassage, List.of(fichier(dossier, "seq_000.wav")));

        verify(client).donnees("part-1");
    }

    @Test
    @DisplayName("#984 : l'avancement du PUT est remonté au suivi par unité (uniteProgresse)")
    void progression_upload_remontee_au_suivi(@TempDir Path dossier) throws IOException {
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(client.creerFichier(anyString(), anyString()))
                .thenReturn(Optional.of(new FichierSigne("f", "https://s3/x")));
        when(client.finaliserFichier(anyString())).thenReturn(true);
        // Le client mocké invoque le callback de progression comme le ferait un vrai PUT streamé.
        when(client.televerserVersS3(anyString(), any(Path.class), anyString(), any()))
                .thenAnswer(invocation -> {
                    DoubleConsumer progression = invocation.getArgument(3);
                    progression.accept(0.5);
                    progression.accept(1.0);
                    return true;
                });
        SuiviDepot suivi = mock(SuiviDepot.class);

        depot.deposer(idPassage, List.of(fichier(dossier, "Car-1.zip")), () -> false, suivi);

        verify(suivi).uniteProgresse("Car-1.zip", 0.5);
        verify(suivi).uniteProgresse("Car-1.zip", 1.0);
    }

    @Test
    @DisplayName("#984 : lancerTraitement délègue le compute au client pour la participation liée")
    void lancer_traitement_delegue_compute() {
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(traitementServeur.etat("part-1")).thenReturn(ReponseApi.succes(Traitement.absent())); // jamais calculée
        when(traitementServeur.lancer("part-1")).thenReturn(ResultatLancement.accepte());

        assertThat(depot.lancerTraitement(idPassage).issue()).isEqualTo(IssueLancement.ACCEPTE);
        verify(traitementServeur).lancer("part-1");
    }

    @Test
    @DisplayName("#1261 : une nuit DÉJÀ analysée n'est pas relancée (le recalcul détruirait ses observations)")
    void lancer_traitement_bloque_la_relance_destructrice() {
        // Le serveur, lui, accepterait : il supprimerait toutes les donnees pour recalculer, et sur un
        // dépôt en archives ZIP il ne peut plus relire les WAV (#1244) → observations perdues. On refuse
        // donc de notre propre chef, SANS même appeler le serveur.
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(traitementServeur.etat("part-1")).thenReturn(ReponseApi.succes(traitement(EtatTraitement.FINI)));

        assertThat(depot.lancerTraitement(idPassage).issue()).isEqualTo(IssueLancement.RELANCE_BLOQUEE);
        verify(traitementServeur, never()).lancer(anyString());
    }

    @Test
    @DisplayName("#1261 : une analyse EN ÉCHEC n'est pas relancée non plus, sauf demande explicite (forcer)")
    void lancer_traitement_relance_forcee() {
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(traitementServeur.etat("part-1")).thenReturn(ReponseApi.succes(traitement(EtatTraitement.ERREUR)));
        when(traitementServeur.lancer("part-1")).thenReturn(ResultatLancement.accepte());

        assertThat(depot.lancerTraitement(idPassage).issue()).isEqualTo(IssueLancement.RELANCE_BLOQUEE);

        assertThat(depot.lancerTraitement(idPassage, true).issue()).isEqualTo(IssueLancement.ACCEPTE);
        verify(traitementServeur).lancer("part-1");
    }

    @Test
    @DisplayName("#1261 : un traitement EN COURS n'est pas une relance (rien à écraser) : le serveur tranche")
    void lancer_traitement_en_cours_passe_au_serveur() {
        // La garde ne s'applique pas : il n'y a pas encore de résultat à détruire. C'est le serveur qui
        // refusera la demande concurrente (400 « Already »), et le client la traduira en DEJA_LANCE.
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(traitementServeur.etat("part-1")).thenReturn(ReponseApi.succes(traitement(EtatTraitement.EN_COURS)));
        when(traitementServeur.lancer("part-1"))
                .thenReturn(ResultatLancement.dejaLance(traitement(EtatTraitement.EN_COURS)));

        assertThat(depot.lancerTraitement(idPassage).issue()).isEqualTo(IssueLancement.DEJA_LANCE);
        verify(traitementServeur).lancer("part-1");
    }

    @Test
    @DisplayName("#984 : lancerTraitement refuse un passage sans participation liée (rien à traiter)")
    void lancer_traitement_sans_participation_refuse() {
        when(participations.participationDe(idPassage)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depot.lancerTraitement(idPassage))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("déposez d'abord");
        verify(traitementServeur, never()).lancer(anyString());
    }

    /// Traitement serveur dans l'état voulu (les dates n'entrent pas en jeu dans la garde).
    private static Traitement traitement(EtatTraitement etat) {
        return new Traitement(etat, null, null, null, null, null);
    }

    @Test
    @DisplayName("#984 : participationLiee reflète le lien local (sans réseau)")
    void participation_liee_reflete_le_lien() {
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"), Optional.empty());

        assertThat(depot.participationLiee(idPassage)).isTrue();
        assertThat(depot.participationLiee(idPassage)).isFalse();
    }

    @Test
    @DisplayName("bug corrigé : un dépôt PARTIEL laisse « Dépôt en cours » (jamais « Déposé »), échec consigné")
    void depot_partiel_ne_bascule_pas_depose(@TempDir Path dossier) throws IOException {
        Path ok = fichier(dossier, "ok.wav");
        Path ko = fichier(dossier, "ko.wav");
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(client.creerFichier(eq("ok.wav"), anyString()))
                .thenReturn(Optional.of(new FichierSigne("f", "https://s3/x")));
        when(client.creerFichier(eq("ko.wav"), anyString())).thenReturn(Optional.empty()); // déclaration refusée
        when(client.televerserVersS3(anyString(), any(Path.class), anyString(), any()))
                .thenReturn(true);
        when(client.finaliserFichier(anyString())).thenReturn(true);

        BilanDepot bilan = depot.deposer(idPassage, List.of(ok, ko));

        assertThat(bilan.deposees()).isEqualTo(1);
        assertThat(bilan.echecs()).containsExactly("ko.wav");
        assertThat(statutPassage()).isEqualTo(StatutWorkflow.DEPOT_EN_COURS);
        DepotUnite enEchec = depotUnites.restantes(idPassage).getFirst();
        assertThat(enEchec.statut()).isEqualTo(StatutDepotUnite.ECHEC);
        assertThat(enEchec.messageErreur()).contains("déclaration");
    }

    @Test
    @DisplayName("reprise : une 2e tentative ne re-téléverse que les unités restantes, puis « Déposé »")
    void reprise_ne_redepose_que_les_restantes(@TempDir Path dossier) throws IOException {
        Path ok = fichier(dossier, "ok.wav");
        Path ko = fichier(dossier, "ko.wav");
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(client.creerFichier(eq("ok.wav"), anyString()))
                .thenReturn(Optional.of(new FichierSigne("f", "https://s3/x")));
        when(client.creerFichier(eq("ko.wav"), anyString())).thenReturn(Optional.empty());
        when(client.televerserVersS3(anyString(), any(Path.class), anyString(), any()))
                .thenReturn(true);
        when(client.finaliserFichier(anyString())).thenReturn(true);
        depot.deposer(idPassage, List.of(ok, ko)); // 1re tentative : ko.wav en échec

        when(client.creerFichier(eq("ko.wav"), anyString()))
                .thenReturn(Optional.of(new FichierSigne("f2", "https://s3/y")));
        BilanDepot reprise = depot.deposer(idPassage, List.of(ok, ko));

        // Seule l'unité en échec a été re-téléversée : ok.wav n'a été déclaré qu'une seule fois en tout.
        verify(client, times(1)).creerFichier(eq("ok.wav"), anyString());
        assertThat(reprise.deposees()).isEqualTo(1);
        assertThat(reprise.estComplet()).isTrue();
        assertThat(statutPassage()).isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(depotUnites.toutesDeposees(idPassage)).isTrue();
    }

    @Test
    @DisplayName("#984 : annulation coopérative → aucune unité non entamée n'est téléversée, « Dépôt en cours »")
    void annulation_cooperative(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "a.wav");
        Path b = fichier(dossier, "b.wav");
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));

        // En parallèle, chaque worker consulte le garde AVANT de démarrer son upload. Annulation déjà
        // demandée → aucune unité n'est entamée, le passage reste « Dépôt en cours » (reprenable).
        BilanDepot bilan = depot.deposer(idPassage, List.of(a, b), () -> true, SuiviDepot.inerte());

        assertThat(bilan.deposees()).isZero();
        assertThat(statutPassage()).isEqualTo(StatutWorkflow.DEPOT_EN_COURS);
        assertThat(depotUnites.restantes(idPassage)).hasSize(2);
        verify(client, never()).creerFichier(anyString(), anyString());
    }

    @Test
    @DisplayName("#984 : dépôt parallèle de plusieurs unités → toutes déposées (écritures depot_unite concurrentes)")
    void depot_parallele_toutes_deposees(@TempDir Path dossier) throws IOException {
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        armerUploadOk();
        List<Path> fichiers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            fichiers.add(fichier(dossier, "Car-" + i + ".zip"));
        }

        BilanDepot bilan = depot.deposer(idPassage, fichiers);

        assertThat(bilan.deposees()).isEqualTo(12);
        assertThat(bilan.estComplet()).isTrue();
        assertThat(depotUnites.parPassage(idPassage))
                .hasSize(12)
                .allSatisfy(unite -> assertThat(unite.statut()).isEqualTo(StatutDepotUnite.DEPOSE));
    }

    @Test
    @DisplayName("création refusée par VigieChiro (repli) → refus dur avec le détail de l'API, aucun upload")
    void participation_refusee() {
        when(participations.participationDe(idPassage)).thenReturn(Optional.empty());
        when(participations.creerPour(idPassage))
                .thenReturn(ResultatParticipation.echouee("HTTP 422 — {\"_errors\":{\"numero\":\"invalid field\"}}"));

        assertThatThrownBy(() -> depot.deposer(idPassage, List.of()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("refusée")
                .hasMessageContaining("422"); // le vrai détail de l'API est remonté, pas un message générique
        verify(client, never()).creerFichier(anyString(), anyString());
    }

    @Test
    @DisplayName("site non rattaché (repli) → l'exception de la passerelle se propage, aucun upload")
    void site_non_rattache_propage() {
        when(participations.participationDe(idPassage)).thenReturn(Optional.empty());
        when(participations.creerPour(idPassage)).thenThrow(new RegleMetierException("Site non rattaché à VigieChiro"));

        assertThatThrownBy(() -> depot.deposer(idPassage, List.of()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("non rattaché");
        verify(client, never()).creerFichier(anyString(), anyString());
    }

    @Test
    @DisplayName("passage introuvable → refus métier explicite (avant tout téléversement)")
    void passage_introuvable(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "a.wav");
        when(participations.participationDe(ID_INEXISTANT)).thenReturn(Optional.of("part-1"));

        assertThatThrownBy(() -> depot.deposer(ID_INEXISTANT, List.of(a)))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
        verify(client, never()).creerFichier(anyString(), anyString());
    }

    @Test
    @DisplayName("#1046 pré-vol : participation liée ≠ passage (point/nuit) → refus dur, ni plan ni téléversement")
    void prevol_refuse_participation_discordante(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "a.wav");
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(participations.ecartsAvecDistant(idPassage))
                .thenReturn(List.of("nuit du 2026-04-22 en local, du 2026-04-23 sur la participation"));

        assertThatThrownBy(() -> depot.deposer(idPassage, List.of(a)))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("ne correspond pas")
                .hasMessageContaining("2026-04-23"); // l'écart précis est remonté, pas un message générique
        verify(client, never()).creerFichier(anyString(), anyString());
        assertThat(depotUnites.parPassage(idPassage)).isEmpty();
        assertThat(statutPassage()).isEqualTo(StatutWorkflow.PRET_A_DEPOSER);
    }

    @Test
    @DisplayName("#1046 réconciliation : un WAV déjà traité côté serveur (titre sans extension) n'est pas re-téléversé")
    void reconciliation_marque_les_wav_deja_traites(@TempDir Path dossier) throws IOException {
        Path dejaEnLigne = fichier(dossier, "seq_000.wav");
        Path restant = fichier(dossier, "seq_001.wav");
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(client.donnees("part-1"))
                .thenReturn(ReponseApi.succes(List.of(new DonneeVigieChiro("d1", "seq_000", List.of()))));
        armerUploadOk();

        BilanDepot bilan = depot.deposer(idPassage, List.of(dejaEnLigne, restant));

        verify(client, never()).creerFichier(eq("seq_000.wav"), anyString());
        verify(client).creerFichier(eq("seq_001.wav"), anyString());
        assertThat(bilan.deposees()).isEqualTo(1); // « cette fois-ci » : la réconciliation n'en fait pas partie
        assertThat(depotUnites.toutesDeposees(idPassage)).isTrue();
        assertThat(statutPassage()).isEqualTo(StatutWorkflow.DEPOSE);
    }

    @Test
    @DisplayName("#1046 réconciliation : tout est déjà en ligne → « Déposé » sans aucun téléversement")
    void reconciliation_complete_bascule_depose(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "seq_000.wav");
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(client.donnees("part-1"))
                .thenReturn(ReponseApi.succes(List.of(new DonneeVigieChiro("d1", "seq_000", List.of()))));

        BilanDepot bilan = depot.deposer(idPassage, List.of(a));

        assertThat(bilan.deposees()).isZero();
        assertThat(bilan.estComplet()).isTrue();
        verify(client, never()).creerFichier(anyString(), anyString());
        assertThat(statutPassage()).isEqualTo(StatutWorkflow.DEPOSE);
    }

    @Test
    @DisplayName("#1046 réconciliation : une archive ZIP homonyme n'est jamais appariée (contenu inconnu localement)")
    void reconciliation_ignore_les_zip(@TempDir Path dossier) throws IOException {
        Path archive = fichier(dossier, "Car-1.zip");
        when(participations.participationDe(idPassage)).thenReturn(Optional.of("part-1"));
        when(client.donnees("part-1"))
                .thenReturn(ReponseApi.succes(List.of(new DonneeVigieChiro("d1", "Car-1", List.of()))));
        armerUploadOk();

        depot.deposer(idPassage, List.of(archive));

        verify(client).creerFichier(eq("Car-1.zip"), anyString()); // téléversée malgré le titre homonyme côté serveur
    }

    private StatutWorkflow statutPassage() {
        return passage().statutWorkflow();
    }

    private Passage passage() {
        return passageDao.findById(idPassage).orElseThrow();
    }

    private void armerUploadOk() {
        when(client.creerFichier(anyString(), anyString()))
                .thenReturn(Optional.of(new FichierSigne("f", "https://s3/x")));
        when(client.televerserVersS3(anyString(), any(Path.class), anyString(), any()))
                .thenReturn(true);
        when(client.finaliserFichier(anyString())).thenReturn(true);
    }

    private static Path fichier(Path dossier, String nom) throws IOException {
        Path chemin = dossier.resolve(nom);
        Files.write(chemin, new byte[] {1, 2, 3});
        return chemin;
    }
}
