package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.PreferenceSourceEspece;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.OngletReglagesGeneral;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import java.nio.file.Path;
import javafx.scene.control.CheckBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;

/// La **source des fiches espèces** se règle dans l'onglet « Général », et sa valeur **survit** à la
/// session (clé [PreferenceSourceEspece#CLE]).
///
/// Ce test remplace `SynchronisationReglagesTest` (#928), qui vérifiait que l'item ☰ et la case de
/// l'onglet restaient synchronisés. L'entrée du menu a été retirée (#1375) parce qu'elle faisait
/// doublon : il ne reste qu'une surface, donc plus rien à synchroniser. Ce qui **méritait** de
/// survivre, c'est l'autre moitié de la promesse : « le réglage est mémorisé d'une session à
/// l'autre », et elle est ici testée plus franchement qu'avant - en rebâtissant le formulaire, ce que
/// l'ancien test ne faisait pas.
@ExtendWith(ApplicationExtension.class)
class PreferenceSourceEspeceReglagesTest {

    @TempDir
    Path dossier;

    private Reglages reglages;
    private ReglagesReactifs reactifs;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        reglages = new Reglages(new ReglagesDao(source));
        reactifs = new ReglagesReactifs(reglages);
    }

    /// La case de l'onglet « Général », bâtie comme par l'écran Réglages.
    ///
    /// Recherche par TYPE plutôt que par position : depuis #2085 chaque réglage est enveloppé dans sa
    /// ligne (contrôle + aide visible), et un test qui compte les enfants casse au prochain habillage.
    private CheckBox caseDeLOnglet() {
        return (CheckBox) ControleursReglages.formulaire(new OngletReglagesGeneral(), reactifs)
                .lookup(".check-box");
    }

    @Test
    @DisplayName("cocher la case persiste le choix")
    void cocherPersisteLeChoix() {
        CheckBox caseOnglet = caseDeLOnglet();
        assertThat(caseOnglet.isSelected()).isFalse();

        caseOnglet.setSelected(true);

        assertThat(reglages.lireBooleen(PreferenceSourceEspece.CLE, false)).isTrue();
    }

    @Test
    @DisplayName("#1375 : le choix mémorisé se retrouve dans un formulaire rebâti")
    void choixMemoriseSeRetrouveDansUnFormulaireRebati() {
        caseDeLOnglet().setSelected(true);

        // Un formulaire tout neuf, comme à la réouverture de l'écran : c'est la promesse « mémorisé
        // d'une session à l'autre » que la fiche Réglages fait à l'utilisateur.
        assertThat(caseDeLOnglet().isSelected()).isTrue();
    }

    @Test
    @DisplayName("décocher revient au défaut, GBIF")
    void decocherRevientAuDefaut() {
        CheckBox caseOnglet = caseDeLOnglet();
        caseOnglet.setSelected(true);

        caseOnglet.setSelected(false);

        assertThat(reglages.lireBooleen(PreferenceSourceEspece.CLE, false)).isFalse();
        assertThat(caseDeLOnglet().isSelected()).isFalse();
    }
}
