package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.LongConsumer;

/// Recopie un flux **bloc par bloc**, en consultant le [JetonAnnulation] entre deux blocs (#2733).
///
/// `InputStream#transferTo` et `Files.copy` recopient **d'un trait** : rien ne s'intercale, et une
/// entrée de plusieurs gigaoctets rend « Annuler » inopérant pendant toute sa durée. Le jeton avait
/// beau être consulté consciencieusement **entre deux entrées**, il ne l'était jamais **pendant**
/// l'entrée qui dure.
///
/// Le trou était le même dans les deux sens, en lecture (`ExtracteurZip`, décompression d'une carte SD)
/// comme en écriture (`EcrivainZip`, export de sons) : les deux passent désormais par ici, ce qui évite
/// que la correction d'un côté laisse l'autre en arrière. C'est le pendant disque de #2712, qui a fermé
/// le même trou côté temporisations réseau.
///
/// **Mémoire bornée** (#104) : c'est le même flux qu'avant, avec un tampon explicite ; rien n'est
/// chargé entier.
public final class CopieInterruptible {

    /// Taille du tampon : la granularité à laquelle l'annulation est consultée.
    private static final int TAILLE_BLOC_OCTETS = 64 * 1024;

    /// Volume recopié entre deux notifications de progression **à l'intérieur** d'une même entrée.
    ///
    /// Une nuit de terrain est faite de tranches de quelques mégaoctets : la plupart des entrées
    /// n'atteignent jamais ce palier et ne coûtent aucune notification supplémentaire. Seules les
    /// grosses en produisent, et ce sont précisément celles pendant lesquelles l'affichage restait figé.
    private static final long PALIER_OCTETS = 4L * 1024 * 1024;

    private CopieInterruptible() {}

    /// Recopie `source` dans `destination` jusqu'à épuisement. **Ne ferme ni l'un ni l'autre** : les deux
    /// appelants écrivent dans un flux d'archive qu'ils continuent d'alimenter après.
    ///
    /// Une annulation laisse la destination **en l'état** : c'est à l'appelant de supprimer ce qu'il
    /// avait commencé, ce que tous deux font déjà (temporaire d'extraction, archive partielle).
    ///
    /// @param jeton consulté entre deux blocs ; une annulation lève [OperationAnnuleeException]
    /// @param surPalier notifié tous les [#PALIER_OCTETS] recopiés, avec le **cumul** écrit. Les paliers
    ///     ne tombent pas à des volumes ronds : un flux compressé rend des blocs de taille irrégulière,
    ///     et le cumul dérive de quelques kilooctets de l'un à l'autre
    /// @return les octets recopiés, comme le rendent `transferTo` et `Files.copy` à qui cette méthode se
    ///     substitue. La décompression s'en sert pour confronter une archive à la taille qu'elle
    ///     annonçait (#2732)
    public static long copier(
            InputStream source, OutputStream destination, JetonAnnulation jeton, LongConsumer surPalier)
            throws IOException {
        byte[] tampon = new byte[TAILLE_BLOC_OCTETS];
        long ecrits = 0;
        long dernierPalier = 0;
        int lus;
        while ((lus = source.read(tampon)) != -1) {
            jeton.leverSiAnnule();
            destination.write(tampon, 0, lus);
            ecrits += lus;
            if (ecrits - dernierPalier >= PALIER_OCTETS) {
                dernierPalier = ecrits;
                surPalier.accept(ecrits);
            }
        }
        return ecrits;
    }
}
