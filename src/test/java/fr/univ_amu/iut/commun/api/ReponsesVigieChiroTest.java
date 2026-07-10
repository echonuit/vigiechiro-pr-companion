package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Lecture des réponses JSON VigieChiro ([ReponsesVigieChiro]) : fonctions pures `String` → record,
/// **tolérantes** (JSON illisible / incomplet → vide, jamais d'exception). Aucun réseau. Le transport
/// HTTP est testé à part dans `ClientVigieChiroTest`.
class ReponsesVigieChiroTest {

    @Test
    @DisplayName("profil lit _id / pseudo / role d'un profil complet")
    void profil_complet() {
        String corps = "{\"_id\":\"698ddf3d\",\"pseudo\":\"Sébastien\",\"role\":\"Observateur\","
                + "\"donnees_publiques\":true}";

        assertThat(ReponsesVigieChiro.profil(corps))
                .contains(new ProfilVigieChiro("698ddf3d", "Sébastien", "Observateur"));
    }

    @Test
    @DisplayName("profil tolère les champs absents ou null (hors _id)")
    void profil_champs_absents() {
        Optional<ProfilVigieChiro> profil = ReponsesVigieChiro.profil("{\"_id\":\"x\",\"pseudo\":null}");

        assertThat(profil).isPresent();
        assertThat(profil.orElseThrow().id()).isEqualTo("x");
        assertThat(profil.orElseThrow().pseudo()).isNull();
        assertThat(profil.orElseThrow().role()).isNull();
    }

    @Test
    @DisplayName("profil : sans _id → vide ; JSON illisible → vide (jamais d'exception)")
    void profil_invalide_est_vide() {
        assertThat(ReponsesVigieChiro.profil("{\"pseudo\":\"x\"}")).isEmpty();
        assertThat(ReponsesVigieChiro.profil("pas du json")).isEmpty();
        assertThat(ReponsesVigieChiro.profil("[]")).isEmpty();
    }

    @Test
    @DisplayName("taxons lit _id / libelle_court / libelle_long depuis la clé _items")
    void taxons_liste() {
        String corps = "{\"_items\":["
                + "{\"_id\":\"5a1\",\"libelle_court\":\"Pippip\",\"libelle_long\":\"Pipistrellus pipistrellus\"},"
                + "{\"_id\":\"5a2\",\"libelle_court\":\"Barbar\",\"libelle_long\":\"Barbastella barbastellus\"}]}";

        assertThat(ReponsesVigieChiro.taxons(corps))
                .containsExactly(
                        new TaxonVigieChiro("5a1", "Pippip", "Pipistrellus pipistrellus"),
                        new TaxonVigieChiro("5a2", "Barbar", "Barbastella barbastellus"));
    }

    @Test
    @DisplayName("taxons : élément sans _id ou sans libelle_court ignoré, libelle_long absent → null")
    void taxons_tolerant() {
        String corps = "{\"_items\":["
                + "{\"_id\":\"5a1\",\"libelle_court\":\"Pippip\"},"
                + "{\"libelle_court\":\"SansId\"},"
                + "{\"_id\":\"5a3\"}]}";

        assertThat(ReponsesVigieChiro.taxons(corps)).containsExactly(new TaxonVigieChiro("5a1", "Pippip", null));
    }

    @Test
    @DisplayName("taxons : corps illisible ou forme inattendue → liste vide (jamais d'exception)")
    void taxons_illisible() {
        assertThat(ReponsesVigieChiro.taxons("pas du json")).isEmpty();
        assertThat(ReponsesVigieChiro.taxons("{\"autre\":1}")).isEmpty();
    }

    @Test
    @DisplayName("idCree : _id d'un document créé (POST participation) ; illisible ou sans _id → vide")
    void id_cree() {
        assertThat(ReponsesVigieChiro.idCree("{\"_id\":\"6a49\",\"_status\":\"OK\"}"))
                .contains("6a49");
        assertThat(ReponsesVigieChiro.idCree("{\"_status\":\"OK\"}")).isEmpty();
        assertThat(ReponsesVigieChiro.idCree("pas du json")).isEmpty();
    }

    @Test
    @DisplayName("fichierSigne : _id + s3_signed_url (POST /fichiers) ; l'un manque → vide")
    void fichier_signe() {
        String corps = "{\"_id\":\"f1\",\"s3_signed_url\":\"https://s3.amazonaws.com/bucket/f1?sig=abc\"}";

        assertThat(ReponsesVigieChiro.fichierSigne(corps))
                .contains(new FichierSigne("f1", "https://s3.amazonaws.com/bucket/f1?sig=abc"));
        assertThat(ReponsesVigieChiro.fichierSigne("{\"_id\":\"f1\"}")).isEmpty(); // pas d'URL
        assertThat(ReponsesVigieChiro.fichierSigne("nope")).isEmpty();
    }
}
