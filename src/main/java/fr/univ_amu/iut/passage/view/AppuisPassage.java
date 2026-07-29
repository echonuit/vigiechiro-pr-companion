package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvrirSynthese;
import java.util.Objects;
import java.util.Optional;

/// Appuis « socle » de [PassageController] regroupés en objet-paramètre (patron `AppuisAudio`) :
/// l'exécuteur de tâches hors fil JavaFX (#1213), le portail VigieChiro (lien de participation) et
/// l'ouvreur de lien système. Garde le constructeur du contrôleur sous le plafond
/// `ExcessiveParameterList` tout en lui donnant accès à l'occupation d'écran (#1014). Public car
/// l'outil de capture (`passage.outils.CapturePassage`) construit le contrôleur à la main.
public final class AppuisPassage {

    private final ExecuteurTache executeur;
    private final PortailVigieChiro portail;
    private final OuvreurDeLien ouvreurDeLien;

    /// Entrée vers la **Synthèse de la nuit** (#2351). Passe par cet objet-paramètre plutôt que par un
    /// douzième paramètre du contrôleur — c'est exactement ce pour quoi il existe. `Optional` : la
    /// feature est conditionnable, et la carte se masque quand elle est coupée.
    private final Optional<OuvrirSynthese> ouvrirSynthese;

    @Inject
    public AppuisPassage(
            ExecuteurTache executeur,
            PortailVigieChiro portail,
            OuvreurDeLien ouvreurDeLien,
            Optional<OuvrirSynthese> ouvrirSynthese) {
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.portail = Objects.requireNonNull(portail, "portail");
        this.ouvreurDeLien = Objects.requireNonNull(ouvreurDeLien, "ouvreurDeLien");
        this.ouvrirSynthese = Objects.requireNonNull(ouvrirSynthese, "ouvrirSynthese");
    }

    ExecuteurTache executeur() {
        return executeur;
    }

    PortailVigieChiro portail() {
        return portail;
    }

    Optional<OuvrirSynthese> ouvrirSynthese() {
        return ouvrirSynthese;
    }

    OuvreurDeLien ouvreurDeLien() {
        return ouvreurDeLien;
    }
}
