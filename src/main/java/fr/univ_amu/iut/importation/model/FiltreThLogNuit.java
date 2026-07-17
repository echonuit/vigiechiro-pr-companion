package fr.univ_amu.iut.importation.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Restreint un relevé climatique `THLog` aux lignes d'**une seule nuit** (#1696).
///
/// Sur une carte SD **multi-nuits à log unique**, l'import copiait le THLog entier dans le dossier de
/// chaque nuit, si bien que le diagnostic de chaque passage affichait la courbe de toutes les nuits.
/// Ce filtre ne conserve que les mesures dont l'horodatage tombe dans la nuit voulue, avec la **même
/// bascule midi** que le regroupement des WAV ([PartitionNuits#nuitDe]) : une mesure d'avant midi
/// appartient à la nuit de la veille.
///
/// Pur (aucune IO), directement testable. Format THLog : tabulation, `Date\tHour\tTemperature\tHumidity`
/// avec `dd/MM/yyyy` et `HH:mm:ss` (cf. `LectureThLog` côté diagnostic).
final class FiltreThLogNuit {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private FiltreThLogNuit() {}

    /// Garde l'entête (1re ligne) puis les seules mesures dont l'horodatage bascule sur `dateNuit`. Une
    /// ligne illisible (entête intercalée, champ tronqué) est écartée : elle ne produit de toute façon
    /// aucune mesure à la lecture.
    static List<String> filtrer(List<String> lignes, LocalDate dateNuit) {
        if (lignes.isEmpty()) {
            return List.of();
        }
        List<String> resultat = new ArrayList<>();
        resultat.add(lignes.get(0)); // entête conservée
        for (String ligne : lignes.subList(1, lignes.size())) {
            if (appartientALaNuit(ligne, dateNuit)) {
                resultat.add(ligne);
            }
        }
        return List.copyOf(resultat);
    }

    private static boolean appartientALaNuit(String ligne, LocalDate dateNuit) {
        String[] colonnes = ligne.split("\t");
        if (colonnes.length < 2) {
            return false;
        }
        try {
            LocalDate date = LocalDate.parse(colonnes[0].strip(), DATE);
            LocalTime heure = LocalTime.parse(colonnes[1].strip(), HEURE);
            return PartitionNuits.nuitDe(LocalDateTime.of(date, heure)).equals(dateNuit);
        } catch (RuntimeException illisible) {
            return false;
        }
    }
}
