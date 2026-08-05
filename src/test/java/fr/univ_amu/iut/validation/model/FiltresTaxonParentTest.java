package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La règle **une fois** (#3324), là où elle était écrite trois fois.
///
/// Ce fichier existe pour une raison précise : le défaut du taxon parent **vide** avait été trouvé par
/// PIT dans une seule des trois copies, corrigé là, et avait survécu dans les deux autres - qu'aucun
/// test ne couvrait sur ce point. Le tester ici le tient pour les trois appelants à la fois.
class FiltresTaxonParentTest {

    /// Une ligne minimale : son taxon parent, et rien d'autre. Le générique ne connaît que l'accesseur.
    private record Ligne(String groupe) {}

    private static final Function<Ligne, String> GROUPE = Ligne::groupe;

    private static final Ligne CHIRO = new Ligne("Chiroptères");
    private static final Ligne OISEAU = new Ligne("Oiseaux");

    @Test
    @DisplayName("#3324 : correspondance partielle, insensible à la casse et aux accents")
    void correspondance_partielle() {
        assertThat(FiltresTaxonParent.parTaxonParent(List.of(CHIRO, OISEAU), "chirop", GROUPE, "Aucune observation"))
                .containsExactly(CHIRO);
        assertThat(FiltresTaxonParent.parTaxonParent(List.of(CHIRO, OISEAU), "OISEAUX", GROUPE, "Aucune observation"))
                .containsExactly(OISEAU);
    }

    @Test
    @DisplayName("#3324 : un critère nul ou vide n'écarte rien")
    void critere_nul_necarte_rien() {
        List<Ligne> toutes = List.of(CHIRO, OISEAU);

        assertThat(FiltresTaxonParent.parTaxonParent(toutes, null, GROUPE, "Aucune observation"))
                .isEqualTo(toutes);
        assertThat(FiltresTaxonParent.parTaxonParent(toutes, "  ", GROUPE, "Aucune observation"))
                .isEqualTo(toutes);
    }

    @Test
    @DisplayName("#3324 : DÉSIGNER un taxon absent refuse, en nommant ce qui existe et son entité")
    void refus_nomme_ce_qui_existe() {
        // L'entité vient de l'appelant : « Aucun contact » et « Aucune observation » ne sont pas
        // interchangeables, et le générique ne peut pas la deviner.
        assertThatThrownBy(
                        () -> FiltresTaxonParent.parTaxonParent(List.of(CHIRO), "Amphibiens", GROUPE, "Aucun contact"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Aucun contact")
                .hasMessageContaining("Amphibiens")
                .hasMessageContaining("Chiroptères");
    }

    @Test
    @DisplayName("#3324 : le refus dit « parmi celles retenues », vrai pour les TROIS appelants")
    void le_refus_dit_sa_portee() {
        // Les trois filtres s'exécutent après un `parLieu` : l'ensemble reçu est toujours déjà restreint.
        // Deux des trois messages omettaient ce membre de phrase, ce qui laissait croire à un inventaire
        // de toute la base. Ce n'était pas une nuance de style, c'était une imprécision.
        assertThatThrownBy(() ->
                        FiltresTaxonParent.parTaxonParent(List.of(CHIRO), "Oiseaux", GROUPE, "Aucune observation"))
                .hasMessageContaining("parmi celles retenues");
    }

    @Test
    @DisplayName("#3324 : le refus n'énumère pas un taxon parent vide, dans les trois cas désormais")
    void le_refus_nenumere_pas_un_groupe_vide() {
        // LE défaut de cette issue. Trouvé par PIT sur une copie, corrigé là, survivant dans les deux
        // autres : « Taxons parents présents : , Chiroptères », une virgule qui ne désigne rien dans la
        // phrase même censée aider à retrouver la bonne valeur.
        List<Ligne> avecVides = List.of(CHIRO, new Ligne(null), new Ligne("   "));

        // La liste entière est épinglée, et non des motifs comme « , , » : une valeur blanche se TRIE
        // avant « Chiroptères », si bien qu'elle produit « présents :    , Chiroptères » - que ces
        // motifs-là ne voient pas. Première écriture de ce cas, verte sous mutation ; corrigée.
        assertThatThrownBy(() -> FiltresTaxonParent.parTaxonParent(avecVides, "Oiseaux", GROUPE, "Aucune observation"))
                .hasMessageContaining("Taxons parents présents : Chiroptères.");
    }

    @Test
    @DisplayName("#3324 : aucune valeur présente du tout se dit « aucun », pas une liste vide")
    void aucun_groupe_du_tout() {
        assertThatThrownBy(() -> FiltresTaxonParent.parTaxonParent(
                        List.of(new Ligne(null)), "Oiseaux", GROUPE, "Aucune observation"))
                .hasMessageContaining("Taxons parents présents : aucun.");
    }
}
