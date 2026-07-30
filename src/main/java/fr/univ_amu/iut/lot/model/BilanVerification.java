package fr.univ_amu.iut.lot.model;

import java.util.List;

/// Bilan de la **vérification a posteriori** d’un dépôt (#1132) : chaque unité du plan local
/// (`depot_unite`) est cherchée côté plateforme : dans le **journal de traitement** (WAV et ZIP y
/// sont nommés un à un) et dans les **titres des `donnees`** Tadarida (WAV sans extension).
///
/// @param participationId participation liée au passage vérifié
/// @param journalDisponible `false` tant que le traitement serveur n’a pas tourné (le journal
///     n’existe qu’après) : les ZIP ne sont alors pas vérifiables
/// @param nombreDonnees nombre de fichiers traités par Tadarida (`donnees`)
/// @param retrouvees unités du plan local retrouvées côté plateforme
/// @param manquantes unités du plan local **introuvables** côté plateforme
public record BilanVerification(
        String participationId,
        boolean journalDisponible,
        int nombreDonnees,
        List<String> retrouvees,
        List<String> manquantes) {

    /// `true` quand toutes les unités du plan local sont retrouvées côté plateforme.
    public boolean estComplet() {
        return manquantes.isEmpty();
    }
}
