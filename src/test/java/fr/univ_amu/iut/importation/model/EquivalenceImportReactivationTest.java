package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.NommageSequences;
import fr.univ_amu.iut.commun.model.NommageSequences.TranchesAttendues;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.passage.model.RegenerationSequences;
import fr.univ_amu.iut.passage.model.SequencesRegenerees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **La propriété dont dépend toute la réactivation** : les deux chemins de transformation doivent rendre
/// exactement les mêmes fichiers, mêmes noms et mêmes octets.
///
/// La réactivation depuis les bruts (#1406) n'accepte une tranche régénérée que parce qu'elle retrouve
/// l'empreinte capturée à l'import : la transformation est déterministe (R11), donc reproductible. Cette
/// preuve ne vaut que si le chemin de régénération est **le même pipeline** que celui de l'import. S'il en
/// diverge d'un cheveu, ce n'est pas une tranche qui manque : c'est le fondement de la réactivation qui
/// tombe.
///
/// Chaque moitié est pourtant testée de son côté ([TransformationAudioTest], [ReconciliationNomsTest]) et
/// la réactivation, elle, l'est avec des doublures. La propriété qui compte n'est celle d'aucune des deux :
/// elle vit **entre** elles, et c'est ici qu'elle se vérifie.
class EquivalenceImportReactivationTest {

    /// Fe divisible par 10 (R10), assez petite pour des fixtures légères.
    private static final int FREQUENCE_ACQUISITION = 2000;

    private static final int CANAUX = 1;
    private static final int BITS = 16;
    private static final int OCTETS_PAR_TRAME = 2;
    private static final int ENTETE_WAV = 44;

    private final Prefixe prefixe = new Prefixe("640380", 2026, 2, "Z1");

    /// Deux originaux **en collision** : c'est le cas que le nommage horodaté rend inévitable, et le seul
    /// où les deux chemins peuvent diverger sans qu'aucun test existant ne s'en aperçoive.
    ///
    /// L'ancien dure 15 s, donc sa 3e tranche porte l'heure `20:53:42`. Le récent **commence** à
    /// `20:53:42` : sa tranche de tête veut le même nom. À l'import, [ReconciliationNoms] tranche (le plus
    /// ancien garde `_000`, le perdant passe `_001`). La régénération, elle, doit aboutir au même
    /// résultat.
    @Test
    @DisplayName("Import et réactivation produisent les mêmes tranches : mêmes noms, mêmes octets")
    void les_deux_chemins_produisent_les_memes_tranches(@TempDir Path tmp) throws IOException {
        Path bruts = Files.createDirectories(tmp.resolve("bruts"));
        String nomAncien = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205332.wav";
        String nomRecent = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342.wav";
        // Graines distinctes, et motif dont la période dépasse la tranche : chaque tranche a une empreinte
        // qui lui est propre. Sans cela le volet « octets » de l'assertion serait creux (toutes les
        // empreintes égales, une divergence de contenu passerait inaperçue).
        Path ancien = ecrireWav(bruts.resolve(nomAncien), secondes(15), 1);
        Path recent = ecrireWav(bruts.resolve(nomRecent), secondes(5), 2);

        Map<String, String> parImport = cheminImport(tmp, List.of(ancien, recent));
        Map<String, String> parReactivation = cheminReactivation(tmp, List.of(ancien, recent));

        // Mêmes noms ET mêmes octets : une clé absente d'un côté, ou une empreinte différente, casse la
        // preuve par déterminisme sur laquelle la réactivation accepte les tranches régénérées.
        assertThat(parReactivation)
                .as("les tranches régénérées doivent être celles que l'import a produites")
                .containsExactlyInAnyOrderEntriesOf(parImport);
    }

    /// Le chemin d'**import** : découpe puis [ReconciliationNoms], via le pipeline réel.
    private Map<String, String> cheminImport(Path tmp, List<Path> originaux) throws IOException {
        Path transformes = Files.createDirectories(tmp.resolve("import-transformes"));
        List<SourceOriginal> sources = new ArrayList<>();
        for (int i = 0; i < originaux.size(); i++) {
            Path original = originaux.get(i);
            sources.add(new SourceOriginal(original, original.getFileName().toString(), i + 1));
        }
        new DecoupageParallele(new TransformationAudio(), 2)
                .decouper(
                        sources,
                        transformes,
                        prefixe,
                        FREQUENCE_ACQUISITION,
                        sources.size(),
                        sources.size(),
                        progres -> {},
                        JetonAnnulation.neutre(),
                        SuiviFichiers.inerte());
        return empreintesDe(transformes);
    }

