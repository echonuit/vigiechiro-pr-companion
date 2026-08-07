package fr.univ_amu.iut.commun.model;

import java.time.ZoneId;

/// Le fuseau dans lequel s'interprètent les heures d'une **nuit d'écoute** (#3406).
///
/// ## Ce que ces heures sont
///
/// Une heure de début ou de fin de passage vient de l'**enregistreur posé sur le site**, pas de la
/// personne qui dépouille. « 21:00 » veut dire « 21 h là où le micro était planté », et cette phrase
/// n'a de sens qu'associée à un fuseau.
///
/// ## Pourquoi Europe/Paris, écrit en dur
///
/// VigieChiro est un **programme national français**. Le fuseau du site est donc connu, et le fixer
/// évite d'aller le chercher. `ZoneId.of` gère l'heure d'été, ce qu'un décalage fixe ne ferait pas.
///
/// ⚠️ **Ce que cela laisse faux** : les carrés d'outre-mer, où l'écart réel atteint plusieurs heures.
/// C'était déjà le cas avant cette classe, et pour tout le monde. Dériver le fuseau des coordonnées du
/// point - comme la commune l'est déjà ([RegionsFrancaises], ADR 2791) - reste la réponse juste ; elle
/// est différée, pas ignorée (#3406).
///
/// ## Le défaut que cette classe supprime
///
/// `CorrespondanceParticipation` convertissait ces heures vers l'UTC attendu par la plateforme en
/// passant par `ZoneId.systemDefault()` - le fuseau du **poste qui dépouille**. Mesuré sur la nuit du
/// 3 juillet 2026, 21:00 → 05:00 :
///
/// | Fuseau du poste | date déposée |
/// | --- | --- |
/// | `Europe/Paris` | `Fri, 3 Jul 2026 19:00:00 GMT` |
/// | `UTC` | `Fri, 3 Jul 2026 21:00:00 GMT` |
/// | `America/Cayenne` | `Sat, 4 Jul 2026 00:00:00 GMT` - **la date change** |
///
/// Trois instants pour une seule nuit, sur une donnée **déposée sur la plateforme nationale**.
public final class FuseauDuSite {

    /// Le fuseau des heures saisies et enregistrées sur le site.
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private FuseauDuSite() {}
}
