package fr.univ_amu.iut.commun.view;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/// Stratégie de **choix parmi plusieurs options** : « à quelle participation rattacher cette nuit ? »,
/// « ces positions déplacées : les enregistrer, ou les abandonner ? ». Quatrième contrat du socle avec
/// [Confirmateur] (le oui/non), [Notificateur] (le compte rendu) et [SelecteurFichier] (la désignation
/// d'un fichier), et il existe pour la **même** raison : un `showAndWait()` fige un test headless, donc
/// une action qui demande un choix n'est **pas jouable** tant que ce choix n'est pas remplaçable.
///
/// **« Annuler » n'est pas une option, c'est un renoncement.** C'est la clé de ce contrat, et elle
/// simplifie tout : un dialogue « Enregistrer / Abandonner / **Annuler** » n'a pas trois issues, il en a
/// **deux** - plus la possibilité de ne pas choisir. Exactement comme un sélecteur de fichier annulé rend
/// [Optional#empty()]. Un `Confirmateur` ne pouvait pas modéliser cela ; ce contrat, si.
///
/// La **présentation** reste au choix de l'appelant, car elle n'est pas la même selon le nombre et la
/// nature des options :
///
/// - [ChoixDansListe] : une liste déroulante, quand les options sont des **données** (les participations
///   d'un compte VigieChiro) ;
/// - [ChoixParBoutons] : un bouton par option, quand ce sont des **décisions** (enregistrer / abandonner)
///   - une liste déroulante y serait un recul d'ergonomie.
///
/// Les tests, eux, branchent un double qui répond ce qu'on lui dit - ou rien du tout, c'est-à-dire que
/// l'utilisateur renonce. Voir [DemandeurDeChoixModifiable] pour le porteur injectable.
///
/// @param <T> ce parmi quoi l'utilisateur choisit
@FunctionalInterface
public interface DemandeurDeChoix<T> {

    /// Demande à l'utilisateur de choisir **une** option.
    ///
    /// @param entete la question en une ligne (« Ce passage n'est pas encore rattaché… »)
    /// @param question le détail, ou l'invite du champ de choix
    /// @param options les options proposées, dans l'ordre d'affichage (jamais vide)
    /// @param libelle comment nommer chaque option pour l'utilisateur
    /// @return l'option choisie, ou **vide** si l'utilisateur **renonce**
    Optional<T> choisir(String entete, String question, List<T> options, Function<T, String> libelle);
}
