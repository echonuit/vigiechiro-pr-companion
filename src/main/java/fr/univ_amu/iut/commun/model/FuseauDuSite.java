package fr.univ_amu.iut.commun.model;

import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

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

    /// Le fuseau des heures saisies et enregistrées sur le site, **en métropole** - et le repli quand
    /// rien ne permet de conclure ([#pour]).
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    /// Département INSEE (`971`…`988`) → fuseau, pour les territoires où la question se pose (#3442).
    ///
    /// ## Pourquoi le département, et non les coordonnées
    ///
    /// La commune d'un point est **déjà dérivée de son GPS** ([ADR 2791]) et stockée à côté de lui ;
    /// `Commune.departement()` en tire déjà `971`…`976`. Un code INSEE désigne un territoire **sans
    /// ambiguïté**, là où des boîtes englobantes seraient une liste écrite à la main - donc une liste
    /// qui se démode en silence, ce que l'[ADR 3439] vient de condamner sur les masques de carte.
    ///
    /// ## Pourquoi le numéro de carré ne pouvait pas servir
    ///
    /// Il ne porte pas le département outre-mer : ces carrés sont numérotés `00xxxx`, `98`, `99`, et le
    /// catalogue de la plateforme n'en porte **aucun** en `97` (#3298, 1 847 points recensés).
    ///
    /// ## Ce que cette table ne prétend pas couvrir
    ///
    /// La **Polynésie française** (`987`) est absente **délibérément** : elle s'étend sur trois fuseaux
    /// (Tahiti `-10:00`, Marquises `-09:30`, Gambier `-09:00`), qu'un code départemental ne distingue
    /// pas. Lui attribuer un fuseau unique serait faux pour deux archipels sur trois, et faux **en
    /// silence**. Elle retombe donc sur le repli, comme aujourd'hui, jusqu'à ce qu'une donnée plus fine
    /// existe.
    private static final Map<String, ZoneId> PAR_DEPARTEMENT = Map.ofEntries(
            Map.entry("971", ZoneId.of("America/Guadeloupe")),
            Map.entry("972", ZoneId.of("America/Martinique")),
            Map.entry("973", ZoneId.of("America/Cayenne")),
            Map.entry("974", ZoneId.of("Indian/Reunion")),
            Map.entry("975", ZoneId.of("America/Miquelon")),
            Map.entry("976", ZoneId.of("Indian/Mayotte")),
            Map.entry("977", ZoneId.of("America/St_Barthelemy")),
            Map.entry("978", ZoneId.of("America/Marigot")),
            Map.entry("986", ZoneId.of("Pacific/Wallis")),
            Map.entry("988", ZoneId.of("Pacific/Noumea")));

    private FuseauDuSite() {}

    /// Le fuseau du site dont le point est situé dans `commune`, ou [#ZONE] quand la commune est
    /// inconnue ou métropolitaine.
    ///
    /// Le repli n'est pas un aveu : pour la métropole il est **juste**, et pour un point dont la commune
    /// n'est pas encore résolue - création hors ligne, point sans GPS, rattrapage non passé (ADR 2791) -
    /// il rend exactement le comportement d'avant ce chantier. On ne dégrade jamais ; on précise quand
    /// on peut.
    public static ZoneId pour(Commune commune) {
        if (commune == null) {
            return ZONE;
        }
        return PAR_DEPARTEMENT.getOrDefault(commune.departement(), ZONE);
    }

    /// Les départements dont le fuseau est **connu** de cette table, pour la garde qui la confronte au
    /// référentiel.
    public static Set<String> departementsConnus() {
        return PAR_DEPARTEMENT.keySet();
    }
}
