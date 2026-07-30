package fr.univ_amu.iut.multisite.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.SuiviTraitement.BilanReleveGroupe;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Traduction d'un relevé groupé en compte rendu chiffré (#2757).
class CompteRenduChiffreReleveTest {

    @Nested
    @DisplayName("Relevé complet")
    class ReleveComplet {

        @Test
        @DisplayName("une seule part, et le résultat ne s'encombre pas d'un « sur »")
        void une_seule_part() {
            CompteRenduChiffre rendu = traduire(new BilanReleveGroupe(12, 0));

            assertThat(rendu.titre()).isEqualTo("État des analyses à jour");
            assertThat(rendu.resultat()).isEqualTo("12 relevées");
            assertThat(rendu.severite()).isEqualTo(Severite.SUCCES);
            assertThat(rendu.ventilation().segments()).singleElement().satisfies(part -> {
                assertThat(part.libelle()).isEqualTo("Relevées");
                assertThat(part.quantite()).isEqualTo(12);
            });
        }

        @Test
        @DisplayName("rien à signaler : aucun avertissement ne vient inquiéter sans motif")
        void aucun_avertissement() {
            assertThat(traduire(new BilanReleveGroupe(12, 0)).avertissements()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Relevé partiel")
    class RelevePartiel {

        @Test
        @DisplayName("la proportion se voit : deux parts qui couvrent le total")
        void deux_parts_exhaustives() {
            CompteRenduChiffre rendu = traduire(new BilanReleveGroupe(9, 3));

            assertThat(rendu.titre()).isEqualTo("Relevé partiel");
            assertThat(rendu.resultat()).isEqualTo("9 / 12 relevées");
            assertThat(rendu.ventilation().total()).isEqualTo(12);
            assertThat(rendu.ventilation().segments())
                    .extracting(CompteRenduChiffre.Segment::libelle, CompteRenduChiffre.Segment::quantite)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("Relevées", 9L),
                            org.assertj.core.groups.Tuple.tuple("Injoignables", 3L));
        }

        @Test
        @DisplayName("un relevé partiel reste une information, jamais une erreur")
        void jamais_une_erreur() {
            // La fraîcheur n'a pas été obtenue partout, mais l'écran continue d'afficher des données
            // justes - seulement plus anciennes. En faire une erreur apprendrait à ignorer les erreurs.
            assertThat(traduire(new BilanReleveGroupe(9, 3)).severite()).isEqualTo(Severite.INFO);
        }

        @Test
        @DisplayName("les injoignables ne sont pas peintes comme un échec")
        void les_injoignables_ne_sont_pas_un_echec() {
            var injoignables = traduire(new BilanReleveGroupe(9, 3))
                    .ventilation()
                    .segments()
                    .get(1);

            // `REFUSE` est rouge et dirait « perdu » ; rien ne l'est. `ECARTE` dit exactement ce qui s'est
            // passé : écarté du relevé, sans que ce soit un échec.
            assertThat(injoignables.teinte()).isEqualTo(Teinte.ECARTE);
        }

        @Test
        @DisplayName("la part injoignable est accompagnée de ce que la barre ne peut pas dire")
        void la_part_injoignable_est_expliquee() {
            List<String> textes = traduire(new BilanReleveGroupe(9, 3)).avertissements().stream()
                    .map(CompteRenduChiffre.Avertissement::texte)
                    .toList();

            assertThat(textes)
                    .as("une barre ocre n'apprend pas que rien n'est perdu")
                    .anySatisfy(texte ->
                            assertThat(texte).contains("dernier état connu").contains("nouveau relevé"));
        }
    }

    @Test
    @DisplayName("tout injoignable : la ventilation reste exhaustive, sans part vide")
    void tout_injoignable() {
        CompteRenduChiffre rendu = traduire(new BilanReleveGroupe(0, 4));

        // Le total est couvert, et « Relevées · 0 » y figure : c'est ce que le constructeur de Ventilation
        // exige. Une part à zéro se lit sans mentir ; c'est son absence qui laisserait un reliquat muet.
        assertThat(rendu.ventilation().total()).isEqualTo(4);
        assertThat(rendu.ventilation().segments()).hasSize(2);
        assertThat(rendu.resultat()).isEqualTo("0 / 4 relevées");
    }

    private static CompteRenduChiffre traduire(BilanReleveGroupe bilan) {
        return CompteRenduChiffreReleve.de(bilan, List.of());
    }
}
