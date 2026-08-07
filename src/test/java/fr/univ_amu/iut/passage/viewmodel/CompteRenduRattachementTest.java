package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel.Envoi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Le compte rendu d'un « Appliquer » dit **ce qui a été fait** avant ce qui n'a pas pu l'être (#3449).
///
/// ## Le défaut
///
/// « Modifier le passage » fait deux choses : renommer les séquences sur le disque, puis pousser vers
/// la plateforme. La modale n'annonçait que la **seconde**. Relevé en séance le 2026-08-07 : le
/// renommage de six séquences avait intégralement réussi, en base **et** sur le disque, et le seul
/// retour était un bandeau **rouge** « Envoi impossible ».
///
/// L'utilisateur en conclut que rien ne s'est passé. Il peut donc recommencer une action déclarée
/// irréversible, ou renoncer en croyant son passage inchangé alors que ses fichiers portent déjà le
/// nouveau nom.
class CompteRenduRattachementTest {

    private static final String ECHEC = "Envoi impossible : Ce passage n'est pas encore lié à une participation.";

    @Nested
    @DisplayName("Quand un renommage a eu lieu")
    class AvecRenommage {

        @Test
        @DisplayName("#3449 : un envoi empêché ne masque plus le renommage réussi")
        void envoiEmpecheDitQuandMemeLeRenommage() {
            Envoi compose = CompteRenduRattachement.de(6, new Envoi.Empeche(ECHEC));

            assertThat(compose.retour().texte())
                    .as("ce qui a été fait se dit avant ce qui n'a pas pu l'être")
                    .startsWith("6 séquence(s) renommée(s).")
                    .contains(ECHEC);
        }

        @Test
        @DisplayName("#3449 : un succès partiel n'est pas une erreur, mais il doit être lu")
        void succesPartielNestPasUneErreur() {
            Envoi compose = CompteRenduRattachement.de(6, new Envoi.Empeche(ECHEC));

            // Le registre compte autant que le texte : un bandeau rouge sur une opération dont la partie
            // irréversible a abouti pousse l'utilisateur à recommencer.
            assertThat(compose).isInstanceOf(Envoi.ALire.class);
            assertThat(compose.peutFermer())
                    .as("la modale retient : le compte rendu doit être lu")
                    .isFalse();
        }

        @Test
        @DisplayName("Quand tout réussit, les deux faits se disent ensemble")
        void toutReussiDitLesDeuxFaits() {
            Envoi compose = CompteRenduRattachement.de(6, new Envoi.Abouti("Métadonnées envoyées."));

            assertThat(compose.retour().texte())
                    .startsWith("6 séquence(s) renommée(s).")
                    .contains("Métadonnées envoyées.");
        }

        @Test
        @DisplayName("Sans envoi à faire, le renommage se dit seul")
        void sansEnvoiLeRenommageSeDitSeul() {
            Envoi compose = CompteRenduRattachement.de(6, new Envoi.SansObjet());

            assertThat(compose.retour().texte()).isEqualTo("6 séquence(s) renommée(s).");
        }
    }

    @Nested
    @DisplayName("Quand aucun renommage n'a eu lieu")
    class SansRenommage {

        @Test
        @DisplayName("Le compte rendu de l'envoi passe intact")
        void envoiIntact() {
            Envoi envoi = new Envoi.Empeche(ECHEC);

            assertThat(CompteRenduRattachement.de(0, envoi))
                    .as("rien à ajouter : l'échec de l'envoi EST le compte rendu")
                    .isSameAs(envoi);
        }

        @Test
        @DisplayName("Un envoi abouti reste un succès qui ferme la modale")
        void aboutiResteFermant() {
            Envoi compose = CompteRenduRattachement.de(0, new Envoi.Abouti("Métadonnées envoyées."));

            assertThat(compose.peutFermer()).isTrue();
        }
    }
}
