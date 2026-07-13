package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Empreintes;
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
/// ## Vue d'ensemble : découper à 5 s réelles, à la cible d'expansion ×10
///
/// Un enregistrement brut est un ultrason mono 16 bits acquis très vite (`Fe`, ex. 384 000 Hz), donc
/// inaudible ; l'écoute Vigie-Chiro se fait **expansé ×10** (`Fe/10`, ex. 38 400 Hz). Point clé :
/// **c'est la vraie fréquence d'acquisition `Fe` (issue du log) qui pilote l'arithmétique, pas
/// l'en-tête WAV** — l'enregistreur PR écrit ses bruts **déjà expansés** (en-tête = `Fe/10`), tandis
/// qu'une source « directe » les porte à `Fe` (cf. [FrequenceAcquisition]). La chaîne :
///
/// 1. **Découpe** le brut en **tranches de 5 s réelles** : chaque tranche porte `5 × Fe` trames (la
///    dernière peut être plus courte), quel que soit ce que dit l'en-tête.
/// 2. **Écrit** chaque tranche à `frequenceSortie = Fe / 10` (ex. 38 400 Hz), **sans recalculer aucun
///    échantillon** : les mêmes octets PCM sont conservés. Une source directe est ainsi réétiquetée
///    ×10 ; un brut PR déjà expansé est simplement réécrit à sa fréquence (aucune ré-expansion). Une
///    tranche de 5 s réelles s'écoute donc en **50 s**.
///
/// ⚠️ **Piège évité** : découper au rythme de l'**en-tête** d'un brut PR déjà expansé donnerait des
/// tranches **10× trop courtes** (0,5 s réelle), désalignées des temps de l'`observations.csv` (en
/// secondes réelles dans une tranche de 5 s). On découpe au rythme d'**acquisition** `Fe`.
///
/// Pour une durée d'acquisition `D` (secondes) : `nbSequences = ceil(D / 5)`.
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

    /// Durée d'une séquence d'écoute, en secondes **réelles** (au rythme d'acquisition) : une tranche = 5 s
    /// de l'enregistrement d'origine, soit 50 s à l'écoute une fois expansée ×10 (R10).
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
    /// @param originalWav chemin du WAV **lu** (mono 16 bits ; en-tête `Fe/10` pour un brut PR, `Fe` pour
    ///     une source directe) — `bruts/` ou carte SD
    /// @param nomR6 nom logique R6 servant au nommage R8 des séquences et au `nomOriginal` du résultat
    /// @param dossierSortie dossier `transformes/` où écrire les séquences (créé si absent)
    /// @param prefixe préfixe de la session (sert au nommage R8 des séquences)
    /// @param frequenceAcquisitionLogHz fréquence d'acquisition déclarée par le log de l'enregistreur
    ///     (`Fe…kHz`), ou `null` si aucun journal (mode dégradé). **Pilote** la fréquence de sortie et le
    ///     découpage à 5 s réelles ; à défaut, elle est déduite de l'en-tête (cf. [FrequenceAcquisition]).
    /// @return le détail de la transformation (métadonnées de l'original + séquences produites)
    /// @throws OriginalIllisibleException si la source est illisible/de format invalide, ou si la fréquence
    ///     d'acquisition n'est pas un multiple de 10 (rejet récupérable #155)
    public TransformationOriginal transformer(
            Path originalWav, String nomR6, Path dossierSortie, Prefixe prefixe, Integer frequenceAcquisitionLogHz) {
        Objects.requireNonNull(originalWav, "originalWav");
        Objects.requireNonNull(nomR6, "nomR6");
        Objects.requireNonNull(dossierSortie, "dossierSortie");
        Objects.requireNonNull(prefixe, "prefixe");

        // Lecture + format de la SOURCE : un échec ici est **récupérable** (#155) → l'original est rejeté.
        FichierWav source = lireSource(originalWav);
        // C'est la **vraie fréquence d'acquisition** qui pilote toute l'arithmétique, PAS l'en-tête WAV :
        // l'enregistreur PR écrit ses bruts déjà expansés ×10 (en-tête = Fe/10). Le log fait foi ; à défaut,
        // on la déduit de l'en-tête (cf. FrequenceAcquisition). L'en-tête ne sert plus qu'au format brut
        // (canaux, bits, octets par trame) et à la copie verbatim du PCM.
        int frequenceAcquisition =
                FrequenceAcquisition.reelle(source.frequenceEchantillonnageHz(), frequenceAcquisitionLogHz);
        if (frequenceAcquisition % FACTEUR_EXPANSION != 0) {
            throw new OriginalIllisibleException("Fréquence d'acquisition "
                    + frequenceAcquisition
                    + " Hz non divisible par "
                    + FACTEUR_EXPANSION
                    + " : "
                    + originalWav.getFileName());
        }
        int frequenceSortie = frequenceAcquisition / FACTEUR_EXPANSION;
        int octetsParTrame = source.octetsParTrame();
        byte[] pcm = source.donneesPcm();
        long tramesTotal = (long) pcm.length / octetsParTrame;
        double dureeAcquisitionSecondes = tramesTotal / (double) frequenceAcquisition;
        // Découpage à 5 s **réelles** = au rythme d'ACQUISITION (ni de sortie, ni de l'en-tête). Chaque
        // séquence porte les octets de 5 s de l'enregistrement d'origine, qui, rejoués à `frequenceSortie`
        // (Fe/10), durent 50 s à l'écoute. C'est le découpage du pipeline Vigie-Chiro/Tadarida (segment
        // _000 = 5 s réelles) : les temps du CSV Tadarida sont en secondes réelles DANS cette séquence de
        // 5 s. (Découper au rythme de l'en-tête d'un brut PR déjà expansé donnerait des séquences 10× trop
        // courtes, désalignées des temps Tadarida.)
        int octetsParSequence = DUREE_SEQUENCE_SECONDES * frequenceAcquisition * octetsParTrame;
        String nomOriginal = nomR6;
        String sha256 = empreinteSource(originalWav);
        long tailleSource = tailleSource(originalWav);

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
                // Durée **réelle** de la séquence : au rythme d'ACQUISITION, comme l'offset (L.154) et le
                // découpage (L.116/122). C'est la durée d'enregistrement (≈5 s) qu'on persiste ; rejouée à
                // `frequenceSortie` (Fe/10), la séquence dure ×10 (≈50 s) à l'écoute (#1051).
                double dureeReelleSecondes = tramesSequence / (double) frequenceAcquisition;
                double offsetSource = ((long) offset / octetsParTrame) / (double) frequenceAcquisition;
                // Taille et empreinte courte (#1299), posées à l'écriture : le fichier vient d'être
                // écrit (cache disque chaud), relire ses 64 premiers Kio est quasi gratuit, et
                // l'empreinte est ainsi calculée sur EXACTEMENT ce qu'une réactivation relira.
                sequences.add(new SequenceProduite(
                        index,
                        nomSequence,
                        cheminSequence,
                        frequenceSortie,
                        dureeReelleSecondes,
                        offsetSource,
                        Files.size(cheminSequence),
                        Empreintes.empreinteCourte(cheminSequence)));
                index++;
            }
            return new TransformationOriginal(
                    nomOriginal,
                    originalWav,
                    frequenceAcquisition,
                    frequenceSortie,
                    dureeAcquisitionSecondes,
                    sha256,
                    tailleSource,
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

    /// Taille de la **source** en octets (#1299) ; une lecture impossible est récupérable (rejet du fichier).
    private static long tailleSource(Path originalWav) {
        try {
            return Files.size(originalWav);
        } catch (IOException e) {
            throw new OriginalIllisibleException("Taille de l'original illisible : " + originalWav.getFileName(), e);
        }
    }

    /// Nombre de séquences attendues pour une durée source donnée (R10) : `ceil(D / 5)` — une séquence par
    /// tranche de 5 s **réelles** de l'enregistrement. Exposé pour la lisibilité des tests et de l'IHM
    /// (prévisualisation du volume à produire).
    public static long nombreSequencesAttendu(double dureeSourceSecondes) {
        return (long) Math.ceil(dureeSourceSecondes / DUREE_SEQUENCE_SECONDES);
    }
}
