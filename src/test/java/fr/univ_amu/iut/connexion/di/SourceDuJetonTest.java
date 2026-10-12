package fr.univ_amu.iut.connexion.di;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La source du jeton que l'application lie : ce qu'un `401` lui fait oublier (#6151).
///
/// Le jeton ponctuel (`--token`, `VIGIECHIRO_TOKEN`) l'emporte sur la connexion enregistrée sans la
/// remplacer. Qu'il soit refusé ne dit rien du jeton enregistré, que l'application garde ; que le jeton
/// enregistré le soit le fait oublier, et l'application se retrouve déconnectée.
class SourceDuJetonTest {

    @TempDir
    Path workspace;

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.token");
    }

    private StockageConnexion stockage() {
        StockageConnexion stockage =
                new StockageConnexion(new Workspace(workspace), Horloge.figeeAu(LocalDate.of(2026, 10, 1)));
        stockage.enregistrer("jeton-enregistre", new ProfilVigieChiro("u-1", "chiro", "observateur"));
        return stockage;
    }

    @Test
    @DisplayName("#6151 : un 401 sur le jeton enregistré le fait oublier")
    void le_jeton_enregistre_refuse_est_oublie() {
        StockageConnexion stockage = stockage();
        FournisseurToken source = new ConnexionModule().fournirFournisseurToken(stockage);

        source.jetonRefuse(source.token().orElseThrow());

        assertThat(stockage.token()).isEmpty();
    }

    @Test
    @DisplayName("#6151 : un 401 sur un jeton ponctuel laisse la connexion enregistrée en place")
    void un_jeton_ponctuel_refuse_ne_touche_pas_la_connexion() {
        StockageConnexion stockage = stockage();
        System.setProperty("vigiechiro.token", "jeton-ponctuel");
        FournisseurToken source = new ConnexionModule().fournirFournisseurToken(stockage);
        assertThat(source.token()).as("le ponctuel l'emporte").contains("jeton-ponctuel");

        source.jetonRefuse("jeton-ponctuel");

        assertThat(stockage.token()).contains("jeton-enregistre");
    }
}
