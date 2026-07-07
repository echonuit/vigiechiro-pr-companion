package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Moteur de transformation audio (R10/R11) : le **point dur** de la feature import. Reproduit
/// **fidèlement** la chaîne Vigie-Chiro/Tadarida, condition pour que l'`observations.csv` (produit par
/// Tadarida sur les mêmes tranches) se raccroche à l'audio produit par l'application.
///
/// ## Vue d'ensemble : découper à 5 s réelles, PUIS expanser ×10
///
/// L'ordre est essentiel. Un enregistrement brut est un ultrason mono 16 bits échantillonné très vite
/// (ex. 384 000 Hz), donc inaudible. La chaîne :
///
/// 1. **Découpe** le brut en **tranches de 5 s au rythme SOURCE** (5 s *réelles*) : chaque tranche porte
///    `5 × frequenceSource` trames (la dernière peut être plus courte).
/// 2. **Expanse ×10** chaque tranche en **réinterprétant** son rythme d'échantillonnage
///    (`frequenceSortie = frequenceSource / 10`, ex. 38 400 Hz) : **aucun échantillon n'est recalculé**,
///    les mêmes octets PCM sont conservés. Une tranche de 5 s réelles devient donc **50 s à l'écoute**,
///    et audible (c'est l'« expansion de temps » du protocole).
///
/// ⚠️ **Piège corrigé (#…)** : découper *après* expansion et au rythme de **sortie** donnerait des
/// tranches de **0,5 s réelles** (10× trop courtes), désalignées des temps de l'`observations.csv` (qui
/// sont en secondes réelles dans une tranche de 5 s). On découpe donc bien au rythme **source**.
///
/// Pour une durée source `D` (secondes) : `nbSequences = ceil(D / 5)`.
///
/// ## Nommage HORODATÉ des tranches (R8, convention Tadarida)
///
/// Chaque tranche est nommée avec l'**heure réelle de son début**, pas un index : l'horodatage de
/// l'original (`_AAAAMMJJ_HHMMSS`) est **décalé** de `index × 5 s`, et le suffixe est **toujours `_000`**.
/// Exemple : `..._20260422_225849.wav` → tranches `..._225849_000`, `..._225854_000`, `..._225859_000`…
/// C'est le nommage que porte l'`observations.csv` : c'est la **clé de jointure** observation ↔ tranche
/// (cf. `ServiceValidation`). Détail dans [Prefixe#nommerSequence(String, int, int)].
///
/// ## Déterminisme (R11)
///
/// Mêmes octets en entrée ⇒ mêmes octets en sortie : le découpage est purement positionnel, les
/// octets PCM sont copiés sans altération (aucun rééchantillonnage, donc aucun clipping
/// introduit), et [FichierWav#ecrire] produit un en-tête canonique fixe. Relancer la
/// transformation réécrit des fichiers identiques au bit près.
public class TransformationAudio {

    /// Durée d'une séquence d'écoute, en secondes **réelles** (au rythme source) : une tranche = 5 s de
    /// l'enregistrement d'origine, soit 50 s à l'écoute une fois expansée ×10 (R10).
    public static final int DUREE_SEQUENCE_SECONDES = 5;

    /// Facteur d'expansion temporelle du protocole Vigie-Chiro (R10).
    public static final int FACTEUR_EXPANSION = 10;

    /// Transforme un original en séquences d'écoute, en **nommant** les tranches d'après le nom du
    /// fichier lu. Surcharge de commodité pour le mode **conservation** (le fichier de `bruts/` porte
    /// déjà son nom R6) et pour les tests : équivaut à [#transformer(Path, String, Path, Prefixe, Integer)]
    /// avec `nomR6 = originalWav.getFileName()`.
    public TransformationOriginal transformer(
            Path originalWav, Path dossierSortie, Prefixe prefixe, Integer frequenceAcquisitionLogHz) {
        Objects.requireNonNull(originalWav, "originalWav");
        return transformer(
                originalWav, originalWav.getFileName().toString(), dossierSortie, prefixe, frequenceAcquisitionLogHz);
    }

