package fr.univ_amu.iut.commun.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;

/// Lecture des réponses JSON menant au **journal de traitement** d’une participation (#1132) :
/// `participation.logs` → id du document `fichiers`, puis `GET /fichiers/{id}/acces` → URL S3
/// signée. Extrait de [ParticipationsVigieChiro] (plafond God Class) : même style **best-effort**
/// (jamais d’exception, vide si absent/illisible), aucune E/S ici.
final class JournalVigieChiro {

    private static final String CLE_LOGS = "logs";

    private JournalVigieChiro() {}

    /// Identifiant du document `fichiers` du journal (`participation.logs._id`), vide si absent ou
    /// marqué indisponible (le traitement n’a pas encore tourné).
    static Optional<String> idJournal(String corps) {
        try {
            JsonObject objet = JsonParser.parseString(corps).getAsJsonObject();
            if (!objet.has(CLE_LOGS) || !objet.get(CLE_LOGS).isJsonObject()) {
                return Optional.empty();
            }
            JsonObject logs = objet.getAsJsonObject(CLE_LOGS);
            if (logs.has("disponible") && !logs.get("disponible").getAsBoolean()) {
                return Optional.empty();
            }
            return Optional.ofNullable(ReponsesVigieChiro.texte(logs, "_id"));
        } catch (RuntimeException illisible) {
            return Optional.empty();
        }
    }
}
