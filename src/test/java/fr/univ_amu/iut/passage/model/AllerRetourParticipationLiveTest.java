package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationADeposer;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ResultatEcriture;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// **Sonde d'aller-retour** sur les écritures de participation (#1862) : écrire, **relire**, et comparer
/// champ à champ ce qui est ressorti.
///
/// ## Pourquoi une sonde de plus
///
/// Sur les sept défauts du train de suites de l'EPIC #1662, six ont été trouvés par l'usage réel, aucun
/// par la suite de tests - qui était verte à chaque fois. Les quatre qui touchaient l'écriture vers la
/// plateforme (#1828, #1839, #1844, #1845) partagent une propriété qu'aucun test bouchonné ne peut voir :
/// **ils réussissent tous**. Publier une sentinelle « INCONNU » rend `200 OK`. Écrire le n° de série sous
/// une clé que le formulaire web ne lit pas rend `200 OK`. Effacer par `PATCH` les champs distants que
/// l'application ne modélise pas rend `200 OK`.
///
/// Un test qui bouchonne l'API vérifie **ce que nous croyons envoyer**, jamais ce que la plateforme en
/// **fait**. Les mocks ont donc confirmé nos hypothèses fausses avec la même conviction que les justes.
/// Seule une relecture du corps réel départage les deux.
///
/// ## Ce que ces probes fixent
///
/// Elles vérifient en réel les trois règles de l'[ADR 0020] (ne rien inventer, ne rien effacer, parler la
/// langue du lecteur), plus la fidélité des dates que #1860 avait prise en défaut. Chacune est un **fait
/// de plateforme** sur lequel repose une décision de conception : si l'une tombe, c'est la décision
/// qu'il faut rouvrir, pas le test qu'il faut ajuster.
///
/// ## Verrous
///
/// Ces probes **écrivent**. Elles exigent donc, comme celles de `ContratApiVigieChiroLiveTest` :
/// `-Dvigiechiro.token=…`, `-Dvigiechiro.write=true`, et une participation **de rebut** explicitement
/// désignée par `-Dvigiechiro.participationEssai=<id>`. Sans les trois, tout se skippe.
///
/// ```
/// ./mvnw -Papi-live test -Dvigiechiro.token=XXXX -Dvigiechiro.write=true \
///        -Dvigiechiro.participationEssai=YYYY -Dtest=AllerRetourParticipationLiveTest
/// ```
///
/// Le contrat live hebdomadaire (`api-live.yml`) est en **lecture seule** et ne passe aucun de ces
/// drapeaux : ces probes n'y tournent jamais.
///
/// L'état de la participation d'essai (configuration **et** bornes de nuit) est **relu au début et
/// restauré à la fin**, et la restauration est **vérifiée** : une sonde qui vérifie « ne rien effacer »
/// n'a pas d'excuse pour laisser derrière elle un dictionnaire tronqué - ni pour croire sa propre remise
/// en état sur parole.
///
/// ## Ce qu'elle ne voit pas
///
/// Le **rendu du formulaire web** lui-même : qu'une clé soit stockée ne prouve pas que le front l'affiche.
/// C'est ce qui reste en recette (session S4, cases 43-53).
///
/// Vit dans `passage.model` et non aux côtés du contrat API : ces probes traversent
/// [CorrespondanceParticipation], qui est de ce paquet. Une sonde d'aller-retour qui reconstruirait le
/// corps à la main ne garderait plus le mapping - or c'est précisément là que #1844 et #1860 se
/// jouaient.
@Tag("api-live")
@DisplayName("Aller-retour d'écriture sur une participation (live) — ce que la plateforme en fait")
class AllerRetourParticipationLiveTest {

    /// Clé témoin, propre à la sonde : elle n'appartient ni à l'application ni au formulaire web. Sa
    /// disparition après un `PATCH` partiel est **la preuve** que le dictionnaire est remplacé.
    private static final String CLE_TEMOIN = "sonde_aller_retour_temoin";

    private static final String CLE_SERIE_CANONIQUE = "detecteur_enregistreur_numero_serie";
    private static final String CLE_SERIE_HISTORIQUE = "detecteur_enregistreur_numserie";

    private static ClientVigieChiro client;
    private static String participation;

    /// État trouvé en arrivant : remis en place à la fin.
    ///
    /// La **première exécution** (2026-07-18) ne restaurait que la configuration, et a donc laissé la nuit
    /// de rebut avec les bornes de la probe. Une sonde qui prêche « ne rien effacer » ne peut pas se
    /// contenter de restaurer ce qu'elle a pensé à noter : les **dates** en font partie.
    private static Map<String, String> configurationInitiale;

    private static String dateDebutInitiale;
    private static String dateFinInitiale;

