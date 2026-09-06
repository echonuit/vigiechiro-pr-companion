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

/// La préférence de désignation suit le réglage **courant**, sur le patron de
/// [PreferenceConservationTest] et pour la même raison (#3471).
///
/// La leçon avait été appliquée à cette classe dès son écriture, et ne l'était **pas** à son appelant :
/// `Selecteurs.pour()` résolvait le dispositif à la construction de l'écran, si bien que basculer
/// l'interrupteur ne changeait rien avant un redémarrage. Ces cas gardent le maillon d'ici ;
/// `SelecteursTest#le_reglage_est_relu_a_chaque_designation` garde celui de là-bas.
class PreferenceDesignationPersisteeTest {

    @TempDir
    Path dossier;

    private Reglages reglages;
    private PreferenceDesignationPersistee preference;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        reglages = new Reglages(new ReglagesDao(source));
        preference = new PreferenceDesignationPersistee(reglages);
    }

    @Test
    @DisplayName("sans rien d'écrit, c'est le DÉFAUT du produit - le dialogue du système")
    void sans_rien_d_ecrit_c_est_le_defaut() {
        // Une installation neuve n'a aucune ligne dans `app_setting` : la valeur ne vient donc pas de
        // la base mais de `ReglageDesignation.DEFAUT`, qui vit à côté de la clé pour cette raison.
        assertThat(preference.dialogueDeLApplication()).isEqualTo(ReglageDesignation.DEFAUT);
        assertThat(preference.dialogueDeLApplication()).isFalse();
    }

    @Test
    @DisplayName("#3471 : un réglage posé APRÈS la construction est pris en compte")
    void le_reglage_pose_apres_la_construction_est_suivi() {
        assertThat(preference.dialogueDeLApplication()).isFalse();

        // L'utilisateur ouvre Réglages ▸ Emplacements et coche la case : l'onglet écrit la clé.
        reglages.ecrireBooleen(ReglageDesignation.CLE, true);

        assertThat(preference.dialogueDeLApplication())
                .as("la préférence se lit au moment de servir, jamais à la construction")
                .isTrue();
    }

    @Test
    @DisplayName("#3471 : et décocher est suivi aussi, sans redémarrer")
    void decocher_est_suivi_aussi() {
        reglages.ecrireBooleen(ReglageDesignation.CLE, true);
        assertThat(preference.dialogueDeLApplication()).isTrue();

        reglages.ecrireBooleen(ReglageDesignation.CLE, false);

        assertThat(preference.dialogueDeLApplication()).isFalse();
    }

    @Test
    @DisplayName("la valeur SURVIT à un redémarrage : une autre instance la retrouve")
    void la_valeur_survit_a_un_redemarrage() {
        reglages.ecrireBooleen(ReglageDesignation.CLE, true);

        // Un second montage sur le MÊME dossier : c'est ce qu'un redémarrage fait, et c'est la seule
        // façon de distinguer « écrit en base » de « gardé en mémoire par cette instance ».
        SourceDeDonnees apresRedemarrage = new SourceDeDonnees(new Workspace(dossier));
        PreferenceDesignationPersistee relue =
                new PreferenceDesignationPersistee(new Reglages(new ReglagesDao(apresRedemarrage)));

        assertThat(relue.dialogueDeLApplication()).isTrue();
    }
}
