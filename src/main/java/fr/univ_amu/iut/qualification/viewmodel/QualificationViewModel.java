package fr.univ_amu.iut.qualification.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.qualification.model.ContexteVerification;
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

/// ViewModel du **noyau verdict** de l'écran M-Qualification (vérifier l'enregistrement, P3).
///
/// Porte le **pré-check** synthétique (3 feux consultatifs R13), le **verdict différé**
/// (OK / douteux / à jeter, persisté en une fois) et le statut/verdict actuels du passage
/// affichés dans le bandeau. La liste de la sélection d'écoute est portée par un VM dédié
/// ([SelectionEcouteViewModel]) : le controller câble les deux sur le même `idPassage`.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls `javafx.beans`
/// sont importés, jamais `javafx.scene`. Construit non-singleton (un VM frais par FXML).
///
/// TODO (M-Qualification) : implémentez les corps des méthodes publiques (ouvrirSur, choisirVerdict,
/// enregistrer) ; les propriétés observables et le binding peutEnregistrer sont fournis. Patron de
/// référence : SiteDetailViewModel (feature sites).
public class QualificationViewModel {

    private final ServiceQualification service;
    private Long idPassage;

    // Pré-check (étape 1) : 3 feux consultatifs + indicateur d'anomalie.
    private final ReadOnlyObjectWrapper<PreCheckNuit.Feu> feuCouverture =
            new ReadOnlyObjectWrapper<>(this, "feuCouverture");
    private final ReadOnlyObjectWrapper<PreCheckNuit.Feu> feuNombre = new ReadOnlyObjectWrapper<>(this, "feuNombre");
    private final ReadOnlyObjectWrapper<PreCheckNuit.Feu> feuRenommage =
            new ReadOnlyObjectWrapper<>(this, "feuRenommage");
    private final ReadOnlyBooleanWrapper preCheckAnomalie = new ReadOnlyBooleanWrapper(this, "preCheckAnomalie", false);

    // Statut/verdict persistés du passage (bandeau), mutés à l'enregistrement.
    private final ReadOnlyObjectWrapper<Verdict> verdictActuel =
            new ReadOnlyObjectWrapper<>(this, "verdictActuel", Verdict.A_VERIFIER);
    private final ReadOnlyObjectWrapper<StatutWorkflow> statut = new ReadOnlyObjectWrapper<>(this, "statut");

    // Verdict différé (étape 3) : choix + commentaire, persiste en une fois.
    private final ObjectProperty<Verdict> verdictChoisi = new SimpleObjectProperty<>(this, "verdictChoisi");
    private final StringProperty commentaire = new SimpleStringProperty(this, "commentaire", "");
    private final ReadOnlyObjectWrapper<EtatVerdict> etatVerdict =
            new ReadOnlyObjectWrapper<>(this, "etatVerdict", EtatVerdict.BROUILLON);
    private final ReadOnlyStringWrapper avertissementAJeter =
            new ReadOnlyStringWrapper(this, "avertissementAJeter", "");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    private final BooleanBinding peutEnregistrer;

    public QualificationViewModel(ServiceQualification service) {
        this.service = Objects.requireNonNull(service, "service");
        peutEnregistrer = Bindings.createBooleanBinding(
                () -> verdictChoisi.get() != null && verdictChoisi.get() != Verdict.A_VERIFIER, verdictChoisi);
    }

    /// Ouvre la vérification du passage `idPassage` : pré-check (3 feux) et amorçage du bandeau
    /// verdict (statut workflow + verdict déjà persisté). Appelée par la navigation après le
    /// chargement du FXML. Une erreur (passage introuvable) est restituée dans [#messageProperty()]
    /// sans lever.
    public void ouvrirSur(Long idPassage) {
        this.idPassage = idPassage;
        // TODO (M-Qualification) : exécutez le pré-check (service.precheck) et amorcez le bandeau
        //   (service.chargerContexte -> statut + verdict actuel) ; en cas d'erreur, réinitialisez et
        //   publiez le message.
        // --solution--
        reinitialiser();
        try {
            appliquerPrecheck(service.precheck(idPassage));
            ContexteVerification contexte = service.chargerContexte(idPassage);
            statut.set(contexte.statut());
            verdictActuel.set(contexte.verdict() == null ? Verdict.A_VERIFIER : contexte.verdict());
            message.set("");
        } catch (RuntimeException echec) {
            reinitialiser();
            message.set(echec.getMessage());
        }
        // --end-solution--
    }

