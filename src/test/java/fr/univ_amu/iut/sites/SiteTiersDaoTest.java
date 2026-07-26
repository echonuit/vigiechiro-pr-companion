package fr.univ_amu.iut.sites;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Marquage « carré d'un tiers » (#2525) via la table latérale de présence `site_tiers` : (dé)marquage
/// idempotent, lecture unitaire et groupée, et **cascade** à la suppression du site.
class SiteTiersDaoTest {

    private static final String ID_USER = "u-1";

    @TempDir
    Path dossier;

    private SiteDao sites;
    private SiteTiersDao dao;
    private long idSite;
    private long idAutreSite;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        sites = new SiteDao(source);
        idSite = insererSite("130711");
        idAutreSite = insererSite("130712");
        dao = new SiteTiersDao(source);
    }

    private long insererSite(String carre) {
        return sites.insert(new Site(null, carre, "Site " + carre, Protocole.STANDARD, null, "2026-01-01", ID_USER))
                .id();
    }

    @Test
    @DisplayName("Par défaut, un carré n'appartient pas à un tiers")
    void absent_par_defaut() {
        assertThat(dao.estTiers(idSite)).isFalse();
    }

    @Test
    @DisplayName("marquer rend le carré « tiers » ; demarquer l'annule")
    void marquer_puis_demarquer() {
        dao.marquer(idSite);
        assertThat(dao.estTiers(idSite)).isTrue();

        dao.demarquer(idSite);
        assertThat(dao.estTiers(idSite)).isFalse();
    }

    @Test
    @DisplayName("marquer est idempotent (ON CONFLICT DO NOTHING)")
    void marquer_idempotent() {
        dao.marquer(idSite);
        dao.marquer(idSite);

        assertThat(dao.estTiers(idSite)).isTrue();
        assertThat(dao.tousLesIds()).containsExactly(idSite);
    }

    @Test
    @DisplayName("definir (dé)marque selon le booléen")
    void definir_selon_booleen() {
        dao.definir(idSite, true);
        assertThat(dao.estTiers(idSite)).isTrue();

        dao.definir(idSite, false);
        assertThat(dao.estTiers(idSite)).isFalse();
    }

    @Test
    @DisplayName("tousLesIds ne remonte que les carrés marqués")
    void tous_les_ids() {
        dao.marquer(idSite);

        assertThat(dao.tousLesIds()).containsExactly(idSite).doesNotContain(idAutreSite);
    }

    @Test
    @DisplayName("Supprimer le site retire son marquage en cascade")
    void cascade_suppression_site() {
        dao.marquer(idSite);

        sites.delete(idSite);

        assertThat(dao.estTiers(idSite)).as("ON DELETE CASCADE").isFalse();
    }
}
