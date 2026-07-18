package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Enroulement des messages de dialogue pour la capture ([ApercuFx#enrouler], #1468/#1865).
///
/// Hors `showAndWait`, un `DialogPane` ne contraint pas sa largeur : son libellé reste sur une ligne
/// unique que le snapshot coupe par une ellipse. L'enroulement corrige cela **sans réécrire le message**
/// - c'est ce que ces tests protègent, la fidélité étant tout l'intérêt de la manœuvre.
class ApercuFxTest {

    private static final String PARAGRAPHE =
            "Publier les corrections de ce passage vers Vigie-Chiro, sachant que les valeurs déjà publiées"
                    + " seront réécrites et qu'une correction publiée ne peut pas être retirée ?";

    @Test
    @DisplayName("aucun mot n'est ajouté, retiré ni modifié : seules des coupures apparaissent")
    void enrouler_ne_change_aucun_mot() {
        String enroule = ApercuFx.enrouler(PARAGRAPHE);

        assertThat(enroule).contains("\n").isNotEqualTo(PARAGRAPHE);
        assertThat(mots(enroule))
                .as("un message documenté qui aurait dérivé de sa source ne documenterait plus rien")
                .isEqualTo(mots(PARAGRAPHE));
    }

    @Test
    @DisplayName("les lignes produites tiennent dans la largeur d'enroulement")
    void enrouler_coupe_les_lignes_trop_longues() {
        // 71 et non 70 : la coupure se décide sur « longueur + mot » sans compter l'espace qui les sépare,
        // si bien qu'une ligne peut dépasser d'un caractère. Comportement d'origine, laissé tel quel : le
        // corriger déplacerait les coupures de captures déjà publiées, pour un caractère.
        assertThat(ApercuFx.enrouler(PARAGRAPHE).split("\n"))
                .allSatisfy(ligne -> assertThat(ligne.length()).isLessThanOrEqualTo(71));
    }

    @Test
    @DisplayName("#1865 : les paragraphes du message sont préservés, et enroulés chacun pour soi")
    void enrouler_preserve_les_paragraphes() {
        String message = PARAGRAPHE + "\n\nResteront à quai : 3 sans certitude déclarée.";

        String enroule = ApercuFx.enrouler(message);

        assertThat(enroule)
                .as("sans cela, la coupure serait comptée comme un mot et le découpage partirait de travers")
                .contains("\n\nResteront à quai : 3 sans certitude déclarée.");
        assertThat(mots(enroule)).isEqualTo(mots(message));
    }

    @Test
    @DisplayName("un message court reste intact : rien à couper, rien à toucher")
    void enrouler_laisse_un_message_court_tel_quel() {
        assertThat(ApercuFx.enrouler("Quitter cet écran ?")).isEqualTo("Quitter cet écran ?");
    }

    /// Les mots du message, coupures ignorées : ce qui doit rester identique de part et d'autre.
    private static java.util.List<String> mots(String message) {
        return Arrays.stream(message.split("\\s+"))
                .filter(mot -> !mot.isEmpty())
                .toList();
    }
}
