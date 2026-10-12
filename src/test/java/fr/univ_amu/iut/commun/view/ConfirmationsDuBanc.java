package fr.univ_amu.iut.commun.view;

import java.util.Objects;

/// Pose le confirmateur d'un banc sur le navigateur, à la place de la modale de production.
///
/// Le confirmateur de sortie d'écran ne se remplace que depuis ce paquet. Un banc qui mesure l'IHM
/// (#105) ne doit jamais attendre une modale : un clic humain entrerait dans ses chiffres. Il pose donc un
/// confirmateur qui note la question et refuse, et c'est lui qui rougit.
public final class ConfirmationsDuBanc {

    private ConfirmationsDuBanc() {}

    /// Fait répondre `confirmateur` à toute confirmation de sortie d'écran de `navigateur`.
    public static void poser(Navigateur navigateur, Confirmateur confirmateur) {
        Objects.requireNonNull(navigateur, "navigateur").setConfirmateurQuitter(confirmateur);
    }
}
