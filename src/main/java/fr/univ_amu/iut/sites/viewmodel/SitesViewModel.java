package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.SynchronisationSites;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran d'accueil **M-Sites** (« Mes sites de suivi »).
///
/// Expose la liste observable des [CarteSite] (une carte par site de l'utilisateur courant),
/// le sous-titre récapitulatif et un indicateur d'état vide. La vue se lie à ces propriétés
/// sans jamais accéder elle-même à la base : tout le calcul d'agrégats (compteurs, codes de
/// points, fraîcheur du dernier passage) est fait ici.
///
/// Le ViewModel s'appuie sur [ServiceSites] (sites et points de l'utilisateur) et lit les
/// passages via [PassageDao] (lecture seule) pour les compteurs et la date du dernier passage.
/// Cette dépendance `sites → passage` est déjà celle de [ServiceSites] : le graphe reste
/// acyclique. Conformément à la règle ArchUnit `viewmodel_sans_javafx_ui`, seul
/// `javafx.beans`/`javafx.collections` est importé ici (jamais `javafx.scene`).
public class SitesViewModel {

    /// Libellé du compte-rendu des sites, aligné sur `RapprochementSites` : pilote la forme
    /// singulier/pluriel du message de synchro (les autres rapprocheurs se disent via `enClair`).
    private static final String LIBELLE_SITES = "sites";

    private final ServiceSites service;
    private final PassageDao passageDao;
    private final Horloge horloge;
    private final LienVigieChiroDao liens;
    private final String idUtilisateur;
    private final Optional<SynchronisationSites> synchronisation;

    private final ObservableList<CarteSite> cartes = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper sousTitre = new ReadOnlyStringWrapper(this, "sousTitre", "");
    private final ReadOnlyBooleanWrapper vide = new ReadOnlyBooleanWrapper(this, "vide", true);
    private final ReadOnlyStringWrapper messageErreur = new ReadOnlyStringWrapper(this, "messageErreur", "");
    private final ReadOnlyStringWrapper messageSynchro = new ReadOnlyStringWrapper(this, "messageSynchro", "");

    public SitesViewModel(
            ServiceSites service,
            PassageDao passageDao,
            Horloge horloge,
            LienVigieChiroDao liens,
            String idUtilisateur,
            Optional<SynchronisationSites> synchronisation) {
        this.service = Objects.requireNonNull(service, "service");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.liens = Objects.requireNonNull(liens, "liens");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        this.synchronisation = Objects.requireNonNull(synchronisation, "synchronisation");
    }

    /// `true` quand la synchronisation à la demande est disponible (app complète). `false` → la vue
    /// masque le bouton, patron de la modale passage (#937).
    public boolean peutSynchroniser() {
        return synchronisation.isPresent();
    }

    /// Rejoue le pull depuis VigieChiro (#1045) : sites **puis** ce qui en dépend, dont les squelettes de
    /// nuits (#1662). À appeler **hors du fil JavaFX** (réseau + écritures base) ; best-effort, sémantique
    /// conservatrice de [SynchronisationSites] (jamais d’écrasement local). Un compte-rendu par rapprocheur
    /// ayant du neuf à annoncer ; liste vide si la passerelle est absente ou n’a rien récupéré.
    public List<RapportSynchro> synchroniserDepuisVigieChiro() {
        return synchronisation.map(SynchronisationSites::synchroniser).orElseGet(List::of);
    }

    /// Parcours complet de la synchronisation à la demande (#1045, déporté #1212), à appeler **hors du
    /// fil JavaFX** : pull best-effort puis relecture des cartes (le pull a pu créer des sites). Le
    /// résultat s'applique sur le fil JavaFX via [#appliquerSynchro].
    public SynchroEtChargement synchroniserEtRecharger() {
        return new SynchroEtChargement(synchroniserDepuisVigieChiro(), charger());
    }

    /// À rappeler **sur le fil JavaFX** après [#synchroniserEtRecharger()] : applique les cartes
    /// rechargées puis pose le message de résultat, jamais un silence (#937).
    public void appliquerSynchro(SynchroEtChargement resultat) {
        appliquer(resultat.chargement());
        messageSynchro.set(messageDe(resultat.rapports()));
    }

    /// Route l'échec de la synchronisation vers son message de restitution (fil JavaFX) : jamais un
    /// silence, ni un bouton resté figé (#795/#1212).
    public void signalerErreurSynchro(Throwable erreur) {
        messageSynchro.set("La synchronisation VigieChiro a échoué : " + erreur.getMessage());
    }

    /// Message de résultat de la synchronisation à la demande, vide tant qu’aucune n’a eu lieu.
    public ReadOnlyStringProperty messageSynchroProperty() {
        return messageSynchro.getReadOnlyProperty();
    }

