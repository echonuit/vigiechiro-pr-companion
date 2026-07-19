package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.di.LotModule;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.lot.model.dao.DepotPlanDao;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Filet de câblage Guice de la feature `lot` : [LotModule] n'étant pas (encore) installé
/// dans `RacineInjecteur` (fichier gelé pour cette tâche), on l'exerce ici au-dessus d'un
/// petit module local qui fournit les feuilles dont il dépend (DAO des features
/// `sites`/`passage` + [Horloge]). On vérifie ainsi que les méthodes `@Provides` de
/// `LotModule` assemblent correctement [VerificationCoherence] et [ServiceLot].
class LotModuleTest {

    @TempDir
    Path workspaceJetable;

    @Test
    @DisplayName("LotModule assemble VerificationCoherence et ServiceLot via Guice")
    void lot_module_resout_les_services() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(workspaceJetable));
        new MigrationSchema(source).migrer();

        Injector injecteur = Guice.createInjector(new LotModule(), new AbstractModule() {
            @Override
            protected void configure() {
                bind(SiteDao.class).toInstance(new SiteDao(source));
                bind(PointDao.class).toInstance(new PointDao(source));
                bind(SessionDao.class).toInstance(new SessionDao(source));
                bind(EnregistrementOriginalDao.class).toInstance(new EnregistrementOriginalDao(source));
                bind(SequenceDao.class).toInstance(new SequenceDao(source));
                bind(JournalDuCapteurDao.class).toInstance(new JournalDuCapteurDao(source));
                bind(ReleveClimatiqueDao.class).toInstance(new ReleveClimatiqueDao(source));
                bind(PassageDao.class).toInstance(new PassageDao(source));
                bind(DepotUniteDao.class).toInstance(new DepotUniteDao(source));
                bind(DepotPlanDao.class).toInstance(new DepotPlanDao(source));
                bind(MoteurWorkflowPassage.class).toInstance(new MoteurWorkflowPassage());
                bind(Horloge.class).toInstance(new HorlogeFigee(LocalDate.of(2026, 5, 31)));
                // ServiceLot lit désormais le plafond d’archive dans les réglages (#1047).
                bind(Reglages.class).toInstance(new Reglages(new ReglagesDao(source)));
            }
        });

        assertThat(injecteur.getInstance(VerificationCoherence.class)).isNotNull();
        assertThat(injecteur.getInstance(ServiceLot.class)).isNotNull();
    }
}
