package fr.univ_amu.iut.multisite.model;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;

/// Jeu de critères de filtrage de la vue multi-sites (parcours P5, P5-CA2). Chaque critère est
/// **optionnel** : un champ `null` signifie « ne pas filtrer sur ce critère ». Les critères
/// renseignés se combinent en ET logique (le passage doit satisfaire **tous** les critères
/// présents).
///
/// Reste la **sémantique de filtrage** de la feature : les prédicats de la barre à puces
/// (`CriteresMultisite`) et la vue « saison » du service ([ServiceMultisite#listerPassagesDeLaSaison(String)])
/// réutilisent [#accepte(LignePassage)]. Les vues mémorisées ne s'appuient plus dessus (#537 étape 6b) :
/// elles passent par le descripteur générique (`commun.view.DescripteurFiltre`).
///
/// @param numeroCarre n° de carré du site à conserver, ou `null` pour tous les sites
/// @param statut statut de workflow à conserver, ou `null` pour tous les statuts
/// @param verdict verdict de vérification à conserver, ou `null` pour tous les verdicts
/// @param annee année à conserver, ou `null` pour toutes les années
public record FiltresMultisite(String numeroCarre, StatutWorkflow statut, Verdict verdict, Integer annee) {

    /// Aucun filtre : toutes les lignes sont conservées.
    public static FiltresMultisite aucun() {
        return new FiltresMultisite(null, null, null, null);
    }

    /// Filtre ne retenant qu'un site (par son n° de carré).
    public static FiltresMultisite parSite(String numeroCarre) {
        return new FiltresMultisite(numeroCarre, null, null, null);
    }

    /// Filtre ne retenant qu'un statut de workflow.
    public static FiltresMultisite parStatut(StatutWorkflow statut) {
        return new FiltresMultisite(null, statut, null, null);
    }

    /// Filtre ne retenant qu'un verdict de vérification.
    public static FiltresMultisite parVerdict(Verdict verdict) {
        return new FiltresMultisite(null, null, verdict, null);
    }

    /// Filtre ne retenant qu'une année.
    public static FiltresMultisite parAnnee(int annee) {
        return new FiltresMultisite(null, null, null, annee);
    }

    /// Indique si `ligne` satisfait **tous** les critères renseignés (les critères `null` sont
    /// ignorés). C'est le prédicat appliqué par [ServiceMultisite] pour filtrer la
    /// vue agrégée.
    public boolean accepte(LignePassage ligne) {
        if (numeroCarre != null && !numeroCarre.equals(ligne.numeroCarre())) {
            return false;
        }
        if (statut != null && statut != ligne.statut()) {
            return false;
        }
        if (verdict != null && verdict != ligne.verdict()) {
            return false;
        }
        return annee == null || annee.intValue() == ligne.annee();
    }
}
