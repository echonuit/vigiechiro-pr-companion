package fr.univ_amu.iut.qualification.viewmodel;

import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/// ViewModel de l'écran **M-Qualification** (vérifier l'enregistrement par échantillonnage, P3).
///
/// Cette première tranche porte le **noyau de décision** : le pré-check synthétique de la nuit (3
/// feux consultatifs, R13, jamais bloquants) et le **verdict différé** (OK / douteux / à jeter)
/// avec son commentaire, persisté en une fois via [ServiceQualification#enregistrerVerdict]. La
/// liste de la sélection, l'écoute audio et la modale de personnalisation viendront ensuite.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seul `javafx.beans` est
/// importé, jamais `javafx.scene`. Construit non-singleton (un VM frais par chargement FXML).
public class QualificationViewModel {

  private final ServiceQualification service;
  private Long idPassage;

  // Pré-check (etape 1) : 3 feux consultatifs + indicateur d'anomalie.
  private final ReadOnlyObjectWrapper<PreCheckNuit.Feu> feuCouverture =
      new ReadOnlyObjectWrapper<>(this, "feuCouverture");
  private final ReadOnlyObjectWrapper<PreCheckNuit.Feu> feuNombre =
      new ReadOnlyObjectWrapper<>(this, "feuNombre");
  private final ReadOnlyObjectWrapper<PreCheckNuit.Feu> feuRenommage =
      new ReadOnlyObjectWrapper<>(this, "feuRenommage");
  private final ReadOnlyBooleanWrapper preCheckAnomalie =
      new ReadOnlyBooleanWrapper(this, "preCheckAnomalie", false);

  // Verdict differe (etape 3) : choix + commentaire, persiste en une fois.
  private final ObjectProperty<Verdict> verdictChoisi =
      new SimpleObjectProperty<>(this, "verdictChoisi");
  private final StringProperty commentaire = new SimpleStringProperty(this, "commentaire", "");
  private final ReadOnlyObjectWrapper<EtatVerdict> etatVerdict =
      new ReadOnlyObjectWrapper<>(this, "etatVerdict", EtatVerdict.BROUILLON);
  private final ReadOnlyStringWrapper avertissementAJeter =
      new ReadOnlyStringWrapper(this, "avertissementAJeter", "");
  private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

  private final BooleanBinding peutEnregistrer;

  public QualificationViewModel(ServiceQualification service) {
    this.service = Objects.requireNonNull(service, "service");
    peutEnregistrer =
        Bindings.createBooleanBinding(
            () -> verdictChoisi.get() != null && verdictChoisi.get() != Verdict.A_VERIFIER,
            verdictChoisi);
  }

  /// Ouvre la vérification du passage `idPassage` et calcule le pré-check (3 feux). Appelée par la
  /// navigation après le chargement du FXML. Un passage introuvable est restitué dans
  /// [#messageProperty()] (consultatif), sans lever.
  public void ouvrirSur(Long idPassage) {
    this.idPassage = idPassage;
    try {
      PreCheckNuit.Diagnostic diagnostic = service.precheck(idPassage);
      feuCouverture.set(diagnostic.couvertureHoraire());
      feuNombre.set(diagnostic.nombreFichiers());
      feuRenommage.set(diagnostic.coherenceRenommage());
      preCheckAnomalie.set(diagnostic.presenteUneAnomalie());
      message.set("");
    } catch (RuntimeException echec) {
      message.set(echec.getMessage());
    }
  }

  /// Choix (différé) du verdict global de la nuit (boutons OK / douteux / à jeter).
  public void choisirVerdict(Verdict verdict) {
    verdictChoisi.set(verdict);
  }

  /// Enregistre le verdict choisi : transite le passage vers `VERIFIE`. Refuse si aucun verdict
  /// décisif n'est choisi. Signale (R14) si « à jeter » exclura le passage du dépôt.
  public void enregistrer() {
    if (!peutEnregistrer.get()) {
      message.set("Choisissez un verdict (OK, douteux ou à jeter) avant d'enregistrer.");
      return;
    }
    try {
      service.enregistrerVerdict(idPassage, verdictChoisi.get(), commentaireOuNull());
      avertissementAJeter.set(
          service.estAJeter(idPassage)
              ? "⚠ Passage marqué « à jeter » : il sera exclu du prochain lot de dépôt (R14)."
              : "");
      message.set("");
      etatVerdict.set(EtatVerdict.ENREGISTRE);
    } catch (RuntimeException refus) {
      message.set(refus.getMessage());
    }
  }

  private String commentaireOuNull() {
    String texte = commentaire.get();
    return texte == null || texte.isBlank() ? null : texte;
  }

  /// Feu du pré-check sur la couverture horaire de la nuit (R3).
  public ReadOnlyObjectProperty<PreCheckNuit.Feu> feuCouvertureProperty() {
    return feuCouverture.getReadOnlyProperty();
  }

  /// Feu du pré-check sur le nombre de fichiers enregistrés.
  public ReadOnlyObjectProperty<PreCheckNuit.Feu> feuNombreProperty() {
    return feuNombre.getReadOnlyProperty();
  }

  /// Feu du pré-check sur la cohérence du renommage (R6).
  public ReadOnlyObjectProperty<PreCheckNuit.Feu> feuRenommageProperty() {
    return feuRenommage.getReadOnlyProperty();
  }

  /// `true` si au moins un feu est rouge (pilote un bandeau d'alerte). Consultatif, jamais
  /// bloquant (R13).
  public ReadOnlyBooleanProperty preCheckAnomalieProperty() {
    return preCheckAnomalie.getReadOnlyProperty();
  }

  /// Verdict choisi mais pas encore enregistré (sélection différée des boutons O / D / J).
  public ObjectProperty<Verdict> verdictChoisiProperty() {
    return verdictChoisi;
  }

  /// Commentaire libre accompagnant le verdict (vide = commentaire existant conservé côté service).
  public StringProperty commentaireProperty() {
    return commentaire;
  }

  /// État du verdict : `BROUILLON` tant qu'il n'est pas persisté, `ENREGISTRE` après.
  public ReadOnlyObjectProperty<EtatVerdict> etatVerdictProperty() {
    return etatVerdict.getReadOnlyProperty();
  }

  /// Avertissement R14 affiché après l'enregistrement d'un verdict « à jeter », vide sinon.
  public ReadOnlyStringProperty avertissementAJeterProperty() {
    return avertissementAJeter.getReadOnlyProperty();
  }

  /// Message d'erreur (passage introuvable, verdict manquant), vide en fonctionnement nominal.
  public ReadOnlyStringProperty messageProperty() {
    return message.getReadOnlyProperty();
  }

  /// Activation du bouton « Enregistrer le verdict » : un verdict décisif (≠ `A_VERIFIER`) choisi.
  public BooleanBinding peutEnregistrer() {
    return peutEnregistrer;
  }
}
