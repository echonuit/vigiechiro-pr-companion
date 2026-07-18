package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.model.EtatLot;
import java.util.Locale;

/// Formatage **textuel** pur des éléments de l'écran M-Lot (récapitulatif, message d'état, ligne
/// d'archive), extrait de [LotViewModel] pour l'alléger (cohésion / seuil GodClass). Sans état ni
/// dépendance JavaFX : directement testable.
public final class FormatsLot {

    private static final String SEPARATEUR = " · ";

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

    /// Récapitulatif du lot : « N séquences · volume ».
    static String recapLisible(EtatLot etat) {
        String volume = etat.volumeSequencesOctets() == null
                ? "volume inconnu"
                : Formats.octetsLisibles(etat.volumeSequencesOctets());
        return etat.nombreSequences() + " séquences · " + volume;
    }

    /// Bilan des archives **présentes** sur disque (zone droite de la barre de statut au repos, #823) :
    /// « N archive(s) · volume dans depot/ », ou vide sans archive. Volume = somme des tailles des lignes
    /// de la table de génération (réhydratée au chargement, #805).
    public static String bilanArchives(java.util.List<LigneArchive> archives) {
        if (archives.isEmpty()) {
            return "";
        }
        long octets = archives.stream()
                .mapToLong(ligne -> ligne.tailleOctetsProperty().get())
                .sum();
        return archives.size() + " archive(s) · " + Formats.octetsLisibles(octets) + " dans depot/";
    }

    /// Libellé « vivant » d'un dépôt VigieChiro **en cours** (#984) : compteur **honnête** déposées / en
    /// cours / échecs sur le total, pour ne plus laisser croire à un dépôt figé quand seuls les succès
    /// étaient comptés (le dépôt parallèle met plusieurs unités « en cours » à la fois).
    /// Annonce du **lancement du traitement** (étape ④) dans la barre de statut. Le lancement n'a pas de
    /// compteur : c'est un appel unique, dont on dit seulement qu'il est parti (#1543, #1886).
    public static String libelleLancementEnCours() {
        return "Lancement de l'analyse sur Vigie-Chiro…";
    }

    public static String libelleDepotEnCours(int deposees, int enCours, int echecs, int total) {
        if (total == 0) {
            return "Dépôt en préparation…";
        }
        StringBuilder libelle = new StringBuilder("Dépôt : " + deposees + "/" + total + " déposé(s)");
        if (enCours > 0) {
            libelle.append(SEPARATEUR).append(enCours).append(" en cours");
        }
        if (echecs > 0) {
            libelle.append(SEPARATEUR).append(echecs).append(" échec(s)");
        }
        return libelle.toString();
    }

    /// Message d'état contextuel du dépôt (déposé, dépôt entamé, cohérence à corriger, lot préparé, ou vide).
    static String messageEtat(EtatLot etat) {
        if (etat.statut() == StatutWorkflow.DEPOSE) {
            return "Passage déposé le " + etat.deposeLe() + ".";
        }
        if (etat.statut() == StatutWorkflow.DEPOT_EN_COURS) {
            // Dépôt automatique entamé mais incomplet (#980) : interrompu ou en cours d'exécution. La
            // reprise ne re-téléverse que le manquant (moteur reprenable, #982).
            return "Dépôt Vigie-Chiro entamé : des fichiers restent à téléverser (reprise possible).";
        }
        if (etat.aDesEchecs()) {
            return "Cohérence : corrigez les contrôles en échec avant de préparer le dépôt.";
        }
        if (etat.statut() == StatutWorkflow.PRET_A_DEPOSER) {
            // Retour explicite de l'étape ① (#251) : ce que « Préparer » a accompli (dépôt validé + verrouillé).
            return "✓ Dépôt préparé : " + etat.nombreSequences()
                    + " séquence(s) validée(s) et verrouillée(s), prêtes à l'archivage.";
        }
        return "";
    }
}
