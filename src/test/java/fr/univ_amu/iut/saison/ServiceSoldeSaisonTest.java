package fr.univ_amu.iut.saison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.saison.model.SoldeSaison;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du service [ServiceSoldeSaison] sur une base SQLite jetable (`@TempDir` + [MigrationSchema]),
/// topologie semée par [JeuDeDonneesPassage]. L'[HorlogeFigee] au **2026-07-20** (dans la fenêtre du
/// premier passage, avant celle du second) rend déterministes les phrases « avant le … ».
///
/// Jeu de données de la saison 2026 (utilisateur `u-solde`, tous sites PointFixeStandard) :
///
/// ```
///   640001 / A1 : P1 Déposé/OK          P2 Déposé/OK          → rien (à jour)
///   640001 / A2 : P1 Déposé/OK          P2 Prêt à déposer/OK  → Téléverser la nuit du 21/08
///   640002 / B1 : P1 Déposé/OK          P2 absent             → Poser l'enregistreur avant le 30/09
///   640002 / B2 : P1 Vérifié/À jeter    P2 absent             → Refaire le 1er passage
///   640003 / C1 : P1 Importé/(non vérifié) P2 absent          → Transformer la nuit du 24/06
///   640004 / D1 : P1 absent             P2 absent             → Poser l'enregistreur avant le 31/07
/// ```
///
/// Plus un site **640099 (RECHERCHE)** avec un point, qui doit être **écarté** du solde.
class ServiceSoldeSaisonTest {

    private static final String ID_USER = "u-solde";

    @TempDir
    Path dossier;

    private Injector injecteur;
    private SourceDeDonnees source;
    private SiteDao siteDao;
    private ServiceSoldeSaison service;
    private PassageOpportunisteDao opportunistes;
    private SiteTiersDao carresDeTiers;
    private ServiceCampagne campagnes;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", dossier.toString());
        injecteur = Guice.createInjector(
                new CommunModule(), new PersistenceModule(), new SitesModule(), new PassageModule());
        source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        semer("640001", "A1", 1, 2026, "2026-06-20", StatutWorkflow.DEPOSE, Verdict.OK);
        semer("640001", "A1", 2, 2026, "2026-08-20", StatutWorkflow.DEPOSE, Verdict.OK);
        semer("640001", "A2", 1, 2026, "2026-06-21", StatutWorkflow.DEPOSE, Verdict.OK);
        semer("640001", "A2", 2, 2026, "2026-08-21", StatutWorkflow.PRET_A_DEPOSER, Verdict.OK);
        semer("640002", "B1", 1, 2026, "2026-06-22", StatutWorkflow.DEPOSE, Verdict.OK);
        semer("640002", "B2", 1, 2026, "2026-06-23", StatutWorkflow.VERIFIE, Verdict.A_JETER);
        semer("640003", "C1", 1, 2026, "2026-06-24", StatutWorkflow.IMPORTE, null);
        // 640004 / D1 : un point déclaré, aucune nuit (semé via DAO pour n'avoir aucun passage).
        siteDao = injecteur.getInstance(SiteDao.class);
        PointDao pointDao = injecteur.getInstance(PointDao.class);
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("640004")
                .nomSite("Site 640004")
                .point("D1")
                .semerSiteEtPoint();
        // Site RECHERCHE, à écarter.
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("640099")
                .nomSite("Labo")
                .protocole(Protocole.RECHERCHE)
                .point("R1")
                .semerSiteEtPoint();

