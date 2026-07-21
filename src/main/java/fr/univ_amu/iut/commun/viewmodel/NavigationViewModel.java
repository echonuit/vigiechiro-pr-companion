package fr.univ_amu.iut.commun.viewmodel;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/// ViewModel du socle de navigation : porte l'état observable du chrome principal.
///
/// Quatre informations transverses, exposées en propriétés JavaFX observables :
///  - le titre de l'application (barre haute),
///  - la vue courante (identifiant logique de la zone centrale affichée),
///  - le fil d'Ariane (libellé lisible du chemin de navigation),
///  - les trois zones de la barre de statut (pied du chrome : identité / résumé / compteurs).
///
/// Cette classe est volontairement **agnostique de l'IHM** : elle n'importe que
/// `javafx.beans.property` (le modèle observable), jamais `javafx.scene`, `javafx.fxml`
/// ni `javafx.stage`. La règle ArchUnit `viewmodel_sans_javafx_ui` verrouille cette frontière.
/// La vue (controllers FXML) se lie à ces propriétés ; le [fr.univ_amu.iut.commun.view.Navigateur]
/// les met à jour quand une feature change de zone centrale.
public class NavigationViewModel {

    /// Zones par défaut de la barre de statut : **toutes vides**. Quand l'écran courant ne fournit pas de
    /// résumé (cf. [fr.univ_amu.iut.commun.view.ResumeStatut]), le chrome masque la barre plutôt que
    /// d'afficher une mention sans information. Le [fr.univ_amu.iut.commun.view.Navigateur] y revient à
    /// chaque changement d'écran ; un écran ne renseigne que les zones qui le concernent (les autres
    /// restent vides par superposition, cf. [ZonesStatut#superposer]).
    public static final ZonesStatut ZONES_DEFAUT = ZonesStatut.VIDE;

    private final StringProperty titreApplication =
            new SimpleStringProperty(this, "titreApplication", "VigieChiro Companion");
    private final StringProperty vueCourante = new SimpleStringProperty(this, "vueCourante", "accueil");
    private final StringProperty filAriane = new SimpleStringProperty(this, "filAriane", "Accueil");
    private final ObjectProperty<ZonesStatut> zonesStatut =
            new SimpleObjectProperty<>(this, "zonesStatut", ZONES_DEFAUT);

    /// Libellé de l'**opération critique** en cours (« l'import », « la génération des archives », « le
    /// dépôt »…), ou chaîne vide si aucune (#906, ex-#54). Une telle opération ne doit pas être interrompue
    /// à la légère : sortir de l'écran ou fermer l'application demande confirmation (le
    /// [fr.univ_amu.iut.commun.view.Navigateur] et `App` consultent ce libellé). La feature concernée pose
    /// le libellé au démarrage de l'opération et le remet à vide à sa fin.
    private final StringProperty operationCritique = new SimpleStringProperty(this, "operationCritique", "");

    /// `true` tant qu'une opération critique est en cours, dérivé de [#operationCritiqueProperty].
    private final BooleanBinding navigationVerrouillee = operationCritique.isNotEmpty();

    /// Propriété observable du titre de l'application affiché dans la barre haute.
    public StringProperty titreApplicationProperty() {
        return titreApplication;
    }

    public String getTitreApplication() {
        return titreApplication.get();
    }

    public void setTitreApplication(String valeur) {
        titreApplication.set(valeur);
    }

    /// Propriété observable de la vue courante (identifiant logique, ex. `accueil`, `sites`).
    public StringProperty vueCouranteProperty() {
        return vueCourante;
    }

    public String getVueCourante() {
        return vueCourante.get();
    }

    public void setVueCourante(String valeur) {
        vueCourante.set(valeur);
    }

    /// Propriété observable du fil d'Ariane (libellé lisible affiché à l'utilisateur).
    public StringProperty filArianeProperty() {
        return filAriane;
    }

    public String getFilAriane() {
        return filAriane.get();
    }

    public void setFilAriane(String valeur) {
        filAriane.set(valeur);
    }

    /// Propriété observable des trois zones de la barre de statut (identité / résumé / compteurs).
    public ObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut;
    }

    public ZonesStatut getZonesStatut() {
        return zonesStatut.get();
    }

    public void setZonesStatut(ZonesStatut valeur) {
        zonesStatut.set(valeur);
    }

    /// Libellé de l'opération critique en cours (vide si aucune, #906). Sortir de l'écran ou fermer l'app
    /// pendant qu'il est non vide déclenche une confirmation.
    public StringProperty operationCritiqueProperty() {
        return operationCritique;
    }

    public String operationCritique() {
        return operationCritique.get();
    }

    /// Pose le libellé de l'opération critique en cours (au démarrage), ou l'efface (`""`/`null`, à la fin).
    public void setOperationCritique(String libelle) {
        operationCritique.set(libelle == null ? "" : libelle);
    }

    /// `true` tant qu'une opération critique est en cours (dérivé de [#operationCritiqueProperty]). Conservé
    /// pour les consommateurs qui n'ont besoin que du booléen (#906, ex-#54).
    public BooleanExpression navigationVerrouilleeProperty() {
        return navigationVerrouillee;
    }

    public boolean isNavigationVerrouillee() {
        return navigationVerrouillee.get();
    }

    /// Met à jour l'état de navigation en une étape : la vue courante et son libellé de fil
    /// d'Ariane. Les features appellent cette méthode quand elles prennent la main sur la zone
    /// centrale, pour garder le chrome cohérent avec le contenu affiché.
    public void naviguerVers(String vue, String libelleFilAriane) {
        vueCourante.set(vue);
        filAriane.set(libelleFilAriane);
    }
}
