package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **L'heure du traitement se lit à l'heure du poste** (#5683). La carte « Traitement Vigie-Chiro »
/// recopiait l'heure UTC du serveur au milieu d'une phrase française : « depuis le 30/09/2026 à 14:07 »
/// pour une analyse partie à 16:07 à Paris. La commande la convertissait depuis #3678, l'écran non.
class FormatsTraitementFuseauTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Test
    @DisplayName("#5683 : l'instant du serveur se lit à Paris en été, deux heures plus tard")
    void paris_en_ete() {
        assertThat(enCoursDepuis("2026-09-30T14:07:45.136000+00:00", PARIS)).contains("depuis le 30/09/2026 à 16:07");
    }

    @Test
    @DisplayName("#5683 : l'hiver, une heure seulement")
    void paris_en_hiver() {
        assertThat(enCoursDepuis("2026-01-15T14:07:00+00:00", PARIS)).contains("depuis le 15/01/2026 à 15:07");
    }

    @Test
    @DisplayName("#5683 : un autre fuseau, en retard sur l'UTC")
    void un_autre_fuseau() {
        assertThat(enCoursDepuis("2026-09-30T14:07:45+00:00", ZoneId.of("America/Cayenne")))
                .contains("depuis le 30/09/2026 à 11:07");
    }

    @Test
    @DisplayName("#5683 : la conversion change le jour quand elle traverse minuit")
    void passage_de_minuit() {
        assertThat(enCoursDepuis("2026-09-30T22:30:00+00:00", PARIS)).contains("depuis le 01/10/2026 à 00:30");
    }

    @Test
    @DisplayName("#5683 : la forme RFC 1123, que la plateforme rend aussi, se convertit de même")
    void forme_rfc_1123() {
        assertThat(enCoursDepuis("Wed, 30 Sep 2026 14:07:45 GMT", PARIS)).contains("depuis le 30/09/2026 à 16:07");
    }

    @Test
    @DisplayName("#5683 : une valeur illisible reste visible, telle quelle")
    void valeur_illisible_reste_visible() {
        assertThat(enCoursDepuis("hier soir", PARIS)).contains("depuis le hier soir");
    }

    @Test
    @DisplayName("#5683 : la fin d'une analyse suit la même règle que son début")
    void la_fin_suit_la_meme_regle() {
        Traitement fini = new Traitement(EtatTraitement.FINI, null, null, "2026-09-30T14:07:45+00:00", null, null);

        assertThat(FormatsTraitement.libelle(fini, PARIS)).contains("le 30/09/2026 à 16:07");
    }

    /// Le relevé est daté par l'horloge du poste, sans décalage : il est déjà à l'heure locale, et la
    /// conversion ne doit pas le déplacer une seconde fois. C'est la date que S4-50 réaffiche.
    @Test
    @DisplayName("#5683 : la date d'un relevé, déjà locale, se lit sans être décalée")
    void le_releve_deja_local_ne_bouge_pas() {
        ReleveTraitement releve = new ReleveTraitement(
                1L, "p", new Traitement(EtatTraitement.EN_COURS, null, null, null, null, null), "2026-09-30T16:07:45");

        assertThat(FormatsTraitement.fraicheur(releve, PARIS)).isEqualTo("Dernier état connu le 30/09/2026 à 16:07.");
    }

    private static String enCoursDepuis(String debut, ZoneId fuseau) {
        return FormatsTraitement.libelle(
                new Traitement(EtatTraitement.EN_COURS, null, debut, null, null, null), fuseau);
    }
}
