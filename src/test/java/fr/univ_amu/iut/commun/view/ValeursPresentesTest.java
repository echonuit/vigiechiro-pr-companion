package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'extraction des **valeurs présentes** d'une dimension (#3097), écrite quatre fois à l'identique
/// dans les catalogues avant cette classe.
///
/// Elle porte trois règles que les quatre copies appliquaient sans qu'aucun test ne les garde : les
/// valeurs nulles sont **écartées**, les doublons **fondus**, et le reste **trié**. Chacune se voit à
/// l'écran : une entrée vide dans un menu, un lieu proposé deux fois, une liste dans le désordre.
class ValeursPresentesTest {

    private record Ligne(String lieu) {}

    private static List<Ligne> lignes(String... lieux) {
        return Arrays.stream(lieux).map(Ligne::new).toList();
    }

    @Test
    @DisplayName("#3097 : les valeurs sont distinctes et triées")
    void les_valeurs_sont_distinctes_et_triees() {
        List<String> valeurs = ValeursPresentes.de(lignes("Venelles", "Aix", "Venelles", "Gardanne"), Ligne::lieu);

        assertThat(valeurs)
                .as("un lieu proposé deux fois se remarque, et une liste non triée se cherche")
                .containsExactly("Aix", "Gardanne", "Venelles");
    }

    @Test
    @DisplayName("#3097 : une dimension absente n'entre pas dans la liste")
    void une_dimension_absente_est_ecartee() {
        // Une ligne sans commune renseignée ne doit pas produire une entrée vide dans le menu : on ne
        // saurait ni ce qu'elle désigne, ni pourquoi la cocher.
        List<String> valeurs = ValeursPresentes.de(lignes("Aix", null, "Venelles"), Ligne::lieu);

        assertThat(valeurs).containsExactly("Aix", "Venelles");
    }

    @Test
    @DisplayName("#3097 : aucune ligne donne une liste vide, pas une erreur")
    void aucune_ligne_donne_une_liste_vide() {
        assertThat(ValeursPresentes.de(List.<Ligne>of(), Ligne::lieu)).isEmpty();
    }
}
