package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Inventaire d'un dossier de séquences audio **déjà transformées** (×10 + découpe déjà appliqués), en
/// vue d'un import qui les **référence en place** sans rien re-transformer.
///
/// Parcourt le dossier, retient les `.wav`, et les regroupe par **original** : le nom sans son suffixe de
/// tranche `_NNN` (même convention que [fr.univ_amu.iut.commun.model.NommageSequences]). Un original
/// rassemble ainsi ses tranches `_000`, `_001`… Contrairement à un import ordinaire, il n'y a **pas de
/// brut** derrière ces séquences : l'original n'est qu'un **placeholder** dont le chemin sentinelle
/// (`<dossier>/<nomOriginal>`) est une provenance, jamais un fichier ouvrable (même esprit que le
/// placeholder `reconstruit.wav` de [fr.univ_amu.iut.passage.model.CreationPassageArchive]).
///
/// Pour chaque tranche, l'inventaire calcule **dès l'inscription** ses preuves d'identité (taille,
/// [Empreintes#empreinteCourte] : la réactivation les revérifiera au réveil), sa **durée réelle**
/// d'acquisition (en-tête WAV ÷ 10, l'en-tête d'une séquence portant Fe/10, cf.
/// [fr.univ_amu.iut.passage.model.VerificationIdentiteAudio]) et son **horodatage de capture**
/// ([Prefixe#horodatageDe]). Classe **pure** au calcul de durée/empreinte près : elle décrit, elle ne
/// persiste rien.
public final class InventaireTransformesReferences {

    /// Facteur d'expansion ×10 du pipeline (cf. `TransformationAudio`) : l'en-tête d'une séquence
    /// transformée porte Fe/10, sa durée réelle d'acquisition est donc la durée « à l'écoute » divisée
    /// par 10 (#1051, même constante que `VerificationIdentiteAudio`).
    private static final int FACTEUR_EXPANSION = 10;

    /// Suffixe `_NNN` (3 chiffres) juste avant l'extension : délimite la tranche au sein de son original
    /// (`_000`, `_001`…). Même forme que le marqueur de `NommageSequences`.
    private static final Pattern SUFFIXE_SEQUENCE = Pattern.compile("_(\\d{3})(\\.[^.]+)$");

    private InventaireTransformesReferences() {}

    /// Une **séquence transformée** trouvée sur le disque, prête à être référencée.
    ///
    /// @param nomFichier nom du fichier (avec suffixe `_NNN`), clé de jointure Tadarida conservée
    /// @param cheminAbsolu chemin absolu du WAV externe (mode référence : la séquence reste là)
    /// @param index index de la tranche, extrait du suffixe `_NNN`
    /// @param dureeReelleSecondes durée réelle d'acquisition (en-tête WAV ÷ 10)
    /// @param tailleOctets taille du fichier, preuve d'identité complémentaire de l'empreinte
    /// @param empreinte empreinte courte du contenu ([Empreintes#empreinteCourte]), preuve d'identité
    /// @param horodatageCapture heure réelle de début de la tranche, ou `null` si le nom n'est pas horodaté
    public record SequenceTransformee(
            String nomFichier,
            Path cheminAbsolu,
            int index,
            double dureeReelleSecondes,
            long tailleOctets,
            String empreinte,
            LocalDateTime horodatageCapture) {

        public SequenceTransformee {
            Objects.requireNonNull(nomFichier, "nomFichier");
            Objects.requireNonNull(cheminAbsolu, "cheminAbsolu");
        }
    }

    /// Un **original** (placeholder, sans brut) et les séquences transformées qui en dérivent.
    ///
    /// @param nomOriginal nom de l'original (nom d'une tranche sans son suffixe `_NNN`)
    /// @param cheminOriginalSentinelle chemin sentinelle non ouvrable (`<dossier>/<nomOriginal>`), posé
    ///     pour satisfaire la colonne `NOT NULL` sans prétendre à un fichier réel
    /// @param sequences tranches de cet original, triées par index
    public record OriginalTransforme(
            String nomOriginal, Path cheminOriginalSentinelle, List<SequenceTransformee> sequences) {

        public OriginalTransforme {
            Objects.requireNonNull(nomOriginal, "nomOriginal");
            Objects.requireNonNull(cheminOriginalSentinelle, "cheminOriginalSentinelle");
            sequences = List.copyOf(sequences);
        }
    }

