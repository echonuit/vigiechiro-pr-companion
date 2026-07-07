package fr.univ_amu.iut.commun.view;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javafx.scene.Node;

/// Un **critère** d'une barre de filtres composables (patron « à la Notion », #470/#537), **générique** sur
/// le type de ligne filtrée `T` : une entrée du menu « + Filtre » qui, une fois choisie, s'ajoute comme
/// **puce** active. Socle partagé (`commun`) des vues tabulaires (audio, puis analyse / multisite).
///
/// Une puce active **filtre toujours** (la retirer se fait par le ✕ de la puce) : l'[#editeur] doit donc
/// appeler `applique` avec un **prédicat non nul** dès sa création, puis à chaque changement de valeur.
///
/// @param <T> type des lignes filtrées (ex. `LigneObservationAudio`)
public interface CritereFiltre<T> {

    /// Clé **stable** du filtre : identifie le prédicat dans le gestionnaire et dédoublonne le menu
    /// (un critère déjà actif n'y réapparaît pas).
    String nom();

    /// Libellé affiché dans le menu « + Filtre » et en tête de puce (ex. « Statut », « Chauves-souris »).
    String libelle();

    /// Construit le **contenu éditable** de la puce (contrôles de valeur : liste, seuil…), ou `null` pour un
    /// critère **booléen** sans valeur (la seule présence de la puce filtre). Doit appeler `applique` avec le
    /// prédicat courant **immédiatement** (activation) puis à chaque changement.
    Node editeur(Consumer<Predicate<T>> applique);

    /// Valeur **sémantique** courante du critère, lue depuis son `editeur` (le Node rendu par [#editeur]),
    /// pour un descripteur de filtre **transportable** ([DescripteurCritere], #537 étape 2). Le critère
    /// reste **sans état** : il connaît la structure de son propre éditeur et en extrait la valeur en clair.
    ///
    /// Défaut : **liste vide** — convient aux critères **booléens** (éditeur `null`, la présence de la puce
    /// suffit) ; les critères à valeur (liste, curseur, plage) la redéfinissent.
    ///
    /// @param editeur le Node renvoyé par [#editeur] pour cette puce (peut être `null` pour un booléen)
    /// @return la/les valeur(s) sémantique(s) courante(s), ou liste vide
    default List<String> valeurCourante(Node editeur) {
        return List.of();
    }

    /// **Restaure**, sur l'`editeur` déjà construit, les `valeurs` sémantiques d'un critère (telles que
    /// produites par [#valeurCourante(Node)]) : c'est l'**inverse** de valeurCourante, pour **rejouer une vue
    /// mémorisée** (#623). L'implémentation traduit les valeurs en clair vers les contrôles (sélection d'une
    /// liste, position d'un curseur…), ce qui déclenche leurs écouteurs et donc la réapplication du prédicat.
    ///
    /// Défaut : **no-op** — convient aux critères **booléens** (éditeur `null`, la présence de la puce suffit)
    /// et à tout critère sans valeur à restaurer ; les critères à valeur le redéfinissent.
    ///
    /// @param editeur le Node renvoyé par [#editeur] pour cette puce (peut être `null` pour un booléen)
    /// @param valeurs les valeurs sémantiques à restaurer (mêmes clés/ordre que [#valeurCourante(Node)])
    default void restaurerValeurs(Node editeur, List<String> valeurs) {
        // no-op par défaut : critère booléen ou sans valeur restaurable.
    }
}
