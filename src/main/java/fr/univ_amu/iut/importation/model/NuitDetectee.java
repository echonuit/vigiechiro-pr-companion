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
/// @param completude ce que le journal permet d'établir : complète, tronquée, ou **inconnue** (#4990)
/// @param motifIncompletude libellé court de la troncature quand la nuit est tronquée (ex. « carte SD
///     pleine »), sinon `null`
/// @param indicesDuJournal ce que le journal montrait juste avant l'arrêt d'une nuit tronquée ; vide
///     sinon, y compris sur une nuit inconnue - il n'y a alors rien à montrer, c'est le problème même.
///     Des **faits relevés**, jamais une cause (#4990)
public record NuitDetectee(
        LocalDate dateNuit,
        LocalDateTime debut,
        LocalDateTime fin,
        List<Path> originaux,
        Completude completude,
        String motifIncompletude,
        List<String> indicesDuJournal) {

    /// Les listes sont figées à la construction : une nuit détectée est un constat, et un constat qui
    /// changerait sous son lecteur ne serait plus un constat.
    public NuitDetectee {
        originaux = List.copyOf(originaux);
        indicesDuJournal = List.copyOf(indicesDuJournal);
    }

    /// Nombre d'enregistrements originaux de la nuit.
    public int nombreFichiers() {
        return originaux.size();
    }
}
