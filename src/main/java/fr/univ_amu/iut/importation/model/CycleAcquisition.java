package fr.univ_amu.iut.importation.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/// Un **cycle d'acquisition** d'une nuit, reconstitué depuis le journal `LogPR` : le firmware se
/// réveille chaque soir (`Wakeup by ALARM... Cpt N`) et se rendort le matin (`### Passage en mode
/// Veille` / `Mise en veille`). Un cycle qui ne se termine **pas** par une mise en veille normale
/// (carte SD pleine, mode erreur, ou journal interrompu) correspond à une **nuit tronquée**.
///
/// Sert à qualifier la **complétude** de chaque nuit détectée (cf. [PartitionNuits]) de façon fiable —
/// contrairement à une heuristique sur les seuls fichiers, qui confondrait une nuit calme (peu de
/// déclenchements avant l'aube) avec une nuit interrompue.
///
/// @param compteur numéro de réveil du firmware (`Cpt N`), croissant sur la carte
/// @param reveil horodatage du réveil (début de nuit, ~21:00)
/// @param fin horodatage de la fin observée (mise en veille normale, ou évènement anormal), ou `null`
///     si le journal s'interrompt sans conclure le cycle
/// @param complet `true` si la nuit s'est terminée par une **mise en veille normale**
/// @param raison libellé de l'anomalie de fin quand `!complet` (ex. « Carte SD pleine ! »), sinon `null`
public record CycleAcquisition(int compteur, LocalDateTime reveil, LocalDateTime fin, boolean complet, String raison) {

    /// Date **du soir** de la nuit (date du réveil ~21:00) : clé de corrélation avec une nuit détectée.
    public LocalDate dateNuit() {
        return reveil.toLocalDate();
    }
}
