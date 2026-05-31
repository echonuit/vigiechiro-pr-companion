package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.validation.ValidateurCodePoint;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/// ViewModel de la **modale d'ajout / d'édition d'un point d'écoute** (M-Site-detail).
///
/// Porte les champs saisis (code, descriptif, latitude, longitude) en propriétés modifiables,
/// auxquelles la vue se lie de façon bidirectionnelle. La logique de présentation y est
/// entièrement testable sans IHM :
///  - validité du code (R2) via le validateur partagé [ValidateurCodePoint] ;
///  - validité des coordonnées GPS (optionnelles ; latitude −90..90, longitude −180..180,
///    virgule décimale tolérée) ;
///  - [#peutEnregistrer()] combine ces validités pour piloter l'état du bouton de validation.
///
/// La création délègue à [ServiceSites#ajouterPoint] (qui rejoue R2 + l'unicité du code dans le
/// site) ; l'édition met à jour via [PointDao] après re-validation du code. Les refus métier
/// (code déjà pris…) sont restitués dans [#messageErreurProperty()] et l'enregistrement renvoie
/// `false` (la modale reste ouverte).
public class PointEditViewModel {

  /// Bornes de validité d'une latitude décimale (degrés).
  private static final double LATITUDE_MAX = 90.0;

  /// Bornes de validité d'une longitude décimale (degrés).
  private static final double LONGITUDE_MAX = 180.0;

  private final ServiceSites service;
  private final PointDao pointDao;

  private final StringProperty code = new SimpleStringProperty(this, "code", "");
  private final StringProperty description = new SimpleStringProperty(this, "description", "");
  private final StringProperty latitude = new SimpleStringProperty(this, "latitude", "");
  private final StringProperty longitude = new SimpleStringProperty(this, "longitude", "");
  private final ReadOnlyStringWrapper titre = new ReadOnlyStringWrapper(this, "titre", "");
  private final ReadOnlyStringWrapper libelleBouton =
      new ReadOnlyStringWrapper(this, "libelleBouton", "+ Ajouter");
  private final ReadOnlyStringWrapper messageErreur =
      new ReadOnlyStringWrapper(this, "messageErreur", "");

  private final BooleanBinding codeValide;
  private final BooleanBinding latitudeValide;
  private final BooleanBinding longitudeValide;
  private final BooleanBinding peutEnregistrer;

  private Long idSite;
  private Long idPointEnEdition;

  public PointEditViewModel(ServiceSites service, PointDao pointDao) {
    this.service = Objects.requireNonNull(service, "service");
    this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
    codeValide =
        Bindings.createBooleanBinding(() -> ValidateurCodePoint.estValide(code.get()), code);
    latitudeValide =
        Bindings.createBooleanBinding(
            () -> coordonneeValide(latitude.get(), LATITUDE_MAX), latitude);
    longitudeValide =
        Bindings.createBooleanBinding(
            () -> coordonneeValide(longitude.get(), LONGITUDE_MAX), longitude);
    peutEnregistrer = codeValide.and(latitudeValide).and(longitudeValide);
  }

  /// Configure la modale en **mode création** d'un point pour le site donné.
  public void preparerCreation(Site site) {
    Objects.requireNonNull(site, "site");
    this.idSite = site.id();
    this.idPointEnEdition = null;
    reinitialiserChamps();
    titre.set("Nouveau point d'écoute · Carré " + site.numeroCarre());
    libelleBouton.set("+ Ajouter");
  }

  /// Configure la modale en **mode édition** : champs pré-remplis depuis le point existant.
  public void preparerEdition(Site site, PointDEcoute point) {
    Objects.requireNonNull(site, "site");
    Objects.requireNonNull(point, "point");
    this.idSite = site.id();
    this.idPointEnEdition = point.id();
    code.set(point.code());
    description.set(point.description() == null ? "" : point.description());
    latitude.set(point.latitude() == null ? "" : Double.toString(point.latitude()));
    longitude.set(point.longitude() == null ? "" : Double.toString(point.longitude()));
    messageErreur.set("");
    titre.set("Modifier le point " + point.code() + " · Carré " + site.numeroCarre());
    libelleBouton.set("Modifier");
  }

  /// Tente d'enregistrer le point (création ou édition).
  ///
  /// @return `true` si l'enregistrement a réussi (la vue peut fermer la modale) ; `false` si une
  ///     règle métier a refusé l'opération (le motif est dans [#messageErreurProperty()])
  public boolean enregistrer() {
    if (!peutEnregistrer.get()) {
      messageErreur.set("Corrigez les champs en rouge avant d'enregistrer.");
      return false;
    }
    try {
      Double lat = parserCoordonnee(latitude.get());
      Double lon = parserCoordonnee(longitude.get());
      String desc = description.get().isBlank() ? null : description.get();
      if (idPointEnEdition == null) {
        service.ajouterPoint(idSite, code.get(), lat, lon, desc);
      } else {
        pointDao.update(new PointDEcoute(idPointEnEdition, code.get(), lat, lon, desc, idSite));
      }
      messageErreur.set("");
      return true;
    } catch (RuntimeException refus) {
      messageErreur.set(refus.getMessage());
      return false;
    }
  }

  public StringProperty codeProperty() {
    return code;
  }

  public StringProperty descriptionProperty() {
    return description;
  }

  public StringProperty latitudeProperty() {
    return latitude;
  }

  public StringProperty longitudeProperty() {
    return longitude;
  }

  public ReadOnlyStringProperty titreProperty() {
    return titre.getReadOnlyProperty();
  }

  public ReadOnlyStringProperty libelleBoutonProperty() {
    return libelleBouton.getReadOnlyProperty();
  }

  public ReadOnlyStringProperty messageErreurProperty() {
    return messageErreur.getReadOnlyProperty();
  }

  /// Validité du code de point (R2) : pilote le surlignage du champ dans la vue.
  public BooleanBinding codeValide() {
    return codeValide;
  }

  /// Conjonction des validités : pilote l'activation du bouton de validation.
  public BooleanBinding peutEnregistrer() {
    return peutEnregistrer;
  }

  private void reinitialiserChamps() {
    code.set("");
    description.set("");
    latitude.set("");
    longitude.set("");
    messageErreur.set("");
  }

  /// Une coordonnée est valide si elle est vide (GPS optionnel) ou décimale dans `[-borne, borne]`.
  private static boolean coordonneeValide(String texte, double borneAbsolue) {
    if (texte == null || texte.isBlank()) {
      return true;
    }
    try {
      double valeur = parserDouble(texte);
      return valeur >= -borneAbsolue && valeur <= borneAbsolue;
    } catch (NumberFormatException malForme) {
      return false;
    }
  }

  /// Parse une coordonnée saisie, ou `null` si le champ est vide (GPS optionnel).
  private static Double parserCoordonnee(String texte) {
    return texte == null || texte.isBlank() ? null : parserDouble(texte);
  }

  /// Parse un décimal en tolérant la virgule française comme séparateur.
  private static double parserDouble(String texte) {
    return Double.parseDouble(texte.trim().replace(',', '.'));
  }
}