        opportunistes = injecteur.getInstance(PassageOpportunisteDao.class);
        carresDeTiers = injecteur.getInstance(SiteTiersDao.class);
        campagnes = new ServiceCampagne(
                new CampagneDao(source),
                injecteur.getInstance(PassageDao.class),
                new HorlogeFigee(LocalDate.of(2026, 7, 20)));
        service = new ServiceSoldeSaison(
                siteDao,
                pointDao,
                injecteur.getInstance(PassageDao.class),
                injecteur.getInstance(PointCommuneDao.class),
                opportunistes,
                carresDeTiers,
                Optional.of(campagnes),
                new HorlogeFigee(LocalDate.of(2026, 7, 20)));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private long semer(
            String carre, String point, int numero, int annee, String date, StatutWorkflow statut, Verdict verdict) {
        return JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(carre)
                .nomSite("Site " + carre)
                .point(point)
                .nuit(numero, annee, date)
                .statut(statut)
                .verdict(verdict)
                .semer()
                .idPassage();
    }

    private LigneSaison ligne(SoldeSaison solde, String carre, String point) {
        return solde.lignes().stream()
                .filter(l -> l.numeroCarre().equals(carre) && l.codePoint().equals(point))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ligne absente : " + carre + " / " + point));
    }

    @Test
    @DisplayName("une ligne par point suivi, triée par carré puis par code de point")
    void une_ligne_par_point_triee() {
        SoldeSaison solde = service.soldePour(ID_USER, 2026);
        assertThat(solde.lignes())
                .extracting(LigneSaison::numeroCarre, LigneSaison::codePoint)
                .containsExactly(
                        tuple("640001", "A1"),
                        tuple("640001", "A2"),
                        tuple("640002", "B1"),
                        tuple("640002", "B2"),
                        tuple("640003", "C1"),
                        tuple("640004", "D1"));
    }

    @Test
    @DisplayName("#3215 : le nom convivial du carré remonte dans la ligne, apparié à SON carré")
    void nom_du_site_remonte() {
        // Ce que ce test protège n'est pas la présence du champ mais son appariement : un nom pris sur
        // le carré voisin passerait toute autre vérification, et la recherche trouverait le mauvais lieu.
        SoldeSaison solde = service.soldePour(ID_USER, 2026);

        assertThat(solde.lignes())
                .extracting(LigneSaison::numeroCarre, LigneSaison::nomSite)
                .contains(tuple("640001", "Site 640001"), tuple("640002", "Site 640002"));
    }

    @Test
    @DisplayName("la colonne « reste à faire » formule une action, cas par cas")
    void reste_a_faire_par_cas() {
        SoldeSaison solde = service.soldePour(ID_USER, 2026);
        assertThat(ligne(solde, "640001", "A1").resteAFaire())
                .as("deux passages déposés")
                .isEmpty();
        assertThat(ligne(solde, "640001", "A2").resteAFaire()).isEqualTo("Téléverser la nuit du 21/08");
        assertThat(ligne(solde, "640002", "B1").resteAFaire()).isEqualTo("Poser l'enregistreur avant le 30/09");
        assertThat(ligne(solde, "640002", "B2").resteAFaire()).isEqualTo("Refaire le 1er passage");
        assertThat(ligne(solde, "640003", "C1").resteAFaire()).isEqualTo("Transformer la nuit du 24/06");
        assertThat(ligne(solde, "640004", "D1").resteAFaire()).isEqualTo("Poser l'enregistreur avant le 31/07");
    }

    @Test
    @DisplayName("un passage inexploitable compte comme restant à faire, pas comme fait")
    void inexploitable_compte_comme_a_refaire() {
        SoldeSaison solde = service.soldePour(ID_USER, 2026);
        LigneSaison b2 = ligne(solde, "640002", "B2");
        assertThat(b2.passage1().inexploitable()).isTrue();
        assertThat(b2.passage1().faite())
                .as("inexploitable ne compte pas comme fait")
                .isFalse();
        assertThat(b2.resteAFaire()).isEqualTo("Refaire le 1er passage");
    }

