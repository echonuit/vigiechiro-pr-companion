package fr.univ_amu.iut.sites.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La mémoire des points **publiés** vers Vigie-Chiro (#3458), sur base réelle jetable.
class PointPublieDaoTest {

    @TempDir
    Path dossier;

    private ServiceSites service;
    private PointPublieDao publies;
    private Site site;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        PointDao pointDao = new PointDao(source);
        service = new ServiceSites(
                new SiteDao(source),
                pointDao,
                new PassageDao(source),
                new HorlogeFigee(LocalDate.of(2026, 6, 1)),
                new PointCommuneDao(source),
                () -> {});
        publies = new PointPublieDao(source);
        site = service.creerSite("130711", "Mon carré", Protocole.STANDARD, null, "u-1");
    }

    @Test
    @DisplayName("#3458 : un point n'est publié que si on l'a marqué, et le marquage est idempotent")
    void le_marquage_est_idempotent() {
        PointDEcoute point = service.ajouterPoint(site.id(), "Z42", 43.52, 5.46, null);

        assertThat(publies.estPublie(point.id()))
                .as("un point neuf n'est pas sur la plateforme tant qu'on ne l'y a pas poussé")
                .isFalse();

        publies.marquer(point.id());
        publies.marquer(point.id());

        assertThat(publies.estPublie(point.id())).isTrue();
        assertThat(publies.parSite(site.id()))
                .as("republier ne doit pas dupliquer la ligne : le geste est rejouable")
                .containsExactly(point.id());
    }

    @Test
    @DisplayName("#3458 : supprimer un point emporte son marquage")
    void la_suppression_emporte_le_marquage() {
        PointDEcoute point = service.ajouterPoint(site.id(), "Z42", 43.52, 5.46, null);
        publies.marquer(point.id());

        service.supprimerPoint(point.id());

        // Sans la cascade, l'identifiant resterait dans la table et un point RECREE sous le même id
        // hériterait d'un « publié » qui ne le concerne pas.
        assertThat(publies.estPublie(point.id())).isFalse();
    }

    @Test
    @DisplayName("#3458 : la lecture par site ne rend que les points de CE site")
    void la_lecture_par_site_ne_deborde_pas() {
        PointDEcoute ici = service.ajouterPoint(site.id(), "Z42", 43.52, 5.46, null);
        Site autre = service.creerSite("999999", "Ailleurs", Protocole.STANDARD, null, "u-1");
        PointDEcoute la = service.ajouterPoint(autre.id(), "Z1", 44.0, 6.0, null);
        publies.marquer(ici.id());
        publies.marquer(la.id());

        assertThat(publies.parSite(site.id())).containsExactly(ici.id());
    }
}
