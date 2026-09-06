package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import java.util.Optional;

/// **Ce qu'on peut ouvrir depuis l'écran de validation des sons** : les quatre écrans qu'il sait
/// joindre.
///
/// Même raison que [fr.univ_amu.iut.passage.view.OuverturesDepuisLePassage], et le fichier de règles
/// PMD nommait déjà `SonsValidationController` parmi les quatre sites qui justifiaient de relever le
/// seuil de 10 à 11. Il y était donc à la limite, et le lot #5310 la lui a fait franchir.
///
/// **Un groupe par écran, et non un fourre-tour commun.** Ces deux écrans ne partagent que trois
/// ports sur onze : un type unique dirait « ce qu'on peut ouvrir » sans dire d'où, et chaque écran y
/// recevrait des ports qu'il n'ouvre pas.
public record OuverturesDepuisLaValidation(
        OuvrirSite site, OuvrirPassage passage, Optional<OuvrirAnalyse> analyse, OuvrirMultisite multisite) {

    @Inject
    public OuverturesDepuisLaValidation {}
}
