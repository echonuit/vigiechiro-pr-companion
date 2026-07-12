package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Pages du portail Vigie-Chiro ([PortailVigieChiro], #1124) sur table des liens **réelle** (SQLite
/// jetable) : URL construite pour une entité rattachée, vide sinon — aucune IHM, aucun réseau.
class PortailVigieChiroTest {

    @TempDir
    Path dossier;

    private LienVigieChiroDao liens;
    private PortailVigieChiro portail;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        liens = new LienVigieChiroDao(source);
        portail = new PortailVigieChiro(liens);
    }

    @Test
    @DisplayName("pageSite : site rattaché → URL #/sites/{objectid} du portail")
    void page_site_rattache() {
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, "7", "5eb12120cbe7410011f0a97f"));

        assertThat(portail.pageSite(7L)).contains("https://vigiechiro.herokuapp.com/#/sites/5eb12120cbe7410011f0a97f");
    }

    @Test
    @DisplayName("pageParticipation et pageDonnees : passage lié → URLs participation et données")
    void pages_participation_et_donnees() {
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, "42", "6a4961f587bc8dba39481180"));

        assertThat(portail.pageParticipation(42L))
                .contains("https://vigiechiro.herokuapp.com/#/participations/6a4961f587bc8dba39481180");
        assertThat(portail.pageDonnees(42L))
                .contains("https://vigiechiro.herokuapp.com/#/participations/6a4961f587bc8dba39481180/donnees");
    }

    @Test
    @DisplayName("entité non rattachée (ou référence nulle) → vide, l'action IHM reste désactivée")
    void entite_non_rattachee() {
        assertThat(portail.pageSite(7L)).isEmpty();
        assertThat(portail.pageParticipation(42L)).isEmpty();
        assertThat(portail.pageDonnees(42L)).isEmpty();
        assertThat(portail.pageSite(null)).isEmpty();
    }
}