    /// Transforme un enregistrement original en séquences d'écoute écrites dans `dossierSortie`.
    ///
    /// Le **nom logique R6** (`nomR6`) est découplé du chemin **physiquement lu** (`originalWav`) : en
    /// mode « sans copie », on lit directement le WAV de la carte SD tout en nommant les séquences
    /// d'après le nom R6 **calculé**, de sorte que la sortie soit identique au mode conservation (même
    /// clé de jointure Tadarida). Cf. [SourceOriginal].
    ///
    /// @param originalWav chemin du WAV **lu** (mono 16 bits, ex. 384 kHz) — `bruts/` ou carte SD
    /// @param nomR6 nom logique R6 servant au nommage R8 des séquences et au `nomOriginal` du résultat
    /// @param dossierSortie dossier `transformes/` où écrire les séquences (créé si absent)
    /// @param prefixe préfixe de la session (sert au nommage R8 des séquences)
    /// @param frequenceAcquisitionLogHz fréquence d'acquisition déclarée par le log de l'enregistreur
    ///     (`Fe…kHz`), ou `null` si aucun journal (mode dégradé). Sert à **rejeter** une source déjà
    ///     ralentie (cf. [DetectionRalenti]) au lieu de la ré-expanser (double expansion).
    /// @return le détail de la transformation (métadonnées de l'original + séquences produites)
    /// @throws OriginalDejaRalentiException si la source est déjà ralentie (rejet récupérable #155)
    /// @throws IllegalArgumentException si la fréquence source n'est pas un multiple de 10
    public TransformationOriginal transformer(
            Path originalWav, String nomR6, Path dossierSortie, Prefixe prefixe, Integer frequenceAcquisitionLogHz) {
        Objects.requireNonNull(originalWav, "originalWav");
        Objects.requireNonNull(nomR6, "nomR6");
        Objects.requireNonNull(dossierSortie, "dossierSortie");
        Objects.requireNonNull(prefixe, "prefixe");

        // Lecture + format de la SOURCE : un échec ici est **récupérable** (#155) → l'original est rejeté.
        FichierWav source = lireSource(originalWav);
        int frequenceSource = source.frequenceEchantillonnageHz();
        // Garde-fou double expansion : une source déjà ralentie (en-tête trop bas au regard du log, ou
        // sous le seuil d'un ultrason brut) est REJETÉE — la ré-expanser donnerait des fréquences 10×
        // trop basses. Rejet récupérable (#155) : le fichier est consigné et l'import continue.
        if (DetectionRalenti.estDejaRalenti(frequenceSource, frequenceAcquisitionLogHz)) {
            throw new OriginalDejaRalentiException("Enregistrement déjà ralenti (en-tête "
                    + frequenceSource
                    + " Hz"
                    + (frequenceAcquisitionLogHz != null
                            ? " vs acquisition " + frequenceAcquisitionLogHz + " Hz du log"
                            : ", sous le seuil d'un ultrason brut")
                    + ") : ce n'est pas un enregistrement brut, il ne peut pas être importé tel quel : "
                    + originalWav.getFileName());
        }
        if (frequenceSource % FACTEUR_EXPANSION != 0) {
            throw new OriginalIllisibleException("Fréquence source "
                    + frequenceSource
                    + " Hz non divisible par "
                    + FACTEUR_EXPANSION
                    + " : "
                    + originalWav.getFileName());
        }
        int frequenceSortie = frequenceSource / FACTEUR_EXPANSION;
        int octetsParTrame = source.octetsParTrame();
        // Découpage à 5 s **réelles** = au rythme SOURCE (et non de sortie). Chaque séquence porte donc les
        // octets de 5 s de l'enregistrement d'origine, qui, rejoués à `frequenceSortie` (÷10), durent 50 s à
        // l'écoute. C'est le découpage du pipeline Vigie-Chiro/Tadarida (segment _000 = 5 s réelles) : les
        // temps du CSV Tadarida sont en secondes réelles DANS cette séquence de 5 s. (L'ancien calcul avec
        // `frequenceSortie` donnait des séquences de 0,5 s réelles, désalignées des temps Tadarida.)
        int octetsParSequence = DUREE_SEQUENCE_SECONDES * frequenceSource * octetsParTrame;
        byte[] pcm = source.donneesPcm();
        String nomOriginal = nomR6;
        String sha256 = empreinteSource(originalWav);

        // Écriture des séquences dans le WORKSPACE : un échec ici (disque plein, permission…) est
        // **fatal** — il ne doit pas être masqué en « fichier rejeté » ; il remonte et la session est
        // nettoyée par l'appelant.
        try {
            Files.createDirectories(dossierSortie);
            List<SequenceProduite> sequences = new ArrayList<>();
            int index = 0;
            for (int offset = 0; offset < pcm.length; offset += octetsParSequence) {
                int longueur = Math.min(octetsParSequence, pcm.length - offset);
                // Nommage horodaté (convention Vigie-Chiro/Tadarida) : la tranche d'index k porte l'heure
                // réelle de son début = horodatage de l'original + k × 5 s, avec un `_000` systématique. Ce
                // nom est la clé de jointure avec les lignes de l'observations.csv (cf. ServiceValidation).
                String nomSequence = prefixe.nommerSequence(nomOriginal, index, index * DUREE_SEQUENCE_SECONDES);
                Path cheminSequence = dossierSortie.resolve(nomSequence);
                // Reprise (#231) : on réécrit **toujours** la séquence (R11 : bytes déterministes), pour ne
                // jamais persister une séquence périmée/corrompue par un crash, même de taille identique.
                FichierWav.ecrire(
                        cheminSequence,
                        source.nombreCanaux(),
                        frequenceSortie,
                        source.bitsParEchantillon(),
                        pcm,
                        offset,
                        longueur);

                long tramesSequence = (long) longueur / octetsParTrame;
                double dureeSortie = tramesSequence / (double) frequenceSortie;
                double offsetSource = ((long) offset / octetsParTrame) / (double) frequenceSource;
                sequences.add(new SequenceProduite(
                        index,
                        nomSequence,
                        cheminSequence,
                        frequenceSortie,
                        dureeSortie,
                        offsetSource,
                        Files.size(cheminSequence)));
                index++;
            }
            return new TransformationOriginal(
                    nomOriginal,
                    originalWav,
                    frequenceSource,
                    frequenceSortie,
                    source.dureeSecondes(),
                    sha256,
                    sequences);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Échec d'écriture des séquences de " + originalWav.getFileName() + " dans le workspace", e);
        }
    }

    /// Lit le WAV source ; une erreur de lecture/format est **récupérable** ([OriginalIllisibleException]).
    private static FichierWav lireSource(Path originalWav) {
        try {
            return FichierWav.lire(originalWav);
        } catch (IOException e) {
            throw new OriginalIllisibleException(
                    "Original illisible (" + e.getMessage() + ") : " + originalWav.getFileName(), e);
        }
    }

    /// Empreinte SHA-256 de la **source** ; une lecture impossible est récupérable (rejet du fichier).
    private static String empreinteSource(Path originalWav) {
        try {
            return Empreintes.sha256Hex(originalWav);
        } catch (IllegalStateException e) {
            throw new OriginalIllisibleException("Empreinte de l'original illisible : " + originalWav.getFileName(), e);
        }
    }

    /// Nombre de séquences attendues pour une durée source donnée (R10) : `ceil(D / 5)` — une séquence par
    /// tranche de 5 s **réelles** de l'enregistrement. Exposé pour la lisibilité des tests et de l'IHM
    /// (prévisualisation du volume à produire).
    public static long nombreSequencesAttendu(double dureeSourceSecondes) {
        return (long) Math.ceil(dureeSourceSecondes / DUREE_SEQUENCE_SECONDES);
    }
}
