package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.RapportAncrage;
import java.util.List;

/// Bilan d'une **publication des corrections** vers VigieChiro (#723) : ce qui a été écrit côté
/// plateforme, ce qui a été écarté et pourquoi, et le détail des refus. La publication est
/// **idempotente** (re-pousser une correction identique réécrit la même valeur) : relancer après
/// avoir complété les manques ou rétabli le réseau est toujours sûr.
///
/// @param poussees corrections effectivement écrites côté plateforme
/// @param sansCertitude observations revues mais **sans certitude déclarée** : « à compléter avant
///     publication » (la plateforme exige la certitude avec le taxon, jamais posée par défaut)
/// @param sansAncrage observations revues mais **sans ancrage plateforme** (import CSV, ou import
///     antérieur au chantier #1139) : réimporter depuis VigieChiro pour les ancrer
/// @param horsReferentiel taxon observateur **sans objectid** VigieChiro (hors référentiel) : non
///     publiable, cas normal à afficher
/// @param echecs détail des refus, une entrée par observation (identification locale + cause)
/// @param rapatriement compte rendu, **prêt à afficher**, de la phase d'ancrage qui a précédé l'envoi
///     (#1838) : elle ramène aussi les **échanges avec le validateur** (#1867). Chaîne **vide** quand la
///     nuit était déjà ancrée et qu'aucun rapatriement n'a eu lieu, ce qui est le cas courant. Le texte
///     est celui du port [fr.univ_amu.iut.commun.model.ImportObservations], repris tel quel : le détail
///     du bilan d'import appartient à l'import, il n'a pas à être re-décrit ici
public record BilanPublication(
        int poussees,
        int sansCertitude,
        int sansAncrage,
        int horsReferentiel,
        List<String> echecs,
        RapportAncrage rapatriement) {

    /// Bilan d'une publication **sans phase de rapatriement** : la nuit portait déjà son ancrage, ou la
    /// publication n'était pas suivie. Rien à annoncer au retour.
    public BilanPublication(
            int poussees, int sansCertitude, int sansAncrage, int horsReferentiel, List<String> echecs) {
        this(poussees, sansCertitude, sansAncrage, horsReferentiel, echecs, RapportAncrage.aucun());
    }

    /// Le même bilan, accompagné du compte rendu de la phase d'ancrage qui l'a précédé.
    public BilanPublication avecRapatriement(RapportAncrage rapatriement) {
        return new BilanPublication(poussees, sansCertitude, sansAncrage, horsReferentiel, echecs, rapatriement);
    }

    /// `true` si tout ce qui était publiable a été écrit (aucun refus ; il peut rester des écartées).
    public boolean sansEchec() {
        return echecs.isEmpty();
    }

    /// Nombre d'observations écartées avant envoi (à compléter, sans ancrage ou hors référentiel).
    public int ecartees() {
        return sansCertitude + sansAncrage + horsReferentiel;
    }
}
