package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que [Horodatage#dateSeule] rend d'une date **venue de la base** (#3950).
///
/// ## Pourquoi ces cas-là, et pas le cas nominal
///
/// Le cas nominal est déjà couvert, indirectement mais réellement :
/// `CompteRenduChiffreImportTest` asserte le titre complet « Import terminé - nuit du 22/04/2026 »,
/// qui traverse cette méthode. Le vérifier une seconde fois n'apprendrait rien.
///
/// ⚠️ Ce sont les **branches défensives** qui manquaient, et une mesure de mutation l'a dit : à la
/// clôture des suites, `dateSeule` ligne 74 - le `return dateIso` du `catch` - ressortait
/// `NO_COVERAGE`, y compris après élargissement du ciblage. J'avais écrit un repli pour une donnée
/// abîmée sans jamais l'exercer.
///
/// Ce n'est pas un défensif **inatteignable** : `Passage.dateEnregistrement` est une **chaîne libre**,
/// et rien dans le schéma n'en garantit la forme. Une base restaurée d'une version ancienne, ou
/// bricolée à la main, y met ce qu'elle veut. Le titre d'un compte rendu ne doit pas casser dessus.
class HorodatageTest {

    @Test
    @DisplayName("#3950 : une date ISO devient la date française d'une phrase")
    void une_date_iso_devient_francaise() {
        assertThat(Horodatage.dateSeule("2026-04-22")).isEqualTo("22/04/2026");
    }

    @Test
    @DisplayName("#3950 : une date illisible ressort TELLE QUELLE, elle ne fait pas tomber le titre")
    void une_date_illisible_ressort_telle_quelle() {
        // Le compte rendu s'affiche au moment où l'import vient de finir. Y lever une exception
        // ferait disparaître l'écran entier pour un format de date : le repli montre ce qu'on a.
        assertThat(Horodatage.dateSeule("22 avril 2026"))
                .as("mieux vaut une date au mauvais format qu'un écran qui ne s'affiche pas")
                .isEqualTo("22 avril 2026");
    }

    @Test
    @DisplayName("#3950 : une date absente ne rend ni « null » ni une exception")
    void une_date_absente_rend_une_chaine_vide() {
        assertThat(Horodatage.dateSeule(null)).isEmpty();
        assertThat(Horodatage.dateSeule("   "))
                .as("une chaîne d'espaces vient d'une colonne remplie à la main ; elle vaut une absence")
                .isEmpty();
    }
}
