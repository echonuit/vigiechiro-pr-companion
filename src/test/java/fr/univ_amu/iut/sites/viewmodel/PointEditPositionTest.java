package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.ServiceCommunes;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.PointPublieDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La **position** et le **code proposé** d'un point d'écoute (#5688) : un seul champ, lu comme celui du
/// site, un code `Z` suivant, et un voisin signalé. Sortis de [PointEditViewModelTest], qui atteignait le
/// plafond de taille du portail qualité.
class PointEditPositionTest {

    private static final String ID_USER = "u-1";

    @TempDir
    Path dossier;

    private ServiceSites service;
    private PointEditViewModel viewModel;
    private Site site;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        PointDao pointDao = new PointDao(source);
        PointCommuneDao communeDao = new PointCommuneDao(source);
        service = new ServiceSites(
                new SiteDao(source),
                pointDao,
                new PassageDao(source),
                new HorlogeFigee(LocalDate.now()),
                communeDao,
                () -> {});
        viewModel = new PointEditViewModel(
                service,
                new ServiceCommunes(pointDao, communeDao, position -> Optional.empty()),
                Optional.empty(),
                new PublicationDepuisLaFiche(
                        new PointPublieDao(source), new LienVigieChiroDao(source), Optional.empty()));
        site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
    }

    /// Une seule règle de lecture pour toute l'interface (#5688) : le point lit ce que lit le site, y
    /// compris ce que l'ancienne lecture axe par axe ne savait pas dire (degrés-minutes décimales en
    /// paire, refus motivé d'une virgule décimale).
    @Test
    @DisplayName("#5688 : un seul champ « Position », lu comme celui du site, et le point est enregistré là")
    void la_position_se_lit_comme_celle_du_site() {
        viewModel.preparerCreation(site);
        viewModel.codeProperty().set("Z1");
        viewModel.positionProperty().set("43°24.06'N 5°26.85'E");

        assertThat(viewModel.retourPositionProperty().get().texte()).isEmpty();
        assertThat(viewModel.enregistrer()).isTrue();
        assertThat(service.listerPoints(site.id())).singleElement().satisfies(point -> {
            assertThat(point.latitude()).isCloseTo(43.401, within(0.00001));
            assertThat(point.longitude()).isCloseTo(5.4475, within(0.00001));
        });
    }

    @Test
    @DisplayName("#5688 : un texte illisible dit son motif et ferme l'enregistrement")
    void un_texte_illisible_ferme_l_enregistrement() {
        viewModel.preparerCreation(site);
        viewModel.codeProperty().set("Z1");

        viewModel.positionProperty().set("43,401, 5,447");

        assertThat(viewModel.retourPositionProperty().get().texte()).contains("point décimal");
        assertThat(viewModel.peutEnregistrer().get()).isFalse();
    }

    @Test
    @DisplayName("#5688 : une position hors des limites du globe est refusée, avec son motif")
    void une_position_hors_limites_est_refusee() {
        viewModel.preparerCreation(site);
        viewModel.codeProperty().set("Z1");

        viewModel.positionProperty().set("95.0, 5.0");

        assertThat(viewModel.retourPositionProperty().get().texte()).contains("latitude");
        assertThat(viewModel.peutEnregistrer().get()).isFalse();
    }

    @Test
    @DisplayName("#5688 : un champ vide enregistre un point sans position, comme avant")
    void un_champ_vide_enregistre_sans_position() {
        viewModel.preparerCreation(site);
        viewModel.codeProperty().set("Z1");

        assertThat(viewModel.enregistrer()).isTrue();
        assertThat(service.listerPoints(site.id())).singleElement().satisfies(point -> {
            assertThat(point.latitude()).isNull();
            assertThat(point.longitude()).isNull();
        });
    }

    /// Glisser le marqueur écrit la position dans le champ (#5688), sous une forme que la lecture relit :
    /// sans cela, le champ et la carte se contrediraient dès le premier glisser.
    @Test
    @DisplayName("#5688 : le marqueur écrit la position dans le champ, et elle se relit à l'identique")
    void le_marqueur_ecrit_la_position() {
        viewModel.preparerCreation(site);

        viewModel.placer(43.4031, -1.5708);

        assertThat(viewModel.positionProperty().get()).isEqualTo("43.403100, -1.570800");
        assertThat(viewModel.coordonneesValides()).hasValueSatisfying(gps -> {
            assertThat(gps[0]).isEqualTo(43.4031);
            assertThat(gps[1]).isEqualTo(-1.5708);
        });
        assertThat(viewModel.retourPositionProperty().get().texte()).isEmpty();
    }

    @Test
    @DisplayName("#5688 : à la création, le code proposé est le Z suivant du site, et reste modifiable")
    void le_code_propose_est_le_z_suivant() {
        service.ajouterPoint(site.id(), "Z1", null, null, null);
        service.ajouterPoint(site.id(), "A1", null, null, null);

        viewModel.preparerCreation(site);

        assertThat(viewModel.codeProperty().get()).isEqualTo("Z2");
        viewModel.codeProperty().set("B2");
        assertThat(viewModel.codeValide().get())
                .as("le code proposé n'est qu'une proposition")
                .isTrue();
    }

    /// Un point déjà posé à 40 m au plus se signale, sans empêcher d'enregistrer (#5688).
    @Test
    @DisplayName("#5688 : un point du site à 30 m se signale par son code, et l'enregistrement reste possible")
    void un_point_voisin_se_signale() {
        service.ajouterPoint(site.id(), "Z1", 43.4, 5.4, null);
        viewModel.preparerCreation(site);

        viewModel.positionProperty().set("43.40027, 5.4"); // 30 m au nord

        assertThat(viewModel.retourVoisinProperty().get().texte())
                .contains("Z1")
                .contains("30 m");
        assertThat(viewModel.retourVoisinProperty().get().severite()).isEqualTo(Severite.AVERTISSEMENT);
        assertThat(viewModel.peutEnregistrer().get()).isTrue();

        viewModel.positionProperty().set("43.41, 5.4"); // plus d'un kilomètre
        assertThat(viewModel.retourVoisinProperty().get().texte()).isEmpty();
    }

    @Test
    @DisplayName("#5688 : les bornes du globe sont incluses, et une longitude au-delà dit son motif")
    void les_bornes_du_globe() {
        viewModel.preparerCreation(site);

        viewModel.positionProperty().set("90, 180");
        assertThat(viewModel.retourPositionProperty().get().texte()).isEmpty();
        assertThat(viewModel.peutEnregistrer().get()).isTrue();

        viewModel.positionProperty().set("-90, -180");
        assertThat(viewModel.peutEnregistrer().get()).isTrue();

        viewModel.positionProperty().set("43.4, 181");
        assertThat(viewModel.retourPositionProperty().get().texte()).contains("longitude");
        assertThat(viewModel.peutEnregistrer().get()).isFalse();
    }
}
