package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/// Calcul d'empreintes **SHA-256** (hexadécimal minuscule) sur des fichiers ou des tableaux
/// d'octets.
///
/// Trois usages :
///
/// - **R9 (copie protégée)** : on hash chaque fichier de la carte SD avant et après la copie pour
/// prouver que la source n'a pas été modifiée (et que la copie est fidèle).
/// - **Intégrité bit-à-bit** : on stocke le SHA-256 de chaque enregistrement original
/// (`original_recording.sha256`) comme référence ultime.
/// - **Identité des séquences d'écoute** (#1299, EPIC #1297) : l'[#empreinteCourte] de chaque
/// séquence, posée à l'import, permet de vérifier qu'un fichier réimporté est bien celui sur
/// lequel les observations avaient été calculées (réactivation d'un passage archivé).
///
/// Implémenté avec [MessageDigest] de `java.base` (aucune dépendance externe ni `java.desktop`).
public final class Empreintes {

    /// Nombre d'octets couverts par l'[#empreinteCourte] : les 64 premiers Kio du fichier. Pour un
    /// WAV, cela couvre l'en-tête complet et le début du signal : deux découpes, deux expansions ou
    /// deux nuits différentes divergent dès ces octets-là. La **taille** du fichier, discriminant
    /// complémentaire (une troncature au-delà des 64 Kio est invisible ici), est persistée à part
    /// (`size_bytes`) : l'empreinte n'a pas besoin de l'inclure.
    public static final int OCTETS_EMPREINTE_COURTE = 64 * 1024;

    private Empreintes() {}

    /// Empreinte **courte** d'un fichier : SHA-256 hexadécimal de ses [#OCTETS_EMPREINTE_COURTE]
    /// premiers octets (du fichier entier s'il est plus petit). De l'ordre de vingt fois moins
    /// d'octets lus qu'un [#sha256Hex(Path)] sur une séquence de 5 s (~1,4 Mo) : assez discriminant
    /// pour l'identité d'une séquence, assez rapide pour être calculé sur des milliers de fichiers
    /// à l'import comme au rétro-remplissage.
    public static String empreinteCourte(Path fichier) {
        MessageDigest digest = nouveauSha256();
        byte[] tampon = new byte[OCTETS_EMPREINTE_COURTE];
        try (InputStream flux = Files.newInputStream(fichier)) {
            int restants = OCTETS_EMPREINTE_COURTE;
            int lus;
            while (restants > 0 && (lus = flux.read(tampon, 0, restants)) != -1) {
                digest.update(tampon, 0, lus);
                restants -= lus;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible pour l'empreinte courte : " + fichier, e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /// SHA-256 hexadécimal d'un fichier, lu par blocs (n'occupe pas toute la RAM).
    public static String sha256Hex(Path fichier) {
        MessageDigest digest = nouveauSha256();
        byte[] tampon = new byte[1 << 16];
        try (InputStream flux = Files.newInputStream(fichier)) {
            int lus;
            while ((lus = flux.read(tampon)) != -1) {
                digest.update(tampon, 0, lus);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible pour le SHA-256 : " + fichier, e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /// SHA-256 hexadécimal d'un tableau d'octets.
    public static String sha256Hex(byte[] octets) {
        return HexFormat.of().formatHex(nouveauSha256().digest(octets));
    }

    private static MessageDigest nouveauSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible sur cette JVM", e);
        }
    }
}