    @BeforeAll
    static void configurer() {
        String token = System.getProperty("vigiechiro.token");
        assumeTrue(
                token != null && !token.isBlank(),
                "Sonde d'aller-retour ignorée : fournir -Dvigiechiro.token=… (profil -Papi-live).");
        assumeTrue(
                Boolean.getBoolean("vigiechiro.write"),
                "Sonde d'aller-retour ignorée : opt-in -Dvigiechiro.write=true (elle écrit sur la plateforme).");
        participation = System.getProperty("vigiechiro.participationEssai");
        assumeTrue(
                participation != null && !participation.isBlank(),
                "Sonde d'aller-retour ignorée : fournir -Dvigiechiro.participationEssai=<participation de rebut>."
                        + " JAMAIS une participation réelle : ces probes réécrivent sa configuration.");
        String baseUrl = System.getProperty("vigiechiro.baseUrl", "https://vigiechiro.herokuapp.com/api/v1");
        client = new ClientVigieChiro(baseUrl, () -> Optional.of(token));
        ParticipationDetail avant = relire();
        configurationInitiale = avant.configuration();
        dateDebutInitiale = avant.dateDebut();
        dateFinInitiale = avant.dateFin();
    }

    @AfterAll
    static void restaurer() {
        if (client == null || configurationInitiale == null) {
            return;
        }
        // Les bornes relues sont en ISO ; Eve REFUSE l'ISO en entrée (cf. CorrespondanceParticipation).
        // Les reposer telles quelles ferait échouer la restauration en silence - au moment précis où
        // cette sonde prétend qu'un succès apparent ne suffit pas.
        ResultatEcriture remise = envoyer(new ParticipationADeposer(
                null, enRfc1123(dateDebutInitiale), enRfc1123(dateFinInitiale), null, configurationInitiale, null));
        assertThat(remise.estReussie())
                .as("la nuit de rebut doit être rendue dans l'état où on l'a trouvée : %s", remise.echec())
                .isTrue();
    }

