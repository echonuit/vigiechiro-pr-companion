package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import java.util.List;

/**
 * Compte rendu d'un import réussi (parcours P2), renvoyé par {@code ServiceImport.importer(...)}.
 *
 * <p>Il rassemble l'agrégat persisté (le {@link Passage} et sa {@link SessionDEnregistrement}, avec
 * leurs {@code id} générés), les volumes traités et les anomalies relevées dans le journal, pour
 * que l'IHM affiche un récapitulatif sans ré-interroger la base.
 *
 * @param passage le passage persisté (statut {@code Transformé}), avec son {@code id}
 * @param session la session d'enregistrement persistée, avec son {@code id}
 * @param numeroSerieEnregistreur n° de série de l'enregistreur (upserté depuis le journal)
 * @param nombreOriginaux nombre d'enregistrements originaux importés
 * @param nombreSequences nombre total de séquences d'écoute produites (R10)
 * @param anomalies anomalies relevées dans le journal du capteur (R19), éventuellement vide
 */
public record ResultatImport(
    Passage passage,
    SessionDEnregistrement session,
    String numeroSerieEnregistreur,
    int nombreOriginaux,
    int nombreSequences,
    List<String> anomalies) {

  public ResultatImport {
    anomalies = List.copyOf(anomalies);
  }
}
