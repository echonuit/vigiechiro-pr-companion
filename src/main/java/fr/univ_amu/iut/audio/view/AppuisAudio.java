package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import java.util.Objects;

/// Appuis « socle » de [SonsValidationController] regroupés en objet-paramètre (patron `Campagne*`) :
/// la persistance des préférences de vue (vues mémorisées, disposition des colonnes) et l'exécuteur de
/// tâches hors fil JavaFX (#1214). Garde le constructeur du contrôleur sous le plafond
/// `ExcessiveParameterList` tout en lui donnant accès à l'occupation d'écran (#1014).
final class AppuisAudio {

    private final DepotVues depotVues;
    private final DepotDispositionColonnes depotColonnes;
    private final ExecuteurTache executeur;

    @Inject
    AppuisAudio(DepotVues depotVues, DepotDispositionColonnes depotColonnes, ExecuteurTache executeur) {
        this.depotVues = Objects.requireNonNull(depotVues, "depotVues");
        this.depotColonnes = Objects.requireNonNull(depotColonnes, "depotColonnes");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    DepotVues depotVues() {
        return depotVues;
    }

    DepotDispositionColonnes depotColonnes() {
        return depotColonnes;
    }

    ExecuteurTache executeur() {
        return executeur;
    }
}
