package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie la **règle de repli** du référentiel d'activité (#2351) : on cherche du plus précis au plus
/// général, mais on retient la **première déclinaison fiable**, pas la plus fine.
///
/// C'est la règle centrale du lot, et la plus facile à écrire à l'envers. Descendre vers un seuil peu
/// fiable parce qu'il est plus spécifique produit une classe plus **fausse**, pas plus juste — et rien
/// à l'écran ne distinguerait les deux.
///
/// Les jeux sont écrits à la main plutôt que lus dans la ressource embarquée : un test de règle qui
/// dépend de données réelles se met à échouer quand la source évolue, pour des raisons qui n'ont rien
/// à voir avec la règle. La justesse de la ressource est gardée à part.
class ReferentielActiviteTest {

    private static ReferentielActivite referentiel(String... lignes) throws IOException {
        return ReferentielActivite.lire(new StringReader(String.join("\n", lignes)));
    }

    private static ContexteActivite contexte(SaisonActivite saison, String region, String milieu) {
        return new ContexteActivite(
                Optional.ofNullable(saison), Optional.ofNullable(region), Optional.ofNullable(milieu));
    }

    @Test
    @DisplayName("La déclinaison la plus précise gagne, quand elle est fiable")
    void plus_precise_si_fiable() throws IOException {
        ReferentielActivite ref = referentiel(
                "Pipkuh;national;toutes;5;50;500;9000;Tres bonne",
                "Pipkuh;region:Occitanie;ete;8;80;800;600;Bonne",
                "Pipkuh;habitat:Foret;ete;10;100;1000;400;Bonne");

        assertThat(ref.pour("Pipkuh", contexte(SaisonActivite.ETE, "Occitanie", "Foret")))
                .get()
                .extracting(SeuilsActivite::declinaison)
                .isEqualTo("habitat:Foret");
    }

    @Test
    @DisplayName("Une déclinaison PRÉCISE mais PEU FIABLE est écartée au profit d'une plus générale fiable")
    void precise_mais_peu_fiable_ecartee() throws IOException {
        // Le cœur de la règle. Douze nuits d'un habitat donné sont plus spécifiques que neuf mille nuits
        // nationales, et beaucoup moins solides.
        ReferentielActivite ref = referentiel(
                "Pipkuh;national;toutes;5;50;500;9000;Tres bonne", "Pipkuh;habitat:Foret;ete;10;100;1000;12;Faible");

        assertThat(ref.pour("Pipkuh", contexte(SaisonActivite.ETE, null, "Foret")))
                .get()
                .extracting(SeuilsActivite::declinaison)
                .as("le national fiable doit l'emporter sur l'habitat peu fiable")
                .isEqualTo("national");
    }

    @Test
    @DisplayName("Faute de mieux, les seuils peu fiables sont rendus — mais marqués indicatifs")
    void faute_de_mieux_indicatif() throws IOException {
        // Ne rien dire ferait croire à une absence de données là où il n'y a qu'une incertitude assumée.
        ReferentielActivite ref = referentiel("Pipkuh;habitat:Foret;ete;10;100;1000;12;Moderee");

        Optional<SeuilsActivite> seuils = ref.pour("Pipkuh", contexte(SaisonActivite.ETE, null, "Foret"));

        assertThat(seuils).isPresent();
        assertThat(seuils.get().indicatif())
                .as("à afficher « Moyenne (indicatif) »")
                .isTrue();
    }

    @Test
    @DisplayName("La saison précise passe avant « toutes saisons », à déclinaison égale")
    void saison_precise_dabord() throws IOException {
        ReferentielActivite ref = referentiel(
                "Pipkuh;national;toutes;5;50;500;9000;Tres bonne", "Pipkuh;national;ete;7;70;700;3000;Tres bonne");

        assertThat(ref.pour("Pipkuh", contexte(SaisonActivite.ETE, null, null)))
                .get()
                .extracting(SeuilsActivite::saison)
                .isEqualTo("ete");
    }

    @Test
    @DisplayName("Un taxon hors référentiel ne rend rien : l'écran écrira « non couvert »")
    void taxon_hors_referentiel() throws IOException {
        ReferentielActivite ref = referentiel("Pipkuh;national;toutes;5;50;500;9000;Tres bonne");

        assertThat(ref.pour("Tetvir", ContexteActivite.NATIONAL))
                .as("un orthoptère n'a pas de seuil : une cellule vide se lirait comme une donnée manquante")
                .isEmpty();
        assertThat(ref.couvre("Tetvir")).isFalse();
        assertThat(ref.couvre("Pipkuh")).isTrue();
    }

    @Test
    @DisplayName("Une ligne illisible est écartée, jamais rangée d'office du côté fiable")
    void ligne_illisible_ecartee() throws IOException {
        ReferentielActivite ref = referentiel(
                "Pipkuh;national;toutes;5;50;500;9000;Confiance inventee",
                "Pipnat;national;toutes;x;50;500;9000;Bonne",
                "Barbar;national;toutes;2;16;181;4770;Tres bonne");

        assertThat(ref.pour("Pipkuh", ContexteActivite.NATIONAL)).isEmpty();
        assertThat(ref.pour("Pipnat", ContexteActivite.NATIONAL)).isEmpty();
        assertThat(ref.pour("Barbar", ContexteActivite.NATIONAL)).isPresent();
    }

    @Test
    @DisplayName("Les quatre classes se découpent sur les quantiles, bornes inclusives vers le haut")
    void classes() {
        SeuilsActivite seuils =
                new SeuilsActivite(10, 100, 1000, 500, ConfianceReferentiel.BONNE, "national", "toutes");

        assertThat(ClasseActivite.de(9, seuils)).isEqualTo(ClasseActivite.FAIBLE);
        assertThat(ClasseActivite.de(10, seuils))
                .as("pile sur Q25 : moyenne, pas faible — sinon le seuil se lirait comme une sous-estimation")
                .isEqualTo(ClasseActivite.MOYENNE);
        assertThat(ClasseActivite.de(99, seuils)).isEqualTo(ClasseActivite.MOYENNE);
        assertThat(ClasseActivite.de(100, seuils)).isEqualTo(ClasseActivite.FORTE);
        assertThat(ClasseActivite.de(999, seuils)).isEqualTo(ClasseActivite.FORTE);
        assertThat(ClasseActivite.de(1000, seuils)).isEqualTo(ClasseActivite.TRES_FORTE);
    }

    @Test
    @DisplayName("La ressource embarquée se charge et porte ses ~3900 jeux de seuils")
    void ressource_embarquee() {
        ReferentielActivite ref = ReferentielActivite.embarque();

        assertThat(ref.taille())
                .as("un chargement qui rendrait peu de lignes signalerait un format mal lu")
                .isGreaterThan(3500);
        assertThat(ref.couvre("Barbar")).as("la Barbastelle doit être couverte").isTrue();
        assertThat(ReferentielActivite.CITATION).contains("Bas Y.", "2020");
        assertThat(ReferentielActivite.AVERTISSEMENT).contains("n'est pas un niveau d'enjeu");
    }
}
