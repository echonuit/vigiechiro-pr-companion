package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le **retour d'opération** : un texte borné et sa sévérité (ADR 0031).
///
/// Le type portait déjà une règle par construction - aucun glyphe de sévérité en tête - et **aucune
/// classe de test**. Celle-ci la verrouille, et ajoute la seconde règle : ce qui vient d'ailleurs est
/// **borné** (#2076).
class RetourOperationTest {

    /// Un message de pilote qui rappelle sa requête entière : 379 caractères mesurés, qui portaient le
    /// bandeau de 56 à 106 px. Le bandeau n'a pas de troncature - son libellé enroule.
    private static final String MESSAGE_DE_PILOTE = "[SQLITE_CONSTRAINT_FOREIGNKEY] A foreign key constraint"
            + " failed lors de : INSERT INTO observation (id_sequence, taxon_tadarida, taxon_observateur,"
            + " certitude, temps_debut, temps_fin, frequence_mediane, probabilite, id_resultats,"
            + " id_donnee_vigiechiro, indice_observation, commentaire, est_reference, est_douteuse)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    @Test
    @DisplayName("#2076 : un message venu d'AILLEURS est borné, et renvoie au journal pour le détail")
    void un_message_externe_est_borne() {
        RetourOperation retour = RetourOperation.erreur(new IllegalStateException(MESSAGE_DE_PILOTE));

        assertThat(MESSAGE_DE_PILOTE.length())
                .as("prérequis du test : le message est bien plus long que la borne")
                .isGreaterThan(300);
        assertThat(retour.texte()).hasSizeLessThan(300).endsWith("… (détail dans le journal)");
    }

    @Test
    @DisplayName("#2076 : un message que NOUS écrivons n'est pas borné - sa longueur est notre affaire")
    void un_message_que_nous_ecrivons_n_est_pas_borne() {
        String notre = "Aucun fichier à importer : la carte ne contient aucun WAV. ".repeat(6);

        assertThat(RetourOperation.erreur(notre).texte())
                .as("borner ici couperait une phrase relue, au lieu d'un déversement")
                .isEqualTo(notre);
    }

    @Test
    @DisplayName("#2635 : le geste attendu survit - c'est ce que `erreur(x.getMessage())` perdait")
    void le_geste_attendu_survit() {
        RegleMetierException refus =
                new RegleMetierException("Dépôt Vigie-Chiro indisponible.", new Besoin.Connexion());

        assertThat(RetourOperation.erreur(refus).texte())
                .as("le modèle dit ce qui manque, l'IHM dit où le régler")
                .contains("Dépôt Vigie-Chiro indisponible.")
                .contains("menu ☰");
    }

    @Test
    @DisplayName("#2076 : un échec SITUÉ garde notre contexte entier, et ne borne que le reste")
    void un_echec_situe_garde_son_contexte() {
        RetourOperation retour = RetourOperation.erreur(
                "Impossible de charger vos sites : ", new IllegalStateException(MESSAGE_DE_PILOTE));

        assertThat(retour.texte())
                .startsWith("Impossible de charger vos sites : ")
                .endsWith("… (détail dans le journal)");
    }

    @Test
    @DisplayName("un message sans cause reste dit, plutôt qu'un bandeau vide")
    void un_message_absent_est_remplace() {
        assertThat(RetourOperation.erreur((String) null).texte()).isEqualTo("Une erreur est survenue.");
        assertThat(RetourOperation.erreur(new IllegalStateException()).texte()).isEqualTo("Le geste est impossible.");
    }

    @Test
    @DisplayName("la sévérité ne s'écrit pas dans le texte : un glyphe en tête est refusé")
    void un_glyphe_de_severite_en_tete_est_refuse() {
        assertThatThrownBy(() -> RetourOperation.erreur("⚠ attention"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("portée par son niveau");
        assertThat(RetourOperation.erreur("attention ⚠ au milieu").severite()).isEqualTo(Severite.ERREUR);
    }

    @Test
    @DisplayName("#2076 : un message pile à la borne passe entier, il n'y a rien à couper")
    void un_message_pile_a_la_borne_passe_entier() {
        String pileALaBorne = "x".repeat(240);

        RetourOperation retour = RetourOperation.erreur(new IllegalStateException(pileALaBorne));

        // La borne est INCLUSIVE : couper à 240 caractères un message qui en fait exactement 240
        // ajouterait « détail dans le journal » pour ne rien renvoyer de plus au journal.
        assertThat(retour.texte()).endsWith(pileALaBorne).doesNotContain("détail dans le journal");
    }
}
