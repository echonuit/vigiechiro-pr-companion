package fr.univ_amu.iut.maj.model;

import java.util.Locale;
import java.util.Optional;

/// Le geste à conseiller, en plus du téléchargement, pour passer à la version disponible (#3616).
/// **Fermer d'abord** (#3457) : l'installateur ne peut pas remplacer des fichiers qu'un processus
/// tient ouverts, et le conseil le dit en premier parce que c'est ce qui bloque. Companion ne se
/// ferme pas lui-même - son annonce ouvre une page web, et le MSI est trois clics plus loin.
///
/// **Deux gestes proposés, jamais un canal deviné** (#2109, #2213) : un MSI posé à la main et un
/// paquet winget s'installent au même endroit, et `winget list --id …` peut se tromper avec
/// assurance. On dit à qui le second s'adresse, et la phrase est rendue à l'identique par l'IHM et
/// par la CLI (parité de l'ADR 0014).
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
