package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// La table de vérité de #4757, cas par cas. Chaque ligne dit **qui a écrit**, et la réponse ne
/// distingue que deux issues : le champ part, ou il se tait.
@DisplayName("ResolutionMeteo : trois valeurs, deux issues")
class ResolutionMeteoTest {

    private static final MeteoDepot M0 = new MeteoDepot("NUL", "0-25", 12, 8);
    private static final MeteoDepot M1 = new MeteoDepot("FORT", "75-100", 20, 18);
    private static final MeteoDepot M2 = new MeteoDepot("MOYEN", "25-50", 15, 11);

    @Nested
    @DisplayName("Ce que la base, nous et eux décident ensemble")
    class TableDeVerite {

        @Test
        @DisplayName("eux seuls ont écrit : le champ se tait, leur saisie survit")
        void eux_seuls() {
            ResolutionMeteo resolue = ResolutionMeteo.entre(M0, M0, M1);

            assertThat(resolue.conflit()).isFalse();
            assertThat(resolue.aEnvoyer()).isNull();
        }

        @Test
        @DisplayName("nous seuls avons écrit : notre saisie part")
        void nous_seuls() {
            ResolutionMeteo resolue = ResolutionMeteo.entre(M0, M1, M0);

            assertThat(resolue.conflit()).isFalse();
            assertThat(resolue.aEnvoyer()).isEqualTo(M1);
        }

        @Test
        @DisplayName("les deux ont écrit LA MÊME chose : rien à arbitrer")
        void les_deux_d_accord() {
            ResolutionMeteo resolue = ResolutionMeteo.entre(M0, M1, M1);

            assertThat(resolue.conflit()).isFalse();
        }

        @Test
        @DisplayName("les deux ont écrit, différemment : personne ne peut trancher à notre place")
        void les_deux_en_desaccord() {
            ResolutionMeteo resolue = ResolutionMeteo.entre(M0, M1, M2);

            assertThat(resolue.conflit()).isTrue();
        }

        @Test
        @DisplayName("personne n'a écrit : le champ se tait plutôt que de répéter l'identique")
        void personne() {
            ResolutionMeteo resolue = ResolutionMeteo.entre(M0, M0, M0);

            assertThat(resolue.conflit()).isFalse();
            assertThat(resolue.aEnvoyer()).isNull();
        }
    }

    @Nested
    @DisplayName("Une météo vide n'est pas une météo")
    class MeteoVide {

        private static final MeteoDepot VIDEE = new MeteoDepot(null, null, null, null);

        @Test
        @DisplayName("un bloc aux quatre composants absents vaut l'absence de bloc")
        void un_bloc_vide_vaut_l_absence() {
            // La plateforme rend l'un ou l'autre selon qu'une meteo a ete saisie puis videe. Sans cette
            // egalite, les trois valeurs seraient toutes distinctes et l'envoi serait REFUSE : notre
            // bloc vide passerait pour une saisie de notre part, contredisant la leur. La configuration
            // matérielle, qui n'y est pour rien, resterait bloquee avec elle.
            //
            // C'est le seul cas ou la normalisation change l'issue : la mesurer ailleurs - sur `conflit`
            // quand une autre branche repond deja - laisse la mutation survivre.
            assertThat(ResolutionMeteo.entre(null, VIDEE, M1).conflit()).isFalse();
            assertThat(ResolutionMeteo.entre(VIDEE, null, M1).conflit()).isFalse();
        }

        @Test
        @DisplayName("effacer une météo ne s'envoie pas, et n'a JAMAIS pu s'envoyer (#4777)")
        void effacer_ne_part_pas() {
            // La base porte une meteo, nous ne la portons plus : nous avons efface. La resolution rend
            // `null`, le champ se tait, et la valeur d'avant survit sur la plateforme.
            //
            // Ce n'est PAS une regression de #4757 : le corps ne peut pas porter un effacement, faute
            // de `serializeNulls()` sur le GSON de RequetesVigieChiro, et `meteo(passage)` rendait deja
            // `null` pour une nuit sans meteo avant ce chantier. Le defaut est anterieur ; la resolution
            // ne fait que le rendre visible. Ce banc l'atteste pour qu'il ne se redecouvre pas.
            ResolutionMeteo resolue = ResolutionMeteo.entre(M0, null, M0);

            assertThat(resolue.conflit()).isFalse();
            assertThat(resolue.aEnvoyer()).isNull();
        }
    }
}
