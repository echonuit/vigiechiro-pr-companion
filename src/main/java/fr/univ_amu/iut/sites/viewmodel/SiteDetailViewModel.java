package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.RegionDuCarre;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran de détail **M-Site-detail** d'un site de suivi.
///
/// Porte l'état observable d'un site : sa fiche d'identité (bandeau d'infos clés), ses points
/// d'écoute (cartes) et l'historique de ses passages (tableau). Le ViewModel lit les points via
/// [ServiceSites] et les passages via [PassageDao] (lecture seule), puis recompose des objets de
/// présentation ([CartePoint], [LignePassage]) que la vue affiche directement.
///
/// Il porte aussi les commandes d'édition du site : suppression du site (déléguée à
/// [ServiceSites], qui refuse tant qu'un passage est rattaché) et suppression d'un point. Comme
/// [ServiceSites] n'expose pas de suppression de point, cette dernière est faite via [PointDao]
/// en **rejouant le même garde-fou** (refus si des passages utilisent le point) afin de rester
/// cohérent avec la règle métier de suppression de site.
public class SiteDetailViewModel {

    private final ServiceSites service;
    private final PassageDao passageDao;
    private final Horloge horloge;
    private final PortailVigieChiro portail;

    /// Correspondances VigieChiro : elles disent si ce site est enregistré, et s'il est verrouillé (#734).
    private final LienVigieChiroDao liens;

    private Site site;

    private final ReadOnlyStringWrapper titre = wrapper("titre");
    private final ReadOnlyStringWrapper sousTitre = wrapper("sousTitre");
    private final ReadOnlyStringWrapper numeroCarre = wrapper("numeroCarre");
    private final ReadOnlyStringWrapper departement = wrapper("departement");
    private final ReadOnlyStringWrapper protocole = wrapper("protocole");
    private final ReadOnlyStringWrapper dateCreation = wrapper("dateCreation");
    private final ReadOnlyStringWrapper derniereNuit = wrapper("derniereNuit");
    private final ReadOnlyStringWrapper passagesDeLAnnee = wrapper("passagesDeLAnnee");
    private final ReadOnlyStringWrapper lienPortail = wrapper("lienPortail");
    private final ReadOnlyBooleanWrapper suppressionPossible =
            new ReadOnlyBooleanWrapper(this, "suppressionPossible", true);

    /// État du site vis-à-vis de la plateforme (#734). Le détail est l'écran où l'on se demande *pourquoi
    /// je ne peux pas déposer* : il affiche donc les trois états, y compris « non enregistré », que la
    /// liste, elle, tait.
    private final ReadOnlyObjectWrapper<StatutPlateforme> statutPlateforme =
            new ReadOnlyObjectWrapper<>(this, "statutPlateforme", StatutPlateforme.ABSENT);

    private final ObservableList<CartePoint> points = FXCollections.observableArrayList();
    private final ObservableList<LignePassage> passages = FXCollections.observableArrayList();

    /// La synchro rapatrie **tous** les points du carré (utile pour importer une nuit sur un point encore
    /// inutilisé), mais la fiche site ne déverse pas cette masse : elle masque par défaut les points
    /// **rapatriés non utilisés** (synchronisés + sans passage) et révèle ces derniers à la demande
    /// (#1738). Les points **ajoutés à la main** et les points **utilisés** restent toujours affichés.
    /// [#toutesLesCartes] tient la liste complète ; [#points] en est la projection affichée.
    private final List<CartePoint> toutesLesCartes = new ArrayList<>();

    private final BooleanProperty afficherTousLesPoints =
            new SimpleBooleanProperty(this, "afficherTousLesPoints", false);
    private final ReadOnlyIntegerWrapper nombrePointsMasques =
            new ReadOnlyIntegerWrapper(this, "nombrePointsMasques", 0);

