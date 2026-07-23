package fr.univ_amu.iut.commun.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.api.ClientGbif;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.PreferenceSourceEspece;
import fr.univ_amu.iut.commun.model.RechercheGlobale;
import fr.univ_amu.iut.commun.model.SourceUniverselle;
import fr.univ_amu.iut.commun.model.SourceUniversellePreferee;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.DispositionColonnesDao;
import fr.univ_amu.iut.commun.model.dao.VueSauvegardeeDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ActionAPropos;
import fr.univ_amu.iut.commun.view.ActionMenu;
import fr.univ_amu.iut.commun.view.ActionOuvrirJournaux;
import fr.univ_amu.iut.commun.view.ActionOuvrirReglages;
import fr.univ_amu.iut.commun.view.ActionRestaurer;
import fr.univ_amu.iut.commun.view.ActionRestaurerComplet;
import fr.univ_amu.iut.commun.view.ActionSauvegarder;
import fr.univ_amu.iut.commun.view.ActionSauvegarderComplet;
import fr.univ_amu.iut.commun.view.ActionSourceEspece;
import fr.univ_amu.iut.commun.view.AnnonceChrome;
import fr.univ_amu.iut.commun.view.DescripteurReglage;
import fr.univ_amu.iut.commun.view.ExecuteurFiche;
import fr.univ_amu.iut.commun.view.ExecuteurFicheAsynchrone;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OngletReglages;
import fr.univ_amu.iut.commun.view.OngletReglagesEmplacements;
import fr.univ_amu.iut.commun.view.OngletReglagesFonctionnalites;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvreurDeLienSysteme;
import fr.univ_amu.iut.commun.view.ResolveurFiche;
import fr.univ_amu.iut.commun.view.ResolveurFicheGbif;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.OngletReglagesGeneral;
import java.util.List;

/// Module Guice du socle : fournit le [Workspace], la [SourceDeDonnees] et le socle IHM
/// (singletons).
///
/// Le workspace est par défaut `<Documents>/VigieChiro-Companion` (R21). Pour les tests
/// d'intégration ou une démo jetable, on peut le surcharger via la propriété système
/// `vigiechiro.workspace` (ex. `-Dvigiechiro.workspace=/tmp/vc`). Les tests unitaires des DAO,
/// eux, instancient directement `SourceDeDonnees` sur un `@TempDir` sans passer par
/// Guice.
public class CommunModule extends AbstractModule {

