package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.fixture.SortieCapturee;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Les liens CLI (#1874) se résolvent avec le référentiel du workspace, sans réseau.
class CliLiensTest {
    @TempDir
    Path workspace;

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    void une_espece_pna_rend_son_url_seule() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Cli cli = Cli.applicative();
        SortieCapturee capture = new SortieCapturee();

        int code = cli.executer(new String[] {"lien-espece", "--code", "Pippip"}, capture.sortie(), capture.erreur());

        assertThat(code).isZero();
        assertThat(capture.texte())
                .isEqualTo("https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/"
                        + System.lineSeparator());
        assertThat(capture.texteErreur()).isEmpty();
    }

    @Test
    void une_participation_liee_rend_son_url_seule() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        var injecteur = Cli.injecteurApplicatif();
        injecteur
                .getInstance(fr.univ_amu.iut.commun.persistence.MigrationSchema.class)
                .migrer();
        injecteur
                .getInstance(fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao.class)
                .upsert(new fr.univ_amu.iut.commun.model.LienVigieChiro("passage", "42", "6a4961f587bc8dba39481180"));
        SortieCapturee capture = new SortieCapturee();

        int code = new Cli(injecteur)
                .executer(new String[] {"lien-participation", "--passage", "42"}, capture.sortie(), capture.erreur());

        assertThat(code).isZero();
        assertThat(capture.texte())
                .isEqualTo("https://vigiechiro.herokuapp.com/#/participations/6a4961f587bc8dba39481180"
                        + System.lineSeparator());
        assertThat(capture.texteErreur()).isEmpty();
    }

    @Test
    void le_repli_suit_la_source_choisie_dans_les_reglages() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        var injecteur = Cli.injecteurApplicatif();
        injecteur
                .getInstance(fr.univ_amu.iut.commun.persistence.MigrationSchema.class)
                .migrer();
        Cli cli = new Cli(injecteur);
        SortieCapturee gbif = new SortieCapturee();
        assertThat(cli.executer(new String[] {"lien-espece", "--code", "Turmer"}, gbif.sortie(), gbif.erreur()))
                .isZero();
        assertThat(gbif.texte())
                .isEqualTo("https://www.gbif.org/species/search?q=Turdus+merula" + System.lineSeparator());
        assertThat(gbif.texteErreur()).isEmpty();

        injecteur
                .getInstance(fr.univ_amu.iut.commun.model.PreferenceSourceEspece.class)
                .definirPrefereWikipedia(true);
        SortieCapturee wikipedia = new SortieCapturee();
        assertThat(cli.executer(
                        new String[] {"lien-espece", "--code", "Turmer"}, wikipedia.sortie(), wikipedia.erreur()))
                .isZero();
        assertThat(wikipedia.texte()).isEqualTo("https://fr.wikipedia.org/wiki/Turdus_merula" + System.lineSeparator());
        assertThat(wikipedia.texteErreur()).isEmpty();
    }

    @Test
    void les_absences_expliquent_le_refus_sans_url() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Cli cli = Cli.applicative();
        String[][] cas = {
            {"lien-espece", "--code", "inconnu", "Taxon inconnu"},
            {"lien-espece", "--code", "noise", "Aucune fiche"},
            {"lien-participation", "--passage", "999", "Aucune participation liée"},
            {"lien-participation", "--passage", "invalide", "Erreur d'usage"}
        };
        for (String[] casRefuse : cas) {
            SortieCapturee capture = new SortieCapturee();
            assertThat(cli.executer(java.util.Arrays.copyOf(casRefuse, 3), capture.sortie(), capture.erreur()))
                    .isEqualTo(2);
            assertThat(capture.texte()).isEmpty();
            assertThat(capture.texteErreur()).contains(casRefuse[3]);
        }
    }
}
