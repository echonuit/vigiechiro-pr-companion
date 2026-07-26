package fr.univ_amu.iut.multisite;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.approvaltests.Approvals;
import org.approvaltests.reporters.QuietReporter;
import org.approvaltests.reporters.UseReporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test « golden » de l'export CSV de la vue agrégée (P5-CA5), via ApprovalTests : le CSV produit
/// par [ServiceMultisite#exporterCsv] est comparé octet pour octet au fichier de référence
/// `.approved.txt`. Verrouille le déterminisme (ordre des colonnes, tri stable, cellule de
/// verdict vide pour un passage non vérifié). [QuietReporter] évite tout outil de diff
/// interactif en cas d'écart (compatible CI headless).
///
/// Topologie semée par [JeuDeDonneesPassage] (nuits qui partagent site et point) ; DAO résolus par
/// injecteur - aucun semis manuel de passage ici.
@UseReporter(QuietReporter.class)
class ServiceMultisiteCsvApprovalTest {

    private static final String ID_USER = "u-1";

    @TempDir
    Path dossier;

    private ServiceMultisite service;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", dossier.toString());
        Injector injecteur = Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new AbstractModule() {
                    @Provides
                    ResultatsIdentificationDao fournirResultatsDao(SourceDeDonnees source) {
                        return new ResultatsIdentificationDao(source);
                    }
                });
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        semer(source, 1, 2025, "2025-06-20", StatutWorkflow.TRANSFORME, Verdict.OK, "640380", "Étang", "A1");
        semer(source, 1, 2026, "2026-06-20", StatutWorkflow.VERIFIE, Verdict.DOUTEUX, "640380", "Étang", "A1");
        semer(source, 1, 2026, "2026-06-21", StatutWorkflow.IMPORTE, null, "640380", "Étang", "B2");
        semer(source, 1, 2026, "2026-06-22", StatutWorkflow.DEPOSE, Verdict.OK, "640381", "Forêt", "A1");
        semer(source, 2, 2026, "2026-08-20", StatutWorkflow.VERIFIE, Verdict.A_JETER, "640381", "Forêt", "A1");

        service = new ServiceMultisite(
                injecteur.getInstance(SiteDao.class),
                injecteur.getInstance(PointDao.class),
                injecteur.getInstance(PassageDao.class),
                injecteur.getInstance(ReleveTraitementDao.class),
                injecteur.getInstance(ResultatsIdentificationDao.class),
                Optional.empty(),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private static void semer(
            SourceDeDonnees source,
            int numero,
            int annee,
            String date,
            StatutWorkflow statut,
            Verdict verdict,
            String carre,
            String nomSite,
            String point) {
        JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(carre)
                .nomSite(nomSite)
                .point(point)
                .nuit(numero, annee, date)
                .statut(statut)
                .verdict(verdict)
                .semer();
    }

    @Test
    @DisplayName("Export CSV déterministe de la vue agrégée (golden)")
    void exporte_la_vue_agregee_en_csv() {
        Approvals.verify(service.exporterCsv(service.listerPassages(ID_USER)));
    }
}
