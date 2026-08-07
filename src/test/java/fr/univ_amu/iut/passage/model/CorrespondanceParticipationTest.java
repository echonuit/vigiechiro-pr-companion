package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationADeposer;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        // Les dates se comparent EN DUR, telles qu'Eve les recevra (#3406).
        //
        // ⚠️ Elles étaient vérifiées par un aller-retour vers le fuseau local, sous le motif
        // « déterministe quel que soit le fuseau ». Il l'était en effet - en reconvertissant avec le
        // MÊME `systemDefault()` qui avait produit la valeur, si bien que l'aller-retour s'annulait.
        // Ce cas restait vert alors que la donnée déposée était fausse : depuis un poste en UTC, cette
        // nuit partait en `21:00 GMT` au lieu de `19:00 GMT`.
        assertThat(p.dateDebut()).isEqualTo("Fri, 3 Jul 2026 19:00:00 GMT");
        assertThat(p.dateFin()).isEqualTo("Sat, 4 Jul 2026 03:00:00 GMT"); // franchit minuit
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
    @DisplayName("#1844 : la configuration distante est PRÉSERVÉE, un envoi n'efface plus ce qu'on ne modélise pas")
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
    @DisplayName("#1828 : un n° de série sentinelle n'est PAS publié, le type reste vrai, le mensonge ne part pas")
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
                serie,
                null);
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

    @Test
    @DisplayName("#3406 : ce qui part vers la plateforme ne dépend PAS du fuseau du poste qui dépouille")
    void le_depot_ne_depend_pas_du_poste() {
        // Les heures d'un passage viennent de l'enregistreur posé sur le SITE. Les convertir avec le
        // fuseau de la machine faisait partir un instant différent selon le poste - et depuis Cayenne,
        // un changement de DATE. C'est une donnée déposée sur la plateforme nationale.
        TimeZone origine = TimeZone.getDefault();
        try {
            for (String posteDeDepouillement : new String[] {"Europe/Paris", "UTC", "America/Cayenne"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(posteDeDepouillement));

                ParticipationADeposer p =
                        CorrespondanceParticipation.versParticipation("Z41", passage(null), MaterielMicro.vide(42L));

                assertThat(p.dateDebut())
                        .as("nuit du 3 juillet, 21:00 sur le site, dépouillée depuis « %s »", posteDeDepouillement)
                        .isEqualTo("Fri, 3 Jul 2026 19:00:00 GMT");
                assertThat(p.dateFin())
                        .as("fin de nuit, dépouillée depuis « %s »", posteDeDepouillement)
                        .isEqualTo("Sat, 4 Jul 2026 03:00:00 GMT");
            }
        } finally {
            TimeZone.setDefault(origine);
        }
    }

    @ParameterizedTest(name = "sans {0}")
    @MethodSource("nuitsSansBorne")
    @DisplayName("#3451 : une nuit dont une borne manque est REFUSÉE, jamais déposée amputée")
    void une_borne_manquante_refuse_le_depot(String borneAbsente, Passage ampute) {
        // Ce que ce garde remplace : un `return null` par borne, qui retirait simplement le champ du corps
        // envoyé. La nuit partait sur la plateforme NATIONALE sans ses horaires, et rien ne le disait.
        //
        // Le cas n'est pas atteignable par la base - `recording_date`, `start_time` et `end_time` y sont
        // `NOT NULL` (V01__schema.sql), et les deux chemins de dépôt chargent par le DAO. L'invariant n'est
        // donc tenu QUE par SQLite : ce test le tient dans le code, pour le jour où un passage sera bâti
        // ailleurs qu'en base (depuis la plateforme, en mémoire).
        assertThatThrownBy(() -> CorrespondanceParticipation.versParticipation("Z41", ampute, MaterielMicro.vide(42L)))
                .as("une nuit sans %s doit être refusée au dépôt", borneAbsente)
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("bornes complètes");
    }

    private static Stream<Arguments> nuitsSansBorne() {
        return Stream.of(
                Arguments.of("date d'enregistrement", avecBornes(null, "21:00:00", "05:00:00")),
                Arguments.of("heure de début", avecBornes("2026-07-03", null, "05:00:00")),
                Arguments.of("heure de fin", avecBornes("2026-07-03", "21:00:00", null)));
    }

    /// La nuit de référence, dont une borne est remplacée - `null` pour la faire manquer.
    private static Passage avecBornes(String date, String heureDebut, String heureFin) {
        Passage complet = passage(null);
        return new Passage(
                complet.id(),
                complet.numeroPassage(),
                complet.annee(),
                date,
                heureDebut,
                heureFin,
                complet.parametresAcquisition(),
                complet.statutWorkflow(),
                complet.verdictVerification(),
                complet.commentaire(),
                complet.donneesMeteo(),
                complet.deposeLe(),
                complet.idPoint(),
                complet.idEnregistreur(),
                complet.idCampagne());
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
                "1997632",
                null);
    }

    @Test
    @DisplayName("#1860/#1862 : la boucle écriture → plateforme → lecture est un POINT FIXE, sur les deux"
            + " moitiés de production (le cliquet ne peut pas revenir par un seul des deux côtés)")
    void boucle_ecriture_lecture_est_un_point_fixe() {
        // `ParticipationOrphelineTest.aller_retour_stable` couvre déjà le retour, mais il REFAIT l'aller de
        // son côté : une régression de rfc1123Utc lui échapperait. Ici les deux moitiés sont les vraies,
        // seule la re-sérialisation d'Eve (même instant, ISO +00:00) est simulée - et ce format-là est
        // pinné par le contrat live (`date_debut` endsWith "+00:00").
        //
        // Vérifié par mutation : en remettant le `toLocalDateTime()` de #1860 dans ParticipationOrpheline,
        // ce test tombe à 13:00 au lieu de 21:00 (quatre cycles, deux heures perdues à chaque tour). Un
        // test de non-régression qui n'a jamais été vu rouge ne prouve rien.
        Passage nuit = passage(null);
        LocalDateTime attenduDebut = LocalDateTime.of(2026, 7, 3, 21, 0);
        LocalDateTime attenduFin = LocalDateTime.of(2026, 7, 4, 5, 0);

        LocalDateTime debut = attenduDebut;
        LocalDateTime fin = attenduFin;
        for (int cycle = 0; cycle < 4; cycle++) {
            ParticipationADeposer envoi =
                    CorrespondanceParticipation.versParticipation("Z41", nuit, MaterielMicro.vide(42L));
            debut = ParticipationOrpheline.horodatage(commeEve(envoi.dateDebut()))
                    .orElseThrow();
            fin = ParticipationOrpheline.horodatage(commeEve(envoi.dateFin())).orElseThrow();
            nuit = avecHeures(nuit, debut, fin);
        }

        assertThat(debut)
                .as("quatre cycles « reconstruire puis envoyer » ne doivent pas plus déplacer la nuit qu'un"
                        + " seul : c'est exactement ce qui a fait descendre 21:00 à 15:00 (#1860)")
                .isEqualTo(attenduDebut);
        assertThat(fin).isEqualTo(attenduFin);
    }

    /// Ce que la plateforme rend d'un horodatage qu'on lui a envoyé en RFC 1123 : le **même instant**,
    /// re-sérialisé en ISO 8601 UTC. La seule transformation simulée de cette boucle.
    private static String commeEve(String rfc1123) {
        return ZonedDateTime.parse(rfc1123, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toOffsetDateTime()
                .toString();
    }

    /// Le passage, ses bornes remplacées par celles qu'on vient de relire : c'est ce que fait une nuit
    /// reconstruite avant d'être renvoyée.
    private static Passage avecHeures(Passage passage, LocalDateTime debut, LocalDateTime fin) {
        return new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                debut.toLocalDate().toString(),
                debut.toLocalTime().toString(),
                fin.toLocalTime().toString(),
                passage.parametresAcquisition(),
                passage.statutWorkflow(),
                passage.verdictVerification(),
                passage.commentaire(),
                passage.donneesMeteo(),
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur(),
                null);
    }

    @Test
    @DisplayName("#1689 : serieDepuis lit la clé canonique Vigie-Chiro, à défaut la clé de l'app, sinon null")
    void serie_depuis_les_deux_cles() {
        assertThat(CorrespondanceParticipation.serieDepuis(Map.of("detecteur_enregistreur_numero_serie", "1997632")))
                .isEqualTo("1997632");
        assertThat(CorrespondanceParticipation.serieDepuis(Map.of("detecteur_enregistreur_numserie", "1925492")))
                .isEqualTo("1925492");
        assertThat(CorrespondanceParticipation.serieDepuis(
                        Map.of("detecteur_enregistreur_numero_serie", "AAA", "detecteur_enregistreur_numserie", "BBB")))
                .as("les deux présentes : la clé canonique Vigie-Chiro l'emporte")
                .isEqualTo("AAA");
        assertThat(CorrespondanceParticipation.serieDepuis(Map.of("micro0_type", "ICS")))
                .isNull();
        assertThat(CorrespondanceParticipation.serieDepuis(null)).isNull();
    }
}
