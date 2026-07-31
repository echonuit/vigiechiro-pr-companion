package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde de [ApercuFx#exigerParLibelle] ([ADR
/// 3053](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/dev-docs/decisions/3053-une-capture-exige-son-libelle.md)).
///
/// Le mécanisme lui-même n'avait **aucun test** : il a été vérifié en lançant les quatre outils de
/// capture, sains puis mutés. C'est une preuve, mais elle ne se rejoue pas toute seule, et le message
/// d'échec est précisément ce qui fait la valeur de la règle - il porte la piste de correction.
///
/// Aucun contrôle JavaFX ici : la fabrique ne connaît que des libellés, et l'exercer sur des chaînes
/// montre qu'elle n'a rien de spécifique à un `MenuItem` ou à un `Tab`.
class ApercuFxLibelleTest {

    private static final List<String> ONGLETS = List.of("Général", "Emplacements", "Import", "Audio");

    @Test
    @DisplayName("#3053 : le libellé présent est rendu")
    void le_libelle_present_est_rendu() {
        assertThat(ApercuFx.exigerParLibelle("les onglets", ONGLETS, texte -> texte, "Import"))
                .isEqualTo("Import");
    }

    @Test
    @DisplayName("#3053 : un libellé absent lève, au lieu de laisser produire une capture sans le geste")
    void un_libelle_absent_leve() {
        assertThatThrownBy(() -> ApercuFx.exigerParLibelle("les onglets", ONGLETS, texte -> texte, "Réglages"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("#3053 : le refus nomme les libellés PRÉSENTS, pas seulement celui qu'il cherchait")
    void le_refus_nomme_ce_qui_etait_offert() {
        // C'est tout l'intérêt du message : un libellé a CHANGÉ, il n'a pas disparu, et la correction
        // consiste à lire la liste. Un « introuvable » sec obligerait à ouvrir le FXML pour la retrouver.
        assertThatThrownBy(() -> ApercuFx.exigerParLibelle("les onglets", ONGLETS, texte -> texte, "Réglages"))
                .hasMessageContaining("Réglages")
                .hasMessageContaining("les onglets")
                .hasMessageContaining("Général")
                .hasMessageContaining("Import");
    }

    @Test
    @DisplayName("#3053 : une liste vide lève aussi, et le dit sans prétendre avoir cherché")
    void une_liste_vide_leve() {
        // Le cas d'un écran chargé trop tôt : chercher dans un menu encore vide ne doit pas se lire
        // comme « ce libellé n'existe pas », mais l'échec reste un échec.
        assertThatThrownBy(() -> ApercuFx.exigerParLibelle("un menu vide", List.<String>of(), texte -> texte, "Lieu"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[]");
    }

    @Test
    @DisplayName("#3053 : la correspondance est exacte, un préfixe ne suffit pas")
    void la_correspondance_est_exacte() {
        // Une correspondance partielle attraperait « Importation » pour « Import » : la capture montrerait
        // alors un autre écran, ce que la règle existe pour empêcher.
        assertThatThrownBy(() -> ApercuFx.exigerParLibelle("les onglets", ONGLETS, texte -> texte, "Impor"))
                .isInstanceOf(IllegalStateException.class);
    }
}
