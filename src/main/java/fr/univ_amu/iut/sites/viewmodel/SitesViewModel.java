package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

  private final ServiceSites service;
  private final PassageDao passageDao;
  private final Horloge horloge;
  private final String idUtilisateur;

  private final ObservableList<CarteSite> cartes = FXCollections.observableArrayList();
  private final ReadOnlyStringWrapper sousTitre = new ReadOnlyStringWrapper(this, "sousTitre", "");
  private final ReadOnlyBooleanWrapper vide = new ReadOnlyBooleanWrapper(this, "vide", true);

  public SitesViewModel(
      ServiceSites service, PassageDao passageDao, Horloge horloge, String idUtilisateur) {
    this.service = Objects.requireNonNull(service, "service");
    this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
    this.horloge = Objects.requireNonNull(horloge, "horloge");
    this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
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

  /// Recharge les sites de l'utilisateur courant et recompose toutes les cartes + le sous-titre.
  public void rafraichir() {
    LocalDate aujourdhui = horloge.aujourdhui();
    int annee = aujourdhui.getYear();
    List<CarteSite> recomposees = new ArrayList<>();
    int totalPassagesAnnee = 0;
    for (Site site : service.listerSites(idUtilisateur)) {
      CarteSite carte = construireCarte(site, aujourdhui, annee);
      totalPassagesAnnee += carte.passagesDeLAnnee();
      recomposees.add(carte);
    }
    cartes.setAll(recomposees);
    vide.set(recomposees.isEmpty());
    sousTitre.set(composerSousTitre(recomposees.size(), totalPassagesAnnee, annee));
  }

  /// Crée un site pour l'utilisateur courant (bouton « + Nouveau site ») puis rafraîchit la liste.
  ///
  /// Délègue la validation R1/R5 à [ServiceSites] : une saisie invalide ou un carré déjà déclaré
  /// remontent sous forme d'exception, traitée par la vue.
  public Site creerSite(
      String numeroCarre, String nomConvivial, Protocole protocole, String commentaire) {
    Site cree = service.creerSite(numeroCarre, nomConvivial, protocole, commentaire, idUtilisateur);
    rafraichir();
    return cree;
  }

  private CarteSite construireCarte(Site site, LocalDate aujourdhui, int annee) {
    List<PointDEcoute> points = service.listerPoints(site.id());
    List<Passage> passages = passagesDuSite(points);
    int passagesAnnee = (int) passages.stream().filter(p -> p.annee() == annee).count();
    int aVerifier =
        (int) passages.stream().filter(p -> p.annee() == annee).filter(this::aVerifier).count();
    LocalDate dernier = datePlusRecente(passages);
    Fraicheur fraicheur = Fraicheur.depuis(dernier, aujourdhui);
    return new CarteSite(
        site,
        points.size(),
        joindreCodes(points),
        passagesAnnee,
        aVerifier,
        fraicheur,
        libelleFraicheur(dernier, aujourdhui));
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

  private String joindreCodes(List<PointDEcoute> points) {
    if (points.isEmpty()) {
      return "—";
    }
    return points.stream().map(PointDEcoute::code).collect(Collectors.joining(" · "));
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
    String passages =
        totalPassagesAnnee
            + (totalPassagesAnnee > 1 ? " passages enregistrés en " : " passage enregistré en ")
            + annee;
    return sites + " · " + passages;
  }
}
