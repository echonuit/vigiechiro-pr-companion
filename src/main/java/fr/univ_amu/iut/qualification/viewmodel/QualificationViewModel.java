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
        // Enregistrable seulement si un verdict réel est choisi ET qu'il constitue un brouillon non encore
        // persisté (#797) : une fois enregistré, le bouton se grise pour ne pas inviter à un double-clic
        // redondant ; toute modification du verdict/commentaire ré-arme le brouillon (redevenirBrouillon…).
        peutEnregistrer = Bindings.createBooleanBinding(
                () -> verdictChoisi.get() != null
                        && verdictChoisi.get() != Verdict.A_VERIFIER
                        && etatVerdict.get() == EtatVerdict.BROUILLON,
                verdictChoisi,
                etatVerdict);
        // Ré-armer la garde de saisie : verdict et commentaire restent éditables après un
        // enregistrement. Toute modification recrée donc un brouillon non persisté (le verdict/
        // commentaire à l'écran ne correspond plus à l'état enregistré) ; sans ce ré-armement, quitter
        // l'écran perdrait silencieusement la modification (cf. garde de navigation #140).
        verdictChoisi.addListener((obs, ancien, nouveau) -> redevenirBrouillonSiModifie());
        commentaire.addListener((obs, ancien, nouveau) -> redevenirBrouillonSiModifie());
    }

    /// Ouvre la vérification du passage `idPassage` : pré-check (3 feux) et amorçage du bandeau
    /// verdict (statut workflow + verdict déjà persisté). Appelée par la navigation après le
    /// chargement du FXML. Une erreur (passage introuvable) est restituée dans [#messageProperty()]
    /// sans lever.
    public void ouvrirSur(Long idPassage) {
        try {
            appliquer(idPassage, charger(idPassage));
        } catch (RuntimeException echec) {
            signalerErreur(idPassage, echec);
        }
    }

    /// Données de vérification chargées **hors du fil JavaFX** (#1210) : pré-check + contexte (statut,
    /// verdict). Lecture seule (aucune mutation observable).
    public record DonneesVerdict(PreCheckNuit.Diagnostic precheck, ContexteVerification contexte) {}

    /// **Lecture seule** du pré-check et du contexte du passage. Sûre **hors du fil JavaFX**.
    public DonneesVerdict charger(Long idPassage) {
        return new DonneesVerdict(service.precheck(idPassage), service.chargerContexte(idPassage));
    }

    /// Applique les données chargées (3 feux, statut, verdict). **Mutations observables** : sur le fil
    /// JavaFX.
    public void appliquer(Long idPassage, DonneesVerdict donnees) {
        this.idPassage = idPassage;
        reinitialiser();
        appliquerPrecheck(donnees.precheck());
        statut.set(donnees.contexte().statut());
        verdictActuel.set(
                donnees.contexte().verdict() == null
                        ? Verdict.A_VERIFIER
                        : donnees.contexte().verdict());
        message.set("");
    }

    /// Route l'échec d'un chargement (passage introuvable…) vers le message de l'écran (filet #795).
    public void signalerErreur(Long idPassage, Throwable erreur) {
        this.idPassage = idPassage;
        reinitialiser();
        String detail = erreur.getMessage();
        message.set(detail != null && !detail.isBlank() ? detail : "Ouverture de la vérification impossible.");
    }

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

    /// Ré-arme l'état « brouillon » dès qu'une modification survient **après** un enregistrement : le
    /// verdict ou le commentaire affiché ne correspond plus au verdict persisté, donc la garde de
    /// navigation doit de nouveau protéger contre une sortie sans enregistrer. Ne fait rien tant qu'on
    /// est déjà en brouillon (saisie initiale).
    private void redevenirBrouillonSiModifie() {
        if (etatVerdict.get() == EtatVerdict.ENREGISTRE) {
            etatVerdict.set(EtatVerdict.BROUILLON);
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
            message.set("Choisissez un verdict (OK, Douteux ou À jeter) avant d'enregistrer.");
            return;
        }
        try {
            service.enregistrerVerdict(idPassage, verdictChoisi.get(), commentaireOuNull());
            verdictActuel.set(verdictChoisi.get());
            statut.set(StatutWorkflow.VERIFIE);
            avertissementAJeter.set(
                    service.estAJeter(idPassage) ? "Passage marqué « à jeter » : il ne pourra pas être déposé." : "");
            message.set("");
            etatVerdict.set(EtatVerdict.ENREGISTRE);
        } catch (RuntimeException refus) {
            message.set(refus.getMessage());
        }
    }

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
