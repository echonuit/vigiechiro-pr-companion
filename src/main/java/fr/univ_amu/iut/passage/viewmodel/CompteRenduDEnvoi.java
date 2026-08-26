package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.passage.model.EnvoiParticipation;
import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel.Envoi;

/// Ce que la modale **dit** d'un envoi de métadonnées, à partir de ce que le modèle en a rapporté.
///
/// Extrait de [RattachementViewModel] au moment où l'envoi a gagné une troisième issue (#4552) : la
/// classe portait déjà trop, et le plafond de qualité l'a signalé pendant que la branche s'écrivait.
final class CompteRenduDEnvoi {

    private static final String VERS = " -> ";

    private CompteRenduDEnvoi() {}

    /// Traduit l'issue d'un envoi en ce que l'utilisateur en lit, et en ce que la modale en fait.
    ///
    /// Les trois cas ne se disent pas de la même façon : la plateforme a refusé, nous avons renoncé,
    /// ou l'envoi est parti.
    static Envoi de(EnvoiParticipation issue) {
        if (!(issue instanceof EnvoiParticipation.Ecrit envoi)) {
            // #4552 : nous renonçons, la plateforme n'a rien refusé. Lui attribuer ce geste ferait
            // chercher la panne du mauvais côté.
            String renoncement = "La nuit a changé sur Vigie-Chiro depuis que cette fiche est ouverte."
                    + " Rien n'a été envoyé, pour ne pas effacer ce qu'un autre poste y a écrit."
                    + " Rouvrez la fiche pour repartir de l'état à jour.";
            // Le réalignement a eu lieu et il est écrit en base, même si rien n'est parti. Le taire ici
            // corrigerait la nuit de l'utilisateur dans son dos, et la CLI le dit déjà.
            return new Envoi.Empeche(issue.realignement()
                    .map(realignement -> renoncement + " " + phrase(realignement))
                    .orElse(renoncement));
        }
        // Le succès se lit sur l'échec, pas sur la présence d'un identifiant : un PATCH ne crée rien.
        if (!envoi.ecriture().estReussie()) {
            return new Envoi.Empeche(
                    "Vigie-Chiro a refusé l'envoi : " + envoi.ecriture().echec());
        }
        // #1885 : un réalignement a modifié les heures de la nuit. Le taire reviendrait à corriger sa
        // saisie dans son dos. Le témoin <Envoi> est nécessaire : sans lui, l'inférence retient `ALire`
        // et refuse le `Abouti` du repli, alors que les deux sont des `Envoi`.
        return envoi.realignement()
                .<Envoi>map(
                        realignement -> new Envoi.ALire("Métadonnées envoyées à Vigie-Chiro. " + phrase(realignement)))
                .orElseGet(() -> new Envoi.Abouti("Métadonnées envoyées à Vigie-Chiro."));
    }

    private static String phrase(EnvoiParticipation.Realignement realignement) {
        return "Les heures de la nuit ont été réalignées sur ses enregistrements : "
                + realignement.debutAvant() + VERS + realignement.debutApres() + " (début), "
                + realignement.finAvant() + VERS + realignement.finApres() + " (fin).";
    }
}
