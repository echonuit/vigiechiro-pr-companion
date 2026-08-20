package fr.univ_amu.iut;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'aiguillage du point d'entrée empaqueté (#4071) : un seul lanceur ouvre la fenêtre **ou** répond
/// en texte, selon le mot reçu.
///
/// Les deux destinations sont fournies à [Launcher#aiguiller], donc rien ici n'ouvre de fenêtre ni ne
/// touche à une base : ce qui est éprouvé, c'est **qui reçoit quoi**.
class LauncherTest {

    /// Une destination qui retient ce qu'elle a reçu, et combien de fois.
    private static final class Destination implements java.util.function.Consumer<String[]> {
        private final List<String[]> appels = new ArrayList<>();

        @Override
        public void accept(String[] args) {
            appels.add(args);
        }

        boolean appelee() {
            return !appels.isEmpty();
        }

        String[] arguments() {
            assertThat(appels).as("destination appelée une seule fois").hasSize(1);
            return appels.getFirst();
        }
    }

    private final Destination fenetre = new Destination();
    private final Destination texte = new Destination();

    @Test
    @DisplayName("Le mot `ihm` ouvre la fenêtre, et le mot ne la suit pas")
    void le_mot_ihm_ouvre_la_fenetre() {
        Launcher.aiguiller(new String[] {"ihm"}, fenetre, texte);

        assertThat(fenetre.appelee())
                .as("le mot déclaré par l'emballage ouvre la fenêtre")
                .isTrue();
        assertThat(texte.appelee()).isFalse();
        // JavaFX expose les arguments tels quels dans `Parameters` : y laisser `ihm` ferait porter à
        // l'application un argument qui ne la concerne pas.
        assertThat(fenetre.arguments())
                .as("le mot est consommé par l'aiguillage")
                .isEmpty();
    }

    @Test
    @DisplayName("Ce qui suit `ihm` est transmis à la fenêtre")
    void ce_qui_suit_le_mot_est_transmis() {
        Launcher.aiguiller(new String[] {"ihm", "--workspace", "/tmp/vc"}, fenetre, texte);

        assertThat(fenetre.arguments()).containsExactly("--workspace", "/tmp/vc");
    }

    @Test
    @DisplayName("Une commande part en texte, arguments intacts")
    void une_commande_part_en_texte() {
        Launcher.aiguiller(new String[] {"lister-sites", "--json"}, fenetre, texte);

        assertThat(texte.appelee()).isTrue();
        assertThat(fenetre.appelee()).as("aucune fenêtre n'a été demandée").isFalse();
        assertThat(texte.arguments()).containsExactly("lister-sites", "--json");
    }

    @Test
    @DisplayName("Sans aucun argument, personne n'a demandé de fenêtre : c'est la ligne de commande qui répond")
    void sans_argument_c_est_la_ligne_de_commande() {
        // ⚠️ Le cas qui décide de la conception (ADR 3828) : traiter l'absence d'argument comme une
        // demande de fenêtre serait une déduction ambiante. Les emballages ÉCRIVENT `ihm` pour le
        // double-clic, personne n'a donc à le deviner ici.
        Launcher.aiguiller(new String[0], fenetre, texte);

        assertThat(texte.appelee()).isTrue();
        assertThat(fenetre.appelee()).isFalse();
        assertThat(texte.arguments()).isEmpty();
    }

    @Test
    @DisplayName("Le mot ne compte qu'en tête : ailleurs, c'est un argument comme un autre")
    void le_mot_ne_compte_qu_en_tete() {
        // Une commande peut légitimement porter « ihm » en valeur d'option le jour où une commande
        // parlera de l'interface. Seule la position de tête déclare.
        Launcher.aiguiller(new String[] {"lister-sites", "ihm"}, fenetre, texte);

        assertThat(fenetre.appelee()).isFalse();
        assertThat(texte.arguments()).containsExactly("lister-sites", "ihm");
    }
}