    public SiteDetailViewModel(
            ServiceSites service,
            PassageDao passageDao,
            Horloge horloge,
            PortailVigieChiro portail,
            LienVigieChiroDao liens) {
        this.service = Objects.requireNonNull(service, "service");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.portail = Objects.requireNonNull(portail, "portail");
        this.liens = Objects.requireNonNull(liens, "liens");
        // La bascule d'affichage re-projette la liste (points utilisés seuls <-> tous), #1738.
        afficherTousLesPoints.addListener((observable, avant, apres) -> projeterPoints());
    }

    /// État du site vis-à-vis de VigieChiro : absent, enregistré, ou verrouillé (le dépôt n'est possible
    /// que dans ce dernier cas).
    public ReadOnlyObjectProperty<StatutPlateforme> statutPlateformeProperty() {
        return statutPlateforme.getReadOnlyProperty();
    }

    /// URL de la page du site sur le portail Vigie-Chiro (#1124), vide tant que le site n’est pas
    /// rattaché (l’action « Ouvrir sur Vigie-Chiro » reste alors désactivée avec explication).
    public ReadOnlyStringProperty lienPortailProperty() {
        return lienPortail.getReadOnlyProperty();
    }

    /// Charge le site à afficher, puis recompose la fiche, les cartes de points et le tableau.
    public void chargerSite(Site site) {
        this.site = Objects.requireNonNull(site, "site");
        // Chaque site s'ouvre décombré : les points non utilisés d'un site précédent ne restent pas révélés.
        afficherTousLesPoints.set(false);
        rafraichir();
    }

    /// Site actuellement affiché (utilisé par la vue pour ouvrir la modale de point ou naviguer).
    public Site siteCourant() {
        return site;
    }

    /// Relit le **site lui-même** puis recharge le reste (#3672).
    ///
    /// ⚠️ [#rafraichir()] ne suffit pas après une modification. Il recharge points, passages, bandeau
    /// et statut, mais le champ `site` est un **record détenu**, posé une seule fois par
    /// [#chargerSite(Site)] : il garde donc l'ancien numéro de carré, l'ancien nom et l'ancien
    /// protocole. Or c'est de lui que dérivent le numéro affiché, le département, le titre, le
    /// contexte transmis à un passage qu'on ouvre d'ici, et la cible de « Voir sur la carte ».
    public void rechargerSiteCourant() {
        this.site = service.site(site.id());
        rafraichir();
    }

    /// Recharge points et passages depuis la base et met à jour toutes les propriétés observables.
    public void rafraichir() {
        List<PointDEcoute> pointsDuSite = service.listerPoints(site.id());
        List<Passage> passagesDuSite = passagesDeTousLesPoints(pointsDuSite);
        mettreAJourCartesPoints(pointsDuSite);
        mettreAJourTableauPassages(pointsDuSite, passagesDuSite);
        mettreAJourBandeau(passagesDuSite);
        suppressionPossible.set(passagesDuSite.isEmpty());
        lienPortail.set(portail.pageSite(site.id()).orElse(""));
        statutPlateforme.set(StatutPlateforme.duSite(site.id(), liens));
    }

    /// Supprime le site courant (bouton header `🗑 Supprimer`).
    ///
    /// @throws RegleMetierException si au moins un passage est rattaché (refus côté service)
    public void supprimerSite() {
        service.supprimerSite(site.id());
    }

    /// Supprime un point d'écoute, puis rafraîchit la liste.
    ///
    /// La règle (refus si des passages sont rattachés) et l'écriture vivent dans [ServiceSites]
    /// depuis #3584. Elles étaient ici, en **copie** : les deux versions du refus avaient déjà cessé
    /// de dire la même chose.
    ///
    /// @throws RegleMetierException si des passages utilisent ce point
    public void supprimerPoint(PointDEcoute point) {
        service.supprimerPoint(point.id());
        rafraichir();
    }

