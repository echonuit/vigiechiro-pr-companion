package fr.univ_amu.iut.audit_perf;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAnalyseDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// « Espèces & observations » lit par **ensembles**, et doit continuer à le faire.
///
/// Cet écran est le **contre-exemple** sur lequel tout le chantier des lectures répétées s'appuie : à
/// volume égal, il chargeait quatre fois plus de lignes que « Mes sites » en soixante fois moins de
/// temps, ce qui a écarté SQLite et le volume comme causes et désigné la façon de lire.
///
/// Un contre-exemple qui se dégraderait en silence ferait perdre le repère. Ce garde compte donc des
/// **connexions** à deux tailles de jeu, et exige que le nombre ne suive pas celui des nuits.
///
/// ⚠️ Sa première version résolvait la source **par l'injecteur** : Guice en fabriquait une autre, le
/// compteur restait à zéro, et « zéro requête » se lit comme une excellente nouvelle. C'est l'assertion
/// de non-vacuité qui l'a dit. La source est désormais construite à la main.
class AuditGlobalDesLecturesTest {

    private static final AtomicInteger CONNEXIONS = new AtomicInteger();

    public static final class SourceComptee extends SourceDeDonnees {
        public SourceComptee(Workspace w) {
            super(w);
        }

        @Override
        public Connection getConnection() {
            CONNEXIONS.incrementAndGet();
            return super.getConnection();
        }
    }

    @Test
    @DisplayName("#4251 : quadrupler les nuits ne change pas le nombre de requêtes de l'écran d'analyse")
    void les_requetes_ne_suivent_pas_les_nuits() throws Exception {
        int pourQuatre = requetes(4);
        int pourSeize = requetes(16);

        // Non-vacuité d'abord : un compteur débranché rend zéro, et zéro se lirait comme un idéal.
        assertThat(pourQuatre)
                .as("le compteur ne voit AUCUNE requête : c'est lui qui est débranché")
                .isPositive();
        assertThat(pourSeize - pourQuatre)
                .as("douze nuits de plus ne doivent pas coûter douze requêtes de plus")
                .isLessThan(12);
    }

    private int requetes(int nuits) throws Exception {
        Path espace = Files.createTempDirectory("audit7-" + nuits);
        // ⚠️ La source est construite À LA MAIN, pas résolue par l'injecteur : Guice en fabrique une
        // autre, et le compteur restait à zéro - un « zéro requête » qui se lit comme une bonne
        // nouvelle. C'est l'assertion de non-vacuité qui l'a dit.
        SourceComptee source = new SourceComptee(new Workspace(espace));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u", "S"));
        for (int i = 0; i < nuits; i++) {
            var jeu = JeuDeDonneesPassage.dans(source)
                    .utilisateur("u")
                    .carre(String.format("%06d", 660000 + i))
                    .nomSite("C" + i)
                    .point("A1")
                    .semer();
            jeu.ajouterObservation("Pippip");
            jeu.ajouterObservation("Nyclei");
        }
        ServiceAnalyse service =
                new ServiceAnalyse(new ProjectionsAnalyseDao(source), new PassageOpportunisteDao(source)::tousLesIds);
        CONNEXIONS.set(0);
        assertThat(new AnalyseViewModel(service, "u").chargerObservations()).isNotNull();
        return CONNEXIONS.get();
    }
}
