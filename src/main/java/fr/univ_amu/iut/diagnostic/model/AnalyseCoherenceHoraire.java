package fr.univ_amu.iut.diagnostic.model;

import fr.univ_amu.iut.commun.model.EphemerideSolaire;
import fr.univ_amu.iut.commun.model.FuseauDuSite;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/// Confronte les horaires d'enregistrement d'un passage à la **fenêtre nocturne** réelle (coucher →
/// lever du soleil) au point d'écoute, via l'[EphemerideSolaire] (#548). Classe **pure** (aucun DAO,
/// aucune IO, aucun JavaFX), directement testable.
///
/// **Fuseau** : VigieChiro est un programme national français ; les horaires du passage sont donc
/// interprétés en heure locale **Europe/Paris** (gestion correcte de l'heure d'été), tandis que
/// l'éphéméride calcule en UTC. La comparaison se fait sur des [Instant] pour rester juste au passage
/// de minuit et au changement d'heure.
public final class AnalyseCoherenceHoraire {

    /// Fuseau des horaires saisis. La décision vivait ici, seule ; elle est désormais au socle
    /// ([FuseauDuSite]) parce que le **chemin d'écriture** vers la plateforme en avait besoin et
    /// employait, lui, le fuseau de la machine (#3406).
    private static final ZoneId FUSEAU_SITE = FuseauDuSite.ZONE;

    private AnalyseCoherenceHoraire() {}

    /// Analyse la cohérence horaire d'un passage.
    ///
    /// @param latitude latitude du point (degrés), ou `null`
    /// @param longitude longitude du point (degrés), ou `null`
    /// @param dateReleve date de la nuit au format ISO `AAAA-MM-JJ`, ou `null`
    /// @param heureDebut heure de début d'enregistrement `HH:mm[:ss]`, ou `null`
    /// @param heureFin heure de fin d'enregistrement `HH:mm[:ss]`, ou `null`
    /// @return la cohérence calculée, ou [CoherenceHoraire#indisponible()] si une donnée manque, est
    ///     illisible, ou si le lieu est en jour/nuit polaire cette nuit-là
    public static CoherenceHoraire analyser(
            Double latitude, Double longitude, String dateReleve, String heureDebut, String heureFin) {
        if (latitude == null || longitude == null || dateReleve == null || heureDebut == null || heureFin == null) {
            return CoherenceHoraire.indisponible();
        }
        try {
            LocalDate nuit = LocalDate.parse(dateReleve);
            LocalTime debut = LocalTime.parse(heureDebut);
            LocalTime fin = LocalTime.parse(heureFin);

            // Le coucher a lieu le soir de la nuit ; le lever, le lendemain matin.
            Optional<LocalTime> coucherUtc = EphemerideSolaire.coucher(latitude, longitude, nuit);
            Optional<LocalTime> leverUtc = EphemerideSolaire.lever(latitude, longitude, nuit.plusDays(1));
            if (coucherUtc.isEmpty() || leverUtc.isEmpty()) {
                return CoherenceHoraire.indisponible();
            }

            Instant coucher = nuit.atTime(coucherUtc.orElseThrow()).toInstant(ZoneOffset.UTC);
            Instant lever = nuit.plusDays(1).atTime(leverUtc.orElseThrow()).toInstant(ZoneOffset.UTC);

            // Un enregistrement qui franchit minuit se termine le lendemain.
            LocalDate jourFin = fin.isBefore(debut) ? nuit.plusDays(1) : nuit;
            Instant demarrage = ZonedDateTime.of(nuit, debut, FUSEAU_SITE).toInstant();
            Instant arret = ZonedDateTime.of(jourFin, fin, FUSEAU_SITE).toInstant();

            boolean demarrageHorsNuit = demarrage.isBefore(coucher);
            boolean arretHorsNuit = arret.isAfter(lever);
            return new CoherenceHoraire(
                    true,
                    coucher.atZone(FUSEAU_SITE).toLocalTime(),
                    lever.atZone(FUSEAU_SITE).toLocalTime(),
                    demarrageHorsNuit,
                    arretHorsNuit);
        } catch (DateTimeParseException horodatageInvalide) {
            return CoherenceHoraire.indisponible();
        }
    }
}
