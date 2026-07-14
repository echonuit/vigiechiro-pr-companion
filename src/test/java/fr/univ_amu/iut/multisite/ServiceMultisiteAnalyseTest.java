package fr.univ_amu.iut.multisite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// L'**état d'analyse** de la vue multi-sites (#1338), de bout en bout et sur une **vraie base** : c'est
/// ici, et pas dans le test unitaire de [EtatAnalyse], qu'on vérifie que le service croise réellement les
/// deux sources (le cache du traitement serveur et les résultats déjà importés).
///
/// Jeu de données : trois nuits **déposées** sur un même point, plus une nuit non déposée.
class ServiceMultisiteAnalyseTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final String RELEVE_LE = "2026-07-14T09:00:00Z";

    @TempDir
    Path dossier;

    private ServiceMultisite service;
    private ReleveTraitementDao releves;
    private ResultatsIdentificationDao resultats;

    /// Les nuits déposées, dans l'ordre de leur date : analyse finie, analyse en cours, analyse en échec.
    private Long finie;
    private Long enCours;
    private Long enEchec;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));

        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);
        releves = new ReleveTraitementDao(source);
        resultats = new ResultatsIdentificationDao(source);
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));

        Site site = siteDao.insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2025-01-01", ID_USER));
        PointDEcoute point = pointDao.insert(new PointDEcoute(null, "A1", null, null, null, site.id()));

        finie = semer(passageDao, 1, "2026-06-20", StatutWorkflow.DEPOSE, point.id());
        enCours = semer(passageDao, 2, "2026-06-21", StatutWorkflow.DEPOSE, point.id());
        enEchec = semer(passageDao, 3, "2026-06-22", StatutWorkflow.DEPOSE, point.id());
        semer(passageDao, 4, "2026-06-23", StatutWorkflow.VERIFIE, point.id());

        relever(finie, EtatTraitement.FINI);
        relever(enCours, EtatTraitement.EN_COURS);
        relever(enEchec, EtatTraitement.ERREUR);

        service = new ServiceMultisite(
                siteDao, pointDao, passageDao, releves, resultats, new HorlogeFigee(LocalDate.of(2026, 7, 14)));
    }

    @Test
    @DisplayName("#1338 : chaque nuit porte son état d'analyse, et la date à laquelle on l'a relevé")
    void chaque_nuit_porte_son_etat_et_la_date_du_releve() {
        List<LignePassage> lignes = service.listerPassages(ID_USER);

        assertThat(lignes)
                .extracting(LignePassage::idPassage, LignePassage::etatAnalyse, LignePassage::analyseReleveeLe)
                .containsExactly(
                        // Analyse finie, résultats pas encore récupérés : la seule nuit à traiter.
                        tuple(finie, EtatAnalyse.A_IMPORTER, RELEVE_LE),
                        tuple(enCours, EtatAnalyse.EN_COURS, RELEVE_LE),
                        tuple(enEchec, EtatAnalyse.EN_ECHEC, RELEVE_LE),
                        // Nuit non déposée : rien à suivre, et aucun relevé à dater.
                        tuple(lignes.get(3).idPassage(), EtatAnalyse.SANS_OBJET, null));
    }

    @Test
    @DisplayName("#1338 : la vue « Résultats à importer » ne retient que la nuit finie ET non importée")
    void vue_resultats_a_importer() {
        List<LignePassage> aImporter =
                service.listerPassages(ID_USER, FiltresMultisite.parEtatAnalyse(EtatAnalyse.A_IMPORTER));

        assertThat(aImporter).extracting(LignePassage::idPassage).containsExactly(finie);
    }

    @Test
    @DisplayName("#1338 : une fois les observations importées, la nuit SORT de « Résultats à importer »")
    void une_nuit_importee_sort_de_la_vue() {
        resultats.insert(new ResultatsIdentification(
                null, "/tmp/observations.csv", ResultatsIdentification.SOURCE_VIGIECHIRO, RELEVE_LE, finie));

        List<LignePassage> lignes = service.listerPassages(ID_USER);

        assertThat(lignes)
                .filteredOn(ligne -> ligne.idPassage().equals(finie))
                .extracting(LignePassage::etatAnalyse)
                .as("l'analyse reste FINIE côté serveur, mais il n'y a plus rien à importer :"
                        + " sans ce croisement, la vue proposerait indéfiniment une nuit déjà traitée")
                .containsExactly(EtatAnalyse.IMPORTEE);
        assertThat(service.listerPassages(ID_USER, FiltresMultisite.parEtatAnalyse(EtatAnalyse.A_IMPORTER)))
                .isEmpty();
    }

    @Test
    @DisplayName("#1338 : une nuit déposée jamais interrogée est « jamais relevé », pas « jamais lancée »")
    void nuit_jamais_interrogee() {
        releves.delete(enCours);

        List<LignePassage> lignes = service.listerPassages(ID_USER);

        assertThat(lignes)
                .filteredOn(ligne -> ligne.idPassage().equals(enCours))
                .extracting(LignePassage::etatAnalyse, LignePassage::analyseReleveeLe)
                .containsExactly(tuple(EtatAnalyse.JAMAIS_RELEVE, null));
    }

    private void relever(Long idPassage, EtatTraitement etat) {
        releves.enregistrer(new ReleveTraitement(
                idPassage,
                "participation-" + idPassage,
                new Traitement(etat, null, null, null, null, null),
                RELEVE_LE));
    }

    private Long semer(PassageDao dao, int numero, String date, StatutWorkflow statut, Long idPoint) {
        return dao.insert(new Passage(
                        null,
                        numero,
                        2026,
                        date,
                        "21:00:00",
                        "05:00:00",
                        null,
                        statut,
                        Verdict.OK,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
    }
}
