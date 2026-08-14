package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.recette.CasDeRecette;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le **contrat collectif** des cartes d'accueil, par opposition au contrat de chacune.
///
/// La mesure de mutation a montré que plusieurs implémentations d'[ActiviteAccueil] n'étaient
/// exercées par aucun test (#3521). Les tester une par une aurait produit des assertions
/// tautologiques - « `titre()` rend la chaîne que `titre()` retourne ». Ce qui mérite un garde, c'est
/// ce qu'aucune classe ne peut vérifier **seule** : les propriétés de l'ensemble.
///
/// L'injecteur est composé **depuis la racine** ([ADR
/// 3018](https://companion-dev.echonuit.fr/decisions/3018-un-outil-compose-depuis-la-racine/)) :
/// énumérer un sous-ensemble de modules donnerait un accueil amputé, indiscernable d'un accueil dont
/// les cartes ont disparu.
class ContratCartesAccueilTest {

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#3521 : dans un prisme, deux cartes ne partagent pas le même rang")
    void un_rang_designe_une_seule_carte_par_prisme(@TempDir Path espaceDeTravail) {
        System.setProperty("vigiechiro.workspace", espaceDeTravail.toString());

        // `MainController` trie les cartes d'un prisme par `ordre()` seul. Un tri stable conserve alors
        // l'ordre d'entrée pour les ex æquo, c'est-à-dire l'ordre de liaison Guice : la position
        // relative de deux cartes de même rang ne se lit donc nulle part dans le code qui les déclare.
        // Elle se décide dans `RacineInjecteur.modules()`, à distance, et sans que rien ne le dise.
        List<String> collisions = cartesAccueil().stream()
                .collect(Collectors.groupingBy(
                        carte -> carte.prisme() + " · rang " + carte.ordre(),
                        Collectors.mapping(carte -> carte.getClass().getSimpleName(), Collectors.toList())))
                .entrySet()
                .stream()
                .filter(entree -> entree.getValue().size() > 1)
                .map(entree -> entree.getKey() + " : "
                        + entree.getValue().stream().sorted().toList())
                .sorted()
                .toList();

        assertThat(collisions)
                .as("deux cartes de même prisme et même rang : leur ordre à l'écran dépend de l'ordre "
                        + "de liaison Guice, pas de ce qu'elles déclarent")
                .isEmpty();
    }

    @Test
    @DisplayName("#3521 : chaque carte annonce un prisme, un titre et une description non vides")
    @CasDeRecette("S1-01")
    void chaque_carte_est_presentable(@TempDir Path espaceDeTravail) {
        System.setProperty("vigiechiro.workspace", espaceDeTravail.toString());

        assertThat(cartesAccueil()).isNotEmpty().allSatisfy(carte -> {
            assertThat(carte.prisme())
                    .as("prisme de %s", carte.getClass().getSimpleName())
                    .isNotNull();
            assertThat(carte.titre())
                    .as("titre de %s", carte.getClass().getSimpleName())
                    .isNotBlank();
            assertThat(carte.description())
                    .as("description de %s", carte.getClass().getSimpleName())
                    .isNotBlank();
            assertThat(carte.pageDoc())
                    .as("fiche d'écran de %s", carte.getClass().getSimpleName())
                    .isNotBlank();
        });
    }

    @Test
    @DisplayName("#3521 : aucune carte ne porte d'emoji, l'icône est un littéral de police d'icônes")
    void les_icones_sont_des_litteraux_de_police(@TempDir Path espaceDeTravail) {
        System.setProperty("vigiechiro.workspace", espaceDeTravail.toString());

        // Un emoji ne se rend pas de la même façon d'une machine à l'autre, et la règle du dépôt est de
        // passer par `FontIcon`. Le littéral suit la forme `fas-feather-alt` : préfixe, tiret, minuscules.
        assertThat(cartesAccueil())
                .allSatisfy(carte -> assertThat(carte.iconeLiteral())
                        .as("icône de %s", carte.getClass().getSimpleName())
                        .matches("[a-z]+-[a-z0-9-]+"));
    }

    private static Set<ActiviteAccueil> cartesAccueil() {
        Injector injecteur = RacineInjecteur.creer();
        return injecteur.getInstance(Key.get(new TypeLiteral<Set<ActiviteAccueil>>() {}));
    }
}
