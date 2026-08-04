package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.LieuQualifie;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// La fabrique du critère **« Lieu »** (#3097), écrit quatre fois à l'identique avant cette classe.
///
/// Le critère confronte **plusieurs dimensions comparables** à une même liste : une ligne passe si
/// **l'une** d'elles figure parmi les valeurs cochées. Les valeurs sont groupées par dimension, chaque
/// groupe précédé de son titre en en-tête non cliquable : une liste plate mêlant communes, carrés et
/// points ne dirait pas de quelle nature est une entrée, et il faut le savoir pour choisir.
///
/// **Trois niveaux, dont un porte deux étiquettes** : commune, carré et point. Ce qui ressemblait à une
/// quatrième dimension, le « site », est le nom convivial du carré, offert dans la même entrée depuis
/// #3157. Leur composition reste un paramètre : Espèces & observations n'offre pas le point tant que sa
/// projection ne remonte pas son code.
@ExtendWith(ApplicationExtension.class)
class CritereLieuTest {

    private record Ligne(String commune, String carre, String point) {}

    private static final List<Ligne> LIGNES =
            List.of(new Ligne("Aix", "640380", "A1"), new Ligne("Venelles", "870150", "B2"));

    private static List<String> entrees(Node editeur) {
        return ((MenuButton) editeur).getItems().stream().map(MenuItem::getText).toList();
    }

    private static void cocher(Node editeur, String valeur) {
        ((MenuButton) editeur)
                .getItems().stream()
                        .filter(CheckMenuItem.class::isInstance)
                        .map(CheckMenuItem.class::cast)
                        .filter(item -> valeur.equals(item.getText()))
                        .findFirst()
                        .orElseThrow()
                        .setSelected(true);
    }

    @Test
    @DisplayName("#3097 : les valeurs sont groupées par dimension, dans l'ordre fourni")
    void les_valeurs_sont_groupees_par_dimension() {
        CritereFiltre<Ligne> critere = CritereLieu.de(
                () -> LIGNES,
                List.of(
                        new CritereLieu.Dimension<>("Communes", Ligne::commune),
                        new CritereLieu.Dimension<>("Carrés", Ligne::carre),
                        new CritereLieu.Dimension<>("Points", Ligne::point)));

        Node editeur = critere.editeur(ignore -> {});

        assertThat(entrees(editeur))
                .as("chaque groupe est annoncé par son titre : « Ahetze » est-il une commune ou un site ?")
                .containsSubsequence("Communes", "Aix", "Venelles", "Carrés", "640380", "870150", "Points", "A1", "B2");
    }

    @Test
    @DisplayName("#3097 : une ligne passe si L'UNE de ses dimensions est cochée")
    void une_ligne_passe_si_l_une_de_ses_dimensions_est_cochee() {
        AtomicReference<Predicate<Ligne>> courant = new AtomicReference<>();
        CritereFiltre<Ligne> critere = CritereLieu.de(
                () -> LIGNES,
                List.of(
                        new CritereLieu.Dimension<>("Communes", Ligne::commune),
                        new CritereLieu.Dimension<>("Carrés", Ligne::carre)));
        Node editeur = critere.editeur(courant::set);

        assertThat(courant.get().test(LIGNES.get(0)))
                .as("rien de coché n'écarte rien")
                .isTrue();

        cocher(editeur, "640380");

        assertThat(courant.get().test(LIGNES.get(0)))
                .as("le carré coché retient la ligne, même si sa commune ne l'est pas")
                .isTrue();
        assertThat(courant.get().test(LIGNES.get(1))).isFalse();
    }

    @Test
    @DisplayName("#3097 : le nombre de dimensions est un PARAMÈTRE, pas un écart à gommer")
    void le_nombre_de_dimensions_est_un_parametre() {
        // Espèces & observations n'offre pas le point, sa projection ne portant qu'un identifiant
        // technique (#3161). La fabrique doit accepter cet écart sans l'aplatir.
        CritereFiltre<Ligne> deuxDimensions = CritereLieu.de(
                () -> LIGNES,
                List.of(
                        new CritereLieu.Dimension<>("Communes", Ligne::commune),
                        new CritereLieu.Dimension<>("Points", Ligne::point)));

        assertThat(entrees(deuxDimensions.editeur(ignore -> {})))
                .contains("Communes", "Points")
                .doesNotContain("Carrés");
    }

