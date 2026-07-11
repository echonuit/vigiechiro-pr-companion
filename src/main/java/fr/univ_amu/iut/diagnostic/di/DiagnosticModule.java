package fr.univ_amu.iut.diagnostic.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import fr.univ_amu.iut.diagnostic.view.NavigationDiagnostic;
import fr.univ_amu.iut.diagnostic.viewmodel.DiagnosticViewModel;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;

/// Module Guice de la feature `diagnostic` : assemble [ServiceDiagnostic] à partir des DAO
/// publiés par les features `passage` ([PassageDao], [SessionDao], [JournalDuCapteurDao],
/// [ReleveClimatiqueDao]) et `sites` ([PointDao]), plus l'[Horloge] du socle. Mêmes conventions
/// que `SitesModule`/`LotModule` : une méthode `@Provides @Singleton` câble un objet resté
/// **sans annotation d'injection**, les DAO inter-features étant reçus en lecture seule (sens
/// autorisé `diagnostic → passage` et `diagnostic → sites`, graphe acyclique).
///
/// **Intégration** : installé dans `RacineInjecteur`. Fournit aussi l'IHM de la feature : le
/// [DiagnosticViewModel] (non-singleton) et le contrat socle [OuvrirDiagnostic] (implémenté par
/// [NavigationDiagnostic]), que `passage` (M-Passage) injecte pour ouvrir le diagnostic sans
/// dépendre du `view` de cette feature.
public class DiagnosticModule extends ModuleDeFeature {

    /// Identité de la feature. `OPTIONNELLE` (désactivable) : son contrat `OuvrirDiagnostic` est neutralisé
    /// chez son consommateur (PassageController l'injecte en `Optional` et masque la carte si absent).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("diagnostic", "Diagnostic du capteur", Categorie.OPTIONNELLE);
    }

    /// Fournit le contrat de navigation socle [OuvrirDiagnostic] : M-Passage l'injecte pour ouvrir
    /// l'écran de diagnostic sans dépendre de cette feature (évite le cycle `passage ↔ diagnostic`).
    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), OuvrirDiagnostic.class)
                .setBinding()
                .to(NavigationDiagnostic.class);
    }

    /// ViewModel de M-Diagnostic. **Non-singleton** (un VM frais par chargement FXML).
    @Provides
    DiagnosticViewModel fournirDiagnosticViewModel(ServiceDiagnostic service) {
        return new DiagnosticViewModel(service);
    }

    @Provides
    @Singleton
    ServiceDiagnostic fournirServiceDiagnostic(
            PassageDao passageDao,
            SessionDao sessionDao,
            JournalDuCapteurDao journalDao,
            ReleveClimatiqueDao releveDao,
            PointDao pointDao,
            Horloge horloge) {
        return new ServiceDiagnostic(passageDao, sessionDao, journalDao, releveDao, pointDao, horloge);
    }
}
