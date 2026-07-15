package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.audit.model.ResultatReset;
import fr.univ_amu.iut.audit.model.ServiceReset;
import fr.univ_amu.iut.audit.model.SeveriteConstat;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.api.TaxonVigieChiro;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Le reset guidé, de bout en bout** (#1419, EPIC #1154) : la question de l'EPIC, prise au mot —
/// *« peut-on repartir d'une base neuve sans perte silencieuse ? »*
///
/// Le parcours part d'une base **abîmée** (une nuit rattachée à rien, dont les fichiers ont disparu du
/// disque) et va jusqu'à un workspace **sain**, repeuplé depuis la plateforme. Il exerce le **vrai
/// câblage** de l'application (`RacineInjecteur`, `Modules.override`), seule la plateforme est bouchonnée.
///
/// Mais l'essentiel de ce test est ailleurs : il prouve surtout ce que la procédure **refuse** de faire.
/// Un reset qui détruirait la base sans prévenir, ou pire, alors que le serveur ne répond pas et ne pourra
/// rien rendre, serait exactement la « perte silencieuse » que l'EPIC combat. Les deux refus sont donc
/// testés **avant** le succès, et chacun vérifie que la base est **restée intacte**.
class ParcoursResetE2ETest {

    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";
    private static final String CARRE = "130711";
    private static final String POINT = "Z41";
    private static final String SITE_TITRE = "Vigiechiro - Point Fixe-" + CARRE;
    private static final String SEQ_1 = "Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000";
    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#1419 : la perte n'est pas acceptée → la procédure REFUSE, et la base est intacte")
    void refuse_tant_que_la_perte_n_est_pas_acceptee() throws Exception {
        Injector injector = injecteurAvec(plateformeBouchonnee());
        SourceDeDonnees source = preparerBaseAbimee(injector);

        ResultatReset resultat = injector.getInstance(ServiceReset.class).executer(dossierSauvegarde(), false);

        assertThat(resultat)
                .as("une nuit dont l'audio ne reviendrait pas : on s'arrête avant de détruire")
                .isInstanceOf(ResultatReset.Refuse.class);
        assertThat(resultat.codeSortie()).isEqualTo(2);
        assertThat(new PassageDao(source).findAll())
                .as("RIEN n'a été touché : c'est ce qui distingue un refus d'un échec")
                .hasSize(1);
    }

    @Test
    @DisplayName("#1419 : VigieChiro injoignable → la procédure REFUSE, même perte acceptée : une base"
            + " neuve qu'on ne peut pas repeupler est un workspace vide")
    void refuse_quand_la_plateforme_ne_repond_pas() throws Exception {
        ClientVigieChiro injoignable = mock(ClientVigieChiro.class);
        when(injoignable.moi()).thenReturn(new ReponseApi.Injoignable<>("délai d'attente dépassé"));
        Injector injector = injecteurAvec(injoignable);
        SourceDeDonnees source = preparerBaseAbimee(injector);

        ResultatReset resultat = injector.getInstance(ServiceReset.class).executer(dossierSauvegarde(), true);

        assertThat(resultat)
                .as("le repeuplement vient du serveur : sans lui, le reset serait une destruction sèche")
                .isInstanceOf(ResultatReset.Refuse.class);
        assertThat(((ResultatReset.Refuse) resultat).motif()).contains("VigieChiro ne répond pas");
        assertThat(new PassageDao(source).findAll())
                .as("la base d'origine est toujours là — c'est tout l'intérêt de refuser AVANT")
                .hasSize(1);
    }

    @Test
    @DisplayName("#1419 : perte acceptée et plateforme joignable → sauvegarde, base neuve, repeuplement"
            + " depuis VigieChiro, audit final SAIN")
    void reset_complet_jusqu_a_un_workspace_sain() throws Exception {
        Injector injector = injecteurAvec(plateformeBouchonnee());
        SourceDeDonnees source = preparerBaseAbimee(injector);
        Path sauvegardes = dossierSauvegarde();

        ResultatReset resultat = injector.getInstance(ServiceReset.class).executer(sauvegardes, true);

        assertThat(resultat).isInstanceOf(ResultatReset.Fait.class);
        ResultatReset.Fait fait = (ResultatReset.Fait) resultat;

        assertThat(fait.sauvegarde().dossier())
                .as("la base d'avant est à l'abri AVANT d'être détruite : c'est la condition du reste")
                .exists();
        assertThat(fait.filet())
                .as("et un filet est posé au passage : `restaurer` sait relire cette base-là")
                .exists();

        List<Passage> passages = new PassageDao(source).findAll();
        assertThat(passages)
                .as("la nuit orpheline a disparu avec l'ancienne base ; celle de la plateforme l'a remplacée")
                .singleElement()
                .satisfies(passage -> assertThat(passage.annee()).isEqualTo(2026));
        assertThat(fait.passagesReconstruits())
                .as("la participation du serveur est revenue, en passage archivé (#1050, #1305)")
                .isEqualTo(1);

        assertThat(fait.audit().constats())
                .as("le workspace remis à neuf est SAIN : aucune erreur. C'est la réciproque de l'EPIC —"
                        + " un audit qui crierait sur un état normal ne vaudrait rien")
                .noneMatch(constat -> constat.severite() == SeveriteConstat.ERREUR);

        assertThat(fait.aRetablir())
                .as("et ce qui manque est DIT : l'audio ne revient pas du serveur, il faut le rebrancher")
                .isNotEmpty();
        assertThat(fait.enClair())
                .as("le message porte lui-même la suite : l'utilisateur n'a pas à deviner quoi faire")
                .contains("Il reste l'audio à rétablir")
                .contains("importer");
    }

    // --- Harnais -----------------------------------------------------------------------------------

    /// Une base **abîmée**, telle qu'on en trouve quand on veut repartir de zéro : une nuit en base,
    /// rattachée à **aucune** participation (elle ne se re-déposera jamais), dont les fichiers ne sont
    /// **pas** sur le disque. `ServiceRecuperabilite` la classe donc en **PERDU** — et c'est bien le cas
    /// qui doit forcer une acceptation explicite avant tout reset.
    private static SourceDeDonnees preparerBaseAbimee(Injector injector) {
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));

        Site site = new SiteDao(source)
                .insert(new Site(null, "999999", "Site fantôme", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "Z9", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, null, null));
        Passage passage = new PassageDao(source)
                .insert(new Passage(
                        null,
                        1,
                        2025,
                        "2025-06-01",
                        "22:00",
                        "06:00",
                        null,
                        StatutWorkflow.IMPORTE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE));
        new SessionDao(source)
                .insert(new SessionDEnregistrement(null, "/disque/qui/n/existe/plus", null, null, passage.id()));
        return source;
    }

    private static Path dossierSauvegarde() throws Exception {
        return Files.createTempDirectory("vc-reset-sauvegardes");
    }

    /// Injecteur **réel** de l'application (tous les modules de feature), avec la seule plateforme
    /// substituée : c'est le vrai câblage qu'on exerce, pas une maquette du parcours.
    private static Injector injecteurAvec(ClientVigieChiro client) throws Exception {
        Path workspace = Files.createTempDirectory("vc-e2e-reset");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        return Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ClientVigieChiro.class).toInstance(client);
            }
        }));
    }

    /// ⚠️ Depuis #1284, un retour `ReponseApi` non bouchonné vaut **`null`** : chaque appel du parcours
    /// doit être stubé, y compris [ClientVigieChiro#moi] — c'est lui, ici, qui atteste que la plateforme
    /// répond avant qu'on ose détruire quoi que ce soit.
    private static ClientVigieChiro plateformeBouchonnee() {
        ClientVigieChiro client = mock(ClientVigieChiro.class);
        when(client.moi()).thenReturn(new ReponseApi.Succes<>(new ProfilVigieChiro(ID_USER, "moi", "Observateur")));
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
                        Map.of("detecteur_enregistreur_numserie", SERIE),
                        Traitement.absent())));
        // Pas de CSV exposé : la reconstruction retombe sur la pagination donnees (#1565).
        when(client.csvObservations(eq(PARTICIPATION)))
                .thenReturn(new ReponseApi.Succes<>(java.util.Optional.<String>empty()));
        when(client.donnees(eq(PARTICIPATION), any()))
                .thenReturn(new ReponseApi.Succes<>(
                        List.of(new DonneeVigieChiro("d-1", SEQ_1, List.of(observation("Pippip"))))));
        return client;
    }

    private static ObservationVigieChiro observation(String taxon) {
        return new ObservationVigieChiro(0, taxon, 0.9, 45.0, 0.20, 0.32, null, null, null, null, null, List.of());
    }
}
