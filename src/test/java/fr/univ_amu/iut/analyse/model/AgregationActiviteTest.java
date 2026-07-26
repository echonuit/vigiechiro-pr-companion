package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/// Vérifie l'agrégation **pure** de l'activité d'une nuit ([AgregationActivite]) : regroupement par
/// espèce, découpage en tranches alignées sur l'horloge, ordre chronologique à cheval sur minuit, contacts
/// écartés et tri de sortie.
class AgregationActiviteTest {

    private static ContactHoraire contact(String taxon, String nom, LocalDateTime heure) {
        return new ContactHoraire(taxon, nom, "Chiroptères", heure);
    }

    private static LocalDateTime le21juin(int heure, int minute) {
        return LocalDateTime.of(2026, 6, 21, heure, minute);
    }

    @Test
    void regroupe_par_espece_et_trie_par_total_decroissant() {
        List<ContactHoraire> contacts = List.of(
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 0)),
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 10)),
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(23, 0)),
                contact("BARBAR", "Barbastelle d'Europe", le21juin(22, 5)),
                contact("BARBAR", "Barbastelle d'Europe", le21juin(22, 40)));

        List<CourbeEspece> courbes = AgregationActivite.parEspece(contacts, LargeurTranche.HEURE);

        assertThat(courbes)
                .extracting(CourbeEspece::taxon, CourbeEspece::total)
                .as("l'espèce la plus contactée vient en premier")
                .containsExactly(tuple("PIPKUH", 3), tuple("BARBAR", 2));
    }

    @Test
    void compte_les_contacts_par_tranche_alignee_sur_l_horloge() {
        List<ContactHoraire> contacts = List.of(
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 7)),
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 29)),
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 31)));

        List<CourbeEspece> courbes = AgregationActivite.parEspece(contacts, LargeurTranche.DEMI_HEURE);

        assertThat(courbes).hasSize(1);
        assertThat(courbes.get(0).points())
                .as("22:07 et 22:29 dans la tranche 22:00, 22:31 dans la tranche 22:30")
                .extracting(PointActivite::debutTranche, PointActivite::nombre)
                .containsExactly(tuple(le21juin(22, 0), 2), tuple(le21juin(22, 30), 1));
    }

    @Test
    void quart_d_heure_distingue_deux_contacts_de_la_meme_demi_heure() {
        List<ContactHoraire> contacts = List.of(
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 7)),
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 20)));

        List<CourbeEspece> courbes = AgregationActivite.parEspece(contacts, LargeurTranche.QUART_HEURE);

        assertThat(courbes.get(0).points())
                .extracting(PointActivite::debutTranche)
                .as("22:07 tombe dans 22:00, 22:20 dans 22:15")
                .containsExactly(le21juin(22, 0), le21juin(22, 15));
    }

    @Test
    void l_ordre_chronologique_est_preserve_a_cheval_sur_minuit() {
        List<ContactHoraire> contacts = List.of(
                contact("PIPKUH", "Pipistrelle de Kuhl", LocalDateTime.of(2026, 6, 22, 0, 10)),
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(23, 50)));

        List<CourbeEspece> courbes = AgregationActivite.parEspece(contacts, LargeurTranche.DEMI_HEURE);

        assertThat(courbes.get(0).points())
                .extracting(PointActivite::debutTranche)
                .as("23:30 du 21 juin passe avant 00:00 du 22 juin, malgré l'heure du jour plus petite")
                .containsExactly(le21juin(23, 30), LocalDateTime.of(2026, 6, 22, 0, 0));
    }

    @Test
    void ecarte_les_contacts_sans_heure() {
        List<ContactHoraire> contacts = List.of(
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 0)),
                contact("PIPKUH", "Pipistrelle de Kuhl", null));

        List<CourbeEspece> courbes = AgregationActivite.parEspece(contacts, LargeurTranche.HEURE);

        assertThat(courbes.get(0).total())
                .as("une séquence non horodatée n'a pas de place sur l'axe")
                .isEqualTo(1);
    }

    @Test
    void ecarte_les_contacts_sans_taxon() {
        List<ContactHoraire> contacts = List.of(
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 0)), contact(null, null, le21juin(22, 30)));

        List<CourbeEspece> courbes = AgregationActivite.parEspece(contacts, LargeurTranche.HEURE);

        assertThat(courbes)
                .as("une séquence non identifiée n'est pas une espèce")
                .singleElement()
                .extracting(CourbeEspece::taxon)
                .isEqualTo("PIPKUH");
    }

    @Test
    void a_total_egal_le_tri_secondaire_est_le_nom_vernaculaire() {
        List<ContactHoraire> contacts = List.of(
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 0)),
                contact("BARBAR", "Barbastelle d'Europe", le21juin(22, 0)));

        List<CourbeEspece> courbes = AgregationActivite.parEspece(contacts, LargeurTranche.HEURE);

        assertThat(courbes)
                .extracting(CourbeEspece::nomEspece)
                .as("à un contact chacune, l'ordre alphabétique du nom départage")
                .containsExactly("Barbastelle d'Europe", "Pipistrelle de Kuhl");
    }

    @Test
    void un_nom_vernaculaire_nul_passe_avant_a_total_egal() {
        List<ContactHoraire> contacts = List.of(
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 0)), contact("XXXXXX", null, le21juin(22, 0)));

        List<CourbeEspece> courbes = AgregationActivite.parEspece(contacts, LargeurTranche.HEURE);

        assertThat(courbes)
                .extracting(CourbeEspece::taxon)
                .as("un nom nul (souche hors référentiel) se range en premier, comme sous SQLite")
                .containsExactly("XXXXXX", "PIPKUH");
    }

    @Test
    void le_total_egale_la_somme_des_points() {
        List<ContactHoraire> contacts = List.of(
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(21, 0)),
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 0)),
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(22, 30)),
                contact("PIPKUH", "Pipistrelle de Kuhl", le21juin(23, 0)));

        CourbeEspece courbe =
                AgregationActivite.parEspece(contacts, LargeurTranche.HEURE).get(0);

        assertThat(courbe.points()).extracting(PointActivite::nombre).containsExactly(1, 2, 1);
        assertThat(courbe.total())
                .as("le total est la somme des tranches")
                .isEqualTo(
                        courbe.points().stream().mapToInt(PointActivite::nombre).sum());
    }

    @Test
    void aucun_contact_donne_aucune_courbe() {
        assertThat(AgregationActivite.parEspece(List.of(), LargeurTranche.DEMI_HEURE))
                .isEmpty();
    }
}
