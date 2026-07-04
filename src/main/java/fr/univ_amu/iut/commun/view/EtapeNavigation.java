package fr.univ_amu.iut.commun.view;

import java.util.Objects;
import javafx.scene.Parent;

/// Une étape de l'**historique de navigation** du [Navigateur] : un écran central visité, conservé
/// vivant pour préserver son état au retour.
///
/// Le `controleur` (renvoyé par `FXMLLoader.getController()`) est mémorisé tel quel pour en dériver, par
/// `instanceof`, les contrats optionnels de l'écran : [GardeQuitter] (saisie non enregistrée) et
/// [EmplacementNavigation] (fil d'Ariane hiérarchique). Il peut être `null` (accueil, écran de test…).
///
/// @param id identifiant logique de l'écran (ex. `accueil`, `sites`, `site-detail`, `passage`)
/// @param libelle libellé court pour le fil d'Ariane (ex. « Mes sites », « Carré 640380 »)
/// @param vue la vue centrale vivante (ré-affichée telle quelle au retour)
/// @param controleur le controller de l'écran, ou `null`
public record EtapeNavigation(String id, String libelle, Parent vue, Object controleur) {

    public EtapeNavigation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(libelle, "libelle");
        Objects.requireNonNull(vue, "vue");
    }

    /// La garde de saisie de l'écran, ou `null` s'il n'en déclare pas.
    public GardeQuitter garde() {
        return controleur instanceof GardeQuitter g ? g : null;
    }

    /// L'emplacement hiérarchique déclaré par l'écran (fil d'Ariane), ou `null`.
    public EmplacementNavigation emplacement() {
        return controleur instanceof EmplacementNavigation e ? e : null;
    }

    /// Le hook de rafraîchissement au retour déclaré par l'écran, ou `null` s'il n'en déclare pas.
    public RafraichirAuRetour rafraichirAuRetour() {
        return controleur instanceof RafraichirAuRetour r ? r : null;
    }

    /// Le hook de **départ d'écran** (#230) déclaré par l'écran, ou `null` s'il n'en déclare pas.
    public AuDepartEcran auDepartEcran() {
        return controleur instanceof AuDepartEcran d ? d : null;
    }

    /// Le **résumé de barre de statut** déclaré par l'écran, ou `null` s'il n'en déclare pas.
    public ResumeStatut resumeStatut() {
        return controleur instanceof ResumeStatut r ? r : null;
    }
}
