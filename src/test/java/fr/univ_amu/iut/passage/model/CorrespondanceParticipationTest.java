package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationADeposer;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Correspondance **pure** passage ↔ participation (axe 4) : construction du corps API (push, dates RFC 1123
/// UTC + météo + configuration) et retraduction météo/config (pull), sans réseau ni base.
class CorrespondanceParticipationTest {

    @Test
    @DisplayName("versParticipation : point, dates RFC 1123 UTC (franchit minuit), météo, config complète")
    void vers_participation() {
        Passage passage = passage("{\"vent\":\"FAIBLE\",\"couvertureNuageuse\":\"DE_25_A_50\"}");
        MaterielMicro micro = new MaterielMicro(42L, PositionMicro.CANOPEE, 4.0, "ICS");

        ParticipationADeposer p = CorrespondanceParticipation.versParticipation("Z41", passage, micro);

        assertThat(p.point()).isEqualTo("Z41");
        // Dates revérifiées par aller-retour vers le fuseau local (déterministe quel que soit le fuseau).
        assertThat(instantLocal(p.dateDebut())).isEqualTo(LocalDateTime.of(2026, 7, 3, 21, 0));
        assertThat(instantLocal(p.dateFin())).isEqualTo(LocalDateTime.of(2026, 7, 4, 5, 0)); // franchit minuit
        assertThat(p.meteo().vent()).isEqualTo("FAIBLE");
        assertThat(p.meteo().couverture()).isEqualTo("25-50");
        assertThat(p.configuration())
                .containsEntry("detecteur_enregistreur_type", "PassiveRecorder")
                .containsEntry("detecteur_enregistreur_numero_serie", "1997632") // clé canonique (#1844)
                .containsEntry("micro0_type", "ICS")
                .containsEntry("micro0_position", "CANOPEE")
                .containsEntry("micro0_hauteur", "4");
    }

    @Test
    @DisplayName("versParticipation : sans météo ni micro → meteo null, config réduite au détecteur")
    void vers_participation_minimale() {
        ParticipationADeposer p =
                CorrespondanceParticipation.versParticipation("Z41", passage(null), MaterielMicro.vide(42L));

        assertThat(p.meteo()).isNull();
        assertThat(p.configuration())
                .containsOnlyKeys("detecteur_enregistreur_type", "detecteur_enregistreur_numero_serie");
    }

    @Test
    @DisplayName("#1844 : le n° de série part sous la clé CANONIQUE, celle que lit le formulaire web")
    void vers_participation_utilise_la_cle_canonique() {
        ParticipationADeposer p =
                CorrespondanceParticipation.versParticipation("Z41", passage(null), MaterielMicro.vide(42L));

        assertThat(p.configuration())
                .as("l'app poussait « numserie », que le front ne lie pas : le n° arrivait invisible")
                .containsEntry("detecteur_enregistreur_numero_serie", "1997632")
                .doesNotContainKey("detecteur_enregistreur_numserie");
    }

    @Test
    @DisplayName("#1844 : la configuration distante est PRÉSERVÉE — un envoi n'efface plus ce qu'on ne modélise pas")
    void vers_participation_preserve_la_configuration_distante() {
        Map<String, String> distante = Map.of(
                "micro0_numero_serie", "M-123",
                "micro1_type", "SMX-U1",
                "canal_expansion_temps", "OUI",
                "detecteur_enregistreur_numserie", "ANCIEN");

        ParticipationADeposer p =
                CorrespondanceParticipation.versParticipation("Z41", passage(null), MaterielMicro.vide(42L), distante);

        assertThat(p.configuration())
                .as("les champs du formulaire web que l'app ne modélise pas survivent à l'envoi")
                .containsEntry("micro0_numero_serie", "M-123")
                .containsEntry("micro1_type", "SMX-U1")
                .containsEntry("canal_expansion_temps", "OUI");
        assertThat(p.configuration())
                .as("l'ancienne clé est retirée : la participation se répare au premier envoi")
                .doesNotContainKey("detecteur_enregistreur_numserie")
                .containsEntry("detecteur_enregistreur_numero_serie", "1997632");
    }

    @Test
    @DisplayName("#1844 : les températures partent, ARRONDIES en entiers (le schéma serveur les type integer)")
    void vers_participation_envoie_les_temperatures_arrondies() {
        String meteo =
                MeteoPassage.definirReleve(null, new MeteoReleve(8.6, 5.2, Vent.FAIBLE, CouvertureNuageuse.DE_0_A_25));

        ParticipationADeposer p =
                CorrespondanceParticipation.versParticipation("Z41", passage(meteo), MaterielMicro.vide(42L));

        assertThat(p.meteo().temperatureDebut())
                .as("8,6 °C s'arrondit à 9 : un décimal serait refusé par le serveur")
                .isEqualTo(9);
        assertThat(p.meteo().temperatureFin()).isEqualTo(5);
    }

    @Test
    @DisplayName("#1828 : un n° de série sentinelle n'est PAS publié — le type reste vrai, le mensonge ne part pas")
    void vers_participation_ne_publie_pas_une_sentinelle() {
        ParticipationADeposer squelette = CorrespondanceParticipation.versParticipation(
                "Z41", passageAvecEnregistreur(Enregistreur.INCONNU), MaterielMicro.vide(42L));
        ParticipationADeposer degrade = CorrespondanceParticipation.versParticipation(
                "Z41", passageAvecEnregistreur(Enregistreur.INCONNU_IMPORT), MaterielMicro.vide(42L));

        assertThat(squelette.configuration())
                .as("« INCONNU » est un aveu, pas un numéro : la plateforme ne doit pas le recevoir")
                .containsOnlyKeys("detecteur_enregistreur_type");
        assertThat(degrade.configuration())
                .as("même chose pour la sentinelle de l'import en mode dégradé")
                .containsOnlyKeys("detecteur_enregistreur_type");
    }

    /// Le même passage que [#passage], avec l'enregistreur qu'on veut éprouver.
    private static Passage passageAvecEnregistreur(String serie) {
        Passage modele = passage(null);
        return new Passage(
                modele.id(),
                modele.numeroPassage(),
                modele.annee(),
                modele.dateEnregistrement(),
                modele.heureDebut(),
                modele.heureFin(),
                modele.parametresAcquisition(),
                modele.statutWorkflow(),
                modele.verdictVerification(),
                modele.commentaire(),
                modele.donneesMeteo(),
                modele.deposeLe(),
                modele.idPoint(),
                serie);
    }

    @Test
    @DisplayName("fusionnerMeteo : le bloc météo distant REMPLACE le local, températures comprises")
    void fusionner_meteo_adopte_le_bloc_distant() {
        MeteoReleve local = new MeteoReleve(12.0, 8.0, Vent.NUL, CouvertureNuageuse.DE_0_A_25);

        MeteoReleve fusion =
                CorrespondanceParticipation.fusionnerMeteo(local, new MeteoDepot("FORT", "75-100", 18, 11));

        // #1844 : on lisait les températures de l'API pour les jeter aussitôt. La plateforme fait foi sur
        // TOUT le bloc météo, comme elle le faisait déjà pour le vent et la couverture.
        assertThat(fusion.temperatureDebutNuit()).isEqualTo(18.0);
        assertThat(fusion.temperatureFinNuit()).isEqualTo(11.0);
        assertThat(fusion.vent()).isEqualTo(Vent.FORT);
        assertThat(fusion.couvertureNuageuse()).isEqualTo(CouvertureNuageuse.DE_75_A_100);
    }

    @Test
    @DisplayName("fusionnerMeteo : un bloc distant SANS température écrase quand même (cohérence du bloc)")
    void fusionner_meteo_bloc_distant_sans_temperature() {
        MeteoReleve local = new MeteoReleve(12.0, 8.0, Vent.NUL, CouvertureNuageuse.DE_0_A_25);

        MeteoReleve fusion = CorrespondanceParticipation.fusionnerMeteo(local, new MeteoDepot("FORT", "75-100"));

        // Conséquence assumée du choix « le bloc distant fait foi » : une fiche saisie sur le web avant que
        // l'app ne transporte les températures n'en porte pas, et le relevé local est perdu. Traiter les
        // températures champ par champ ferait cohabiter deux règles de fusion dans le même objet.
        assertThat(fusion.temperatureDebutNuit()).isNull();
        assertThat(fusion.temperatureFinNuit()).isNull();
        assertThat(fusion.vent()).isEqualTo(Vent.FORT);
    }

    @Test
    @DisplayName("fusionnerMeteo : météo distante null → relevé local inchangé")
    void fusionner_meteo_distant_null() {
        MeteoReleve local = new MeteoReleve(12.0, null, Vent.FAIBLE, null);
        assertThat(CorrespondanceParticipation.fusionnerMeteo(local, null)).isEqualTo(local);
    }

    @Test
    @DisplayName("microDepuis : mappe micro0_* vers MaterielMicro ; valeurs absentes → null")
    void micro_depuis_config() {
        MaterielMicro micro = CorrespondanceParticipation.microDepuis(
                42L, Map.of("micro0_type", "ICS", "micro0_position", "CANOPEE", "micro0_hauteur", "4"));

        assertThat(micro.typeMicro()).isEqualTo("ICS");
        assertThat(micro.positionMicro()).isEqualTo(PositionMicro.CANOPEE);
        assertThat(micro.hauteurMetres()).isEqualTo(4.0);

        MaterielMicro vide = CorrespondanceParticipation.microDepuis(42L, Map.of());
        assertThat(vide.typeMicro()).isNull();
        assertThat(vide.positionMicro()).isNull();
        assertThat(vide.hauteurMetres()).isNull();
    }

    private static Passage passage(String donneesMeteo) {
        // Nuit du 3→4 juillet : début 21:00, fin 05:00 (franchit minuit).
        return new Passage(
                42L,
                1,
                2026,
                "2026-07-03",
                "21:00:00",
                "05:00:00",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                donneesMeteo,
                null,
                7L,
                "1997632");
    }

    private static LocalDateTime instantLocal(String rfc1123) {
        return ZonedDateTime.parse(rfc1123, DateTimeFormatter.RFC_1123_DATE_TIME)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    @Test
    @DisplayName("#1689 : serieDepuis lit la clé canonique VigieChiro, à défaut la clé de l'app, sinon null")
    void serie_depuis_les_deux_cles() {
        assertThat(CorrespondanceParticipation.serieDepuis(Map.of("detecteur_enregistreur_numero_serie", "1997632")))
                .isEqualTo("1997632");
        assertThat(CorrespondanceParticipation.serieDepuis(Map.of("detecteur_enregistreur_numserie", "1925492")))
                .isEqualTo("1925492");
        assertThat(CorrespondanceParticipation.serieDepuis(
                        Map.of("detecteur_enregistreur_numero_serie", "AAA", "detecteur_enregistreur_numserie", "BBB")))
                .as("les deux présentes : la clé canonique VigieChiro l'emporte")
                .isEqualTo("AAA");
        assertThat(CorrespondanceParticipation.serieDepuis(Map.of("micro0_type", "ICS")))
                .isNull();
        assertThat(CorrespondanceParticipation.serieDepuis(null)).isNull();
    }
}
