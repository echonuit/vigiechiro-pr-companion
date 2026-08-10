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
/// @param regime comment les dossiers ont été replacés, ce qui dit la garantie **qu'on aurait eue**
///     si le disque avait lâché en route (#3563)
public record BilanRestauration(
        boolean manifestePresent,
        List<PlacementRacine> placements,
        List<String> absentesDeLaSauvegarde,
        RegimeRestauration regime) {

    public BilanRestauration {
        placements = List.copyOf(Objects.requireNonNull(placements, "placements"));
        absentesDeLaSauvegarde = List.copyOf(Objects.requireNonNull(absentesDeLaSauvegarde, "absentesDeLaSauvegarde"));
        Objects.requireNonNull(regime, "regime");
    }

    /// Restauration d'une sauvegarde sans manifeste : rien n'a pu être replacé ni corrigé.
    public static BilanRestauration sansManifeste() {
        return new BilanRestauration(false, List.of(), List.of(), RegimeRestauration.COPIE_DIRECTE);
    }

    /// `true` si quelque chose mérite l'attention de l'utilisateur : une nuit a changé de place, une
    /// nuit manque, la sauvegarde est trop ancienne pour qu'on sache la replacer, ou la garantie a été
    /// **dégradée** faute de place (#3563).
    ///
    /// ⚠️ Le régime dégradé y figure parce que la capture de la passe 8 a montré l'inverse : le compte
    /// rendu portait le paragraphe qui dit la garantie moindre, sous un titre « Sauvegarde restaurée »
    /// en information. Un paragraphe d'avertissement sous un titre rassurant ne se lit pas.
    public boolean appelleUnRegard() {
        return !manifestePresent
                || !absentesDeLaSauvegarde.isEmpty()
                || regime == RegimeRestauration.RACINE_PAR_RACINE
                || placements.stream().anyMatch(PlacementRacine::deplacee);
    }

    /// `true` si la restauration laisse un **manque** : on ne sait pas ce qu'on a remplacé, ou une nuit
    /// connue de la base n'était pas dans la sauvegarde.
    ///
    /// ⚠️ Plus étroit qu'[#appelleUnRegard()], et volontairement : une nuit simplement **replacée
    /// ailleurs** n'est pas un manque. C'est le cas normal d'une restauration sur une autre machine,
    /// c'est-à-dire l'usage principal de la sauvegarde complète - le compte rendu nomme déjà l'ancienne
    /// et la nouvelle adresse.
    ///
    /// L'IHM lit [#appelleUnRegard()], qui est juste devant quelqu'un qui lit ce compte rendu ; la CLI
    /// lit celui-ci, parce qu'elle parle à un script (#3500). Les deux surfaces divergent, et c'est
    /// écrit.
    public boolean laisseUnManque() {
        return !manifestePresent || !absentesDeLaSauvegarde.isEmpty();
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
        if (regime == RegimeRestauration.RACINE_PAR_RACINE) {
            // La contrepartie de la souplesse. Sans cette phrase, l'utilisateur croit avoir eu la
            // garantie forte, et un incident ultérieur le trouverait sans explication (#3563).
            resume.append("\n\nLa place ne permettait pas de tout préparer avant de basculer : les nuits"
                    + " ont été remises une nuit à la fois. Chacune est complète, mais si l'opération"
                    + " avait été interrompue, les premières auraient été en place et pas les dernières.");
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
