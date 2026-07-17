package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.JsonSimple;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Contenu exploité du journal du capteur `LogPR<n>.txt` (C9, R19), tel que produit par
/// [AnalyseurLogPR]. C'est la **seule source d'identité de l'enregistreur** (n° de série) et des
/// paramètres d'acquisition d'une nuit.
///
/// Tous les champs hors `numeroSerie` et `sondePresente` sont nullables : un journal circulaire
/// peut avoir perdu des entrées (R19), l'inspection exploite ce qui est présent.
///
/// Les évènements et anomalies sont des [LigneJournal] : ils portent l'**horodatage** de leur ligne
/// de log, ce qui permet de les ranger dans la bonne nuit sur une carte multi-nuits à log unique
/// (#1696). Les colonnes stockées (`sensor_log.parsed_events` / `detected_anomalies`) restent des
/// tableaux de messages : l'horodatage ne sert qu'au filtrage, à l'import.
///
/// @param numeroSerie n° de série de l'enregistreur (clé naturelle `recorder.serial_number`)
/// @param versionModele modèle / firmware lus du journal (ex. `V1.01, T4.1`), ou `null`
/// @param dateDebut date de la nuit d'enregistrement (1re ligne du journal), ou `null`
/// @param heureDebut heure de début de la fenêtre d'acquisition (ISO `HH:MM:SS`), ou `null`
/// @param heureFin heure de fin de la fenêtre d'acquisition (ISO `HH:MM:SS`), ou `null`
/// @param frequenceEchantillonnageHz fréquence d'acquisition en Hz (ex. 384000), ou `null`
/// @param bandePassante bande passante du micro (ex. `8-120kHz`), ou `null`
/// @param sensibilite réglage de sensibilité (ex. `16dB 1dt. GN0`), ou `null`
/// @param sondePresente `true` si une sonde T°/hygrométrie est annoncée présente (R20)
/// @param parametresBruts ligne « Paramètres : … » brute, conservée telle quelle, ou `null`
/// @param evenements évènements remarquables relevés (changements de mode, réveils…), horodatés
/// @param anomalies anomalies détectées (réveils non programmés, batterie faible, erreurs SD,
/// sonde absente…), horodatées
public record JournalParse(
        String numeroSerie,
        String versionModele,
        LocalDate dateDebut,
        String heureDebut,
        String heureFin,
        Integer frequenceEchantillonnageHz,
        String bandePassante,
        String sensibilite,
        boolean sondePresente,
        String parametresBruts,
        List<LigneJournal> evenements,
        List<LigneJournal> anomalies) {

    public JournalParse {
        evenements = List.copyOf(evenements);
        anomalies = List.copyOf(anomalies);
    }

    /// `true` si au moins une anomalie a été détectée dans le journal.
    public boolean aDesAnomalies() {
        return !anomalies.isEmpty();
    }

    /// Messages des évènements, sans horodatage (affichage, résultat d'import).
    public List<String> messagesEvenements() {
        return messages(evenements);
    }

    /// Messages des anomalies, sans horodatage.
    public List<String> messagesAnomalies() {
        return messages(anomalies);
    }

    /// Paramètres d'acquisition sérialisés en JSON (colonne `passage.acquisition_params`).
    public String parametresAcquisitionJson() {
        Map<String, String> champs = new LinkedHashMap<>();
        champs.put("feHz", frequenceEchantillonnageHz == null ? null : frequenceEchantillonnageHz.toString());
        champs.put("fenetre", heureDebut == null || heureFin == null ? null : heureDebut + "-" + heureFin);
        champs.put("bandePassante", bandePassante);
        champs.put("sensibilite", sensibilite);
        champs.put("brut", parametresBruts);
        return JsonSimple.objet(champs);
    }

    /// Évènements sérialisés en tableau JSON (colonne `sensor_log.parsed_events`), toutes nuits.
    public String evenementsJson() {
        return JsonSimple.tableau(messagesEvenements());
    }

    /// Anomalies sérialisées en tableau JSON (colonne `sensor_log.detected_anomalies`), toutes nuits.
    public String anomaliesJson() {
        return JsonSimple.tableau(messagesAnomalies());
    }

    /// Évènements de la **seule nuit** `nuit` (#1696), en tableau JSON : une entrée horodatée est rangée
    /// par bascule midi ([PartitionNuits#nuitDe]) ; une entrée non datée (déploiement) est conservée.
    public String evenementsJsonPourNuit(LocalDate nuit) {
        return JsonSimple.tableau(messages(deLaNuit(evenements, nuit)));
    }

    /// Anomalies de la **seule nuit** `nuit` (#1696), en tableau JSON (même règle que les évènements).
    public String anomaliesJsonPourNuit(LocalDate nuit) {
        return JsonSimple.tableau(messages(deLaNuit(anomalies, nuit)));
    }

    private static List<String> messages(List<LigneJournal> lignes) {
        return lignes.stream().map(LigneJournal::texte).toList();
    }

    private static List<LigneJournal> deLaNuit(List<LigneJournal> lignes, LocalDate nuit) {
        return lignes.stream()
                .filter(l -> l.horodatage() == null
                        || PartitionNuits.nuitDe(l.horodatage()).equals(nuit))
                .toList();
    }
}
