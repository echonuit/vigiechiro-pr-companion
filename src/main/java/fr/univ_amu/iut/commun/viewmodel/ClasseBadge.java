package fr.univ_amu.iut.commun.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import java.util.Locale;

/// Dérivation **centralisée** de la classe CSS d'un badge de statut / verdict d'un passage, pour les
/// types de `commun.model`. Point unique de vérité partagé par la vue (`ColonneBadge`, dans une cellule
/// de table) et par les viewmodels de feature (`LignePassage` côté « Mes sites ») : ces deux couches ne
/// peuvent pas s'appeler l'une l'autre (un viewmodel ne dépend pas de la couche view), et doivent pourtant
/// produire **exactement** la même chaîne, sinon la couleur du badge se perd. Centralise aussi le
/// `Locale.ROOT` (la casse du nom d'énum ne doit pas varier avec la locale par défaut).
///
/// Pour un statut **propre à une feature** (hors `commun.model`), le mapping reste côté feature
/// (cf. `Fraicheur.classeBadge`, `FormatLigneAudio.classeBadgeStatut`) : `commun` ne dépend d'aucune
/// feature, sous peine de cycle d'architecture.
public final class ClasseBadge {

    private ClasseBadge() {}

    /// Classe CSS du badge de **statut workflow** d'un passage (`badge-statut-…`).
    public static String pour(StatutWorkflow statut) {
        return "badge-statut-" + statut.name().toLowerCase(Locale.ROOT);
    }

    /// Classe CSS du badge de **verdict** d'un passage (`badge-verdict-…`) ; « à vérifier » par défaut
    /// lorsqu'aucun verdict n'est encore saisi.
    public static String pour(Verdict verdict) {
        Verdict effectif = verdict == null ? Verdict.A_VERIFIER : verdict;
        return "badge-verdict-" + effectif.name().toLowerCase(Locale.ROOT);
    }
}
