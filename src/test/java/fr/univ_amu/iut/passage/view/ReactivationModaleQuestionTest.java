package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La phrase de la modale de réactivation nomme le dossier où des fichiers transformés ont été
/// trouvés. Jumeau de #3461 trouvé par audit croisé : elle ne plantait pas, elle disait « null ».
class ReactivationModaleQuestionTest {

    @Test
    @DisplayName("Un dossier ordinaire est nommé par son dernier segment")
    void un_dossier_est_nomme_par_son_dernier_segment() {
        assertThat(ReactivationModaleController.question(Path.of("/mnt/sd/MaNuit"), false))
                .contains("« MaNuit »");
    }

    @Test
    @DisplayName("#3461 : une carte SD désignée par sa racine est nommée par son chemin, pas « null »")
    void une_racine_de_volume_est_nommee_par_son_chemin() {
        Path racine = FileSystems.getDefault().getRootDirectories().iterator().next();
        assertThat(racine.getFileName())
                .as("prémisse du cas : une racine de volume n'a aucun dernier segment")
                .isNull();

        String phrase = ReactivationModaleController.question(racine, true);

        assertThat(phrase).contains("« " + racine + " »").doesNotContain("null");
    }
}
