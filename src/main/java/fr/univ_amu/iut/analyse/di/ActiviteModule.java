package fr.univ_amu.iut.analyse.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import fr.univ_amu.iut.analyse.view.NavigationActivite;
import fr.univ_amu.iut.analyse.viewmodel.ActiviteViewModel;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.view.OuvrirActivite;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;

/// Second module de feature du paquet `analyse` (à côté d'[AnalyseModule]) : l'écran **Activité de la
/// nuit** (#2352, lot 2 du chantier #2348). Deux modules dans une même feature suivent le précédent de
/// `audio` (AudioModule, DiscussionModule…).
///
/// Assemble [ServiceActivite] sur deux sources déjà publiées par `validation` : [ProjectionsAudioDao]
/// (observations avec leur `heureCapture`) et [PlageNuitPassage] (fenêtre nocturne au point d'écoute) —
/// `analyse → validation` est déjà une arête autorisée, aucune nouvelle dépendance. Fournit aussi le
/// [ActiviteViewModel] (non-singleton) et le contrat socle [OuvrirActivite] (implémenté par
/// [NavigationActivite]), que `passage` injecte pour ouvrir l'écran sans dépendre de son `view`.
public class ActiviteModule extends ModuleDeFeature {

    /// Identité de la feature. `EXPERIMENTALE` (désactivable, **inactive par défaut**) le temps du chantier
    /// #2348 : la carte n'apparaît pas tant que l'écran n'est pas complet, si bien que les paliers
    /// intermédiaires se mergent sans exposer un écran à moitié fait. Passera `OPTIONNELLE` à la clôture.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("activite-nuit", "Activité de la nuit", Categorie.EXPERIMENTALE);
    }

    /// Fournit le contrat de navigation socle [OuvrirActivite] : M-Passage l'injecte pour ouvrir l'écran
    /// d'activité sans dépendre de cette feature (évite le cycle `passage ↔ analyse`).
    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), OuvrirActivite.class)
                .setBinding()
                .to(NavigationActivite.class);
    }

    /// ViewModel de M-Activite. **Non-singleton** (un VM frais par chargement FXML).
    @Provides
    ActiviteViewModel fournirActiviteViewModel(ServiceActivite service) {
        return new ActiviteViewModel(service);
    }

    @Provides
    @Singleton
    ServiceActivite fournirServiceActivite(ProjectionsAudioDao projections, PlageNuitPassage plageNuitPassage) {
        return new ServiceActivite(projections, plageNuitPassage);
    }
}
