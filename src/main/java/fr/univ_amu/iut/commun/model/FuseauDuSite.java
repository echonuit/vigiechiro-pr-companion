package fr.univ_amu.iut.commun.model;

import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/// Le fuseau dans lequel s'interprètent les heures d'une **nuit d'écoute** (#3406) : une heure vient
/// de l'**enregistreur posé sur le site**, pas du poste qui dépouille. Passer par
/// `ZoneId.systemDefault()` donnait trois instants pour une même nuit du 3 juillet 2026 - `19:00 GMT`
/// depuis Paris, `21:00` depuis UTC, et **le 4 juillet** depuis Cayenne.
///
/// `Europe/Paris` est écrit en dur, VigieChiro étant un programme national, et `ZoneId.of` gère
/// l'heure d'été. Cela laisse faux les carrés d'outre-mer, ce qui l'était déjà : dériver le fuseau
/// des coordonnées du point, comme la commune l'est déjà ([RegionsFrancaises], ADR 2791), reste la
/// réponse juste, différée et non ignorée.
public final class FuseauDuSite {

    /// Le fuseau des heures saisies et enregistrées sur le site, **en métropole** - et le repli quand
    /// rien ne permet de conclure ([#pour]).
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    /// Département INSEE (`971`…`988`) → fuseau, pour les territoires où la question se pose (#3442).
    ///
    /// **Le département, et non les coordonnées** : la commune est déjà dérivée du GPS ([ADR 2791]) et
    /// `Commune.departement()` en tire `971`…`976`. Un code INSEE désigne un territoire sans ambiguïté,
    /// là où des boîtes englobantes seraient une liste écrite à la main, qui se démode en silence
    /// (l'[ADR 3439]). Le numéro de carré ne pouvait pas servir : outre-mer ils sont en `00xxxx`, `98`,
    /// `99`, et le catalogue n'en porte aucun en `97` (#3298). La **Polynésie** (`987`) est absente
    /// **délibérément** - trois fuseaux qu'un code départemental ne distingue pas - et retombe sur le
    /// repli.
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
