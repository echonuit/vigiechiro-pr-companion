package fr.univ_amu.iut.lot.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.lot.model.CompacteurDepot;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.lot.view.NavigationLot;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import fr.univ_amu.iut.lot.viewmodel.OngletReglagesDepot;
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

    /// Identité de la feature. `OPTIONNELLE` (désactivable) : son contrat `OuvrirLot` est neutralisé chez
    /// son consommateur (PassageController l'injecte en `Optional` et masque la carte si absent).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("lot", "Lots de dépôt", Categorie.OPTIONNELLE);
    }

    /// Fournit le contrat de navigation socle [OuvrirLot] : M-Passage l'injecte pour ouvrir la
    /// préparation/dépôt sans dépendre de la vue de cette feature (graphe de slices acyclique).
    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), OuvrirLot.class).setBinding().to(NavigationLot.class);
        // Dépôt VigieChiro (#142) en liaison **optionnelle** : déclaré ici (défaut absent) pour que les
        // injecteurs partiels de la feature `lot` — notamment `CaptureLot`, sans `ConnexionModule` donc sans
        // client HTTP — résolvent `Optional<DepotVigieChiro>` à vide. La liaison réelle est posée par
        // `DepotVigieChiroModule` (chargé seulement dans l'injecteur applicatif complet).
        OptionalBinder.newOptionalBinder(binder(), DepotVigieChiro.class);
        // Suivi du traitement serveur (#1263) : même montage que le dépôt ci-dessus. Déclaré ici (et pas
        // seulement dans `CommunModule`) pour que les injecteurs partiels de `lot` — capture, tests de vue —
        // résolvent `Optional<SuiviTraitement>` à vide sans binding manquant. La liaison réelle est posée par
        // `ConnexionModule`, où vit le client HTTP.
        OptionalBinder.newOptionalBinder(binder(), SuiviTraitement.class);
        // Onglet « Dépôt » de l’écran Réglages (#1047) : plafond des archives.
        ongletReglages(OngletReglagesDepot.class);
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

    /// Plafond (octets) des archives de dépôt (#110, #1047), par priorité : propriété système
    /// `vigiechiro.depot.taille-max-mo` (tests/outils), sinon le réglage persisté de l’écran
    /// Réglages, sinon 700 Mo (contrainte plateforme). Relu à **chaque génération** d’archives
    /// (fournisseur dans [ServiceLot]) : un changement de réglage s’applique sans redémarrage.
    static long plafondArchiveOctets(Reglages reglages) {
        String surcharge = System.getProperty("vigiechiro.depot.taille-max-mo");
        long plafondMo = surcharge != null && !surcharge.isBlank()
                ? Long.parseLong(surcharge.trim())
                : reglages.lireEntier(OngletReglagesDepot.CLE_TAILLE_MAX, OngletReglagesDepot.DEFAUT_TAILLE_MAX_MO);
        return plafondMo * 1000 * 1000;
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
            Reglages reglages,
            DepotUniteDao depotUnites) {
        return new ServiceLot(
                passageDao,
                sessionDao,
                sequenceDao,
                verification,
                moteurWorkflow,
                horloge,
                () -> new CompacteurDepot(plafondArchiveOctets(reglages)),
                depotUnites);
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