    // --solution--
    /// Remet l'écran à un état vierge avant chaque (ré)ouverture et après un échec : feux, bandeau et
    /// saisie de verdict d'un passage précédent ne doivent jamais subsister à l'écran (le VM est
    /// non-singleton, mais rien n'empêche une réouverture sur un autre passage).
    private void reinitialiser() {
        feuCouverture.set(null);
        feuNombre.set(null);
        feuRenommage.set(null);
        preCheckAnomalie.set(false);
        statut.set(null);
        verdictActuel.set(Verdict.A_VERIFIER);
        verdictChoisi.set(null);
        commentaire.set("");
        etatVerdict.set(EtatVerdict.BROUILLON);
        avertissementAJeter.set("");
    }
    // --end-solution--

    /// Choix (différé) du verdict global de la nuit (boutons OK / douteux / à jeter).
    public void choisirVerdict(Verdict verdict) {
        // TODO (M-Qualification) : mémorisez le verdict choisi (verdictChoisi).
        // --solution--
        verdictChoisi.set(verdict);
        // --end-solution--
    }

    /// Enregistre le verdict choisi : transite le passage vers `VERIFIE`. Refuse si aucun verdict
    /// décisif n'est choisi. Signale (R14) si « à jeter » exclura le passage du dépôt.
    public void enregistrer() {
        // TODO (M-Qualification) : refusez sans verdict décisif ; sinon persistez le verdict
        //   (service.enregistrerVerdict), passez le statut à VERIFIE, gérez l'avertissement « à jeter »
        //   (R14) et l'état ENREGISTRE.
        // --solution--
        if (!peutEnregistrer.get()) {
            message.set("Choisissez un verdict (OK, douteux ou à jeter) avant d'enregistrer.");
            return;
        }
        try {
            service.enregistrerVerdict(idPassage, verdictChoisi.get(), commentaireOuNull());
            verdictActuel.set(verdictChoisi.get());
            statut.set(StatutWorkflow.VERIFIE);
            avertissementAJeter.set(
                    service.estAJeter(idPassage)
                            ? "⚠ Passage marqué « à jeter » : il sera exclu du prochain lot de dépôt (R14)."
                            : "");
            message.set("");
            etatVerdict.set(EtatVerdict.ENREGISTRE);
        } catch (RuntimeException refus) {
            message.set(refus.getMessage());
        }
        // --end-solution--
    }

    // --solution--
    private void appliquerPrecheck(PreCheckNuit.Diagnostic diagnostic) {
        feuCouverture.set(diagnostic.couvertureHoraire());
        feuNombre.set(diagnostic.nombreFichiers());
        feuRenommage.set(diagnostic.coherenceRenommage());
        preCheckAnomalie.set(diagnostic.presenteUneAnomalie());
    }

    private String commentaireOuNull() {
        String texte = commentaire.get();
        return texte == null || texte.isBlank() ? null : texte;
    }
    // --end-solution--

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

    /// Verdict persisté du passage (`A_VERIFIER` tant qu'aucun verdict n'est enregistré).
    public ReadOnlyObjectProperty<Verdict> verdictActuelProperty() {
        return verdictActuel.getReadOnlyProperty();
    }

    /// Statut workflow courant du passage (`TRANSFORME` → `VERIFIE` après enregistrement).
    public ReadOnlyObjectProperty<StatutWorkflow> statutProperty() {
        return statut.getReadOnlyProperty();
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
