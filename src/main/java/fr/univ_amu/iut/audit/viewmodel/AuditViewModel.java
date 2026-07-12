package fr.univ_amu.iut.audit.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.audit.model.SeveriteConstat;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran **Audit de cohérence** : lance l'audit **global** ([ServiceAuditCoherence#auditerTout])
/// et expose ses constats en liste observable plus un résumé lisible. État observable seul (aucune
/// dépendance à la scène) ; l'écran le rafraîchit à l'ouverture et sur demande.
public class AuditViewModel {

    private final ServiceAuditCoherence service;
    private final ObservableList<ConstatAudit> constats = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final ReadOnlyBooleanWrapper sain = new ReadOnlyBooleanWrapper(this, "sain", true);

    @Inject
    public AuditViewModel(ServiceAuditCoherence service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// (Re)lance l'audit global et met à jour la liste des constats + le résumé.
    public void rafraichir() {
        RapportAudit rapport = service.auditerTout();
        constats.setAll(rapport.constats());
        sain.set(rapport.sain());
        resume.set(
                rapport.sain()
                        ? "Cohérence disque / base : aucun écart détecté."
                        : rapport.constats().size()
                                + " écart(s) : "
                                + rapport.nombre(SeveriteConstat.ERREUR)
                                + " erreur(s), "
                                + rapport.nombre(SeveriteConstat.AVERTISSEMENT)
                                + " avertissement(s), "
                                + rapport.nombre(SeveriteConstat.INFO)
                                + " info(s).");
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
