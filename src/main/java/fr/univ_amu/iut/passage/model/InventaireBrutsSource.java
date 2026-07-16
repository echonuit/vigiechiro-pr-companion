package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.nio.file.Path;
import java.util.Optional;

/// Port d'**inventaire des bruts d'un passage reconstruit** (#1649, EPIC #1653), en amont de leur
/// hydratation (#1650, #1651).
///
/// Un passage reconstruit (#1305) ne porte qu'un placeholder à la place de ses originaux : ni empreinte,
/// ni **fréquence d'acquisition**. Or c'est cette fréquence, lue du **log** de l'enregistreur, qui
/// pilote la transformation à l'identique. Ce port lit le dossier désigné et en tire ce qu'il faut pour
/// régénérer : la fréquence, et le nom R6 de chaque brut trouvé.
///
/// Comme [RegenerationSequences], le port vit dans `passage` et son implémentation dans `importation`
/// (qui connaît le format du log et dépend déjà de `passage` : l'inverse serait un cycle,
/// `ArchitectureTest`). Il est **optionnel** : la feature « Importation » est désactivable, et sans elle
/// la voie « bruts » d'un passage reconstruit se refuse en le disant.
@FunctionalInterface
public interface InventaireBrutsSource {

    /// Inventorie les bruts du dossier désigné pour le passage de préfixe `prefixe`.
    ///
    /// Retourne `Optional.empty()` quand le dossier ne porte **pas de log exploitable** (donc pas de
    /// fréquence d'acquisition sûre) ou **aucun brut** : dans ces cas, rien ne peut être régénéré à
    /// l'identique, et il vaut mieux le dire (repli sur le compte rendu honnête, #1648) que d'inventer
    /// une fréquence et de fabriquer des tranches fausses.
    ///
    /// @param dossierBruts dossier désigné par l'utilisateur (exploré comme une carte SD ou une session)
    /// @param prefixe préfixe R6 de la session (relu du nom de son dossier), pour nommer les originaux
    Optional<InventaireBruts> inventorier(Path dossierBruts, Prefixe prefixe);
}
