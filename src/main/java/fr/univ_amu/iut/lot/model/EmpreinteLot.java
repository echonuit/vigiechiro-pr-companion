package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.Empreintes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// Empreinte de la **liste source** d'un depot (#1993) : ce qui permet de dire, a la reprise, que le
/// lot n'a pas change depuis que le plan a ete pose.
///
/// La partition des sequences en archives ([PlanificateurArchives]) est un glouton a ordre preserve :
/// elle est **deterministe a liste source inchangee**, ce qui autorise a liberer une archive des
/// qu'elle est en ligne puis a la regenerer a l'identique si la reprise en a besoin (#1994, #1995).
///
/// Ce determinisme ne tient qu'a cette condition. Une sequence ajoutee, retiree ou re-transformee
/// decale la partition : l'archive `N` porterait le meme nom pour un contenu different, et serait
/// re-televersee par-dessus celle deja en ligne **sans que rien ne le signale**. L'empreinte rend ce
/// defaut detectable, donc refusable.
///
/// ## Ce qu'elle couvre, et ce qu'elle ne couvre pas
///
/// Le **nom et la taille de chaque fichier, dans l'ordre** : c'est exactement ce dont la partition
/// depend ([PlanificateurArchives#partitionner] repartit sur un cout derive de la taille, sans jamais
/// reordonner). Elle **ne lit pas le contenu** : hacher des dizaines de gigaoctets de WAV a chaque
/// pose de plan couterait bien plus que le defaut qu'on cherche a eviter.
///
/// A ne pas confondre avec [Empreintes], dont c'est ici un usage et non un doublon : cette classe-la
/// empreint le **contenu d'un fichier** (integralement, ou sur ses 64 premiers Kio) pour prouver
/// l'identite d'une sequence ; celle-ci empreint la **composition d'une liste**. Le calcul SHA-256
/// lui-meme est delegue a [Empreintes], seul endroit ou il vit.
public final class EmpreinteLot {

    private EmpreinteLot() {}

    /// Empreinte de `fichiers`, dans l'ordre donne. Deux listes identiques en noms et en tailles ont
    /// la meme empreinte ; toute addition, suppression, reordonnancement ou changement de taille la
    /// change.
    ///
    /// @throws UncheckedIOException si la taille d'un fichier ne peut pas etre lue
    public static String de(List<Path> fichiers) {
        StringBuilder composition = new StringBuilder();
        for (Path fichier : fichiers) {
            composition.append(ligneDe(fichier));
        }
        return Empreintes.sha256Hex(composition.toString().getBytes(StandardCharsets.UTF_8));
    }

    /// Un fichier dans la composition : son nom et sa taille, separes de facon a ce qu'aucune
    /// concatenation de noms ne puisse produire la meme chaine qu'une autre liste.
    private static String ligneDe(Path fichier) {
        try {
            return fichier.getFileName() + " " + Files.size(fichier) + "\n";
        } catch (IOException erreur) {
            throw new UncheckedIOException("Taille illisible pour " + fichier, erreur);
        }
    }
}
