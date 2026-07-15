package fr.univ_amu.iut.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le contrat de [JeuDeDonneesPassage] pour ses deux capacités ajoutées en #1258 : le **verdict** est
/// bien persisté, et deux `semer()` qui partagent leurs coordonnées **partagent réellement** leur site et
/// leur point (trouver-ou-créer) au lieu de les doublonner.
///
/// Ce fichier construit des DAO à la main pour **lire** ce que la fixture a semé : c'est le seul moyen de
/// vérifier la fixture elle-même. Il ne sème aucun passage à la main (tout passe par la fixture).
class JeuDeDonneesPassageTest {

    @TempDir
    Path dossier;

    private SourceDeDonnees source;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
    }

    @Test
    @DisplayName("#1258 : le verdict passé à la fixture est bien persisté sur le passage")
    void verdict_persiste() {
        long id = JeuDeDonneesPassage.dans(source)
                .statut(StatutWorkflow.VERIFIE)
                .verdict(Verdict.DOUTEUX)
                .semer()
                .idPassage();

        assertThat(new PassageDao(source).findById(id).orElseThrow().verdictVerification())
                .isEqualTo(Verdict.DOUTEUX);
    }

    @Test
    @DisplayName("#1258 : deux nuits sur le même point le PARTAGENT (un seul site, un seul point)")
    void deux_nuits_partagent_le_point() {
        long p1 = JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .point("A1")
                .nuit(1, 2025, "2025-06-20")
                .semer()
                .idPoint();
        long p2 = JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .point("A1")
                .nuit(1, 2026, "2026-06-20")
                .semer()
                .idPoint();

        assertThat(p2).as("le second semis retrouve le point du premier").isEqualTo(p1);
        assertThat(new SiteDao(source).findByUtilisateur("u-1"))
                .as("un seul site pour le carré partagé")
                .hasSize(1);
        long idSite = new SiteDao(source).findByUtilisateur("u-1").get(0).id();
        assertThat(new PointDao(source).findBySite(idSite))
                .as("un seul point A1 sous ce site")
                .hasSize(1);
    }

    @Test
    @DisplayName("#1258 : un autre point sous le même carré s'ajoute au même site")
    void autre_point_meme_site() {
        JeuDeDonneesPassage.dans(source).carre("640380").point("A1").semer();
        JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .point("B2")
                .nuit(1, 2026, "2026-06-21")
                .semer();

        assertThat(new SiteDao(source).findByUtilisateur("u-1")).hasSize(1);
        long idSite = new SiteDao(source).findByUtilisateur("u-1").get(0).id();
        assertThat(new PointDao(source).findBySite(idSite))
                .extracting(point -> point.code())
                .containsExactlyInAnyOrder("A1", "B2");
    }
}
