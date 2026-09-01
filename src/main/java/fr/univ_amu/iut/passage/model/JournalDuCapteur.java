package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Completude;
/// Journal du capteur : journal technique du PR pour la nuit, parsé depuis le fichier
/// `LogPR<n>.txt` du firmware Teensy (C9, table `sensor_log`). Relation **1:1** avec la session
/// (`session_id` unique). C'est lui qui fournit l'identité de l'[Enregistreur].
///
/// Les champs [#evenementsParses] et [#anomaliesDetectees] sont sérialisés en `TEXT` JSON ;
/// `anomaliesDetectees` est **dérivé** du parsing des évènements (réveils non programmés, erreurs
/// SD…) et simplement mappé ici.
///
/// @param id clé technique, `null` avant insertion
/// @param cheminFichier chemin du fichier `LogPR<n>.txt` à la racine de la session (R22)
/// @param evenementsParses évènements parsés sérialisés en JSON (optionnel)
/// @param anomaliesDetectees anomalies détectées sérialisées en JSON (dérivé, optionnel)
/// @param completude ce que le journal dit de la fin de sa nuit ; `null` en base se lit
///     [Completude#INCONNUE], jamais complète (#5030)
/// @param idSession identifiant de la session référencée (FK → `recording_session.id`, unique)

public record JournalDuCapteur(
        Long id,
        String cheminFichier,
        String evenementsParses,
        String anomaliesDetectees,
        Completude completude,
        Long idSession) {

    /// Le journal, avec sa complétude ramenée à [Completude#INCONNUE] quand elle manque.
    ///
    /// **`null` n'est pas un quatrième état** : il porte ce qu'`INCONNUE` porte déjà, « le journal ne
    /// permet pas de conclure ». C'est ce que rend une base d'avant la migration V45, et le rabattre
    /// sur `COMPLETE` referait au report le défaut que #4990 a corrigé au calcul (#5030).
    public JournalDuCapteur {
        completude = completude == null ? Completude.INCONNUE : completude;
    }
}
