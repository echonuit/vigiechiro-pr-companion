package fr.univ_amu.iut.audit.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.audit.model.SeveriteConstat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran **Audit de cohérence** : lance l'audit **global** disque / base
/// ([ServiceAuditCoherence#auditerTout]) et, à la demande, la vérification **en ligne**
/// ([ServiceAuditCoherence#auditerEnLigne]). Expose les constats en liste observable + un résumé lisible.
/// État observable seul (aucune dépendance à la scène) ; l'écran l'applique sur le fil JavaFX.
public class AuditViewModel {

    private final ServiceAuditCoherence service;
    private final ObservableList<ConstatAudit> constats = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final ReadOnlyBooleanWrapper sain = new ReadOnlyBooleanWrapper(this, "sain", true);

    @Inject
    public AuditViewModel(ServiceAuditCoherence service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// (Re)lance l'audit **disque / base** (hors ligne, rapide) et applique le résultat.
    public void rafraichir() {
        appliquer(service.auditerTout().constats());
    }

    /// Calcule l'audit **complet** : disque / base **puis** vérification en ligne. Fait des lectures base et
    /// des appels réseau : à appeler **hors fil JavaFX** ; le résultat est ensuite posé via [#appliquer].
    public List<ConstatAudit> calculerAvecEnLigne() {
        List<ConstatAudit> tous = new ArrayList<>(service.auditerTout().constats());
        tous.addAll(service.auditerEnLigne().constats());
        return tous;
    }

    /// Applique une liste de constats (liste observable + résumé + drapeau sain). À exécuter sur le fil
    /// JavaFX (mutation de la liste observable).
    public void appliquer(List<ConstatAudit> nouveaux) {
        RapportAudit rapport = new RapportAudit(nouveaux);
        constats.setAll(rapport.constats());
        sain.set(rapport.sain());
        resume.set(resume(rapport));
    }

    /// Restitue un échec de la vérification en ligne dans le résumé (filet d'erreurs #795) : les constats
    /// de l'audit hors ligne restent affichés, seul le bandeau explique l'échec.
    public void signalerErreur(Throwable erreur) {
        String detail = erreur.getMessage();
        resume.set("Vérification en ligne impossible : "
                + (detail != null && !detail.isBlank() ? detail : "erreur inattendue."));
    }

    private static String resume(RapportAudit rapport) {
        if (rapport.sain()) {
            return "Cohérence : aucun écart détecté.";
        }
        return rapport.constats().size()
                + " écart(s) : "
                + rapport.nombre(SeveriteConstat.ERREUR)
                + " erreur(s), "
                + rapport.nombre(SeveriteConstat.AVERTISSEMENT)
                + " avertissement(s), "
                + rapport.nombre(SeveriteConstat.INFO)
                + " info(s).";
    }

    public ObservableList<ConstatAudit> constats() {
        return constats;
    }

    public ReadOnlyStringProperty resumeProperty() {
        return resume.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty sainProperty() {
        return sain.getReadOnlyProperty();
    }
}
