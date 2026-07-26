package fr.univ_amu.iut.audit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Audit en ligne des points (#1178) : DAO réels sur SQLite jetable, client **mocké**. On seede un site
/// (carré 040962) + un point A1 (43.5, 5.4) lié au serveur, puis on fait varier ce que renvoie
/// `mesSites()`.
class AuditPointsServeurTest {

    private static final String ID_USER = "u-1";
    private static final String OBJECTID_SITE = "srv-site-1";

    @TempDir
    Path dossier;

    private ClientVigieChiro client;
    private ServiceReconstructionPassages reconstruction;
    private AuditPointsServeur audit;
    private SourceDeDonnees source;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        LienVigieChiroDao liens = new LienVigieChiroDao(source);
        Site site = siteDao.insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        pointDao.insert(new PointDEcoute(null, "A1", 43.5, 5.4, null, site.id()));
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), OBJECTID_SITE));

        client = mock(ClientVigieChiro.class);
        reconstruction = mock(ServiceReconstructionPassages.class);
        // Par défaut : la plateforme ne porte aucune nuit qu'on n'aurait pas. Le sens serveur -> local se
        // tait donc, et les tests du sens local -> serveur restent lisibles tels quels.
        when(reconstruction.orphelines()).thenReturn(List.of());
        audit = new AuditPointsServeur(client, siteDao, pointDao, liens, ID_USER, Optional.of(reconstruction));
    }

    @Test
    @DisplayName("Point local absent du serveur : POINT_DIVERGENT")
    void point_absent_serveur() {
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(
                        List.of(new SiteVigieChiro(OBJECTID_SITE, "Étang", true, "040962", null, List.of()))));

        assertThat(audit.auditer()).singleElement().satisfies(c -> {
            assertThat(c.categorie()).isEqualTo(CategorieConstat.POINT_DIVERGENT);
            assertThat(c.cible()).isEqualTo("040962 / A1");
            assertThat(c.detail()).contains("inconnu du serveur");
        });
    }

    @Test
    @DisplayName("Position différente du serveur : POINT_DIVERGENT")
    void position_differente() {
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(new SiteVigieChiro(
                        OBJECTID_SITE, "Étang", true, "040962", null, List.of(new PointVigieChiro("A1", 44.0, 5.4))))));

        assertThat(audit.auditer())
                .extracting(ConstatAudit::categorie)
                .containsExactly(CategorieConstat.POINT_DIVERGENT);
    }

    @Test
    @DisplayName("Point identique (code + position) : aucun constat")
    void point_identique() {
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(new SiteVigieChiro(
                        OBJECTID_SITE, "Étang", true, "040962", null, List.of(new PointVigieChiro("A1", 43.5, 5.4))))));

        assertThat(audit.auditer()).isEmpty();
    }

    @Test
    @DisplayName("Aucun site distant (succès, liste vide) : constat INFO qui le dit, sans parler de panne")
    void aucun_site_distant() {
        when(client.mesSites()).thenReturn(ReponseApi.succes(List.of()));

        assertThat(audit.auditer()).singleElement().satisfies(c -> {
            assertThat(c.categorie()).isEqualTo(CategorieConstat.SERVEUR_INJOIGNABLE);
            assertThat(c.detail()).contains("aucun site distant");
        });
    }

    @Test
    @DisplayName("#1284 : non connecté, injoignable et refusé donnent chacun leur constat exact")
    void causes_indisponibilite_distinctes() {
        when(client.mesSites()).thenReturn(ReponseApi.nonConnecte());
        assertThat(audit.auditer())
                .singleElement()
                .satisfies(c -> assertThat(c.detail()).contains("non connecté"));

        when(client.mesSites()).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));
        assertThat(audit.auditer())
                .singleElement()
                .satisfies(c -> assertThat(c.detail()).contains("injoignable").contains("délai"));

        when(client.mesSites()).thenReturn(ReponseApi.refuse(403, "interdit"));
        assertThat(audit.auditer())
                .singleElement()
                .satisfies(c -> assertThat(c.detail()).contains("HTTP 403"));
    }

    // ─── Sens inverse (#1455) : ce que la plateforme sait et qu'on n'a pas ───────────────────────────
    //
    // Le site est à jour côté points (A1 identique) : le sens local -> serveur se tait. Seul le sens
    // serveur -> local peut parler, et il ne doit le faire QUE si la localité inconnue cache du travail.

    @Test
    @DisplayName("Localité serveur inconnue qui porte des nuits absentes d'ici : POINT_SERVEUR_IGNORE")
    void localite_serveur_qui_cache_du_travail() {
        siteAJour();
        when(reconstruction.orphelines())
                .thenReturn(List.of(
                        new ParticipationOrpheline("p-1", "040962", "B2", "2026-04-11T20:00:00Z", false),
                        new ParticipationOrpheline("p-2", "040962", "B2", "2026-04-12T20:00:00Z", false)));

        assertThat(audit.auditer()).singleElement().satisfies(constat -> {
            assertThat(constat.categorie()).isEqualTo(CategorieConstat.POINT_SERVEUR_IGNORE);
            assertThat(constat.severite()).isEqualTo(Severite.AVERTISSEMENT);
            assertThat(constat.cible()).isEqualTo("040962 / B2");
            // Le constat nomme le travail : c'est lui qui rend l'écart intéressant, pas la localité.
            assertThat(constat.detail())
                    .contains("2 nuit(s) absente(s)")
                    .contains("2026-04-11")
                    .contains("2026-04-12");
        });
    }

    @Test
    @DisplayName("Localité serveur inconnue qui ne porte AUCUNE nuit : aucun constat (sa création est voulue)")
    void localite_serveur_sans_travail_ne_dit_rien() {
        siteAJour();
        when(reconstruction.orphelines()).thenReturn(List.of());

        assertThat(audit.auditer()).isEmpty();
    }

    @Test
    @DisplayName("Nuit absente d'ici, mais à une localité qu'on connaît : pas ce constat (affaire de #1305)")
    void nuit_absente_a_une_localite_connue() {
        siteAJour();
        when(reconstruction.orphelines())
                .thenReturn(List.of(new ParticipationOrpheline("p-3", "040962", "A1", "2026-04-11T20:00:00Z", true)));

        // Rien n'est créé en silence ici : le point existe déjà. La nuit manquante reste l'affaire de
        // l'écran de reconstruction, qui la propose explicitement.
        assertThat(audit.auditer()).isEmpty();
    }

    @Test
    @DisplayName("Plateforme injoignable pendant le second appel : INFO, jamais un échec dur")
    void reconstruction_injoignable_degrade_en_info() {
        siteAJour();
        when(reconstruction.orphelines()).thenThrow(new RegleMetierException("Vigie-Chiro est injoignable (timeout)"));

        assertThat(audit.auditer()).singleElement().satisfies(constat -> {
            assertThat(constat.severite()).isEqualTo(Severite.INFO);
            assertThat(constat.categorie()).isEqualTo(CategorieConstat.SERVEUR_INJOIGNABLE);
            assertThat(constat.detail()).contains("injoignable");
        });
    }

    @Test
    @DisplayName("Feature « reconstruire » désactivée : le sens inverse se tait, il n'échoue pas")
    void sans_reconstruction_le_sens_inverse_se_tait() {
        AuditPointsServeur sansReconstruction = new AuditPointsServeur(
                client,
                new SiteDao(source),
                new PointDao(source),
                new LienVigieChiroDao(source),
                ID_USER,
                Optional.empty());
        siteAJour();

        assertThat(sansReconstruction.auditer()).isEmpty();
    }

    /// Le serveur connaît exactement le point local A1, à la même position : le sens local -> serveur n'a
    /// rien à dire, ce qui isole le sens inverse dans les assertions.
    private void siteAJour() {
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(new SiteVigieChiro(
                        OBJECTID_SITE, "Étang", true, "040962", null, List.of(new PointVigieChiro("A1", 43.5, 5.4))))));
    }
}
