package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Tests des fabriques d'**énumération** de [CritereListe] (#2967). [ApplicationExtension] initialise le
/// toolkit JavaFX (la fabrique construit une `ComboBox`) ; aucune scène affichée.
///
/// Ces fabriques ont remplacé cinq implémentations écrites à la main. Deux de leurs règles n'étaient
/// gardées **nulle part** avant ce fichier, ce qui s'est vu en mutant le code : la **présélection** de
/// l'écran de revue et la **restauration** d'une vue mémorisée par le `name()` de la constante.
@ExtendWith(ApplicationExtension.class)
class CritereListeEnumerationTest {

    private static final String CLE = "couleur";
    private static final String LIBELLE = "Couleur";
    private static final String INVITE = "Choisir une couleur";

    /// Une énumération de test : la fabrique ne doit rien savoir des énumérations du domaine.
    private enum Couleur {
        ROUGE,
        VERT,
        BLEU
    }

    private static final List<Couleur> TOUTES = List.of(Couleur.values());

    private static String libelle(Couleur couleur) {
        return switch (couleur) {
            case ROUGE -> "Rouge vif";
            case VERT -> "Vert d'eau";
            case BLEU -> "Bleu nuit";
        };
    }

    /// Le critère sous test : une énumération quelconque, filtrée par égalité.
    private static CritereFiltre<Couleur> critere() {
        return CritereListe.enumeration(CLE, LIBELLE, INVITE, TOUTES, CritereListeEnumerationTest::libelle, egalite());
    }

    private static Function<Couleur, Predicate<Couleur>> egalite() {
        return attendue -> couleur -> couleur == attendue;
    }

    private static Node editeurDe(CritereFiltre<Couleur> critere, AtomicReference<Predicate<Couleur>> courant) {
        return critere.editeur(courant::set);
    }

    /// Le texte que la liste affiche pour l'entrée `indice`, c'est-à-dire le convertisseur à l'œuvre.
    ///
    /// Paramétrée sur `E` plutôt qu'écrite sur `ComboBox<?>` : la capture du joker lie alors le type des
    /// entrées à celui du convertisseur, ce qui évite un transtypage non vérifié pour les rapprocher.
    private static <E> String texteAffiche(ComboBox<E> choix, int indice) {
        return choix.getConverter().toString(choix.getItems().get(indice));
    }

    @Test
    @DisplayName("#2967 : sans présélection, ajouter la puce n'écarte rien")
    void sans_preselection_la_puce_n_ecarte_rien() {
        CritereFiltre<Couleur> critere = critere();
        AtomicReference<Predicate<Couleur>> courant = new AtomicReference<>();
        editeurDe(critere, courant);

        assertThat(courant.get())
                .as("un prédicat non nul est publié dès l'ouverture de l'éditeur")
                .isNotNull();
        for (Couleur couleur : Couleur.values()) {
            assertThat(courant.get().test(couleur))
                    .as("%s passe tant que rien n'est choisi", couleur)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("#2967 : la liste affiche les LIBELLÉS, jamais le name() de la constante")
    void la_liste_affiche_les_libelles() {
        AtomicReference<Predicate<Couleur>> courant = new AtomicReference<>();
        CritereFiltre<Couleur> critere = critere();

        ComboBox<?> choix = (ComboBox<?>) editeurDe(critere, courant);

        assertThat(choix.getItems()).isEqualTo(TOUTES);
        assertThat(texteAffiche(choix, Couleur.VERT.ordinal()))
                .as("c'est tout ce que le socle ne savait pas faire avant #2967")
                .isEqualTo("Vert d'eau");
        assertThat(choix.getPromptText()).isEqualTo(INVITE);
    }

    @Test
    @DisplayName("#2967 : choisir une valeur applique le prédicat de l'écran")
    void choisir_une_valeur_applique_le_predicat() {
        AtomicReference<Predicate<Couleur>> courant = new AtomicReference<>();
        CritereFiltre<Couleur> critere = critere();
        ComboBox<?> choix = (ComboBox<?>) editeurDe(critere, courant);

        choix.getSelectionModel().select(Couleur.BLEU.ordinal());

        assertThat(courant.get().test(Couleur.BLEU)).isTrue();
        assertThat(courant.get().test(Couleur.ROUGE)).isFalse();
    }

    @Test
    @DisplayName("#2967 : une vue mémorisée se rejoue par le name(), pas par le libellé")
    void une_vue_memorisee_se_rejoue_par_le_name() {
        // Le libellé peut changer (traduction, reformulation) sans rien casser ; le name() est le
        // contrat de sérialisation. Mémoriser l'intitulé rendrait caduques toutes les vues enregistrées.
        AtomicReference<Predicate<Couleur>> courant = new AtomicReference<>();
        CritereFiltre<Couleur> critere = critere();
        Node editeur = editeurDe(critere, courant);

        critere.restaurerValeurs(editeur, List.of("VERT"));

        assertThat(critere.valeurCourante(editeur))
                .as("ce qui est mémorisé est le name(), et il se relit tel quel")
                .containsExactly("VERT");
        assertThat(courant.get().test(Couleur.VERT)).isTrue();
        assertThat(courant.get().test(Couleur.ROUGE)).isFalse();
    }

    @Test
    @DisplayName("#2967 : une constante disparue laisse la vue sans filtre, sans faire échouer l'écran")
    void une_constante_inconnue_ne_fait_pas_echouer_l_ecran() {
        // Enum.valueOf aurait levé ici. Une vue mémorisée devenue caduque doit se rejouer sans filtre :
        // l'écran s'ouvre, quitte à montrer trop, plutôt que de ne pas s'ouvrir du tout.
        AtomicReference<Predicate<Couleur>> courant = new AtomicReference<>();
        CritereFiltre<Couleur> critere = critere();
        Node editeur = editeurDe(critere, courant);

        critere.restaurerValeurs(editeur, List.of("MAGENTA"));

        assertThat(critere.valeurCourante(editeur)).isEmpty();
        assertThat(courant.get().test(Couleur.ROUGE))
                .as("rien de sélectionné : la puce n'écarte rien")
                .isTrue();
    }

    @Test
    @DisplayName("#2967 : présélectionnée, la puce filtre DÈS son ajout (« À revoir » de l'écran de revue)")
    void preselectionnee_la_puce_filtre_des_son_ajout() {
        // Cette règle n'était gardée nulle part : l'écran de revue s'ouvre sur l'onglet « Tout », si bien
        // que la présélection ne joue QUE lorsque la puce est ajoutée à la main. Supprimer la ligne qui
        // la pose ne faisait alors rougir aucun des 45 tests de l'écran.
        AtomicReference<Predicate<Couleur>> courant = new AtomicReference<>();
        CritereFiltre<Couleur> critere = CritereListe.enumerationPreselectionnee(
                CLE, LIBELLE, TOUTES, CritereListeEnumerationTest::libelle, egalite(), Couleur.ROUGE);
        Node editeur = editeurDe(critere, courant);

        assertThat(courant.get().test(Couleur.ROUGE))
                .as("la valeur par défaut est appliquée sans qu'on touche à rien")
                .isTrue();
        assertThat(courant.get().test(Couleur.BLEU))
                .as("et elle écarte réellement le reste")
                .isFalse();
        assertThat(critere.valeurCourante(editeur))
                .as("elle se mémorise comme un choix ordinaire")
                .containsExactly("ROUGE");
    }
}
