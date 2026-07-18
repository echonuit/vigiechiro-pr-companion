package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Noyau de **structure** d'un passage archivé (#1662), sur une base jetable : `creer` crée le passage
/// (déposé) + la session archivée + les lignes de séquences (sans fichier) + rapatrie enregistreur / météo
/// / micro, et émet ses deux points de progression. Aucune observation ni audio (autres coutures).
class CreationPassageArchiveTest {

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private CreationPassageArchive creation;
    private Long idPoint;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "130711", "Test", Protocole.STANDARD, null, "2026-05-31", "u-1"));
        PointDEcoute point = new PointDao(source).insert(new PointDEcoute(null, "Z41", 43.5, 5.4, null, site.id()));
        idPoint = point.id();
        creation =
                new CreationPassageArchive(source, new Workspace(dossier), new HorlogeFigee(LocalDate.of(2026, 7, 17)));
    }

    private static ParticipationDetail detailComplet() {
        return new ParticipationDetail(
                "p-1",
                "etag",
                "Z41",
                "2026-07-03T22:00:00+02:00",
                "2026-07-04T06:30:00+02:00",
                new MeteoDepot("FAIBLE", "0-25"),
                Map.of("detecteur_enregistreur_numero_serie", "1997632", "micro0_type", "ICS"),
                Traitement.absent());
    }

    @Test
    @DisplayName(
            "creer : session archivée + une séquence par fichier + enregistreur/micro rapatriés, progression émise")
    void creer_squelette_complet() {
        LocalDateTime debut = LocalDateTime.of(2026, 7, 3, 21, 0);
        LocalDateTime fin = LocalDateTime.of(2026, 7, 4, 5, 0);
        Prefixe prefixe = new Prefixe("130711", 2026, 1, "Z41");
        List<Progression> points = new ArrayList<>();

        CreationPassageArchive.PassageArchive r = creation.creer(
                idPoint, 1, debut, fin, prefixe, detailComplet(), List.of("seqA_000", "seqB_000"), points::add);

        // Le statut (déposé) et la météo du passage lui-même sont vérifiés de bout en bout par
        // ServiceReconstructionPassagesTest (qui délègue à ce noyau) ; ici on cible les pièces de structure.
        assertThat(r.nbSequences()).isEqualTo(2);
        assertThat(new EnregistreurDao(source).findById("1997632"))
                .as("enregistreur créé depuis le n° de série rapatrié (clé canonique), pas « INCONNU »")
                .isPresent();
        SessionDEnregistrement session =
                new SessionDao(source).trouverParPassage(r.idPassage()).orElseThrow();
        assertThat(session.archivee())
                .as("le passage naît archivé (aucun audio)")
                .isTrue();
        assertThat(new SequenceDao(source).findBySession(session.id()))
                .as("une ligne de séquence par fichier distant, sans fichier sur disque")
                .hasSize(2);
        assertThat(new MaterielMicroDao(source).pour(r.idPassage()).typeMicro()).isEqualTo("ICS");
        assertThat(points)
                .extracting(Progression::libelle)
                .anyMatch(l -> l.contains("Création du passage"))
                .anyMatch(l -> l.contains("Création des séquences"));
    }

    @Test
    @DisplayName("creerAvecIdentite (#1814) : passage archivé PORTANT enregistreur/micro réels, mais 0 séquence")
    void creer_avec_identite_sans_sequence() {
        LocalDateTime debut = LocalDateTime.of(2026, 7, 3, 21, 0);
        LocalDateTime fin = LocalDateTime.of(2026, 7, 4, 5, 0);
        Prefixe prefixe = new Prefixe("130711", 2026, 1, "Z41");

        CreationPassageArchive.PassageArchive r =
                creation.creerAvecIdentite(idPoint, 1, debut, fin, prefixe, detailComplet());

        assertThat(r.nbSequences())
                .as("un passage avec identité reste un squelette : aucune séquence")
                .isZero();
        assertThat(new EnregistreurDao(source).findById("1997632"))
                .as("enregistreur réel rapatrié depuis le détail (clé canonique), pas « INCONNU »")
                .isPresent();
        assertThat(new MaterielMicroDao(source).pour(r.idPassage()).typeMicro())
                .as("le micro est rapatrié dès la synchro")
                .isEqualTo("ICS");
        SessionDEnregistrement session =
                new SessionDao(source).trouverParPassage(r.idPassage()).orElseThrow();
        assertThat(session.archivee()).as("la nuit naît archivée").isTrue();
        assertThat(new SequenceDao(source).findBySession(session.id()))
                .as("0 séquence : l'audio et les observations viennent à la reconstruction")
                .isEmpty();
    }
}
