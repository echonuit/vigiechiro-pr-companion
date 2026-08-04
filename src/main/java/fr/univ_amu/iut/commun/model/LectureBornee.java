package fr.univ_amu.iut.commun.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// Lit une **entrée externe** sous un [PlafondLecture] (#3222) : ce qui vient de la carte SD ou du
/// réseau ne se charge plus en mémoire sans qu'on ait accepté combien.
///
/// ## Deux façons de mesurer, parce qu'il y a deux situations
///
/// - **Un fichier annonce sa taille** : le système la donne avant la première lecture, donc le refus
/// tombe avant qu'un octet soit lu ([#lignes]).
/// - **Un flux ne l'annonce pas toujours** : une réponse HTTP en encodage par blocs n'a pas de
/// `Content-Length`. On compte alors **pendant** la lecture, et on s'arrête au franchissement
/// ([#texte]).
///
/// La seconde est le cœur de l'affaire : un plafond vérifié **après** avoir tout chargé ne protège de
/// rien, puisque la mémoire est déjà prise. C'est la différence entre borner une lecture et constater
/// qu'elle était trop grosse.
public final class LectureBornee {

    /// Taille du tampon de lecture d'un flux. C'est aussi ce qu'on peut dépasser au plus avant de
    /// refuser : le contrôle a lieu après chaque bloc, pas après chaque octet.
    public static final int TAILLE_BLOC_OCTETS = 8192;

    private LectureBornee() {}

    /// Lignes d'un fichier texte (UTF-8), refusé au-delà du plafond **avant** d'être lu.
    ///
    /// @throws EntreeTropVolumineuse si le fichier annonce une taille hors plafond
    /// @throws IOException si le fichier est illisible
    public static List<String> lignes(Path fichier, PlafondLecture plafond) throws IOException {
        plafond.exiger(Files.size(fichier), fichier.getFileName().toString());
        return Files.readAllLines(fichier, StandardCharsets.UTF_8);
    }

    /// Contenu textuel d'un flux (UTF-8), lu par blocs et **interrompu** au franchissement du plafond.
    ///
    /// @param origine ce qu'on lisait, tel que le refus le nomme (chemin d'appel, nom de fichier)
    /// @throws EntreeTropVolumineuse dès que le cumul lu franchit le plafond ; ce qui a été lu
    ///     jusque-là est abandonné
    /// @throws IOException si le flux est illisible
    public static String texte(InputStream flux, PlafondLecture plafond, String origine) throws IOException {
        ByteArrayOutputStream accumule = new ByteArrayOutputStream();
        byte[] tampon = new byte[TAILLE_BLOC_OCTETS];
        long lus = 0;
        int bloc;
        while ((bloc = flux.read(tampon)) != -1) {
            lus += bloc;
            plafond.exiger(lus, origine);
            accumule.write(tampon, 0, bloc);
        }
        return accumule.toString(StandardCharsets.UTF_8);
    }
}
