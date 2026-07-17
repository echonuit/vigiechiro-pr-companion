package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.audit.model.SeveriteConstat;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.api.TaxonVigieChiro;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.DisponibiliteAudio;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.VoieReactivation;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Test E2E de restauration : une machine vierge se reconstruit depuis la plateforme** (#1050, EPIC
/// #1154).
///
/// C'est la promesse de l'issue G : *« sur une installation vierge (nouvelle machine, base perdue),
/// reconstruire depuis la plateforme tout ce qu'elle connaît de l'observateur »*. Le code de cette
/// promesse existe — la pagination des participations (#1150), le rapprochement des sites
/// (`RapprochementSites`), la reconstruction des passages (#1305) — mais il a été livré **par morceaux,
/// dans trois chantiers différents**, et **personne n'avait jamais exercé la chaîne entière**. Ce test
/// est la preuve qui manquait, et il est le prérequis du reset guidé (#1151), dont il constitue
/// l'étape 4.
///
/// **Le parcours, sur une base réellement vide** (aucun site, aucun point, aucun passage) :
///
/// 1. **synchroniser** : les rapprocheurs du socle (`Set<RapprochementVigieChiro>`, ceux-là mêmes que
///    déclenche la connexion) créent les **sites et leurs points** à partir des participations, puis les
///    **passages en squelettes** (#1707) : point + date + n°, archivés, sans observations ;
/// 2. **retrouver** : la nuit déposée sur la plateforme est désormais un **passage local** (elle n'est
///    plus une « orpheline »), consultable dans l'historique de son carré ;
/// 3. **auditer** : le workspace, réduit à des squelettes, est **sain**. Un passage sans audio n'est pas
///    un passage cassé — c'est un passage **archivé** (#1297), et l'audit informe au lieu de crier
///    (#1303, garde-fou #1719).
///
/// La plateforme est bouchonnée ([ClientVigieChiro] mocké, substitué dans l'injecteur **réel** par
/// `Modules.override`) : tout le reste est le vrai câblage de l'application.
///
/// ⚠️ Ce que le test **ne** prouve **pas**, parce que ce n'est pas encore fait : l'**hydratation** du
/// squelette (séquences, observations, matériel, météo) à la demande de l'utilisateur — c'est le geste
/// suivant (#1710). Et ce qu'il ne prouvera jamais, parce que c'est faux : que l'audio revienne d'un
/// dépôt ZIP (le mode par défaut ne laisse **aucun** audio sur le serveur).
class ParcoursRestaurationDepuisVigieChiroE2ETest {

    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";
    private static final String CARRE = "130711";
    private static final String POINT = "Z41";
    private static final String SITE_TITRE = "Vigiechiro - Point Fixe-" + CARRE;
    private static final String SEQ_1 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000";
    private static final String SEQ_2 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220534_000";
    private static final int FREQUENCE_ACQUISITION_HZ = 40_000;
    private static final double DUREE_BRUT_S = 12.0;

    @Test
    @DisplayName("Base vierge : la synchro rapatrie sites, points et passages (en squelettes archivés)")
    void base_vierge_restauree_depuis_la_plateforme() throws Exception {
        ClientVigieChiro client = plateformeBouchonnee();
        Injector injector = injecteurAvec(client);
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        assertThat(new PassageDao(source).findAll())
                .as("le point de départ est une base réellement vide : rien à restaurer depuis le disque")
                .isEmpty();

        // 1. Synchroniser : ce que fait la connexion (ConnexionViewModel#amorcerRapprochements) et la CLI
        //    `synchroniser-vigiechiro`. Les sites VIENNENT des participations ; les passages en découlent
        //    (#1707), en SQUELETTE. Les rapprocheurs ne sont pas ordonnés et le squelette exige que son
        //    point soit déjà local : DEUX passes garantissent l'état final quel que soit l'ordre - c'est
        //    l'idempotence de la synchro, et le « à la synchro suivante » documenté pour un point tout neuf.
        for (int passe = 0; passe < 2; passe++) {
            for (RapprochementVigieChiro rapprocheur :
                    injector.getInstance(Key.get(new TypeLiteral<Set<RapprochementVigieChiro>>() {}))) {
                rapprocheur.synchroniser(client);
            }
        }

        ServiceSites serviceSites = injector.getInstance(ServiceSites.class);
        String idUtilisateur = injector.getInstance(Key.get(String.class, Names.named("idUtilisateurCourant")));
        List<Site> sites = serviceSites.listerSites(idUtilisateur);
        assertThat(sites)
                .as("le site du carré %s est créé depuis la participation", CARRE)
                .singleElement()
                .satisfies(site -> assertThat(site.numeroCarre()).isEqualTo(CARRE));
        assertThat(new PointDao(source).findBySite(sites.getFirst().id()))
                .as("ses points d'écoute aussi : sans eux, aucune participation ne serait rattachable")
                .hasSize(1);

        // 2. Retrouver : la nuit déposée sur la plateforme est désormais un PASSAGE LOCAL, en squelette. Elle
        //    n'est donc plus une « orpheline » (participation sans passage). Son hydratation - séquences,
        //    observations, matériel, météo - est le geste SUIVANT (#1710), à la demande de l'utilisateur.
        ServiceReconstructionPassages reconstruction = injector.getInstance(
                        Key.get(new TypeLiteral<Optional<ServiceReconstructionPassages>>() {}))
                .orElseThrow();
        assertThat(reconstruction.orphelines())
                .as("la synchro a consommé l'orpheline : la nuit est un passage local (squelette)")
                .isEmpty();

        List<Passage> passages = new PassageDao(source).findAll();
        assertThat(passages)
                .as("un seul passage : le squelette de la nuit rapatriée")
                .hasSize(1);
        Passage squelette = passages.getFirst();
        assertThat(squelette.statutWorkflow())
                .as("la participation existe sur la plateforme : le passage est déposé")
                .isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(squelette.idEnregistreur())
                .as("aucun détail téléchargé pour un squelette : enregistreur honnêtement « inconnu »")
                .isEqualTo("INCONNU");
        SessionDao sessionDao = new SessionDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        SessionDEnregistrement session =
                sessionDao.trouverParPassage(squelette.id()).orElseThrow();
        assertThat(session.archivee())
                .as("un squelette naît archivé : rien n'a jamais été importé ici")
                .isTrue();
        assertThat(sequenceDao.findBySession(session.id()))
                .as("un squelette n'a pas encore de séquence : l'hydratation (#1710) les créera")
                .isEmpty();
        assertThat(new LienVigieChiroDao(source)
                        .objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(squelette.id())))
                .as("le passage est ancré sur sa participation : la nuit est reliée à ce qu'en sait le serveur")
                .contains(PARTICIPATION);

        // 3. Auditer : le workspace, réduit à un squelette, est SAIN. C'est la question de fond de l'EPIC
        //    #1154 — « chaque écart disque / base / serveur est-il visible ? » — et sa réciproque, tout
        //    aussi importante : un audit qui crie sur un état normal ne vaut rien, car on cesse de l'écouter.
        RapportAudit audit = injector.getInstance(ServiceAuditCoherence.class).auditerTout();
        assertThat(audit.constats())
                .as("aucune ERREUR : un squelette est archivé, pas corrompu (#1303, garde-fou #1719)")
                .noneMatch(constat -> constat.severite() == SeveriteConstat.ERREUR);
        assertThat(audit.constats())
                .as("le squelette porte le VRAI préfixe R6 : un préfixe fabriqué serait signalé à vie")
                .noneMatch(constat -> constat.categorie() == CategorieConstat.PREFIXE_NON_CONFORME);
        assertThat(audit.constats())
                .as("aucun fichier n'est réclamé : un squelette n'a aucune séquence sur disque")
                .noneMatch(constat -> constat.categorie() == CategorieConstat.DISQUE_MANQUANT);
        assertThat(audit.constats())
                .as("il reste ce qu'il doit rester : UN constat, informatif, qui dit que l'audio est archivé")
                .singleElement()
                .satisfies(constat -> {
                    assertThat(constat.severite()).isEqualTo(SeveriteConstat.INFO);
                    assertThat(constat.categorie()).isEqualTo(CategorieConstat.AUDIO_ARCHIVE);
                });
    }

    @Test
    @DisplayName("Reconstruit depuis la plateforme PUIS réactivé depuis les bruts : l'audio redevient COMPLETE")
    void reconstruit_puis_reactive_depuis_les_bruts() throws Exception {
        ClientVigieChiro client = plateformeBouchonnee();
        Injector injector = injecteurAvec(client);
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // Créer sites + points, mais PAS le squelette de passage (#1707) : on veut la reconstruction
        // COMPLÈTE, avec séquences et placeholder, pas un squelette. On exclut donc, parmi les rapprocheurs,
        // celui des passages ; les séquences reconstruites viendront de la pagination donnees (SEQ_1, SEQ_2).
        for (RapprochementVigieChiro rapprocheur :
                injector.getInstance(Key.get(new TypeLiteral<Set<RapprochementVigieChiro>>() {}))) {
            if (!(rapprocheur instanceof ServiceReconstructionPassages)) {
                rapprocheur.synchroniser(client);
            }
        }
        ServiceReconstructionPassages reconstruction = injector.getInstance(
                        Key.get(new TypeLiteral<Optional<ServiceReconstructionPassages>>() {}))
                .orElseThrow();
        RapportReconstruction rapport = reconstruction.reconstruire(PARTICIPATION);

        ServiceDisponibiliteAudio disponibilite = injector.getInstance(ServiceDisponibiliteAudio.class);
        assertThat(disponibilite.disponibilite(rapport.idPassage()))
                .as("point de départ : archivé, pas écoutable - l'audio d'un dépôt ZIP n'est pas sur le serveur")
                .isEqualTo(DisponibiliteAudio.ABSENTE);

        // L'utilisateur retrouve, sur sa carte SD, le brut de cette nuit et le log de l'enregistreur. SEQ_1 et
        // SEQ_2 sont les tranches i=0 et i=1 (à 5 s d'écart) de ce brut : sa régénération les reproduit à
        // l'identique, et la cascade structurelle les accepte (#1650/#1682).
        Path sd = Files.createTempDirectory("vc-e2e-bruts");
        ecrireBrut(sd.resolve("PaRec_20260703_220529.wav"));
        ecrireLog(sd.resolve("LogPR1925492.txt"));

        RapportReactivation reactivation = injector.getInstance(ServiceReactivationPassage.class)
                .reactiver(rapport.idPassage(), sd, progres -> {});

        assertThat(reactivation.voie())
                .as("le dossier porte le log et le brut d'un passage reconstruit : voie hydratation depuis les bruts")
                .isEqualTo(VoieReactivation.BRUTS);
        assertThat(reactivation.reactivees()).isEqualTo(2);
        assertThat(reactivation.complete()).isTrue();
        assertThat(disponibilite.disponibilite(rapport.idPassage()))
                .as("l'audio est revenu : le passage est de nouveau écoutable")
                .isEqualTo(DisponibiliteAudio.COMPLETE);

        SessionDao sessionDao = new SessionDao(source);
        SessionDEnregistrement session =
                sessionDao.trouverParPassage(rapport.idPassage()).orElseThrow();
        assertThat(session.archivee())
                .as("l'audio revenu : le passage n'est plus archivé")
                .isFalse();
        assertThat(new EnregistrementOriginalDao(source).findBySession(session.id()))
                .as("le placeholder a cédé la place aux vrais originaux, porteurs de leur fréquence d'acquisition")
                .isNotEmpty()
                .allSatisfy(original ->
                        assertThat(original.frequenceEchantillonnageHz()).isNotNull())
                .noneMatch(original -> original.nomFichier().endsWith("reconstruit.wav"));
    }

    // --- Harnais -----------------------------------------------------------------------------------

    /// Injecteur **réel** de l'application (tous les modules de feature), avec la seule plateforme
    /// substituée : c'est le vrai câblage qu'on exerce, pas une maquette du parcours.
    private static Injector injecteurAvec(ClientVigieChiro client) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-restauration");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        return Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ClientVigieChiro.class).toInstance(client);
            }
        }));
    }

    /// La plateforme telle qu'elle répond à un observateur qui a déposé une nuit depuis un autre poste.
    ///
    /// ⚠️ Depuis #1284, un retour `ReponseApi` non bouchonné vaut **`null`** (et non plus une liste vide) :
    /// chaque appel que le parcours déclenche doit être stubé explicitement — y compris [ClientVigieChiro#taxons],
    /// que le rapprocheur des taxons appelle en même temps que celui des sites.
    private static ClientVigieChiro plateformeBouchonnee() {
        ClientVigieChiro client = mock(ClientVigieChiro.class);
        when(client.taxons()).thenReturn(new ReponseApi.Succes<List<TaxonVigieChiro>>(List.of()));
        when(client.mesSites())
                .thenReturn(new ReponseApi.Succes<>(List.of(new SiteVigieChiro(
                        "site-1", SITE_TITRE, true, CARRE, List.of(new PointVigieChiro(POINT, 43.5, 5.4))))));
        when(client.mesParticipations())
                .thenReturn(new ReponseApi.Succes<>(List.of(
                        new ParticipationVigieChiro(PARTICIPATION, POINT, "2026-07-03T22:00:00+02:00", SITE_TITRE))));
        when(client.participation(PARTICIPATION))
                .thenReturn(new ReponseApi.Succes<>(new ParticipationDetail(
                        PARTICIPATION,
                        "etag-1",
                        POINT,
                        "2026-07-03T22:00:00+02:00",
                        "2026-07-04T06:30:00+02:00",
                        null,
                        Map.of("detecteur_enregistreur_numserie", "1925492"),
                        Traitement.absent())));
        // Pas de CSV exposé : la reconstruction retombe sur la pagination donnees (#1565).
        when(client.csvObservations(eq(PARTICIPATION))).thenReturn(new ReponseApi.Succes<>(Optional.<String>empty()));
        when(client.donnees(eq(PARTICIPATION), any()))
                .thenReturn(new ReponseApi.Succes<>(List.of(
                        new DonneeVigieChiro("d-1", SEQ_1, List.of(observation("Pippip"), observation("Pipkuh"))),
                        new DonneeVigieChiro("d-2", SEQ_2, List.of(observation("Rhifer"))))));
        return client;
    }

    private static ObservationVigieChiro observation(String taxon) {
        return new ObservationVigieChiro(0, taxon, 0.9, 45.0, 0.20, 0.32, null, null, null, null, null, List.of());
    }

    /// Le brut tel qu'il sort de l'enregistreur PR : [#DUREE_BRUT_S] secondes **réelles**, en-tête à `Fe/10`,
    /// contenu déterministe. Sa transformation à la fréquence du log produit des tranches de 5 s réelles dont
    /// les deux premières (i=0, i=1) portent les noms exacts de SEQ_1 et SEQ_2.
    private static void ecrireBrut(Path fichier) throws IOException {
        int echantillons = (int) Math.round(DUREE_BRUT_S * FREQUENCE_ACQUISITION_HZ);
        byte[] pcm = new byte[echantillons * 2];
        int valeur = 42;
        for (int n = 0; n < echantillons; n++) {
            valeur = valeur * 31 + 17;
            short amplitude = (short) (valeur % 8000);
            pcm[2 * n] = (byte) (amplitude & 0xFF);
            pcm[2 * n + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        Files.createDirectories(fichier.getParent());
        FichierWav.ecrire(fichier, 1, FREQUENCE_ACQUISITION_HZ / 10, 16, pcm, 0, pcm.length);
    }

    /// Journal minimal de l'enregistreur : la ligne « Paramètres » porte la fréquence d'acquisition `Fe…kHz`,
    /// la seule chose que l'inventaire (#1649) y lit pour régénérer à l'identique.
    private static void ecrireLog(Path fichier) throws IOException {
        Files.write(
                fichier,
                List.of(
                        "03/07/26 - 22:00:00 PR1925492 Démarrage v1.0",
                        "03/07/26 - 22:00:01 PR1925492 Paramètres : Acquisi. 22:00-06:30, Fe"
                                + (FREQUENCE_ACQUISITION_HZ / 1000) + "kHz, S. R. Med, Bd. Freq. 8-120kHz"),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
