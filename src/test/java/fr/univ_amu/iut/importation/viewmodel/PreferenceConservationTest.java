package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.model.ReglageConservationOriginaux;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La préférence « conserver les originaux » suit le réglage **courant** (#3471).
///
/// ## Le défaut
///
/// La case vivait autrefois sur l'écran d'import, liée bidirectionnellement à cette préférence, qui la
/// mémorisait au lancement. Le contrat était cohérent. La case a **déménagé dans Réglages ▸ Import**,
/// qui écrit la clé directement, et la préférence est restée un instantané pris au démarrage.
///
/// Retour utilisateur de Samuel : « j'avais coché l'option "conserver les originaux" dans l'onglet
/// paramètre, mais la copie ne s'est à priori pas faite ».
///
/// Le réglage est un **filet de sécurité** : garder les WAV bruts pour ré-analyser plus tard. Un filet
/// qu'on croit tendu et qui ne l'est pas vaut moins que pas de filet, parce qu'on compte dessus.
class PreferenceConservationTest {

    @TempDir
    Path dossier;

    private Reglages reglages;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        reglages = new Reglages(new ReglagesDao(source));
    }

    @Test
    @DisplayName("#3471 : un réglage modifié APRÈS la construction est pris en compte")
    void reglageModifieApresConstructionEstPrisEnCompte() {
        reglages.ecrireBooleen(ReglageConservationOriginaux.CLE, false);
        PreferenceConservation preference = new PreferenceConservation(reglages);

        // L'utilisateur ouvre Réglages ▸ Import et coche la case : l'onglet écrit la clé directement.
        reglages.ecrireBooleen(ReglageConservationOriginaux.CLE, true);

        assertThat(preference.valeur())
                .as("l'import doit lire le réglage au moment de s'en servir, comme le fait la CLI")
                .isTrue();
    }

    @Test
    @DisplayName("#3471 : et dans l'autre sens, décocher est suivi aussi")
    void decocherEstSuiviAussi() {
        reglages.ecrireBooleen(ReglageConservationOriginaux.CLE, true);
        PreferenceConservation preference = new PreferenceConservation(reglages);

        reglages.ecrireBooleen(ReglageConservationOriginaux.CLE, false);

        // Le sens inverse compte autant : une préférence qui ne suivrait que l'activation laisserait
        // copier plusieurs Go par nuit à quelqu'un qui vient de dire ne plus le vouloir.
        assertThat(preference.valeur()).isFalse();
    }

    @Test
    @DisplayName("Clé absente : le défaut du produit s'applique")
    void cleAbsenteDonneLeDefaut() {
        PreferenceConservation preference = new PreferenceConservation(reglages);

        assertThat(preference.valeur()).isEqualTo(ReglageConservationOriginaux.DEFAUT);
    }
}
