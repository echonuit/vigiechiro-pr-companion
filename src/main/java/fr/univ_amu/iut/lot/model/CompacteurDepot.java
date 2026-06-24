package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/// Compacte les fichiers d'un lot en **archives ZIP de dépôt** conformes aux contraintes Tadarida /
/// Vigie-Chiro (#110) :
///
/// - **plafond de taille** par archive ([#TAILLE_MAX_DEFAUT_OCTETS], 700 Mo) : le lot est scindé en
///   autant d'archives que nécessaire ;
/// - **nommage** : chaque archive porte le **préfixe R6** du passage suivi d'un **numéro croissant**
///   (`<préfixe>-1.zip`, `<préfixe>-2.zip`, …).
///
/// **Répartition** : remplissage glouton dans l'ordre fourni — on ajoute les fichiers à l'archive
/// courante tant que la somme de leurs tailles reste sous le plafond, puis on en ouvre une nouvelle.
/// Le plafond s'applique à la **taille cumulée des fichiers** (non compressés) ; comme l'écriture passe
/// par une compression DEFLATE qui ne fait que réduire, l'archive produite reste sous le plafond.
///
/// **Mémoire bornée** (#104) : chaque fichier est recopié **en flux** dans l'archive ([Files#copy]
/// vers le [ZipOutputStream]), jamais chargé entièrement en mémoire.
public final class CompacteurDepot {

    /// Plafond de taille d'une archive de dépôt : **700 Mo**. Base 1000 (et non 2^20) par prudence —
    /// c'est la borne basse d'une éventuelle interprétation « 700 Mio » côté plateforme, donc on reste
    /// conforme dans tous les cas.
    public static final long TAILLE_MAX_DEFAUT_OCTETS = 700L * 1000 * 1000;

    private final long tailleMaxOctets;

    public CompacteurDepot() {
        this(TAILLE_MAX_DEFAUT_OCTETS);
    }

    /// @param tailleMaxOctets plafond de taille par archive (injectable, p. ex. pour les tests)
    public CompacteurDepot(long tailleMaxOctets) {
        if (tailleMaxOctets <= 0) {
            throw new IllegalArgumentException("Le plafond de taille d'archive doit être positif.");
        }
        this.tailleMaxOctets = tailleMaxOctets;
    }

    /// Scinde `fichiers` en archives ZIP `<prefixe>-N.zip` (N croissant) écrites dans `dossierSortie`,
    /// chacune sous le plafond de taille. Renvoie la liste des archives produites, dans l'ordre.
    ///
    /// @throws RegleMetierException si un fichier dépasse à lui seul le plafond (indécoupable)
    public List<ArchiveDepot> compacter(List<Path> fichiers, String prefixe, Path dossierSortie) {
        Objects.requireNonNull(fichiers, "fichiers");
        Objects.requireNonNull(prefixe, "prefixe");
        Objects.requireNonNull(dossierSortie, "dossierSortie");
        try {
            Files.createDirectories(dossierSortie);
            List<ArchiveDepot> archives = new ArrayList<>();
            List<Path> lotCourant = new ArrayList<>();
            long tailleCourante = 0;
            for (Path fichier : fichiers) {
                long taille = Files.size(fichier);
                if (taille > tailleMaxOctets) {
                    throw new RegleMetierException("Le fichier "
                            + fichier.getFileName()
                            + " ("
                            + taille
                            + " o) dépasse à lui seul le plafond de "
                            + tailleMaxOctets
                            + " o : impossible de l'inclure dans une archive de dépôt.");
                }
                if (!lotCourant.isEmpty() && tailleCourante + taille > tailleMaxOctets) {
                    archives.add(ecrireArchive(lotCourant, prefixe, dossierSortie, archives.size() + 1));
                    lotCourant = new ArrayList<>();
                    tailleCourante = 0;
                }
                lotCourant.add(fichier);
                tailleCourante += taille;
            }
            if (!lotCourant.isEmpty()) {
                archives.add(ecrireArchive(lotCourant, prefixe, dossierSortie, archives.size() + 1));
            }
            return archives;
        } catch (IOException e) {
            throw new UncheckedIOException("Génération des archives de dépôt impossible dans " + dossierSortie, e);
        }
    }

    private static ArchiveDepot ecrireArchive(List<Path> fichiers, String prefixe, Path dossierSortie, int numero)
            throws IOException {
        Path archive = dossierSortie.resolve(prefixe + "-" + numero + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive)))) {
            for (Path fichier : fichiers) {
                zos.putNextEntry(new ZipEntry(fichier.getFileName().toString()));
                Files.copy(fichier, zos); // recopie en flux : mémoire bornée (#104)
                zos.closeEntry();
            }
        }
        return new ArchiveDepot(archive, numero, Files.size(archive), fichiers.size());
    }
}
