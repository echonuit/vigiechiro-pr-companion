package fr.univ_amu.iut.sites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du service métier de référence [ServiceSites], sur une base SQLite jetable
/// (`@TempDir` + [MigrationSchema]), comme les `*DaoTest`.
///
/// L'[HorlogeFigee] rend la date de création déterministe (assertion exacte possible). Les
/// passages utilisés pour le test de refus de suppression sont insérés via les DAO réels (le
/// service est exercé de bout en bout sur une vraie base, pas sur des mocks).
class ServiceSitesTest {

    private static final String ID_USER = "u-1";
    private static final LocalDate JOUR_FIXE = LocalDate.of(2026, 5, 31);

    @TempDir
    Path dossier;

    private ServiceSites service;
    private SiteDao siteDao;
    private PointDao pointDao;
    private PassageDao passageDao;
    private EnregistreurDao enregistreurDao;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        siteDao = new SiteDao(source);
        pointDao = new PointDao(source);
        passageDao = new PassageDao(source);
        enregistreurDao = new EnregistreurDao(source);
        service = new ServiceSites(siteDao, pointDao, passageDao, new HorlogeFigee(JOUR_FIXE));
    }

    // --- Création de site (R1, protocole, R5) ---

    @Test
    @DisplayName("Créer un site valide l'insère, date de création lue de l'horloge")
    void creer_site_valide() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);

        assertThat(site.id()).isNotNull();
        assertThat(site.dateCreation()).isEqualTo("2026-05-31");
        assertThat(siteDao.findById(site.id())).isPresent();
        assertThat(service.listerSites(ID_USER)).extracting(Site::numeroCarre).containsExactly("640380");
    }

    @Test
    @DisplayName("R1 : un numéro de carré mal formé est refusé (IllegalArgumentException)")
    void creer_site_carre_invalide() {
        assertThatThrownBy(() -> service.creerSite("64038", null, Protocole.STANDARD, null, ID_USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Protocole null bascule sur PointFixeStandard par défaut (P1)")
    void creer_site_protocole_par_defaut() {
        Site site = service.creerSite("123456", null, null, null, ID_USER);

        assertThat(site.protocole()).isEqualTo(Protocole.STANDARD);
    }

    @Test
    @DisplayName("R5 : deux fois le même carré pour le même utilisateur est refusé")
    void creer_site_carre_duplique_meme_utilisateur() {
        service.creerSite("888888", null, Protocole.STANDARD, null, ID_USER);

        assertThatThrownBy(() -> service.creerSite("888888", null, Protocole.STANDARD, null, ID_USER))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("888888");
    }

    @Test
    @DisplayName("R5 : le même carré reste possible pour deux utilisateurs distincts")
    void creer_site_meme_carre_utilisateurs_distincts() {
        new UtilisateurDao(new SourceDeDonnees(new Workspace(dossier))).insert(new Utilisateur("u-2", "Autre"));
        service.creerSite("777777", null, Protocole.STANDARD, null, ID_USER);

        Site autre = service.creerSite("777777", null, Protocole.STANDARD, null, "u-2");

        assertThat(autre.id()).isNotNull();
    }

    // --- Modification de site (R1, R5, conservation des invariants) ---

    @Test
    @DisplayName("Modifier un site met à jour les champs et conserve id, date de création et propriétaire")
    void modifier_site_conserve_invariants() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, "Aix", ID_USER);

        Site modifie = service.modifierSite(site.id(), "130010", "Calanques", Protocole.RECHERCHE, "Marseille");

        assertThat(modifie.id()).isEqualTo(site.id());
        assertThat(modifie.dateCreation()).isEqualTo(site.dateCreation());
        assertThat(modifie.idUtilisateur()).isEqualTo(ID_USER);
        assertThat(modifie.numeroCarre()).isEqualTo("130010");
        assertThat(modifie.nomConvivial()).isEqualTo("Calanques");
        assertThat(modifie.protocole()).isEqualTo(Protocole.RECHERCHE);
        assertThat(service.listerSites(ID_USER))
                .singleElement()
                .satisfies(enregistre -> assertThat(enregistre.numeroCarre()).isEqualTo("130010"));
    }

    @Test
    @DisplayName("Modifier un site introuvable est refusé")
    void modifier_site_introuvable_refuse() {
        assertThatThrownBy(() -> service.modifierSite(999L, "640380", null, Protocole.STANDARD, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    @DisplayName("R1 : modifier vers un numéro de carré mal formé est refusé")
    void modifier_site_carre_invalide_refuse() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);

        assertThatThrownBy(() -> service.modifierSite(site.id(), "64038", null, Protocole.STANDARD, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("R5 : modifier vers un carré déjà pris par un autre site du même utilisateur est refusé")
    void modifier_site_carre_pris_par_autre_refuse() {
        Site premier = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);
        service.creerSite("130010", null, Protocole.STANDARD, null, ID_USER);

        assertThatThrownBy(() -> service.modifierSite(premier.id(), "130010", null, Protocole.STANDARD, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("130010");
    }

    @Test
    @DisplayName("Modifier un site en gardant son carré reste possible (pas de faux conflit avec lui-même)")
    void modifier_site_meme_carre_possible() {
        Site site = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);

        Site modifie = service.modifierSite(site.id(), "640380", "Renommé", Protocole.STANDARD, null);

        assertThat(modifie.nomConvivial()).isEqualTo("Renommé");
    }

    // --- Ajout de point (R2, unicité code) ---

    @Test
    @DisplayName("Ajouter un point valide l'insère et le rattache au site")
    void ajouter_point_valide() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);

        PointDEcoute point = service.ajouterPoint(site.id(), "A1", 43.5, 5.4, "Lisière");

        assertThat(point.id()).isNotNull();
        assertThat(point.idSite()).isEqualTo(site.id());
        assertThat(service.listerPoints(site.id()))
                .extracting(PointDEcoute::code)
                .containsExactly("A1");
    }

    @Test
    @DisplayName("R2 : un code de point mal formé est refusé (IllegalArgumentException)")
    void ajouter_point_code_invalide() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);

        assertThatThrownBy(() -> service.ajouterPoint(site.id(), "AA", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Le code de point doit être unique dans le site")
    void ajouter_point_code_duplique_dans_site() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(site.id(), "A1", null, null, null);

        assertThatThrownBy(() -> service.ajouterPoint(site.id(), "A1", null, null, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("A1");
    }

    @Test
    @DisplayName("Le même code reste possible dans deux sites différents")
    void ajouter_point_meme_code_sites_distincts() {
        Site site1 = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);
        Site site2 = service.creerSite("640381", null, Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(site1.id(), "A1", null, null, null);

        PointDEcoute point2 = service.ajouterPoint(site2.id(), "A1", null, null, null);

        assertThat(point2.id()).isNotNull();
    }

    @Test
    @DisplayName("Ajouter un point à un site inconnu est refusé")
    void ajouter_point_site_inconnu() {
        assertThatThrownBy(() -> service.ajouterPoint(9999L, "A1", null, null, null))
                .isInstanceOf(RegleMetierException.class);
    }

    // --- Modification de point (R2, intégrité point/site, unicité hors soi) ---

    @Test
    @DisplayName("Modifier un point met à jour code et coordonnées")
    void modifier_point_valide() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = service.ajouterPoint(site.id(), "A1", null, null, "Avant");

        service.modifierPoint(point.id(), site.id(), "B2", 43.5, 5.4, "Après");

        assertThat(pointDao.findById(point.id())).get().satisfies(p -> {
            assertThat(p.code()).isEqualTo("B2");
            assertThat(p.latitude()).isEqualTo(43.5);
            assertThat(p.description()).isEqualTo("Après");
        });
    }

    @Test
    @DisplayName("Conserver son propre code en modification est autorisé (pas de faux conflit)")
    void modifier_point_conserve_son_code() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = service.ajouterPoint(site.id(), "A1", null, null, "Avant");

        PointDEcoute maj = service.modifierPoint(point.id(), site.id(), "A1", null, null, "Après");

        assertThat(maj.code()).isEqualTo("A1");
        assertThat(pointDao.findById(point.id()))
                .get()
                .extracting(PointDEcoute::description)
                .isEqualTo("Après");
    }

    @Test
    @DisplayName("Modifier vers le code d'un AUTRE point du site est refusé (unicité)")
    void modifier_point_code_duplique_refuse() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(site.id(), "A1", null, null, null);
        PointDEcoute b1 = service.ajouterPoint(site.id(), "B1", null, null, null);

        assertThatThrownBy(() -> service.modifierPoint(b1.id(), site.id(), "A1", null, null, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("A1");
    }

    @Test
    @DisplayName("Modifier un point introuvable est refusé")
    void modifier_point_introuvable() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);

        assertThatThrownBy(() -> service.modifierPoint(9999L, site.id(), "A1", null, null, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    @DisplayName("Modifier un point avec un site incohérent est refusé (intégrité)")
    void modifier_point_autre_site_refuse() {
        Site site1 = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);
        Site site2 = service.creerSite("640381", null, Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = service.ajouterPoint(site1.id(), "A1", null, null, null);

        assertThatThrownBy(() -> service.modifierPoint(point.id(), site2.id(), "A1", null, null, null))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("n'appartient pas");
    }

    @Test
    @DisplayName("#154 : déplacer un point ne change QUE le GPS (code et description préservés)")
    void deplacer_point_ne_change_que_le_gps() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = service.ajouterPoint(site.id(), "A1", 43.40, -1.57, "Près du chêne");

        PointDEcoute deplace = service.deplacerPoint(point.id(), 43.4055, -1.5680);

        assertThat(deplace.latitude()).isEqualTo(43.4055);
        assertThat(deplace.longitude()).isEqualTo(-1.5680);
        assertThat(pointDao.findById(point.id())).get().satisfies(p -> {
            assertThat(p.latitude()).isEqualTo(43.4055);
            assertThat(p.longitude()).isEqualTo(-1.5680);
            assertThat(p.code()).as("le code est préservé").isEqualTo("A1");
            assertThat(p.description()).as("la description est préservée").isEqualTo("Près du chêne");
            assertThat(p.idSite()).isEqualTo(site.id());
        });
    }

    @Test
    @DisplayName("#154 : déplacer un point introuvable est refusé")
    void deplacer_point_introuvable() {
        assertThatThrownBy(() -> service.deplacerPoint(9999L, 43.4, -1.5))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("introuvable");
    }

    // --- Suppression de site (refus si passage rattaché) ---

    @Test
    @DisplayName("Supprimer un site sans passage retire aussi ses points (cascade)")
    void supprimer_site_sans_passage() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = service.ajouterPoint(site.id(), "A1", null, null, null);

        service.supprimerSite(site.id());

        assertThat(siteDao.findById(site.id())).isEmpty();
        assertThat(pointDao.findById(point.id()))
                .as("point supprimé par cascade")
                .isEmpty();
    }

    @Test
    @DisplayName("Supprimer un site avec un passage rattaché est refusé, rien n'est supprimé")
    void supprimer_site_avec_passage_refuse() {
        Site site = service.creerSite("640380", null, Protocole.STANDARD, null, ID_USER);
        PointDEcoute point = service.ajouterPoint(site.id(), "A1", null, null, null);
        enregistreurDao.insert(new Enregistreur("1925492", "V1.01", null));
        passageDao.insert(new Passage(
                null,
                1,
                2026,
                "2026-06-20",
                "21:00:00",
                "05:00:00",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                null,
                null,
                point.id(),
                "1925492"));

        assertThatThrownBy(() -> service.supprimerSite(site.id()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("passage");

        assertThat(siteDao.findById(site.id())).as("site conservé").isPresent();
        assertThat(passageDao.findByPoint(point.id())).as("passage conservé").isNotEmpty();
    }

    // --- Rappels protocole (R3, règle soft → ResultatVerification) ---

    @Test
    @DisplayName("R3 : un site PointFixeStandard émet un rappel soft non bloquant")
    void rappels_protocole_standard() {
        var resultat = service.rappelsProtocole(Protocole.STANDARD);

        assertThat(resultat.estConforme()).isFalse();
        assertThat(resultat.estBloquant())
                .as("rappel R3 = soft, jamais bloquant")
                .isFalse();
        assertThat(resultat.messages()).hasSize(1);
    }

    @Test
    @DisplayName("R3 : un site PointFixeRecherche est muet (résultat conforme)")
    void rappels_protocole_recherche() {
        var resultat = service.rappelsProtocole(Protocole.RECHERCHE);

        assertThat(resultat.estConforme()).isTrue();
    }

    @Test
    @DisplayName("compterSites() et compterPoints() reflètent les insertions (compteurs d'accueil)")
    void compteurs_du_tableau_de_bord() {
        assertThat(service.compterSites()).isZero();
        assertThat(service.compterPoints()).isZero();

        Site etang = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        service.creerSite("752204", "ZAC Nord", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(etang.id(), "A1", 43.5, 5.4, "Chêne");

        assertThat(service.compterSites()).isEqualTo(2L);
        assertThat(service.compterPoints()).isEqualTo(1L);
    }
}
