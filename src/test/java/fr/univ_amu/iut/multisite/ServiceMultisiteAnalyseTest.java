package fr.univ_amu.iut.multisite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.multisite.di.MultisiteModule;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// L'**état d'analyse** de la vue multi-sites (#1338), de bout en bout et sur une **vraie base** : c'est
/// ici qu'on prouve que le service croise réellement le cache du traitement serveur et les résultats
/// d'identification en base (les cas de bord — sans objet, filtre — sont couverts par [EtatAnalyseTest]
/// (unité), `ServiceMultisiteCsvApprovalTest` (golden multi-nuits) et `MultisiteVueIntegrationTest`).
///
/// La nuit est semée par [JeuDeDonneesPassage] (pas de topologie à la main) et le service est résolu par
/// un injecteur Guice — d'où l'absence totale de semis manuel de passage dans ce fichier. Le seul DAO
/// fourni localement est [ResultatsIdentificationDao] (feature `validation`), que l'injecteur partiel de
/// `multisite` ne connaît pas ; c'est un simple objet sur la [SourceDeDonnees] déjà liée.
class ServiceMultisiteAnalyseTest {

    private static final String ID_USER = "u-1";
    private static final String RELEVE_LE = "2026-07-14T09:00:00Z";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ServiceMultisite service;
    private ReleveTraitementDao releves;
    private JeuDeDonneesPassage nuit;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", dossier.toString());
        Injector injecteur = Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new MultisiteModule(),
                new AbstractModule() {
                    @Provides
                    ResultatsIdentificationDao fournirResultatsIdentificationDao(SourceDeDonnees source) {
                        return new ResultatsIdentificationDao(source);
                    }
                });
        source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        releves = injecteur.getInstance(ReleveTraitementDao.class);
        service = injecteur.getInstance(ServiceMultisite.class);

        // Une nuit **déposée** : c'est la seule dont l'analyse serveur existe, et donc la seule sur
        // laquelle l'état d'analyse a un sens.
        nuit = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("640380")
                .point("A1")
                .statut(StatutWorkflow.DEPOSE)
                .semer();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private EtatAnalyse etatDeLaNuit() {
        return service.listerPassages(ID_USER).stream()
                .filter(ligne -> ligne.idPassage() == nuit.idPassage())
                .map(LignePassage::etatAnalyse)
                .findFirst()
                .orElseThrow();
    }

    private void relever(EtatTraitement etat) {
        releves.enregistrer(new ReleveTraitement(
                nuit.idPassage(),
                "part-" + nuit.idPassage(),
                new Traitement(etat, null, null, null, null, null),
                RELEVE_LE));
    }

    @Test
    @DisplayName("#1338 : nuit déposée jamais interrogée → « jamais relevé » (on ne SAIT pas, on le dit)")
    void jamais_releve() {
        assertThat(etatDeLaNuit()).isEqualTo(EtatAnalyse.JAMAIS_RELEVE);
    }

    @Test
    @DisplayName("#1338 : analyse FINIE sans résultats = à importer ; une fois importés = importée")
    void croise_le_cache_et_les_resultats() {
        relever(EtatTraitement.FINI);
        assertThat(etatDeLaNuit())
                .as("analyse finie, résultats pas encore là : la nuit est à importer")
                .isEqualTo(EtatAnalyse.A_IMPORTER);
        assertThat(service.listerPassages(ID_USER, FiltresMultisite.parEtatAnalyse(EtatAnalyse.A_IMPORTER)))
                .extracting(LignePassage::idPassage)
                .containsExactly(nuit.idPassage());

        // Les observations arrivent en base (identification_results) : la nuit n'est plus « à importer ».
        nuit.ajouterResultats();

        assertThat(etatDeLaNuit())
                .as("l'analyse reste finie côté serveur, mais il n'y a plus rien à importer")
                .isEqualTo(EtatAnalyse.IMPORTEE);
        assertThat(service.listerPassages(ID_USER, FiltresMultisite.parEtatAnalyse(EtatAnalyse.A_IMPORTER)))
                .isEmpty();
    }

    @Test
    @DisplayName("#1338 : la ligne porte la date du relevé (état observé, pas une vérité)")
    void porte_la_date_du_releve() {
        relever(EtatTraitement.EN_COURS);

        assertThat(service.listerPassages(ID_USER))
                .filteredOn(ligne -> ligne.idPassage() == nuit.idPassage())
                .extracting(LignePassage::etatAnalyse, LignePassage::analyseReleveeLe)
                .containsExactly(tuple(EtatAnalyse.EN_COURS, RELEVE_LE));
    }
}