    @Test
    @DisplayName("#3097 : une dimension sans aucune valeur ne laisse pas un en-tête orphelin")
    void une_dimension_vide_n_affiche_pas_son_entete() {
        // Un en-tête sans valeur ne renseigne sur rien et fait croire à une liste tronquée.
        CritereFiltre<Ligne> critere = CritereLieu.de(
                () -> List.of(new Ligne("Aix", null, null)),
                List.of(
                        new CritereLieu.Dimension<>("Communes", Ligne::commune),
                        new CritereLieu.Dimension<>("Carrés", Ligne::carre)));

        assertThat(entrees(critere.editeur(ignore -> {})))
                .contains("Communes", "Aix")
                .doesNotContain("Carrés");
    }

    private record LigneSite(String carre, String nomSite, String point) {}

    private static CritereFiltre<LigneSite> critereDeCarres(List<LigneSite> lignes) {
        return CritereLieu.de(() -> lignes, List.of(CritereLieu.carres(LigneSite::carre, LigneSite::nomSite)));
    }

    @Test
    @DisplayName("#3157 : un carré, une entrée, ses deux étiquettes quand la seconde existe")
    void un_carre_une_entree_deux_etiquettes() {
        // Le numéro et le nom convivial sortent de la MÊME ligne `monitoring_site`. Les offrir dans deux
        // groupes revenait à proposer deux fois le même lieu, sans que rien ne le dise.
        CritereFiltre<LigneSite> critere =
                critereDeCarres(List.of(new LigneSite("640380", "Vallon", null), new LigneSite("870150", null, null)));

        assertThat(entrees(critere.editeur(ignore -> {})))
                .as("un carré sans nom convivial garde son numéro seul : le nom est facultatif")
                .containsSubsequence("Carrés", "640380 · Vallon", "870150")
                .doesNotContain("Sites", "Vallon", "640380");
    }

    @Test
    @DisplayName("#3157 : deux lignes du même carré ne donnent qu'une entrée")
    void deux_lignes_du_meme_carre_ne_donnent_qu_une_entree() {
        CritereFiltre<LigneSite> critere = critereDeCarres(
                List.of(new LigneSite("640380", "Vallon", null), new LigneSite("640380", "Vallon", null)));

        assertThat(entrees(critere.editeur(ignore -> {})))
                .filteredOn("640380 · Vallon"::equals)
                .hasSize(1);
    }

    @Test
    @DisplayName("#3158 : un carré mémorisé nu se replace, malgré les points qu'il qualifie")
    void un_carre_memorise_nu_se_replace_malgre_ses_points() {
        // Le cas qui décide de la règle. Le point est qualifié PAR son carré : « 640380 » se lit aussi
        // en tête de « 640380 · A1 ». Chercher « un segment quelque part » trouvait donc DEUX
        // prétendants et s'abstenait - c'est-à-dire jamais de rattrapage pour un carré, le cas même que
        // la fusion crée. Chaque dimension est désormais interrogée sur ses propres valeurs, du côté
        // qu'elle déclare.
        List<LigneSite> lignes = List.of(new LigneSite("640380", "Vallon", "A1"));
        CritereFiltre<LigneSite> critere = CritereLieu.de(
                () -> lignes,
                List.of(
                        CritereLieu.carres(LigneSite::carre, LigneSite::nomSite),
                        CritereLieu.points(ligne -> LieuQualifie.qualifier(ligne.carre(), ligne.point()))));
        Node editeur = critere.editeur(ignore -> {});

        List<String> perdues = critere.restaurerValeurs(editeur, List.of("640380", "A1"));

        assertThat(cochees(editeur))
                .as("le numéro désigne le carré, le code désigne le point : chacun retrouve le sien")
                .containsExactly("640380 · Vallon", "640380 · A1");
        assertThat(perdues).isEmpty();
    }

