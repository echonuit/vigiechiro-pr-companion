package fr.univ_amu.iut.validation.view;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.OuvrirNonIdentifies;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import java.util.Objects;

/// Implémentation du contrat socle [OuvrirNonIdentifies] : ouvre la vue « Sons & validation » sur les
/// **séquences non identifiées** d'un passage (source [SourceObservations.NonIdentifies]) en déléguant au
/// contrat socle [OuvrirAudio].
///
/// Voisine de [NavigationValidation] (même famille « ouvrir l'audio pour un passage ») : cette feature ne
/// dépend que du contrat socle `OuvrirAudio` (commun), pas de `audio.view` (le graphe de slices reste
/// acyclique). Bindée par `ValidationModule`.
@Singleton
public class NavigationNonIdentifies implements OuvrirNonIdentifies {

    private final OuvrirAudio ouvrirAudio;

    @Inject
    public NavigationNonIdentifies(OuvrirAudio ouvrirAudio) {
        this.ouvrirAudio = Objects.requireNonNull(ouvrirAudio, "ouvrirAudio");
    }

    @Override
    public void ouvrir(ContextePassage passage) {
        ouvrirAudio.ouvrir(new SourceObservations.NonIdentifies(passage));
    }
}
