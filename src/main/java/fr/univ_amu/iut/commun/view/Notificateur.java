package fr.univ_amu.iut.commun.view;

/// Stratégie de **compte rendu d'une action** : « 4,2 Go libérés », « 56 séquences réactivées, 2
/// refusées », « purge impossible ». Contrat **neutre** du socle, jumeau de [Confirmateur].
///
/// Il existe pour la même raison que lui, et cette raison est allée à moitié du chemin jusqu'ici : le
/// **oui/non** avait été rendu injectable parce qu'un `Alert.showAndWait()` **fige** un test TestFX
/// headless, mais le **compte rendu** était resté un `Alert` en dur dans chaque action. Conséquence :
/// aucune action qui rend compte (archiver, réactiver, purger, sauvegarder) ne pouvait être testée
/// **jusqu'à son effet** - on ne testait que le grisage de son bouton. Le geste lui-même n'était
/// couvert nulle part.
///
/// L'application branche [NotificationDialogue] (vrai `Alert`) ; les tests un double qui **capture** ce
/// qui a été dit, ce qui permet enfin de vérifier que l'action a eu lieu **et** qu'elle l'a annoncé
/// honnêtement. Voir [NotificateurModifiable] pour le porteur injectable.
@FunctionalInterface
public interface Notificateur {

    /// Rend compte à l'utilisateur.
    ///
    /// @param niveau ce que la nouvelle vaut ([NiveauNotification#INFORMATION] : c'est fait ;
    ///     [NiveauNotification#AVERTISSEMENT] : c'est fait à moitié, ou pas fait)
    /// @param entete la nouvelle en une ligne (« Passage archivé »)
    /// @param message le détail, tel que l'utilisateur doit le lire
    void notifier(NiveauNotification niveau, String entete, String message);
}
