package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Planifie la répartition des fichiers d'un lot en **archives ZIP** avant toute écriture (#814 / #820) :
/// partition gloutonne garantissant « archive ≤ plafond » (sur un coût source **majoré**), description du
/// plan ([ArchivePlanifiee]) et volume source total (pour le garde-fou disque). Extrait de
/// [CompacteurDepot] pour la cohésion : le compacteur reste dédié à l'écriture parallèle, la planification
/// (sans I/O d'écriture) vit ici.
final class PlanificateurArchives {

    /// Réserve forfaitaire pour le répertoire de fin d'archive (End Of Central Directory, 22 o fixes, plus
    /// une marge) : retranchée du plafond pour le calcul de remplissage.
    private static final long RESERVE_FIN_ARCHIVE = 128;

    private final long tailleMaxOctets;

    PlanificateurArchives(long tailleMaxOctets) {
        this.tailleMaxOctets = tailleMaxOctets;
    }

    /// Partitionne `fichiers` en lots dont le coût majoré ([#coutMaximalDansArchive]) tient sous le plafond
    /// (répartition gloutonne, ordre des fichiers préservé). Chaque lot deviendra une archive indépendante,
    /// compressable en parallèle.
    ///
    /// @throws RegleMetierException si un fichier dépasse à lui seul le plafond (indécoupable)
    List<List<Path>> partitionner(List<Path> fichiers) throws IOException {
        long budget = tailleMaxOctets - RESERVE_FIN_ARCHIVE;
        List<List<Path>> lots = new ArrayList<>();
        List<Path> lotCourant = new ArrayList<>();
        long coutCourant = 0;
        for (Path fichier : fichiers) {
            long cout = coutMaximalDansArchive(fichier);
            if (cout > budget) {
                throw new RegleMetierException("Le fichier "
                        + fichier.getFileName()
                        + " ("
                        + Files.size(fichier)
                        + " o) dépasse à lui seul le plafond de "
                        + tailleMaxOctets
                        + " o : impossible de l'inclure dans une archive de dépôt.");
            }
            if (!lotCourant.isEmpty() && coutCourant + cout > budget) {
                lots.add(lotCourant);
                lotCourant = new ArrayList<>();
                coutCourant = 0;
            }
            lotCourant.add(fichier);
            coutCourant += cout;
        }
        if (!lotCourant.isEmpty()) {
            lots.add(lotCourant);
        }
        return lots;
    }

    /// Décrit les `lots` en [ArchivePlanifiee] (numéro, nombre de fichiers, taille compressée **estimée** =
    /// volume source × [CompacteurDepot#RATIO_COMPRESSION_ESTIME]) pour pré-remplir la table de dépôt (#820).
    List<ArchivePlanifiee> decrire(List<List<Path>> lots) throws IOException {
        List<ArchivePlanifiee> plan = new ArrayList<>(lots.size());
        for (int i = 0; i < lots.size(); i++) {
            List<Path> lot = lots.get(i);
            long volume = volumeSource(lot);
            plan.add(new ArchivePlanifiee(
                    i + 1, lot.size(), (long) (volume * CompacteurDepot.RATIO_COMPRESSION_ESTIME)));
        }
        return plan;
    }

    /// Somme des tailles **source** de `fichiers`, en octets (base du garde-fou d'espace disque).
    long volumeSource(List<Path> fichiers) throws IOException {
        long volume = 0L;
        for (Path fichier : fichiers) {
            volume += Files.size(fichier);
        }
        return volume;
    }

    /// Majorant du nombre d'octets qu'une entrée occupera dans l'archive : données (DEFLATE n'expanse
    /// quasiment jamais, on garde +0,1 % + 64 o de marge), en-tête local, descripteur de données et
    /// entrée du répertoire central (chacun proportionnel à la longueur du nom UTF-8).
    private long coutMaximalDansArchive(Path fichier) throws IOException {
        long taille = Files.size(fichier);
        int nomOctets = fichier.getFileName().toString().getBytes(StandardCharsets.UTF_8).length;
        long donnees = taille + taille / 1000 + 64; // marge large sur l'expansion DEFLATE
        long entetesZip = (30L + nomOctets) + 16L + (46L + nomOctets); // local + descripteur + central
        return donnees + entetesZip;
    }
}
