package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.view.carte.CarreGeo;
import fr.univ_amu.iut.commun.view.carte.CarteSites;
import fr.univ_amu.iut.commun.view.carte.DonneesCarte;
import fr.univ_amu.iut.commun.view.carte.EmpriseCarre;
import fr.univ_amu.iut.commun.view.carte.PointGeo;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.PointAgrege;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ToggleButton;

/// **Mode édition des positions** de la carte multi-sites (#154), extrait du [MultisiteController] pour le
/// garder petit et focalisé. Branche la carte, le toggle et le bouton « Enregistrer » sur la file de
/// déplacements en attente du ViewModel ([fr.univ_amu.iut.multisite.viewmodel.PositionsEnAttente]) :
///
/// - **glisser** un marqueur le **clampe à son carré** et met le point en attente (rien n'est écrit) ;
/// - le **toggle** entre/sort de l'édition ; sortir avec des changements non enregistrés **avertit** ;
/// - le **bouton** « Enregistrer » (visible en édition, actif s'il y a des changements) persiste tout.
///
/// Garde deux index reconstruits à chaque refresh de la carte : libellé de marqueur (`carré / code`) →
/// `idPoint`, et numéro de carré → emprise tracée (pour le clamp).
final class EditionPositionsCarte {

    private final CarteSites carte;
    private final MultisiteViewModel viewModel;
    private final ToggleButton toggle;
    private final Button boutonEnregistrer;

    private final Map<String, Long> idPointParLibelle = new HashMap<>();
    private final Map<String, EmpriseCarre> empriseParCarre = new HashMap<>();

    EditionPositionsCarte(
            CarteSites carte, MultisiteViewModel viewModel, ToggleButton toggle, Button boutonEnregistrer) {
        this.carte = carte;
        this.viewModel = viewModel;
        this.toggle = toggle;
        this.boutonEnregistrer = boutonEnregistrer;
    }

    /// Câble la carte et les boutons sur la file de déplacements en attente.
    void brancher() {
        carte.setContrainteDeplacement(this::clampAuCarre);
        carte.setOnPointDeplace((point, latitude, longitude) -> {
            Long idPoint = idPointParLibelle.get(point.libelle());
            if (idPoint != null) {
                viewModel.positionsEnAttente().deplacer(idPoint, latitude, longitude);
            }
        });
        boutonEnregistrer.visibleProperty().bind(toggle.selectedProperty());
        boutonEnregistrer.managedProperty().bind(toggle.selectedProperty());
        boutonEnregistrer
                .disableProperty()
                .bind(viewModel.positionsEnAttente().modifieesProperty().not());
    }

    /// Reconstruit les index d'édition après un refresh de la carte (libellé → idPoint, carré → emprise).
    void indexer(DonneesCarte donnees, List<CarreAgrege> carres) {
        idPointParLibelle.clear();
        for (CarreAgrege carre : carres) {
            for (PointAgrege point : carre.points()) {
                if (point.idPoint() != null) {
                    idPointParLibelle.put(carre.numeroCarre() + " / " + point.codePoint(), point.idPoint());
                }
            }
        }
        empriseParCarre.clear();
        for (CarreGeo carre : donnees.carres()) {
            empriseParCarre.put(carre.numeroCarre(), carre.emprise());
        }
    }

    /// Toggle « ✎ Éditer les positions » : entre en édition, ou en sort (en avertissant si des
    /// déplacements sont en attente).
    void basculer() {
        if (toggle.isSelected()) {
            carte.setEditionActive(true);
        } else if (!viewModel.positionsEnAttente().aDesEnAttente()) {
            carte.setEditionActive(false);
        } else {
            confirmerSortie();
        }
    }

    /// Bouton « 💾 Enregistrer les positions » : persiste les déplacements en attente (on reste en édition).
    void enregistrer() {
        viewModel.positionsEnAttente().enregistrer();
    }

    private void confirmerSortie() {
        Alert alerte = new Alert(Alert.AlertType.CONFIRMATION);
        alerte.setTitle("Positions modifiées");
        alerte.setHeaderText("Des points ont été déplacés sans être enregistrés.");
        alerte.setContentText("Enregistrer les nouvelles positions ?");
        ButtonType enregistrer = new ButtonType("Enregistrer", ButtonBar.ButtonData.YES);
        ButtonType abandonner = new ButtonType("Abandonner", ButtonBar.ButtonData.NO);
        ButtonType annuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alerte.getButtonTypes().setAll(enregistrer, abandonner, annuler);
        ButtonType choix = alerte.showAndWait().orElse(annuler);
        if (choix == enregistrer) {
            viewModel.positionsEnAttente().enregistrer();
            carte.setEditionActive(false);
        } else if (choix == abandonner) {
            viewModel.positionsEnAttente().annuler();
            carte.setEditionActive(false);
        } else {
            toggle.setSelected(true); // Annuler → on reste en édition
        }
    }

    /// Clampe une position proposée aux bornes de l'emprise du carré du `point` (le marqueur s'arrête au
    /// bord de sa maille). Sans emprise connue, la position passe inchangée.
    private double[] clampAuCarre(PointGeo point, double latitude, double longitude) {
        EmpriseCarre emprise = empriseParCarre.get(numeroCarreDe(point));
        if (emprise == null) {
            return new double[] {latitude, longitude};
        }
        double lat = Math.max(emprise.latMin(), Math.min(emprise.latMax(), latitude));
        double lon = Math.max(emprise.lonMin(), Math.min(emprise.lonMax(), longitude));
        return new double[] {lat, lon};
    }

    /// Numéro de carré porté par le libellé d'un marqueur (`carré / code`).
    private static String numeroCarreDe(PointGeo point) {
        String libelle = point.libelle();
        int separateur = libelle.indexOf(" / ");
        return separateur < 0 ? libelle : libelle.substring(0, separateur);
    }
}
