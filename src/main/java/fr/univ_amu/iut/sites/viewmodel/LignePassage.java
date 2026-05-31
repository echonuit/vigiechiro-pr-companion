package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.Passage;
import java.util.Locale;

/// Données de présentation d'une ligne du tableau des passages (écran M-Site-detail).
///
/// Aplatit un [Passage] avec le code de son point d'écoute (résolu par le ViewModel via la
/// liste des points du site) pour alimenter directement les colonnes du `TableView`. Les
/// libellés affichables sont pré-calculés afin que les `cellValueFactory` de la vue restent
/// triviaux (un simple accès au champ), conformément à la séparation viewmodel/vue.
///
/// @param idPassage clé technique du passage (pour la navigation vers M-Passage)
/// @param date date d'enregistrement (ISO `AAAA-MM-JJ`)
/// @param codePoint code du point d'écoute (ex. `A1`)
/// @param numeroPassage n° de passage dans l'année, déjà rendu en texte
/// @param statut statut du workflow (porte la couleur du badge)
/// @param verdict verdict de vérification (`null` tant que non vérifié)
/// @param enregistreur libellé de l'enregistreur (ex. `PR 1925492`)
/// @param deposeLe date de dépôt, ou `—` si non déposé
public record LignePassage(
    Long idPassage,
    String date,
    String codePoint,
    String numeroPassage,
    StatutWorkflow statut,
    Verdict verdict,
    String enregistreur,
    String deposeLe) {

  /// Libellé du statut (toujours présent).
  public String statutLibelle() {
    return statut.libelle();
  }

  /// Libellé du verdict : `— à vérifier` tant qu'aucun verdict n'est posé.
  public String verdictLibelle() {
    return verdict == null ? "— à vérifier" : verdict.libelle();
  }

  /// Classe CSS du badge de statut, dérivée de l'énum (couleur jamais stockée). Ex.
  /// `badge-statut-transforme`.
  public String statutClasseCss() {
    return "badge-statut-" + statut.name().toLowerCase(Locale.ROOT);
  }

  /// Classe CSS du badge de verdict, dérivée de l'énum (`A_VERIFIER` si aucun verdict). Ex.
  /// `badge-verdict-ok`.
  public String verdictClasseCss() {
    Verdict effectif = verdict == null ? Verdict.A_VERIFIER : verdict;
    return "badge-verdict-" + effectif.name().toLowerCase(Locale.ROOT);
  }

  /// Construit une ligne à partir d'un passage et du code de son point d'écoute.
  public static LignePassage depuis(Passage passage, String codePoint) {
    return new LignePassage(
        passage.id(),
        passage.dateEnregistrement(),
        codePoint,
        Integer.toString(passage.numeroPassage()),
        passage.statutWorkflow(),
        passage.verdictVerification(),
        "PR " + passage.idEnregistreur(),
        passage.deposeLe() == null ? "—" : passage.deposeLe());
  }
}
