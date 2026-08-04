package fr.univ_amu.iut.commun.model;

import java.util.Optional;
import java.util.Set;

/// Situe un **carré** dans une région administrative, pour choisir la déclinaison régionale du
/// référentiel d'activité (#2351).
///
/// **Les deux premiers chiffres du numéro de carré sont le département.** Ce n'est écrit nulle part
/// dans le format lui-même : c'est une propriété du numérotage Vigie-Chiro, confirmée par le porteur du
/// produit et tracée par l'ADR 2351. Une déduction tirée de quelques exemples n'aurait pas suffi : une
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
    /// ⚠️ La valeur rendue est une **clé de référentiel** (`Provence-Alpes-Cote dAzur`), sans accents
    /// ni apostrophe. Pour l'afficher à un utilisateur, passer par
    /// [LibellesReferentiel#region(String)] : c'est l'oubli de cette traduction qui a fait lire
    /// « region Provence-Alpes-Cote dAzur » en pied d'écran pendant des mois (#3049).
    public static Optional<String> pour(String numeroCarre) {
        return departement(numeroCarre).flatMap(RegionsFrancaises::pourDepartement);
    }

    /// Le **département** que porte le numéro de carré (ses deux premiers chiffres), ou **vide** si le
    /// numéro ne permet pas de conclure : nul ou trop court.
    ///
    /// Rendu public par #2848, qui confronte cette lecture à celle de la commune du point. La règle
    /// « les deux premiers chiffres » se réécrivait ailleurs en `substring(0, 2)` ; ici elle vit avec
    /// l'ADR qui l'établit.
    ///
    /// ⚠️ Ce n'est **pas** le code officiel : la Corse y porte `20`, là où l'INSEE écrit `2A`/`2B`, et
    /// l'outre-mer y tient sur deux caractères là où le code en compte trois. Pour confronter cette
    /// écriture à celle d'un code INSEE, passer par [RegionsFrancaises#memeDepartement].
    public static Optional<String> departement(String numeroCarre) {
        if (numeroCarre == null || numeroCarre.length() < 2) {
            return Optional.empty();
        }
        return Optional.of(numeroCarre.substring(0, 2));
    }

    /// Toutes les régions que le numérotage peut produire : sert à la garde qui les confronte au
    /// référentiel embarqué.
    public static Set<String> regionsConnues() {
        return RegionsFrancaises.regionsConnues();
    }
}