    @Test
    @DisplayName("#3157 : deux carrés portant le même nom restent distincts")
    void deux_carres_homonymes_restent_distincts() {
        // Rien n'impose l'unicité de `friendly_name` : deux carrés peuvent s'appeler « Vallon ». Sous le
        // groupe « Sites », une entrée « Vallon » en désignait deux, sans moyen de choisir - le défaut
        // que #2992 avait corrigé pour les points, jamais traité à ce niveau.
        AtomicReference<Predicate<LigneSite>> courant = new AtomicReference<>();
        List<LigneSite> lignes =
                List.of(new LigneSite("640380", "Vallon", null), new LigneSite("870150", "Vallon", null));
        CritereFiltre<LigneSite> critere = critereDeCarres(lignes);
        Node editeur = critere.editeur(courant::set);

        cocher(editeur, "640380 · Vallon");

        assertThat(courant.get().test(lignes.get(0))).isTrue();
        assertThat(courant.get().test(lignes.get(1)))
                .as("cocher un « Vallon » ne doit pas retenir l'autre")
                .isFalse();
    }

    /// Une valeur **qualifiée** (« 640380 · A1 ») telle que la produisent les écrans depuis #2992.
    private static String qualifie(Ligne ligne) {
        return ligne.point() == null ? null : ligne.carre() + LieuQualifie.SEPARATEUR + ligne.point();
    }

    private static CritereFiltre<Ligne> avecPointsQualifies(List<Ligne> lignes) {
        return CritereLieu.de(
                () -> lignes,
                List.of(
                        new CritereLieu.Dimension<>("Carrés", Ligne::carre),
                        CritereLieu.points(CritereLieuTest::qualifie)));
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

    @Test
    @DisplayName("#3158 : une valeur mémorisée avant la qualification retrouve son entrée")
    void une_valeur_memorisee_avant_qualification_retrouve_son_entree() {
        // Une vue enregistrée avant #2992 porte « A1 » nu. Le lieu n'a pas changé, seulement son
        // écriture : la vue doit se rejouer, pas s'excuser.
        CritereFiltre<Ligne> critere = avecPointsQualifies(LIGNES);
        Node editeur = critere.editeur(ignore -> {});

        List<String> perdues = critere.restaurerValeurs(editeur, List.of("A1"));

        assertThat(cochees(editeur)).containsExactly("640380 · A1");
        assertThat(perdues)
                .as("rien n'est perdu : le bandeau de #3093 n'a rien à annoncer")
                .isEmpty();
    }

    @Test
    @DisplayName("#3158 : deux entrées reprennent la valeur, aucune n'est cochée")
    void deux_entrees_candidates_ne_rattrapent_rien() {
        // Le garde central. « Z1 » existe dans presque tous les carrés : cocher le premier venu
        // filtrerait sur un point que l'utilisateur n'a pas choisi, et l'écran aurait l'air juste.
        List<Ligne> deuxZ1 = List.of(new Ligne("Aix", "640380", "Z1"), new Ligne("Venelles", "870150", "Z1"));
        CritereFiltre<Ligne> critere = avecPointsQualifies(deuxZ1);
        Node editeur = critere.editeur(ignore -> {});

        List<String> perdues = critere.restaurerValeurs(editeur, List.of("Z1"));

        assertThat(cochees(editeur))
                .as("deviner entre deux lieux serait pire que ne rien replacer")
                .isEmpty();
        assertThat(perdues)
                .as("et #3093 le dit, pour que l'utilisateur tranche lui-même")
                .containsExactly("Z1");
    }

    @Test
    @DisplayName("#3158 : un fragment de valeur ne rattrape rien")
    void un_fragment_ne_rattrape_rien() {
        // Une valeur mémorisée est un lieu qui a existé, pas une amorce de recherche : la comparaison
        // porte sur des segments entiers.
        CritereFiltre<Ligne> critere = avecPointsQualifies(LIGNES);
        Node editeur = critere.editeur(ignore -> {});

        List<String> perdues = critere.restaurerValeurs(editeur, List.of("6403"));

        assertThat(cochees(editeur)).isEmpty();
        assertThat(perdues).containsExactly("6403");
    }

    @Test
    @DisplayName("#3158 : le texte exact prime sur le rattrapage")
    void le_texte_exact_prime() {
        // Le carré « 640380 » est offert tel quel, et il est aussi un segment de « 640380 · A1 ». La
        // valeur mémorisée désigne l'entrée qui porte son texte, sans quoi le rattrapage déplacerait
        // des filtres qui fonctionnent.
        CritereFiltre<Ligne> critere = avecPointsQualifies(LIGNES);
        Node editeur = critere.editeur(ignore -> {});

        critere.restaurerValeurs(editeur, List.of("640380"));

        assertThat(cochees(editeur)).containsExactly("640380");
    }
}
