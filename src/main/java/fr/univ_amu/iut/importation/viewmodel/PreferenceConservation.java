package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Reglages;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/// Préférence **« conserver les originaux »** de l'import, extraite de [ImportationViewModel] (Extract
/// Class) : porte le choix éditable (la case de la vue), son défaut persisté et sa mémorisation.
///
/// **Partagée** (singleton) entre la **vue** (qui lie bidirectionnellement la case) et le **ViewModel**
/// (qui lit la valeur au lancement de l'import), de sorte que le choix survive à la réouverture de
/// l'écran — le ViewModel, lui, est recréé à chaque chargement FXML, alors que cette préférence, non.
///
/// VM-agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seul `javafx.beans` est importé,
/// jamais `javafx.scene`. La persistance passe par le service socle [Reglages] (jamais un DAO).
public final class PreferenceConservation {

    /// Clé du réglage persisté (cf. [Reglages]).
    static final String CLE = "import.conserver-originaux";

    private final Reglages reglages;
    private final BooleanProperty conserverOriginaux = new SimpleBooleanProperty(this, "conserverOriginaux", false);

    public PreferenceConservation(Reglages reglages) {
        this.reglages = Objects.requireNonNull(reglages, "reglages");
        // Restaure le dernier choix. Défaut : **ne pas conserver** (#2063). Copier les bruts coûte
        // plusieurs Go et les deux tiers du temps d'import, pour un service dont rien dans
        // l'application ne dépend : c'est une option de ré-analyse, pas un dû.
        //
        // Une installation qui a déjà importé porte sa valeur en base : `lireBooleen` ne retombe sur le
        // défaut que si la clé est absente. On ne change donc pas dans son dos le choix de quelqu'un
        // qui l'a déjà fait — seules les installations neuves basculent.
        conserverOriginaux.set(reglages.lireBooleen(CLE, false));
    }

    /// Propriété **éditable** : la vue y lie bidirectionnellement sa case à cocher (`true` = copie dans
    /// `bruts/`, `false` = transformation directe depuis la source).
    public BooleanProperty conserverOriginauxProperty() {
        return conserverOriginaux;
    }

    /// Valeur courante du choix (`true` = conserver les originaux).
    public boolean valeur() {
        return conserverOriginaux.get();
    }

    /// Mémorise le choix courant (persistance), pour qu'il survive d'une session à l'autre. À appeler au
    /// lancement d'un import.
    public void memoriser() {
        reglages.ecrireBooleen(CLE, conserverOriginaux.get());
    }
}
