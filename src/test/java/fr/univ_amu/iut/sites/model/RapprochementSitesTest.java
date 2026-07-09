package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Rapprochement des sites (#728), API et DAO site **mockés**, DAO de liens réel (base jetable) : on
/// vérifie l'heuristique de titre ([RapprochementSites#correspond] : carré contenu dans le titre, ou nom
/// convivial égal au titre), le bout-à-bout de synchronisation, et l'absence de purge hors-ligne.
@ExtendWith(MockitoExtension.class)
class RapprochementSitesTest {

    @TempDir
    Path dossier;

    @Mock
    private ClientVigieChiro client;

    @Mock
    private SiteDao siteDao;

    private LienVigieChiroDao liens;
    private RapprochementSites rapprochement;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        liens = new LienVigieChiroDao(source);
        rapprochement = new RapprochementSites(siteDao, liens);
    }

    private static Site site(Long id, String carre, String nom) {
        return new Site(id, carre, nom, Protocole.STANDARD, null, "2026-06-01", "u1");
    }

    @Test
    @DisplayName("correspond : titre contenant le carré, ou nom convivial égal au titre (casse ignorée)")
    void correspond_par_carre_ou_nom() {
        assertThat(RapprochementSites.correspond(
                        site(1L, "810123", "Jardin"), new SiteVigieChiro("x", "Carre 810123", true)))
                .isTrue();
        assertThat(RapprochementSites.correspond(
                        site(1L, "810123", "Mon Jardin"), new SiteVigieChiro("x", "mon jardin", false)))
                .isTrue();
        assertThat(RapprochementSites.correspond(
                        site(1L, "810123", "Jardin"), new SiteVigieChiro("x", "Autre 999999", false)))
                .isFalse();
        assertThat(RapprochementSites.correspond(site(1L, "810123", null), new SiteVigieChiro("x", null, false)))
                .isFalse();
    }

    @Test
    @DisplayName("relie chaque site local au premier site VigieChiro correspondant, par son id technique")
    void relie_les_sites_locaux() {
        when(siteDao.findAll()).thenReturn(List.of(site(11L, "810123", "Jardin"), site(22L, "999999", "Colline")));
        when(client.mesSites())
                .thenReturn(List.of(
                        new SiteVigieChiro("s1", "STOC 810123 (parcelle A)", true),
                        new SiteVigieChiro("s2", "Colline", false),
                        new SiteVigieChiro("s3", "Site sans correspondance", false)));

        Optional<RapportSynchro> rapport = rapprochement.synchroniser(client);

        assertThat(rapport).contains(new RapportSynchro("sites", 2));
        assertThat(liens.tous(LienVigieChiro.ENTITE_SITE)).containsOnly(Map.entry("11", "s1"), Map.entry("22", "s2"));
    }

    @Test
    @DisplayName("hors-ligne (aucun site renvoyé) : rapport vide, ne purge pas les correspondances")
    void hors_ligne_ne_purge_pas() {
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, "11", "s1"));
        when(siteDao.findAll()).thenReturn(List.of(site(11L, "810123", "Jardin")));
        when(client.mesSites()).thenReturn(List.of());

        assertThat(rapprochement.synchroniser(client)).isEmpty();

        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_SITE, "11")).contains("s1");
    }
}
