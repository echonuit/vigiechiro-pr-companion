package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Résultat de l'inspection <b>en lecture seule</b> d'un dossier de carte SD (avant tout import),
 * produit par {@link InspecteurDossier}. Il photographie ce que contient la source sans rien y
 * écrire (R9) : journal du capteur parsé, enregistrements originaux détectés, présence d'un relevé
 * climatique et état de nommage des fichiers.
 *
 * <p>C'est sur ce rapport que l'IHM peut afficher un récapitulatif (« 191 fichiers, sonde présente,
 * 0 anomalie, fichiers encore bruts ») et proposer ou non l'import.
 *
 * @param dossierSource dossier inspecté (racine de la carte SD ou d'une session)
 * @param cheminJournal chemin du fichier {@code LogPR<n>.txt}, ou {@code null} si absent
 * @param journal contenu parsé du journal ({@link JournalParse}), ou {@code null} si pas de journal
 * @param cheminReleveClimatique chemin du {@code *_THLog.csv}, ou {@code null} si la sonde manque
 *     (R20)
 * @param originaux enregistrements originaux WAV détectés, triés par nom de fichier
 * @param etatNommage état de nommage des originaux : bruts, déjà préfixés, ou aucun (R6)
 */
public record RapportInspection(
    Path dossierSource,
    Path cheminJournal,
    JournalParse journal,
    Path cheminReleveClimatique,
    List<Path> originaux,
    EtatNommage etatNommage) {

  public RapportInspection {
    originaux = List.copyOf(originaux);
  }

  /** {@code true} si un journal du capteur a pu être localisé et parsé. */
  public boolean aUnJournal() {
    return journal != null;
  }

  /** {@code true} si un relevé climatique accompagne la nuit (sonde présente, R20). */
  public boolean aUnReleveClimatique() {
    return cheminReleveClimatique != null;
  }

  /** Journal parsé sous forme d'{@link Optional} (pratique côté appelant). */
  public Optional<JournalParse> journalOptionnel() {
    return Optional.ofNullable(journal);
  }

  /** Nombre d'enregistrements originaux détectés. */
  public int nombreOriginaux() {
    return originaux.size();
  }
}
