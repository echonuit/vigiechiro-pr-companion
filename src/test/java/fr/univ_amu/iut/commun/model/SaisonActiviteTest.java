package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie les **fenêtres phénologiques** du référentiel d'activité (#2351).
///
/// Les bornes tombent **au milieu des mois**, ce qui est précisément ce qu'un découpage calendaire
/// raterait : le 10 juin est du printemps, le 20 juin de l'été, et août appartient encore à l'été.
/// Chaque borne est éprouvée des **deux côtés** — un test qui ne vérifierait que le milieu de chaque
/// fenêtre laisserait passer un décalage d'un jour comme d'un mois.
class SaisonActiviteTest {

    private static LocalDate nuit(int mois, int jour) {
        return LocalDate.of(2026, mois, jour);
    }

    @Test
    @DisplayName("Printemps : du 1er avril au 15 juin (gestation)")
    void printemps() {
        assertThat(SaisonActivite.de(nuit(4, 1))).contains(SaisonActivite.PRINTEMPS);
        assertThat(SaisonActivite.de(nuit(6, 15))).contains(SaisonActivite.PRINTEMPS);
    }

    @Test
    @DisplayName("Été : du 16 juin au 31 août (mise bas et élevage)")
    void ete() {
        assertThat(SaisonActivite.de(nuit(6, 16))).contains(SaisonActivite.ETE);
        assertThat(SaisonActivite.de(nuit(8, 31)))
                .as("août est encore de l'été : le juger à l'aune de l'automne fausserait la classe")
                .contains(SaisonActivite.ETE);
    }

    @Test
    @DisplayName("Automne : du 1er septembre au 15 novembre (migration, accouplements)")
    void automne() {
        assertThat(SaisonActivite.de(nuit(9, 1))).contains(SaisonActivite.AUTOMNE);
        assertThat(SaisonActivite.de(nuit(11, 15))).contains(SaisonActivite.AUTOMNE);
    }

    @Test
    @DisplayName("Hors fenêtre, aucune saison : l'hibernation n'a pas de seuil")
    void hors_fenetre() {
        // Une nuit à trois contacts en janvier n'est pas une nuit faible : c'est une nuit d'hiver. Lui
        // appliquer un seuil estival la ferait passer pour un désert.
        assertThat(SaisonActivite.de(nuit(1, 15))).isEmpty();
        assertThat(SaisonActivite.de(nuit(3, 31)))
                .as("mars est hors fenêtre : le printemps ne commence qu'au 1er avril")
                .isEmpty();
        assertThat(SaisonActivite.de(nuit(11, 16))).isEmpty();
        assertThat(SaisonActivite.de(null)).isEmpty();
    }

    @Test
    @DisplayName("Les trois fenêtres ne se chevauchent pas et ne laissent aucun trou en saison")
    void fenetres_contigues() {
        // Du 1er avril au 15 novembre, chaque jour appartient à exactement une saison. Un décalage de
        // borne créerait soit un jour orphelin, soit un jour revendiqué deux fois.
        for (LocalDate jour = nuit(4, 1); !jour.isAfter(nuit(11, 15)); jour = jour.plusDays(1)) {
            assertThat(SaisonActivite.de(jour))
                    .as("le %s doit appartenir à une saison", jour)
                    .isPresent();
        }
    }
}
