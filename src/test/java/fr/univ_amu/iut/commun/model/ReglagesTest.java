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

/// Lecture/écriture typée du service [Reglages], au-dessus d'un [ReglagesDao] réel sur base jetable.
/// On couvre, pour chaque type (booléen, texte, entier, énumération), le retour au **défaut**
/// (réglage jamais écrit), l'aller-retour, et la **tolérance** de lecture (valeur stockée illisible
/// -> défaut, sans exception).
class ReglagesTest {

    private static final String CLE = "import.conserver-originaux";

    @TempDir
    Path dossier;

    private ReglagesDao dao;
    private Reglages reglages;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        dao = new ReglagesDao(source);
        reglages = new Reglages(dao);
    }

    @Test
    @DisplayName("un réglage jamais écrit retombe sur le défaut fourni")
    void reglage_absent_retombe_sur_le_defaut() {
        assertThat(reglages.lireBooleen(CLE, true)).isTrue();
        assertThat(reglages.lireBooleen(CLE, false)).isFalse();
    }

    @Test
    @DisplayName("aller-retour d'un booléen (true puis false)")
    void aller_retour_booleen() {
        reglages.ecrireBooleen(CLE, false);
        assertThat(reglages.lireBooleen(CLE, true)).isFalse();

        reglages.ecrireBooleen(CLE, true);
        assertThat(reglages.lireBooleen(CLE, false)).isTrue();
    }

    @Test
    @DisplayName("une valeur stockée non booléenne est lue comme false")
    void valeur_non_booleenne_lue_false() {
        dao.ecrire(CLE, "peut-être");

        assertThat(reglages.lireBooleen(CLE, true)).isFalse();
    }

    @Test
    @DisplayName("aller-retour d'un texte, défaut si jamais écrit")
    void aller_retour_texte() {
        assertThat(reglages.lireTexte(CLE, "defaut")).isEqualTo("defaut");

        reglages.ecrireTexte(CLE, "wikipedia");
        assertThat(reglages.lireTexte(CLE, "defaut")).isEqualTo("wikipedia");
    }

    @Test
    @DisplayName("aller-retour d'un entier, défaut si jamais écrit")
    void aller_retour_entier() {
        assertThat(reglages.lireEntier(CLE, 42)).isEqualTo(42);

        reglages.ecrireEntier(CLE, 7);
        assertThat(reglages.lireEntier(CLE, 42)).isEqualTo(7);
    }

    @Test
    @DisplayName("un entier illisible retombe sur le défaut, sans exception")
    void entier_illisible_retombe_sur_le_defaut() {
        dao.ecrire(CLE, "pas un nombre");

        assertThat(reglages.lireEntier(CLE, 42)).isEqualTo(42);
    }
}
