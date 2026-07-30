package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.TexteCompteRenduChiffre;
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

    /// Rend compte **en chiffres** : une ventilation qui se voit, des motifs regroupés, des mentions.
    ///
    /// ## Pourquoi une méthode par défaut, et non un second contrat
    ///
    /// Le port était `@FunctionalInterface`, et vingt-huit appels s'appuient sur sa forme textuelle. Une
    /// seconde méthode **abstraite** aurait cassé les deux. Le défaut préserve l'existant sans rien
    /// demander aux implémentations, comme `SuiviReprise.renonce()` l'avait fait pour porter le
    /// renoncement jusqu'au réessai sans faire grossir l'arité.
    ///
    /// ## Pourquoi le défaut se replie sur le texte
    ///
    /// Une implémentation qui n'affiche que du texte ne doit pas **cesser de rendre compte** parce qu'on
    /// lui a donné plus riche. Le repli passe par [TexteCompteRenduChiffre], qui garde les nombres et
    /// leurs pourcentages : ce qu'il perd est la proportion **vue**, pas la proportion.
    ///
    /// C'est aussi ce qui garde les doubles de test existants utilisables : un double écrit comme une
    /// lambda continue de capturer une chaîne, et celui qui veut éprouver les chiffres redéfinit cette
    /// méthode-ci.
    default void notifier(NiveauNotification niveau, String entete, CompteRenduChiffre compteRendu) {
        notifier(niveau, entete, TexteCompteRenduChiffre.rendre(compteRendu));
    }
}
