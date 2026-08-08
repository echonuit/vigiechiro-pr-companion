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
            supprimerRecursivement(dossier);
            if (Files.exists(dossier)) {
                resistants.add(new BilanNettoyage.DossierResistant(dossier, "le dossier est encore là"));
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

    private static void supprimerRecursivement(Path dossier) {
        try (Stream<Path> chemins = Files.walk(dossier)) {
            chemins.sorted(Comparator.reverseOrder()).forEach(NettoyageDossiersOrphelins::supprimer);
        } catch (IOException _) {
            // Le dossier survivra à l'appel : c'est `Files.exists` qui le constatera, pas cette branche.
        }
    }

    private static void supprimer(Path chemin) {
        try {
            Files.deleteIfExists(chemin);
        } catch (IOException _) {
            // Idem : on ne conclut pas ici, on laisse la vérification finale trancher.
        }
    }
}
