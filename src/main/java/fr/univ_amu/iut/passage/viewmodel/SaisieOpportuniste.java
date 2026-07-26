package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.passage.model.ServicePassage;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/// Sous-ViewModel de la **nature opportuniste** (#2525) de la modale « Modifier le passage » : une
/// participation réalisée sur le carré d'un tiers, exemptée de R3/R4. Extrait de [RattachementViewModel]
/// pour ne pas l'alourdir (Extract Class, comme [SelectionCampagne]).
///
/// Contrairement à la campagne (feature désactivable), l'opportunisme est un attribut **COEUR** du
/// passage, toujours disponible : le service est donc requis, pas optionnel. Pur (`javafx.beans`
/// uniquement, règle `viewmodel_sans_javafx_ui`).
public class SaisieOpportuniste {

    private final ServicePassage service;
    private final BooleanProperty opportuniste = new SimpleBooleanProperty(this, "opportuniste", false);

    public SaisieOpportuniste(ServicePassage service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// Charge l'état opportuniste courant du passage `idPassage`.
    public void charger(Long idPassage) {
        opportuniste.set(service.estOpportuniste(idPassage));
    }

    /// Persiste l'état opportuniste saisi (marque ou démarque le passage `idPassage`).
    public void enregistrer(Long idPassage) {
        service.marquerOpportuniste(idPassage, opportuniste.get());
    }

    /// Nature opportuniste ; liée en bidirectionnel à la case de la modale.
    public BooleanProperty opportunisteProperty() {
        return opportuniste;
    }
}
