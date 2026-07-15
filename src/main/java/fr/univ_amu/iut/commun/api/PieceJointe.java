package fr.univ_amu.iut.commun.api;

/// Un **fichier rattaché** à une participation, tel que listé par
/// `GET /participations/{id}/pieces_jointes?<filtre>` (backend `Scille/vigiechiro-api`,
/// `participations.py:342-350`). C'est **la** voie pour obtenir le `_id` d'un fichier : la collection
/// `/fichiers` n'est pas listable pour un observateur (`403`), seul le document participation qui les
/// référence les expose.
///
/// [#disponible] dit si le fichier est **monté sur S3** et donc téléchargeable via
/// [ClientVigieChiro#accesFichier(String)] : les fichiers générés avec `force_upload=True` (le CSV
/// d'observations, les logs) le sont toujours ; les WAV extraits d'un dépôt ZIP, non (#1244).
///
/// @param id `_id` Eve du document `fichiers` (jamais `null` : un fichier sans `_id` est ignoré au parsing)
/// @param titre nom du fichier (ex. `participation-<id>-observations.csv`, `Car…_000.wav`), ou `null`
/// @param disponible `true` si le fichier est sur S3 (téléchargeable), `false` sinon
public record PieceJointe(String id, String titre, boolean disponible) {}
