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
import fr.univ_amu.iut.commun.api.RapportSynchro;
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
import fr.univ_amu.iut.sites.model.SynchronisationSites;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
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
///    **passages archivés** (#1707/#1814) : point + date + n° + **identité** (enregistreur, météo, micro,
///    lue dans le détail par nuit), mais sans séquences ni observations (elles viennent à la reconstruction) ;
/// 2. **retrouver** : la nuit déposée sur la plateforme est un **passage local en squelette**, listé
///    « à reconstruire » ;
/// 3. **hydrater** : reconstruire la nuit **remplace** le squelette par un passage complet — séquences
///    (lignes sans fichier) + observations rapatriées (#1710) ;
/// 4. **auditer** : le workspace restauré est **sain**. Un passage sans audio n'est pas un passage cassé —
///    c'est un passage **archivé** (#1297), et l'audit informe au lieu de crier (#1303, garde-fou #1719).
///
/// La plateforme est bouchonnée ([ClientVigieChiro] mocké, substitué dans l'injecteur **réel** par
/// `Modules.override`) : tout le reste est le vrai câblage de l'application.
///
/// ⚠️ Ce que le test **ne** prouve **pas**, parce que c'est faux : que l'audio revienne. Un dépôt ZIP (le
/// mode par défaut) ne laisse **aucun** audio sur le serveur ; le passage restauré est **ABSENTE** :
/// consultable, non écoutable, réactivable en réimportant les fichiers d'origine (#1302).
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
        //    `recuperer-vigiechiro`. Les sites VIENNENT des participations ; les passages en découlent
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

        // 2. Retrouver : la nuit déposée sur la plateforme est désormais un PASSAGE LOCAL, en SQUELETTE
        //    (point + date, sans séquence). Elle reste listée « à reconstruire » (#1710) : rattachée, mais
        //    pas encore hydratée.
        ServiceReconstructionPassages reconstruction = injector.getInstance(
                        Key.get(new TypeLiteral<Optional<ServiceReconstructionPassages>>() {}))
                .orElseThrow();
        assertThat(reconstruction.orphelines())
                .as("la nuit rapatriée en squelette reste à hydrater : elle figure dans la liste")
                .singleElement()
                .satisfies(orpheline -> assertThat(orpheline.idParticipation()).isEqualTo(PARTICIPATION));

        SessionDao sessionDao = new SessionDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        Passage squelette = new PassageDao(source).findAll().getFirst();
        assertThat(squelette.statutWorkflow())
                .as("la participation existe sur la plateforme : le passage est déposé")
                .isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(squelette.idEnregistreur())
                .as("#1814 : l'identité (n° série) est rapatriée depuis le détail dès la synchro, plus « INCONNU »")
                .isEqualTo("1925492");
        SessionDEnregistrement sessionSquelette =
                sessionDao.trouverParPassage(squelette.id()).orElseThrow();
        assertThat(sessionSquelette.archivee())
                .as("un squelette naît archivé : rien n'a jamais été importé ici")
                .isTrue();
        assertThat(sequenceDao.findBySession(sessionSquelette.id()))
                .as("un squelette n'a toujours pas de séquence : identité oui, audio/observations à la reconstruction")
                .isEmpty();

        // 3. Hydrater : reconstruire la nuit REMPLACE le squelette par un passage complet - séquences (lignes
        //    sans fichier) + observations rapatriées. Rendu possible par #1710 : reconstruire sait désormais
        //    hydrater un squelette rattaché au lieu de le refuser.
        RapportReconstruction rapport = reconstruction.reconstruire(PARTICIPATION);
        assertThat(rapport.sequencesRecreees()).isEqualTo(2);

        Long idSession =
                sessionDao.trouverParPassage(rapport.idPassage()).orElseThrow().id();
        ObservationDao observationDao = new ObservationDao(source);
        long observations = sequenceDao.findBySession(idSession).stream()
                .mapToLong(
                        sequence -> observationDao.findBySequence(sequence.id()).size())
                .sum();
        assertThat(observations)
                .as("les observations sont EN BASE, rattachées aux séquences recréées par leur nom")
                .isEqualTo(3);
        assertThat(new LienVigieChiroDao(source)
                        .objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(rapport.idPassage())))
                .as("le passage hydraté est ré-ancré sur sa participation : il se re-déposera, se re-vérifiera")
                .contains(PARTICIPATION);

        // La limite, figée : le serveur n'a pas rendu l'audio, et il ne le rendra pas (dépôt ZIP).
        ServiceDisponibiliteAudio disponibilite = injector.getInstance(ServiceDisponibiliteAudio.class);
        assertThat(disponibilite.disponibilite(rapport.idPassage()))
                .as("consultable, pas écoutable : le passage restauré est archivé, pas complet")
                .isEqualTo(DisponibiliteAudio.ABSENTE);
        assertThat(rapport.lacunes())
                .as("ce qui manque est DIT, pas deviné (ni journal, ni relevé, ni non-identifiés, ni empreintes)")
                .isNotEmpty();

        // 4. Auditer : le workspace restauré est SAIN. C'est la question de fond de l'EPIC #1154 — « chaque
        //    écart disque / base / serveur est-il visible ? » — et sa réciproque, tout aussi importante : un
        //    audit qui crie sur un état normal ne vaut rien, car on cesse de l'écouter.
        RapportAudit audit = injector.getInstance(ServiceAuditCoherence.class).auditerTout();
        assertThat(audit.constats())
                .as("aucune ERREUR : un passage restauré est archivé, pas corrompu (#1303)")
                .noneMatch(constat -> constat.severite() == SeveriteConstat.ERREUR);
        assertThat(audit.constats())
                .as("le passage restauré porte le VRAI préfixe R6 : un préfixe fabriqué serait signalé à vie")
                .noneMatch(constat -> constat.categorie() == CategorieConstat.PREFIXE_NON_CONFORME);
        assertThat(audit.constats())
                .as("aucun fichier n'est réclamé : les observations de la plateforme ne viennent d'aucun CSV")
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

    @Test
    @DisplayName("#1808 : le bouton « Mes sites » rapatrie AUSSI les passages, en un seul tour (pas que la connexion)")
    void bouton_mes_sites_rapatrie_les_passages() throws Exception {
        ClientVigieChiro client = plateformeBouchonnee();
        Injector injector = injecteurAvec(client);
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // Le chemin du BOUTON (SitesViewModel -> SynchronisationSites), distinct de la connexion et de la CLI
        // qui itèrent le set de rapprocheurs. Le vrai câblage : la structure des sites précède ses dépendants,
        // si bien qu'UN SEUL appel suffit à rapatrier le squelette de nuit sur le point tout juste créé (#1776).
        SynchronisationSites synchronisation = injector.getInstance(
                        Key.get(new TypeLiteral<Optional<SynchronisationSites>>() {}))
                .orElseThrow();

        List<RapportSynchro> rapports = synchronisation.synchroniser();

        assertThat(rapports)
                .as("le bouton annonce et les sites et les passages : plus jamais « 41 points, 0 passage »")
                .extracting(RapportSynchro::libelle)
                .contains("sites", "passage(s) rapatrié(s)");
        assertThat(new PassageDao(source).findAll())
                .as("un passage archivé est créé dès ce clic, sur le point tout juste rapatrié")
                .singleElement()
                .satisfies(passage -> {
                    assertThat(passage.statutWorkflow()).isEqualTo(StatutWorkflow.DEPOSE);
                    assertThat(passage.idEnregistreur())
                            .as("#1814 : son identité (n° série réel) remonte dès la synchro, plus « INCONNU »")
                            .isEqualTo("1925492");
                });
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
