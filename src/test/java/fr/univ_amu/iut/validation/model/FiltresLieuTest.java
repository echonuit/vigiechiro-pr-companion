package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde du filtre `--lieu` (#2971), pendant CLI de la puce « Lieu ».
///
/// Ce que ces cas épinglent n'est pas seulement le comportement, mais les **décisions de conception**
/// : la correspondance partielle, le refus plutôt qu'un ensemble vide, et - depuis #3350 - le fait que
/// le point se **compare** mais ne s'**énumère** pas au refus. Les redécouvrir par accident coûterait
/// plus cher que de les lire ici.
///
/// Le point était **exclu** jusqu'à #3350, et deux cas l'affirmaient. L'argument - « une ligne de
/// commande n'a pas de liste sous les yeux pour distinguer les A1 de deux carrés » - a été démenti par
/// l'inventaire : toutes les sorties qui offrent `--lieu` portent le carré. Ce n'est pas le critère qui
/// doit se restreindre, c'est la sortie qui désambiguïse.
class FiltresLieuTest {

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
        assertThat(FiltresLieu.parLieu(toutes, List.of())).isEqualTo(toutes);
        assertThat(FiltresLieu.parLieu(toutes, null)).isEqualTo(toutes);
        assertThat(FiltresLieu.parLieu(toutes, List.of("  "))).isEqualTo(toutes);
    }

    @Test
    @DisplayName("#2971 : chaque dimension filtre : commune, carré, site")
    void chaque_dimension_filtre() {
        List<LigneObservationAudio> toutes = List.of(AHETZE, VENELLES);
        assertThat(FiltresLieu.parLieu(toutes, List.of("Ahetze"))).containsExactly(AHETZE);
        assertThat(FiltresLieu.parLieu(toutes, List.of("870150"))).containsExactly(VENELLES);
        assertThat(FiltresLieu.parLieu(toutes, List.of("Le pré"))).containsExactly(VENELLES);
    }

    @Test
    @DisplayName("#2971 : la correspondance est partielle, insensible à la casse et aux accents")
    void correspondance_partielle_et_tolerante() {
        List<LigneObservationAudio> toutes = List.of(AHETZE, VENELLES);
        // « etang » sans accent trouve « Étang de la Tuilière » : en ligne de commande on tape à
        // l'aveugle, sans liste pour rappeler l'orthographe.
        assertThat(FiltresLieu.parLieu(toutes, List.of("etang"))).containsExactly(AHETZE);
        assertThat(FiltresLieu.parLieu(toutes, List.of("AHETZE"))).containsExactly(AHETZE);
        assertThat(FiltresLieu.parLieu(toutes, List.of("venel"))).containsExactly(VENELLES);
    }

    @Test
    @DisplayName("#2971 : plusieurs lieux cumulent, comme cocher deux cases")
    void plusieurs_lieux_cumulent() {
        assertThat(FiltresLieu.parLieu(List.of(AHETZE, VENELLES), List.of("Ahetze", "Venelles")))
                .as("appartenance : une ligne passe par l'un OU l'autre")
                .containsExactly(AHETZE, VENELLES);
    }

    @Test
    @DisplayName("#3350 : le point EST une dimension du filtre, et un code seul retient les deux carrés")
    void le_point_est_filtrable() {
        // Ce test disait l'inverse jusqu'à #3350, sur l'argument qu'un code seul (« A1 ») désigne
        // autant de lieux qu'il y a de carrés et qu'une ligne de commande n'a pas de liste sous les
        // yeux pour les distinguer. L'inventaire a démenti la prémisse : les sorties qui offrent
        // `--lieu` portent toutes le carré, donc elles distinguent ces deux lignes. Ce n'est pas le
        // critère qui doit se restreindre, c'est la sortie qui désambiguïse.
        assertThat(FiltresLieu.parLieu(List.of(AHETZE, VENELLES), List.of("A1")))
                .as("les deux carrés ont un point « A1 » : les deux passent, et la sortie les sépare")
                .containsExactly(AHETZE, VENELLES);
    }

    @Test
    @DisplayName("#3350 : le point qualifié par son carré ne désigne qu'une ligne")
    void le_point_qualifie_designe_un_seul_lieu() {
        // Le corollaire de ce qui précède : qui veut UN point le qualifie, exactement comme l'écran
        // l'écrit (#2992). Sans cela, accepter le point n'aurait fait qu'élargir sans rien offrir.
        assertThat(FiltresLieu.parLieu(List.of(AHETZE, VENELLES), List.of("640380 · A1")))
                .containsExactly(AHETZE);
    }

    @Test
    @DisplayName("#3350 : un code de point absent reste un refus, il ne rend pas un ensemble vide")
    void un_point_absent_refuse() {
        // Élargir les dimensions ne doit pas affaiblir le refus : « Z9 » n'existe nulle part.
        assertThatThrownBy(() -> FiltresLieu.parLieu(List.of(AHETZE, VENELLES), List.of("Z9")))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Z9");
    }

    @Test
    @DisplayName("#2971 : un lieu sans correspondance est un REFUS, qui nomme les lieux présents")
    void sans_correspondance_le_filtre_refuse() {
        assertThatThrownBy(() -> FiltresLieu.parLieu(List.of(AHETZE, VENELLES), List.of("Marseille")))
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
        assertThatThrownBy(() -> FiltresLieu.parLieu(List.of(AHETZE, VENELLES), List.of("", "Marseille")))
                .as("« Marseille » reste introuvable, que la liste porte une valeur vide ou non")
                .isInstanceOf(RegleMetierException.class);

        assertThat(FiltresLieu.parLieu(List.of(AHETZE, VENELLES), List.of("")))
                .as("une demande vide SEULE ne désigne rien : elle n'écarte rien, comme --lieu absent")
                .containsExactly(AHETZE, VENELLES);
    }

    @Test
    @DisplayName("#3191 : le refus cite plusieurs lieux au pluriel, et borne ce qu'il énumère")
    void le_refus_cite_au_pluriel_et_borne_son_enumeration() {
        assertThatThrownBy(() -> FiltresLieu.parLieu(List.of(AHETZE), List.of("Marseille", "Toulon")))
                .as("deux valeurs demandées ne se citent pas comme une seule")
                .hasMessageContaining("ces lieux (Marseille, Toulon)");

        // Un refus qui déverserait deux cents communes ne se lirait pas : au-delà de douze, il tronque
        // et annonce le total. C'est le seul endroit où l'utilisateur apprend qu'il n'a pas tout vu.
        List<LigneObservationAudio> quinzeCarres = java.util.stream.IntStream.range(0, 15)
                .mapToObj(rang -> ligne(rang, "Ahetze", "6403" + (10 + rang), "A1", null))
                .map(LigneObservationAudio.class::cast)
                .toList();

        assertThatThrownBy(() -> FiltresLieu.parLieu(quinzeCarres, List.of("Marseille")))
                .hasMessageContaining("… (16 en tout)");

        // La borne EXACTE, sans quoi rien ne distingue « au-delà de douze » de « à partir de douze » :
        // onze carrés et leur commune font douze lieux, qui doivent tous se lire.
        List<LigneObservationAudio> douzeLieux = java.util.stream.IntStream.range(0, 11)
                .mapToObj(rang -> ligne(rang, "Ahetze", "6403" + (10 + rang), "A1", null))
                .map(LigneObservationAudio.class::cast)
                .toList();

        assertThatThrownBy(() -> FiltresLieu.parLieu(douzeLieux, List.of("Marseille")))
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

        assertThat(FiltresLieu.parLieu(List.of(sansCommune), List.of("130711")))
                .as("le carré reste comparable, la commune manquante n'empêche rien")
                .containsExactly(sansCommune);
        // La liste des lieux présents est lue TELLE QUELLE, au lieu d'ôter le numéro de carré du
        // message pour voir ce qui reste : cette technique supposait exactement deux dimensions, et
        // #3350 en a ajouté une troisième - le point qualifié - qu'elle prenait pour un résidu vide.
        assertThatThrownBy(() -> FiltresLieu.parLieu(List.of(sansCommune), List.of("Marseille")))
                .isInstanceOf(RegleMetierException.class)
                .extracting(erreur -> lieuxPresents(erreur.getMessage()))
                .as("le refus nomme le seul carré, et RIEN d'autre : ni entrée vide pour la commune non "
                        + "résolue, ni le point - qui se compare mais ne s'énumère pas (#3350)")
                .isEqualTo(List.of("130711"));
    }

    @Test
    @DisplayName("#3159 : le carré se tape par son numéro, par son nom, ou tel que le refus l'écrit")
    void le_carre_se_tape_par_l_une_ou_l_autre_etiquette() {
        // La contrepartie de la ligne ci-dessus : nommer les lieux qualifiés ne doit obliger personne à
        // taper un point médian. La correspondance reste partielle, comme depuis #2971.
        assertThat(FiltresLieu.parLieu(List.of(AHETZE, VENELLES), List.of("640380")))
                .as("le numéro officiel, ce que tapent les scripts")
                .containsExactly(AHETZE);
        assertThat(FiltresLieu.parLieu(List.of(AHETZE, VENELLES), List.of("tuiliere")))
                .as("le nom convivial, sans accent ni casse : c'est ainsi qu'on retient son propre carré")
                .containsExactly(AHETZE);
        assertThat(FiltresLieu.parLieu(List.of(AHETZE, VENELLES), List.of("640380 · Étang de la Tuilière")))
                .as("et la forme qualifiée, recopiée depuis un refus ou depuis l'écran")
                .containsExactly(AHETZE);
    }

    /// Les lieux que le refus enumere, dans l ordre, tels qu on pourrait les recopier.
    ///
    /// Lire la liste plutot que de fouiller la chaine : une entree vide se voit alors comme une chaine
    /// vide dans le resultat, sans dependre du nombre de dimensions comparees.
    private static List<String> lieuxPresents(String message) {
        String marqueur = "Lieux présents (communes et carrés) : ";
        String liste = message.substring(message.indexOf(marqueur) + marqueur.length());
        return List.of(liste.substring(0, liste.length() - 1).split(", ", -1));
    }
}
