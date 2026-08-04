package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.FiltreLieu;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Gardes des filtres CLI de l'activité (#3059), pendants des cinq puces de l'écran.
///
/// Ce qui est épinglé ici n'est pas le comportement nominal - il se devine - mais les **décisions** : ce
/// que le point ne fait pas, ce qui refuse et ce qui rend légitimement vide.
class FiltresActiviteTest {

    private static final Set<Long> AUCUNE_OPPORTUNISTE = Set.of();

    private static ContactHoraire contact(
            String taxon, String groupe, LocalDateTime heure, String commune, String point, Long passage) {
        return new ContactHoraire(taxon, taxon, groupe, heure, commune, "640380", point, passage, null);
    }

    private static final ContactHoraire RHIFER =
            contact("Rhifer", "Chiroptères", LocalDateTime.of(2026, 6, 21, 22, 0), "Ahetze", "A1", 1L);
    private static final ContactHoraire PIPKUH =
            contact("Pipkuh", "Chiroptères", LocalDateTime.of(2026, 6, 22, 2, 0), "Biarritz", "Z1", 2L);
    private static final ContactHoraire SAUTERELLE =
            contact("Ortsp", "Orthoptères", LocalDateTime.of(2026, 6, 25, 23, 0), "Ahetze", "B2", 3L);
    private static final List<ContactHoraire> TOUS = List.of(RHIFER, PIPKUH, SAUTERELLE);

