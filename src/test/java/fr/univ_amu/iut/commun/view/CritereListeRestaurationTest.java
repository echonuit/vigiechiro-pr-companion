package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Ce que devient une **vue mémorisée** quand les valeurs offertes ont changé de libellé, pour un
/// critère qui **ne déclare aucun rattrapage** ([CritereListe#SANS_RATTRAPAGE], le cas de tous les
/// critères sauf « Lieu »).
///
/// Une vue sauvegardée persiste les valeurs cochées **en clair** (table `vue_sauvegardee`,
/// [DescripteurCritere#valeurs]). Rien ne garantit qu'elles existeront encore : #2995 a renommé les
/// entrées de la puce « Lieu » de « Z1 » en « 640380 · Z1 » pour qualifier le point par son carré, et
/// toute vue enregistrée avant ce changement porte désormais des valeurs introuvables.
///
/// Depuis #3158, le critère « Lieu » **rattrape** ce cas : voir `CritereLieuTest`. Le rattrapage y
/// resterait pourtant sans effet, la fixture ci-dessous portant **deux** carrés dont le point s'appelle
/// « Z1 » : une valeur qui en désigne deux ne se replace nulle part, par construction. Ce qui se joue
/// ici est donc bien le socle nu.
@ExtendWith(ApplicationExtension.class)
class CritereListeRestaurationTest {

    private static final List<CritereListe.GroupeValeurs> POINTS_QUALIFIES =
            List.of(new CritereListe.GroupeValeurs("Points", List.of("640380 · Z1", "640381 · Z1")));

    private static CritereFiltre<String> critere() {
        return CritereListe.multipleParmi(
                "lieu", "Lieu", "Choisir un lieu", () -> POINTS_QUALIFIES, (Function<String, List<String>>) List::of);
    }

    @Test
    @DisplayName("Une valeur mémorisée qui n'existe plus n'est pas cochée, et elle est RENDUE")
    void valeur_disparue_est_rendue() {
        CritereFiltre<String> critere = critere();
        Node editeur = critere.editeur(ignore -> {});

        List<String> sansCorrespondance = critere.restaurerValeurs(editeur, List.of("Z1"));

        assertThat(cochees(editeur))
                .as("« Z1 » nu ne correspond à aucune entrée depuis que le point est qualifié par son carré")
                .isEmpty();
        assertThat(sansCorrespondance)
                .as("l'appelant ne peut le dire que si on le lui remonte : c'est tout l'objet de #3056")
                .containsExactly("Z1");
    }

    @Test
    @DisplayName("Une restauration partielle ne rend que ce qui manque")
    void restauration_partielle_ne_rend_que_ce_qui_manque() {
        // Le cas courant d'une vue qui traverse un renommage : une partie des valeurs survit.
        CritereFiltre<String> critere = critere();
        Node editeur = critere.editeur(ignore -> {});

        List<String> sansCorrespondance = critere.restaurerValeurs(editeur, List.of("640380 · Z1", "Z1"));

        assertThat(cochees(editeur)).containsExactly("640380 · Z1");
        assertThat(sansCorrespondance)
                .as("ce qui a été replacé ne doit pas être signalé comme perdu")
                .containsExactly("Z1");
    }

    @Test
    @DisplayName("Rien de coché n'écarte rien : la vue restaurée cesse alors de filtrer, sans le dire")
    void une_vue_restauree_sur_des_valeurs_disparues_ne_filtre_plus() {
        // C'est le mode de panne, et il va dans le sens rassurant : l'utilisateur rouvre une vue
        // nommée « Z1 du carré 640380 », la voit s'ouvrir sans erreur, et lit des observations de tous
        // les carrés en croyant lire celles d'un point.
        CritereFiltre<String> critere = critere();
        AtomicReference<Predicate<String>> applique = new AtomicReference<>();
        Node editeur = critere.editeur(applique::set);

        critere.restaurerValeurs(editeur, List.of("Z1"));

        assertThat(applique.get().test("640381 · Z1"))
                .as("la ligne d'un AUTRE carré passe le filtre : la vue montre plus que ce qu'elle promet")
                .isTrue();
    }

    @Test
    @DisplayName("Une valeur mémorisée toujours offerte se recoche, elle")
    void valeur_encore_offerte_est_cochee() {
        // Le pendant : la restauration fonctionne, et le défaut ci-dessus tient bien à la disparition
        // de la valeur, pas à un mécanisme cassé.
        CritereFiltre<String> critere = critere();
        Node editeur = critere.editeur(ignore -> {});

        List<String> sansCorrespondance = critere.restaurerValeurs(editeur, List.of("640380 · Z1"));

        assertThat(cochees(editeur)).containsExactly("640380 · Z1");
        assertThat(sansCorrespondance)
                .as("rien ne manque : le signalement doit rester silencieux en temps normal")
                .isEmpty();
    }

    private static List<String> cochees(Node editeur) {
        return ((MenuButton) editeur)
                .getItems().stream()
                        .filter(CheckMenuItem.class::isInstance)
                        .map(CheckMenuItem.class::cast)
                        .filter(CheckMenuItem::isSelected)
                        .map(CheckMenuItem::getText)
                        .toList();
    }
}
