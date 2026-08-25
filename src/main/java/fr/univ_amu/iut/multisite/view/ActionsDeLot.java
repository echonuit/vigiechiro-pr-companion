package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.model.ActionGroupee;
import java.util.Objects;
import java.util.Optional;

/// Les actions applicables à une **sélection** de passages (#2357), regroupées en objet-paramètre
/// (#2483) : trois paramètres du même type qui ne se distinguent que par leur **ordre** se seraient
/// échangés sans que rien ne rougisse - « préparer » lancerait un téléversement, et le compte rendu
/// porterait le bon libellé pour la mauvaise action.
///
/// **Chacune est optionnelle**, et ce n'est pas une précaution : les features qui possèdent ces
/// gestes sont **désactivables**, et une version qui les exigeait faisait échouer l'injecteur entier
/// dès qu'on coupait `lot`. Absente, l'entrée de menu **disparaît** plutôt que de rester grisée -
/// un item qui ne peut rien faire vaut moins qu'un item absent.
public record ActionsDeLot(
        Optional<ActionGroupee> preparerDepot,
        Optional<ActionGroupee> televerser,
        Optional<ActionGroupee> importerResultats,
        Optional<ActionGroupee> declencherCalcul) {

    public ActionsDeLot {
        Objects.requireNonNull(preparerDepot, "preparerDepot");
        Objects.requireNonNull(televerser, "televerser");
        Objects.requireNonNull(importerResultats, "importerResultats");
        Objects.requireNonNull(declencherCalcul, "declencherCalcul");
    }
}
