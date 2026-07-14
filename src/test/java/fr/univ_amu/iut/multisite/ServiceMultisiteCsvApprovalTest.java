package fr.univ_amu.iut.multisite;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Path;
import java.time.LocalDate;
import org.approvaltests.Approvals;
import org.approvaltests.reporters.QuietReporter;
import org.approvaltests.reporters.UseReporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test « golden » de l'export CSV de la vue agrégée (P5-CA5), via ApprovalTests : le CSV produit
/// par [ServiceMultisite#exporterCsv] est comparé octet pour octet au fichier de référence
/// `.approved.txt`. Verrouille le déterminisme (ordre des colonnes, tri stable, cellule de
/// verdict vide pour un passage non vérifié). [QuietReporter] évite tout outil de diff
/// interactif en cas d'écart (compatible CI headless).
@UseReporter(QuietReporter.class)
class ServiceMultisiteCsvApprovalTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";

    @TempDir
    Path dossier;

    private ServiceMultisite service;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));

        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));

        Site siteA = siteDao.insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2025-01-01", ID_USER));
        Site siteB = siteDao.insert(new Site(null, "640381", "Forêt", Protocole.STANDARD, null, "2025-01-01", ID_USER));
        PointDEcoute pa1 = pointDao.insert(new PointDEcoute(null, "A1", null, null, null, siteA.id()));
        PointDEcoute pb2 = pointDao.insert(new PointDEcoute(null, "B2", null, null, null, siteA.id()));
        PointDEcoute pBa1 = pointDao.insert(new PointDEcoute(null, "A1", null, null, null, siteB.id()));

        semer(passageDao, 1, 2025, "2025-06-20", StatutWorkflow.TRANSFORME, Verdict.OK, pa1.id());
        semer(passageDao, 1, 2026, "2026-06-20", StatutWorkflow.VERIFIE, Verdict.DOUTEUX, pa1.id());
        semer(passageDao, 1, 2026, "2026-06-21", StatutWorkflow.IMPORTE, null, pb2.id());
        semer(passageDao, 1, 2026, "2026-06-22", StatutWorkflow.DEPOSE, Verdict.OK, pBa1.id());
        semer(passageDao, 2, 2026, "2026-08-20", StatutWorkflow.VERIFIE, Verdict.A_JETER, pBa1.id());

        service = new ServiceMultisite(
                siteDao,
                pointDao,
                passageDao,
                new ReleveTraitementDao(source),
                new ResultatsIdentificationDao(source),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)));
    }

    private void semer(
            PassageDao dao, int numero, int annee, String date, StatutWorkflow statut, Verdict verdict, Long idPoint) {
        dao.insert(new Passage(
                null,
                numero,
                annee,
                date,
                "21:00:00",
                "05:00:00",
                null,
                statut,
                verdict,
                null,
                null,
                null,
                idPoint,
                SERIE));
    }

    @Test
    @DisplayName("Export CSV déterministe de la vue agrégée (golden)")
    void exporte_la_vue_agregee_en_csv() {
        Approvals.verify(service.exporterCsv(service.listerPassages(ID_USER)));
    }
}
