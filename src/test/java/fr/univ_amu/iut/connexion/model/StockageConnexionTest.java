package fr.univ_amu.iut.connexion.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du stockage local de la connexion (#727) : round-trip token + identité dans
/// `connexion.json`, péremption à 14 jours, tolérance (fichier absent) et effacement.
class StockageConnexionTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 1, 1);
    private static final ProfilVigieChiro PROFIL = new ProfilVigieChiro("6a1b", "Sébastien", "Observateur");

    @TempDir
    Path workspace;

    private StockageConnexion stockage(LocalDate jour) {
        return new StockageConnexion(new Workspace(workspace), Horloge.figeeAu(jour));
    }

    @Test
    @DisplayName("enregistrer un token + identité : token, profil et estConnecte remontés")
    void round_trip() {
        StockageConnexion stockage = stockage(JOUR);

        stockage.enregistrer("TOK123", PROFIL);

        assertThat(stockage.token()).contains("TOK123");
        assertThat(stockage.profil()).contains(PROFIL);
        assertThat(stockage.estConnecte()).isTrue();
    }

    @Test
    @DisplayName("token seul (identité pas encore récupérée) : token présent, profil vide")
    void token_sans_profil() {
        StockageConnexion stockage = stockage(JOUR);

        stockage.enregistrer("TOK123", null);

        assertThat(stockage.token()).contains("TOK123");
        assertThat(stockage.profil()).isEmpty();
        assertThat(stockage.estConnecte()).isTrue();
    }

    @Test
    @DisplayName("péremption : valide jusqu'à J+14 inclus, périmé à J+15 (token et profil vides)")
    void peremption_a_14_jours() {
        stockage(JOUR).enregistrer("TOK123", PROFIL);

        assertThat(stockage(JOUR.plusDays(14)).token()).as("J+14").contains("TOK123");
        assertThat(stockage(JOUR.plusDays(15)).token()).as("J+15").isEmpty();
        assertThat(stockage(JOUR.plusDays(15)).profil()).as("J+15").isEmpty();
        assertThat(stockage(JOUR.plusDays(15)).estConnecte()).isFalse();
    }

    @Test
    @DisplayName("aucune connexion / après effacement : tout est vide (jamais d'exception)")
    void absent_ou_efface() {
        StockageConnexion stockage = stockage(JOUR);
        assertThat(stockage.token()).isEmpty();
        assertThat(stockage.estConnecte()).isFalse();

        stockage.enregistrer("TOK123", PROFIL);
        stockage.effacer();
        assertThat(stockage.token()).isEmpty();
        assertThat(stockage.profil()).isEmpty();

        stockage.effacer(); // idempotent
    }

    @Test
    @DisplayName("le fichier de connexion est restreint au propriétaire (POSIX 600)")
    void permissions_restreintes() throws IOException {
        assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "système de fichiers non POSIX : permissions non applicables");
        StockageConnexion stockage = stockage(JOUR);

        stockage.enregistrer("TOK123", PROFIL);

        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(workspace.resolve("connexion.json"));
        assertThat(perms).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }
}