    /// Inventorie `dossier` : un groupe [OriginalTransforme] par original, séquences triées par index. Le
    /// dossier sert de racine aux chemins sentinelles des originaux (rendus absolus pour être stables).
    ///
    /// @throws UncheckedIOException si le dossier ou un en-tête WAV est illisible
    public static List<OriginalTransforme> inventorier(Path dossier) {
        Objects.requireNonNull(dossier, "dossier");
        Path racine = dossier.toAbsolutePath();
        Map<String, List<SequenceTransformee>> parOriginal = new LinkedHashMap<>();
        for (Path wav : listerWav(racine)) {
            String nom = wav.getFileName().toString();
            Decoupe decoupe = decouper(nom);
            parOriginal
                    .computeIfAbsent(decoupe.nomOriginal(), cle -> new ArrayList<>())
                    .add(sequenceDe(nom, wav, decoupe.index()));
        }
        List<OriginalTransforme> originaux = new ArrayList<>();
        for (Map.Entry<String, List<SequenceTransformee>> groupe : parOriginal.entrySet()) {
            List<SequenceTransformee> triees = groupe.getValue().stream()
                    .sorted(Comparator.comparingInt(SequenceTransformee::index))
                    .toList();
            originaux.add(new OriginalTransforme(groupe.getKey(), racine.resolve(groupe.getKey()), triees));
        }
        return List.copyOf(originaux);
    }

    /// Découpe d'un nom de séquence : son original (sans `_NNN`) et son index. Repli sur `index = 0` et le
    /// nom entier si le fichier ne porte pas de suffixe reconnu (nom non standard).
    private record Decoupe(String nomOriginal, int index) {}

    private static Decoupe decouper(String nomFichier) {
        Matcher marqueur = SUFFIXE_SEQUENCE.matcher(nomFichier);
        if (marqueur.find()) {
            return new Decoupe(nomFichier.substring(0, marqueur.start()) + marqueur.group(2), parserIndex(marqueur));
        }
        return new Decoupe(nomFichier, 0);
    }

    private static int parserIndex(Matcher marqueur) {
        return Integer.parseInt(marqueur.group(1));
    }

    private static SequenceTransformee sequenceDe(String nom, Path wav, int index) {
        return new SequenceTransformee(
                nom,
                wav,
                index,
                dureeReelleSecondes(wav),
                tailleDe(wav),
                Empreintes.empreinteCourte(wav),
                Prefixe.horodatageDe(nom).orElse(null));
    }

    /// Durée réelle d'acquisition : durée de l'en-tête WAV (au rythme de restitution Fe/10) ramenée au
    /// rythme d'acquisition en la divisant par le facteur d'expansion, sans charger le signal.
    private static double dureeReelleSecondes(Path wav) {
        try {
            FichierWav.EnteteWav entete = FichierWav.lireEntete(wav);
            return entete.dureeSecondes(entete.frequenceEchantillonnageHz()) / FACTEUR_EXPANSION;
        } catch (IOException e) {
            throw new UncheckedIOException("En-tête WAV illisible : " + wav, e);
        }
    }

    private static long tailleDe(Path wav) {
        try {
            return Files.size(wav);
        } catch (IOException e) {
            throw new UncheckedIOException("Taille illisible : " + wav, e);
        }
    }

    private static List<Path> listerWav(Path dossier) {
        try (Stream<Path> flux = Files.list(dossier)) {
            return flux.filter(Files::isRegularFile)
                    .filter(InventaireTransformesReferences::estWav)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du dossier de séquences transformées impossible : " + dossier, e);
        }
    }

    private static boolean estWav(Path fichier) {
        return fichier.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav");
    }
}
