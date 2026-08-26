package fr.univ_amu.iut.recette.film;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LibelleDesCasTest {

    @Test
    @DisplayName("une puce tient sur une ligne")
    void une_puce_simple() {
        LibelleDesCas recueil = LibelleDesCas.de(List.of("- **S1-26** · La modale s'ouvre sans saut visuel"));
        assertEquals(Optional.of("La modale s'ouvre sans saut visuel"), recueil.de("S1-26"));
    }

    @Test
    @DisplayName("une puce repliée se recolle ENTIÈREMENT, dernière ligne comprise")
    void une_puce_pliee_se_recolle() {
        // Le défaut de référence : le carton affichait « ... rien ne se », qui s'arrête juste
        // avant le verbe et annonce le contraire du cas.
        LibelleDesCas recueil = LibelleDesCas.de(
                List.of("- **S1-26** · La modale s'ouvre sans saut visuel, et", "  rien ne se réordonne après coup"));
        assertEquals(
                Optional.of("La modale s'ouvre sans saut visuel, et rien ne se réordonne après coup"),
                recueil.de("S1-26"));
    }

    @Test
    @DisplayName("une puce à case à cocher est reconnue comme les autres")
    void une_puce_a_case_a_cocher() {
        // Le défaut de référence : 47 cas sur 392 sortaient sans libellé, tous venus des deux
        // seules sessions qui cochent leurs puces. Un carton sans libellé ne rougit nulle part,
        // il se constate à l'œil sur le film.
        LibelleDesCas recueil = LibelleDesCas.de(List.of(
                "- [ ] **S10-01** · Lancer une seconde instance sur le même dossier",
                "- [x] **S7-12** · Le réglage survit au redémarrage"));
        assertEquals(Optional.of("Lancer une seconde instance sur le même dossier"), recueil.de("S10-01"));
        assertEquals(Optional.of("Le réglage survit au redémarrage"), recueil.de("S7-12"));
    }

    @Test
    @DisplayName("la qualification de session ne fait pas partie du libellé")
    void la_qualification_est_retiree() {
        LibelleDesCas recueil =
                LibelleDesCas.de(List.of("- **S2-04** · *perceptif* · Le tableau garde sa position de défilement"));
        assertEquals(Optional.of("Le tableau garde sa position de défilement"), recueil.de("S2-04"));
    }

    @Test
    @DisplayName("#4465 : un cas qui porte DEUX marqueurs n'en garde aucun dans son libellé")
    void les_marqueurs_multiples_sont_tous_retires() {
        // La ligne est celle de S10-01, telle qu'elle est écrite dans sa session. Le carton d'un clip
        // affichait « *carton: une seconde instance…* · Lancer l'application… », c'est-à-dire du
        // balisage à la place de la phrase, et sur un cas dont le marqueur `carton` sert précisément à
        // remplacer une étape muette.
        LibelleDesCas recueil = LibelleDesCas.de(List.of("- [ ] **S10-01** · *geste: refus-du-dossier-deja-tenu*"
                + " · *carton: une seconde instance démarre sur le même dossier de travail*"
                + " · Lancer l'application, la laisser ouverte"));

        assertEquals(Optional.of("Lancer l'application, la laisser ouverte"), recueil.de("S10-01"));
    }

    @Test
    @DisplayName("#4465 : une puce qui n'est pas un cas déclaré ne reçoit pas de libellé")
    void une_puce_qui_n_est_pas_un_cas_est_ignoree() {
        // Douze puces des fichiers de session recevaient un libellé sans être des cas : six lignes du
        // tableau de capacités, que `MUETTES_ADMISES` déclare hors session, et du texte gras en début
        // de ligne. Un libellé fabriqué pour ce que personne ne citera est du bruit, pas un service.
        LibelleDesCas recueil = LibelleDesCas.de(List.of(
                "- **Données** · enregistreur, carte SD réelle",
                "- **PC2-01** · une ligne du tableau des capacités",
                "- **S1-26** · La modale s'ouvre sans saut visuel"));

        assertTrue(recueil.de("Données").isEmpty(), "« Données » n'est pas un identifiant de cas");
        assertTrue(recueil.de("PC2-01").isEmpty(), "PC2-01 vit dans une session déclarée muette");
        assertEquals(Optional.of("La modale s'ouvre sans saut visuel"), recueil.de("S1-26"));
    }

    @Test
    @DisplayName("gras, liens et numéro d'issue sont dépouillés")
    void les_ornements_sont_retires() {
        LibelleDesCas recueil = LibelleDesCas.de(
                List.of("- **S3-01** · Le **quota** est lu depuis [la fiche](https://exemple.org/x) #4053"));
        assertEquals(Optional.of("Le quota est lu depuis la fiche"), recueil.de("S3-01"));
    }

    @Test
    @DisplayName("une puce imbriquée n'est pas avalée par celle qui la précède")
    void une_puce_imbriquee_nest_pas_avalee() {
        LibelleDesCas recueil = LibelleDesCas.de(
                List.of("- **S4-01** · Le premier cas", "  - une précision qui n'appartient pas au libellé"));
        assertEquals(Optional.of("Le premier cas"), recueil.de("S4-01"));
    }

    @Test
    @DisplayName("un cas non rédigé rend vide, et NON un faux libellé")
    void un_cas_non_redige_rend_vide() {
        LibelleDesCas recueil = LibelleDesCas.de(List.of("- **S1-26** · Un cas"));
        assertEquals(Optional.empty(), recueil.de("S9-99"));
    }

    @Test
    @DisplayName("un libellé très long est coupé sur un BLANC et s'annonce comme coupé")
    void un_libelle_trop_long_est_coupe_proprement() {
        String tresLong = ("mot ".repeat(80)).strip();
        LibelleDesCas recueil = LibelleDesCas.de(List.of("- **S5-01** · " + tresLong));
        String libelle = recueil.de("S5-01").orElseThrow();
        assertTrue(libelle.endsWith("…"), "la coupe devrait s'annoncer : " + libelle);
        assertTrue(libelle.endsWith("mot…"), "la coupe devrait tomber sur un blanc : " + libelle);
    }
}
