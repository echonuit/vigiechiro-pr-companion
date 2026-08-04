package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.EspaceDisque;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.io.IOException;
import java.nio.file.Path;

/// Ce qui borne la décompression d'une archive en **ressources** (#2732), là où la garde « zip-slip »
/// la bornait déjà en **chemins**.
///
/// Sans bornes, `transferTo` écrivait jusqu'à la fin de l'entrée ou la saturation du disque. L'erreur
/// « Aucun espace disponible » était bien exposée, mais **après** la saturation, qui met en difficulté
/// tout le poste : la base SQLite du workspace vit sur le même volume. #2041 avait posé ce contrôle
/// pour l'import, une étape en aval, et sa Javadoc nommait le trou restant.
///
/// ## Deux gardes, parce qu'un seul se ferait berner
///
/// 1. **Avant d'écrire le premier octet**, sur l'inventaire **annoncé** ([#verifierAvantExtraction]) :
///    nombre d'entrées, taille de la plus grosse, total, et espace disque disponible avec marge.
/// 2. **Pendant la copie**, sur les octets **réellement** écrits ([#exigerCumulSousLePlafond]) : une
///    bombe ZIP ment précisément sur ce que le premier garde lit. Le second la confronte à **sa propre
///    déclaration**, celle sur laquelle l'espace disque a été validé, et n'a donc besoin d'aucune
///    constante arbitraire.
///
/// Ensemble, ils tiennent une garantie simple : **on n'écrit jamais plus que ce qui a été déclaré, et
/// on n'accepte jamais une déclaration qui ne tient pas.**
///
/// ## Pourquoi il n'y a pas de plafond de taux de compression
///
/// C'est le garde classique contre les bombes ZIP, et il a été écrit ici avant d'être **retiré**. Il ne
/// sépare pas les deux populations dans ce domaine : les fixtures de recette, produites par le
/// générateur de cartes SD, se décompressent **137 fois**, et un enregistrement réellement silencieux
/// fait bien davantage. Or de l'audio silencieux et une bombe sont **les mêmes octets** : aucun seuil
/// ne distingue l'un de l'autre.
///
/// Il n'apporte de toute façon rien à la garantie ci-dessus. Un taux énorme ne nuit que s'il aboutit à
/// beaucoup d'octets écrits, ce que le total annoncé, le contrôle d'espace disque et le second garde
/// bornent déjà. Le conserver, c'était payer des refus injustifiés pour une protection qu'on avait par
/// ailleurs.
///
/// ## Des défauts larges, surchargeables, mais pas un réglage
///
/// Une vraie nuit fait quelques milliers de fichiers et une dizaine de gigaoctets : elle doit passer
/// **sans que personne ait rien à régler**. Les défauts sont donc un à deux ordres de grandeur au-dessus.
///
/// Chaque borne se surcharge par **propriété système** (patron de `vigiechiro.depot.taille-max-mo`),
/// pour l'archive légitime mais inhabituelle. Il n'y a délibérément **pas** de réglage dans l'écran
/// Réglages : un naturaliste n'a pas à choisir un plafond d'entrées. C'est le **message de refus** qui
/// doit nommer la limite atteinte, sans quoi la seule issue serait de renoncer à l'archive.
///
/// @param maxEntrees nombre d'entrées « fichier » au-delà duquel l'archive est refusée
/// @param maxOctetsParEntree taille décompressée annoncée maximale d'une entrée
/// @param maxOctetsTotal total décompressé annoncé maximal, et plafond de repli quand l'archive
///     n'annonce aucune taille (le second garde n'a alors rien à quoi la confronter)
/// @param margeDisqueOctets ce qu'on laisse libre sur le volume après extraction
/// @param espaceDisque source de l'espace disponible, injectable pour éprouver le refus sans dépendre
///     de l'état réel de la machine
public record BornesExtraction(
        int maxEntrees,
        long maxOctetsParEntree,
        long maxOctetsTotal,
        long margeDisqueOctets,
        EspaceDisque espaceDisque) {

    /// Une nuit de terrain compte quelques milliers de fichiers.
    private static final int DEFAUT_MAX_ENTREES = 200_000;

    /// Aucun enregistrement d'une nuit n'approche cette taille pour un seul fichier.
    private static final long DEFAUT_MAX_PAR_ENTREE = 50L * 1000 * 1000 * 1000;

    /// Une nuit pèse une dizaine de gigaoctets décompressés.
    private static final long DEFAUT_MAX_TOTAL = 500L * 1000 * 1000 * 1000;

    /// Même marge que le garde d'import (#2041), pour la même raison : ne pas rendre un volume
    /// exactement plein.
    private static final long DEFAUT_MARGE_DISQUE = 100L * 1000 * 1000;

    private static final String PREFIXE = "vigiechiro.import.zip.";

    /// Les bornes de production : les défauts, chacun surchargeable par propriété système.
    public static BornesExtraction parDefaut() {
        return new BornesExtraction(
                (int) entier("max-entrees", DEFAUT_MAX_ENTREES),
                entier("max-octets-par-entree", DEFAUT_MAX_PAR_ENTREE),
                entier("max-octets-total", DEFAUT_MAX_TOTAL),
                entier("marge-disque-octets", DEFAUT_MARGE_DISQUE),
                EspaceDisque.reel());
    }

    private static long entier(String cle, long defaut) {
        String surcharge = System.getProperty(PREFIXE + cle);
        return surcharge == null || surcharge.isBlank() ? defaut : Long.parseLong(surcharge.trim());
    }

    /// Refuse une archive qui s'annonce hors bornes, **avant** que le premier octet soit écrit : à
    /// mi-parcours, le disque est déjà saturé et le reste du poste en pâtit.
    ///
    /// @param destination volume d'accueil, dont l'espace disponible est confronté au total annoncé
    /// @throws RegleMetierException si une borne est franchie ; le message nomme la limite **et** le
    ///     chiffre observé, pour que le refus soit actionnable
    /// @throws IOException si l'espace disque est illisible : un doute doit refuser plutôt que lancer
    ///     une extraction qui échouera à mi-parcours (convention d'[EspaceDisque])
    public void verifierAvantExtraction(InventaireArchive inventaire, Path destination) throws IOException {
        if (inventaire.nbFichiers() > maxEntrees) {
            throw new RegleMetierException("Archive zip refusée : " + inventaire.nbFichiers()
                    + " fichiers, au-delà des " + maxEntrees + " admis. Pour une archive légitime, relancez avec"
                    + " -D" + PREFIXE + "max-entrees=<valeur>.");
        }
        if (inventaire.plusGrandeEntree() > maxOctetsParEntree) {
            throw new RegleMetierException("Archive zip refusée : « " + nomCourt(inventaire.nomPlusGrandeEntree())
                    + " » annonce " + Formats.octetsLisibles(inventaire.plusGrandeEntree()) + ", au-delà des "
                    + Formats.octetsLisibles(maxOctetsParEntree) + " admis pour un seul fichier.");
        }
        if (inventaire.octetsAnnonces() > maxOctetsTotal) {
            throw new RegleMetierException("Archive zip refusée : elle annonce "
                    + Formats.octetsLisibles(inventaire.octetsAnnonces()) + " décompressés, au-delà des "
                    + Formats.octetsLisibles(maxOctetsTotal) + " admis.");
        }
        long requis = inventaire.octetsAnnonces() + margeDisqueOctets;
        long disponible = espaceDisque.disponibleOctets(destination);
        if (disponible < requis) {
            throw new RegleMetierException("Espace disque insuffisant pour décompresser : besoin d'environ "
                    + Formats.octetsLisibles(requis) + ", seulement " + Formats.octetsLisibles(disponible)
                    + " disponibles. Libérez de l'espace, ou décompressez l'archive vous-même.");
        }
    }

    /// Dernier segment d'un nom d'entrée : un chemin interne d'archive peut faire deux cents caractères,
    /// et le refus qui le citerait entier se ferait **tronquer** par la borne du bandeau (#2076), en y
    /// perdant précisément la phrase qui dit quoi faire.
    private static String nomCourt(String nomEntree) {
        int barre = nomEntree.lastIndexOf('/');
        return barre < 0 ? nomEntree : nomEntree.substring(barre + 1);
    }

    /// Arrête une archive qui écrit **plus qu'elle n'a annoncé** : c'est ce que fait une bombe ZIP, et
    /// c'est invisible au garde préalable, qui lit précisément la déclaration mensongère.
    ///
    /// Le plafond est la déclaration elle-même, sans tolérance inventée : c'est sur elle que l'espace
    /// disque a été validé, donc la dépasser invalide la validation. Quand l'archive n'annonce aucune
    /// taille, il ne reste que la borne absolue.
    ///
    /// Appelé aux paliers de progression : le dépassement est donc constaté à un palier près, ce qui
    /// suffit largement pour un mécanisme qui vise les facteurs mille.
    ///
    /// @throws RegleMetierException si le cumul écrit dépasse le plafond
    public void exigerCumulSousLePlafond(long octetsEcrits, InventaireArchive inventaire) {
        long plafond = inventaire.octetsAnnonces() > 0 ? inventaire.octetsAnnonces() : maxOctetsTotal;
        if (octetsEcrits > plafond) {
            throw new RegleMetierException("Archive zip interrompue : elle a écrit "
                    + Formats.octetsLisibles(octetsEcrits) + " alors qu'elle en annonçait "
                    + Formats.octetsLisibles(plafond) + ". Une archive qui ment sur sa taille n'est pas une"
                    + " carte SD.");
        }
    }
}
