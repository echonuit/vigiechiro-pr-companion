package fr.univ_amu.iut.commun.viewmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javafx.collections.transformation.FilteredList;

/// Filtres **composables** d'une table (#470/#537), **génériques** sur le type de ligne `T` : plusieurs
/// prédicats nommés combinés en **ET**, appliqués à la [FilteredList] affichée. À chaque changement, on
/// recompose la conjonction puis on **notifie l'appelant** (`apresApplication`) : c'est là qu'un view-model
/// recalcule ce qui dépend du sous-ensemble affiché (compteurs, indice d'état vide).
///
/// Piloté par la **barre de filtres** (patron « à la Notion ») : chaque puce active branche son prédicat via
/// [#definir] avec sa propre clé (statut, taxon, proba, texte…). Socle partagé (`commun`) des vues tabulaires.
///
/// @param <T> type des lignes filtrées
public final class Filtres<T> {

    private final FilteredList<T> affichees;
    private final Runnable apresApplication;
    private final Map<String, Predicate<T>> actifs = new LinkedHashMap<>();

    public Filtres(FilteredList<T> affichees, Runnable apresApplication) {
        this.affichees = Objects.requireNonNull(affichees, "affichees");
        this.apresApplication = Objects.requireNonNull(apresApplication, "apresApplication");
    }

    /// Définit (ou **retire** si `predicat` est `null`) le filtre identifié par `nom`, puis réapplique.
    public void definir(String nom, Predicate<T> predicat) {
        Objects.requireNonNull(nom, "nom");
        if (predicat == null) {
            actifs.remove(nom);
        } else {
            actifs.put(nom, predicat);
        }
        appliquer();
    }

    /// Les lignes que **tous les filtres sauf `nom`** laissent passer : le **domaine encore atteignable**
    /// d'un critère (#3095).
    ///
    /// Un critère qui peuple sa liste de valeurs doit la calculer **sans son propre prédicat**. En
    /// partant du sous-ensemble affiché, il s'auto-effondrerait : une fois « Aix » retenu, les lignes
    /// restantes ne parlent plus que d'Aix, donc le menu n'offrirait plus qu'« Aix » et l'on ne pourrait
    /// jamais retenir une seconde commune.
    ///
    /// Part de la **source** et non de [#affichees], pour la même raison : la liste affichée est déjà
    /// filtrée, elle ne peut donc jamais faire réapparaître une valeur redevenue disponible.
    ///
    /// Un `nom` inconnu ne retranche rien : le résultat est alors exactement ce que la table affiche.
    ///
    /// @param nom clé du critère à **exclure** de la conjonction
    /// @return les lignes retenues, dans l'ordre de la source
    public List<T> saufLui(String nom) {
        Objects.requireNonNull(nom, "nom");
        Predicate<T> autres = actifs.entrySet().stream()
                .filter(actif -> !actif.getKey().equals(nom))
                .map(Map.Entry::getValue)
                .reduce(Predicate::and)
                .orElse(ligne -> true);
        // Boucle typée plutôt qu'un flux : `getSource()` rend un `ObservableList<? extends T>`, dont le
        // flux ne se collecte pas en `List<T>` sans transtypage non vérifié.
        List<T> retenues = new ArrayList<>();
        for (T ligne : affichees.getSource()) {
            if (autres.test(ligne)) {
                retenues.add(ligne);
            }
        }
        return List.copyOf(retenues);
    }

    /// Retire **tous** les filtres actifs (ex. navigation vers une ligne précise qui doit rester visible quel
    /// que soit le filtrage courant).
    public void reinitialiser() {
        if (!actifs.isEmpty()) {
            actifs.clear();
            appliquer();
        }
    }

    /// Réapplique la **conjonction** des filtres actifs (ou aucun prédicat si vide) à la liste affichée, puis
    /// notifie l'appelant pour qu'il recalcule ce qui dépend du sous-ensemble affiché. Public pour permettre
    /// une **ré-application forcée** après un changement des données sous-jacentes (ex. rechargement de la
    /// table) : le prédicat courant est ré-évalué et le callback (compteurs, indice d'état vide) redéclenché.
    public void appliquer() {
        affichees.setPredicate(actifs.values().stream().reduce(Predicate::and).orElse(null));
        apresApplication.run();
    }
}
