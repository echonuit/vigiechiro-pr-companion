package fr.univ_amu.iut.commun.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

/// Éphéméride solaire **locale et déterministe** : heures de **lever** et de **coucher** du soleil
/// pour une position (latitude/longitude) et une date, calculées sans aucun accès réseau.
///
/// Sert la feature `diagnostic` (#548) à repérer les événements **hors de la fenêtre nocturne**
/// (coucher → lever) : un démarrage ou un arrêt d'enregistrement en plein jour est suspect pour un
/// protocole chiroptère (activité nocturne).
///
/// **Algorithme** : « sunrise equation » de la NOAA (approximation du mouvement apparent du soleil),
/// précise à environ une minute aux latitudes tempérées, ce qui suffit très largement pour un repère
/// de cohérence horaire. Aucune dépendance, entièrement testable hors ligne.
///
/// **Fuseau** : les heures renvoyées sont en **Temps universel coordonné (UTC)**. La conversion vers
/// l'heure locale du site (l'appelant connaît le fuseau) reste à sa charge : garder ce calcul en UTC
/// le rend déterministe et indépendant du fuseau de la machine.
///
/// **Jour/nuit polaire** : aux latitudes extrêmes, le soleil peut ne jamais se lever (nuit polaire)
/// ni se coucher (jour polaire) à la date donnée. Ces cas renvoient [Optional#empty()] plutôt qu'une
/// valeur trompeuse.
public final class EphemerideSolaire {

    /// Angle du centre du soleil sous l'horizon au lever/coucher officiel (degrés) : -0,833° couvre le
    /// rayon apparent du disque solaire (~0,533°) et la réfraction atmosphérique (~0,3°).
    private static final double ANGLE_HORIZON = -0.833;

    /// Obliquité moyenne de l'écliptique (degrés) : inclinaison de l'axe terrestre.
    private static final double OBLIQUITE = 23.4397;

    /// Jour julien de l'époque J2000.0 (2000-01-01 12:00 UTC), origine des calculs.
    private static final double J2000 = 2451545.0;

    /// Correction de secondes intercalaires (ΔT ≈ 69 s exprimés en fraction de jour).
    private static final double CORRECTION_JOUR = 0.0008;

    private EphemerideSolaire() {}

    /// Heure de **lever** du soleil (UTC), ou vide en cas de jour/nuit polaire à cette date.
    ///
    /// @param latitude latitude du lieu en degrés décimaux (positif vers le nord)
    /// @param longitude longitude du lieu en degrés décimaux (positif vers l'est)
    /// @param date date considérée
    /// @return l'heure UTC du lever, ou [Optional#empty()] si le soleil ne se lève pas ce jour-là
    public static Optional<LocalTime> lever(double latitude, double longitude, LocalDate date) {
        return evenement(latitude, longitude, date, true);
    }

    /// Heure de **coucher** du soleil (UTC), ou vide en cas de jour/nuit polaire à cette date.
    ///
    /// @param latitude latitude du lieu en degrés décimaux (positif vers le nord)
    /// @param longitude longitude du lieu en degrés décimaux (positif vers l'est)
    /// @param date date considérée
    /// @return l'heure UTC du coucher, ou [Optional#empty()] si le soleil ne se couche pas ce jour-là
    public static Optional<LocalTime> coucher(double latitude, double longitude, LocalDate date) {
        return evenement(latitude, longitude, date, false);
    }

    /// Heure **locale** du coucher du soleil dans le fuseau `zone` (converti depuis l'UTC de [#coucher]),
    /// ou vide en cas de jour/nuit polaire.
    ///
    /// @param latitude latitude du lieu en degrés décimaux (positif vers le nord)
    /// @param longitude longitude du lieu en degrés décimaux (positif vers l'est)
    /// @param date date considérée
    /// @param zone fuseau horaire du lieu (gère l'heure d'été)
    /// @return l'heure locale du coucher, ou [Optional#empty()]
    public static Optional<LocalTime> coucherLocal(double latitude, double longitude, LocalDate date, ZoneId zone) {
        return enHeureLocale(coucher(latitude, longitude, date), date, zone);
    }

