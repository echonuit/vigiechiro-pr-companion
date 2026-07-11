package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests unitaires du value object [ZonesStatut] (#495) : normalisation des `null`, fabriques et
/// superposition zone par zone (un écran ne renseigne que certaines zones, les autres gardent le défaut).
class ZonesStatutTest {

    @Test
    @DisplayName("Le constructeur normalise les null en chaîne vide")
    void constructeur_normalise_les_null() {
        ZonesStatut zones = new ZonesStatut(null, null, null);
        assertThat(zones.gauche()).isEmpty();
        assertThat(zones.centre()).isEmpty();
        assertThat(zones.droite()).isEmpty();
        assertThat(zones).isEqualTo(ZonesStatut.VIDE);
    }

    @Test
    @DisplayName("Les fabriques ne remplissent que le centre (et la droite)")
    void fabriques_centre_et_droite() {
        assertThat(ZonesStatut.centre("résumé")).isEqualTo(new ZonesStatut("", "résumé", ""));
        assertThat(ZonesStatut.centreEtDroite("résumé", "3 / 42")).isEqualTo(new ZonesStatut("", "résumé", "3 / 42"));
    }

    @Test
    @DisplayName("estVide : vrai seulement quand les trois zones sont vides ou blanches")
    void est_vide_si_toutes_les_zones_blanches() {
        assertThat(ZonesStatut.VIDE.estVide()).isTrue();
        assertThat(new ZonesStatut("  ", "", "\t").estVide()).isTrue();
        assertThat(ZonesStatut.centre("résumé").estVide()).isFalse();
        assertThat(new ZonesStatut("contexte", "", "").estVide()).isFalse();
    }

    @Test
    @DisplayName("premierNonVide : renvoie la 1re chaîne non blanche par priorité, sinon vide")
    void premier_non_vide_par_priorite() {
        // Progression > alerte > bilan : la progression en cours l'emporte.
        assertThat(ZonesStatut.premierNonVide("Compression 3/21", "Espace faible", "21 archives"))
                .isEqualTo("Compression 3/21");
        // Rien en cours : l'alerte l'emporte sur le bilan au repos.
        assertThat(ZonesStatut.premierNonVide("", "Espace faible", "21 archives"))
                .isEqualTo("Espace faible");
        // Au repos : le bilan.
        assertThat(ZonesStatut.premierNonVide(null, "  ", "21 archives")).isEqualTo("21 archives");
        // Toutes vides (ou nulles) → chaîne vide.
        assertThat(ZonesStatut.premierNonVide(null, "", "\t")).isEmpty();
        assertThat(ZonesStatut.premierNonVide()).isEmpty();
    }

    @Test
    @DisplayName("superposer : une zone non vide de l'écran l'emporte, une zone vide garde le défaut")
    void superposer_zone_par_zone() {
        ZonesStatut defaut = new ZonesStatut("Identité", "", "");

        // L'écran ne renseigne que centre + droite → l'identité par défaut reste à gauche.
        ZonesStatut resultat = ZonesStatut.superposer(defaut, ZonesStatut.centreEtDroite("60 obs", "12 / 60"));
        assertThat(resultat).isEqualTo(new ZonesStatut("Identité", "60 obs", "12 / 60"));

        // L'écran surcharge explicitement la gauche → elle l'emporte sur le défaut.
        ZonesStatut avecContexte = ZonesStatut.superposer(defaut, new ZonesStatut("Carré 640380", "60 obs", ""));
        assertThat(avecContexte).isEqualTo(new ZonesStatut("Carré 640380", "60 obs", ""));

        // Un écran sans aucune zone → le défaut est intégralement conservé.
        assertThat(ZonesStatut.superposer(defaut, ZonesStatut.VIDE)).isEqualTo(defaut);
    }
}
