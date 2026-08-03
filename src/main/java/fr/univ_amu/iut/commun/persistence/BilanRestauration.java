package fr.univ_amu.iut.commun.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Ce qu'une **restauration complète** a réellement fait des dossiers de son (#2727).
///
/// Une restauration complète déplace des gigaoctets et corrige la base : se contenter de dire
/// « restauré » laisse l'utilisateur ignorer que ses nuits ont atterri ailleurs que sur leur disque,
/// ou qu'une nuit connue de la base n'était pas dans la sauvegarde.
///
/// @param manifestePresent `false` pour une sauvegarde antérieure au manifeste (#2726) : faute de
///     savoir d'où venaient les dossiers, ils sont remis à la racine du workspace et la base n'est
///     pas corrigée, comme avant
/// @param placements où chaque racine du manifeste a été remise
/// @param absentesDeLaSauvegarde `root_path` connus de la base mais absents du manifeste : la
///     sauvegarde ne les contenait pas, leur dossier reste introuvable
public record BilanRestauration(
        boolean manifestePresent, List<PlacementRacine> placements, List<String> absentesDeLaSauvegarde) {

    public BilanRestauration {
        placements = List.copyOf(Objects.requireNonNull(placements, "placements"));
        absentesDeLaSauvegarde = List.copyOf(Objects.requireNonNull(absentesDeLaSauvegarde, "absentesDeLaSauvegarde"));
    }

    /// Restauration d'une sauvegarde sans manifeste : rien n'a pu être replacé ni corrigé.
    public static BilanRestauration sansManifeste() {
        return new BilanRestauration(false, List.of(), List.of());
    }

    /// `true` si quelque chose mérite l'attention de l'utilisateur : une nuit a changé de place, une
    /// nuit manque, ou la sauvegarde est trop ancienne pour qu'on sache la replacer.
    public boolean appelleUnRegard() {
        return !manifestePresent
                || !absentesDeLaSauvegarde.isEmpty()
                || placements.stream().anyMatch(PlacementRacine::deplacee);
    }

    /// Résumé prêt à afficher (IHM comme CLI).
    public String enClair() {
        if (!manifestePresent) {
            return "Cette sauvegarde est antérieure au format actuel : elle ne dit pas d'où venaient les"
                    + " dossiers de son. Ils ont été remis dans votre dossier de travail, et la base"
                    + " continue de les désigner par leur ancien emplacement.";
        }
        StringBuilder resume = new StringBuilder(placements.size() + " dossier(s) de son restauré(s).");
        List<PlacementRacine> deplacees =
                placements.stream().filter(PlacementRacine::deplacee).toList();
        if (!deplacees.isEmpty()) {
            resume.append("\n\n")
                    .append(deplacees.size())
                    .append(" n'ont pas retrouvé leur emplacement d'origine (disque absent ?). Elles sont"
                            + " maintenant dans ")
                    .append(dossierDArrivee(deplacees))
                    .append(", et la base a été corrigée pour les y retrouver :")
                    .append(lignes(deplacees.stream()
                            .map(BilanRestauration::nuitDeplacee)
                            .toList()));
        }
        if (!absentesDeLaSauvegarde.isEmpty()) {
            resume.append("\n\n")
                    .append(absentesDeLaSauvegarde.size())
                    .append(" nuit(s) connue(s) de la base n'étaient pas dans la sauvegarde : leur dossier"
                            + " reste introuvable.")
                    .append(lignes(absentesDeLaSauvegarde));
        }
        return resume.toString();
    }

    /// Une nuit déplacée, sur **une** ligne : son nom de dossier, puis d'où elle venait.
    ///
    /// L'ancienne forme mettait l'origine, un saut de ligne, une flèche et la destination complète. Sur
    /// un dialogue de largeur ordinaire, la destination revenait à la ligne et **laissait la flèche
    /// seule** : avec deux nuits, la liste se lisait comme quatre lignes sans lien. Trouvé en ouvrant
    /// l'aperçu, jamais par un test (#3148).
    private static String nuitDeplacee(PlacementRacine placement) {
        Path origine = Path.of(placement.origine());
        Path nom = origine.getFileName();
        Path venaitDe = origine.getParent();
        return (nom == null ? placement.origine() : nom.toString())
                + (venaitDe == null ? "" : ", qui venait de " + venaitDe);
    }

    /// Le dossier où les nuits déplacées ont atterri, nommé **une seule fois**.
    ///
    /// Elles y vont toutes : la destination d'une racine déplacée est le dossier de travail. Répéter ce
    /// chemin à chaque ligne était ce qui les allongeait au point de les faire revenir à la ligne. Si
    /// deux destinations divergeaient malgré tout, on retombe sur une formulation neutre plutôt que
    /// d'en nommer une au hasard.
    private static String dossierDArrivee(List<PlacementRacine> deplacees) {
        List<String> dossiers = deplacees.stream()
                .map(placement -> Path.of(placement.destination()).getParent())
                .map(parent -> parent == null ? "" : parent.toString())
                .distinct()
                .toList();
        return dossiers.size() == 1 && !dossiers.getFirst().isEmpty()
                ? dossiers.getFirst()
                : "votre dossier de travail";
    }

    private static String lignes(List<String> elements) {
        return "\n  - " + String.join("\n  - ", elements);
    }
}
