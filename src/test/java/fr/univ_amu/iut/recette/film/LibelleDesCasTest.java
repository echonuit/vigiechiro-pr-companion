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
    void unePuceSimple() {
        LibelleDesCas recueil = LibelleDesCas.de(List.of("- **S1-26** · La modale s'ouvre sans saut visuel"));
        assertEquals(Optional.of("La modale s'ouvre sans saut visuel"), recueil.de("S1-26"));
    }

    @Test
    @DisplayName("une puce repliée se recolle ENTIÈREMENT, dernière ligne comprise")
    void unePucePlieeSeRecolle() {
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
    void unePuceACaseACocher() {
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
    void laQualificationEstRetiree() {
        LibelleDesCas recueil =
                LibelleDesCas.de(List.of("- **S2-04** · *perceptif* · Le tableau garde sa position de défilement"));
        assertEquals(Optional.of("Le tableau garde sa position de défilement"), recueil.de("S2-04"));
    }

    @Test
    @DisplayName("gras, liens et numéro d'issue sont dépouillés")
    void lesOrnementsSontRetires() {
        LibelleDesCas recueil = LibelleDesCas.de(
                List.of("- **S3-01** · Le **quota** est lu depuis [la fiche](https://exemple.org/x) #4053"));
        assertEquals(Optional.of("Le quota est lu depuis la fiche"), recueil.de("S3-01"));
    }

    @Test
    @DisplayName("une puce imbriquée n'est pas avalée par celle qui la précède")
    void unePuceImbriqueeNestPasAvalee() {
        LibelleDesCas recueil = LibelleDesCas.de(
                List.of("- **S4-01** · Le premier cas", "  - une précision qui n'appartient pas au libellé"));
        assertEquals(Optional.of("Le premier cas"), recueil.de("S4-01"));
    }

    @Test
    @DisplayName("un cas non rédigé rend vide, et NON un faux libellé")
    void unCasNonRedigeRendVide() {
        LibelleDesCas recueil = LibelleDesCas.de(List.of("- **S1-26** · Un cas"));
        assertEquals(Optional.empty(), recueil.de("S9-99"));
    }

    @Test
    @DisplayName("un libellé très long est coupé sur un BLANC et s'annonce comme coupé")
    void unLibelleTropLongEstCoupeProprement() {
        String tresLong = ("mot ".repeat(80)).strip();
        LibelleDesCas recueil = LibelleDesCas.de(List.of("- **S5-01** · " + tresLong));
        String libelle = recueil.de("S5-01").orElseThrow();
        assertTrue(libelle.endsWith("…"), "la coupe devrait s'annoncer : " + libelle);
        assertTrue(libelle.endsWith("mot…"), "la coupe devrait tomber sur un blanc : " + libelle);
    }
}
