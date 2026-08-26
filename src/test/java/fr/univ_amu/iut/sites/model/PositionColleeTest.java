package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Lire une position collée depuis une carte (#4575)")
class PositionColleeTest {

    @Test
    @DisplayName("Degrés décimaux : « 43.296482, 5.369780 » se lit latitude puis longitude")
    void degres_decimaux_latitude_puis_longitude() {
        LecturePosition lecture = PositionCollee.lire("43.296482, 5.369780");

        assertThat(lecture).isEqualTo(new LecturePosition.Lue(43.296482, 5.369780));
    }

    @Test
    @DisplayName("Texte illisible : aucun nombre à lire, et le refus dit quoi coller")
    void texte_illisible_refuse_en_disant_quoi_coller() {
        LecturePosition lecture = PositionCollee.lire("l'étang de la Tuilière");

        assertThat(lecture).isInstanceOf(LecturePosition.Illisible.class);
        assertThat(lecture.message())
                .as("le refus doit nommer le format attendu, sans quoi il ne sert à rien")
                .contains("deux nombres")
                .contains("virgule");
    }

    @Test
    @DisplayName("URL de carte : refusée avec SON motif, qui dit de coller la position et non le lien")
    void url_de_carte_refuse_avec_son_propre_motif() {
        LecturePosition lecture = PositionCollee.lire("https://www.google.com/maps/@43.296482,5.369780,17z");

        assertThat(lecture)
                .as("une URL n'est pas un texte illisible : l'observateur a collé quelque chose de sensé,"
                        + " et le motif doit lui dire quoi coller à la place")
                .isInstanceOf(LecturePosition.UrlDeCarte.class);
        assertThat(lecture.message()).contains("lien");
    }

    @Test
    @DisplayName("Degrés-minutes-secondes : lu comme la même position que son équivalent décimal")
    void degres_minutes_secondes_valent_leur_equivalent_decimal() {
        LecturePosition lecture = PositionCollee.lire("43°17'47.3\"N 5°22'11.2\"E");

        assertThat(lecture).isInstanceOf(LecturePosition.Lue.class);
        LecturePosition.Lue lue = (LecturePosition.Lue) lecture;
        // 43 + 17/60 + 47.3/3600 et 5 + 22/60 + 11.2/3600, au dix-millième de degré : environ 11 m,
        // très en dessous des 2 km d'un carré.
        assertThat(lue.latitude()).isCloseTo(43.296472, within(0.0001));
        assertThat(lue.longitude()).isCloseTo(5.369778, within(0.0001));
    }

    @Test
    @DisplayName("Sud et ouest portent le signe : sans quoi une position austral se lit dans l'hémisphère nord")
    void sud_et_ouest_comptent_negativement() {
        LecturePosition lecture = PositionCollee.lire("12°30'0\"S 3°15'0\"W");

        LecturePosition.Lue lue = (LecturePosition.Lue) lecture;
        assertThat(lue.latitude()).isCloseTo(-12.5, within(0.0001));
        assertThat(lue.longitude()).isCloseTo(-3.25, within(0.0001));
    }

    @Test
    @DisplayName("« O » vaut « W » : une carte en français écrit Ouest")
    void ouest_s_ecrit_aussi_en_francais() {
        LecturePosition lecture = PositionCollee.lire("12°30'0\"N 3°15'0\"O");

        LecturePosition.Lue lue = (LecturePosition.Lue) lecture;
        assertThat(lue.longitude()).isCloseTo(-3.25, within(0.0001));
    }
}