    private static String messageDe(List<RapportSynchro> rapports) {
        if (rapports.isEmpty()) {
            return "Aucun site distant récupéré (non connecté, ou aucun site sur VigieChiro).";
        }
        // #1284 : une synchronisation EMPECHEE (injoignable, refus) se dit telle quelle, au lieu de se
        // confondre avec « aucun site distant ».
        Optional<RapportSynchro> empeche =
                rapports.stream().filter(rapport -> rapport.souci() != null).findFirst();
        if (empeche.isPresent()) {
            return "Sites non synchronisés : " + empeche.get().souci() + ".";
        }
        String corps = rapports.stream().map(SitesViewModel::segmentSynchro).collect(Collectors.joining(", "));
        return corps + " depuis VigieChiro.";
    }

    /// Rend un rapport dans le message du bouton. Les sites gardent leur forme verbale singulier/pluriel
    /// (« 1 site synchronisé ») ; les autres rapprocheurs (passages rapatriés, #1662) se disent via
    /// [RapportSynchro#enClair] (« 5 passage(s) rapatrié(s) »).
    private static String segmentSynchro(RapportSynchro rapport) {
        if (LIBELLE_SITES.equals(rapport.libelle())) {
            return rapport.nombre() + (rapport.nombre() > 1 ? " sites synchronisés" : " site synchronisé");
        }
        return rapport.enClair();
    }

    /// Liste observable des cartes de sites, alimentée par [#rafraichir()].
    public ObservableList<CarteSite> cartes() {
        return cartes;
    }

    /// Sous-titre du bandeau (ex. `3 sites déclarés · 12 passages enregistrés en 2026`).
    public ReadOnlyStringProperty sousTitreProperty() {
        return sousTitre.getReadOnlyProperty();
    }

    /// `true` quand l'utilisateur n'a encore déclaré aucun site (déclenche l'état vide).
    public ReadOnlyBooleanProperty videProperty() {
        return vide.getReadOnlyProperty();
    }

    /// Message d'erreur de chargement, vide quand tout va bien (#795) : un échec de lecture des sites
    /// (base indisponible…) y est routé au lieu d'être avalé, pour que la vue puisse l'afficher.
    public ReadOnlyStringProperty messageErreurProperty() {
        return messageErreur.getReadOnlyProperty();
    }

    /// Recharge les sites de l'utilisateur courant et recompose toutes les cartes + le sous-titre,
    /// en **synchrone** (fil JavaFX) : réservé aux suites d'actions déjà sur ce fil (création d'un
    /// site). L'ouverture d'écran et la synchronisation passent par le couple [#charger] /
    /// [#appliquer], exécuté hors du fil JavaFX (#1212).
    public void rafraichir() {
        try {
            appliquer(charger());
        } catch (RuntimeException echec) {
            // Sans ce filet, l'échec remontait non capturé et l'écran restait muet (#795).
            signalerErreur(echec);
        }
    }

    /// Lit et recompose les cartes de l'utilisateur courant, **hors du fil JavaFX** (lectures base) :
    /// aucune propriété observable n'est touchée ici. Le résultat s'applique via [#appliquer] ; un
    /// échec de lecture remonte à l'appelant (routé vers [#signalerErreur] par l'exécuteur).
    public ChargementCartes charger() {
        LocalDate aujourdhui = horloge.aujourdhui();
        int annee = aujourdhui.getYear();
        // Statut plateforme de chaque site (#728/#718), lu une fois pour tout le lot : présent dans les
        // correspondances = « enregistré » ; correspondance verrouillée = « verrouillé » (dépôt possible).
        Map<String, String> sitesEnregistres = liens.tous(LienVigieChiro.ENTITE_SITE);
        Set<String> sitesVerrouilles = liens.verrouilles(LienVigieChiro.ENTITE_SITE);
        List<CarteSite> recomposees = new ArrayList<>();
        int totalPassagesAnnee = 0;
        for (Site site : service.listerSites(idUtilisateur)) {
            StatutPlateforme statut =
                    StatutPlateforme.deduire(String.valueOf(site.id()), sitesEnregistres, sitesVerrouilles);
            CarteSite carte = construireCarte(site, aujourdhui, annee, statut);
            totalPassagesAnnee += carte.passagesDeLAnnee();
            recomposees.add(carte);
        }
        return new ChargementCartes(
                List.copyOf(recomposees), composerSousTitre(recomposees.size(), totalPassagesAnnee, annee));
    }

    /// Applique un [ChargementCartes] aux propriétés observables, **sur le fil JavaFX**.
    public void appliquer(ChargementCartes chargement) {
        cartes.setAll(chargement.cartes());
        vide.set(chargement.cartes().isEmpty());
        sousTitre.set(chargement.sousTitre());
        messageErreur.set("");
    }

    /// Route un échec de chargement vers le filet d'erreurs de l'écran (#795), **sur le fil JavaFX**.
    public void signalerErreur(Throwable erreur) {
        messageErreur.set("Impossible de charger vos sites : " + erreur.getMessage());
    }

