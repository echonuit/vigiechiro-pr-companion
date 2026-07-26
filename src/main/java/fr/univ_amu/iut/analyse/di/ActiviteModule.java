package fr.univ_amu.iut.analyse.di;

import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.analyse.view.NavigationActivite;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.view.OuvrirActivite;

/// Module de feature du paquet `analyse` qui **gate l'entrée** de l'écran **Activité de la nuit** (#2352,
/// lot 2 du chantier #2348), à côté d'[AnalyseModule]. Deux modules dans une même feature suivent le
/// précédent de `audio` (AudioModule, DiscussionModule…).
///
/// Il ne porte **que le point d'entrée** : le contrat socle [OuvrirActivite] (implémenté par
/// [NavigationActivite]), que `passage` injecte pour ouvrir l'écran sans dépendre de son `view`. La
/// **machinerie** de l'écran (le [...viewmodel.ActiviteViewModel] et le [...model.ServiceActivite]) est
/// fournie par [AnalyseModule] — toujours actif — et **non** ici : l'écran fait partie de la feature
/// `analyse`, et son FXML doit rester chargeable (garde-fou `ChargementFxmlTest`) même quand cette entrée
/// est coupée. Ce module ne gate donc que **l'accès**, jamais les composants.
public class ActiviteModule extends ModuleDeFeature {

    /// Identité de la feature. `EXPERIMENTALE` (désactivable, **inactive par défaut**) le temps du chantier
    /// #2348 : la carte n'apparaît pas tant que l'écran n'est pas complet, si bien que les paliers
    /// intermédiaires se mergent sans exposer un écran à moitié fait. Passera `OPTIONNELLE` à la clôture.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("activite-nuit", "Activité de la nuit", Categorie.EXPERIMENTALE);
    }

    /// Fournit le contrat de navigation socle [OuvrirActivite] : M-Passage l'injecte pour ouvrir l'écran
    /// d'activité sans dépendre de cette feature (évite le cycle `passage ↔ analyse`). Coupé, le binder de
    /// base de `PassageModule` laisse l'Optional vide et la carte est masquée.
    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), OuvrirActivite.class)
                .setBinding()
                .to(NavigationActivite.class);
    }
}
