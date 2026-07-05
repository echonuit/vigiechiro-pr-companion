package fr.univ_amu.iut.passage.model;

import java.time.LocalDateTime;

/// Séquence d'écoute : fichier dérivé d'un enregistrement original par expansion de temps ×10 et
/// découpage en tranches de 5 s (C8, table `listening_sequence`). **Audible** : c'est ce qui est
/// déposé sur Vigie-Chiro et analysé par Tadarida.
///
/// Doublement rattachée : à sa session (`session_id`) et à son original source
/// (`original_recording_id`), les deux en `ON DELETE CASCADE`. Le [#offsetSourceSecondes] est un
/// champ **dérivé** (position avant le ×10), simplement mappé ici. Le drapeau [#dansSelection]
/// dénormalise l'appartenance à une sélection d'écoute.
///
/// @param id clé technique, `null` avant insertion
/// @param nomFichier nom de fichier (nom de l'original + suffixe `_000`, `_001`…, R8)
/// @param idEnregistrementOriginal identifiant de l'original source (FK → `original_recording.id`)
/// @param indexSource index (≥ 0) de la séquence dans l'original (optionnel)
/// @param offsetSourceSecondes offset temporel dans le source en secondes (dérivé, optionnel)
/// @param dureeSecondes durée en secondes (optionnel, typiquement 5 s)
/// @param cheminFichier chemin sur disque, sous-dossier `transformes/` (R22)
/// @param dansSelection `true` si la séquence fait partie de la sélection d'écoute
/// @param idSession identifiant de la session contenante (FK → `recording_session.id`)
/// @param horodatageCapture heure réelle de début de la tranche (extraite du nom de fichier à l'import,
///     `_AAAAMMJJ_HHMMSS`), ou `null` si le nom n'est pas horodaté (jeux de test, fichiers non standard)
public record SequenceDEcoute(
        Long id,
        String nomFichier,
        Long idEnregistrementOriginal,
        Integer indexSource,
        Double offsetSourceSecondes,
        Double dureeSecondes,
        String cheminFichier,
        boolean dansSelection,
        Long idSession,
        LocalDateTime horodatageCapture) {

    /// Constructeur de **compatibilité** (sans horodatage de capture) : préserve les appels antérieurs à
    /// #530 (l'horodatage est `null`, rempli à l'import ou par backfill). Voir [#horodatageCapture].
    public SequenceDEcoute(
            Long id,
            String nomFichier,
            Long idEnregistrementOriginal,
            Integer indexSource,
            Double offsetSourceSecondes,
            Double dureeSecondes,
            String cheminFichier,
            boolean dansSelection,
            Long idSession) {
        this(
                id,
                nomFichier,
                idEnregistrementOriginal,
                indexSource,
                offsetSourceSecondes,
                dureeSecondes,
                cheminFichier,
                dansSelection,
                idSession,
                null);
    }
}
