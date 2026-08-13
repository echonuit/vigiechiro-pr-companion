package fr.univ_amu.iut.commun.view;

/// Contrat **optionnel** d'un écran central : suivre la **donnée**, et pas seulement la navigation.
///
/// ## Ce qu'il couvre, et pourquoi [RafraichirAuRetour] n'y suffisait pas
///
/// `RafraichirAuRetour` répond à « une sous-activité a travaillé pendant que j'étais masqué ». Il ne
/// dit rien de ce qui arrive **pendant** qu'on regarde : un import, une synchronisation lancée depuis
/// le menu ☰, une restauration. Ces écritures-là ne provoquent aucun retour de navigation, donc aucun
/// rechargement, et l'écran reste sur des chiffres périmés sous les yeux de l'utilisateur.
///
/// Les deux contrats **coexistent** parce qu'ils ne couvrent pas les mêmes écritures : le retour voit
/// les `update` (un verdict, un dépôt), la révision voit les `insert` / `delete`. Un écran d'inventaire
/// déclare donc les deux.
///
/// ## Pourquoi c'est le [Navigateur] qui pose et rend l'abonnement
///
/// Les cinq premiers écrans à suivre la révision l'ont fait chacun de leur côté : un champ
/// `ChangeListener`, un `addListener` dans `initialize()`, un `removeListener` dans `auDepartEcran()`.
/// Trois lignes, cinq fois, dont **une seule** empêchait une fuite - `RevisionDonnees` est un
/// singleton, un écran ne l'est pas, et un abonnement non rendu fait recharger une vue que plus
/// personne ne regarde.
///
/// Ce n'est pas un travail d'écran : c'est un cycle de vie, et le `Navigateur` porte déjà celui des
/// quatre autres contrats. Il pose l'abonnement quand l'étape entre dans l'historique et le rend quand
/// elle en sort, **au même endroit** que [AuDepartEcran]. Un écran qui déclare ce contrat n'a donc plus
/// besoin de connaître [fr.univ_amu.iut.commun.viewmodel.RevisionDonnees] : ni champ, ni écouteur, ni
/// constructeur à rallonger.
///
/// ⚠️ L'abonnement est posé **par vue**, pas par étape : `actualiserLibelleCourant` (#1213) remplace
/// une étape par sa jumelle relibellée, et un `setAll` retire puis replace l'accueil. Dans les deux
/// cas la vue est la même et l'écran n'a **pas** été quitté.
public interface SuitLaRevision {

    /// Recharge les données de l'écran depuis la source de vérité, parce qu'une mutation
    /// **structurelle** vient d'être validée (un `insert` ou un `delete` sur l'inventaire affiché).
    ///
    /// Appelé par le [Navigateur] sur le fil JavaFX, y compris quand l'écran est **masqué** par une
    /// sous-activité empilée par-dessus : il reste dans l'historique, donc on le tient à jour pour
    /// qu'il soit juste au retour.
    void rafraichirDepuisLaDonnee();
}
