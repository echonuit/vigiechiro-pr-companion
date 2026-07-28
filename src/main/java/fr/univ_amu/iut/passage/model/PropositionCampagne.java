package fr.univ_amu.iut.passage.model;

import java.util.List;
import java.util.Optional;

/// Port de **campagne à l'import** (#2631) : ce dont `importation` a besoin des campagnes, et rien de
/// plus.
///
/// Trois verbes, parce que l'assistant d'import fait trois choses : lister ce qu'on peut proposer,
/// deviner ce qu'il faut proposer sur un point donné, et rattacher les nuits créées. Interface étroite
/// à l'image de [MarquageOpportuniste] : `importation` crée des passages sans être `passage`, et n'a
/// pas à dépendre de tout [ServiceCampagne] pour autant.
///
/// **Optionnel côté consommateur** : la fonctionnalité `campagne` est désactivable. Absente, l'import
/// se comporte exactement comme avant - ni liste, ni proposition, ni rattachement.
public interface PropositionCampagne {

    /// Campagnes existantes, pour peupler la liste déroulante du rattachement.
    List<Campagne> campagnes();

    /// Campagne du **dernier passage** de ce point, à proposer par défaut. Vide si aucun n'en porte :
    /// c'est un cas normal, le rattachement étant facultatif.
    Optional<Campagne> proposerPour(Long idPoint);

    /// Rattache une nuit importée à `idCampagne`, ou l'en détache si `null`.
    void rattacher(long idPassage, Long idCampagne);
}
