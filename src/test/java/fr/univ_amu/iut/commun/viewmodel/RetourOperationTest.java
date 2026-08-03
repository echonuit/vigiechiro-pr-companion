package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import java.util.List;
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

    @Test
    @DisplayName("#3056 : une seule valeur perdue se nomme, elle ne se compte pas")
    void vue_amputee_au_singulier_nomme_la_valeur() {
        // Trouvé en regardant la capture, pas en lisant un test : « sans 1 valeur(s) qui n'existent
        // plus (Z1) » ne s'accordait pas, et compter jusqu'à un n'apprenait rien.
        RetourOperation retour = RetourOperation.vueAmputee("Z1 du carre 640380", valeursPerdues("Z1"));

        assertThat(retour.texte())
                .contains("sans « Z1 », qui n'existe plus")
                .doesNotContain("valeur(s)")
                .doesNotContain("1 valeur");
    }

    @Test
    @DisplayName("#3056 : plusieurs valeurs perdues se comptent et s'énumèrent, au pluriel")
    void vue_amputee_au_pluriel_compte_et_enumere() {
        RetourOperation retour = RetourOperation.vueAmputee("Ma saison", valeursPerdues("Z1", "Z2", "Z3"));

        assertThat(retour.texte())
                .contains("sans 3 valeurs qui n'existent plus (Z1, Z2, Z3)")
                .doesNotContain("valeur(s)");
    }

    @Test
    @DisplayName("#3056 : le compte rendu est un avertissement, pas une erreur")
    void vue_amputee_est_un_avertissement() {
        // Rien n'a échoué et l'utilisateur n'a rien à réparer : la sévérité doit le dire, sans quoi le
        // bandeau rouge ferait croire à une panne.
        assertThat(RetourOperation.vueAmputee("V", valeursPerdues("Z1")).severite())
                .isEqualTo(Severite.AVERTISSEMENT);
    }

    @Test
    @DisplayName("#3093 : un critère absent du catalogue se dit, au singulier comme au pluriel")
    void critere_inconnu_se_dit() {
        assertThat(RetourOperation.vueAmputee("V", criteresInconnus("proba")).texte())
                .contains("le critère « proba », qu'il n'offre pas");

        assertThat(RetourOperation.vueAmputee("V", criteresInconnus("proba", "heure"))
                        .texte())
                .contains("2 critères qu'il n'offre pas (proba, heure)");
    }

    @Test
    @DisplayName("#3093 : les deux causes se disent séparément, jamais confondues")
    void les_deux_causes_ne_se_melangent_pas() {
        // Une valeur disparue tient aux données et est passagère ; un critère absent tient à l'écran et
        // est structurel. Les fondre en un seul décompte ferait chercher au mauvais endroit.
        RetourOperation retour = RetourOperation.vueAmputee(
                "Rhinolophes", new ResteDeRestauration(List.of("640380 · Z1"), List.of("proba", "heure")));

        assertThat(retour.texte())
                .contains("« 640380 · Z1 », qui n'existe plus")
                .contains("2 critères qu'il n'offre pas (proba, heure)");
    }

    @Test
    @DisplayName("#3093 : le transport et la mémoire de session ont leur propre phrase")
    void transport_et_session_ne_parlent_pas_de_vue() {
        // Ni l'un ni l'autre n'est une vue nommée : les faire parler de « la vue » demanderait un nom
        // qu'ils n'ont pas.
        assertThat(RetourOperation.filtresNonRepris(criteresInconnus("proba")).texte())
                .contains("plus large que la liste d'où vous venez")
                .doesNotContain("La vue");

        assertThat(RetourOperation.filtresDeSessionAmputes(valeursPerdues("Z1")).texte())
                .contains("plus large que la dernière fois")
                .doesNotContain("La vue");
    }

    private static ResteDeRestauration valeursPerdues(String... valeurs) {
        return new ResteDeRestauration(List.of(valeurs), List.of());
    }

    private static ResteDeRestauration criteresInconnus(String... criteres) {
        return new ResteDeRestauration(List.of(), List.of(criteres));
    }
}
