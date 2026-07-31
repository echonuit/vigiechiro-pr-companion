package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

import fr.univ_amu.iut.commun.model.ReferentielActivite;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie la **couture** ajoutée par #3018 : le référentiel est un collaborateur de [ServiceSynthese],
/// pas une dépendance cachée.
///
/// L'agrégation elle-même est couverte par [AgregationSyntheseTest], qui passe déjà son référentiel à
/// `AgregationSynthese.de`. Ce qui ne l'était pas, c'est `referentielDisponible()` : les tests de vue
/// **bouchonnent** le service (`when(service.referentielDisponible()).thenReturn(false)`) et
/// n'éprouvent donc que le comportement de l'écran, jamais la règle qui décide de cet état.
///
/// L'enjeu est concret. Tant que le référentiel n'entrait que par `ReferentielActivite.embarque()`, qui
/// **lève** si la ressource manque, l'état « indisponible » n'était atteignable par aucun chemin : ni
/// en production, ni en test. L'écran qui le gère et l'aperçu qui le montre reposaient sur une règle que
/// rien n'exécutait.
class ServiceSyntheseReferentielTest {

    private static ServiceSynthese avec(String csv) throws IOException {
        return new ServiceSynthese(mock(ProjectionsAudioDao.class), ReferentielActivite.lire(new StringReader(csv)));
    }

    @Test
    @DisplayName("#3018 : un référentiel sans aucune ligne se déclare indisponible")
    void referentiel_vide_est_indisponible() throws IOException {
        assertThat(avec("").referentielDisponible())
                .as("c'est l'état que rend l'aperçu « sans référentiel » : sans lui, les colonnes "
                        + "d'activité resteraient affichées avec des cellules vides")
                .isFalse();
    }

    @Test
    @DisplayName("#3018 : une seule déclinaison suffit à rendre le référentiel disponible")
    void referentiel_peuple_est_disponible() throws IOException {
        assertThat(avec("Pipkuh;national;toutes;10;100;1000;9000;Tres bonne").referentielDisponible())
                .isTrue();
    }

    @Test
    @DisplayName("#3018 : le constructeur historique garde le référentiel embarqué, qui n'est pas vide")
    void constructeur_sans_referentiel_prend_l_embarque() {
        // Garde le défaut : si la ressource embarquée devenait vide ou introuvable, tous les écrans
        // basculeraient en « indisponible » sans que rien d'autre ne rougisse.
        assertThat(new ServiceSynthese(mock(ProjectionsAudioDao.class)).referentielDisponible())
                .isTrue();
    }

    @Test
    @DisplayName("#3018 : un référentiel nul est refusé à la construction, pas plus tard")
    void referentiel_nul_refuse() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ServiceSynthese(mock(ProjectionsAudioDao.class), null))
                .withMessageContaining("referentiel");
    }
}
