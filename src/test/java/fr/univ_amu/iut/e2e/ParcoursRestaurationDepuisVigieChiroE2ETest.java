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
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.DisponibiliteAudio;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
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
///    déclenche la connexion) créent les **sites et leurs points** à partir des participations ;
/// 2. **repérer** : les participations sans équivalent local sont listées (`orphelines`) et leur point
///    est désormais **connu** — sans l'étape 1, la reconstruction les refuserait ;
/// 3. **reconstruire** : chaque participation devient un **passage archivé** (lignes de séquences sans
///    fichier) et ses **observations** sont rapatriées ;
/// 4. **auditer** : le workspace est **sain**. Un passage sans audio n'est pas un passage cassé — c'est
///    un passage **archivé** (#1297), et l'audit informe au lieu de crier (#1303).
///
/// La plateforme est bouchonnée ([ClientVigieChiro] mocké, substitué dans l'injecteur **réel** par
/// `Modules.override`) : tout le reste est le vrai câblage de l'application.
///
/// ⚠️ Ce que le test **ne** prouve **pas**, parce que c'est faux : que l'audio revienne. Un dépôt ZIP
/// (le mode par défaut) ne laisse **aucun** audio sur le serveur. Le passage restauré est **ABSENTE** :
/// consultable, non écoutable, réactivable en réimportant les fichiers d'origine (#1302). C'est
/// exactement la limite que la doc annonce, et le test la fige.
class ParcoursRestaurationDepuisVigieChiroE2ETest {

    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";
    private static final String CARRE = "130711";
    private static final String POINT = "Z41";
    private static final String SITE_TITRE = "Vigiechiro - Point Fixe-" + CARRE;
    private static final String SEQ_1 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000";
    private static final String SEQ_2 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220534_000";

    @Test
    @DisplayName("Base vierge : la plateforme rend les sites, les points, les passages et leurs observations")
    void base_vierge_restauree_depuis_la_plateforme() throws Exception {
        ClientVigieChiro client = plateformeBouchonnee();
        Injector injector = injecteurAvec(client);
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        assertThat(new PassageDao(source).findAll())
                .as("le point de départ est une base réellement vide : rien à restaurer depuis le disque")
                .isEmpty();

        // 1. Synchroniser : ce que fait la connexion (ConnexionViewModel#amorcerRapprochements) et la CLI
        //    `synchroniser-vigiechiro`. Les sites VIENNENT des participations (toutes pages, dédupliquées).
        for (RapprochementVigieChiro rapprocheur :
                injector.getInstance(Key.get(new TypeLiteral<Set<RapprochementVigieChiro>>() {}))) {
            rapprocheur.synchroniser(client);
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

        // 2. Repérer : la participation n'a aucun passage local, et son point est maintenant connu.
        ServiceReconstructionPassages reconstruction = injector.getInstance(
                        Key.get(new TypeLiteral<Optional<ServiceReconstructionPassages>>() {}))
                .orElseThrow();
        List<ParticipationOrpheline> orphelines = reconstruction.orphelines();
        assertThat(orphelines).singleElement().satisfies(orpheline -> {
            assertThat(orpheline.idParticipation()).isEqualTo(PARTICIPATION);
            assertThat(orpheline.pointLocalConnu())
                    .as("l'étape 1 a créé le point : sans elle, la reconstruction refuserait cette nuit")
                    .isTrue();
        });

        // 3. Reconstruire : passage archivé + séquences (lignes sans fichier) + observations rapatriées.
        RapportReconstruction rapport = reconstruction.reconstruire(PARTICIPATION);

        assertThat(rapport.sequencesRecreees()).isEqualTo(2);
        SessionDao sessionDao = new SessionDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
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
                .as("le passage est ré-ancré sur sa participation : il se re-déposera, se re-vérifiera")
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
        //    écart disque / base / serveur est-il visible ? » — et sa réciproque, tout aussi importante :
        //    un audit qui crie sur un état normal ne vaut rien, car on cesse de l'écouter.
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
        when(client.donnees(eq(PARTICIPATION), any()))
                .thenReturn(new ReponseApi.Succes<>(List.of(
                        new DonneeVigieChiro("d-1", SEQ_1, List.of(observation("Pippip"), observation("Pipkuh"))),
                        new DonneeVigieChiro("d-2", SEQ_2, List.of(observation("Rhifer"))))));
        return client;
    }

    private static ObservationVigieChiro observation(String taxon) {
        return new ObservationVigieChiro(0, taxon, 0.9, 45.0, 0.20, 0.32, null, null, null, null, null, List.of());
    }
}
