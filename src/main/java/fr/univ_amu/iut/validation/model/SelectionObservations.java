package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.util.List;
import java.util.Objects;

/// **Désigner** les observations sur lesquelles agir (#1311) - la vraie difficulté de la parité CLI.
///
/// L'écran raisonne par **sélection dans une table** : on voit les lignes, on en surligne quelques-unes,
/// on agit. La ligne de commande n'a pas de table. Elle offre donc **deux** manières de désigner, et une
/// seule règle :
///
/// 1. **Par identifiants** (`--observation 12,13,14`) : le geste chirurgical, sur des lignes qu'on a
///    **lues** (c'est `lister-observations` qui les donne). Un identifiant inconnu **arrête tout** - le lot
///    est atomique, on ne valide pas 2 lignes sur 3 en laissant l'utilisateur deviner laquelle a échoué.
/// 2. **Par filtres** (`--passage 3 --statut a-revoir`) : le geste scripté, reproductible, sur un
///    sous-ensemble décrit.
///
/// **La règle** : c'est le **même** code qui choisit, pour lister et pour agir. `lister-observations
/// --passage 3 --statut a-revoir` et `valider-observations --passage 3 --statut a-revoir` voient
/// **exactement** le même ensemble. Sans ça, un geste irréversible piloté par un filtre serait un pari :
/// on ne saurait pas ce qu'on touche avant de l'avoir touché.
public class SelectionObservations {

    private final ProjectionsAudioDao projections;

    public SelectionObservations(ProjectionsAudioDao projections) {
        this.projections = Objects.requireNonNull(projections, "projections");
    }

    /// Les lignes du passage qui passent les critères, dans l'ordre de la projection (celui de l'écran).
    public List<LigneObservationAudio> lignes(Long idPassage, CriteresRevue criteres) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(criteres, "criteres");
        return projections.lignesAudioDuPassage(idPassage).stream()
                .filter(criteres::retient)
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