    /// Heure **locale** du lever du soleil dans le fuseau `zone` (converti depuis l'UTC de [#lever]), ou
    /// vide en cas de jour/nuit polaire.
    ///
    /// @param latitude latitude du lieu en degrés décimaux (positif vers le nord)
    /// @param longitude longitude du lieu en degrés décimaux (positif vers l'est)
    /// @param date date considérée
    /// @param zone fuseau horaire du lieu (gère l'heure d'été)
    /// @return l'heure locale du lever, ou [Optional#empty()]
    public static Optional<LocalTime> leverLocal(double latitude, double longitude, LocalDate date, ZoneId zone) {
        return enHeureLocale(lever(latitude, longitude, date), date, zone);
    }

    /// Convertit une heure UTC (survenant le jour `date`) en heure locale du fuseau `zone`.
    private static Optional<LocalTime> enHeureLocale(Optional<LocalTime> heureUtc, LocalDate date, ZoneId zone) {
        return heureUtc.map(
                utc -> date.atTime(utc).toInstant(ZoneOffset.UTC).atZone(zone).toLocalTime());
    }

    /// Cœur du calcul, partagé par [#lever] et [#coucher] : la seule différence est le signe de
    /// l'angle horaire appliqué au midi solaire (transit).
    private static Optional<LocalTime> evenement(double latitude, double longitude, LocalDate date, boolean lever) {
        // Longitude comptée positive vers l'ouest par l'algorithme NOAA.
        double longitudeOuest = -longitude;
        double jours = jourJulien(date) - J2000 + CORRECTION_JOUR;

        // Midi solaire moyen (en jours depuis J2000) au méridien du lieu.
        double midiMoyen = jours + longitudeOuest / 360.0;

        // Anomalie moyenne du soleil, puis équation du centre → longitude écliptique.
        double anomalie = Math.toRadians(mod360(357.5291 + 0.98560028 * midiMoyen));
        double centre = 1.9148 * Math.sin(anomalie) + 0.0200 * Math.sin(2 * anomalie) + 0.0003 * Math.sin(3 * anomalie);
        double longitudeEcliptique = Math.toRadians(mod360(Math.toDegrees(anomalie) + centre + 282.9372));

        // Instant du transit (soleil au plus haut) et déclinaison du soleil.
        double transit = J2000 + midiMoyen + 0.0053 * Math.sin(anomalie) - 0.0069 * Math.sin(2 * longitudeEcliptique);
        double sinDeclinaison = Math.sin(longitudeEcliptique) * Math.sin(Math.toRadians(OBLIQUITE));
        double declinaison = Math.asin(sinDeclinaison);

        // Angle horaire du lever/coucher ; hors de [-1, 1] ⇒ pas de lever/coucher (jour/nuit polaire).
        double phi = Math.toRadians(latitude);
        double cosAngleHoraire = (Math.sin(Math.toRadians(ANGLE_HORIZON)) - Math.sin(phi) * sinDeclinaison)
                / (Math.cos(phi) * Math.cos(declinaison));
        if (cosAngleHoraire < -1.0 || cosAngleHoraire > 1.0) {
            return Optional.empty();
        }

        double angleHoraire = Math.toDegrees(Math.acos(cosAngleHoraire)) / 360.0;
        double jourJulienEvenement = lever ? transit - angleHoraire : transit + angleHoraire;
        return Optional.of(heureUtc(jourJulienEvenement));
    }

    /// Jour julien (à 12:00 UTC) d'une date grégorienne, via l'algorithme calendaire standard.
    private static long jourJulien(LocalDate date) {
        int annee = date.getYear();
        int mois = date.getMonthValue();
        int jour = date.getDayOfMonth();
        int a = (14 - mois) / 12;
        long y = annee + 4800L - a;
        int m = mois + 12 * a - 3;
        return jour + (153L * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045;
    }

    /// Extrait l'heure UTC (partie fractionnaire de jour) d'une date julienne.
    private static LocalTime heureUtc(double jourJulienDate) {
        double fractionDepuisMinuit = jourJulienDate + 0.5 - Math.floor(jourJulienDate + 0.5);
        long secondes = Math.floorMod(Math.round(fractionDepuisMinuit * 86400.0), 86400L);
        return LocalTime.ofSecondOfDay(secondes);
    }

    /// Normalise un angle en degrés dans l'intervalle [0, 360), y compris pour les valeurs négatives.
    private static double mod360(double degres) {
        return ((degres % 360.0) + 360.0) % 360.0;
    }
}
