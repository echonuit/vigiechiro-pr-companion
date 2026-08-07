package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/// Le fuseau d'un site se dérive de la **commune** de son point (#3442).
///
/// La commune est elle-même dérivée du GPS et stockée à côté du point (ADR 2791) : la chaîne complète
/// est donc `coordonnées → commune → département INSEE → fuseau`, sans qu'aucun maillon ne soit écrit
/// à la main.
class FuseauDuSiteTest {

    @ParameterizedTest(name = "{2} ({0}) → {1}")
    @CsvSource({
        "97105, America/Guadeloupe, Basse-Terre",
        "97209, America/Martinique, Fort-de-France",
        "97302, America/Cayenne, Cayenne",
        "97415, Indian/Reunion, Saint-Denis",
        "97502, America/Miquelon, Saint-Pierre",
        "97601, Indian/Mayotte, Mamoudzou",
        "97701, America/St_Barthelemy, Saint-Barthelemy",
        "97801, America/Marigot, Saint-Martin",
        "98611, Pacific/Wallis, Mata-Utu",
        "98818, Pacific/Noumea, Noumea"
    })
    @DisplayName("un point d'outre-mer porte le fuseau de son territoire, pas celui de la métropole")
    void le_fuseau_suit_le_territoire(String codeInsee, String fuseauAttendu, String nom) {
        assertThat(FuseauDuSite.pour(new Commune(nom, codeInsee)))
                .as("%s (%s) n'est pas à l'heure de Paris", nom, codeInsee)
                .isEqualTo(ZoneId.of(fuseauAttendu));
    }

    @ParameterizedTest(name = "{1} ({0})")
    @CsvSource({"13001, Aix-en-Provence", "64001, Ahetze", "2A004, Ajaccio", "75056, Paris"})
    @DisplayName("un point de métropole reste à l'heure de Paris")
    void la_metropole_reste_a_paris(String codeInsee, String nom) {
        assertThat(FuseauDuSite.pour(new Commune(nom, codeInsee))).isEqualTo(FuseauDuSite.ZONE);
    }

    @Test
    @DisplayName("commune inconnue : on retombe sur le comportement d'avant, jamais pire")
    void sans_commune_on_retombe_sur_le_repli() {
        // Le cas est réel : point sans GPS, création hors ligne, rattrapage des communes non passé
        // (ADR 2791). Le repli rend alors exactement ce que le produit faisait avant ce chantier.
        assertThat(FuseauDuSite.pour(null)).isEqualTo(FuseauDuSite.ZONE);
    }

    @Test
    @DisplayName("la Polynésie française est ABSENTE de la table, et c'est délibéré")
    void la_polynesie_n_est_pas_couverte() {
        // Elle s'étend sur TROIS fuseaux (Tahiti -10:00, Marquises -09:30, Gambier -09:00), qu'un code
        // départemental ne distingue pas. Un fuseau unique serait faux pour deux archipels sur trois,
        // et faux en silence. Ce cas fige ce renoncement pour qu'il ne se comble pas par distraction.
        assertThat(FuseauDuSite.departementsConnus())
                .as("987 ne peut pas être tranché par le seul département")
                .doesNotContain("987");
        assertThat(FuseauDuSite.pour(new Commune("Papeete", "98735"))).isEqualTo(FuseauDuSite.ZONE);
    }

    @Test
    @DisplayName("aucune entrée de la table ne vaut le repli : une ligne qui ne change rien est une erreur")
    void aucune_entree_ne_repete_le_repli() {
        // Un garde sur la FORME : une entrée valant Europe/Paris passerait inaperçue et laisserait
        // croire le territoire traité. Il vaut pour les lignes futures, pas pour celles d'aujourd'hui.
        assertThat(FuseauDuSite.departementsConnus())
                .allSatisfy(departement -> assertThat(FuseauDuSite.pour(new Commune("x", departement + "01")))
                        .as("le département %s est déclaré, mais rend le repli", departement)
                        .isNotEqualTo(FuseauDuSite.ZONE));
    }

    @ParameterizedTest(name = "depuis {0}")
    @MethodSource("nuitsDeposees")
    @DisplayName("#3442 : la MÊME nuit part à un instant différent selon le territoire du site")
    void la_nuit_deposee_depend_du_territoire(String territoire, Commune commune, String attendu) {
        // C'est le fond du chantier, et il ne se voit que sur la donnée déposée : une nuit du 3 juillet
        // 21:00 n'est pas le même instant à Saint-Denis et à Aix-en-Provence. Avant #3442, les deux
        // partaient à 19:00 GMT - donc l'une des deux était fausse de deux heures.
        String depose = DateTimeFormatter.RFC_1123_DATE_TIME.format(LocalDate.of(2026, 7, 3)
                .atTime(LocalTime.of(21, 0))
                .atZone(FuseauDuSite.pour(commune))
                .withZoneSameInstant(ZoneOffset.UTC));

        assertThat(depose)
                .as("nuit du 3 juillet, 21:00 sur un site de %s", territoire)
                .isEqualTo(attendu);
    }

    private static Stream<Arguments> nuitsDeposees() {
        return Stream.of(
                Arguments.of("métropole", new Commune("Ahetze", "64001"), "Fri, 3 Jul 2026 19:00:00 GMT"),
                Arguments.of("La Réunion", new Commune("Saint-Denis", "97415"), "Fri, 3 Jul 2026 17:00:00 GMT"),
                Arguments.of("Guyane", new Commune("Cayenne", "97302"), "Sat, 4 Jul 2026 00:00:00 GMT"),
                Arguments.of("Nouvelle-Calédonie", new Commune("Noumea", "98818"), "Fri, 3 Jul 2026 10:00:00 GMT"));
    }

    @Test
    @DisplayName("les fuseaux déclarés existent vraiment dans la base de fuseaux de la JVM")
    void les_fuseaux_declares_existent() {
        // `ZoneId.of` lève déjà à l'initialisation de la classe ; ce cas le rend LISIBLE plutôt que de
        // laisser un `ExceptionInInitializerError` sans message au premier appelant.
        assertThat(FuseauDuSite.departementsConnus())
                .isNotEmpty()
                .allSatisfy(departement -> assertThat(ZoneId.getAvailableZoneIds())
                        .contains(FuseauDuSite.pour(new Commune("x", departement + "01"))
                                .getId()));
    }
}
