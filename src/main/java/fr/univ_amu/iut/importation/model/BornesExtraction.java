package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.CleDeReglage;
import fr.univ_amu.iut.commun.model.EspaceDisque;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.io.IOException;
import java.nio.file.Path;

/// Ce qui borne la décompression d'une archive en **ressources** (#2732), là où la garde « zip-slip »
/// la bornait déjà en **chemins**. Sans elles, `transferTo` écrit jusqu'à saturer le volume, sur lequel
/// vit aussi la base SQLite du workspace.
///
/// Deux gardes tiennent une seule garantie : **on n'écrit jamais plus que ce qui a été déclaré, et on
/// n'accepte jamais une déclaration qui ne tient pas.** Le premier lit l'inventaire **annoncé** avant
/// le premier octet ([#verifierAvantExtraction]) ; le second compte les octets **réellement** écrits
/// ([#exigerCumulSousLePlafond]) et confronte l'archive à sa propre déclaration, celle sur laquelle
/// l'espace disque a été validé.
///
/// Chaque borne se surcharge par **propriété système** (patron de `vigiechiro.depot.taille-max-mo`),
/// jamais par l'écran Réglages. Le **message de refus** doit nommer la limite atteinte, sans quoi la
/// seule issue serait de renoncer à l'archive.
///
/// L'[ADR 2732] porte le reste, et notamment pourquoi il n'y a **pas** de plafond de taux de
/// compression : de l'audio silencieux et une bombe ZIP sont les mêmes octets.
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

    /// Les bornes de production : les défauts, chacun surchargeable par propriété système.
    public static BornesExtraction parDefaut() {
        return new BornesExtraction(
                (int) entier(CleDeReglage.IMPORT_ZIP_MAX_ENTREES, DEFAUT_MAX_ENTREES),
                entier(CleDeReglage.IMPORT_ZIP_MAX_OCTETS_PAR_ENTREE, DEFAUT_MAX_PAR_ENTREE),
                entier(CleDeReglage.IMPORT_ZIP_MAX_OCTETS_TOTAL, DEFAUT_MAX_TOTAL),
                entier(CleDeReglage.IMPORT_ZIP_MARGE_DISQUE, DEFAUT_MARGE_DISQUE),
                EspaceDisque.reel());
    }

    private static long entier(CleDeReglage cle, long defaut) {
        String surcharge = System.getProperty(cle.propriete());
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
                    + " fichiers, au-delà des " + maxEntrees + " admis. "
                    + CleDeReglage.IMPORT_ZIP_MAX_ENTREES.commentRelever());
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
