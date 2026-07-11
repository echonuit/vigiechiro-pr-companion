package fr.univ_amu.iut.lot.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.lot.model.CompacteurDepot;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.lot.view.NavigationLot;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
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
import java.util.Optional;

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
public class LotModule extends ModuleDeFeature {

    /// Fournit le contrat de navigation socle [OuvrirLot] : M-Passage l'injecte pour ouvrir la
    /// préparation/dépôt sans dépendre de la vue de cette feature (graphe de slices acyclique).
    @Override
    protected void configure() {
        bind(OuvrirLot.class).to(NavigationLot.class);
        // Dépôt VigieChiro (#142) en liaison **optionnelle** : déclaré ici (défaut absent) pour que les
        // injecteurs partiels de la feature `lot` — notamment `CaptureLot`, sans `ConnexionModule` donc sans
        // client HTTP — résolvent `Optional<DepotVigieChiro>` à vide. La liaison réelle est posée par
        // `DepotVigieChiroModule` (chargé seulement dans l'injecteur applicatif complet).
        OptionalBinder.newOptionalBinder(binder(), DepotVigieChiro.class);
    }

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

    /// Compacteur d'archives de dépôt (#110). **Réglage applicatif** : le plafond (en Mo, base 1000) est
    /// surchargeable via la propriété système `vigiechiro.depot.taille-max-mo` (même mécanisme que
    /// `vigiechiro.workspace`) ; à défaut, 700 Mo (contrainte Tadarida).
    @Provides
    @Singleton
    CompacteurDepot fournirCompacteurDepot() {
        String surcharge = System.getProperty("vigiechiro.depot.taille-max-mo");
        if (surcharge == null || surcharge.isBlank()) {
            return new CompacteurDepot();
        }
        return new CompacteurDepot(Long.parseLong(surcharge.trim()) * 1000 * 1000);
    }

    @Provides
    @Singleton
    ServiceLot fournirServiceLot(
            PassageDao passageDao,
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            VerificationCoherence verification,
            MoteurWorkflowPassage moteurWorkflow,
            Horloge horloge,
            CompacteurDepot compacteur,
            DepotUniteDao depotUnites) {
        return new ServiceLot(
                passageDao, sessionDao, sequenceDao, verification, moteurWorkflow, horloge, compacteur, depotUnites);
    }

    /// ViewModel de M-Lot. **Non-singleton** (un VM frais par chargement FXML).
    @Provides
    LotViewModel fournirLotViewModel(ServiceLot service) {
        return new LotViewModel(service);
    }

    /// ViewModel du **téléversement VigieChiro** (#142), séparé de [LotViewModel] (concern distinct, et pour
    /// ne pas alourdir ce VM déjà volumineux). `depot` est vide dans les injecteurs partiels de capture
    /// (sans `connexion`) et présent dans l'application complète (cf. `DepotVigieChiroModule`).
    @Provides
    DepotViewModel fournirDepotViewModel(ServiceLot service, Optional<DepotVigieChiro> depot) {
        return new DepotViewModel(service, depot);
    }
}
