package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// Sous-ViewModel de **M-Passage** : « Importer les observations » (#1350). Extrait de
/// [PassageViewModel] (qui passait God Class), sur le patron de `SaisiePassageConditions` — un
/// sous-VM par préoccupation, l'écran se contentant de le porter.
///
/// L'action vit sur M-Passage et non plus dans la modale « Modifier le passage » : celle-ci est fermée
/// sur un passage déposé (garde de renommage #1134), c'est-à-dire exactement le cas où l'import a le
/// plus de sens. **L'import ne renomme rien** : il n'a pas à subir cette garde.
///
/// Le gating est donc volontairement **indépendant du statut** du passage. Ce qui compte est que
/// l'import soit disponible (observateur connecté) et que la nuit soit **liée à une participation** :
/// sans lien, il n'y a rien à aller chercher.
///
/// Pur (aucun `javafx.scene` — règle ArchUnit `viewmodel_sans_javafx_ui`).
public class ImportObservationsPassage {

    /// Le port du socle (#1264), absent hors connexion.
    private final Optional<ImportObservations> port;

    private final ReadOnlyBooleanWrapper possible = new ReadOnlyBooleanWrapper(this, "possible", false);
    private final ReadOnlyStringWrapper motifBlocage = new ReadOnlyStringWrapper(this, "motifBlocage", "");

    private Long idPassage;

    public ImportObservationsPassage(Optional<ImportObservations> port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    /// Recalcule le gating pour le passage affiché.
    ///
    /// `estRattache` est une lecture **locale** (table des liens), pas un appel réseau : elle peut donc se
    /// faire sur le fil JavaFX, à l'application de la projection.
    void charger(Long idPassage) {
        this.idPassage = idPassage;
        boolean ouvert = idPassage != null && port.isPresent() && port.get().estRattache(idPassage);
        possible.set(ouvert);
        motifBlocage.set(motif(ouvert));
    }

    void reinitialiser() {
        idPassage = null;
        possible.set(false);
        motifBlocage.set("");
    }

    /// Pourquoi l'import est refusé. Le bouton reste visible et grisé (affordance #789) : il documente ce
    /// qui manque plutôt que de disparaître.
    private String motif(boolean ouvert) {
        if (ouvert) {
            return "";
        }
        if (port.isEmpty()) {
            return "Import indisponible hors connexion : connectez-vous à VigieChiro pour récupérer les"
                    + " résultats de l'analyse.";
        }
        return "Ce passage n'est pas encore lié à une participation VigieChiro : il n'y a rien à importer."
                + " Le lien se crée au dépôt, ou à la main depuis « Sons & validation ».";
    }

    /// Importe les observations de la nuit depuis Vigie-Chiro. **Bloquant** (réseau) : à appeler **hors du
    /// fil JavaFX**.
    ///
    /// Renvoie le compte rendu à afficher — ou, s'il n'y a rien à importer, **la raison** (analyse jamais
    /// lancée, en cours, en échec…), que l'import sait donner (#1264). Un refus qui renseigne n'est pas un
    /// incident : c'est une réponse, et elle dit quoi faire.
    public String importer() {
        try {
            return port.orElseThrow(() -> new RegleMetierException("Import VigieChiro indisponible dans ce contexte."))
                    .importer(idPassage, false);
        } catch (RegleMetierException rien) {
            return rien.getMessage();
        }
    }

    /// `true` si « Importer les observations » a lieu d'être : import disponible **et** nuit liée à une
    /// participation. Indépendant du statut du passage, à dessein (#1350).
    public ReadOnlyBooleanProperty possibleProperty() {
        return possible.getReadOnlyProperty();
    }

    /// Motif du blocage (tooltip de l'enveloppe, #789) ; vide quand l'import est possible.
    public ReadOnlyStringProperty motifBlocageProperty() {
        return motifBlocage.getReadOnlyProperty();
    }
}
