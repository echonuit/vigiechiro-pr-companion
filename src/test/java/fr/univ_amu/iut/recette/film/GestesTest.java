package fr.univ_amu.iut.recette.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Ce qu'un clip montre du GESTE, et non de son seul effet (#4191).
///
/// ## Pourquoi ces cas sont purs
///
/// Le suivi lui-même est trois lignes d'adaptation : un `EventFilter` qui recopie une position dans
/// un champ. Ce qui casse en silence, ce n'est pas la recopie, ce sont les **décisions** - quelle
/// frappe mérite un badge, où tombe le pointeur sur la toile, combien de temps un halo tient.
/// Elles sortent donc du filtre, et ces cas les tiennent sans monter la moindre scène.
class GestesTest {

    /// La règle qui distingue **taper** de **commander**.
    ///
    /// ⚠️ C'est le point délicat de toute la fonctionnalité, et la règle naïve s'y casse. Les
    /// raccourcis de l'écran d'écoute sont des lettres NUES - `R` référence, `D` douteux, `N`
    /// suivant, `1/2/3` certitude. « N'afficher que les touches modifiées ou nommées » raterait
    /// donc exactement ce que la session S3 vient juger. Et tout afficher rendrait les dix-sept
    /// caractères d'un jeton en autant de badges, pour rien.
    @Nested
    @DisplayName("Quelle frappe mérite un badge")
    class QuelleFrappeMeriteUnBadge {

        @Test
        @DisplayName("une lettre nue hors d'un champ est un raccourci")
        void une_lettre_nue_hors_d_un_champ_est_un_raccourci() {
            assertThat(Gestes.aAfficher(false, false))
                    .as("R, D, N, 1/2/3 sont des raccourcis de l'écran d'écoute")
                    .isTrue();
        }

        @Test
        @DisplayName("la même lettre dans un champ de saisie est de la frappe")
        void la_meme_lettre_dans_un_champ_est_de_la_frappe() {
            assertThat(Gestes.aAfficher(false, true))
                    .as("le champ montre déjà ce qu'on y tape : un badge par caractère n'ajoute rien")
                    .isFalse();
        }

        @Test
        @DisplayName("un modificateur fait un raccourci, même dans un champ de saisie")
        void un_modificateur_fait_un_raccourci_meme_dans_un_champ() {
            assertThat(Gestes.aAfficher(true, true))
                    .as("Ctrl+S se commande aussi depuis un champ, et son effet est invisible")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Le libellé du badge")
    class LeLibelleDuBadge {

        @Test
        @DisplayName("une touche nue se lit seule")
        void une_touche_nue_se_lit_seule() {
            assertThat(Gestes.libelle("R", false, false, false)).isEqualTo("R");
        }

        @Test
        @DisplayName("les modificateurs précèdent la touche, dans un ordre fixe")
        void les_modificateurs_precedent_la_touche() {
            // L'ordre est fixe pour que deux clips ne montrent pas deux libellés du même geste.
            assertThat(Gestes.libelle("S", true, true, true)).isEqualTo("Ctrl + Maj + Alt + S");
        }

        @Test
        @DisplayName("un modificateur seul se lit quand même")
        void un_modificateur_seul_se_lit_quand_meme() {
            assertThat(Gestes.libelle("S", true, false, false)).isEqualTo("Ctrl + S");
        }
    }

    /// ⚠️ Ces deux-là sont la raison d'être du halo. Un appui tient sur UNE image à dix images par
    /// seconde : sans résorption, il faudrait tomber pile dessus pour le voir.
    @Nested
    @DisplayName("La résorption du halo")
    class LaResorptionDuHalo {

        @Test
        @DisplayName("à l'instant de l'appui, le halo est entier")
        void a_l_instant_de_l_appui_le_halo_est_entier() {
            assertThat(Gestes.resorption(0, 300)).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("à mi-chemin, il est à moitié")
        void a_mi_chemin_il_est_a_moitie() {
            assertThat(Gestes.resorption(150, 300)).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("passé sa durée, il a disparu et ne repart pas en négatif")
        void passe_sa_duree_il_a_disparu() {
            assertThat(Gestes.resorption(300, 300)).isZero();
            assertThat(Gestes.resorption(10_000, 300))
                    .as("un clip long ne doit pas rallumer un halo éteint")
                    .isZero();
        }

        @Test
        @DisplayName("un âge négatif ne dépasse pas le halo entier")
        void un_age_negatif_ne_depasse_pas_le_halo_entier() {
            // L'horloge peut rendre un instant antérieur entre deux images ; le halo ne doit pas
            // grossir au-delà de lui-même pour autant.
            assertThat(Gestes.resorption(-50, 300)).isCloseTo(1.0, within(0.001));
        }
    }

    /// Le suivi, éprouvé par ses entrées plutôt que par une scène montée.
    @Nested
    @DisplayName("Ce que le suivi retient")
    class CeQueLeSuiviRetient {

        @Test
        @DisplayName("sans le moindre geste, il n'y a rien à dessiner")
        void sans_geste_rien_a_dessiner() {
            assertThat(new Gestes().pointeur()).isEmpty();
        }

        @Test
        @DisplayName("le dernier déplacement fait foi")
        void le_dernier_deplacement_fait_foi() {
            Gestes gestes = new Gestes();

            gestes.noterPointeur("fenetre", 10, 20);
            gestes.noterPointeur("fenetre", 30, 40);

            assertThat(gestes.pointeur()).hasValueSatisfying(vu -> {
                assertThat(vu.x()).isEqualTo(30);
                assertThat(vu.y()).isEqualTo(40);
            });
        }

        /// ⚠️ La fenêtre est retenue AVEC la position, et c'est ce qui fait suivre un menu.
        /// Une position de scène ne veut rien dire sans la scène à laquelle elle se rapporte :
        /// dessiner un point de menu dans le repère de la fenêtre principale le poserait ailleurs.
        @Test
        @DisplayName("la position se rapporte à la fenêtre où elle a été prise")
        void la_position_se_rapporte_a_la_fenetre() {
            Gestes gestes = new Gestes();

            gestes.noterPointeur("menu", 5, 6);

            assertThat(gestes.pointeur())
                    .hasValueSatisfying(vu -> assertThat(vu.fenetre()).isEqualTo("menu"));
        }

        @Test
        @DisplayName("un badge remplace le précédent, il ne s'y empile pas")
        void un_badge_remplace_le_precedent() {
            Gestes gestes = new Gestes();

            gestes.noterTouche("R", 1_000);
            gestes.noterTouche("D", 1_100);

            assertThat(gestes.badge(1_150, 800)).hasValue("D");
        }

        @Test
        @DisplayName("passé sa durée, le badge s'efface")
        void passe_sa_duree_le_badge_s_efface() {
            Gestes gestes = new Gestes();

            gestes.noterTouche("R", 1_000);

            assertThat(gestes.badge(1_799, 800)).hasValue("R");
            assertThat(gestes.badge(1_801, 800))
                    .as("un badge qui resterait ferait croire à un raccourci qu'on n'a pas frappé")
                    .isEmpty();
        }
    }
}
