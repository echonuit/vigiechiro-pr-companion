package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyEvent;

/// **Revue au clavier** de la table audio (#478) : enchaîner des centaines de cris sans souris.
///
/// - `Entrée` : valider l'observation sélectionnée.
/// - `R` : basculer l'archivage en référence.
/// - `D` : basculer le drapeau **douteux** (« à repasser », #160) de l'observation sélectionnée.
/// - `1` / `2` / `3` (pavé numérique compris) : déclarer la **certitude observateur** (#1139) de la
///   sélection : Sûr / Probable / Possible (saisie explicite, cf. [MenuCertitude]).
/// - `N` : aller à la **prochaine « À revoir »** (statut non touché), en **bouclant** en fin de liste.
/// - `↑` / `↓` : navigation native du [TableView] (observation précédente / suivante), qui déclenche
///   l'écoute automatique via la sélection — rien à recâbler ici.
///
/// Le handler est posé **sur la table** : les touches ne sont donc captées que lorsque la table a le focus,
/// ce qui **respecte la saisie** dans les champs (recherche, sélecteur de taxon, éditeur de commentaire) sans
/// garde explicite. Sorti du controller (pur câblage, seuil de cohésion PMD), comme [LecteurAudio].
final class RevueClavier {

    private RevueClavier() {}

    /// Installe les raccourcis de revue sur `table` : Entrée / R passent par `actions` (unitaire si une seule
    /// ligne, **en lot** si plusieurs, #479) ; N (prochaine « À revoir ») pilote la sélection de la table via
    /// `viewModel`. Documente les touches via l'**aide d'accessibilité** (lecteurs d'écran).
    static void installer(
            TableView<LigneObservationAudio> table, AudioViewModel viewModel, ActionsSelectionAudio actions) {
        table.setOnKeyPressed(evenement -> traiter(evenement, table, viewModel, actions));
        table.setAccessibleHelp("Revue au clavier : Entrée valide la sélection, R bascule l'archivage en"
                + " référence, D bascule le drapeau douteux, 1, 2 et 3 déclarent la certitude (Sûr,"
                + " Probable, Possible), N va à la prochaine « À revoir » ; les flèches haut et bas"
                + " naviguent.");
    }

    private static void traiter(
            KeyEvent evenement,
            TableView<LigneObservationAudio> table,
            AudioViewModel vm,
            ActionsSelectionAudio actions) {
        switch (evenement.getCode()) {
            case ENTER -> {
                actions.valider();
                evenement.consume();
            }
            case R -> {
                actions.basculerReference();
                evenement.consume();
            }
            case D -> {
                actions.basculerDouteux();
                evenement.consume();
            }
            case DIGIT1, NUMPAD1 -> {
                actions.poserCertitude(CertitudeObservateur.SUR);
                evenement.consume();
            }
            case DIGIT2, NUMPAD2 -> {
                actions.poserCertitude(CertitudeObservateur.PROBABLE);
                evenement.consume();
            }
            case DIGIT3, NUMPAD3 -> {
                actions.poserCertitude(CertitudeObservateur.POSSIBLE);
                evenement.consume();
            }
            case N -> {
                allerProchaineARevoir(table, vm);
                evenement.consume();
            }
            default -> {
                // ↑ / ↓ et autres : laissés au TableView (navigation native + écoute automatique).
            }
        }
    }

    /// Sélectionne (**seule**, `clearAndSelect`) et fait défiler jusqu'à la prochaine observation **« À
    /// revoir »** après la sélection courante ; ne fait rien s'il n'y en a aucune.
    private static void allerProchaineARevoir(TableView<LigneObservationAudio> table, AudioViewModel vm) {
        int depart = table.getItems().indexOf(vm.selectionProperty().get());
        int cible = indexProchaineARevoir(table.getItems(), depart);
        if (cible >= 0) {
            table.getSelectionModel().clearAndSelect(cible);
            table.scrollTo(cible);
        }
    }

    /// Index de la prochaine observation de statut [StatutObservation#NON_TOUCHEE] **après** `depart`, en
    /// **bouclant** (recherche circulaire sur toute la liste, hors `depart` lui-même), ou `-1` si aucune. Un
    /// `depart` négatif (pas de sélection) démarre la recherche au début. Fonction pure (testable sans IHM).
    static int indexProchaineARevoir(List<LigneObservationAudio> lignes, int depart) {
        int n = lignes.size();
        for (int pas = 1; pas <= n; pas++) {
            int i = Math.floorMod(depart + pas, n);
            if (lignes.get(i).statut() == StatutObservation.NON_TOUCHEE) {
                return i;
            }
        }
        return -1;
    }
}
