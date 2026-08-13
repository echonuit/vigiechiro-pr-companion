package fr.univ_amu.iut.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.RevisionDonnees;
import fr.univ_amu.iut.diagnostic.di.DiagnosticModule;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Filet de câblage Guice de la feature `diagnostic` : [DiagnosticModule] n'étant pas installé
/// dans `RacineInjecteur` (fichier gelé pour cette tâche), on l'exerce ici au-dessus d'un petit
/// module local fournissant les feuilles dont il dépend (DAO des features `passage`/`sites` +
/// [Horloge]). On vérifie que la méthode `@Provides` de `DiagnosticModule` assemble correctement
/// [ServiceDiagnostic].
class DiagnosticModuleTest {

    @TempDir
    Path workspaceJetable;

    @Test
    @DisplayName("DiagnosticModule assemble ServiceDiagnostic via Guice")
    void diagnostic_module_resout_le_service() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(workspaceJetable));
        new MigrationSchema(source).migrer();

        Injector injecteur = Guice.createInjector(new DiagnosticModule(), new AbstractModule() {

            /// Le `Navigateur` du socle abonne les écrans qui déclarent `SuitLaRevision` (ADR 3651) :
            /// il réclame donc la révision, que cet injecteur partiel doit lier comme les autres
            /// pièces du socle. Exécution en ligne : le test n'a pas de fil JavaFX à attendre.
            @Provides
            @Singleton
            RevisionDonnees revision() {
                return new RevisionDonnees(Runnable::run);
            }

            @Override
            protected void configure() {
                bind(PassageDao.class).toInstance(new PassageDao(source));
                bind(SessionDao.class).toInstance(new SessionDao(source));
                bind(JournalDuCapteurDao.class).toInstance(new JournalDuCapteurDao(source));
                bind(ReleveClimatiqueDao.class).toInstance(new ReleveClimatiqueDao(source));
                bind(PointDao.class).toInstance(new PointDao(source));
                bind(Horloge.class).toInstance(new HorlogeFigee(LocalDate.of(2026, 5, 31)));
            }
        });

        assertThat(injecteur.getInstance(ServiceDiagnostic.class)).isNotNull();
    }
}
