package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Marquage **douteux** (#160) : le collaborateur charge l'observation, bascule le drapeau et réécrit. La
/// **persistance** du drapeau (insert / update / relecture, y compris la projection audio) est couverte par
/// `ObservationDaoTest#douteux_persiste_relu_et_projete` (`marquer` ne fait que chaîner ces opérations) ;
/// on vérifie ici le **refus** quand l'observation n'existe pas.
class MarquageDouteuxTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Marquer une observation introuvable lève une RegleMetierException")
    void marquer_introuvable_leve() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        MarquageDouteux marquage = new MarquageDouteux(new ObservationDao(source));

        assertThatThrownBy(() -> marquage.marquer(999L, true)).isInstanceOf(RegleMetierException.class);
    }
}
