package fr.univ_amu.iut.audit.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.model.ContexteAuditPassage;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

/// ViewModel de l'écran **Audit de cohérence** : lance l'audit **global** disque / base
/// ([ServiceAuditCoherence#auditerTout]) et, à la demande, la vérification **en ligne**
/// ([ServiceAuditCoherence#auditerEnLigne]). Expose les constats en liste observable + un résumé lisible.
/// État observable seul (aucune dépendance à la scène) ; l'écran l'applique sur le fil JavaFX.
public class AuditViewModel {

    private final ServiceAuditCoherence service;
    private final ObservableList<ConstatAudit> constats = FXCollections.observableArrayList();

    /// Les constats **retenus par la barre de filtres** (#3100). C'est cette liste que la table montre ;
    /// [#constats] reste le résultat entier de l'audit, sur lequel se calculent le résumé et le verdict.
    /// Masquer des lignes n'efface pas des constats.
    private final FilteredList<ConstatAudit> constatsFiltres = new FilteredList<>(constats);

    private final Filtres<ConstatAudit> filtres = new Filtres<>(constatsFiltres, () -> {});
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final ReadOnlyBooleanWrapper sain = new ReadOnlyBooleanWrapper(this, "sain", true);
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

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

    /// Audit **ciblé** sur un passage (#1347) : `ServiceAuditCoherence.auditerPassage` existait depuis
    /// #1133, mais l'IHM n'exposait que l'audit global. Après avoir réparé une nuit, on veut vérifier
    /// **cette** nuit, pas tout le workspace : surtout quand il en compte des dizaines.
    public void auditerPassage(Long idPassage) {
        appliquer(service.auditerPassage(idPassage).constats());
        resume.set("Audit du passage " + idPassage + " : " + resumeProperty().get());
    }

    /// Site et point du passage cité par un constat, pour l'**ouvrir** (#1347). Vide si le constat ne cite
    /// aucun passage, ou si son site est introuvable.
    public Optional<ContexteAuditPassage> contexteDuPassage(Long idPassage) {
        return idPassage == null ? Optional.empty() : service.contexteDuPassage(idPassage);
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
                + rapport.nombre(Severite.ERREUR)
                + " erreur(s), "
                + rapport.nombre(Severite.AVERTISSEMENT)
                + " avertissement(s), "
                + rapport.nombre(Severite.INFO)
                + " info(s).";
    }

    public ObservableList<ConstatAudit> constats() {
        return constats;
    }

    /// Les constats que la barre de filtres laisse passer (#3100) : ce que la **table** montre.
    public ObservableList<ConstatAudit> constatsFiltres() {
        return constatsFiltres;
    }

    /// Socle de filtres composables (#537) sur les constats : la barre à puces de l'écran y branche et
    /// y retire ses prédicats (gravité, catégorie, passage, recherche).
    public Filtres<ConstatAudit> filtres() {
        return filtres;
    }

    /// Retour de la **dernière opération** avec sa sévérité, pour le bandeau de l'écran.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme).
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }

    /// Signale qu'une **vue mémorisée** vient d'être rejouée **amputée** de valeurs devenues
    /// introuvables (#3056) : elle filtre moins large qu'à son enregistrement.
    public void signalerVueAmputee(String nomVue, ResteDeRestauration reste) {
        retour.set(RetourOperation.vueAmputee(nomVue, reste));
    }

    /// Les filtres de la **mémoire de session** (#484, étendue en #3098) que la réouverture de l'écran
    /// n'a pas su remettre en place (#3093) : les constats ont changé depuis la dernière visite, parce
    /// qu'entre-temps l'audit a été relancé. C'est le chemin le plus discret des trois, puisque
    /// personne n'a rien demandé.
    public void signalerFiltresDeSessionAmputes(ResteDeRestauration reste) {
        retour.set(RetourOperation.filtresDeSessionAmputes(reste));
    }

    public ReadOnlyStringProperty resumeProperty() {
        return resume.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty sainProperty() {
        return sain.getReadOnlyProperty();
    }
}
