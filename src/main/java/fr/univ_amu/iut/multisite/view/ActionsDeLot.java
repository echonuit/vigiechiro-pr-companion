package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.model.ActionGroupee;
import java.util.Objects;
import java.util.Optional;

/// Les actions applicables à une **sélection** de passages (#2357, lot 3), regroupées.
///
/// Objet-paramètre (#2483) : chaque PR du lot en ajoute une, et l'écran les recevait une par une. Trois
/// paramètres du même type qui ne se distinguent que par leur **ordre** se seraient échangés sans que
/// rien ne rougisse - « préparer » lancerait un téléversement, et le compte rendu porterait le bon
/// libellé pour la mauvaise action.
///
/// ## Chacune est **optionnelle**, et ce n'est pas une précaution
///
/// Les features qui possèdent ces gestes sont **désactivables** : `lot` (préparer, téléverser) et
/// `import-vigiechiro` (importer les résultats). Une version qui les exigeait faisait échouer
/// l'injecteur entier dès qu'on coupait `lot` - défaut attrapé par le garde-fou de désactivation, pas
/// par un raisonnement.
///
/// Absente, l'entrée de menu correspondante **disparaît** plutôt que de rester grisée : « un item qui
/// ne peut rien faire ne vaut pas mieux qu'un item absent, il vaut moins » (patron déjà suivi pour
/// « Compléter une nuit récupérée » et « Relever l'état des analyses »).
public record ActionsDeLot(
        Optional<ActionGroupee> preparerDepot,
        Optional<ActionGroupee> televerser,
        Optional<ActionGroupee> importerResultats) {

    public ActionsDeLot {
        Objects.requireNonNull(preparerDepot, "preparerDepot");
        Objects.requireNonNull(televerser, "televerser");
        Objects.requireNonNull(importerResultats, "importerResultats");
    }
}
