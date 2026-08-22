package fr.univ_amu.iut.connexion.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le reflet observable du jeton (#4205), éprouvé sans IHM : `RefletDuJeton` ne touche qu'une
/// `Property` et un `Executor`, et les deux se pilotent depuis un test ordinaire.
///
/// ⚠️ Cette classe est **construite** par sept tests de vue, et n'était **éprouvée par aucun**. Un
/// objet que tout le monde câble et que personne ne teste est un angle mort : chacun le croit couvert
/// par le voisin.
class RefletDuJetonTest {

    private final AtomicReference<Optional<String>> jeton = new AtomicReference<>(Optional.empty());

    private RefletDuJeton reflet(java.util.concurrent.Executor filAffichage) {
        return new RefletDuJeton(jeton::get, filAffichage);
    }

    @Test
    @DisplayName("#4205 : la valeur se lit AU BERCEAU, pas à la première relecture")
    void la_valeur_initiale_vient_du_jeton_deja_present() {
        jeton.set(Optional.of("jeton-deja-la"));

        assertThat(reflet(Runnable::run).connecteProperty().get())
                .as("une session déjà connectée doit ouvrir ses gestes DÈS le démarrage : partir de"
                        + " false les grisserait tous jusqu'à la première ouverture de la modale")
                .isTrue();
    }

    @Test
    @DisplayName("#4205 : sans jeton au berceau, le reflet dit non")
    void sans_jeton_le_reflet_dit_non() {
        assertThat(reflet(Runnable::run).connecteProperty().get()).isFalse();
    }

    @Test
    @DisplayName("#4205 : relire publie la nouvelle valeur, et réveille qui l'observe")
    void relire_publie_la_nouvelle_valeur() {
        RefletDuJeton reflet = reflet(Runnable::run);
        List<Boolean> reveils = new ArrayList<>();
        reflet.connecteProperty().addListener((observable, avant, apres) -> reveils.add(apres));

        jeton.set(Optional.of("jeton-qui-arrive"));
        reflet.relire();

        assertThat(reflet.connecteProperty().get()).isTrue();
        assertThat(reveils)
                .as("c'est ce réveil qui rouvre un geste fermé faute de jeton (#4194) : sans lui,"
                        + " l'écran conseille de se connecter et ne voit pas qu'on l'a fait")
                .containsExactly(true);
    }

    @Test
    @DisplayName("#4205 : reposer la même valeur ne réveille personne")
    void reposer_la_meme_valeur_ne_reveille_personne() {
        jeton.set(Optional.of("jeton-stable"));
        RefletDuJeton reflet = reflet(Runnable::run);
        List<Boolean> reveils = new ArrayList<>();
        reflet.connecteProperty().addListener((observable, avant, apres) -> reveils.add(apres));

        reflet.relire();
        reflet.relire();

        // ⚠️ C'est la différence avec `RevisionDonnees`, et elle est voulue : deux mutations
        // successives doivent réveiller deux fois, deux lectures qui rendent « connecté » ne sont pas
        // deux nouvelles. Un compteur ici ferait relire l'écran à chaque `rafraichir()` de la modale.
        assertThat(reveils)
                .as("un état qui ne change pas n'est pas un événement")
                .isEmpty();
    }

    @Test
    @DisplayName("#4205 : la publication passe par l'exécuteur, jamais par le fil appelant")
    void la_publication_passe_par_l_executeur() {
        List<Runnable> differes = new ArrayList<>();
        RefletDuJeton reflet = reflet(differes::add);
        jeton.set(Optional.of("jeton-depuis-un-fil-de-fond"));

        reflet.relire();

        // ⚠️ La connexion se termine sur un fil d'arrière-plan, et une `Property` JavaFX se mute sur le
        // fil JavaFX. Si `relire()` posait la valeur directement, le défaut ne se verrait qu'en
        // production, sous forme d'exception hors du fil - et jamais dans un test à exécuteur direct.
        assertThat(reflet.connecteProperty().get())
                .as("rien n'est publié tant que l'exécuteur n'a pas rendu la main")
                .isFalse();
        assertThat(differes).hasSize(1);

        differes.forEach(Runnable::run);
        assertThat(reflet.connecteProperty().get()).isTrue();
    }
}
