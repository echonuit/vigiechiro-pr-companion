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
import javafx.scene.control.CheckMenuItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;

/// Synchro **live** entre l'item ☰ « source des fiches espèces » et la case de l'onglet « Général »
/// de l'écran Réglages (#928) : tous deux liés à la MÊME Property réactive (clé
/// [PreferenceSourceEspece#CLE]), une bascule d'un côté se reflète de l'autre et persiste. Reproduit
/// fidèlement la liaison de l'item ☰ ([ActionSourceEspece]) et le contrôle bâti par
/// [ControleursReglages].
@ExtendWith(ApplicationExtension.class)
class SynchronisationReglagesTest {

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

    @Test
    @DisplayName("basculer l'item ☰ coche la case de l'onglet (et persiste), et réciproquement")
    void menu_et_onglet_synchronises() {
        // Item ☰ : lié comme dans MenuOutils.
        CheckMenuItem item = new CheckMenuItem();
        item.selectedProperty().bindBidirectional(reactifs.proprieteBooleen(PreferenceSourceEspece.CLE, false));

        // Case de l'onglet « Général » : bâtie comme par l'écran Réglages (même réactif, même clé).
        // Recherche par TYPE plutôt que par position : depuis #2085 chaque réglage est enveloppé dans sa
        // ligne (contrôle + aide visible), et un test qui compte les enfants casse au prochain habillage.
        CheckBox caseOnglet = (CheckBox) ControleursReglages.formulaire(new OngletReglagesGeneral(), reactifs)
                .lookup(".check-box");

        assertThat(item.isSelected()).isFalse();
        assertThat(caseOnglet.isSelected()).isFalse();

        // Menu -> onglet + persistance.
        item.setSelected(true);
        assertThat(caseOnglet.isSelected()).isTrue();
        assertThat(reglages.lireBooleen(PreferenceSourceEspece.CLE, false)).isTrue();

        // Onglet -> menu.
        caseOnglet.setSelected(false);
        assertThat(item.isSelected()).isFalse();
    }
}