    /// Une borne ISO relue, reformatée dans le seul format que la plateforme accepte en **entrée**.
    private static String enRfc1123(String iso) {
        return iso == null ? null : OffsetDateTime.parse(iso).format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    @Test
    @DisplayName("PROBE #1844 : un PATCH REMPLACE la configuration entière - ce que l'on n'envoie pas est"
            + " effacé (le fait qui oblige à partir de la configuration distante)")
    void patch_remplace_la_configuration() {
        Map<String, String> deuxCles = new LinkedHashMap<>();
        deuxCles.put(CLE_TEMOIN, "present");
        deuxCles.put(CLE_SERIE_CANONIQUE, "9999999");
        assertThat(ecrire(deuxCles).estReussie())
                .as("première écriture acceptée")
                .isTrue();
        assertThat(relire().configuration())
                .as("les deux clés sont bien posées avant la seconde écriture")
                .containsEntry(CLE_TEMOIN, "present")
                .containsEntry(CLE_SERIE_CANONIQUE, "9999999");

        // Seconde écriture SANS la clé témoin : si Eve fusionnait, elle survivrait.
        assertThat(ecrire(Map.of(CLE_SERIE_CANONIQUE, "9999999")).estReussie()).isTrue();

        assertThat(relire().configuration())
                .as("VERDICT : le PATCH remplace le dictionnaire. C'est pour cela que"
                        + " CorrespondanceParticipation PART de la configuration distante (#1844) - sans quoi"
                        + " chaque envoi effacerait micro1_*, canal_* et les champs saisis sur le web.")
                .doesNotContainKey(CLE_TEMOIN)
                .containsEntry(CLE_SERIE_CANONIQUE, "9999999");
    }

    @Test
    @DisplayName("PROBE #1844 : le n° de série écrit sous la clé canonique ressort VERBATIM sous cette clé"
            + " (la plateforme ne renomme ni ne normalise)")
    void la_cle_canonique_survit_a_l_aller_retour() {
        // On pose AUSSI la clé historique : c'est l'état d'une participation déposée par l'app avant #1844.
        Map<String, String> avant = new LinkedHashMap<>();
        avant.put(CLE_SERIE_HISTORIQUE, "1111111");
        ecrire(avant);

        // Ce que l'application enverrait aujourd'hui pour cette nuit, mapping compris.
        Passage nuit = nuitDEssai("1925492", "21:00", "06:00");
        ParticipationADeposer envoi = CorrespondanceParticipation.versParticipation(
                "Z41", nuit, MaterielMicro.vide(1L), relire().configuration());
        assertThat(envoyer(envoi).estReussie()).isTrue();

        Map<String, String> relue = relire().configuration();
        assertThat(relue)
                .as("la clé que le formulaire web lit porte le numéro")
                .containsEntry(CLE_SERIE_CANONIQUE, "1925492");
        assertThat(relue)
                .as("et l'ancienne clé a disparu : une participation déposée avant #1844 se répare au"
                        + " premier envoi, elle ne garde pas deux numéros concurrents")
                .doesNotContainKey(CLE_SERIE_HISTORIQUE);
    }

    @Test
    @DisplayName("PROBE #1828 : un enregistreur INCONNU ne franchit pas la frontière - aucune clé de n° de"
            + " série n'apparaît (ne rien inventer)")
    void la_sentinelle_ne_franchit_pas_la_frontiere() {
        ecrire(Map.of(CLE_TEMOIN, "present")); // départ propre : aucun n° de série posé

        Passage nuit = nuitDEssai(Enregistreur.INCONNU, "21:00", "06:00");
        ParticipationADeposer envoi = CorrespondanceParticipation.versParticipation(
                "Z41", nuit, MaterielMicro.vide(1L), relire().configuration());
        assertThat(envoyer(envoi).estReussie()).isTrue();

        assertThat(relire().configuration())
                .as("VERDICT : la sentinelle reste locale. La publier fabriquerait une donnée que le"
                        + " prochain poste relirait comme réelle (#1828) - « INCONNU » n'est pas un numéro,"
                        + " c'est un aveu d'ignorance")
                .doesNotContainKey(CLE_SERIE_CANONIQUE)
                .doesNotContainKey(CLE_SERIE_HISTORIQUE);
    }

    @Test
    @DisplayName("PROBE #1860 : deux allers-retours de suite rendent les MÊMES heures - la nuit ne dérive"
            + " pas d'un cycle à l'autre (le mécanisme à cliquet)")
    void les_heures_ne_derivent_pas_d_un_cycle_a_l_autre() {
        Passage nuit = nuitDEssai("1925492", "21:00", "06:00");

        // Premier cycle : ce que l'app enverrait, puis ce qu'elle relirait.
        HeuresRelues premier = cycle(nuit);
        assertThat(premier.debut())
                .as("l'heure de début revient telle qu'envoyée")
                .isEqualTo("21:00");
        assertThat(premier.fin()).as("l'heure de fin revient telle qu'envoyée").isEqualTo("06:00");

        // Second cycle, en repartant des heures RELUES : c'est ainsi que #1860 composait. Chaque
        // « reconstruire puis envoyer » retranchait le décalage horaire, jusqu'à 21:00 -> 19:00 -> 17:00.
        HeuresRelues second = cycle(nuitDEssai("1925492", premier.debut(), premier.fin()));
        assertThat(second.debut())
                .as("VERDICT : l'aller-retour est STABLE. S'il ne l'est plus, la nuit se décale un peu"
                        + " plus à chaque envoi et personne ne le voit avant que la fenêtre n'ait fondu")
                .isEqualTo(premier.debut());
        assertThat(second.fin()).isEqualTo(premier.fin());
    }

    /// Un aller-retour complet : mapping local -> `PATCH` -> relecture -> heures locales.
    private static HeuresRelues cycle(Passage nuit) {
        ParticipationADeposer envoi = CorrespondanceParticipation.versParticipation(
                "Z41", nuit, MaterielMicro.vide(1L), relire().configuration());
        assertThat(envoyer(envoi).estReussie())
                .as("écriture des dates acceptée")
                .isTrue();
        ParticipationDetail relu = relire();
        return new HeuresRelues(heureLocale(relu.dateDebut()), heureLocale(relu.dateFin()));
    }

    /// Heure **locale** portée par un horodatage distant, au format `HH:mm`.
    ///
    /// Délègue à [ParticipationOrpheline#horodatage], le **lecteur de production** - et non à une
    /// conversion réécrite ici. C'est tout l'intérêt : la boucle sondée est celle qui a fauté,
    /// [CorrespondanceParticipation] (écriture) → plateforme → [ParticipationOrpheline] (lecture). Une
    /// sonde qui referait la conversion de son côté vérifierait sa propre arithmétique et laisserait
    /// repasser #1860 sans broncher.
    private static String heureLocale(String horodatageDistant) {
        LocalDateTime local = ParticipationOrpheline.horodatage(horodatageDistant)
                .orElseThrow(() -> new AssertionError(
                        "Borne de nuit illisible au retour de la plateforme : " + horodatageDistant));
        return String.format("%02d:%02d", local.getHour(), local.getMinute());
    }

    /// Passage d'essai : seules comptent ici la nuit, ses bornes et son enregistreur.
    private static Passage nuitDEssai(String enregistreur, String heureDebut, String heureFin) {
        return new Passage(
                1L, 1, 2026, "2026-07-04", heureDebut, heureFin, null, null, null, null, null, null, 1L, enregistreur);
    }

    /// Écrit une configuration seule, sans toucher aux autres champs de la participation.
    private static ResultatEcriture ecrire(Map<String, String> configuration) {
        return envoyer(new ParticipationADeposer(null, null, null, null, configuration, null));
    }

    /// Envoie une mise à jour, `_etag` relu juste avant (comme le fait `SynchronisationParticipation`).
    private static ResultatEcriture envoyer(ParticipationADeposer miseAJour) {
        return client.modifierParticipation(participation, relire().etag(), miseAJour);
    }

    private static ParticipationDetail relire() {
        return client.participation(participation)
                .enOptionnel()
                .orElseThrow(() -> new IllegalStateException(
                        "Participation d'essai illisible : " + participation + " (supprimée, ou jeton sans accès)."));
    }

    /// Les deux bornes d'une nuit, relues depuis la plateforme et ramenées à l'heure de l'observateur.
    private record HeuresRelues(String debut, String fin) {}
}
