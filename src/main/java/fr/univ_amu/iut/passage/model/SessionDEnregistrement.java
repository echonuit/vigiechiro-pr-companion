package fr.univ_amu.iut.passage.model;

import java.time.LocalDateTime;

/// Session d'enregistrement : agrégat de données produit par un passage (C6, table
/// `recording_session`). Relation **1:1 stricte** avec le passage (`passage_id` unique) : la
/// session regroupe les enregistrements originaux, les séquences d'écoute, le journal du capteur
/// et l'éventuel relevé climatique d'une même nuit.
///
/// Les volumes ([#volumeOriginauxOctets], [#volumeSequencesOctets]) sont des champs **dérivés**
/// (calculés à partir des fichiers sur disque), non autoritaires : le DAO se contente de les
/// mapper, sans garantir leur recalcul. Ils sont donc nullables.
///
/// La session ne porte **plus** de marqueur d'archivage : la disponibilité de l'audio est un état
/// **observé** sur le disque ([DisponibiliteAudio], #1298), pas un fait déclaré (ADR 0048).
///
/// @param id clé technique, `null` avant insertion
/// @param cheminRacine chemin du sous-dossier workspace de la session (R22)
/// @param volumeOriginauxOctets volume total des originaux en octets (dérivé, optionnel)
/// @param volumeSequencesOctets volume total des séquences en octets (dérivé, optionnel)
/// @param idPassage identifiant du passage producteur (FK → `passage.id`, unique)
/// @param horodatagePurgeOriginaux moment où les **bruts** de la session ont été purgés
///     volontairement (#1303, en remplacement de l'heuristique `volume == 0`), `null` s'ils ne
///     l'ont jamais été
public record SessionDEnregistrement(
        Long id,
        String cheminRacine,
        Long volumeOriginauxOctets,
        Long volumeSequencesOctets,
        Long idPassage,
        LocalDateTime horodatagePurgeOriginaux) {

    /// Constructeur de **compatibilité** (sans horodatage de purge) : préserve les appels antérieurs
    /// à #1303 (le marqueur vaut `null`, posé uniquement par la purge). Voir
    /// [#horodatagePurgeOriginaux].
    public SessionDEnregistrement(
            Long id, String cheminRacine, Long volumeOriginauxOctets, Long volumeSequencesOctets, Long idPassage) {
        this(id, cheminRacine, volumeOriginauxOctets, volumeSequencesOctets, idPassage, null);
    }

    /// Les bruts de la session ont-ils été **purgés volontairement** (#1303) ? Fait déclaré : c'est
    /// lui qui fait taire le contrôle d'existence des originaux dans l'audit, plus l'heuristique de
    /// volume.
    public boolean originauxPurges() {
        return horodatagePurgeOriginaux != null;
    }
}
