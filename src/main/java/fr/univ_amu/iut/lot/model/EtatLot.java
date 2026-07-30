package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import java.util.List;

/// Projection de lecture pour l'écran **M-Lot** : l'état de dépôt d'un passage, **sans** le
/// transitionner (contrairement à [ServiceLot#preparerLot] / [ServiceLot#marquerDepose]).
///
/// Permet à l'IHM de savoir quoi proposer selon l'avancement : préparer le lot (depuis `Vérifié`),
/// marquer déposé (depuis `Prêt à déposer`), ou simplement constater le dépôt (`Déposé`). Le chemin
/// du dossier (R22) est la cible du téléversement manuel, le volume et le nombre de séquences en
/// donnent l'ordre de grandeur, et la **checklist de cohérence** ([#controles]) montre, contrôle par
/// contrôle, ce que « Préparer le lot » vérifie (#254) : les contrôles en échec bloquent la préparation.
///
/// @param statut statut workflow courant du passage
/// @param cheminDossier chemin du dossier de session à téléverser (R22), `null` si pas de session
/// @param nombreSequences nombre de séquences transformées du lot
/// @param volumeSequencesOctets volume total des séquences en octets, `null` si non calculé
/// @param controles checklist des contrôles de cohérence (✓ / ✗ / ⚠) ; un contrôle en échec bloque
/// @param deposeLe date de dépôt (ISO), `null` tant que le passage n'est pas déposé
public record EtatLot(
        StatutWorkflow statut,
        String cheminDossier,
        int nombreSequences,
        Long volumeSequencesOctets,
        List<ControleCoherence> controles,
        String deposeLe) {

    public EtatLot {
        controles = List.copyOf(controles);
    }

    /// `true` si au moins un contrôle de cohérence **échoue** (préparation bloquée).
    public boolean aDesEchecs() {
        return controles.stream().anyMatch(ControleCoherence::estBloquant);
    }
}