    @Test
    @DisplayName("le décompte d'en-tête est dérivé des mêmes lignes")
    void decompte_entete_derive_des_lignes() {
        SoldeSaison solde = service.soldePour(ID_USER, 2026);
        assertThat(solde.pointsSuivis()).isEqualTo(6);
        assertThat(solde.passagesAttendus()).isEqualTo(12);
        assertThat(solde.passagesFaits())
                .as("A1(2) + A2(2) + B1(1) + C1(1), B2 inexploitable exclu")
                .isEqualTo(6);
        assertThat(solde.pointsAJour()).as("seul A1 est à jour").isEqualTo(1);
    }

    @Test
    @DisplayName("signalement de la fenêtre du second passage : échéance et points en attente")
    void signalement_second_passage() {
        SoldeSaison solde = service.soldePour(ID_USER, 2026);
        assertThat(solde.premierPassageAttenduDes()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(solde.echeanceSecondPassage()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(solde.joursAvantEcheanceSecondPassage()).isEqualTo(72);
        assertThat(solde.pointsSecondPassageEnAttente())
                .as("B1, B2, C1, D1 sans P2 valable")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("un site en protocole RECHERCHE est écarté du solde")
    void site_recherche_ecarte() {
        SoldeSaison solde = service.soldePour(ID_USER, 2026);
        assertThat(solde.lignes()).extracting(LigneSaison::numeroCarre).doesNotContain("640099");
    }

    @Test
    @DisplayName("le solde ne mélange pas les années")
    void filtre_par_annee() {
        semer("640001", "A1", 1, 2025, "2025-06-20", StatutWorkflow.DEPOSE, Verdict.OK);

        SoldeSaison solde2026 = service.soldePour(ID_USER, 2026);
        assertThat(ligne(solde2026, "640001", "A1").passage1().date())
                .as("la nuit 2026 de A1, pas celle de 2025")
                .isEqualTo(LocalDate.of(2026, 6, 20));

        SoldeSaison solde2025 = service.soldePour(ID_USER, 2025);
        LigneSaison a1en2025 = ligne(solde2025, "640001", "A1");
        assertThat(a1en2025.passage1().date()).isEqualTo(LocalDate.of(2025, 6, 20));
        assertThat(a1en2025.passage2().presente()).as("aucun P2 en 2025").isFalse();
    }

    @Test
    @DisplayName("soldeCourant utilise l'année de l'horloge")
    void solde_courant_utilise_annee_horloge() {
        assertThat(service.soldeCourant(ID_USER).annee()).isEqualTo(2026);
        assertThat(service.soldeCourant(ID_USER).lignes()).hasSize(6);
    }

    @Test
    @DisplayName("un observateur sans site déclaré a un solde vide")
    void observateur_sans_site_solde_vide() {
        SoldeSaison solde = service.soldePour("inconnu", 2026);
        assertThat(solde.lignes()).isEmpty();
        assertThat(solde.pointsSuivis()).isZero();
        assertThat(solde.passagesAttendus()).isZero();
    }

    @Test
    @DisplayName("#2525 : les nuits opportunistes sont hors décompte et n'engendrent aucune action")
    void nuits_opportunistes_hors_protocole() {
        long id1 = semer("640005", "E1", 1, 2026, "2026-06-25", StatutWorkflow.DEPOSE, Verdict.OK);
        long id2 = semer("640005", "E1", 2, 2026, "2026-08-25", StatutWorkflow.DEPOSE, Verdict.OK);
        opportunistes.marquer(id1);
        opportunistes.marquer(id2);

        LigneSaison ligne = ligne(service.soldePour(ID_USER, 2026), "640005", "E1");

        // Les deux nuits sont hors protocole : elles quittent les colonnes de passage, qui redeviennent
        // « absentes ». Sans quoi une case remplie et un « reste à faire » réclamant ce même passage se
        // contrediraient sur la même ligne.
        assertThat(ligne.horsProtocole())
                .as("les deux nuits opportunistes vivent dans leur propre colonne")
                .hasSize(2)
                .allMatch(CasePassage::opportuniste);
        assertThat(ligne.passage1().presente())
                .as("le passage 1 PROTOCOLAIRE reste à faire")
                .isFalse();
        assertThat(ligne.passage2().presente()).isFalse();
        assertThat(ligne.passage1().faite()).isFalse();
        assertThat(ligne.resteAFaire())
                .as("le point n'a aucune nuit protocolaire : il en réclame une")
                .isEqualTo("Poser l'enregistreur avant le 31/07");
    }

    @Test
    @DisplayName("#2525 : un carré appartenant à un tiers sort du solde (ses points ne sont plus suivis)")
    void carre_de_tiers_exclu_du_solde() {
        SoldeSaison avant = service.soldePour(ID_USER, 2026);
        int pointsAvant = avant.pointsSuivis();
        assertThat(avant.lignes()).anyMatch(ligne -> ligne.numeroCarre().equals("640002"));

        // Le carré 640002 se révèle être celui d'un autre observateur (dérivé de site.observateur).
        carresDeTiers.marquer(idDuCarre("640002"));

        SoldeSaison apres = service.soldePour(ID_USER, 2026);
        assertThat(apres.lignes())
                .as("un carré de tiers n'engage aucune obligation de protocole")
                .noneMatch(ligne -> ligne.numeroCarre().equals("640002"));
        assertThat(apres.pointsSuivis())
                .as("640002 portait deux points (B1 et B2)")
                .isEqualTo(pointsAvant - 2);
    }

    @Test
    @DisplayName("#2355 : filtrer le solde par campagne ne garde que les points concernés")
    void solde_filtre_par_campagne() {
        // Le 1er passage de 640001/A1 relève d'un suivi ; les autres points n'ont aucune campagne.
        Campagne suivi = campagnes.creerCampagne("Suivi ENS", 2026, null);
        LigneSaison a1 = ligne(service.soldePour(ID_USER, 2026), "640001", "A1");
        campagnes.rattacherPassage(a1.passage1().idPassage(), suivi.id());

        SoldeSaison filtre = service.soldePour(ID_USER, 2026, "ens");

        assertThat(filtre.lignes())
                .as("fragment insensible à la casse ; seul le point rattaché est retenu")
                .extracting(LigneSaison::numeroCarre, LigneSaison::codePoint)
                .containsExactly(tuple("640001", "A1"));
        // Le point est montré ENTIER : son second passage, hors campagne, reste visible.
        assertThat(filtre.lignes().getFirst().passage2().presente()).isTrue();
    }

    @Test
    @DisplayName("#2355 : sans filtre, le solde reste complet ; un filtre inconnu ne retient rien")
    void solde_sans_filtre_et_filtre_inconnu() {
        int pointsSuivis = service.soldePour(ID_USER, 2026).pointsSuivis();

        assertThat(service.soldePour(ID_USER, 2026, null).pointsSuivis())
                .as("campagne nulle = pas de restriction")
                .isEqualTo(pointsSuivis);
        assertThat(service.soldePour(ID_USER, 2026, "  ").pointsSuivis())
                .as("saisie vide = pas de restriction")
                .isEqualTo(pointsSuivis);
        assertThat(service.soldePour(ID_USER, 2026, "Inconnue").lignes())
                .as("aucun point ne relève de cette campagne")
                .isEmpty();
    }

    @Test
    @DisplayName("la ventilation ferme : faits + à refaire + à réaliser = attendus, hors protocole à part")
    void ventilation_exhaustive() {
        long idOpportuniste = semer("640005", "E1", 1, 2026, "2026-07-04", StatutWorkflow.DEPOSE, Verdict.OK);
        opportunistes.marquer(idOpportuniste);

        SoldeSaison solde = service.soldePour(ID_USER, 2026);

        // L'invariant, et non des nombres appris par cœur : un décompte qui ne ferme pas laisse
        // l'observateur deviner où sont passés les manquants. La nuit opportuniste est DEHORS du total :
        // elle a eu lieu, mais ce n'est pas un passage attendu.
        assertThat(solde.passagesFaits() + solde.passagesARefaire() + solde.passagesARealiser())
                .as("les trois catégories couvrent exactement les passages attendus")
                .isEqualTo(solde.passagesAttendus());
        assertThat(solde.nuitsHorsProtocole())
                .as("la nuit de E1, comptée à côté")
                .isEqualTo(1);
        assertThat(solde.passagesAttendus())
                .as("E1 ajoute un point suivi, donc deux passages attendus de plus")
                .isEqualTo(14);
        assertThat(solde.passagesARefaire()).as("B2 seul est inexploitable").isEqualTo(1);
    }

    @Test
    @DisplayName("fenêtre du second passage dépassée : le reste à faire le dit, et le signalement se tait")
    void fenetre_depassee_apres_l_echeance() {
        // Horloge au 15 octobre : les deux fenêtres 2026 sont closes. Deux comportements en dépendent,
        // aucun n'était éprouvé (deux survivants PIT) : la phrase de `actionPoser` quand la fenêtre est
        // derrière nous, et le signalement qui ne concerne QUE les fenêtres encore ouvertes.
        SoldeSaison solde = serviceAu(LocalDate.of(2026, 10, 15)).soldePour(ID_USER, 2026);

        assertThat(ligne(solde, "640002", "B1").resteAFaire())
                .as("P1 déposé, P2 jamais posé et la fenêtre est close")
                .isEqualTo("Fenêtre du 2e passage dépassée (30/09)");
        assertThat(ligne(solde, "640004", "D1").resteAFaire())
                .as("aucune nuit : c'est la fenêtre du PREMIER passage qui est annoncée dépassée")
                .isEqualTo("Fenêtre du 1er passage dépassée (31/07)");

        assertThat(solde.joursAvantEcheanceSecondPassage()).isNegative();
        assertThat(solde.pointsSecondPassageEnAttente())
                .as("l'application signale une échéance qui approche, pas une échéance passée")
                .isZero();
    }

    /// Le même service, vu d'un autre jour. Les phrases du solde dépendent de la date courante ;
    /// l'horloge du montage est figée au 20/07/2026, dans la fenêtre du premier passage.
    private ServiceSoldeSaison serviceAu(LocalDate jour) {
        return new ServiceSoldeSaison(
                siteDao,
                injecteur.getInstance(PointDao.class),
                injecteur.getInstance(PassageDao.class),
                injecteur.getInstance(PointCommuneDao.class),
                opportunistes,
                carresDeTiers,
                Optional.of(campagnes),
                new HorlogeFigee(jour));
    }

    /// Identifiant local du carré `carre`.
    private long idDuCarre(String carre) {
        return siteDao.findByUtilisateur(ID_USER).stream()
                .filter(site -> site.numeroCarre().equals(carre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Carré absent : " + carre))
                .id();
    }

    @Test
    @DisplayName("#3313 : la commune du point remonte sur sa ligne, absente si non résolue")
    void commune_du_point_remonte() {
        // Une ligne porte UN point, donc une seule commune : le critère de l'ADR 2861 est satisfait.
        // Le test vérifie l'APPARIEMENT (tuple), pas la seule présence : une commune prise sur le point
        // voisin ferait trouver le mauvais lieu et passerait toute autre vérification.
        Long idPoint = injecteur.getInstance(PointDao.class).findBySite(idDuCarre("640001")).stream()
                .filter(point -> "A1".equals(point.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Point A1 absent du carré 640001."))
                .id();
        injecteur.getInstance(PointCommuneDao.class).definir(idPoint, new Commune("Ahetze", "64014"));

        assertThat(service.soldePour(ID_USER, 2026).lignes())
                .extracting(LigneSaison::codePoint, LigneSaison::commune)
                .contains(tuple("A1", "Ahetze"))
                .as("un point sans commune résolue la laisse absente : c'est un état normal")
                .contains(tuple("A2", null));
    }
}
