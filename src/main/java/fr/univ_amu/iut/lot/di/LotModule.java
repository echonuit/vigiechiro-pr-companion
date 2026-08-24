package fr.univ_amu.iut.lot.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RearmementDepot;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.lot.model.CompacteurDepot;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.LancementCalculGroupe;
import fr.univ_amu.iut.lot.model.ModeDepot;
import fr.univ_amu.iut.lot.model.PreparationGroupee;
import fr.univ_amu.iut.lot.model.RearmementDepotUnites;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.TeleversementGroupe;
import fr.univ_amu.iut.lot.model.VerificationCoherence;
import fr.univ_amu.iut.lot.model.dao.DepotPlanDao;
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
        return new Fonctionnalite("lot", "Préparation du dépôt", Categorie.OPTIONNELLE);
    }

    /// Fournit le contrat de navigation socle [OuvrirLot] : M-Passage l'injecte pour ouvrir la
    /// préparation/dépôt sans dépendre de la vue de cette feature (graphe de slices acyclique).
    @Override
    protected void configure() {
        // Action(s) de lot (#2357) : la valeur du port optionnel déclaré par `MultisiteModule`.
        // Coupée, cette feature retire simplement son entrée du menu.
        OptionalBinder.newOptionalBinder(binder(), Key.get(ActionGroupee.class, Names.named("action.preparerDepot")))
                .setBinding()
                .to(Key.get(ActionGroupee.class, Names.named("action.preparerDepot.impl")));
        OptionalBinder.newOptionalBinder(binder(), Key.get(ActionGroupee.class, Names.named("action.televerser")))
                .setBinding()
                .to(Key.get(ActionGroupee.class, Names.named("action.televerser.impl")));
        OptionalBinder.newOptionalBinder(binder(), Key.get(ActionGroupee.class, Names.named("action.declencherCalcul")))
                .setBinding()
                .to(Key.get(ActionGroupee.class, Names.named("action.declencherCalcul.impl")));
        OptionalBinder.newOptionalBinder(binder(), OuvrirLot.class).setBinding().to(NavigationLot.class);
        // Rearmement d un depot refuse (#3689) : la feature qui tient la table pose l implementation du
        // port declare a vide par CommunModule. Sans elle, la connexion ne rearme rien.
        OptionalBinder.newOptionalBinder(binder(), RearmementDepot.class)
                .setBinding()
                .to(RearmementDepotUnites.class);
        // Dépôt VigieChiro (#142) en liaison **optionnelle** : déclaré ici (défaut absent) pour que les
        // injecteurs partiels de la feature `lot` (notamment `CaptureLot`, sans `ConnexionModule` donc sans
        // client HTTP) résolvent `Optional<DepotVigieChiro>` à vide. La liaison réelle est posée par
        // `DepotVigieChiroModule` (chargé seulement dans l'injecteur applicatif complet).
        OptionalBinder.newOptionalBinder(binder(), DepotVigieChiro.class);
        // Suivi du traitement serveur (#1263) : même montage que le dépôt ci-dessus. Déclaré ici (et pas
        // seulement dans `CommunModule`) pour que les injecteurs partiels de `lot`, capture, tests de vue :
        // résolvent `Optional<SuiviTraitement>` à vide sans binding manquant. La liaison réelle est posée par
        // `ConnexionModule`, où vit le client HTTP.
        OptionalBinder.newOptionalBinder(binder(), SuiviTraitement.class);
        // Onglet « Dépôt » de l'écran Réglages (#1047) : plafond des archives.
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
    /// `vigiechiro.depot.taille-max-mo` (tests/outils), sinon le réglage persisté de l'écran
    /// Réglages, sinon 700 Mo (contrainte plateforme). Relu à **chaque génération** d'archives
    /// (fournisseur dans [ServiceLot]) : un changement de réglage s'applique sans redémarrage.
    static long plafondArchiveOctets(Reglages reglages) {
        String surcharge = System.getProperty("vigiechiro.depot.taille-max-mo");
        long plafondMo = surcharge != null && !surcharge.isBlank()
                ? Long.parseLong(surcharge.trim())
                : reglages.lireEntier(OngletReglagesDepot.CLE_TAILLE_MAX, OngletReglagesDepot.DEFAUT_TAILLE_MAX_MO);
        return plafondMo * 1000 * 1000;
    }

    /// Mode de dépôt choisi (#1997), par priorité : propriété système `vigiechiro.depot.mode`
    /// (tests/outils), sinon le réglage persisté, sinon les archives ZIP. Relu à **chaque dépôt**
    /// (fournisseur dans [ServiceLot]), comme le plafond d'archive.
    static ModeDepot modeDepot(Reglages reglages) {
        String surcharge = System.getProperty("vigiechiro.depot.mode");
        if (surcharge != null && !surcharge.isBlank()) {
            return ModeDepot.parValeur(surcharge.trim());
        }
        return ModeDepot.parValeur(
                reglages.lireTexte(OngletReglagesDepot.CLE_MODE_DEPOT, ModeDepot.ARCHIVES_ZIP.valeur()));
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
            DepotUniteDao depotUnites,
            DepotPlanDao depotPlans) {
        return new ServiceLot(
                passageDao,
                sessionDao,
                sequenceDao,
                verification,
                moteurWorkflow,
                horloge,
                () -> new CompacteurDepot(plafondArchiveOctets(reglages)),
                () -> modeDepot(reglages),
                depotUnites,
                depotPlans);
    }

    /// ViewModel de M-Lot. **Non-singleton** (un VM frais par chargement FXML).
    /// Action groupée **« Préparer le dépôt »** (#2357, lot 3), exposée sous le port [ActionGroupee].
    ///
    /// C'est `lot` qui possède le geste, donc `lot` qui le fournit. La feature qui l'applique à une
    /// sélection (`multisite`) consomme le **port**, jamais [ServiceLot] : elle n'a pas à connaître le
    /// dépôt pour savoir enchaîner une action sur des passages.
    ///
    /// Nommée : les PR suivantes du lot 3 en apporteront d'autres (téléverser, importer les résultats,
    /// déclencher le calcul), et elles se distinguent par ce nom.
    @Provides
    @Singleton
    @Named("action.preparerDepot.impl")
    ActionGroupee fournirPreparationGroupee(ServiceLot service) {
        return new PreparationGroupee(service);
    }

    /// Action groupée **« Téléverser vers Vigie-Chiro »** (#2357, lot 3, PR 3/5).
    ///
    /// Elle relaie le jeton du lot au dépôt, qui le consulte avant chaque unité : c'est la seule action
    /// du lot dont l'interruption laisse un état **nommé et reprenable** (« Dépôt en cours » + plan
    /// persisté), donc la seule qui ait le droit de s'arrêter au milieu d'un passage.
    /// Action groupée **« Déclencher le calcul »** (#2357, lot 3, PR 5/5).
    ///
    /// Elle ne force **jamais** : relancer un calcul détruit les observations d'une nuit déposée en ZIP.
    @Provides
    @Singleton
    @Named("action.declencherCalcul.impl")
    ActionGroupee fournirLancementCalculGroupe(Optional<DepotVigieChiro> depot) {
        return new LancementCalculGroupe(depot);
    }

    @Provides
    @Singleton
    @Named("action.televerser.impl")
    ActionGroupee fournirTeleversementGroupe(ServiceLot service, Optional<DepotVigieChiro> depot) {
        return new TeleversementGroupe(service, depot);
    }

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
