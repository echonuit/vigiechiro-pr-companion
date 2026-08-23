package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.cli.commande.ListerSites;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/// Parité CLI ↔ IHM sur la **façon de lire** (ADR 0014) : « Mes sites » lit ses points par lot depuis
/// #4251 ; `lister-sites` doit en faire autant, sinon le chantier a déplacé le défaut au lieu de le
/// supprimer.
class RequetesDeListerSitesTest {

    @TempDir
    Path dossier;

    private static final class SourceComptee extends SourceDeDonnees {

        private final AtomicInteger connexions = new AtomicInteger();

        SourceComptee(Workspace workspace) {
            super(workspace);
        }

        @Override
        public Connection getConnection() {
            connexions.incrementAndGet();
            return super.getConnection();
        }
    }

    @Test
    @DisplayName("#4251 : quadrupler les carrés ne quadruple pas les requêtes de lister-sites")
    void les_requetes_ne_suivent_pas_les_carres() {
        int pourQuatre = requetesPour(4);
        int pourSeize = requetesPour(16);

        // ⚠️ Le garde compte des REQUÊTES, pas des millisecondes : la machine de mise au point portait un
        // banc filmé, et tout chronométrage y variait du simple au double.
        int surcout = pourSeize - pourQuatre;
        assertThat(surcout)
                .as("douze carrés de plus ne doivent pas coûter douze requêtes de plus")
                .isLessThan(12);
    }

    private int requetesPour(int carres) {
        Path espace = dossier.resolve("ws-" + carres);
        SourceComptee source = new SourceComptee(new Workspace(espace));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u", "S"));
        ServiceSites service = new ServiceSites(
                new SiteDao(source),
                new PointDao(source),
                new PassageDao(source),
                new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                new PointCommuneDao(source),
                () -> {});
        for (int i = 0; i < carres; i++) {
            Site site = service.creerSite(String.format("%06d", 640500 + i), "C" + i, Protocole.STANDARD, null, "u");
            service.ajouterPoint(site.id(), "A1", 43.5, 5.4, null);
            service.ajouterPoint(site.id(), "B2", 43.6, 5.5, null);
        }

        ListerSites commande = new ListerSites(service, () -> "u");
        CommandLine ligne = new CommandLine(commande);
        ligne.setOut(new PrintWriter(new StringWriter()));
        int avant = source.connexions.get();
        assertThat(ligne.execute()).isZero();
        return source.connexions.get() - avant;
    }
}
