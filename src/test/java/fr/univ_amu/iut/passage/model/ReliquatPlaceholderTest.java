package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.NommageSequences;
import fr.univ_amu.iut.commun.model.NommageSequences.TranchesAttendues;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.importation.model.RegenerationParTransformationAudio;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **Le reliquat du placeholder** (#1937) : une séquence ayant perdu une collision de noms à l'import se
/// retrouve accrochée au placeholder d'un passage reconstruit, et plus à l'enregistrement dont elle vient.
///
/// La réactivation parcourt les originaux et ne regarde, pour chacun, que les séquences qui pointent sur
/// lui. Ces orphelines ne figurent donc dans les `sesSequences` d'aucun original réel : leur audio a beau
/// être régénérable et correctement nommé depuis #1932, **personne ne les cherche**. Le placeholder, lui,
/// n'a aucun brut sur la carte : ses séquences tombent dans « introuvables » sans un mot de plus.
///
/// Constaté sur une nuit réelle : 163 séquences, portant 417 observations, muettes à chaque réactivation.
class ReliquatPlaceholderTest {

    private static final int FREQUENCE_ACQUISITION = 2000;
    private static final int CANAUX = 1;
    private static final int BITS = 16;
    private static final int OCTETS_PAR_TRAME = 2;
    private static final int ENTETE_WAV = 44;

    private static final long ID_ANCIEN = 1L;
    private static final long ID_RECENT = 2L;
    private static final long ID_PLACEHOLDER = 99L;
    private static final long ID_SESSION = 7L;

    private final Prefixe prefixe = new Prefixe("640380", 2026, 2, "Z1");

    /// L'ancien dure 15 s : sa tranche de queue porte l'heure `20:53:42`. Le récent **commence** à
    /// `20:53:42` : sa tranche de tête a donc perdu la collision et s'appelle `_205342_001`. C'est elle
    /// qu'on accroche au placeholder, comme la vraie base le fait.
    @Test
    @DisplayName("#1937 : une séquence accrochée au placeholder est rattachée et rebranchée")
    void orpheline_du_placeholder_est_rebranchee(@TempDir Path tmp) throws IOException {
        Path bruts = Files.createDirectories(tmp.resolve("bruts"));
        String nomAncien = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205332.wav";
        String nomRecent = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342.wav";
        Path ancien = ecrireWav(bruts.resolve(nomAncien), secondes(15), 1);
        Path recent = ecrireWav(bruts.resolve(nomRecent), secondes(5), 2);

        // La vérité de l'import : les tranches telles qu'elles ont été écrites, avec leurs noms arbitrés
        // et leurs empreintes. C'est ce que la base a enregistré ce soir-là.
        Path transformes = Files.createDirectories(tmp.resolve("transformes"));
        Map<String, List<String>> arbitrage = NommageSequences.arbitrer(
                prefixe,
                List.of(
                        new TranchesAttendues(nomAncien, NommageSequences.nombreTranches(15)),
                        new TranchesAttendues(nomRecent, NommageSequences.nombreTranches(5))));
        List<SequenceDEcoute> sequences = new ArrayList<>();
        sequences.addAll(tranchesDe(ancien, nomAncien, arbitrage, transformes, ID_ANCIEN, 1L));
        // La tranche du récent a perdu la collision, et c'est le PLACEHOLDER qu'elle désigne.
        sequences.addAll(tranchesDe(recent, nomRecent, arbitrage, transformes, ID_PLACEHOLDER, 100L));

        // Rien sur le disque : tout est à réactiver depuis les bruts.
        Path destination = Files.createDirectories(tmp.resolve("destination"));
        List<SequenceDEcoute> attendues = sequences.stream()
                .map(sequence -> deplacerVers(sequence, destination))
                .toList();

        List<EnregistrementOriginal> originaux = List.of(
                original(ID_ANCIEN, nomAncien, ancien, 15.0),
                original(ID_RECENT, nomRecent, recent, 5.0),
                placeholder());

        BilanReactivation bilan = reactivation()
                .appliquer(
                        attendues, originaux, CandidatsReactivation.dans(bruts), Optional.of(prefixe), progres -> {});

        assertThat(bilan.reactivees)
                .as("les 4 tranches sont régénérables : celle du placeholder aussi")
                .isEqualTo(4);
        assertThat(bilan.manquantes).as("aucune ne doit rester introuvable").isZero();
        assertThat(attendues.stream().allMatch(sequence -> Files.exists(Path.of(sequence.cheminFichier()))))
                .as("chaque séquence est revenue à sa place")
                .isTrue();
    }

    /// **Une relance ne redécoupe pas ce qui est déjà là** (#1962). Prouver un brut par SHA-256 puis le
    /// redécouper en entier pour ne rien rebrancher coûtait 4 min 31 sur une nuit réelle, alors que 4
    /// séquences seulement manquaient.
    ///
    /// Le brut est **retiré du dossier** après la première réactivation : s'il était encore lu, le test
    /// échouerait. C'est ce qui prouve qu'on ne le touche plus.
    @Test
    @DisplayName("#1962 : une relance ne régénère pas un original dont tout est déjà en place")
    void relance_ne_regenere_pas_ce_qui_est_deja_la(@TempDir Path tmp) throws IOException {
        Path bruts = Files.createDirectories(tmp.resolve("bruts"));
        String nomAncien = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205332.wav";
        Path ancien = ecrireWav(bruts.resolve(nomAncien), secondes(15), 1);

        Path transformes = Files.createDirectories(tmp.resolve("transformes"));
        Map<String, List<String>> arbitrage = NommageSequences.arbitrer(
                prefixe, List.of(new TranchesAttendues(nomAncien, NommageSequences.nombreTranches(15))));
        List<SequenceDEcoute> lignes = tranchesDe(ancien, nomAncien, arbitrage, transformes, ID_ANCIEN, 1L);
        Path destination = Files.createDirectories(tmp.resolve("destination"));
        List<SequenceDEcoute> attendues = lignes.stream()
                .map(sequence -> deplacerVers(sequence, destination))
                .toList();
        List<EnregistrementOriginal> originaux = List.of(original(ID_ANCIEN, nomAncien, ancien, 15.0));

        // Première passe : tout revient.
        reactivation()
                .appliquer(attendues, originaux, CandidatsReactivation.dans(bruts), Optional.of(prefixe), p -> {});

        // Le brut disparaît du dossier : une relance qui le lirait encore échouerait ici.
        Files.delete(ancien);
        BilanReactivation relance = reactivation()
                .appliquer(
                        attendues, originaux, CandidatsReactivation.dans(bruts), Optional.of(prefixe), progres -> {});

        assertThat(relance.dejaPresentes)
                .as("les tranches en place sont comptées sans que leur brut soit relu")
                .isEqualTo(attendues.size());
        assertThat(relance.reactivees).isZero();
        assertThat(relance.manquantes)
                .as("rien ne manque : le brut absent n'est même pas cherché")
                .isZero();
    }

    private static ReactivationDepuisBruts reactivation() {
        VerificationIdentiteAudio verification = new VerificationIdentiteAudio();
        return new ReactivationDepuisBruts(
                verification,
                new RebranchementSequences(verification, Optional.empty()),
                Optional.of(new RegenerationParTransformationAudio(new TransformationAudio())));
    }

    /// Régénère les tranches d'un brut sous leurs noms arbitrés et en fait des lignes de base, telles que
    /// l'import les aurait persistées (nom, empreinte, taille).
    private List<SequenceDEcoute> tranchesDe(
            Path brut,
            String nomOriginal,
            Map<String, List<String>> arbitrage,
            Path dossier,
            long idOriginal,
            long premierId)
            throws IOException {
        Path sortie = Files.createDirectories(dossier.resolve(Long.toString(premierId)));
        SequencesRegenerees regenerees = new RegenerationParTransformationAudio(new TransformationAudio())
                .regenerer(brut, nomOriginal, prefixe, FREQUENCE_ACQUISITION, sortie, arbitrage.get(nomOriginal));
        List<SequenceDEcoute> lignes = new ArrayList<>();
        long id = premierId;
        for (Path tranche : regenerees.tranches()) {
            lignes.add(new SequenceDEcoute(
                    id++,
                    tranche.getFileName().toString(),
                    idOriginal,
                    null,
                    null,
                    (double) NommageSequences.DUREE_SEQUENCE_SECONDES,
                    tranche.toString(),
                    false,
                    ID_SESSION,
                    null,
                    Files.size(tranche),
                    empreinte(tranche)));
        }
        return lignes;
    }

    /// La séquence telle que la base l'attend : à sa place définitive, qui n'existe pas encore sur disque.
    private static SequenceDEcoute deplacerVers(SequenceDEcoute sequence, Path destination) {
        return new SequenceDEcoute(
                sequence.id(),
                sequence.nomFichier(),
                sequence.idEnregistrementOriginal(),
                sequence.indexSource(),
                sequence.offsetSourceSecondes(),
                sequence.dureeSecondes(),
                destination.resolve(sequence.nomFichier()).toString(),
                sequence.dansSelection(),
                sequence.idSession(),
                sequence.horodatageCapture(),
                sequence.tailleOctets(),
                sequence.empreinte());
    }

    private static EnregistrementOriginal original(long id, String nom, Path fichier, double duree) throws IOException {
        return new EnregistrementOriginal(
                id,
                nom,
                fichier.toString(),
                duree,
                FREQUENCE_ACQUISITION,
                empreinte(fichier),
                ID_SESSION,
                Files.size(fichier));
    }

    /// Le placeholder d'un passage reconstruit (#1648) : ni durée, ni empreinte, ni fichier.
    private static EnregistrementOriginal placeholder() {
        return new EnregistrementOriginal(
                ID_PLACEHOLDER, "Car640380-2026-Pass2-Z1-reconstruit.wav", "", null, null, null, ID_SESSION, null);
    }

    // --- Helpers (autonomes, pas de helper partagé entre fichiers de test) --------------------

    private static int secondes(int duree) {
        return duree * FREQUENCE_ACQUISITION;
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
