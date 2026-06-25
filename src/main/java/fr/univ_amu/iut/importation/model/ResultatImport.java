package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import java.util.List;

/// Compte rendu d'un import réussi (parcours P2), renvoyé par `ServiceImport.importer(...)`.
///
/// Il rassemble l'agrégat persisté (le [Passage] et sa [SessionDEnregistrement], avec leurs `id`
/// générés), les volumes traités et les anomalies relevées dans le journal, pour que l'IHM affiche
/// un récapitulatif sans ré-interroger la base.
///
/// @param passage le passage persisté (statut `Transformé`), avec son `id`
/// @param session la session d'enregistrement persistée, avec son `id`
/// @param numeroSerieEnregistreur n° de série de l'enregistreur (upserté depuis le journal)
/// @param nombreOriginaux nombre d'enregistrements originaux importés
/// @param nombreSequences nombre total de séquences d'écoute produites (R10)
/// @param anomalies anomalies relevées dans le journal du capteur (R19), éventuellement vide
/// @param rapport rapport d'import résilient — importés / ignorés / rejetés (#155)
public record ResultatImport(
        Passage passage,
        SessionDEnregistrement session,
        String numeroSerieEnregistreur,
        int nombreOriginaux,
        int nombreSequences,
        List<String> anomalies,
        RapportImport rapport) {

    public ResultatImport {
        anomalies = List.copyOf(anomalies);
        rapport = rapport == null ? new RapportImport(List.of()) : rapport;
    }

    /// Variante sans rapport (rapport vide) : pour les appelants/tests qui n'en construisent pas.
    public ResultatImport(
            Passage passage,
            SessionDEnregistrement session,
            String numeroSerieEnregistreur,
            int nombreOriginaux,
            int nombreSequences,
            List<String> anomalies) {
        this(
                passage,
                session,
                numeroSerieEnregistreur,
                nombreOriginaux,
                nombreSequences,
                anomalies,
                new RapportImport(List.of()));
    }
}
