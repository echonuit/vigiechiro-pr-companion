package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/// Lecture de la réponse JSON de `GET /participations/{id}/pieces_jointes?<filtre>` vers des
/// [PieceJointe]. Best-effort comme les autres lecteurs du paquet (jamais d'exception ; un élément sans
/// `_id` est ignoré, un corps illisible donne une liste vide), sans aucune E/S — testable sur des
/// réponses figées.
final class PiecesJointesVigieChiro {

    private PiecesJointesVigieChiro() {}

    /// Fichiers rattachés d'une participation : le tableau `_items`, chaque objet portant `_id`, `titre`
    /// et `disponible`. Un élément sans `_id` est ignoré ; `disponible` absent ou illisible vaut `false`
    /// (prudence : un fichier dont on ne sait pas s'il est monté sur S3 n'est pas présumé téléchargeable).
    static List<PieceJointe> pieces(String corps) {
        List<PieceJointe> pieces = new ArrayList<>();
        for (JsonElement element : ReponsesVigieChiro.items(corps)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject objet = element.getAsJsonObject();
            String id = ReponsesVigieChiro.texte(objet, "_id");
            if (id != null) {
                pieces.add(new PieceJointe(id, ReponsesVigieChiro.texte(objet, "titre"), estDisponible(objet)));
            }
        }
        return pieces;
    }

    private static boolean estDisponible(JsonObject objet) {
        try {
            JsonElement disponible = objet.get("disponible");
            return disponible != null && disponible.isJsonPrimitive() && disponible.getAsBoolean();
        } catch (RuntimeException illisible) {
            return false;
        }
    }
}
