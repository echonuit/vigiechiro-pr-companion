package fr.univ_amu.iut.commun.view;

/// Contrat **optionnel** d'un écran central : suivre la **donnée**, et pas seulement la navigation.
///
/// Il **complète** [RafraichirAuRetour] sans le remplacer, les deux ne couvrant pas les mêmes
/// écritures : le retour voit les `update` (un verdict, un dépôt) au moment où l'on revient sur
/// l'écran ; la révision voit les `insert` / `delete` **pendant** qu'on le regarde - un import, une
/// synchronisation lancée depuis le menu ☰, une restauration, qui ne provoquent aucun retour de
/// navigation et laisseraient donc l'écran sur des chiffres périmés. Un écran d'inventaire déclare
/// les deux.
///
/// ## Pourquoi c'est le [Navigateur] qui pose et rend l'abonnement
///
/// Un abonnement posé par l'écran lui-même **fuit** dès qu'il n'est pas rendu : `RevisionDonnees` est
/// un singleton, un écran ne l'est pas, et un abonnement survivant fait recharger une vue que plus
/// personne ne regarde.
///
/// Ce n'est donc pas un travail d'écran, c'est un cycle de vie, et le `Navigateur` porte déjà celui des
/// quatre autres contrats. Il pose l'abonnement quand l'étape entre dans l'historique et le rend quand
/// elle en sort, **au même endroit** que [AuDepartEcran]. Un écran qui déclare ce contrat n'a donc plus
/// besoin de connaître [fr.univ_amu.iut.commun.viewmodel.RevisionDonnees] : ni champ, ni écouteur, ni
/// constructeur à rallonger.
///
/// L'abonnement est posé **par vue**, pas par étape : `actualiserLibelleCourant` (#1213) remplace
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