    @Override
    protected void configure() {
        // Socle IHM transverse : état de navigation observable + service de swap de la zone
        // centrale. Singletons pour que le chrome et toutes les features partagent la même
        // instance. Pas de @Provides : pas de logique de construction (constructeurs @Inject).
        bind(NavigationViewModel.class).in(Singleton.class);
        bind(Navigateur.class).in(Singleton.class);
        // Ouverture de liens externes (ex. coordonnées GPS -> OpenStreetMap). Singleton :
        // `App` y branche le HostServices une fois au démarrage (cf. App.start).
        bind(OuvreurDeLien.class).to(OuvreurDeLienSysteme.class).in(Singleton.class);

        // Préférence « source des fiches espèces » (#849) : singleton pour que le menu ☰ (qui la modifie)
        // et le constructeur de liens (qui la lit) partagent le même service persistant.
        bind(PreferenceSourceEspece.class).in(Singleton.class);
        // Fiche espèce (#922) : en production, résolution GBIF (recherche → fiche) hors du fil JavaFX.
        // Surchargent les défauts @ImplementedBy (identité + synchrone) réservés aux tests.
        bind(ExecuteurFiche.class).to(ExecuteurFicheAsynchrone.class).in(Singleton.class);
        bind(ExecuteurTache.class).to(ExecuteurTacheAsynchrone.class).in(Singleton.class);
        // Point d'extension « onglets de réglages » (#927) : `Set<OngletReglages>` est toujours
        // injectable (écran Réglages + menu ☰), chaque feature y ajoutant son onglet via son module.
        // Le socle contribue lui-même l'onglet « Général » (#928 : source des fiches espèces, puis
        // thème/daltonien).
        Multibinder.newSetBinder(binder(), OngletReglages.class).addBinding().to(OngletReglagesGeneral.class);
        // Onglet « Emplacements » (#1038) : où vivent le dossier de travail et la base. Personnalisé
        // (sélecteur de dossier + sonde + avis de redémarrage), donc en `commun.view` et non en viewmodel.
        Multibinder.newSetBinder(binder(), OngletReglages.class).addBinding().to(OngletReglagesEmplacements.class);
        // Onglet « Fonctionnalités » (#1057) : un interrupteur par feature désactivable, calculé depuis le
        // registre des fonctionnalités. Les features COEUR (socle) n'y figurent pas.
        Multibinder.newSetBinder(binder(), OngletReglages.class)
                .addBinding()
                .toInstance(new OngletReglagesFonctionnalites(descripteursFonctionnalites()));
        // Point d'extension « entrées du menu ☰ » (#930) : le MenuButton est bâti par le socle depuis
        // `Set<ActionMenu>`. Le socle contribue les entrées transverses (sauvegarde / restauration /
        // purge / source des fiches / réglages) ; les features en ajoutent via leur module (ex.
        // l'entrée « Connexion » vient de `connexion`, #931).
        // Annonces du chrome (#2109) : Multibinder déclaré VIDE ici, pour que le socle démarre
        // même si aucune feature n'en contribue - c'est le cas quand `maj` est désactivée.
        Multibinder.newSetBinder(binder(), AnnonceChrome.class);

        Multibinder<ActionMenu> actions = Multibinder.newSetBinder(binder(), ActionMenu.class);
        actions.addBinding().to(ActionSauvegarder.class);
        // Sauvegarde/restauration COMPLETES (base + audio, #1346) : le moteur existait depuis #1142 sans
        // aucun appelant. C'est pourtant la seule qui protege l'audio, que la plateforme ne rend pas.
        actions.addBinding().to(ActionSauvegarderComplet.class);
        actions.addBinding().to(ActionRestaurer.class);
        actions.addBinding().to(ActionRestaurerComplet.class);
        // « Ouvrir le dossier des journaux » (#1523) : accès direct aux logs pour joindre la trace d'un
        // incident à un signalement.
        actions.addBinding().to(ActionOuvrirJournaux.class);
        // « À propos » (#2108) : version, JDK, système et dossier de travail. Voisine des journaux
        // parce qu'on cherche les deux au même moment, pour renseigner un signalement.
        actions.addBinding().to(ActionAPropos.class);
        actions.addBinding().to(ActionSourceEspece.class);
        actions.addBinding().to(ActionOuvrirReglages.class);
        // Recherche globale du chrome (#144) : OptionalBinder VIDE (feature `recherche` désactivable, #1087).
        // `RechercheModule` fait `setBinding` quand elle est active ; sinon MainController masque la barre.
        OptionalBinder.newOptionalBinder(binder(), RechercheGlobale.class);
        // Suivi du traitement serveur (#1259) : OptionalBinder VIDE ici, car il exige le client HTTP —
        // absent des injecteurs de capture, qui assemblent l'application sans `connexion`. `ConnexionModule`
        // pose la liaison réelle. Les consommateurs (M-Lot, modale de rattachement, CLI) reçoivent donc un
        // Optional : hors connexion, le suivi est simplement indisponible, sans binding manquant.
        OptionalBinder.newOptionalBinder(binder(), SuiviTraitement.class);
        // Import des observations (#1264) : port déclaré À VIDE, implémenté par la feature `validation`.
        // M-Passage le consomme en Optional — sans connexion (ou feature désactivée), le bouton d'import
        // n'apparaît tout simplement pas.
        OptionalBinder.newOptionalBinder(binder(), ImportObservations.class);
    }

