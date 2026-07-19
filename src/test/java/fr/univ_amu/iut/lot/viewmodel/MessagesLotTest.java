package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Contrat des **deux canaux** de l'écran de dépôt (#1890, clôture de l'EPIC #1870).
///
/// L'essentiel tient dans ce que chaque geste **ne** touche **pas** : PIT laissait survivre les mutants
/// qui vident `effacerRetour` et `reinitialiser` de leurs affectations, faute de test disant lequel des
/// deux canaux chacun est censé remettre à zéro. C'est précisément ce que
/// l'[ADR 0028](../../../../../../../dev-docs/decisions/0028-un-etat-n-est-pas-un-compte-rendu.md)
/// sépare : un état survit à la fermeture du bandeau, un compte rendu non.
class MessagesLotTest {

    private final MessagesLot messages = new MessagesLot();

    @Test
    @DisplayName("À l'ouverture, les deux canaux sont muets")
    void deux_canaux_muets_a_l_ouverture() {
        assertThat(messages.etatLotProperty().get()).isEmpty();
        assertThat(messages.retourProperty().get()).isEqualTo(RetourOperation.AUCUN);
    }

    @Test
    @DisplayName("Chaque fabrique pose sa sévérité dans la valeur, pas dans le nom du canal")
    void chaque_fabrique_pose_sa_severite() {
        // Pas d'`info` ici : l'écran du lot n'émet que des succès et des échecs, les guidages venant du
        // dépôt (`DepotViewModel`). Une fabrique qu'aucun appelant n'utilise serait du code mort.
        messages.succes("Archives générées.");
        assertThat(messages.retourProperty().get().severite()).isEqualTo(RetourOperation.Severite.SUCCES);

        messages.erreur("Disque plein.");
        assertThat(messages.retourProperty().get().severite()).isEqualTo(RetourOperation.Severite.ERREUR);
        assertThat(messages.retourProperty().get().texte()).contains("Disque plein.");
    }

    @Test
    @DisplayName("Fermer le bandeau efface le compte rendu et LAISSE l'état du lot")
    void effacer_le_retour_laisse_l_etat() {
        messages.etat("Passage déposé le 2026-06-23T08:00.");
        messages.succes("Archives supprimées.");

        messages.effacerRetour();

        assertThat(messages.retourProperty().get()).isEqualTo(RetourOperation.AUCUN);
        assertThat(messages.etatLotProperty().get())
                .as("l'état décrit la nuit : il n'a pas à disparaître parce qu'on a lu un compte rendu")
                .isEqualTo("Passage déposé le 2026-06-23T08:00.");
    }

    @Test
    @DisplayName("Changer de passage remet les DEUX canaux à zéro")
    void reinitialiser_remet_les_deux_canaux_a_zero() {
        messages.etat("Passage déposé le 2026-06-23T08:00.");
        messages.erreur("Disque plein.");

        messages.reinitialiser();

        assertThat(messages.etatLotProperty().get())
                .as("un état qui survit au changement de passage décrirait le passage précédent")
                .isEmpty();
        assertThat(messages.retourProperty().get()).isEqualTo(RetourOperation.AUCUN);
    }

    @Test
    @DisplayName("Poser un état ne touche pas au compte rendu en cours")
    void poser_un_etat_ne_recouvre_pas_le_retour() {
        // Le défaut d'origine : l'état et le compte rendu partageaient une propriété, et leur
        // cohabitation ne tenait qu'à l'ordre de deux lignes (ADR 0028).
        messages.succes("Archives supprimées (2 Ko libérés).");

        messages.etat("Passage déposé le 2026-06-23T08:00.");

        assertThat(messages.retourProperty().get().texte()).contains("Archives supprimées");
    }
}
