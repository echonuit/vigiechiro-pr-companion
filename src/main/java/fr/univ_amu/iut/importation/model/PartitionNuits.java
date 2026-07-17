package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/// Partitionne les enregistrements d'un dossier d'import en **nuits** (soir `J` → matin `J+1`) et
/// qualifie la **complétude** de chacune. C'est l'étape amont du découpage de l'import : chaque nuit
/// incluse donnera **un passage** distinct.
///
/// **Groupage** purement à partir des noms de fichiers horodatés (`PaRecPR..._AAAAMMJJ_HHMMSS.wav`,
/// via [Prefixe#horodatageDe(String)]), avec un **seuil de bascule à midi** : un enregistrement du petit
/// matin (avant midi) appartient à la nuit de la **veille**. Robuste même sans journal.
///
/// **Complétude** déduite des [cycles du journal][CyclesJournal] (fiable) : une nuit dont le cycle s'est
/// terminé anormalement (carte SD pleine, erreur, interruption) est **tronquée**. Une nuit sans cycle
/// correspondant (journal absent) est supposée **complète** — on ne devine pas, pour éviter les fausses
/// alertes (une nuit calme au petit matin a peu de fichiers sans être tronquée).
public final class PartitionNuits {

    /// Heure de bascule jour/nuit : les protocoles nocturnes (acquisition ~21:00→06:30) ne produisent
    /// aucun fichier autour de midi, qui sépare donc proprement deux nuits consécutives.
    private static final LocalTime BASCULE = LocalTime.NOON;

    private PartitionNuits() {}

    /// Les nuits détectées, **triées par date croissante**. Les fichiers d'une nuit sont triés
    /// chronologiquement. Les noms non horodatés sont ignorés.
    public static List<NuitDetectee> partitionner(List<Path> originaux, List<CycleAcquisition> cycles) {
        Map<LocalDate, List<Path>> parNuit = new TreeMap<>();
        for (Path original : originaux) {
            horodatage(original)
                    .ifPresent(ts -> parNuit.computeIfAbsent(nuitDe(ts), n -> new ArrayList<>())
                            .add(original));
        }
        Map<LocalDate, CycleAcquisition> cycleParNuit = new TreeMap<>();
        for (CycleAcquisition cycle : cycles) {
            cycleParNuit.putIfAbsent(cycle.dateNuit(), cycle);
        }

        List<NuitDetectee> nuits = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Path>> entree : parNuit.entrySet()) {
            List<Path> fichiers = entree.getValue().stream()
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            LocalDateTime debut = horodatage(fichiers.get(0)).orElse(null);
            LocalDateTime fin = horodatage(fichiers.get(fichiers.size() - 1)).orElse(null);
            CycleAcquisition cycle = cycleParNuit.get(entree.getKey());
            boolean complete = cycle == null || cycle.complet();
            String motif = cycle != null && !cycle.complet() ? cycle.raison() : null;
            nuits.add(new NuitDetectee(entree.getKey(), debut, fin, fichiers, complete, motif));
        }
        return nuits;
    }

    /// La nuit d'un horodatage : sa **date du soir** (un enregistrement d'avant midi appartient à la nuit
    /// de la veille). Réutilisé par [FiltreThLogNuit] pour ranger une mesure climatique dans sa nuit
    /// (#1696), avec la même bascule que les WAV.
    static LocalDate nuitDe(LocalDateTime ts) {
        return ts.toLocalTime().isBefore(BASCULE) ? ts.toLocalDate().minusDays(1) : ts.toLocalDate();
    }

    private static Optional<LocalDateTime> horodatage(Path original) {
        return Prefixe.horodatageDe(original.getFileName().toString());
    }
}
