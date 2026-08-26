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
    /// La valeur rendue est une **clé de référentiel** (`Provence-Alpes-Cote dAzur`), sans accents
    /// ni apostrophe. Pour l'afficher à un utilisateur, passer par
    /// [LibellesReferentiel#region(String)] : c'est l'oubli de cette traduction qui a fait lire
    /// « region Provence-Alpes-Cote dAzur » en pied d'écran pendant des mois (#3049).
    public static Optional<String> pour(String numeroCarre) {
        return departement(numeroCarre).flatMap(RegionsFrancaises::pourDepartement);
    }

    /// Le **département** que porte le numéro de carré (ses deux premiers chiffres), ou **vide** quand
    /// le numéro n'en désigne aucun : nul, trop court, ou **préfixe qui n'est pas un département**.
    ///
    /// Rendu public par #2848, qui confronte cette lecture à celle de la commune du point. La règle
    /// « les deux premiers chiffres » se réécrivait ailleurs en `substring(0, 2)` ; ici elle vit avec
    /// l'ADR qui l'établit.
    ///
    /// ## Tous les préfixes ne sont pas des départements (#3298)
    ///
    /// Le catalogue de la plateforme, recensé site par site, porte aussi des préfixes `00` (307 carrés),
    /// `98` (313), `99` (127) et `96` (1) - **1 847 points, 3,5 % du total** - et **aucun `97`**. Les
    /// carrés d'outre-mer sont numérotés `00xxxx` : `000294` est à Saint-Joseph, `001293` à Salazie.
    ///
    /// Rendre « 00 » comme s'il s'agissait d'un département faisait signaler à l'audit une divergence
    /// **systématiquement fausse** - `00` ne sera jamais égal à `974`, et aucune vérification de terrain
    /// n'aurait pu la faire taire. Un préfixe qui ne désigne pas de département ne porte donc **pas de
    /// lecture**, exactement comme un numéro trop court.
    ///
    /// Ce n'est pas le code **officiel** pour autant : la Corse porte `20`, là où l'INSEE écrit
    /// `2A`/`2B`. Pour confronter cette écriture à celle d'un code INSEE, passer par
    /// [RegionsFrancaises#memeDepartement].
    public static Optional<String> departement(String numeroCarre) {
        if (numeroCarre == null || numeroCarre.length() < 2) {
            return Optional.empty();
        }
        String prefixe = numeroCarre.substring(0, 2);
        return RegionsFrancaises.estUnDepartement(prefixe) ? Optional.of(prefixe) : Optional.empty();
    }

    /// Toutes les régions que le numérotage peut produire : sert à la garde qui les confronte au
    /// référentiel embarqué.
    public static Set<String> regionsConnues() {
        return RegionsFrancaises.regionsConnues();
    }
}
