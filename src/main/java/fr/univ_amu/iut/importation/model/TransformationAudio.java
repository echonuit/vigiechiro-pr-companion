package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Moteur de transformation audio (R10/R11) : le **point dur** de la feature import.
///
/// ## Expansion temporelle ×10 = réinterprétation du rythme d'échantillonnage
///
/// Un enregistrement original est un ultrason mono 16 bits échantillonné très vite (ex. 384 000
/// Hz), donc inaudible. La transformation ne **recalcule aucun échantillon** : elle conserve les
/// **mêmes octets PCM** et se contente de déclarer une fréquence de lecture dix fois plus basse
/// (`frequenceSortie = frequenceSource / 10`, ex. 38 400 Hz). Le signal devient alors audible et
/// dure dix fois plus longtemps : c'est l'« expansion de temps » du protocole Vigie-Chiro.
///
/// ## Découpage en séquences de 5 s au NOUVEAU rythme (R10)
///
/// Le signal expansé est tranché en séquences de 5 s *au nouveau rythme* : chaque séquence porte
/// `5 * frequenceSortie` trames (la dernière peut être plus courte). Comme la concaténation des
/// séquences reconstitue exactement les octets source, on a, pour une durée source `D` :
///
/// ```
/// tramesParSequence = 5 * frequenceSortie = frequenceSource / 2
/// nbSequences       = ceil(tramesTotales / tramesParSequence) = ceil(2 * D)
/// ```
///
/// ## Déterminisme (R11)
///
/// Mêmes octets en entrée ⇒ mêmes octets en sortie : le découpage est purement positionnel, les
/// octets PCM sont copiés sans altération (aucun rééchantillonnage, donc aucun clipping
/// introduit), et [FichierWav#ecrire] produit un en-tête canonique fixe. Relancer la
/// transformation réécrit des fichiers identiques au bit près.
///
/// Nommage des séquences (R8) : nom de l'original + suffixe `_000`, `_001`… inséré avant
/// l'extension, via [Prefixe#nommerSequence(String, int)].
public class TransformationAudio {

    /// Durée d'une séquence d'écoute, en secondes, au rythme de sortie (R10).
    public static final int DUREE_SEQUENCE_SECONDES = 5;

    /// Facteur d'expansion temporelle du protocole Vigie-Chiro (R10).
    public static final int FACTEUR_EXPANSION = 10;

    /// Transforme un enregistrement original en séquences d'écoute écrites dans `dossierSortie`.
    ///
    /// @param originalWav chemin du WAV original (mono 16 bits, ex. 384 kHz)
    /// @param dossierSortie dossier `transformes/` où écrire les séquences (créé si absent)
    /// @param prefixe préfixe de la session (sert au nommage R8 des séquences)
    /// @return le détail de la transformation (métadonnées de l'original + séquences produites)
    /// @throws IllegalArgumentException si la fréquence source n'est pas un multiple de 10
    public TransformationOriginal transformer(Path originalWav, Path dossierSortie, Prefixe prefixe) {
        Objects.requireNonNull(originalWav, "originalWav");
        Objects.requireNonNull(dossierSortie, "dossierSortie");
        Objects.requireNonNull(prefixe, "prefixe");
        try {
            FichierWav source = FichierWav.lire(originalWav);
            int frequenceSource = source.frequenceEchantillonnageHz();
            if (frequenceSource % FACTEUR_EXPANSION != 0) {
                throw new IllegalArgumentException("Fréquence source "
                        + frequenceSource
                        + " Hz non divisible par "
                        + FACTEUR_EXPANSION
                        + " : l'expansion temporelle exige un multiple de 10 (R10).");
            }
            int frequenceSortie = frequenceSource / FACTEUR_EXPANSION;
            int octetsParTrame = source.octetsParTrame();
            int tramesParSequence = DUREE_SEQUENCE_SECONDES * frequenceSortie;
            int octetsParSequence = tramesParSequence * octetsParTrame;

            byte[] pcm = source.donneesPcm();
            String nomOriginal = originalWav.getFileName().toString();
            Files.createDirectories(dossierSortie);

            List<SequenceProduite> sequences = new ArrayList<>();
            int index = 0;
            for (int offset = 0; offset < pcm.length; offset += octetsParSequence) {
                int longueur = Math.min(octetsParSequence, pcm.length - offset);
                String nomSequence = prefixe.nommerSequence(nomOriginal, index);
                Path cheminSequence = dossierSortie.resolve(nomSequence);
                // Reprise (#231) : on réécrit **toujours** la séquence (R11 : bytes déterministes). C'est le
                // choix le plus sûr — une séquence périmée ou corrompue par un crash (même de taille
                // identique) est ainsi régénérée, jamais persistée telle quelle. Le gain d'un import
                // interrompu vient de la phase de copie (bruts vérifiés par empreinte), pas de la sortie.
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
                    Empreintes.sha256Hex(originalWav),
                    sequences);
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de la transformation de " + originalWav, e);
        }
    }

    /// Nombre de séquences attendues pour une durée source donnée (R10) : `ceil(2 * D)`. Exposé pour
    /// la lisibilité des tests et de l'IHM (prévisualisation du volume à produire).
    public static long nombreSequencesAttendu(double dureeSourceSecondes) {
        return (long) Math.ceil(FACTEUR_EXPANSION * dureeSourceSecondes / DUREE_SEQUENCE_SECONDES);
    }
}
