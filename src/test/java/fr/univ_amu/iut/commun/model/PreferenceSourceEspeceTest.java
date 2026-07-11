package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Vérifie la persistance de la préférence de source (défaut GBIF, aller-retour vers Wikipédia) au-dessus
/// d'un [Reglages] réel sur base jetable.
class PreferenceSourceEspeceTest {

    @TempDir
    Path dossier;

    private PreferenceSourceEspece preference;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        preference = new PreferenceSourceEspece(new Reglages(new ReglagesDao(source)));
    }

    @Test
    @DisplayName("Par défaut (jamais écrite), la préférence est GBIF (pas Wikipédia)")
    void defaut_gbif() {
        assertThat(preference.prefereWikipedia()).isFalse();
    }

    @Test
    @DisplayName("Le choix Wikipédia est persisté puis relu")
    void choix_wikipedia_persiste() {
        preference.definirPrefereWikipedia(true);
        assertThat(preference.prefereWikipedia()).isTrue();

        preference.definirPrefereWikipedia(false);
        assertThat(preference.prefereWikipedia()).isFalse();
    }
}
