package fr.univ_amu.iut.commun.api;

/// Issue d'un **lancement de traitement** (`POST /participations/#id/compute`) : ce que le serveur a fait
/// de notre demande, et ce qu'il faut en dire à l'utilisateur (#1261).
///
/// Auparavant le lancement rendait un simple `booléen` : un refus « un traitement est déjà en cours » (qui
/// est **bénin** : le serveur travaille, il n'y a rien à faire) était indiscernable d'une vraie panne. Le
/// message affiché s'achevait d'ailleurs sur un point d'interrogation (« déjà en cours ? »), aveu que le
/// code ne savait pas.
///
/// L'issue est déterminée par **l'état du traitement relu après un refus** (cf.
/// [ClientVigieChiro#lancerTraitement]), pas par le décryptage du message d'erreur : c'est l'état qui fait
/// foi.
///
/// @param issue ce qu'il est advenu de la demande
/// @param traitement état du traitement **relu** après coup, ou [Traitement#absent()] quand il n'a pas pu
///     l'être (demande acceptée, serveur injoignable)
/// @param détail précision technique (statut HTTP et corps du refus), `null` s'il n'y a rien à préciser
public record ResultatLancement(IssueLancement issue, Traitement traitement, String détail) {

    /// Le serveur a **accepté** : le traitement est planifié, les résultats arriveront après le calcul.
    public static ResultatLancement accepte() {
        return new ResultatLancement(IssueLancement.ACCEPTE, Traitement.absent(), null);
    }

    /// Le serveur a **refusé parce qu'il travaille déjà** sur cette participation (400 « Already », dans sa
    /// fenêtre de 24 h). Ce n'est pas un échec : il n'y a qu'à attendre.
    public static ResultatLancement dejaLance(Traitement traitement) {
        return new ResultatLancement(IssueLancement.DEJA_LANCE, traitement, null);
    }

    /// **Nous** avons refusé, sans rien demander au serveur : la participation a déjà été calculée, et la
    /// relancer détruirait les observations (cf. [IssueLancement#RELANCE_BLOQUEE]).
    public static ResultatLancement relanceBloquee(Traitement traitement) {
        return new ResultatLancement(IssueLancement.RELANCE_BLOQUEE, traitement, null);
    }

    /// Le serveur a **répondu non**, pour une autre raison (droits, participation inconnue, panne serveur).
    public static ResultatLancement refuse(int statut, String corps) {
        return new ResultatLancement(IssueLancement.REFUSE, Traitement.absent(), "HTTP " + statut + " " + corps);
    }

    /// Le serveur n'a **pas répondu** : hors ligne, non connecté, ou délai dépassé. On ne sait rien de
    /// l'état réel (le traitement a pu partir malgré tout).
    public static ResultatLancement injoignable() {
        return new ResultatLancement(IssueLancement.INJOIGNABLE, Traitement.absent(), null);
    }

    /// Le serveur travaille-t-il, à l'issue de cette demande ? Vrai qu'il vienne de l'accepter ou qu'il
    /// l'ait refusée parce qu'un calcul était déjà lancé : dans les deux cas, l'utilisateur n'a plus qu'à
    /// attendre. C'est la question que pose l'IHM (et le code de retour de la commande).
    public boolean traitementEnRoute() {
        return issue == IssueLancement.ACCEPTE || issue == IssueLancement.DEJA_LANCE;
    }
}
