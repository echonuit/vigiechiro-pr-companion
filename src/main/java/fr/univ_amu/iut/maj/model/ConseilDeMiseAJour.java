package fr.univ_amu.iut.maj.model;

import java.util.Locale;
import java.util.Optional;

/// Le geste à conseiller, en plus du téléchargement, pour passer à la version disponible (#3616).
///
/// ## Fermer d'abord, parce que l'installateur ne le fera pas (#3457)
///
/// Un utilisateur a lancé l'installation depuis le bandeau et s'est retrouvé devant une installation
/// qui **ne se termine pas** : l'installateur ne peut pas remplacer des fichiers qu'un processus tient
/// ouverts, et rien ne lui disait ni pourquoi ni quoi faire.
///
/// ⚠️ **Fermer l'application nous-mêmes n'est pas à notre portée**, et c'est une mesure et non une
/// préférence : Companion ne lance aucun installateur. Le bandeau produit une annonce dont l'action
/// **ouvre une page web** ; entre ce clic et le MSI il y a le navigateur, un téléchargement et un
/// double-clic. Se fermer sur ce clic-là fermerait l'application pour aller lire une page.
///
/// Le conseil le **dit** donc, et il le dit en premier : c'est ce qui bloque, le canal d'installation
/// ne vient qu'ensuite.
///
/// ## Pourquoi ce conseil existe
///
/// L'annonce livrée au lot 4 (#2109) renvoyait **tout le monde** vers la page des Releases. Depuis
/// que winget distribue le produit (#2213), ce conseil est **mauvais** pour qui a installé par lui :
/// il pousse à poser un MSI par-dessus une installation gérée par le gestionnaire de paquets.
///
/// ## Pourquoi on ne DÉTECTE pas le canal d'installation
///
/// Le chemin ne discrimine pas : le scope `user` étant une constante d'identité (ADR 0045), un MSI
/// posé à la main et un paquet winget s'installent au **même endroit**. Une détection reste possible
/// - `winget list --id …` porte une colonne `Source` - mais elle demande de lancer un processus
/// externe au démarrage, et surtout elle peut **se tromper avec assurance**.
///
/// Un geste unique et faux envoie l'utilisateur casser son installation ; deux gestes proposés lui
/// coûtent trois secondes de lecture. On propose donc, en disant **à qui** le second s'adresse,
/// plutôt que de deviner - ADR 2213, un dispositif qui ne peut pas conclure ne conclut pas.
///
/// ## Une phrase, pas deux
///
/// La phrase est rendue **à l'identique** par l'IHM ([fr.univ_amu.iut.maj.view.AnnonceMiseAJour]) et
/// par la CLI (`verifier-maj`), conformément à la parité de l'ADR 0014. Si chacune composait sa
/// version, elles divergeraient au premier ajustement.
public final class ConseilDeMiseAJour {

    /// L'identifiant du paquet, tel que winget le connaît (ADR 0047). Stable à vie.
    private static final String PAQUET_WINGET = "Echonuit.VigieChiroCompanion";

    private ConseilDeMiseAJour() {
        // Porte une règle, pas un état.
    }

    /// Le conseil à ajouter pour ce système, ou vide s'il n'y a rien à ajouter.
    ///
    /// @param nomDuSysteme la valeur de `os.name`, telle quelle - éventuellement `null` ou vide, ce
    ///     qui est le cas d'une propriété système qu'on n'a pas pu lire
    public static Optional<String> pour(String nomDuSysteme) {
        if (nomDuSysteme == null || nomDuSysteme.isBlank()) {
            return Optional.empty();
        }
        if (!nomDuSysteme.toLowerCase(Locale.ROOT).startsWith("windows")) {
            return Optional.empty();
        }
        return Optional.of("Fermez l'application avant d'installer la nouvelle version."
                + " Si vous avez installé par winget : winget upgrade " + PAQUET_WINGET);
    }

    /// Le conseil pour le système sur lequel on tourne.
    ///
    /// La lecture de la propriété est isolée ici, comme [fr.univ_amu.iut.commun.model.ConfigurationAmorcage]
    /// le fait pour ses dossiers : la règle reste **pure et testable**, seule la frontière lit le
    /// système.
    public static Optional<String> pourCeSysteme() {
        return pour(System.getProperty("os.name", ""));
    }
}
