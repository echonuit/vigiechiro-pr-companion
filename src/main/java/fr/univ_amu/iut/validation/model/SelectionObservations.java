package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.util.List;
import java.util.Objects;

/// **Désigner** les observations sur lesquelles agir (#1311), par **filtres** - la moitié que la
/// ligne de commande n'avait pas, l'autre étant la désignation par identifiants, portée par les
/// commandes.
///
/// L'écran raisonne par sélection dans une table ; la ligne de commande n'en a pas. **La règle** :
/// c'est le **même** code qui choisit, pour lister et pour agir. `lister-observations --passage 3
/// --statut a-revoir` et `valider-observations --passage 3 --statut a-revoir` voient **exactement**
/// le même ensemble - sans quoi un geste irréversible piloté par un filtre serait un pari.
public class SelectionObservations {

    private final ProjectionsAudioDao projections;
    private final MarqueurEspecesAEnjeu marqueurEnjeu;

    public SelectionObservations(ProjectionsAudioDao projections, EspecesPrioritaires especesPrioritaires) {
        this.projections = Objects.requireNonNull(projections, "projections");
        this.marqueurEnjeu = new MarqueurEspecesAEnjeu(especesPrioritaires);
    }

    /// Les lignes du passage qui passent les critères, dans l'ordre de la projection (celui de l'écran).
    public List<LigneObservationAudio> lignes(Long idPassage, CriteresRevue criteres) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(criteres, "criteres");
        return projections.lignesAudioDuPassage(idPassage).stream()
                .filter(criteres::retient)
                .filter(ligne -> criteres.retientLEnjeu(marqueurEnjeu.aEnjeu(ligne.taxonRetenu())))
                .toList();
    }

    /// Les **identifiants** des lignes retenues : ce que les gestes de revue consomment.
    ///
    /// @throws RegleMetierException si le passage n'a **aucune** observation retenue - un geste qui ne
    ///     toucherait rien est presque toujours une erreur de filtre, et le dire vaut mieux que de
    ///     répondre « 0 observation traitée » comme si de rien n'était
    public List<Long> ids(Long idPassage, CriteresRevue criteres) {
        List<LigneObservationAudio> retenues = lignes(idPassage, criteres);
        if (retenues.isEmpty()) {
            throw new RegleMetierException("Aucune observation du passage " + idPassage
                    + " ne correspond aux filtres. Vérifiez-les avec « lister-observations --passage "
                    + idPassage + " ».");
        }
        return retenues.stream().map(LigneObservationAudio::idObservation).toList();
    }
}
