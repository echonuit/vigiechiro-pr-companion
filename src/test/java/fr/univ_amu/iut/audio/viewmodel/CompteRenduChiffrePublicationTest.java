package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.RapportAncrage;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.validation.model.BilanPublication;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Traduction d'une publication de corrections en compte rendu chiffré (#2358).
///
/// Ce que les tests défendent : **la part qui a atteint la plateforme**. La restitution textuelle
/// annonçait « 12 corrections envoyées » sans jamais dire sur combien - c'est précisément la question de
/// l'observateur, et une ventilation fausse y répondrait faux avec l'autorité du visuel.
class CompteRenduChiffrePublicationTest {

    private static final String REFUS_404 = "HTTP 404 (ancrage périmé : réimportez depuis Vigie-Chiro puis republiez)";

    private static String refus(long idObservation, String cause) {
        return "Observation " + idObservation + " (donnée d1, indice 3) : " + cause;
    }

    private static CompteRenduChiffre rendu(BilanPublication bilan) {
        return CompteRenduChiffrePublication.de(bilan, List.of());
    }

    /// Une publication ordinaire : 12 parties, 5 écartées pour trois raisons, 3 refusées pour deux causes.
    private static BilanPublication melangee() {
        return new BilanPublication(
                12, 2, 2, 1, List.of(refus(41, REFUS_404), refus(42, REFUS_404), refus(43, "HTTP 500")));
    }

    @Nested
    @DisplayName("Le verdict")
    class Verdict {

        @Test
        @DisplayName("Le résultat dit la part publiée sur tout ce qui a été revu")
        void resultat_est_une_part() {
            // 12 publiées + 5 écartées + 3 refusées = 20 observations revues.
            assertThat(rendu(melangee()).resultat()).isEqualTo("12 / 20 publiées");
        }

        @Test
        @DisplayName("Tout publié : le résultat ne montre pas un écart qui n'existe pas")
        void resultat_sans_ecart() {
            CompteRenduChiffre rendu = rendu(new BilanPublication(12, 0, 0, 0, List.of()));

            assertThat(rendu.resultat()).isEqualTo("12 publiées");
            assertThat(rendu.severite()).isEqualTo(Severite.SUCCES);
        }

        @Test
        @DisplayName("Un refus de la plateforme est une erreur : la correction n'y est pas")
        void refus_est_une_erreur() {
            assertThat(rendu(melangee()).severite()).isEqualTo(Severite.ERREUR);
        }

        @Test
        @DisplayName("Un écart avant envoi n'est qu'un avertissement : il attend une action, pas un correctif")
        void ecart_est_un_avertissement() {
            assertThat(rendu(new BilanPublication(10, 2, 0, 0, List.of())).severite())
                    .isEqualTo(Severite.AVERTISSEMENT);
        }
    }

    @Nested
    @DisplayName("La ventilation")
    class VentilationDesObservations {

        @Test
        @DisplayName("Les trois natures d'écart sont distinguées, et la somme fait le total revu")
        void somme_egale_le_total() {
            var ventilation = rendu(melangee()).ventilation();

            assertThat(ventilation.total()).isEqualTo(20);
            assertThat(ventilation.segments().stream()
                            .mapToLong(Segment::quantite)
                            .sum())
                    .isEqualTo(20);
            assertThat(ventilation.segments())
                    .as("« écartées » d'un bloc masquerait que les trois appellent des gestes différents")
                    .extracting(Segment::libelle)
                    .containsExactly("Publiées", "À compléter", "Sans ancrage", "Hors référentiel", "Refusées");
        }

        @Test
        @DisplayName("Une nature sans observation ne se déclare pas : « 0 hors référentiel » est du bruit")
        void nature_vide_absente() {
            assertThat(rendu(new BilanPublication(10, 2, 0, 0, List.of()))
                            .ventilation()
                            .segments())
                    .extracting(Segment::libelle)
                    .containsExactly("Publiées", "À compléter");
        }

