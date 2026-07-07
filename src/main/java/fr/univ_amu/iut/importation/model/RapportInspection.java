package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/// Résultat de l'inspection **en lecture seule** d'un dossier de carte SD (avant tout import),
/// produit par [InspecteurDossier]. Il photographie ce que contient la source sans rien y écrire
/// (R9) : journal du capteur parsé, enregistrements originaux détectés, présence d'un relevé
/// climatique et état de nommage des fichiers.
///
/// C'est sur ce rapport que l'IHM peut afficher un récapitulatif (« 191 fichiers, sonde présente,
/// 0 anomalie, fichiers encore bruts ») et proposer ou non l'import.
///
/// @param dossierSource dossier inspecté (racine de la carte SD ou d'une session)
/// @param cheminJournal chemin du fichier `LogPR<n>.txt`, ou `null` si absent
/// @param journal contenu parsé du journal ([JournalParse]), ou `null` si pas de journal
/// @param cheminReleveClimatique chemin du `*_THLog.csv`, ou `null` si la sonde manque (R20)
/// @param originaux enregistrements originaux WAV détectés, triés par nom de fichier
/// @param etatNommage état de nommage des originaux : bruts, déjà préfixés, ou aucun (R6)
/// @param frequenceEnTeteHz fréquence d'échantillonnage lue dans l'en-tête d'un original représentatif
///     (le premier lisible), ou `null` si aucun original lisible. Sert à repérer, à l'aperçu, un
///     enregistrement **déjà ralenti** (cf. [DetectionRalenti]) au regard de la fréquence du journal.
/// @param cyclesJournal cycles d'acquisition (réveil→veille) extraits du journal, un par nuit ; vide si
///     pas de journal. Sert à qualifier la **complétude** des nuits détectées (cf. [#partitionNuits()]).
public record RapportInspection(
        Path dossierSource,
        Path cheminJournal,
        JournalParse journal,
        Path cheminReleveClimatique,
        List<Path> originaux,
        EtatNommage etatNommage,
        Integer frequenceEnTeteHz,
        List<CycleAcquisition> cyclesJournal) {

    public RapportInspection {
        originaux = List.copyOf(originaux);
        cyclesJournal = List.copyOf(cyclesJournal);
    }

    /// `true` si un journal du capteur a pu être localisé et parsé.
    public boolean aUnJournal() {
        return journal != null;
    }

    /// `true` si un relevé climatique accompagne la nuit (sonde présente, R20).
    public boolean aUnReleveClimatique() {
        return cheminReleveClimatique != null;
    }

    /// Journal parsé sous forme d'[Optional] (pratique côté appelant).
    public Optional<JournalParse> journalOptionnel() {
        return Optional.ofNullable(journal);
    }

    /// Nombre d'enregistrements originaux détectés.
    public int nombreOriginaux() {
        return originaux.size();
    }

    /// Détection du cas limite **« mélange »** (#33) : plusieurs enregistreurs et/ou plusieurs nuits,
    /// dérivée des noms des [#originaux] (`PaRecPR<série>_<date>_…`). Sert d'avertissement non bloquant
    /// à l'inspection.
    public AnalyseMelange melange() {
        return AnalyseMelange.depuis(originaux);
    }

    /// Détection du cas limite **« incohérence »** (#33) : l'identité déclarée (journal du capteur et
    /// nom du relevé climatique) confrontée aux séries/dates portées par les [#originaux]. Sert
    /// d'avertissement non bloquant à l'inspection.
    public AnalyseCoherence coherence() {
        return AnalyseCoherence.depuis(journal, cheminReleveClimatique, originaux);
    }

    /// Partition des [#originaux] en **nuits** (soir → matin), avec l'état complet/tronqué de chacune
    /// déduit des [#cyclesJournal()]. Une seule nuit ⇒ liste à un élément (import classique). Sert à
    /// l'IHM (liste des nuits + inclure/exclure) et au découpage de l'import en un passage par nuit.
    public List<NuitDetectee> partitionNuits() {
        return PartitionNuits.partitionner(originaux, cyclesJournal);
    }
}
