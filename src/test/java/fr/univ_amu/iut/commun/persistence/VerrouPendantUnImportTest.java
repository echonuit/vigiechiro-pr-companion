package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Un import lit longtemps ; pendant ce temps, une écriture doit pouvoir aboutir (#4983).
///
/// ## Ce que le journal de Samuel montre
///
/// Le 14 août, l'écriture qui **relie la nuit à sa participation** a échoué sur `SQLITE_BUSY`. Le
/// `POST /participations` avait réussi onze secondes plus tôt : la participation existe donc sur la
/// plateforme, et le lien local qui la désigne n'a jamais été écrit. La conséquence se voit tard - un
/// import qui recréera une participation, ou un dépôt qui ne saura pas où déposer.
///
/// ## Ce que ce banc reproduit, et pourquoi il le reproduit ainsi
///
/// Un **lecteur qui tient sa transaction** pendant qu'un autre fil écrit. C'est la situation d'un
/// import : il lit beaucoup et longtemps, et la base n'était passée en aucun mode journal explicite -
/// donc en rollback, où un verrou partagé bloque tout écrivain pour la durée de la transaction.
///
/// Le `busy_timeout` de dix secondes ne change rien à cela : il fait attendre, il ne fait pas passer.
/// Le banc l'éprouve donc **sans laisser au délai le temps de courir**, faute de quoi il durerait dix
/// secondes pour rendre le même verdict.
class VerrouPendantUnImportTest {

    @TempDir
    private Path racine;

    @Test
    @DisplayName("#4983 : une écriture aboutit pendant qu'un lecteur tient sa transaction")
    void une_ecriture_aboutit_pendant_une_lecture_longue() throws Exception {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(racine.resolve("ws")));
        new MigrationSchema(source).migrer();

        CountDownLatch lectureOuverte = new CountDownLatch(1);
        CountDownLatch ecritureFinie = new CountDownLatch(1);
        Thread lecteur = new Thread(() -> {
            try (Connection cx = source.getConnection();
                    Statement st = cx.createStatement()) {
                cx.setAutoCommit(false);
                st.executeQuery("SELECT count(*) FROM vigiechiro_link").close();
                lectureOuverte.countDown();
                // La transaction reste OUVERTE : c'est tout l'objet du cas.
                ecritureFinie.await(30, TimeUnit.SECONDS);
                cx.rollback();
            } catch (Exception ignore) {
                lectureOuverte.countDown();
            }
        });
        lecteur.start();
        lectureOuverte.await(10, TimeUnit.SECONDS);

        LienVigieChiroDao dao = new LienVigieChiroDao(source);

        assertThatCode(() -> dao.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, "1", "obj-1", false)))
                .as("l'écriture qui relie la nuit à sa participation doit ABOUTIR pendant qu'un import"
                        + " lit. Sinon la participation existe sur la plateforme et rien ne la désigne"
                        + " localement, ce que personne ne voit avant le prochain import (#4983)")
                .doesNotThrowAnyException();

        ecritureFinie.countDown();
        lecteur.join(30_000);
    }

    @Test
    @DisplayName("#4983 : une base d'une version antérieure s'ouvre et se relit, sans rien demander")
    void une_base_anterieure_s_ouvre_sans_rien_demander() throws Exception {
        // Le passage en WAL convertit le fichier EN PLACE, à la première ouverture. Ce cas éprouve
        // qu'un utilisateur qui met à jour ne perde rien et n'ait rien à faire : sa base est en journal
        // rollback, et elle doit s'ouvrir, se relire, et accepter une écriture.
        Workspace workspace = new Workspace(racine.resolve("ancien"));
        SourceDeDonnees ancienne = new SourceDeDonnees(workspace);
        new MigrationSchema(ancienne).migrer();
        try (Connection cx = ancienne.getConnection();
                Statement st = cx.createStatement()) {
            st.execute("PRAGMA journal_mode = DELETE");
        }
        new LienVigieChiroDao(ancienne).upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, "7", "obj-7", false));

        // Rouverte par la version d'aujourd'hui, sur le MÊME fichier.
        SourceDeDonnees aujourdhui = new SourceDeDonnees(workspace);

        assertThat(new LienVigieChiroDao(aujourdhui).objectidPour(LienVigieChiro.ENTITE_SITE, "7"))
                .as("ce que l'ancienne version a écrit doit se relire tel quel : une conversion de mode"
                        + " journal ne doit rien coûter à l'utilisateur, ni rien lui demander")
                .isPresent();

        assertThatCode(() -> new LienVigieChiroDao(aujourdhui)
                        .upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, "8", "obj-8", false)))
                .as("et la base convertie doit accepter une écriture")
                .doesNotThrowAnyException();
    }
}