    /// Le chemin de **réactivation** : arbitrage rejoué sur la nuit entière, puis régénération brut par
    /// brut, chacun dans son temporaire - exactement l'enchaînement de `ReactivationDepuisBruts`.
    ///
    /// L'arbitrage est calculé ici parce que c'est le rôle de l'appelant : lui seul a la nuit sous les
    /// yeux. Ce sont les **durées connues en base** qui l'alimentent en production ; le test les tient de
    /// ses fixtures, ce qui revient au même.
    private Map<String, String> cheminReactivation(Path tmp, List<Path> originaux) throws IOException {
        Path racine = Files.createDirectories(tmp.resolve("reactivation-regenere"));
        RegenerationSequences regeneration = new RegenerationParTransformationAudio(new TransformationAudio());
        Map<String, List<String>> arbitrage = NommageSequences.arbitrer(prefixe, attendues(originaux));
        Map<String, String> empreintes = new TreeMap<>();
        for (int i = 0; i < originaux.size(); i++) {
            Path original = originaux.get(i);
            String nomOriginal = original.getFileName().toString();
            Path sortie = Files.createDirectories(racine.resolve(Integer.toString(i)));
            SequencesRegenerees regenerees = regeneration.regenerer(
                    original,
                    nomOriginal,
                    prefixe,
                    FREQUENCE_ACQUISITION,
                    sortie,
                    arbitrage.getOrDefault(nomOriginal, List.of()));
            for (Path tranche : regenerees.tranches()) {
                // Un nom déjà vu signale que la régénération a produit deux fois le même : c'est
                // précisément ce que l'arbitrage doit empêcher.
                empreintes.merge(
                        tranche.getFileName().toString(),
                        empreinte(tranche),
                        (premier, second) -> premier + " ET " + second);
            }
        }
        return empreintes;
    }

    /// Ce que chaque original attend de l'arbitrage, tel que la réactivation le construit depuis la base :
    /// son nom, et son nombre de tranches déduit de sa durée.
    private static List<TranchesAttendues> attendues(List<Path> originaux) throws IOException {
        List<TranchesAttendues> attendues = new ArrayList<>();
        for (Path original : originaux) {
            double duree = (Files.size(original) - ENTETE_WAV) / (double) (FREQUENCE_ACQUISITION * OCTETS_PAR_TRAME);
            attendues.add(
                    new TranchesAttendues(original.getFileName().toString(), NommageSequences.nombreTranches(duree)));
        }
        return attendues;
    }

    private static Map<String, String> empreintesDe(Path dossier) throws IOException {
        Map<String, String> empreintes = new TreeMap<>();
        try (Stream<Path> fichiers = Files.list(dossier)) {
            fichiers.filter(Files::isRegularFile)
                    .forEach(fichier -> empreintes.put(fichier.getFileName().toString(), empreinte(fichier)));
        }
        return empreintes;
    }

    private static String empreinte(Path fichier) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(Files.readAllBytes(fichier)));
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible : " + fichier, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    // --- Helpers (autonomes, pas de helper partagé entre fichiers de test) --------------------

    private static int secondes(int duree) {
        return duree * FREQUENCE_ACQUISITION;
    }

    /// PCM mono 16 bits déterministe, écrit dans un WAV canonique (en-tête 44 octets, little-endian).
    ///
    /// Le motif ne se répète pas à l'intérieur d'un fichier (période > durée), et la `graine` distingue
    /// deux fichiers : deux tranches ont donc des empreintes différentes, ce qui rend la comparaison
    /// d'octets réellement discriminante.
    private static Path ecrireWav(Path fichier, int trames, int graine) throws IOException {
        byte[] pcm = new byte[trames * OCTETS_PAR_TRAME];
        for (int i = 0; i < trames; i++) {
            short echantillon = (short) ((graine * 7919 + i) % Short.MAX_VALUE);
            pcm[2 * i] = (byte) (echantillon & 0xFF);
            pcm[2 * i + 1] = (byte) ((echantillon >> 8) & 0xFF);
        }
        int blocAlign = CANAUX * (BITS / 8);
        // Writer de production (#2864) : memes octets, et c'est le format que l'application
        // saura relire.
        FichierWav.ecrire(fichier, CANAUX, FREQUENCE_ACQUISITION, BITS, pcm, 0, pcm.length);
        return fichier;
    }
}
