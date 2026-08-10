package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Sépare les enregistrements **de la série du journal** de ceux d'un autre capteur (#1492).
///
/// ## Pourquoi un tri, et pas seulement un avertissement
///
/// Un dossier qui mélange deux enregistreurs était signalé, puis importé **en entier** : le passage
/// portait l'enregistreur du journal et contenait des séquences d'un autre capteur. La donnée était
/// incohérente avec elle-même, et partait telle quelle au dépôt.
///
/// [AnalyseMelange] savait déjà **détecter** le mélange ; ce qui manquait était la conséquence.
///
/// ## Ce que le tri ne fait pas
///
/// Il ne devine rien. Sans journal - donc sans série attendue - **tout est retenu** : on ne peut pas
/// écarter des fichiers au nom d'une référence qu'on n'a pas. Un fichier dont le nom ne porte aucune
/// série lisible est retenu lui aussi : c'est à la transformation de dire ce qu'elle en fait, et
/// l'écarter ici le ferait disparaître sur un critère qu'il ne peut pas satisfaire.
///
/// @param retenus enregistrements de la série attendue, à importer
/// @param ecartes enregistrements d'un autre capteur, à signaler sans les importer
record TriParSerie(List<Path> retenus, List<Path> ecartes) {

    /// Le motif de nommage du capteur, dont le premier groupe est le **numéro de série**. Même forme que
    /// celui d'[AnalyseMelange], qui s'en sert pour compter les séries présentes.
    private static final Pattern MOTIF = Pattern.compile("PaRecPR(\\d+)_(\\d{8})_\\d{6}");

    TriParSerie {
        retenus = List.copyOf(retenus);
        ecartes = List.copyOf(ecartes);
    }

    /// Trie `originaux` selon `serieAttendue` (celle du journal). Une série attendue `null` ou vide rend
    /// tout retenu : sans référence, on n'écarte rien.
    static TriParSerie selon(List<Path> originaux, String serieAttendue) {
        Objects.requireNonNull(originaux, "originaux");
        if (serieAttendue == null || serieAttendue.isBlank()) {
            return new TriParSerie(originaux, List.of());
        }
        List<Path> retenus = originaux.stream()
                .filter(original -> serieDe(original).map(serieAttendue::equals).orElse(true))
                .toList();
        List<Path> ecartes = originaux.stream()
                .filter(original -> serieDe(original)
                        .map(serie -> !serieAttendue.equals(serie))
                        .orElse(false))
                .toList();
        return new TriParSerie(retenus, ecartes);
    }

    /// Le numéro de série lu dans le nom, vide si le nom ne suit pas le motif du capteur.
    private static java.util.Optional<String> serieDe(Path original) {
        Matcher correspondance = MOTIF.matcher(original.getFileName().toString());
        return correspondance.find() ? java.util.Optional.of(correspondance.group(1)) : java.util.Optional.empty();
    }

    /// Vrai si des enregistrements ont été écartés : c'est ce qui doit se dire au compte rendu.
    boolean aEcarte() {
        return !ecartes.isEmpty();
    }
}
