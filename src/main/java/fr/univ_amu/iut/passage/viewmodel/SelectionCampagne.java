package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// Sous-ViewModel du **rattachement à une campagne** (facultatif, #2355) de la modale « Modifier le
/// passage ». Extrait de [RattachementViewModel] pour ne pas l'alourdir (Extract Class).
///
/// **Optionnel** : la feature `campagne` est désactivable. Absent le service, il ne propose ni
/// n'enregistre rien ([activee] renvoie `false`) et la modale masque son champ. Pur (`javafx.beans` /
/// `javafx.collections` uniquement, règle `viewmodel_sans_javafx_ui`).
public class SelectionCampagne {

    private final Optional<ServiceCampagne> service;
    private final ObservableList<Campagne> campagnes = FXCollections.observableArrayList();
    private final ObjectProperty<Campagne> selection = new SimpleObjectProperty<>(this, "selection");

    public SelectionCampagne(Optional<ServiceCampagne> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// La feature `campagne` est-elle active (donc le champ campagne affiché) ?
    public boolean activee() {
        return service.isPresent();
    }

    /// Peuple la liste des campagnes (sentinelle `null` « aucune » en tête) et pré-sélectionne celle du
    /// passage. Sans effet si la feature est coupée.
    public void charger(Long idPassage) {
        service.ifPresent(svc -> {
            campagnes.clear();
            campagnes.add(null); // sentinelle « aucune campagne » en tête (patron des combos de la modale)
            campagnes.addAll(svc.listerCampagnes());
            selection.set(svc.campagneDePassage(idPassage).orElse(null));
        });
    }

    /// Recharge **la liste seule**, en conservant le choix en cours (#2630).
    ///
    /// Appelée au retour de « Gérer les campagnes » : une campagne créée là doit apparaître ici sans
    /// rouvrir la modale. Volontairement distincte de [#charger] : celle-là **repositionne** la
    /// sélection sur la campagne persistée du passage, ce qui jetterait un choix que l'utilisateur
    /// vient de faire et n'a pas encore appliqué.
    ///
    /// La campagne sélectionnée est ré-identifiée par son `id` : le rechargement en produit de
    /// nouvelles instances, et un rapprochement par référence perdrait la sélection à chaque fois.
    public void rechargerListe() {
        service.ifPresent(svc -> {
            Long idSelectionne =
                    selection.get() == null ? null : selection.get().id();
            campagnes.clear();
            campagnes.add(null); // sentinelle « aucune campagne » en tête
            campagnes.addAll(svc.listerCampagnes());
            selection.set(campagnes.stream()
                    .filter(Objects::nonNull)
                    .filter(campagne -> campagne.id().equals(idSelectionne))
                    .findFirst()
                    .orElse(null));
        });
    }

    /// Persiste le rattachement sélectionné (ou le détachement si aucune). Sans effet si la feature est
    /// coupée.
    public void enregistrer(Long idPassage) {
        service.ifPresent(svc -> svc.rattacherPassage(
                idPassage, selection.get() == null ? null : selection.get().id()));
    }

    /// Campagnes proposées au ComboBox (vide si la feature est coupée).
    public ObservableList<Campagne> campagnes() {
        return campagnes;
    }

    /// Campagne sélectionnée (`null` = aucune) ; liée en bidirectionnel au ComboBox.
    public ObjectProperty<Campagne> selectionProperty() {
        return selection;
    }
}