    /// Instantané du rechargement des cartes, calculé hors du fil JavaFX ([#charger]) puis appliqué
    /// sur le fil JavaFX ([#appliquer]) - le ViewModel reste agnostique de l'IHM (#1212).
    public record ChargementCartes(List<CarteSite> cartes, String sousTitre) {}

    /// Résultat du parcours « synchroniser puis recharger » ([#synchroniserEtRecharger]) : les rapports
    /// du pull (un par rapprocheur ayant du neuf ; liste vide si rien récupéré) et les cartes rechargées
    /// à appliquer.
    public record SynchroEtChargement(List<RapportSynchro> rapports, ChargementCartes chargement) {}

    /// Crée un site pour l'utilisateur courant (bouton « + Nouveau site ») puis rafraîchit la liste.
    ///
    /// Délègue la validation R1/R5 à [ServiceSites] : une saisie invalide ou un carré déjà déclaré
    /// remontent sous forme d'exception, traitée par la vue.
    public Site creerSite(String numeroCarre, String nomConvivial, Protocole protocole, String commentaire) {
        Site cree = service.creerSite(numeroCarre, nomConvivial, protocole, commentaire, idUtilisateur);
        rafraichir();
        return cree;
    }

    private CarteSite construireCarte(Site site, LocalDate aujourdhui, int annee, StatutPlateforme statut) {
        List<PointDEcoute> points = service.listerPoints(site.id());
        List<Passage> passages = passagesDuSite(points);
        Set<Long> pointsAvecPassage = passages.stream().map(Passage::idPoint).collect(Collectors.toSet());
        int passagesAnnee =
                (int) passages.stream().filter(p -> p.annee() == annee).count();
        int aVerifier = (int) passages.stream()
                .filter(p -> p.annee() == annee)
                .filter(this::aVerifier)
                .count();
        LocalDate dernier = datePlusRecente(passages);
        Fraicheur fraicheur = Fraicheur.depuis(dernier, aujourdhui);
        return new CarteSite(
                site,
                points.size(),
                joindreCodes(points, pointsAvecPassage),
                passagesAnnee,
                annee,
                aVerifier,
                fraicheur,
                libelleFraicheur(dernier, aujourdhui),
                statut);
    }

    private List<Passage> passagesDuSite(List<PointDEcoute> points) {
        List<Passage> passages = new ArrayList<>();
        for (PointDEcoute point : points) {
            passages.addAll(passageDao.findByPoint(point.id()));
        }
        return passages;
    }

    /// Un passage est « à vérifier » tant qu'aucun verdict n'a été posé.
    private boolean aVerifier(Passage passage) {
        return passage.verdictVerification() == null;
    }

    /// Codes des points à **mettre en avant** sur la carte du site : ceux qui **servent** (au moins un
    /// passage) ou **ajoutés à la main** (non synchronisés). Les points **rapatriés non utilisés** (grille
    /// STOC importée en masse par la synchro) ne sont pas égrenés mais **résumés** - « + N rapatriés » -,
    /// pour que la carte reste lisible (#1750, corollaire de #1738 côté « Mes Sites »).
    private String joindreCodes(List<PointDEcoute> points, Set<Long> pointsAvecPassage) {
        if (points.isEmpty()) {
            return "—";
        }
        List<PointDEcoute> enAvant = points.stream()
                .filter(point -> pointsAvecPassage.contains(point.id()) || !point.synchronise())
                .toList();
        long rapatriesMasques = points.size() - enAvant.size();
        if (enAvant.isEmpty()) {
            // Carré entièrement rapatrié, aucun point encore utilisé : on résume, on n'égrène pas.
            return rapatriesMasques + " point(s) rapatrié(s), aucun utilisé";
        }
        String codes = enAvant.stream().map(PointDEcoute::code).collect(Collectors.joining(" · "));
        return rapatriesMasques == 0
                ? codes
                : codes + "  (+ " + rapatriesMasques + " rapatrié" + (rapatriesMasques > 1 ? "s" : "") + ")";
    }

    private LocalDate datePlusRecente(List<Passage> passages) {
        return passages.stream()
                .map(Passage::dateEnregistrement)
                .filter(Objects::nonNull)
                .map(LocalDate::parse)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private String libelleFraicheur(LocalDate dernier, LocalDate aujourdhui) {
        if (dernier == null) {
            return "Aucun passage";
        }
        long jours = ChronoUnit.DAYS.between(dernier, aujourdhui);
        return "Dernier passage : il y a " + jours + " j";
    }

    private String composerSousTitre(int nombreSites, int totalPassagesAnnee, int annee) {
        String sites = nombreSites + (nombreSites > 1 ? " sites déclarés" : " site déclaré");
        String passages = totalPassagesAnnee
                + (totalPassagesAnnee > 1 ? " passages enregistrés en " : " passage enregistré en ")
                + annee;
        return sites + " · " + passages;
    }
}
