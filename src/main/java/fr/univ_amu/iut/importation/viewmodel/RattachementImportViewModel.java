package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// Sous-ViewModel de **M-Import** — étape 3 : **rattachement** de la nuit (site / point / année /
/// n° de passage) et **aperçu du préfixe** Vigie-Chiro (R6).
///
/// Extrait de [ImportationViewModel] (#183) pour le décharger : cet objet ne porte **que** l'état du
/// rattachement, sans rien savoir de l'inspection ni de l'exécution de l'import. L'orchestrateur
/// ([ImportationViewModel]) le **compose** : il lit [#estComplet()] pour `peutImporter`, assemble la
/// demande d'import depuis [#idPointSelectionne()] / [#prefixeCourant()], et lui fournit le rapport
/// d'inspection courant ([#definirRapport]) qui sert d'exemple de nom pour l'aperçu.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls `javafx.beans` /
/// `javafx.collections` sont importés, jamais `javafx.scene`.
public class RattachementImportViewModel {

    private final ServiceSites serviceSites;
    private final String idUtilisateur;

    private final ObservableList<Site> sites = FXCollections.observableArrayList();
    private final ObjectProperty<Site> siteSelectionne = new SimpleObjectProperty<>(this, "siteSelectionne");
    private final ObservableList<PointDEcoute> points = FXCollections.observableArrayList();
    private final ObjectProperty<PointDEcoute> pointSelectionne = new SimpleObjectProperty<>(this, "pointSelectionne");
    private final IntegerProperty annee = new SimpleIntegerProperty(this, "annee");
    private final IntegerProperty numeroPassage = new SimpleIntegerProperty(this, "numeroPassage", 1);
    private final ReadOnlyStringWrapper apercuPrefixe = new ReadOnlyStringWrapper(this, "apercuPrefixe", "");

    /// Exemple de nom d'origine **fourni par l'orchestrateur** (dérivé de l'inspection) : sert de
    /// gabarit pour l'aperçu du préfixe. `null` tant qu'aucune inspection n'a réussi (un gabarit
    /// générique est alors utilisé). Le rattachement ne dépend ainsi pas du sous-VM d'inspection.
    private String exempleNomOriginal;

    public RattachementImportViewModel(ServiceSites serviceSites, Horloge horloge, String idUtilisateur) {
        this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        Objects.requireNonNull(horloge, "horloge");

        // Valeur initiale avant d'installer les écouteurs (évite un recalcul d'aperçu prématuré).
        annee.set(horloge.aujourdhui().getYear());

        // Changer de site recharge ses points et réinitialise le point sélectionné.
        siteSelectionne.addListener((obs, ancien, nouveau) -> {
            points.setAll(nouveau == null ? List.of() : serviceSites.listerPoints(nouveau.id()));
            pointSelectionne.set(null);
            majApercu();
        });
        pointSelectionne.addListener((obs, ancien, nouveau) -> majApercu());
        annee.addListener((obs, ancien, nouveau) -> majApercu());
        numeroPassage.addListener((obs, ancien, nouveau) -> majApercu());
    }

    /// Recharge les sites de l'utilisateur courant (à l'ouverture de l'écran ou après création d'un site).
    public void chargerSites() {
        sites.setAll(serviceSites.listerSites(idUtilisateur));
    }

    /// `true` si le rattachement est complet : site + point + n° de passage valides. Condition
    /// nécessaire de [ImportationViewModel#peutImporter()].
    public boolean estComplet() {
        return siteSelectionne.get() != null && pointSelectionne.get() != null && numeroPassage.get() >= 1;
    }

    /// Identifiant du point d'écoute sélectionné, pour assembler la demande d'import. Précondition :
    /// rattachement complet ([#estComplet()] vrai).
    public Long idPointSelectionne() {
        return pointSelectionne.get().id();
    }

    /// Préfixe Vigie-Chiro courant (R6) déduit du rattachement. Précondition : rattachement complet.
    public Prefixe prefixeCourant() {
        Site site = siteSelectionne.get();
        return new Prefixe(
                site.numeroCarre(),
                annee.get(),
                numeroPassage.get(),
                pointSelectionne.get().code());
    }

    /// Fournit (orchestrateur) l'exemple de nom d'origine pour l'aperçu — dérivé de l'inspection — ou
    /// `null` pour le réinitialiser ; recalcule l'aperçu en conséquence.
    public void definirExempleNom(String exempleNomOriginal) {
        this.exempleNomOriginal = exempleNomOriginal;
        majApercu();
    }

    /// Liste observable des sites de l'utilisateur (combobox Site), alimentée par [#chargerSites()].
    public ObservableList<Site> sites() {
        return sites;
    }

    /// Site auquel rattacher la nuit (sélection dans la combobox).
    public ObjectProperty<Site> siteSelectionneProperty() {
        return siteSelectionne;
    }

    /// Points du site sélectionné (recalculés à chaque changement de site).
    public ObservableList<PointDEcoute> points() {
        return points;
    }

    /// Point d'écoute auquel rattacher la nuit.
    public ObjectProperty<PointDEcoute> pointSelectionneProperty() {
        return pointSelectionne;
    }

    /// Année du passage (préremplie à l'année de l'horloge applicative).
    public IntegerProperty anneeProperty() {
        return annee;
    }

    /// Numéro de passage dans l'année pour ce point (défaut 1, éditable).
    public IntegerProperty numeroPassageProperty() {
        return numeroPassage;
    }

    /// Aperçu du nom préfixé appliqué aux fichiers (R6), recalculé dès qu'un champ change ; vide tant
    /// que le site ou le point n'est pas choisi.
    public ReadOnlyStringProperty apercuPrefixeProperty() {
        return apercuPrefixe.getReadOnlyProperty();
    }

    private void majApercu() {
        apercuPrefixe.set(ApercuPrefixe.calculer(
                siteSelectionne.get(), pointSelectionne.get(), annee.get(), numeroPassage.get(), exempleNomOriginal));
    }
}
