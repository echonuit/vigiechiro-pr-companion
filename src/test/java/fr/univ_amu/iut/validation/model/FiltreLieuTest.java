package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde du filtre `--lieu` (#2971), pendant CLI de la puce « Lieu ».
///
/// Ce que ces cas épinglent n'est pas seulement le comportement, mais les **écarts assumés** avec
/// l'écran : le point exclu, la correspondance partielle, et le refus plutôt qu'un ensemble vide. Ce
/// sont trois décisions de conception, prises avant le code ; les redécouvrir par accident coûterait
/// plus cher que de les lire ici.
class FiltreLieuTest {

    private static LigneObservationAudio ligne(long id, String commune, String carre, String point, String site) {
        return new LigneObservationAudio(
                id,
                id,
                1L,
                2,
                "2026-06-22",
                carre,
                point,
                site,
                "Pippip",
                0.9,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                null,
                "Pipistrelle commune",
                null,
                null,
                "Chiroptères",
                "seq" + id + ".wav",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                commune);
    }

    private static final LigneObservationAudio AHETZE = ligne(1, "Ahetze", "640380", "A1", "Étang de la Tuilière");
    private static final LigneObservationAudio VENELLES = ligne(2, "Venelles", "870150", "A1", "Le pré");

    @Test
    @DisplayName("#2971 : aucun lieu demandé n'écarte rien")
    void sans_lieu_rien_n_est_ecarte() {
        List<LigneObservationAudio> toutes = List.of(AHETZE, VENELLES);
        assertThat(FiltreLieu.appliquer(toutes, List.of())).isEqualTo(toutes);
        assertThat(FiltreLieu.appliquer(toutes, null)).isEqualTo(toutes);
        assertThat(FiltreLieu.appliquer(toutes, List.of("  "))).isEqualTo(toutes);
    }

    @Test
    @DisplayName("#2971 : chaque dimension filtre : commune, carré, site")
    void chaque_dimension_filtre() {
        List<LigneObservationAudio> toutes = List.of(AHETZE, VENELLES);
        assertThat(FiltreLieu.appliquer(toutes, List.of("Ahetze"))).containsExactly(AHETZE);
        assertThat(FiltreLieu.appliquer(toutes, List.of("870150"))).containsExactly(VENELLES);
        assertThat(FiltreLieu.appliquer(toutes, List.of("Le pré"))).containsExactly(VENELLES);
    }

    @Test
    @DisplayName("#2971 : la correspondance est partielle, insensible à la casse et aux accents")
    void correspondance_partielle_et_tolerante() {
        List<LigneObservationAudio> toutes = List.of(AHETZE, VENELLES);
        // « etang » sans accent trouve « Étang de la Tuilière » : en ligne de commande on tape à
        // l'aveugle, sans liste pour rappeler l'orthographe.
        assertThat(FiltreLieu.appliquer(toutes, List.of("etang"))).containsExactly(AHETZE);
        assertThat(FiltreLieu.appliquer(toutes, List.of("AHETZE"))).containsExactly(AHETZE);
        assertThat(FiltreLieu.appliquer(toutes, List.of("venel"))).containsExactly(VENELLES);
    }

    @Test
    @DisplayName("#2971 : plusieurs lieux cumulent, comme cocher deux cases")
    void plusieurs_lieux_cumulent() {
        assertThat(FiltreLieu.appliquer(List.of(AHETZE, VENELLES), List.of("Ahetze", "Venelles")))
                .as("appartenance : une ligne passe par l'un OU l'autre")
                .containsExactly(AHETZE, VENELLES);
    }

    @Test
    @DisplayName("#2971 : le point n'est PAS une dimension du filtre, contrairement à la puce")
    void le_point_n_est_pas_filtrable() {
        // Les deux lignes portent un point « A1 » dans des carrés différents : c'est précisément
        // l'ambiguïté que la puce règle en qualifiant, et que la CLI écarte en n'offrant pas le point.
        // Sans cette exclusion, « --lieu A1 » retiendrait les deux sans que rien ne le montre.
        assertThatThrownBy(() -> FiltreLieu.appliquer(List.of(AHETZE, VENELLES), List.of("A1")))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    @DisplayName("#2971 : un lieu sans correspondance est un REFUS, qui nomme les lieux présents")
    void sans_correspondance_le_filtre_refuse() {
        assertThatThrownBy(() -> FiltreLieu.appliquer(List.of(AHETZE, VENELLES), List.of("Marseille")))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Marseille")
                // Le refus doit DIRE ce qui existe : sans cela il laisse l'utilisateur deviner sa faute
                // de frappe, et un ensemble vide rendu en silence serait pire encore.
                .hasMessageContaining("Ahetze")
                // Le carré est nommé D'UN SEUL TENANT, comme l'écran l'affiche (#3159) : ce que le refus
                // liste doit se recopier tel quel dans la commande suivante. Deux entrées séparées,
                // « 870150 » puis « Le pré », donneraient à croire à deux lieux là où il n'y en a qu'un.
                .hasMessageContaining("870150 · Le pré");
    }

    @Test
    @DisplayName("#3191 : une demande blanche ne dispense pas les autres de correspondre")
    void une_demande_blanche_ne_dispense_pas_les_autres() {
        // Le cas d'une boucle de script qui interpole une variable vide parmi d'autres valeurs, ce qui
        // est l'usage même d'une option répétable. Sans le garde, la chaîne vide entre dans les
        // demandes, « contient "" » est toujours vrai, et le refus promis par l'ADR 3082 n'a pas lieu.
        assertThatThrownBy(() -> FiltreLieu.appliquer(List.of(AHETZE, VENELLES), List.of("", "Marseille")))
                .as("« Marseille » reste introuvable, que la liste porte une valeur vide ou non")
                .isInstanceOf(RegleMetierException.class);

        assertThat(FiltreLieu.appliquer(List.of(AHETZE, VENELLES), List.of("")))
                .as("une demande vide SEULE ne désigne rien : elle n'écarte rien, comme --lieu absent")
                .containsExactly(AHETZE, VENELLES);
    }

    @Test
    @DisplayName("#3191 : le refus cite plusieurs lieux au pluriel, et borne ce qu'il énumère")
    void le_refus_cite_au_pluriel_et_borne_son_enumeration() {
        assertThatThrownBy(() -> FiltreLieu.appliquer(List.of(AHETZE), List.of("Marseille", "Toulon")))
                .as("deux valeurs demandées ne se citent pas comme une seule")
                .hasMessageContaining("ces lieux (Marseille, Toulon)");

        // Un refus qui déverserait deux cents communes ne se lirait pas : au-delà de douze, il tronque
        // et annonce le total. C'est le seul endroit où l'utilisateur apprend qu'il n'a pas tout vu.
        List<LigneObservationAudio> quinzeCarres = java.util.stream.IntStream.range(0, 15)
                .mapToObj(rang -> ligne(rang, "Ahetze", "6403" + (10 + rang), "A1", null))
                .map(LigneObservationAudio.class::cast)
                .toList();

        assertThatThrownBy(() -> FiltreLieu.appliquer(quinzeCarres, List.of("Marseille")))
                .hasMessageContaining("… (16 en tout)");

        // La borne EXACTE, sans quoi rien ne distingue « au-delà de douze » de « à partir de douze » :
        // onze carrés et leur commune font douze lieux, qui doivent tous se lire.
        List<LigneObservationAudio> douzeLieux = java.util.stream.IntStream.range(0, 11)
                .mapToObj(rang -> ligne(rang, "Ahetze", "6403" + (10 + rang), "A1", null))
                .map(LigneObservationAudio.class::cast)
                .toList();

        assertThatThrownBy(() -> FiltreLieu.appliquer(douzeLieux, List.of("Marseille")))
                .as("douze lieux tiennent dans un refus : c'est treize qui tronque")
                .hasMessageNotContaining("en tout");
    }

    @Test
    @DisplayName("Une commune non résolue ne devient pas un lieu vide, ni au filtre ni au refus")
    void une_commune_non_resolue_ne_devient_pas_un_lieu_vide() {
        // Cas réel, pas théorique : un point sans GPS n'a pas de commune (`point_commune` est une table
        // latérale, ADR 2791). Sans le garde, la ligne offrirait une dimension nulle - et le refus
        // listerait un lieu vide entre deux virgules, ce que personne ne saurait recopier.
        LigneObservationAudio sansCommune = ligne(3, null, "130711", "A1", null);

        assertThat(FiltreLieu.appliquer(List.of(sansCommune), List.of("130711")))
                .as("le carré reste comparable, la commune manquante n'empêche rien")
                .containsExactly(sansCommune);
        assertThatThrownBy(() -> FiltreLieu.appliquer(List.of(sansCommune), List.of("Marseille")))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("130711")
                .as("le refus nomme le seul lieu présent, et rien d'autre")
                .extracting(erreur -> erreur.getMessage().replace("130711", ""))
                .asString()
                .doesNotContain(", ,")
                .doesNotContain(": ,");
    }

    @Test
    @DisplayName("#3159 : le carré se tape par son numéro, par son nom, ou tel que le refus l'écrit")
    void le_carre_se_tape_par_l_une_ou_l_autre_etiquette() {
        // La contrepartie de la ligne ci-dessus : nommer les lieux qualifiés ne doit obliger personne à
        // taper un point médian. La correspondance reste partielle, comme depuis #2971.
        assertThat(FiltreLieu.appliquer(List.of(AHETZE, VENELLES), List.of("640380")))
                .as("le numéro officiel, ce que tapent les scripts")
                .containsExactly(AHETZE);
        assertThat(FiltreLieu.appliquer(List.of(AHETZE, VENELLES), List.of("tuiliere")))
                .as("le nom convivial, sans accent ni casse : c'est ainsi qu'on retient son propre carré")
                .containsExactly(AHETZE);
        assertThat(FiltreLieu.appliquer(List.of(AHETZE, VENELLES), List.of("640380 · Étang de la Tuilière")))
                .as("et la forme qualifiée, recopiée depuis un refus ou depuis l'écran")
                .containsExactly(AHETZE);
    }
}
