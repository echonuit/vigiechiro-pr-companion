package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import java.util.Objects;

/// Appuis « socle » de [SonsValidationController] regroupés en objet-paramètre (patron `Campagne*`) :
/// la persistance des préférences de vue (vues mémorisées, disposition des colonnes) et l'exécuteur de
/// tâches hors fil JavaFX (#1214). Garde le constructeur du contrôleur sous le plafond
/// `ExcessiveParameterList` tout en lui donnant accès à l'occupation d'écran (#1014).
final class AppuisAudio {

    private final DepotVues depotVues;
    private final DepotDispositionColonnes depotColonnes;
    private final ExecuteurTache executeur;
    private final EspecesPrioritaires especesPrioritaires;

    @Inject
    AppuisAudio(
            DepotVues depotVues,
            DepotDispositionColonnes depotColonnes,
            ExecuteurTache executeur,
            EspecesPrioritaires especesPrioritaires) {
        this.depotVues = Objects.requireNonNull(depotVues, "depotVues");
        this.depotColonnes = Objects.requireNonNull(depotColonnes, "depotColonnes");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.especesPrioritaires = Objects.requireNonNull(especesPrioritaires, "especesPrioritaires");
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

    /// Le référentiel des **espèces à enjeu** (#2353). Passe par cet objet-paramètre plutôt que par un
    /// douzième paramètre du contrôleur : c'est exactement ce pour quoi il existe.
    EspecesPrioritaires especesPrioritaires() {
        return especesPrioritaires;
    }
}