    @Test
    @DisplayName("#3059 : le point n'est PAS une dimension de lieu en ligne de commande")
    void le_point_n_est_pas_filtrable() {
        // Le schéma pose UNIQUE(site_id, code) : « Z1 » désigne autant de lieux qu'il y a de carrés, et
        // « --lieu Z1 » ne désignerait donc rien de précis. Même arbitrage que exporter-sons, et deux
        // commandes qui traiteraient le lieu différemment seraient pires que la limite elle-même.
        assertThat(FiltresActivite.dimensionsLieu(RHIFER))
                .as("commune et carré, rien d'autre")
                .containsExactly("Ahetze", "640380");
        assertThatThrownBy(() -> FiltreLieu.appliquer(TOUS, List.of("A1"), FiltresActivite::dimensionsLieu))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    @DisplayName("#3159 : le carré se compare qualifié, et se tape par l'une ou l'autre étiquette")
    void le_carre_se_compare_qualifie() {
        // Ce que le refus nomme doit se recopier tel quel : il liste donc le carré comme l'écran
        // l'affiche. Ce qui ne veut pas dire l'exiger - la correspondance reste partielle.
        ContactHoraire nomme = new ContactHoraire(
                RHIFER.taxon(),
                RHIFER.nomEspece(),
                RHIFER.groupe(),
                RHIFER.heure(),
                RHIFER.commune(),
                RHIFER.numeroCarre(),
                RHIFER.codePoint(),
                RHIFER.idPassage(),
                "Vallon");

        assertThat(FiltresActivite.dimensionsLieu(nomme))
                .as("le nom du site n'est pas une dimension de plus : c'est l'autre étiquette du carré")
                .containsExactly("Ahetze", "640380 · Vallon");
        assertThat(FiltreLieu.appliquer(List.of(nomme), List.of("vallon"), FiltresActivite::dimensionsLieu))
                .as("le nom seul retient ce carré, sans point médian à taper")
                .containsExactly(nomme);
        assertThat(FiltreLieu.appliquer(List.of(nomme), List.of("640380"), FiltresActivite::dimensionsLieu))
                .as("le numéro seul aussi, comme depuis #3059")
                .containsExactly(nomme);
    }

    @Test
    @DisplayName("#3059 : --lieu retient sur la commune comme sur le carré, partiellement")
    void le_lieu_retient_sur_chaque_dimension() {
        assertThat(FiltreLieu.appliquer(TOUS, List.of("ahetze"), FiltresActivite::dimensionsLieu))
                .as("insensible à la casse, comme --lieu de exporter-sons")
                .containsExactly(RHIFER, SAUTERELLE);
        assertThat(FiltreLieu.appliquer(TOUS, List.of("640380"), FiltresActivite::dimensionsLieu))
                .hasSize(3);
    }

    @Test
    @DisplayName("#3059 : --nuit suit la bascule à midi, comme l'écran")
    void la_nuit_est_biologique() {
        // 02:00 le 22 juin appartient à la nuit du 21 : la CLI ne peut pas dire autre chose que l'écran
        // sur la même donnée, sans quoi un recoupement entre les deux surfaces deviendrait faux.
        assertThat(FiltresActivite.parNuit(TOUS, "2026-06-21")).containsExactly(RHIFER, PIPKUH);
    }

    @Test
    @DisplayName("#3059 : une nuit ou un taxon parent absent REFUSE, en nommant ce qui est présent")
    void ce_qui_designe_refuse_et_nomme() {
        // Un ensemble vide en code 0 est un succès qui ne contient rien : le script enchaîne et l'expert
        // reçoit un fichier creux. Le refus nomme ce qui existe, parce que la correction consiste à lire.
        assertThatThrownBy(() -> FiltresActivite.parNuit(TOUS, "2026-07-01"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("2026-06-21")
                .hasMessageContaining("2026-06-25");
        assertThatThrownBy(() -> FiltresActivite.parTaxonParent(TOUS, "Oiseaux"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Chiroptères")
                .hasMessageContaining("Orthoptères");
    }

    @Test
    @DisplayName("#3059 : une saisie illisible ou hors valeurs est refusée avec l'attendu")
    void une_saisie_fautive_est_refusee() {
        assertThatThrownBy(() -> FiltresActivite.parNuit(TOUS, "21/06/2026"))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("AAAA-MM-JJ");
        assertThatThrownBy(() -> FiltresActivite.parNature(TOUS, "aleatoire", AUCUNE_OPPORTUNISTE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("protocole");
    }

    @Test
    @DisplayName("#3059 : le taxon parent est partiel et insensible aux accents")
    void le_taxon_parent_est_partiel() {
        assertThat(FiltresActivite.parTaxonParent(TOUS, "chiropteres"))
                .as("sans accent et en minuscules, comme --lieu")
                .containsExactly(RHIFER, PIPKUH);
    }

    @Test
    @DisplayName("#3059 : ce qui QUALIFIE rend légitimement un ensemble vide, sans refuser")
    void ce_qui_qualifie_rend_vide_sans_refuser() {
        // Asymétrie assumée avec --nuit et --taxon-parent : « aucune nuit opportuniste » est une réponse,
        // souvent celle qu'on cherchait. Refuser ici obligerait à savoir d'avance ce qu'on va trouver.
        assertThat(FiltresActivite.parNature(TOUS, "opportuniste", AUCUNE_OPPORTUNISTE))
                .as("aucune nuit marquée : la réponse est « rien », pas une erreur")
                .isEmpty();
        assertThat(FiltresActivite.parNature(TOUS, "protocole", AUCUNE_OPPORTUNISTE))
                .as("une nuit sans marquage relève du protocole, qui est le cas courant")
                .hasSize(3);
        assertThat(FiltresActivite.parNature(TOUS, "opportuniste", Set.of(2L))).containsExactly(PIPKUH);
        assertThat(FiltresActivite.aEnjeu(TOUS, Set.of("Barbar")::contains))
                .as("aucune espèce prioritaire dans ce lot : une information, pas une faute")
                .isEmpty();
        assertThat(FiltresActivite.aEnjeu(TOUS, Set.of("Rhifer")::contains)).containsExactly(RHIFER);
    }

    @Test
    @DisplayName("#3059 : sans critère, rien n'est écarté")
    void sans_critere_rien_n_est_ecarte() {
        assertThat(FiltresActivite.parNuit(TOUS, null)).isEqualTo(TOUS);
        assertThat(FiltresActivite.parTaxonParent(TOUS, "  ")).isEqualTo(TOUS);
        assertThat(FiltresActivite.parNature(TOUS, null, AUCUNE_OPPORTUNISTE)).isEqualTo(TOUS);
        assertThat(FiltreLieu.appliquer(TOUS, List.of(), FiltresActivite::dimensionsLieu))
                .isEqualTo(TOUS);
    }

    @Test
    @DisplayName("#3059 : un référentiel vide se DIT, au lieu de rendre un fichier vide et muet")
    void un_referentiel_vide_se_dit() {
        // ADR 3048 : une sortie machine ne retire pas, elle dit. C'est le seul filtre dont un résultat
        // vide a deux causes OPPOSÉES - aucune espèce prioritaire ici, ou aucun référentiel du tout - et
        // elles appellent des conduites contraires : lire le résultat, ou réparer une installation.
        assertThat(FiltresActivite.avertissementReferentielVide(Set.of()))
                .hasValueSatisfying(message ->
                        assertThat(message).contains("--a-enjeu").contains("non parce qu'aucune espèce prioritaire"));
        assertThat(FiltresActivite.avertissementReferentielVide(Set.of("Rhifer")))
                .as("référentiel présent : rien à signaler, le vide voudra dire ce qu'il dit")
                .isEmpty();
    }

    @Test
    @DisplayName("#3059 : une commune non résolue ne fait pas échouer --lieu")
    void une_commune_absente_ne_casse_rien() {
        // Le modèle l'autorise explicitement (« commune non résolue »), et cela arrive : la commune se
        // dérive du GPS du point, qui peut manquer. Sans le filtre sur les valeurs nulles, la
        // comparaison normalise un null et l'appel entier échoue - sur une donnée parfaitement légitime.
        //
        // Trouvé par PIT : remplacer ce filtre par `true` ne faisait rougir aucun test, mon semis ayant
        // toujours une commune.
        ContactHoraire sansCommune =
                contact("Barbar", "Chiroptères", LocalDateTime.of(2026, 6, 21, 23, 0), null, "C3", 4L);

        assertThat(FiltresActivite.dimensionsLieu(sansCommune))
                .as("le carré reste comparable, la commune manquante est écartée")
                .containsExactly("640380");
        assertThat(FiltreLieu.appliquer(List.of(sansCommune), List.of("640380"), FiltresActivite::dimensionsLieu))
                .containsExactly(sansCommune);
    }
}
