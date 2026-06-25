package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
/// - **plafond de taille** par archive ([#TAILLE_MAX_DEFAUT_OCTETS] par défaut, 700 Mo ; configurable) :
///   le lot est scindé en autant d'archives que nécessaire ;
/// - **nommage** : chaque archive porte le **préfixe R6** du passage suivi d'un **numéro croissant**
///   (`<préfixe>-1.zip`, `<préfixe>-2.zip`, …).
///
/// **Garantie « archive ≤ plafond ».** La répartition gloutonne ne se fonde **pas** sur la seule taille
/// source : DEFLATE peut très légèrement *grossir* des données peu compressibles (blocs « stored » +
/// overhead) et le format ZIP ajoute des en-têtes (local + répertoire central + descripteur). On
/// majore donc le coût réel de chaque entrée ([#coutMaximalDansArchive]) et on garde une marge pour le
/// répertoire de fin (EOCD) ; en dernier recours, la taille réelle de l'archive est **vérifiée après
/// écriture** ([#verifierTaille]) — défense en profondeur si l'estimation était prise en défaut.
///
/// **Mémoire bornée** (#104) : chaque fichier est recopié **en flux** dans l'archive ([Files#copy]
/// vers le [ZipOutputStream]), jamais chargé entièrement en mémoire.
public final class CompacteurDepot {

    /// Plafond de taille d'une archive de dépôt **par défaut** : **700 Mo**. Base 1000 (et non 2^20) par
    /// prudence — c'est la borne basse d'une éventuelle interprétation « 700 Mio » côté plateforme, donc
    /// on reste conforme dans tous les cas. Surchargeable via le constructeur (réglage applicatif #110).
    public static final long TAILLE_MAX_DEFAUT_OCTETS = 700L * 1000 * 1000;

    /// Réserve forfaitaire pour le répertoire de fin d'archive (End Of Central Directory, 22 o fixes,
    /// plus une marge) : retranchée du plafond pour le calcul de remplissage.
    private static final long RESERVE_FIN_ARCHIVE = 128;

    private final long tailleMaxOctets;

    public CompacteurDepot() {
        this(TAILLE_MAX_DEFAUT_OCTETS);
    }

    /// @param tailleMaxOctets plafond de taille par archive (configurable : réglage applicatif / tests)
    public CompacteurDepot(long tailleMaxOctets) {
        if (tailleMaxOctets <= 0) {
            throw new IllegalArgumentException("Le plafond de taille d'archive doit être positif.");
        }
        this.tailleMaxOctets = tailleMaxOctets;
    }

    /// Plafond de taille appliqué à chaque archive, en octets (exposé pour l'affichage du réglage).
    public long tailleMaxOctets() {
        return tailleMaxOctets;
    }

    /// Scinde `fichiers` en archives ZIP `<prefixe>-N.zip` (N croissant) écrites dans `dossierSortie`,
    /// **chacune garantie sous le plafond**. Renvoie la liste des archives produites, dans l'ordre.
    ///
    /// @throws RegleMetierException si un fichier dépasse à lui seul le plafond (indécoupable)
    public List<ArchiveDepot> compacter(List<Path> fichiers, String prefixe, Path dossierSortie) {
        Objects.requireNonNull(fichiers, "fichiers");
        Objects.requireNonNull(prefixe, "prefixe");
        Objects.requireNonNull(dossierSortie, "dossierSortie");
        long budget = tailleMaxOctets - RESERVE_FIN_ARCHIVE;
        try {
            Files.createDirectories(dossierSortie);
            List<ArchiveDepot> archives = new ArrayList<>();
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
                    archives.add(ecrireArchive(lotCourant, prefixe, dossierSortie, archives.size() + 1));
                    lotCourant = new ArrayList<>();
                    coutCourant = 0;
                }
                lotCourant.add(fichier);
                coutCourant += cout;
            }
            if (!lotCourant.isEmpty()) {
                archives.add(ecrireArchive(lotCourant, prefixe, dossierSortie, archives.size() + 1));
            }
            return archives;
        } catch (IOException e) {
            throw new UncheckedIOException("Génération des archives de dépôt impossible dans " + dossierSortie, e);
        }
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

    private ArchiveDepot ecrireArchive(List<Path> fichiers, String prefixe, Path dossierSortie, int numero)
            throws IOException {
        Path archive = dossierSortie.resolve(prefixe + "-" + numero + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive)))) {
            for (Path fichier : fichiers) {
                zos.putNextEntry(new ZipEntry(fichier.getFileName().toString()));
                Files.copy(fichier, zos); // recopie en flux : mémoire bornée (#104)
                zos.closeEntry();
            }
        }
        long tailleReelle = Files.size(archive);
        verifierTaille(archive, tailleReelle);
        return new ArchiveDepot(archive, numero, tailleReelle, fichiers.size());
    }

    /// Défense en profondeur : si malgré la majoration une archive dépassait le plafond, on échoue
    /// explicitement plutôt que de livrer une archive non conforme à la plateforme.
    private void verifierTaille(Path archive, long tailleReelle) {
        if (tailleReelle > tailleMaxOctets) {
            throw new IllegalStateException("Archive de dépôt "
                    + archive.getFileName()
                    + " de "
                    + tailleReelle
                    + " o au-delà du plafond de "
                    + tailleMaxOctets
                    + " o (estimation de remplissage prise en défaut).");
        }
    }
}
