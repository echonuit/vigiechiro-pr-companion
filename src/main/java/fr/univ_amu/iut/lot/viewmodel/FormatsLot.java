package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.EtatLot;
import java.util.Locale;

/// Formatage **textuel** pur des éléments de l'écran M-Lot (récapitulatif, message d'état, ligne
/// d'archive), extrait de [LotViewModel] pour l'alléger (cohésion / seuil GodClass). Sans état ni
/// dépendance JavaFX : directement testable.
final class FormatsLot {

    private FormatsLot() {}

    /// Alerte « espace disque insuffisant » (gigaoctets base 1000) affichée AVANT génération (#…) : volume
    /// estimé des archives (compression comprise) vs espace disponible.
    static String messageEspaceInsuffisant(long requisOctets, long disponibleOctets) {
        return "⚠ Espace disque insuffisant : environ " + enGigaoctets(requisOctets)
                + " Go estimés pour les archives, seulement " + enGigaoctets(disponibleOctets)
                + " Go disponibles. Libérez de l'espace avant de générer.";
    }

    private static String enGigaoctets(long octets) {
        return String.format(Locale.FRENCH, "%.1f", octets / 1_000_000_000.0);
    }

    /// Ligne lisible d'une archive de dépôt : « nom · N fichiers · taille ».
    static String archiveLisible(ArchiveDepot archive) {
        return archive.chemin().getFileName()
                + " · "
                + archive.nombreFichiers()
                + " fichiers · "
                + Formats.octetsLisibles(archive.tailleOctets());
    }

    /// Récapitulatif du lot : « N séquences · volume ».
    static String recapLisible(EtatLot etat) {
        String volume = etat.volumeSequencesOctets() == null
                ? "volume inconnu"
                : Formats.octetsLisibles(etat.volumeSequencesOctets());
        return etat.nombreSequences() + " séquences · " + volume;
    }

    /// Message d'état contextuel du dépôt (déposé, cohérence à corriger, lot préparé, ou vide).
    static String messageEtat(EtatLot etat) {
        if (etat.statut() == StatutWorkflow.DEPOSE) {
            return "Passage déposé le " + etat.deposeLe() + ".";
        }
        if (etat.aDesEchecs()) {
            return "Cohérence : corrigez les contrôles en échec avant de préparer le lot.";
        }
        if (etat.statut() == StatutWorkflow.PRET_A_DEPOSER) {
            // Retour explicite de l'étape ① (#251) : ce que « Préparer » a accompli (lot validé + verrouillé).
            return "✓ Lot préparé : " + etat.nombreSequences()
                    + " séquence(s) validée(s) et verrouillée(s), prêtes à l'archivage.";
        }
        return "";
    }
}
