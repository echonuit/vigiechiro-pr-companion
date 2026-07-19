package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.ExecutionParallele;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.importation.model.RegenerationParTransformationAudio;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **L'hydratation doit rejouer l'arbitrage des collisions** (#1934), comme la voie « bruts » depuis #1932.
///
/// Elle ne le fait pas : elle régénère chaque brut isolément, donc toujours en `_000`, et revendique par
/// nom. Une tranche que l'import avait renommée `_001` pour cause de collision n'est donc revendiquée par
/// personne. Pire, le brut dont elle est l'unique tranche n'est **pas adopté** - il n'entre jamais en base,
/// et l'arbitrage de la réactivation, qui se calcule sur les originaux connus, ne pourra jamais produire
/// son nom. Le cercle est fermé.
///
/// Constaté sur une nuit réelle : 117 tranches perdues à l'hydratation, dont 4 définitivement hors de
/// portée parce que leur enregistrement n'a jamais été adopté.
class HydratationCollisionTest {

    private static final int FREQUENCE_ACQUISITION = 2000;
    private static final int CANAUX = 1;
    private static final int BITS = 16;
    private static final int OCTETS_PAR_TRAME = 2;
    private static final int ENTETE_WAV = 44;
    private static final long ID_PLACEHOLDER = 99L;
    private static final long ID_SESSION = 7L;

    private final Prefixe prefixe = new Prefixe("640380", 2026, 2, "Z1");

    /// L'ancien dure 15 s : sa tranche de queue porte l'heure `20:53:42`. Le récent **commence** à
    /// `20:53:42` et n'a qu'une tranche, qui a donc perdu la collision et s'appelle `_205342_001`.
    ///
    /// Les séquences sont sans taille ni empreinte, comme celles d'un passage reconstruit depuis la
    /// plateforme : c'est le cas réel, et c'est lui qui doit passer.
    @Test
    @DisplayName("#1934 : la tranche ayant perdu une collision est revendiquée à l'hydratation")
    void tranche_perdante_revendiquee(@TempDir Path tmp) throws IOException {
        Path bruts = Files.createDirectories(tmp.resolve("bruts"));
        String nomAncien = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205332.wav";
        String nomRecent = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342.wav";
        Path ancien = ecrireWav(bruts.resolve(nomAncien), secondes(15), 1);
        Path recent = ecrireWav(bruts.resolve(nomRecent), secondes(5), 2);

        Path destination = Files.createDirectories(tmp.resolve("transformes"));
        List<SequenceDEcoute> sequences = List.of(
                attendue(1L, "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205332_000.wav", destination),
                attendue(2L, "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205337_000.wav", destination),
                attendue(3L, "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342_000.wav", destination),
                // La perdante : l'import l'a écrite `_001`, c'est sous ce nom que la plateforme la connaît.
                attendue(4L, "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342_001.wav", destination));

        // Le nombre de trames vient du **vrai** lecteur d'en-tête, celui que l'inventaire utilise en
        // production : sans lui, l'arbitrage se désactive et le test mesurerait autre chose.
        InventaireBruts inventaire = new InventaireBruts(
                FREQUENCE_ACQUISITION, List.of(inventorie(ancien, nomAncien), inventorie(recent, nomRecent)));
        Optional<ResultatHydratation> resultat = hydratation(inventaire)
                .appliquer(sequences, bruts, Optional.of(prefixe), progres -> {}, JetonAnnulation.neutre());

        assertThat(resultat)
                .as("l'inventaire est exploitable : l'hydratation s'exécute")
                .isPresent();
        BilanReactivation bilan = resultat.orElseThrow().bilan();
        assertThat(bilan.reactivees)
                .as("les 4 tranches sont régénérables, y compris celle qui a perdu la collision")
                .isEqualTo(4);
        assertThat(bilan.manquantes).as("aucune ne doit rester introuvable").isZero();
        assertThat(sequences.stream().allMatch(sequence -> Files.exists(Path.of(sequence.cheminFichier()))))
                .as("chaque séquence est revenue à sa place")
                .isTrue();
    }

    /// **On préfère le trou au mensonge** (ADR 0026). Un inventaire dont un seul brut n'a pas livré sa
    /// durée ne permet plus d'arbitrer juste : les noms de tous les bruts suivants se décaleraient, et des
    /// tranches se rebrancheraient sous des noms que l'import n'a jamais écrits - une observation pointant
    /// sur le mauvais son, en silence.
    ///
    /// L'arbitrage se désactive alors pour la nuit entière. La tranche perdante reste non revendiquée, ce
    /// qui se voit et se dit ; aucune n'est mal appariée.
    @Test
    @DisplayName("ADR 0026 : une durée manquante désactive l'arbitrage plutôt que de le fausser")
    void duree_manquante_desactive_l_arbitrage(@TempDir Path tmp) throws IOException {
        Path bruts = Files.createDirectories(tmp.resolve("bruts"));
        String nomAncien = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205332.wav";
        String nomRecent = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342.wav";
        Path ancien = ecrireWav(bruts.resolve(nomAncien), secondes(15), 1);
        Path recent = ecrireWav(bruts.resolve(nomRecent), secondes(5), 2);

        Path destination = Files.createDirectories(tmp.resolve("transformes"));
        List<SequenceDEcoute> sequences = List.of(
                attendue(1L, "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205332_000.wav", destination),
                attendue(2L, "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205337_000.wav", destination),
                attendue(3L, "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342_000.wav", destination),
                attendue(4L, "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342_001.wav", destination));

        // Le second brut ne livre pas sa durée : l'inventaire n'a pas pu lire son en-tête.
        InventaireBruts inventaire = new InventaireBruts(
                FREQUENCE_ACQUISITION,
                List.of(inventorie(ancien, nomAncien), new BrutInventorie(recent, nomRecent, null)));
        BilanReactivation bilan = hydratation(inventaire)
                .appliquer(sequences, bruts, Optional.of(prefixe), progres -> {}, JetonAnnulation.neutre())
                .orElseThrow()
                .bilan();

        assertThat(bilan.reactivees)
                .as("les trois tranches sans ambiguïté reviennent")
                .isEqualTo(3);
        assertThat(bilan.manquantes)
                .as("la perdante reste non revendiquée : un trou, pas un mauvais appariement")
                .isEqualTo(1);
        assertThat(Files.exists(destination.resolve("Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342_001.wav")))
                .as("rien n'a été écrit sous le nom de la perdante")
                .isFalse();
    }

    /// Un brut tel que l'inventaire le livre depuis #1934 : chemin, nom R6, et nombre de trames lu dans
    /// l'en-tête seul.
    private static BrutInventorie inventorie(Path source, String nomOriginal) throws IOException {
        return new BrutInventorie(
                source, nomOriginal, FichierWav.lireEntete(source).nombreTrames());
    }

    private HydratationDepuisBruts hydratation(InventaireBruts inventaire) {
        VerificationIdentiteAudio verification = new VerificationIdentiteAudio();
        return new HydratationDepuisBruts(
                Optional.of((dossier, prefixeSession) -> Optional.of(inventaire)),
                Optional.of(new RegenerationParTransformationAudio(new TransformationAudio())),
                new RebranchementSequences(verification, Optional.empty(), true),
                new ExecutionParallele(2));
    }

    /// Une séquence telle qu'un passage **reconstruit** la porte : un nom, une place attendue, et rien
    /// d'autre - ni taille, ni durée, ni empreinte. Le CSV de la plateforme ne les transporte pas.
    private static SequenceDEcoute attendue(long id, String nom, Path destination) {
        return new SequenceDEcoute(
                id,
                nom,
                ID_PLACEHOLDER,
                null,
                null,
                null,
                destination.resolve(nom).toString(),
                false,
                ID_SESSION,
                null,
                null,
                null);
    }

    // --- Helpers (autonomes, pas de helper partagé entre fichiers de test) --------------------

    private static int secondes(int duree) {
        return duree * FREQUENCE_ACQUISITION;
    }

    private static Path ecrireWav(Path fichier, int trames, int graine) throws IOException {
        byte[] pcm = new byte[trames * OCTETS_PAR_TRAME];
        for (int i = 0; i < trames; i++) {
            short echantillon = (short) ((graine * 7919 + i) % Short.MAX_VALUE);
            pcm[2 * i] = (byte) (echantillon & 0xFF);
            pcm[2 * i + 1] = (byte) ((echantillon >> 8) & 0xFF);
        }
        int blocAlign = CANAUX * (BITS / 8);
        ByteBuffer buf = ByteBuffer.allocate(ENTETE_WAV + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) CANAUX);
        buf.putInt(FREQUENCE_ACQUISITION);
        buf.putInt(FREQUENCE_ACQUISITION * blocAlign);
        buf.putShort((short) blocAlign);
        buf.putShort((short) BITS);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
        return fichier;
    }
}
