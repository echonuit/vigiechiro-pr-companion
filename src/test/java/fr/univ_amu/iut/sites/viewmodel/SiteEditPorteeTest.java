package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La modale d'édition d'un site dit **ce que son édition atteint** (#1380).
///
/// Renommer un site relié à Vigie-Chiro ne remonte rien à la plateforme. L'édition locale reste utile -
/// un nom d'usage aide à s'y retrouver - mais le silence crée une incohérence **perçue** entre ce qu'on
/// voit ici et ce que montre le portail.
class SiteEditPorteeTest {

    @TempDir
    Path dossier;

    private ServiceSites service;
    private LienVigieChiroDao liens;
    private SiteEditViewModel viewModel;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        liens = new LienVigieChiroDao(source);
        service = new ServiceSites(
                new SiteDao(source),
                new PointDao(source),
                new PassageDao(source),
                new HorlogeFigee(LocalDate.of(2026, 4, 22)),
                new PointCommuneDao(source),
                () -> {});
        viewModel = new SiteEditViewModel(service, liens, "u-1");
    }

    private Site siteDeclare() {
        return service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, null, "u-1");
    }

    @Test
    @DisplayName("#1380 : un site verrouillé annonce que l'édition reste locale")
    void site_verrouille_annonce_la_portee_locale() {
        Site site = siteDeclare();
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), "abc123", true));

        viewModel.preparerEdition(site);

        assertThat(viewModel.porteeEditionProperty().get().present()).isTrue();
        assertThat(viewModel.porteeEditionProperty().get().texte())
                .contains("locale")
                .contains("Vigie-Chiro");
    }

    @Test
    @DisplayName("#1380 : un site simplement enregistré l'annonce aussi")
    void site_enregistre_annonce_la_portee_locale() {
        Site site = siteDeclare();
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), "abc123", false));

        viewModel.preparerEdition(site);

        // Enregistré sans être verrouillé : la correspondance existe déjà côté plateforme, donc l'écart
        // entre les deux noms est tout aussi visible. Ne le dire que sur les verrouillés laisserait la
        // moitié des cas dans le silence.
        assertThat(viewModel.porteeEditionProperty().get().present()).isTrue();
    }

    @Test
    @DisplayName("#1380 : un site absent de la plateforme n'a rien à annoncer")
    void site_absent_ne_dit_rien() {
        Site site = siteDeclare();

        viewModel.preparerEdition(site);

        // Aucune correspondance : il n'y a pas d'écart possible entre local et plateforme, et un message
        // permanent sur le cas nominal serait du bruit.
        assertThat(viewModel.porteeEditionProperty().get().present()).isFalse();
    }

    @Test
    @DisplayName("#1380 : une déclaration ne parle pas de portée")
    void creation_ne_dit_rien() {
        Site site = siteDeclare();
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), "abc123", true));
        viewModel.preparerEdition(site);

        viewModel.preparerCreation();

        // Le message doit disparaître quand la même modale sert à déclarer : un site qui n'existe pas
        // encore n'est relié à rien.
        assertThat(viewModel.porteeEditionProperty().get().present()).isFalse();
    }
}
