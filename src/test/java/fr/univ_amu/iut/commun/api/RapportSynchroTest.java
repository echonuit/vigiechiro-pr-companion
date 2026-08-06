package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Le compte rendu de synchronisation **s'accorde** (#1373).
///
/// Le bandeau de connexion affichait « référentiel à jour : **1 sites**, 199 taxons ». Le libellé est
/// écrit au pluriel par ses appelants, et rien ne l'accordait au nombre : un défaut minuscule, sur le
/// tout premier écran que voit un utilisateur.
class RapportSynchroTest {

    private static final String SITES = "sites";

    @Nested
    @DisplayName("Le nombre commande l'accord du libellé")
    class Accord {

        @Test
        @DisplayName("#1373 : un seul élément met le libellé au singulier")
        void unSeulElementDonneLeSingulier() {
            assertThat(new RapportSynchro(SITES, 1).enClair()).isEqualTo("1 site");
        }

        @Test
        @DisplayName("Deux éléments gardent le pluriel : c'est la frontière de l'accord")
        void deuxElementsGardentLePluriel() {
            // Frontière révélée par PIT : `nombre >= 2` mute en `> 2` sans qu'aucun test ne bronche
            // tant qu'on ne teste que 3 et 4. Or deux est le pluriel le plus courant.
            assertThat(new RapportSynchro(SITES, 2).enClair()).isEqualTo("2 sites");
        }

        @Test
        @DisplayName("Plusieurs éléments gardent le pluriel")
        void plusieursElementsGardentLePluriel() {
            assertThat(new RapportSynchro(SITES, 3).enClair()).isEqualTo("3 sites");
        }

        @Test
        @DisplayName("Zéro élément se dit au singulier, comme en français courant")
        void zeroSeDitAuSingulier() {
            assertThat(new RapportSynchro(SITES, 0).enClair()).isEqualTo("0 site");
        }

        @Test
        @DisplayName("Un libellé de plusieurs mots s'accorde en entier")
        void libelleDePlusieursMots() {
            assertThat(new RapportSynchro("nuits opportunistes", 1).enClair()).isEqualTo("1 nuit opportuniste");
            assertThat(new RapportSynchro("nuits opportunistes", 4).enClair()).isEqualTo("4 nuits opportunistes");
        }

        @Test
        @DisplayName("Un libellé qui porte déjà sa marque d'accord n'est pas touché")
        void libelleDejaMarqueResteIntact() {
            // « nuit(s) récupérée(s) » dit déjà son accord : y retirer un « s » le mutilerait.
            assertThat(new RapportSynchro("nuit(s) récupérée(s)", 1).enClair()).isEqualTo("1 nuit(s) récupérée(s)");
        }

        @Test
        @DisplayName("Un mot qui ne finit pas par « s » traverse l'accord intact")
        void motSansMarqueDePluriel() {
            assertThat(new RapportSynchro("taxa", 1).enClair()).isEqualTo("1 taxa");
        }
    }

    @Nested
    @DisplayName("Ce que l'accord ne doit pas casser")
    class Existant {

        @Test
        @DisplayName("Un empêchement énonce sa cause, sans compter")
        void empechementDitSaCause() {
            assertThat(RapportSynchro.empechee(SITES, "Vigie-Chiro injoignable").enClair())
                    .isEqualTo("sites non récupérés (Vigie-Chiro injoignable)");
        }

        @Test
        @DisplayName("Une précision reste accolée au compte, accordé lui aussi")
        void precisionSuitLAccord() {
            assertThat(new RapportSynchro(SITES, 1)
                            .avecPrecision("40 restent à compléter")
                            .enClair())
                    .isEqualTo("1 site (40 restent à compléter)");
        }
    }
}
