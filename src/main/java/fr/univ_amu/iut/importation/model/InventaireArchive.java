package fr.univ_amu.iut.importation.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/// Ce qu'une archive ZIP **déclare** d'elle-même (#2732), lu dans son **répertoire central** : la table
/// des matières écrite en fin d'archive, que `ZipFile` lit sans décompresser un seul octet. Une nuit de
/// 10 Go s'inventorie donc instantanément.
///
/// **Déclaré n'est pas constaté.** Une archive piégée ment dans ses en-têtes : c'est la définition
/// même d'une bombe ZIP, qui annonce quelques kilooctets et en écrit quelques gigaoctets. Cet
/// inventaire sert donc à **refuser tôt** ce qui s'annonce déjà hors bornes, et à donner un plafond que
/// [BornesExtraction] confronte ensuite aux octets **réellement** écrits.
///
/// Il ne retient délibérément **pas** la taille compressée : elle ne servait qu'à calculer un taux de
/// décompression, garde retiré parce qu'il ne sépare pas l'audio silencieux d'une bombe (cf.
/// [BornesExtraction]).
///
/// @param nbFichiers entrées « fichier » (les dossiers ne comptent pas) : c'est aussi le dénominateur
///     de la progression « X / N »
/// @param octetsAnnonces total décompressé annoncé, ou `0` si l'archive ne l'annonce pas
/// @param plusGrandeEntree taille décompressée annoncée de la plus grosse entrée
/// @param nomPlusGrandeEntree son nom, pour que le refus désigne un fichier et pas un chiffre
public record InventaireArchive(
        int nbFichiers, long octetsAnnonces, long plusGrandeEntree, String nomPlusGrandeEntree) {

    /// Inventorie `archiveZip` par son répertoire central, sans rien décompresser.
    public static InventaireArchive lire(Path archiveZip) throws IOException {
        int fichiers = 0;
        long annonces = 0;
        long plusGrande = 0;
        String nomPlusGrande = "";
        try (ZipFile zf = new ZipFile(archiveZip.toFile())) {
            for (ZipEntry entree : zf.stream().filter(e -> !e.isDirectory()).toList()) {
                fichiers++;
                // Une taille inconnue vaut -1 : elle ne se compte pas, et laisse l'inventaire à 0 plutôt
                // que de fabriquer un total négatif qui passerait toutes les bornes.
                long taille = Math.max(0, entree.getSize());
                annonces += taille;
                if (taille > plusGrande) {
                    plusGrande = taille;
                    nomPlusGrande = entree.getName();
                }
            }
        }
        return new InventaireArchive(fichiers, annonces, plusGrande, nomPlusGrande);
    }
}