    public ReadOnlyStringProperty titreProperty() {
        return titre.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty sousTitreProperty() {
        return sousTitre.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty numeroCarreProperty() {
        return numeroCarre.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty departementProperty() {
        return departement.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty protocoleProperty() {
        return protocole.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty dateCreationProperty() {
        return dateCreation.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty derniereNuitProperty() {
        return derniereNuit.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty passagesDeLAnneeProperty() {
        return passagesDeLAnnee.getReadOnlyProperty();
    }

    /// `false` si un passage est rattaché : le bouton « Supprimer » du site est alors désactivé.
    public ReadOnlyBooleanProperty suppressionPossibleProperty() {
        return suppressionPossible.getReadOnlyProperty();
    }

    /// Cartes des points d'écoute du site **effectivement affichées** : par défaut, tous sauf les points
    /// rapatriés non utilisés ; tous si [#afficherTousLesPointsProperty] est vrai (#1738).
    public ObservableList<CartePoint> points() {
        return points;
    }

    /// Bascule d'affichage des points **rapatriés non utilisés** (synchronisés + sans passage) : faux par
    /// défaut (fiche décombrée), la vue l'inverse par le lien de révélation (#1738).
    public BooleanProperty afficherTousLesPointsProperty() {
        return afficherTousLesPoints;
    }

    /// Nombre de points **masqués** par défaut : rapatriés de la plateforme et sans aucun passage. La vue
    /// n'affiche le lien de révélation que si ce nombre est > 0, et l'indique dans son libellé.
    public ReadOnlyIntegerProperty nombrePointsMasquesProperty() {
        return nombrePointsMasques.getReadOnlyProperty();
    }

    /// Lignes du tableau des passages, triées de la plus récente à la plus ancienne.
    public ObservableList<LignePassage> passages() {
        return passages;
    }

    private void mettreAJourCartesPoints(List<PointDEcoute> pointsDuSite) {
        List<CartePoint> cartes = new ArrayList<>();
        for (PointDEcoute point : pointsDuSite) {
            Double distanceProche = ProximitePoints.distanceAuPlusProche(point, pointsDuSite);
            cartes.add(new CartePoint(point, passageDao.findByPoint(point.id()).size(), distanceProche));
        }
        toutesLesCartes.clear();
        toutesLesCartes.addAll(cartes);
        projeterPoints();
    }

    /// Projette [#toutesLesCartes] sur [#points] selon la bascule : par défaut, tout **sauf** les points
    /// rapatriés non utilisés ; tous si [#afficherTousLesPoints]. Recompte au passage les points masqués,
    /// pour que la vue sache s'il faut proposer la révélation (#1738). L'ordre des cartes est préservé (le
    /// filtre est un simple sous-ensemble).
    private void projeterPoints() {
        List<CartePoint> visibles = toutesLesCartes.stream()
                .filter(carte -> afficherTousLesPoints.get() || affichableParDefaut(carte))
                .toList();
        long masques = toutesLesCartes.stream()
                .filter(carte -> !affichableParDefaut(carte))
                .count();
        nombrePointsMasques.set((int) masques);
        points.setAll(visibles);
    }

    /// Affiché par défaut si le point **sert** (au moins un passage) **ou** s'il a été **ajouté à la main** :
    /// seuls les points rapatriés (synchronisés) et non utilisés encombrent, et sont donc masqués (#1738).
    private static boolean affichableParDefaut(CartePoint carte) {
        return carte.nombrePassages() > 0 || !carte.point().synchronise();
    }

    private void mettreAJourTableauPassages(List<PointDEcoute> pointsDuSite, List<Passage> passagesDuSite) {
        Map<Long, String> codeParPoint = new LinkedHashMap<>();
        for (PointDEcoute point : pointsDuSite) {
            codeParPoint.put(point.id(), point.code());
        }
        List<LignePassage> lignes = new ArrayList<>();
        for (Passage passage : passagesDuSite) {
            lignes.add(LignePassage.depuis(passage, codeParPoint.getOrDefault(passage.idPoint(), "?")));
        }
        lignes.sort(Comparator.comparing(LignePassage::date).reversed());
        passages.setAll(lignes);
    }

    private void mettreAJourBandeau(List<Passage> passagesDuSite) {
        int annee = horloge.aujourdhui().getYear();
        titre.set(composerTitre());
        sousTitre.set(composerSousTitre());
        numeroCarre.set(site.numeroCarre());
        departement.set(departementDeCarre(site.numeroCarre()));
        protocole.set(site.protocole().libelle());
        dateCreation.set(site.dateCreation());
        derniereNuit.set(libelleDerniereNuit(passagesDuSite));
        passagesDeLAnnee.set(libellePassagesAnnee(passagesDuSite, annee));
    }

    private List<Passage> passagesDeTousLesPoints(List<PointDEcoute> pointsDuSite) {
        List<Passage> tous = new ArrayList<>();
        for (PointDEcoute point : pointsDuSite) {
            tous.addAll(passageDao.findByPoint(point.id()));
        }
        return tous;
    }

    private String composerTitre() {
        String prefixe = "Carré " + site.numeroCarre();
        return site.nomConvivial() == null ? prefixe : prefixe + " - " + site.nomConvivial();
    }

    /// Sous-titre de la barre de statut : commune (si connue) puis protocole.
    ///
    /// Sans repère de lieu (#1564). Le « 📍 » qui ouvrait cette chaîne **ne se rendait pas** sur les
    /// aperçus - vérifié à la loupe - et il ne pouvait pas devenir une icône ici : le sous-titre part
    /// dans `ZonesStatut`, un contrat fait de **chaînes** et partagé par tous les écrans. Le convertir
    /// demanderait d'y faire passer un nœud, ce qui dépasse de loin un repère décoratif. Rien n'est
    /// perdu : rien ne s'affichait.
    private String composerSousTitre() {
        String base = "Protocole " + site.protocole().libelle();
        return site.commentaire() == null ? base : site.commentaire() + " · " + base;
    }

    /// Le département affiché au bandeau : celui du **numéro de carré**, et non celui de la commune des
    /// points. Les deux lectures peuvent se contredire, et l'audit de cohérence les confronte (#2848) ;
    /// ici la règle ne se réécrit plus en `substring(0, 2)`, elle est lue là où l'ADR 2351 l'établit.
    private String departementDeCarre(String carre) {
        return RegionDuCarre.departement(carre).orElse(Formats.VALEUR_ABSENTE);
    }

    private String libelleDerniereNuit(List<Passage> passagesDuSite) {
        LocalDate derniere = passagesDuSite.stream()
                .map(Passage::dateEnregistrement)
                .filter(Objects::nonNull)
                .map(LocalDate::parse)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (derniere == null) {
            return Formats.VALEUR_ABSENTE;
        }
        long jours = ChronoUnit.DAYS.between(derniere, horloge.aujourdhui());
        return derniere + " (il y a " + jours + " j)";
    }

    private String libellePassagesAnnee(List<Passage> passagesDuSite, int annee) {
        long total = passagesDuSite.stream().filter(p -> p.annee() == annee).count();
        long aVerifier = passagesDuSite.stream()
                .filter(p -> p.annee() == annee)
                .filter(p -> p.verdictVerification() == null)
                .count();
        // Sans le « ⚠ » d'antan (#2036) : un ViewModel ne décide pas comment une sévérité s'affiche, or
        // c'est ce que faisait ce glyphe préfixé. Les mots « à vérifier » portent déjà l'avertissement ;
        // s'il fallait un jour une pastille, elle serait posée par la vue (cf. ColonneBadge, #2056), pas
        // écrite ici.
        return aVerifier > 0 ? total + " (dont " + aVerifier + " à vérifier)" : Long.toString(total);
    }

    private static ReadOnlyStringWrapper wrapper(String nom) {
        return new ReadOnlyStringWrapper(null, nom, "");
    }
}
