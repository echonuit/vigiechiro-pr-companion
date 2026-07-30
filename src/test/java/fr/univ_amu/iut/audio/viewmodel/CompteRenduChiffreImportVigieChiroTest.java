package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Traduction du bilan d'un import Vigie-Chiro en compte rendu chiffré (#2651).
///
/// L'écran n'affichait qu'un des sept nombres du bilan, dans une phrase. Ce qui est vérifié ici est
/// que les six autres arrivent **à la bonne place** : deux parts de ventilation pour ce qui compte des
/// observations, des mentions à registre pour le reste.
class CompteRenduChiffreImportVigieChiroTest {

    private static final ResultatsIdentification JEU =
            new ResultatsIdentification(7L, ResultatsIdentification.SOURCE_VIGIECHIRO, "vigiechiro", null, 1L);

    @Nested
    @DisplayName("La ventilation")
    class LaVentilation {

        @Test
        @DisplayName("est exhaustive avec deux parts : importées et ignorées font le total reçu")
        void deux_parts_font_le_total() {
            CompteRenduChiffre rendu = traduire(new BilanImport(JEU, 128, 12, 3));

            // Le constructeur de Ventilation REFUSE un total non exhaustif : si cette traduction se
            // trompait de parts, elle lèverait ici plutôt que de dessiner une barre fausse.
            assertThat(rendu.ventilation().total()).isEqualTo(140);
            assertThat(rendu.ventilation().segments())
                    .extracting(Segment::libelle, Segment::quantite, Segment::teinte)
                    .containsExactly(
                            org.assertj.core.api.Assertions.tuple("Importées", 128L, Teinte.RETENU),
                            org.assertj.core.api.Assertions.tuple("Ignorées", 12L, Teinte.ECARTE));
        }

        @Test
        @DisplayName("ne porte JAMAIS les taxons hors référentiel : ils comptent des taxons, pas des" + " observations")
        void les_taxons_ne_sont_pas_une_part() {
            // 3 taxons hors référentiel sur un import où RIEN n'est ignoré : si on les prenait pour une
            // part, la somme ferait 131 pour un total de 128 et la barre mentirait.
            CompteRenduChiffre rendu = traduire(new BilanImport(JEU, 128, 0, 3));

            assertThat(rendu.ventilation().total()).isEqualTo(128);
            assertThat(rendu.ventilation().segments())
                    .extracting(Segment::libelle)
                    .containsExactly("Importées");
            assertThat(textes(rendu)).anyMatch(texte -> texte.contains("3 taxon(s) hors référentiel"));
        }

        @Test
        @DisplayName("n'affiche pas un écart qui n'existe pas : « 128 importées », pas « 128 / 128 »")
        void pas_d_ecart_invente() {
            assertThat(traduire(new BilanImport(JEU, 128, 0, 0)).resultat()).isEqualTo("128 importées");
            assertThat(traduire(new BilanImport(JEU, 128, 12, 0)).resultat()).isEqualTo("128 / 140 importées");
        }
    }

    @Nested
    @DisplayName("Les validations de l'observateur")
    class LesValidations {

        @Test
        @DisplayName("perdues : dites en avertissement, c'est le fait le plus coûteux du bilan")
        void les_perdues_sont_dites() {
            CompteRenduChiffre rendu = traduire(new BilanImport(JEU, 128, 0, 0, 41, 2));

            assertThat(textes(rendu))
                    .as("c'est le travail de revue de l'observateur qui disparaît, et rien ne le disait")
                    .anyMatch(texte -> texte.contains("2 validation(s) perdue(s)"));
            assertThat(rendu.severite())
                    .as("perdre du travail déjà accompli décide de la sévérité")
                    .isEqualTo(Severite.AVERTISSEMENT);
        }

        @Test
        @DisplayName("aucune perdue : la mention devient une bonne nouvelle, au registre du succès")
        void tout_preserve_est_une_bonne_nouvelle() {
            CompteRenduChiffre rendu = traduire(new BilanImport(JEU, 128, 0, 0, 41, 0));

            assertThat(rendu.avertissements()).anySatisfy(mention -> {
                assertThat(mention.texte()).contains("41 validation(s) préservée(s)");
                assertThat(mention.severite()).isEqualTo(Severite.SUCCES);
            });
            assertThat(rendu.severite()).isEqualTo(Severite.SUCCES);
        }

        @Test
        @DisplayName("hors réimport : rien n'est dit, plutôt que « 0 validation perdue »")
        void hors_reimport_rien_n_est_annonce() {
            CompteRenduChiffre rendu = traduire(new BilanImport(JEU, 128, 0, 0));

            // Les deux compteurs valent 0 hors réimport. Les afficher annoncerait une absence - la faute
            // que #2677 vient de corriger sur la ligne des volumes de l'import de carte SD.
            assertThat(textes(rendu)).noneMatch(texte -> texte.contains("validation"));
        }
    }

    @Test
    @DisplayName("un échange avec le validateur est annoncé, jamais laissé à découvrir par hasard")
    void les_echanges_sont_annonces() {
        CompteRenduChiffre rendu = traduire(new BilanImport(JEU, 128, 0, 0).avecEchanges(4));

        assertThat(textes(rendu)).anyMatch(texte -> texte.contains("4 observation(s) portent un échange"));
    }

    @Test
    @DisplayName("l'action suivante vient de l'écran : le compte rendu ne finit pas sur « Fermer »")
    void l_action_suivante_vient_de_l_ecran() {
        CompteRenduChiffre rendu = CompteRenduChiffreImportVigieChiro.de(
                new BilanImport(JEU, 128, 0, 0),
                List.of(new CompteRenduChiffre.Action("Ouvrir la revue", true, () -> {})));

        assertThat(rendu.actions())
                .extracting(CompteRenduChiffre.Action::libelle)
                .containsExactly("Ouvrir la revue");
    }

    private static CompteRenduChiffre traduire(BilanImport bilan) {
        return CompteRenduChiffreImportVigieChiro.de(bilan, List.of());
    }

    private static List<String> textes(CompteRenduChiffre rendu) {
        return rendu.avertissements().stream().map(Avertissement::texte).toList();
    }

    @Test
    @DisplayName("ce qui vaut zéro ne se mentionne pas : pas de « 0 ligne(s) ignorée(s) »")
    void ce_qui_vaut_zero_ne_se_mentionne_pas() {
        // Le même défaut que la recette golden avait attrapé sur « 0 Ko de bruts conservés » : une
        // mention à zéro n'informe pas, elle inquiète. Chaque compteur est éprouvé à zéro séparément,
        // sinon un seul garde relâché passerait inaperçu derrière les autres.
        List<String> textes = textes(traduire(new BilanImport(JEU, 140, 0, 0, 0, 0)));

        assertThat(textes)
                .as("aucune mention ne s'affiche pour un compteur à zéro")
                .noneMatch(texte -> texte.contains("0 ligne(s) ignorée(s)"))
                .noneMatch(texte -> texte.contains("0 taxon(s) hors référentiel"))
                .noneMatch(texte -> texte.contains("0 validation(s)"))
                .noneMatch(texte -> texte.contains("0 observation(s)"));
    }
}