    /// Descripteurs de l'onglet « Fonctionnalités » : un booléen `feature.<id>.active` par feature
    /// **désactivable** (OPTIONNELLE / EXPERIMENTALE), défaut = état par défaut de sa [Categorie]. Les
    /// features EXPERIMENTALE sont suffixées « (expérimental) ».
    private static List<DescripteurReglage> descripteursFonctionnalites() {
        return Fonctionnalites.toutes().stream()
                .filter(fonctionnalite -> fonctionnalite.categorie().desactivable())
                .<DescripteurReglage>map(fonctionnalite -> new DescripteurReglage.Booleen(
                        Fonctionnalites.PREFIXE_CLE + fonctionnalite.id() + Fonctionnalites.SUFFIXE_CLE,
                        fonctionnalite.categorie() == Categorie.EXPERIMENTALE
                                ? fonctionnalite.libelle() + " (expérimental)"
                                : fonctionnalite.libelle(),
                        "Effet au prochain démarrage de l'application.",
                        fonctionnalite.categorie().activeParDefaut()))
                .toList();
    }

    /// Résolveur de fiche espèce (#922) : convertit l'URL de recherche GBIF en URL de fiche en résolvant
    /// la clé d'usage via l'API GBIF. Singleton (réutilise le client HTTP). Les liens PNA/Wikipédia, déjà
    /// directs, passent inchangés.
    @Provides
    @Singleton
    ResolveurFiche fournirResolveurFiche() {
        return new ResolveurFicheGbif(new ClientGbif()::cleUsage);
    }

    /// Source universelle des fiches espèces (repli hors PNA), pilotée par la préférence utilisateur
    /// (#849) : GBIF par défaut, Wikipédia FR au choix, relue à chaque lien (effet immédiat). Alimente le
    /// `ConstructeurLienEspece` par injection.
    @Provides
    @Singleton
    SourceUniverselle fournirSourceUniverselle(PreferenceSourceEspece preference) {
        return new SourceUniversellePreferee(preference::prefereWikipedia);
    }

    @Provides
    @Singleton
    Workspace fournirWorkspace() {
        return Workspace.resolu();
    }

    @Provides
    @Singleton
    SourceDeDonnees fournirSourceDeDonnees(Workspace workspace) {
        return new SourceDeDonnees(workspace);
    }

    /// Dépôt des **vues mémorisées** (#623), partagé par toutes les vues tabulaires (multisite, puis
    /// audio / analyse) : le composant d'onglets `commun.view.GestionnaireVues` y passe (via l'interface
    /// [DepotVues]) plutôt que par le DAO concret. Bindé au socle pour éviter une liaison par feature.
    @Provides
    @Singleton
    DepotVues fournirDepotVues(SourceDeDonnees source) {
        return new VueSauvegardeeDao(source);
    }

    /// Dépôt de la **disposition des colonnes par écran** (#994) : le socle `commun.view.GestionnaireColonnes`
    /// y passe (via l'interface [DepotDispositionColonnes]) pour retenir/restaurer l'ordre + la visibilité des
    /// colonnes entre deux ouvertures. Bindé ici au **DAO SQLite** ; le défaut `@ImplementedBy` en mémoire ne
    /// sert que les contextes sans base (tests de vue isolés).
    @Provides
    @Singleton
    DepotDispositionColonnes fournirDepotDispositionColonnes(SourceDeDonnees source) {
        return new DispositionColonnesDao(source);
    }

    /// Horloge applicative : l'horloge système en production. Transverse (les règles de dates R3/R4
    /// et
    /// les horodatages des features la réclament), elle est donc bindée au niveau du socle. Les tests
    /// n'utilisent pas ce binding : ils injectent directement une
    /// [fr.univ_amu.iut.commun.model.HorlogeFigee].
    @Provides
    @Singleton
    Horloge fournirHorloge() {
        return Horloge.systeme();
    }
}
