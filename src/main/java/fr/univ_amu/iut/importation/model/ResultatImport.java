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
/// @param rapport rapport d'import résilient : importés / ignorés / rejetés (#155)
/// @param volumes volumes lus et écrits par cet import (#2358) ; [VolumesImport#AUCUN] si non mesurés
/// @param participationCreee `true` si l'import a créé une **participation sur Vigie-Chiro** (#1488).
///     L'écriture est faite après la persistance locale, en best-effort ; elle n'était annoncée nulle
///     part, alors qu'elle porte les données de l'utilisateur sur un serveur distant
public record ResultatImport(
        Passage passage,
        SessionDEnregistrement session,
        String numeroSerieEnregistreur,
        int nombreOriginaux,
        int nombreSequences,
        List<String> anomalies,
        RapportImport rapport,
        VolumesImport volumes,
        boolean participationCreee) {

    public ResultatImport {
        anomalies = List.copyOf(anomalies);
        rapport = rapport == null ? new RapportImport(List.of()) : rapport;
        volumes = volumes == null ? VolumesImport.AUCUN : volumes;
    }

    /// Le même import, **avec sa participation Vigie-Chiro créée** (#1488). La participation est écrite
    /// après la persistance locale, donc après la construction de ce résultat : c'est le service qui en
    /// enrichit la copie, plutôt que le moteur qui l'anticiperait.
    public ResultatImport avecParticipationCreee() {
        return new ResultatImport(
                passage,
                session,
                numeroSerieEnregistreur,
                nombreOriginaux,
                nombreSequences,
                anomalies,
                rapport,
                volumes,
                true);
    }

    /// Variante sans volumes ni participation : pour les appelants/tests qui ne les renseignent pas.
    public ResultatImport(
            Passage passage,
            SessionDEnregistrement session,
            String numeroSerieEnregistreur,
            int nombreOriginaux,
            int nombreSequences,
            List<String> anomalies,
            RapportImport rapport) {
        this(
                passage,
                session,
                numeroSerieEnregistreur,
                nombreOriginaux,
                nombreSequences,
                anomalies,
                rapport,
                VolumesImport.AUCUN);
    }

    /// Variante sans participation : pour le moteur, qui produit le résultat **avant** l'écriture distante.
    public ResultatImport(
            Passage passage,
            SessionDEnregistrement session,
            String numeroSerieEnregistreur,
            int nombreOriginaux,
            int nombreSequences,
            List<String> anomalies,
            RapportImport rapport,
            VolumesImport volumes) {
        this(
                passage,
                session,
                numeroSerieEnregistreur,
                nombreOriginaux,
                nombreSequences,
                anomalies,
                rapport,
                volumes,
                false);
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
