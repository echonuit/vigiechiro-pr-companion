package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.persistence.ArborescenceFichiers;
import fr.univ_amu.iut.commun.persistence.GestesFichiers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Retire du disque les dossiers de session qu'aucun passage ne réclame plus (#3482).
///
/// ## Pourquoi ce service existe alors qu'un helper de suppression existe déjà
///
/// `ExtracteurZip.supprimerRecursivement` est **best-effort et silencieux**, ce qui est juste pour un
/// dossier temporaire et faux pour des données d'utilisateur : ici, un fichier qui résiste est
/// précisément ce qu'il faut dire. Le service mesure avant, supprime, **vérifie après**, et ne compte
/// pour libéré que ce qui a effectivement disparu.
///
/// ## Ce qu'il ne fait pas
///
/// Il ne décide pas ce qui est orphelin - c'est le travail de l'audit
/// ([ServiceAuditCoherence]), qui sait lire la base. Ce service ne voit que des chemins, et c'est
/// volontaire : il ne peut pas se tromper de critère puisqu'il n'en porte aucun.
public class NettoyageDossiersOrphelins {

    /// Retire les `dossiers` et rend compte de ce qui est parti, de ce qui a résisté et de la place
    /// regagnée. Un dossier déjà absent n'est ni retiré ni résistant : il n'y avait rien à faire.
    public BilanNettoyage retirer(List<Path> dossiers) {
        return retirer(dossiers, GestesFichiers.reels());
    }

    /// [#retirer(List)], avec les gestes de disque injectés.
    ///
    /// ⚠️ Même motif que [#tailleDe(Path,GestesFichiers)], et il vaut aussi pour la **suppression** : un
    /// dossier qui résiste ne se fabrique pas de façon portable - `File.setWritable(false)` rend `false`
    /// sous Windows, et un `chmod` rendrait le test **inerte** là où la suite le joue chaque mardi
    /// (#3526). Sans cette couture, la **raison** portée par le contrat « au mieux » n'était éprouvée
    /// nulle part, alors que l'ADR 3574 la tient pour la justification de tout ce contrat (#3681).
    BilanNettoyage retirer(List<Path> dossiers, GestesFichiers gestes) {
        List<Path> retires = new ArrayList<>();
        List<BilanNettoyage.DossierResistant> resistants = new ArrayList<>();
        long octets = 0L;

        for (Path dossier : dossiers) {
            if (dossier == null || !Files.exists(dossier)) {
                continue;
            }
            // Mesurer AVANT de supprimer : après, il n'y a plus rien à peser.
            long taille = tailleDe(dossier, gestes);
            String echec = premierEchec(dossier, gestes);
            if (Files.exists(dossier)) {
                resistants.add(new BilanNettoyage.DossierResistant(dossier, echec));
            } else {
                retires.add(dossier);
                octets += taille;
            }
        }
        return new BilanNettoyage(retires, resistants, octets);
    }

    /// Place qu'occupent `dossiers`, pour l'annoncer **avant** de demander confirmation. Un dossier
    /// absent compte pour zéro : on ne promet pas de libérer ce qui n'existe pas.
    public long mesurer(List<Path> dossiers) {
        return dossiers.stream()
                .filter(dossier -> dossier != null && Files.exists(dossier))
                .mapToLong(NettoyageDossiersOrphelins::tailleDe)
                .sum();
    }

    /// Taille cumulée des fichiers sous `dossier`, déléguée à la mesure d'affichage du dépôt.
    ///
    /// ⚠️ C'était une **neuvième** implémentation du même parcours, et elle ne tenait pas le contrat
    /// que son propre commentaire annonçait : « un fichier illisible compte pour zéro plutôt que de
    /// faire échouer la mesure ». `Files.walk` enveloppe l'échec de parcours dans une
    /// `UncheckedIOException` que le `catch (IOException)` ne voyait pas, si bien qu'un sous-dossier
    /// fermé faisait **lever** la mesure - et l'absorber en rendant `0` aurait annoncé « aucun gain »,
    /// ce que le contrat refuse tout autant (#3632).
    ///
    /// [ArborescenceFichiers#octets] porte exactement ce contrat : elle compte ce qu'elle a pu lire, et
    /// ne se laisse pas arrêter par ce qu'elle n'a pas pu. Qui **décide** appelle `peser` (#3627) ; ici
    /// on ne fait qu'annoncer un gain, donc `octets` est le bon des deux.
    static long tailleDe(Path dossier) {
        return tailleDe(dossier, GestesFichiers.reels());
    }

    /// [#tailleDe(Path)], avec les gestes de disque injectés : l'illisibilité d'un dossier ne se
    /// fabrique pas de façon portable, `File.setReadable(false)` rendant `false` sous Windows (#3526).
    static long tailleDe(Path dossier, GestesFichiers gestes) {
        try {
            return ArborescenceFichiers.octets(dossier, gestes);
        } catch (IOException illisible) {
            return 0L;
        }
    }

    /// La **première** raison qui a résisté, ou rien du tout.
    ///
    /// Se ramène au contrat « au mieux » d'[ArborescenceFichiers#effacerAuMieux] (#3574), qui porte la
    /// raison précisément pour cet appelant : c'est le seul dont l'utilisateur attend une explication,
    /// et la lui retirer aurait obligé à refaire le parcours pour la retrouver.
    private static String premierEchec(Path dossier, GestesFichiers gestes) {
        return ArborescenceFichiers.effacerAuMieux(dossier, gestes).stream()
                .findFirst()
                .map(echec -> raisonLisible(echec.cause()))
                .orElse("");
    }

    /// Le message du système, ou à défaut le type de la panne : une raison vide vaudrait le silence
    /// qu'on cherche justement à éviter.
    private static String raisonLisible(IOException echec) {
        String message = echec.getMessage();
        return message == null || message.isBlank() ? echec.getClass().getSimpleName() : message;
    }
}
