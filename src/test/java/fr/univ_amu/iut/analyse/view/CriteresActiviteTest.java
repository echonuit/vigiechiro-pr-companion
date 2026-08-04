package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.analyse.model.ContactHoraire;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/// Vérifie la **recherche texte** des critères de la vue Activité ([CriteresActivite]) : elle balaie
/// taxon, nom, commune, carré et point, insensible à la casse et aux accents, et tolère les champs nuls.
/// Les critères à éditeur (Lieu, Taxon parent) sont exercés par le test d'intégration de la vue.
class CriteresActiviteTest {

    private static final BiPredicate<ContactHoraire, String> RECHERCHE = CriteresActivite.rechercheTexte();

    private static ContactHoraire contact(String taxon, String nom, String commune, String carre, String point) {
        return new ContactHoraire(taxon, nom, "Chiroptères", null, commune, carre, point, 1L, null);
    }

    @Test
    void trouve_par_taxon_nom_commune_carre_ou_point_insensible_casse_et_accents() {
        ContactHoraire kuhl = contact("PIPKUH", "Pipistrelle de Kuhl", "Ahetze", "640380", "A1");
        assertThat(RECHERCHE.test(kuhl, "pipkuh")).as("taxon, casse").isTrue();
        assertThat(RECHERCHE.test(kuhl, "KUHL")).as("nom, casse").isTrue();
        assertThat(RECHERCHE.test(kuhl, "ahetze")).as("commune (#2967)").isTrue();
        assertThat(RECHERCHE.test(kuhl, "640380")).as("carré").isTrue();
        assertThat(RECHERCHE.test(kuhl, "a1")).as("point").isTrue();

        // Le nom du site est la seconde étiquette du carré (ADR 3157) : la puce le laissait cocher, la
        // recherche ne le laissait pas taper. La projection le porte depuis #3175.
        ContactHoraire nomme = new ContactHoraire(
                "PIPKUH", "Pipistrelle de Kuhl", "Chiroptères", null, "Ahetze", "640380", "A1", 1L, "Bois du bourg");
        assertThat(RECHERCHE.test(nomme, "bourg"))
                .as("un carré se cherche par son nom comme par son numéro")
                .isTrue();

        ContactHoraire serotine = contact("EPTSER", "Sérotine commune", "Bénesse", "770123", "B2");
        assertThat(RECHERCHE.test(serotine, "serotine"))
                .as("accents ignorés, taxon")
                .isTrue();
        assertThat(RECHERCHE.test(serotine, "benesse"))
                .as("accents ignorés, commune")
                .isTrue();
    }

    @Test
    void ne_trouve_pas_l_absent() {
        ContactHoraire kuhl = contact("PIPKUH", "Pipistrelle de Kuhl", "Ahetze", "640380", "A1");
        assertThat(RECHERCHE.test(kuhl, "barbastelle")).isFalse();
    }

    @Test
    void tolere_les_champs_nuls() {
        assertThat(RECHERCHE.test(contact(null, null, null, null, null), "xxx")).isFalse();
    }
}
