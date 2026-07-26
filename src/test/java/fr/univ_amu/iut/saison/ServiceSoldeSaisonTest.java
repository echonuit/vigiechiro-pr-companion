package fr.univ_amu.iut.saison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.saison.model.SoldeSaison;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
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

    private SourceDeDonnees source;
    private ServiceSoldeSaison service;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", dossier.toString());
        Injector injecteur = Guice.createInjector(
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
        SiteDao siteDao = injecteur.getInstance(SiteDao.class);
        PointDao pointDao = injecteur.getInstance(PointDao.class);
        Site d = siteDao.insert(
                new Site(null, "640004", "Site 640004", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        pointDao.insert(new PointDEcoute(null, "D1", null, null, null, d.id()));
        // Site RECHERCHE, à écarter.
        Site r = siteDao.insert(new Site(null, "640099", "Labo", Protocole.RECHERCHE, null, "2026-01-01", ID_USER));
        pointDao.insert(new PointDEcoute(null, "R1", null, null, null, r.id()));

        service = new ServiceSoldeSaison(
                siteDao,
                pointDao,
                injecteur.getInstance(PassageDao.class),
                new HorlogeFigee(LocalDate.of(2026, 7, 20)));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private void semer(
            String carre, String point, int numero, int annee, String date, StatutWorkflow statut, Verdict verdict) {
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(carre)
                .nomSite("Site " + carre)
                .point(point)
                .nuit(numero, annee, date)
                .statut(statut)
                .verdict(verdict)
                .semer();
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
}
