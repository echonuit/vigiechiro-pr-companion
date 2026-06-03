package fr.univ_amu.iut.lot.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;

/// Module Guice de la feature `lot` : assemble le moteur de vérification et le service de
/// dépôt à partir des DAO publiés par les autres features (`sites`, `passage`) et de
/// l'[Horloge] du socle.
///
/// Même patron que `SitesModule` : des méthodes `@Provides @Singleton` câblent des objets
/// restés **sans annotation d'injection** (`VerificationCoherence`, `ServiceLot` sont de
/// simples objets Java instanciables à la main dans les tests). Les DAO inter-feature sont
/// reçus en lecture seule (sens autorisé `lot → sites` et `lot → passage`, graphe acyclique).
///
/// **Intégration** : ce module est installé dans `RacineInjecteur` (la racine de composition
/// de l'application), ce qui rend `ServiceLot` résoluble par l'injecteur applicatif. Le câblage
/// en isolation reste validé par `LotModuleTest` (injecteur local).
public class LotModule extends AbstractModule {

    @Provides
    @Singleton
    VerificationCoherence fournirVerificationCoherence(
            SiteDao siteDao,
            PointDao pointDao,
            SessionDao sessionDao,
            EnregistrementOriginalDao originalDao,
            SequenceDao sequenceDao,
            JournalDuCapteurDao journalDao,
            ReleveClimatiqueDao releveDao) {
        return new VerificationCoherence(
                siteDao, pointDao, sessionDao, originalDao, sequenceDao, journalDao, releveDao);
    }

    @Provides
    @Singleton
    ServiceLot fournirServiceLot(
            PassageDao passageDao,
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            VerificationCoherence verification,
            MoteurWorkflowPassage moteurWorkflow,
            Horloge horloge) {
        return new ServiceLot(passageDao, sessionDao, sequenceDao, verification, moteurWorkflow, horloge);
    }

    /// ViewModel de M-Lot. **Non-singleton** (un VM frais par chargement FXML).
    @Provides
    LotViewModel fournirLotViewModel(ServiceLot service) {
        return new LotViewModel(service);
    }
}
