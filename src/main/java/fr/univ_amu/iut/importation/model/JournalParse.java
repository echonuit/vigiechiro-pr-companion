package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.JsonSimple;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenu exploité du journal du capteur {@code LogPR<n>.txt} (C9, R19), tel que produit par {@link
 * AnalyseurLogPR}. C'est la <b>seule source d'identité de l'enregistreur</b> (n° de série) et des
 * paramètres d'acquisition d'une nuit.
 *
 * <p>Tous les champs hors {@code numeroSerie} et {@code sondePresente} sont nullables : un journal
 * circulaire peut avoir perdu des entrées (R19), l'inspection exploite ce qui est présent.
 *
 * @param numeroSerie n° de série de l'enregistreur (clé naturelle {@code recorder.serial_number})
 * @param versionModele modèle / firmware lus du journal (ex. {@code V1.01, T4.1}), ou {@code null}
 * @param dateDebut date de la nuit d'enregistrement (1re ligne du journal), ou {@code null}
 * @param heureDebut heure de début de la fenêtre d'acquisition (ISO {@code HH:MM:SS}), ou {@code
 *     null}
 * @param heureFin heure de fin de la fenêtre d'acquisition (ISO {@code HH:MM:SS}), ou {@code null}
 * @param frequenceEchantillonnageHz fréquence d'acquisition en Hz (ex. 384000), ou {@code null}
 * @param bandePassante bande passante du micro (ex. {@code 8-120kHz}), ou {@code null}
 * @param sensibilite réglage de sensibilité (ex. {@code 16dB 1dt. GN0}), ou {@code null}
 * @param sondePresente {@code true} si une sonde T°/hygrométrie est annoncée présente (R20)
 * @param parametresBruts ligne « Paramètres : … » brute, conservée telle quelle, ou {@code null}
 * @param evenements évènements remarquables relevés (changements de mode, réveils…)
 * @param anomalies anomalies détectées (réveils non programmés, batterie faible, erreurs SD, sonde
 *     absente…)
 */
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
    List<String> evenements,
    List<String> anomalies) {

  public JournalParse {
    evenements = List.copyOf(evenements);
    anomalies = List.copyOf(anomalies);
  }

  /** {@code true} si au moins une anomalie a été détectée dans le journal. */
  public boolean aDesAnomalies() {
    return !anomalies.isEmpty();
  }

  /** Paramètres d'acquisition sérialisés en JSON (colonne {@code passage.acquisition_params}). */
  public String parametresAcquisitionJson() {
    Map<String, String> champs = new LinkedHashMap<>();
    champs.put(
        "feHz", frequenceEchantillonnageHz == null ? null : frequenceEchantillonnageHz.toString());
    champs.put(
        "fenetre", heureDebut == null || heureFin == null ? null : heureDebut + "-" + heureFin);
    champs.put("bandePassante", bandePassante);
    champs.put("sensibilite", sensibilite);
    champs.put("brut", parametresBruts);
    return JsonSimple.objet(champs);
  }

  /** Évènements sérialisés en tableau JSON (colonne {@code sensor_log.parsed_events}). */
  public String evenementsJson() {
    return JsonSimple.tableau(evenements);
  }

  /** Anomalies sérialisées en tableau JSON (colonne {@code sensor_log.detected_anomalies}). */
  public String anomaliesJson() {
    return JsonSimple.tableau(anomalies);
  }
}