        @Test
        @DisplayName("Une revue sans observation n'a pas de ventilation à montrer")
        void revue_vide() {
            assertThat(rendu(new BilanPublication(0, 0, 0, 0, List.of()))
                            .ventilation()
                            .estVide())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Les refus")
    class Refus {

        @Test
        @DisplayName("Un motif par cause, portant les observations concernées")
        void un_motif_par_cause() {
            List<Motif> motifs = rendu(melangee()).motifs();

            assertThat(motifs).hasSize(2);
            assertThat(motifs.get(0).compte())
                    .as("deux refus pour la même panne font un motif de deux, pas deux motifs d'un")
                    .isEqualTo(2);
            assertThat(motifs.get(0).sujets())
                    .containsExactly("Observation 41 (donnée d1, indice 3)", "Observation 42 (donnée d1, indice 3)");
            assertThat(motifs.get(1).libelle()).isEqualTo("observation(s) : HTTP 500");
        }

        @Test
        @DisplayName("La cause devient le libellé, l'observation reste le sujet")
        void la_cause_est_le_libelle() {
            assertThat(rendu(melangee()).motifs().get(0).libelle())
                    .isEqualTo("observation(s) : " + REFUS_404)
                    .doesNotContain("Observation 41");
        }

        @Test
        @DisplayName("Un refus sans séparateur reconnaissable garde son texte et prend une cause nommée")
        void refus_sans_separateur() {
            CompteRenduChiffre rendu = rendu(new BilanPublication(0, 0, 0, 0, List.of("panne brute")));

            assertThat(rendu.motifs()).singleElement().satisfies(motif -> {
                assertThat(motif.libelle()).isEqualTo("observation(s) : cause non précisée");
                assertThat(motif.sujets()).containsExactly("panne brute");
            });
        }

        @Test
        @DisplayName("Sans refus, aucun motif à ouvrir")
        void sans_refus() {
            assertThat(rendu(new BilanPublication(12, 0, 0, 0, List.of())).motifs())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Ce que la barre ne porte pas : le remède")
    class Remedes {

        @Test
        @DisplayName("Chaque nature d'écart dit ce qu'il faut faire, et à quel registre")
        void chaque_ecart_dit_son_remede() {
            CompteRenduChiffre rendu = rendu(melangee());

            assertThat(rendu.textesDesAvertissements())
                    .anySatisfy(texte -> assertThat(texte).contains("certitude avec le taxon"))
                    .anySatisfy(texte -> assertThat(texte).contains("rattachez la nuit à sa participation"))
                    .anySatisfy(texte -> assertThat(texte).contains("elles restent locales, c'est attendu"));
            // Hors référentiel est un cas NORMAL : l'annoncer au registre de l'alerte ferait chercher un
            // remède qui n'existe pas.
            assertThat(rendu.avertissements())
                    .filteredOn(mention -> mention.texte().contains("hors référentiel"))
                    .singleElement()
                    .extracting(CompteRenduChiffre.Avertissement::severite)
                    .isEqualTo(Severite.INFO);
        }

        @Test
        @DisplayName("Une publication intégrale le dit, et rappelle qu'on peut la relancer sans risque")
        void publication_integrale_rassure() {
            assertThat(rendu(new BilanPublication(12, 0, 0, 0, List.of())).avertissements())
                    .singleElement()
                    .satisfies(mention -> {
                        assertThat(mention.severite()).isEqualTo(Severite.SUCCES);
                        assertThat(mention.texte()).contains("ne créera pas de doublon");
                    });
        }

        @Test
        @DisplayName("Rien de publié et rien à dire : aucune mention inventée")
        void revue_vide_sans_mention() {
            assertThat(rendu(new BilanPublication(0, 0, 0, 0, List.of())).avertissements())
                    .isEmpty();
        }

        @Test
        @DisplayName("Ce que la phase d'ancrage a rapatrié est repris tel quel")
        void rapatriement_repris_tel_quel() {
            BilanPublication avecAncrage =
                    new BilanPublication(12, 0, 0, 0, List.of()).avecRapatriement(new RapportAncrage("3 ancrées."));

            assertThat(rendu(avecAncrage).textesDesAvertissements()).contains("3 ancrées.");
        }
    }

    @Test
    @DisplayName("Les actions viennent de l'écran : le compte rendu ne décide pas où mènent ses boutons")
    void actions_viennent_de_l_ecran() {
        List<Action> actions = List.of(new Action("Voir sur Vigie-Chiro", true, () -> {}));

        assertThat(CompteRenduChiffrePublication.de(melangee(), actions).actions())
                .isEqualTo(actions);
    }
}
