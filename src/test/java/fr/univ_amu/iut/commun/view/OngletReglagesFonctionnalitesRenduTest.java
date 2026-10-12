package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
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
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
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
                "feature.import-vigiechiro.active", "Import depuis Vigie-Chiro", "Effet au prochain démarrage.", true);
        OngletReglagesFonctionnalites onglet = new OngletReglagesFonctionnalites(List.of(toggle));

        VBox formulaire = (VBox) ControleursReglages.formulaire(onglet, reactifs);

        // 1re ligne : la case de la feature (cochée = active par défaut).
        // Par type, pas par position : chaque réglage est enveloppé dans sa ligne depuis #2085.
        CheckBox caseFeature = (CheckBox) formulaire.lookup(".check-box");
        assertThat(caseFeature.getText()).isEqualTo("Import depuis Vigie-Chiro");
        assertThat(caseFeature.isSelected()).isTrue();

        // Décocher -> persiste feature.import-vigiechiro.active = false.
        caseFeature.setSelected(false);
        assertThat(reglages.lireBooleen("feature.import-vigiechiro.active", true))
                .isFalse();

        // L'avis « effet au prochain démarrage » (composant partagé AvisRedemarrage, #2258) est rendu
        // sous les cases. Sans bouton « Quitter » ici : ce réglage est différé, il n'y a rien à déclencher.
        Label avis = (Label) formulaire.lookup(".avis-redemarrage-texte");
        assertThat(avis).as("l'avis de redémarrage est rendu").isNotNull();
        assertThat(avis.getText()).contains("prochain démarrage");
        assertThat(formulaire.lookup(".avis-redemarrage-quitter"))
                .as("pas de bouton « Quitter » pour un réglage de fonctionnalité (différé, sans action)")
                .isNull();
    }
}
