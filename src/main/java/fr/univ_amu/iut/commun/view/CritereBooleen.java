package fr.univ_amu.iut.commun.view;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javafx.scene.Node;

/// Fabrique du **critère booléen** (#3097) : une puce dont la **seule présence** filtre.
///
/// « Sons de référence », « douteux », « non identifiés », « espèces à enjeu » et leurs équivalents sur
/// les autres écrans n'ont rien à choisir : il n'y a que deux états, et le second s'obtient en retirant
/// la puce. Douze critères écrivaient donc la même classe anonyme, dont seules trois valeurs
/// changeaient : la clé, le libellé et le prédicat.
///
/// ## Ce que « pas d'éditeur » veut dire
///
/// [CritereFiltre#editeur] rend `null`, et c'est le contrat que [GestionnaireFiltres] attend : la puce
/// n'affiche alors que son libellé et sa croix, sans contrôle au milieu. Le prédicat est publié
/// **immédiatement**, puisqu'il n'y a rien à attendre de l'utilisateur.
///
/// C'est la seule famille de critères pour laquelle « une puce ajoutée filtre d'emblée » ne fait pas
/// exception à la règle du socle : il n'y a pas d'état neutre à offrir.
///
/// ## Ce qu'une vue mémorise
///
/// Rien, sinon la présence du critère. `valeurCourante` rend donc une liste vide, et `restaurerValeurs`
/// ne signale jamais de perte : le critère est posé, ce qui est tout ce qu'il promet. Rendre une valeur
/// ici ferait croire à une amputation au compte rendu de #3093.
public final class CritereBooleen {

    private CritereBooleen() {}

    /// Un critère sans éditeur, qui applique `retenue` dès que sa puce est ajoutée.
    ///
    /// @param cle clé stable du critère (elle sert aussi aux vues mémorisées)
    /// @param libelle intitulé de la puce
    /// @param retenue ce qui fait qu'une ligne passe
    public static <T> CritereFiltre<T> de(String cle, String libelle, Predicate<T> retenue) {
        return new CritereFiltre<T>() {
            @Override
            public String nom() {
                return cle;
            }

            @Override
            public String libelle() {
                return libelle;
            }

            @Override
            public Node editeur(Consumer<Predicate<T>> applique) {
                applique.accept(retenue); // filtre actif dès l'ajout de la puce
                return null; // booléen : pas d'éditeur, la présence de la puce suffit
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                return List.of();
            }

            @Override
            public List<String> restaurerValeurs(Node editeur, List<String> valeurs) {
                return List.of();
            }
        };
    }
}
