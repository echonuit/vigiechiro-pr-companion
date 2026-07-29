package fr.univ_amu.iut.commun.model;

import java.util.Optional;
import java.util.Set;

/// Situe un **carré** dans une région administrative, pour choisir la déclinaison régionale du
/// référentiel d'activité (#2351).
///
/// **Les deux premiers chiffres du numéro de carré sont le département.** Ce n'est écrit nulle part
/// dans le format lui-même : c'est une propriété du numérotage Vigie-Chiro, confirmée par le porteur du
/// produit et tracée par l'ADR 2351. Une déduction tirée de quelques exemples n'aurait pas suffi — une
/// région devinée de travers change le verdict d'activité en silence.
///
/// La table département → région elle-même vit dans [RegionsFrancaises] (#2791) : elle sert aussi la
/// chaîne `point → commune → département → région`, et n'existe qu'en un seul endroit. Cette classe
/// n'apporte que le décodage propre au numéro de carré (la Corse y porte `20`, en chiffres seuls, là
/// où le code officiel se décline en `2A`/`2B`).
public final class RegionDuCarre {

    private RegionDuCarre() {}

    /// La région du carré, ou **vide** si le numéro ne permet pas de conclure : nul, trop court, ou
    /// département inconnu (outre-mer, saisie erronée). Le référentiel retombe alors sur `national`,
    /// ce qui est une lecture plus large mais jamais fausse.
    public static Optional<String> pour(String numeroCarre) {
        if (numeroCarre == null || numeroCarre.length() < 2) {
            return Optional.empty();
        }
        return RegionsFrancaises.pourDepartement(numeroCarre.substring(0, 2));
    }

    /// Toutes les régions que le numérotage peut produire : sert à la garde qui les confronte au
    /// référentiel embarqué.
    public static Set<String> regionsConnues() {
        return RegionsFrancaises.regionsConnues();
    }
}
