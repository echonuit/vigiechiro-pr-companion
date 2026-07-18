package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.Taxon;
import java.util.List;

/// Données d'ouverture de la vue audio, chargées **hors du fil JavaFX** (#1214) : référentiel des
/// taxons, id des résultats, lignes d'observation de la source, disponibilité de l'audio du
/// passage (#1301, `null` quand la source n'en cible pas un seul) et **absence d'ancrage plateforme**
/// (#1596 : passage reconstruit par CSV non réactivé, `false` hors passage unique). Lecture seule
/// (aucune mutation observable) ; [AudioViewModel#appliquerOuverture] les publie sur le fil JavaFX.
public record DonneesOuverture(
        List<Taxon> taxons,
        Long idResultats,
        List<LigneObservationAudio> lignes,
        DecompteAudio decompteAudio,
        boolean publicationImpossible) {}
