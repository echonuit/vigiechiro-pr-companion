package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **Repartir d'une base vide** ([BaseNeuve], #1419) : le seul geste de la procédure de reset qui
/// n'existait pas encore.
class BaseNeuveTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("#1419 : la base est vidée et recréée — schéma à jour, référentiel semé, aucune donnée")
    void la_base_repart_vide_mais_utilisable() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        UtilisateurDao utilisateurs = new UtilisateurDao(source);
        utilisateurs.insert(new Utilisateur("u-1", "Testeur"));
        assertThat(utilisateurs.findAll()).hasSize(1);

        new BaseNeuve(source).repartirDeZero();

        assertThat(utilisateurs.findAll())
                .as("les données sont parties : c'est le but")
                .isEmpty();
        assertThat(utilisateurs.compter())
                .as("mais la base répond : le schéma a été rejoué, elle est utilisable telle quelle")
                .isZero();
        assertThat(new fr.univ_amu.iut.validation.model.dao.TaxonDao(source).findAll())
                .as("et le référentiel de taxons est resemé — une base neuve, pas une base cassée")
                .isNotEmpty();
    }

    @Test
    @DisplayName("#1419 : un filet est posé — la base d'avant reste relisible si le reset était une erreur")
    void un_filet_est_pose_avant_de_detruire() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));

        Path filet = new BaseNeuve(source).repartirDeZero();

        assertThat(filet).exists();
        // Le filet est une vraie base : `restaurer` sait la relire, et l'utilisateur y est encore.
        new ServiceSauvegarde(source, new fr.univ_amu.iut.commun.model.HorlogeFigee(java.time.LocalDate.now()))
                .restaurer(filet);
        assertThat(new UtilisateurDao(source).findAll())
                .as("la base d'avant se restaure : le reset est réversible tant que le filet est là")
                .extracting(Utilisateur::localId)
                .containsExactly("u-1");
    }

    @Test
    @DisplayName("#1419 : les journaux SQLite sont purgés — un WAL périmé rejouerait l'ancienne base"
            + " par-dessus la neuve")
    void les_journaux_sont_purges() throws Exception {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        Path base = new Workspace(dossier).cheminBaseDeDonnees();
        Path wal = base.resolveSibling(base.getFileName() + "-wal");
        Files.writeString(wal, "journal périmé");

        new BaseNeuve(source).repartirDeZero();

        assertThat(wal).doesNotExist();
    }
}
