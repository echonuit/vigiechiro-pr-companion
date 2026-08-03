package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La fabrique de **critère booléen** (#3097) : une puce dont la **seule présence** filtre, sans
/// éditeur.
///
/// Douze critères de l'application écrivaient cette classe anonyme à l'identique - références, douteux,
/// non identifiés, espèces à enjeu, et leurs équivalents sur les autres écrans. Ce qui variait tenait à
/// trois valeurs : la clé, le libellé, et le prédicat.
///
/// Test **pur** : un critère booléen n'a pas d'éditeur, donc rien à construire côté JavaFX.
class CritereBooleenTest {

    private record Ligne(String nom, boolean marquee) {}

    private static final Ligne MARQUEE = new Ligne("a", true);
    private static final Ligne ORDINAIRE = new Ligne("b", false);

    private static CritereFiltre<Ligne> critere() {
        return CritereBooleen.de("reference", "Références", Ligne::marquee);
    }

    @Test
    @DisplayName("#3097 : la puce porte sa clé et son libellé")
    void la_puce_porte_sa_cle_et_son_libelle() {
        CritereFiltre<Ligne> critere = critere();

        assertThat(critere.nom()).isEqualTo("reference");
        assertThat(critere.libelle()).isEqualTo("Références");
    }

    @Test
    @DisplayName("#3097 : la seule présence de la puce filtre, sans éditeur")
    void la_presence_de_la_puce_filtre() {
        // C'est toute la différence avec les autres critères : il n'y a rien à choisir, donc le filtre
        // s'applique dès l'ajout. Un éditeur nul est le contrat que `GestionnaireFiltres` attend pour
        // ne pas dessiner de contrôle dans la puce.
        AtomicReference<Predicate<Ligne>> courant = new AtomicReference<>();

        assertThat(critere().editeur(courant::set))
                .as("un booléen n'a pas d'éditeur : la puce n'affiche que son libellé et sa croix")
                .isNull();
        assertThat(courant.get()).isNotNull();
        assertThat(courant.get().test(MARQUEE)).isTrue();
        assertThat(courant.get().test(ORDINAIRE)).isFalse();
    }

    @Test
    @DisplayName("#3097 : un booléen ne mémorise aucune valeur, et n'en perd donc aucune")
    void un_booleen_ne_memorise_aucune_valeur() {
        // Ce que la vue enregistrée doit retenir d'un booléen, c'est sa seule présence. `valeurCourante`
        // vide et `restaurerValeurs` sans reste sont donc le comportement juste, pas un oubli : rendre
        // une valeur ici ferait croire à une perte au compte rendu de #3093.
        CritereFiltre<Ligne> critere = critere();

        assertThat(critere.valeurCourante(null)).isEmpty();
        assertThat(critere.restaurerValeurs(null, List.of()))
                .as("rien n'était mémorisé, rien ne peut manquer")
                .isEmpty();
        assertThat(critere.restaurerValeurs(null, List.of("vestige")))
                .as("une valeur parasite d'une vue ancienne ne se signale pas comme perdue : le critère"
                        + " est bien posé, et c'est tout ce qu'il promet")
                .isEmpty();
    }
}
