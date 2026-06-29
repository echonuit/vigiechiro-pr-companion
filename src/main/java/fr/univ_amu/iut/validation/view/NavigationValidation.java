package fr.univ_amu.iut.validation.view;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import java.util.Objects;

/// Implémentation du contrat socle [OuvrirValidation] : ouvre la **validation Tadarida d'un passage**.
///
/// Depuis la vue audio unifiée (#audio), elle **délègue au contrat socle [OuvrirAudio]** avec une source
/// `ParPassage` : la validation d'un passage est désormais un cas particulier de l'écran « Sons &
/// validation » (table des observations du passage + écoute + valider/corriger/référence + import CSV /
/// export `_Vu`). Les appelants historiques (M-Passage, et l'analyse via la cible de focus) restent
/// inchangés : ils continuent d'appeler `OuvrirValidation`, qui ouvre maintenant l'écran unifié. L'ancien
/// écran `Validation.fxml` n'est plus chargé en production (il sera retiré au démantèlement).
///
/// `OuvrirValidation` reste bindé par `ValidationModule` ; cette feature ne dépend que du **contrat
/// socle** `OuvrirAudio` (commun), pas de `audio.view` (le graphe de slices reste acyclique).
@Singleton
public class NavigationValidation implements OuvrirValidation {

    private final OuvrirAudio ouvrirAudio;

    @Inject
    public NavigationValidation(OuvrirAudio ouvrirAudio) {
        this.ouvrirAudio = Objects.requireNonNull(ouvrirAudio, "ouvrirAudio");
    }

    @Override
    public void ouvrir(ContextePassage passage) {
        ouvrir(passage, null);
    }

    /// Ouvre la vue audio unifiée sur le passage `passage` (source `ParPassage`), en pré-sélectionnant
    /// l'observation `idObservationCible` si elle est fournie (arrivée depuis « Espèces & observations »
    /// droit sur une détection).
    @Override
    public void ouvrir(ContextePassage passage, Long idObservationCible) {
        ouvrirAudio.ouvrir(new SourceObservations.ParPassage(passage), idObservationCible);
    }
}
