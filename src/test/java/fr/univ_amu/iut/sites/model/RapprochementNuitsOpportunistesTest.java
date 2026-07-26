package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Marquage rétroactif des nuits réalisées sur un carré de tiers (#2525). Base SQLite jetable, topologie
/// semée par [JeuDeDonneesPassage] : un carré à moi (640001) et un carré d'un tiers (640002), chacun
/// portant une nuit. Le client n'est pas utilisé (ce rapprocheur ne fait aucun appel réseau).
class RapprochementNuitsOpportunistesTest {

    private static final String ID_USER = "u-1";

    @TempDir
    Path dossier;

    private SiteDao siteDao;
    private SiteTiersDao siteTiers;
    private PassageOpportunisteDao opportunistes;
    private RapprochementNuitsOpportunistes rapprochement;
    private long nuitDuTiers;
    private long maNuit;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        maNuit = semer("640001", "A1");
        nuitDuTiers = semer("640002", "B1");
        siteDao = new SiteDao(source);
        siteTiers = new SiteTiersDao(source);
        opportunistes = new PassageOpportunisteDao(source);
        rapprochement = new RapprochementNuitsOpportunistes(
                siteTiers, new PointDao(source), new PassageDao(source), opportunistes);
    }

    private long semer(String carre, String point) {
        return JeuDeDonneesPassage.dans(new SourceDeDonnees(new Workspace(dossier)))
                .utilisateur(ID_USER)
                .carre(carre)
                .nomSite("Site " + carre)
                .point(point)
                .semer()
                .idPassage();
    }

    /// Identifiant local du carré `carre`.
    private long idSite(String carre) {
        return siteDao.findByUtilisateur(ID_USER).stream()
                .filter(site -> site.numeroCarre().equals(carre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Carré absent : " + carre))
                .id();
    }

    @Test
    @DisplayName("Il s'exécute APRÈS le rapprocheur des sites, dont il lit le référentiel")
    void phase_dependante() {
        assertThat(rapprochement.phase()).isEqualTo(RapprochementVigieChiro.Phase.DEPENDANTE);
    }

    @Test
    @DisplayName("#2525 : les nuits d'un carré de tiers sont marquées, les miennes ne le sont pas")
    void marque_les_nuits_des_carres_de_tiers() {
        siteTiers.marquer(idSite("640002"));

        assertThat(rapprochement.synchroniser(mock(ClientVigieChiro.class)))
                .contains(new RapportSynchro("nuits opportunistes", 1));
        assertThat(opportunistes.estOpportuniste(nuitDuTiers)).isTrue();
        assertThat(opportunistes.estOpportuniste(maNuit))
                .as("mon propre carré : rien à marquer")
                .isFalse();
    }

    @Test
    @DisplayName("Rejouer la synchro ne remarque rien et ne rend aucun compte (idempotent)")
    void rejeu_idempotent() {
        siteTiers.marquer(idSite("640002"));
        rapprochement.synchroniser(mock(ClientVigieChiro.class));

        assertThat(rapprochement.synchroniser(mock(ClientVigieChiro.class)))
                .as("tout est déjà à jour : synchro de routine, silence")
                .isEmpty();
        assertThat(opportunistes.estOpportuniste(nuitDuTiers)).isTrue();
    }

    @Test
    @DisplayName("Aucun carré de tiers : rien à faire, aucun compte rendu")
    void aucun_carre_de_tiers() {
        assertThat(rapprochement.synchroniser(mock(ClientVigieChiro.class))).isEmpty();
        assertThat(opportunistes.tousLesIds()).isEmpty();
    }

    @Test
    @DisplayName("#2525 : une saisie manuelle n'est jamais démarquée (sens unique)")
    void ne_demarque_jamais_une_saisie_manuelle() {
        // Nuit d'une participation NON connectée, cochée à la main : son carré n'est pas dans site_tiers.
        opportunistes.marquer(maNuit);
        siteTiers.marquer(idSite("640002"));

        rapprochement.synchroniser(mock(ClientVigieChiro.class));

        assertThat(opportunistes.estOpportuniste(maNuit))
                .as("la saisie manuelle survit à la synchronisation")
                .isTrue();
    }
}
