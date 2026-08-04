package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.time.LocalDateTime;
import java.util.List;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Ce qu'affichent les colonnes de **contexte** de « Sons & validation » quand leur valeur manque.
///
/// La règle est simple - un **cadratin** ([Formats#VALEUR_ABSENTE], que le dépôt appelle « tiret ») -
/// et elle vivait dans cinq appels à `FormatLigneAudio.ouTiret` qu'aucun test ne tenait. Les
/// formateurs, eux, sont couverts par `FormatLigneAudioTest` ; ce qui manquait est le **câblage** :
/// quelle colonne applique la règle. C'est lui qui se perd, une cellule pouvant afficher `null` sans
/// que rien ne rougisse (#3236, trou relevé à la clôture du lot 3).
///
/// Les assertions comparent à la **constante**, jamais à un littéral : un tiret d'imprimerie tapé à la
/// main ressemble au cadratin à l'écran et n'est pas le même caractère.
@ExtendWith(ApplicationExtension.class)
class ColonnesContexteAbsenteTest {

    /// Une observation dont **tout le contexte manque** : ni carré, ni point, ni commune, ni date, ni
    /// nom de fichier. C'est le cas d'une séquence orpheline, et le seul qui exerce les cinq règles.
    private static LigneObservationAudio sansContexte() {
        return new LigneObservationAudio(
                1L, // idObservation
                10L, // idSequence
                7L, // idPassage
                1, // numeroPassage
                null, // dateEnregistrement, absente
                null, // numeroCarre, absent
                null, // codePoint, absent
                null, // nomSite
                "Pippip", // taxonTadarida
                0.9,
                null, // taxonObservateur
                null,
                StatutObservation.NON_TOUCHEE,
                false, // reference
                null, // commentaire
                null, // frequenceKHz
                "Pipistrelle commune", // nomEspece
                "Pipistrelle commune", // nomTadarida
                null, // latinTadarida
                "Chiroptères", // groupe
                null, // nomFichier, absent
                null, // debutS
                null, // finS
                LocalDateTime.of(2026, 6, 20, 22, 30), // heureCapture
                false, // douteux
                Certitude.SUR,
                null, // taxonValidateur
                null, // certitudeValidateur
                null, // nomValidateur
                0, // nbMessages
                null); // commune, absente
    }

    private static ColonnesAudio.Colonnes colonnes() {
        return new ColonnesAudio.Colonnes(
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                new TableColumn<LigneObservationAudio, LocalDateTime>(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne());
    }

    private static TableColumn<LigneObservationAudio, String> colonne() {
        return new TableColumn<>();
    }

    private static String valeurAffichee(TableColumn<LigneObservationAudio, String> colonne) {
        return colonne.getCellValueFactory()
                .call(new TableColumn.CellDataFeatures<>(null, colonne, sansContexte()))
                .getValue();
    }

    @Test
    @DisplayName("#3236 : chaque colonne de contexte marque l'absence par le cadratin, pas par « null »")
    void chaque_colonne_de_contexte_marque_l_absence() {
        ColonnesAudio.Colonnes col = colonnes();
        ColonnesAudio.configurer(col, ligne -> false, (id, texte) -> {});

        assertThat(List.of(
                        valeurAffichee(col.carre()),
                        valeurAffichee(col.point()),
                        valeurAffichee(col.commune()),
                        valeurAffichee(col.date()),
                        valeurAffichee(col.fichier())))
                .as("carré, point, commune, date et fichier : cinq colonnes, une seule règle")
                .containsOnly(Formats.VALEUR_ABSENTE);
    }

    @Test
    @DisplayName("#3236 : la marque d'absence est un CADRATIN, et non un tiret tapé à la main")
    void la_marque_est_un_cadratin() {
        // Les deux se ressemblent à l'écran et ne sont pas le même caractère : un test écrit avec un
        // littéral passerait ou échouerait pour de mauvaises raisons, selon ce qu'aurait tapé son auteur.
        assertThat(Formats.VALEUR_ABSENTE).hasSize(1);
        assertThat(Formats.VALEUR_ABSENTE.codePointAt(0))
                .as("U+2014, le cadratin - le dépôt l'appelle « tiret » par habitude")
                .isEqualTo(0x2014);
    }
}
