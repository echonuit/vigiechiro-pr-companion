package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;

/// Rendu de l'onglet « Fonctionnalités » (#1057) par le socle : la case d'une feature désactivable
/// écrit bien le flag persisté `feature.<id>.active`, et le bandeau « effet au prochain démarrage »
/// (échappatoire [OngletReglagesPersonnalise]) est rendu sous les cases.
@ExtendWith(ApplicationExtension.class)
class OngletReglagesFonctionnalitesRenduTest {

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
    @DisplayName("la case d'une feature désactivable persiste feature.<id>.active, et le bandeau est rendu")
    void case_persiste_le_flag_et_bandeau_present() {
        DescripteurReglage.Booleen toggle = new DescripteurReglage.Booleen(
                "feature.import-vigiechiro.active", "Import depuis VigieChiro", "Effet au prochain démarrage.", true);
        OngletReglagesFonctionnalites onglet = new OngletReglagesFonctionnalites(List.of(toggle));

        VBox formulaire = (VBox) ControleursReglages.formulaire(onglet, reactifs);

        // 1re ligne : la case de la feature (cochée = active par défaut).
        CheckBox caseFeature = (CheckBox) formulaire.getChildren().get(0);
        assertThat(caseFeature.getText()).isEqualTo("Import depuis VigieChiro");
        assertThat(caseFeature.isSelected()).isTrue();

        // Décocher -> persiste feature.import-vigiechiro.active = false.
        caseFeature.setSelected(false);
        assertThat(reglages.lireBooleen("feature.import-vigiechiro.active", true))
                .isFalse();

        // Dernier nœud : le bandeau « effet au prochain démarrage » (échappatoire personnalisée).
        Label bandeau =
                (Label) formulaire.getChildren().get(formulaire.getChildren().size() - 1);
        assertThat(bandeau.getText()).contains("prochain démarrage");
    }
}
