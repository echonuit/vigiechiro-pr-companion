package fr.univ_amu.iut.analyse.di;

import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.analyse.view.ActiviteNuit;
import fr.univ_amu.iut.analyse.view.NavigationActivite;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.view.OuvrirActivite;

/// Module de feature du paquet `analyse` qui **conditionne l'accès** à l'écran **Activité de la nuit** (#2352,
/// lot 2 du chantier #2348), à côté d'[AnalyseModule]. Deux modules dans une même feature suivent le
/// précédent de `audio` (AudioModule, DiscussionModule…).
///
/// Il ne porte **que les points d'entrée** : le contrat socle [OuvrirActivite] (implémenté par
/// [NavigationActivite]), que `passage` injecte pour ouvrir l'écran depuis un passage, et la carte
/// d'accueil transverse [ActiviteNuit] (prisme « Espèces & biodiversité »). La **machinerie** de l'écran
/// (le [...viewmodel.ActiviteViewModel] et le [...model.ServiceActivite]) est fournie par [AnalyseModule]
/// — toujours actif — et **non** ici : l'écran fait partie de la feature `analyse`, et son FXML doit
/// rester chargeable (garde-fou `ChargementFxmlTest`) même quand ces entrées sont coupées. Ce module ne
/// conditionne donc que **l'accès**, jamais les composants.
public class ActiviteModule extends ModuleDeFeature {

    /// Identité de la feature. `OPTIONNELLE` (désactivable, **active par défaut**) depuis la clôture du lot
    /// #2352 : l'écran est complet, il s'offre donc comme les autres. Elle a été `EXPERIMENTALE` le temps du
    /// chantier, ce qui a permis de fusionner les paliers intermédiaires sans exposer un écran à moitié
    /// fait.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("activite-nuit", "Activité de la nuit", Categorie.OPTIONNELLE);
    }

    /// Fournit le contrat de navigation socle [OuvrirActivite] : M-Passage l'injecte pour ouvrir l'écran
    /// d'activité sans dépendre de cette feature (évite le cycle `passage ↔ analyse`). Coupé, le binder de
    /// base de `PassageModule` laisse l'Optional vide et la carte est masquée.
    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), OuvrirActivite.class)
                .setBinding()
                .to(NavigationActivite.class);
        // Carte d'accueil transverse (prisme « Espèces & biodiversité ») : présente seulement quand la
        // feature est active, comme la carte du passage. Le socle ne connaît jamais cette feature.
        activite(ActiviteNuit.class);
    }
}
