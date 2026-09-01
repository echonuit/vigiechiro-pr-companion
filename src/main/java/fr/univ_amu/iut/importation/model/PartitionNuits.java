package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.model.Nuit;
import fr.univ_amu.iut.commun.model.Prefixe;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
/// **Complétude** déduite des [cycles du journal][CyclesJournal], en [trois états][Completude]. Une
/// nuit dont le cycle s'est terminé anormalement est **tronquée** ; une nuit sans cycle correspondant
/// est **inconnue**, et non complète.
///
/// La supposition de complétude tenait à une crainte juste - ne pas deviner, une nuit calme au petit
/// matin ayant peu de fichiers sans être tronquée. Le troisième état la préserve tout en cessant de
/// rassurer : on ne devine toujours pas, et on ne conclut plus non plus (#4990).
public final class PartitionNuits {

    private PartitionNuits() {}

    /// Les nuits détectées, **triées par date croissante**. Les fichiers d'une nuit sont triés
    /// chronologiquement. Les noms non horodatés sont ignorés.
    public static List<NuitDetectee> partitionner(List<Path> originaux, List<CycleAcquisition> cycles) {
        return partitionner(originaux, cycles, null);
    }

    /// Les nuits détectées, avec ce que le journal montrait avant l'arrêt de celles qui n'ont pas été
    /// refermées (#4990). `journal` peut être `null` : sans lui, les indices sont vides et le reste
    /// est inchangé.
    public static List<NuitDetectee> partitionner(
            List<Path> originaux, List<CycleAcquisition> cycles, JournalParse journal) {
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
            // Aucun cycle ne dit rien de cette nuit, ni en bien ni en mal. Le premier jet concluait
            // `cycle == null || cycle.complet()`, donc « complète » : l'absence de preuve lue comme
            // une preuve, et le badge le plus rassurant sur la nuit dont on sait le moins (#4990).
            Completude completude =
                    cycle == null ? Completude.INCONNUE : cycle.complet() ? Completude.COMPLETE : Completude.TRONQUEE;
            String motif = completude == Completude.TRONQUEE ? cycle.raison() : null;
            // Les indices n'accompagnent QUE la nuit tronquée. Sur une nuit inconnue il n'y a rien à
            // montrer - c'est précisément le problème - et en montrer sur une nuit complète ferait
            // passer des faits ordinaires pour un signal.
            List<String> indices = completude == Completude.TRONQUEE && journal != null
                    ? journal.anomaliesDeLaNuit(entree.getKey())
                    : List.of();
            nuits.add(new NuitDetectee(entree.getKey(), debut, fin, fichiers, completude, motif, indices));
        }
        return nuits;
    }

    /// La nuit d'un horodatage : sa **date du soir** (un enregistrement d'avant midi appartient à la nuit
    /// de la veille). Réutilisé par [FiltreThLogNuit] pour ranger une mesure climatique dans sa nuit
    /// (#1696), avec la même bascule que les WAV. Délègue à [Nuit] (concept partagé, #1724).
    static LocalDate nuitDe(LocalDateTime ts) {
        return Nuit.de(ts);
    }

    private static Optional<LocalDateTime> horodatage(Path original) {
        return Prefixe.horodatageDe(original.getFileName().toString());
    }
}
