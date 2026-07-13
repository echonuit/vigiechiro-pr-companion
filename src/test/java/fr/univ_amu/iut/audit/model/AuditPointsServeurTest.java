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
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.util.List;
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
    private AuditPointsServeur audit;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        LienVigieChiroDao liens = new LienVigieChiroDao(source);
        Site site = siteDao.insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        pointDao.insert(new PointDEcoute(null, "A1", 43.5, 5.4, null, site.id()));
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), OBJECTID_SITE));

        client = mock(ClientVigieChiro.class);
        audit = new AuditPointsServeur(client, siteDao, pointDao, liens, ID_USER);
    }

    @Test
    @DisplayName("Point local absent du serveur : POINT_DIVERGENT")
    void point_absent_serveur() {
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(
                        List.of(new SiteVigieChiro(OBJECTID_SITE, "Étang", true, "040962", List.of()))));

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
                        OBJECTID_SITE, "Étang", true, "040962", List.of(new PointVigieChiro("A1", 44.0, 5.4))))));

        assertThat(audit.auditer())
                .extracting(ConstatAudit::categorie)
                .containsExactly(CategorieConstat.POINT_DIVERGENT);
    }

    @Test
    @DisplayName("Point identique (code + position) : aucun constat")
    void point_identique() {
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(new SiteVigieChiro(
                        OBJECTID_SITE, "Étang", true, "040962", List.of(new PointVigieChiro("A1", 43.5, 5.4))))));

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
}
