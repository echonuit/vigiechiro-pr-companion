package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du [PointEditViewModel] : validation R2 du code, validation des coordonnées (optionnelles,
/// virgule décimale), pilotage du bouton, et enregistrement (création et édition via le service)
/// sur base SQLite jetable.
class PointEditViewModelTest {

    private static final String ID_USER = "u-1";

    @TempDir
    Path dossier;

    private ServiceSites service;
    private PointDao pointDao;
    private PointEditViewModel viewModel;
    private Site site;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);
        service = new ServiceSites(siteDao, pointDao, passageDao, new HorlogeFigee(LocalDate.now()));
        // Contrôle du carré STOC absent (#733) : le cas hors connexion, où la saisie doit rester entière.
        viewModel = new PointEditViewModel(service, Optional.empty());
        site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
    }

    @Test
    @DisplayName("#733 : sans contrôle du carré (hors connexion), la modale se tait et n'entrave rien")
    void sans_controle_carre_le_silence() {
        viewModel.preparerCreation(site);
        viewModel.codeProperty().set("A1");
        viewModel.latitudeProperty().set("43.5298");
        viewModel.longitudeProperty().set("5.4474");

        viewModel.appliquerControleCarre(viewModel.controlerCarre());

        assertThat(viewModel.messageCarreProperty().get())
                .as("le contrôle est un confort : sans plateforme, il ne dit rien plutôt que de se plaindre")
                .isEmpty();
        assertThat(viewModel.alerteCarreProperty().get()).isFalse();
        assertThat(viewModel.peutEnregistrer().get())
                .as("et il n'a jamais eu le pouvoir de bloquer l'enregistrement")
                .isTrue();
    }

    @Test
    @DisplayName("En création, un code vide interdit l'enregistrement (R2)")
    void code_vide_interdit() {
        viewModel.preparerCreation(site);

        assertThat(viewModel.codeValide().get()).isFalse();
        assertThat(viewModel.peutEnregistrer().get()).isFalse();
        assertThat(viewModel.titreProperty().get()).contains("640380");
        assertThat(viewModel.libelleBoutonProperty().get()).isEqualTo("+ Ajouter");
    }

    @Test
    @DisplayName("Un code valide (lettre + chiffre) autorise l'enregistrement")
    void code_valide_autorise() {
        viewModel.preparerCreation(site);

        viewModel.codeProperty().set("AA");
        assertThat(viewModel.codeValide().get()).isFalse();

        viewModel.codeProperty().set("A1");
        assertThat(viewModel.codeValide().get()).isTrue();
        assertThat(viewModel.peutEnregistrer().get()).isTrue();
    }

    @Test
    @DisplayName("Une coordonnée hors bornes bloque, la virgule décimale est tolérée")
    void coordonnees_validees() {
        viewModel.preparerCreation(site);
        viewModel.codeProperty().set("A1");

        viewModel.latitudeProperty().set("200");
        assertThat(viewModel.peutEnregistrer().get()).isFalse();

        viewModel.latitudeProperty().set("43,4010");
        assertThat(viewModel.peutEnregistrer().get()).isTrue();
    }

    @Test
    @DisplayName("coordonneesValides() : couple complet et borné seulement (sert à la carte-outil, #153)")
    void coordonnees_valides_pour_la_carte() {
        viewModel.preparerCreation(site);

        // Aucune saisie → vide (pas de marqueur réel à projeter).
        assertThat(viewModel.coordonneesValides()).isEmpty();

        // Latitude seule → couple incomplet → vide.
        viewModel.latitudeProperty().set("43.4010");
        assertThat(viewModel.coordonneesValides()).isEmpty();

        // Couple complet et borné (virgule tolérée) → présent, parsé.
        viewModel.longitudeProperty().set("-1,5740");
        assertThat(viewModel.coordonneesValides()).hasValueSatisfying(gps -> {
            assertThat(gps[0]).isEqualTo(43.4010);
            assertThat(gps[1]).isEqualTo(-1.5740);
        });

        // Latitude hors bornes (200, refusée par le formulaire) → vide : la carte ne projette pas un
        // point hors [-90,90] (finding #345).
        viewModel.latitudeProperty().set("200");
        assertThat(viewModel.coordonneesValides()).isEmpty();
    }

    @Test
    @DisplayName("Une saisie DMS est acceptée et convertie en degrés décimaux (#153)")
    void coordonnees_dms() {
        viewModel.preparerCreation(site);
        viewModel.codeProperty().set("A1");

        // Latitude en degrés/minutes/secondes, longitude en DMS ouest (négative).
        viewModel.latitudeProperty().set("43°24'3.6\"N");
        viewModel.longitudeProperty().set("1°34'26.4\"W");
        assertThat(viewModel.peutEnregistrer().get()).isTrue();

        assertThat(viewModel.coordonneesValides()).hasValueSatisfying(gps -> {
            assertThat(gps[0]).isCloseTo(43.401, org.assertj.core.api.Assertions.within(1e-6));
            assertThat(gps[1]).isCloseTo(-1.574, org.assertj.core.api.Assertions.within(1e-6));
        });
    }

    @Test
    @DisplayName("Enregistrer en création insère le point (coordonnées parsées)")
    void enregistrer_creation() {
        viewModel.preparerCreation(site);
        viewModel.codeProperty().set("A1");
        viewModel.latitudeProperty().set("43,4010");
        viewModel.longitudeProperty().set("-1,5740");
        viewModel.descriptionProperty().set("Près du chêne");

        boolean ok = viewModel.enregistrer();

        assertThat(ok).isTrue();
        assertThat(service.listerPoints(site.id())).singleElement().satisfies(point -> {
            assertThat(point.code()).isEqualTo("A1");
            assertThat(point.latitude()).isEqualTo(43.4010);
            assertThat(point.description()).isEqualTo("Près du chêne");
        });
    }

    @Test
    @DisplayName("Un code déjà pris est refusé : enregistrement faux + message d'erreur")
    void code_duplique_refuse() {
        service.ajouterPoint(site.id(), "A1", null, null, null);
        viewModel.preparerCreation(site);
        viewModel.codeProperty().set("A1");

        boolean ok = viewModel.enregistrer();

        assertThat(ok).isFalse();
        assertThat(viewModel.messageErreurProperty().get()).contains("A1");
        assertThat(service.listerPoints(site.id())).hasSize(1);
    }

    @Test
    @DisplayName("En édition, les champs sont pré-remplis et l'enregistrement met à jour le point")
    void enregistrer_edition() {
        PointDEcoute existant = service.ajouterPoint(site.id(), "A1", 43.40, -1.57, "Ancienne note");
        viewModel.preparerEdition(site, existant);

        assertThat(viewModel.codeProperty().get()).isEqualTo("A1");
        assertThat(viewModel.libelleBoutonProperty().get()).isEqualTo("Modifier");

        viewModel.descriptionProperty().set("Nouvelle note");
        boolean ok = viewModel.enregistrer();

        assertThat(ok).isTrue();
        assertThat(pointDao.findById(existant.id()))
                .get()
                .extracting(PointDEcoute::description)
                .isEqualTo("Nouvelle note");
    }

    @Test
    @DisplayName("En édition, viser le code d'un AUTRE point est refusé (unicité, message lisible)")
    void edition_vers_code_existant_refuse() {
        service.ajouterPoint(site.id(), "A1", null, null, null);
        PointDEcoute b1 = service.ajouterPoint(site.id(), "B1", null, null, null);
        viewModel.preparerEdition(site, b1);

        viewModel.codeProperty().set("A1");
        boolean ok = viewModel.enregistrer();

        assertThat(ok).isFalse();
        assertThat(viewModel.messageErreurProperty().get()).contains("A1").contains("existe déjà");
        assertThat(pointDao.findById(b1.id()))
                .get()
                .extracting(PointDEcoute::code)
                .isEqualTo("B1");
    }
}
