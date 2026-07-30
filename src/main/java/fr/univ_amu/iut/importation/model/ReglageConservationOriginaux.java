package fr.univ_amu.iut.importation.model;

/// Le réglage **« conserver les originaux »** : sa clé de persistance et son défaut, en un seul endroit.
///
/// Ce réglage a **trois** lecteurs, dont un hors de la feature : l'écran d'import
/// ([PreferenceConservation][fr.univ_amu.iut.importation.viewmodel.PreferenceConservation]), l'onglet
/// « Import » des réglages, et la commande CLI `importer`. La CLI le lit elle-même, comme l'IHM, depuis
/// #2064 : auparavant elle passait par une variante courte du service qui conservait en **dur**, et le
/// même geste ne faisait donc pas la même chose des deux côtés (ADR 0014, parité CLI ↔ IHM).
///
/// **Pourquoi ici et pas dans le `viewmodel`.** La clé y a d'abord vécu, sous la forme d'un `CLE_PUBLIQUE`
/// que la CLI citait. C'était une dépendance vers le `viewmodel` d'une **autre** feature, ce que la règle
/// ArchUnit `une feature ne dépend pas du view ni du viewmodel d'une autre feature` interdit : sans
/// pouvoir la voir : `CLE_PUBLIQUE` était une constante compile-time, donc inlinée par le compilateur, et
/// le `.class` de la commande n'en gardait aucune trace (#2181). La règle était verte à tort.
///
/// Le `model` d'une feature, lui, est **public par contrat** : la commande y cite déjà [ServiceImport],
/// [RapportImport], [ResultatImport] et [PassageExistant]. Descendre la clé d'un cran suffit donc à
/// rendre la dépendance légitime, sans installer dans `commun` un réglage que seul l'import utilise.
///
/// **Pourquoi la clé et le défaut ensemble.** Ils étaient écrits à deux endroits : `lireBooleen(CLE,
/// false)` dans la préférence, et le même appel dans la commande. Un défaut dupliqué est une divergence
/// qui attend son tour : changer d'avis sur « conserver ou non » aurait demandé de le changer deux fois,
/// et rien n'aurait signalé l'oubli.
public final class ReglageConservationOriginaux {

    /// Clé du réglage persisté dans `app_setting` (cf. `Reglages`).
    public static final String CLE = "import.conserver-originaux";

    /// Défaut : **ne pas conserver** (#2063). Copier les bruts coûte plusieurs Go et les deux tiers du
    /// temps d'import, pour un service dont rien dans l'application ne dépend : c'est une option de
    /// ré-analyse, pas un dû.
    ///
    /// Une installation qui a déjà importé porte sa valeur en base, et `lireBooleen` ne retombe sur ce
    /// défaut que si la clé est **absente** : on ne change donc pas dans son dos le choix de quelqu'un qui
    /// l'a déjà fait, seules les installations neuves basculent.
    public static final boolean DEFAUT = false;

    private ReglageConservationOriginaux() {
        // Porteur de constantes : pas d'instance.
    }
}
