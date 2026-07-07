package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/// Une **nuit** détectée dans un dossier d'import : le groupe de WAV appartenant à une même session
/// d'acquisition (soir `J` ~21:00 → matin `J+1` ~06:30). Produite par [PartitionNuits].
///
/// C'est l'unité que l'import transformera en **un passage** : chaque nuit incluse donnera un passage
/// distinct (même point, n° de passage consécutif, date propre = [#dateNuit()]).
///
/// @param dateNuit date **du soir** de la nuit (clé de tri et date du passage)
/// @param debut horodatage du premier enregistrement de la nuit
/// @param fin horodatage du dernier enregistrement de la nuit
/// @param originaux les WAV de cette nuit, triés chronologiquement
/// @param complete `true` si la nuit s'est terminée normalement ; `false` si **tronquée** (carte pleine,
///     interruption)
/// @param motifIncompletude libellé court de la troncature quand `!complete` (ex. « carte SD pleine »),
///     sinon `null`
public record NuitDetectee(
        LocalDate dateNuit,
        LocalDateTime debut,
        LocalDateTime fin,
        List<Path> originaux,
        boolean complete,
        String motifIncompletude) {

    /// Nombre d'enregistrements originaux de la nuit.
    public int nombreFichiers() {
        return originaux.size();
    }
}
