package fr.univ_amu.iut.audit.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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
        List<Path> retires = new ArrayList<>();
        List<BilanNettoyage.DossierResistant> resistants = new ArrayList<>();
        long octets = 0L;

        for (Path dossier : dossiers) {
            if (dossier == null || !Files.exists(dossier)) {
                continue;
            }
            // Mesurer AVANT de supprimer : après, il n'y a plus rien à peser.
            long taille = tailleDe(dossier);
            String echec = supprimerRecursivement(dossier);
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

    /// Taille cumulée des fichiers sous `dossier`. Un fichier illisible compte pour zéro plutôt que de
    /// faire échouer la mesure : mieux vaut annoncer un gain prudent qu'aucun gain.
    static long tailleDe(Path dossier) {
        try (Stream<Path> chemins = Files.walk(dossier)) {
            return chemins.filter(Files::isRegularFile)
                    .mapToLong(NettoyageDossiersOrphelins::tailleOuZero)
                    .sum();
        } catch (IOException _) {
            return 0L;
        }
    }

    private static long tailleOuZero(Path fichier) {
        try {
            return Files.size(fichier);
        } catch (IOException _) {
            return 0L;
        }
    }

    /// Supprime le contenu puis le dossier, et **rend la première raison d'échec** rencontrée (chaîne
    /// vide si tout est parti).
    ///
    /// La suppression continue après un échec - un fichier verrouillé ne doit pas laisser les autres en
    /// place - mais la cause n'est **pas avalée** (ADR 0008) : c'est elle qui dira à l'utilisateur
    /// pourquoi son ménage n'a pas abouti. « Le dossier est encore là » ne l'aide en rien ; « le
    /// processus ne peut pas accéder au fichier » lui dit de fermer sa fenêtre.
    private static String supprimerRecursivement(Path dossier) {
        try (Stream<Path> chemins = Files.walk(dossier)) {
            // `toList` et non `findFirst` : il faut parcourir TOUT l'arbre pour supprimer ce qui peut
            // l'être, là où un court-circuit s'arrêterait au premier fichier récalcitrant.
            List<String> echecs = chemins.sorted(Comparator.reverseOrder())
                    .map(NettoyageDossiersOrphelins::supprimer)
                    .filter(raison -> !raison.isEmpty())
                    .toList();
            return echecs.isEmpty() ? "" : echecs.getFirst();
        } catch (IOException echec) {
            return raisonLisible(echec);
        }
    }

    private static String supprimer(Path chemin) {
        try {
            Files.deleteIfExists(chemin);
            return "";
        } catch (IOException echec) {
            return raisonLisible(echec);
        }
    }

    /// Le message du système, ou à défaut le type de la panne : une raison vide vaudrait le silence
    /// qu'on cherche justement à éviter.
    private static String raisonLisible(IOException echec) {
        String message = echec.getMessage();
        return message == null || message.isBlank() ? echec.getClass().getSimpleName() : message;
    }
}
