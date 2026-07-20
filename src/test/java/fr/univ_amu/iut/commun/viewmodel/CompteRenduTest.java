package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le **compte rendu** comme structure (ADR 0031), par opposition au texte assemblé qu'il remplace.
///
/// Ce que ces tests tiennent, et qu'un `StringBuilder` ne permettait pas de vérifier autrement qu'en
/// cherchant des sous-chaînes dans un pavé : la sévérité de l'ensemble se déduit des constats, les détails
/// restent **tous** dans le modèle (le plafond appartient à la surface), et sujet et précision ne se
/// fondent pas.
class CompteRenduTest {

    @Test
    @DisplayName("La sévérité de l'ensemble est la plus forte de ses constats")
    void severite_la_plus_forte() {
        CompteRendu rendu = CompteRendu.de(
                "Réactivation partielle",
                List.of(
                        Constat.de("4229 séquence(s) réactivée(s).", Severite.SUCCES),
                        Constat.de("7 séquence(s) restent introuvables.", Severite.ERREUR)));

        assertThat(rendu.severite())
                .as("un compte rendu qui contient un échec ne se présente pas comme un succès")
                .isEqualTo(Severite.ERREUR);
    }

    @Test
    @DisplayName("Sans constat, la sévérité reste neutre plutôt que d'échouer")
    void severite_neutre_sans_constat() {
        assertThat(CompteRendu.de("Rien à signaler", List.of()).severite()).isEqualTo(Severite.INFO);
    }

    @Test
    @DisplayName("Le modèle porte TOUS les détails : le plafond d'affichage appartient à la surface")
    void tous_les_details_sont_portes() {
        List<Detail> cent = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> new Detail("fichier-" + i + ".wav", "enregistrement absent du dossier"))
                .toList();

        CompteRendu rendu = CompteRendu.de(
                "Réactivation partielle", List.of(new Constat("100 introuvables.", Severite.ERREUR, cent)));

        assertThat(rendu.constats().getFirst().details())
                .as("la modale en montrera cinq, la CLI toutes : c'est leur affaire, pas celle du modèle")
                .hasSize(100);
    }

    @Test
    @DisplayName("Sujet et précision restent distincts : l'un identifie, l'autre explique")
    void sujet_et_precision_ne_se_fondent_pas() {
        Detail detail = new Detail("PaRecPR1997632_20260704_223507.wav", "enregistrement absent du dossier");

        assertThat(detail.sujet())
                .as("ce que l'utilisateur ira chercher dans son dossier")
                .doesNotContain("absent");
        assertThat(detail.precision())
                .as("ce qui lui dit si la balle est dans son camp ou dans le nôtre")
                .isEqualTo("enregistrement absent du dossier");
    }

    @Test
    @DisplayName("Un compte rendu vide se reconnaît : mieux vaut ne rien afficher qu'un cadre vide")
    void compte_rendu_vide() {
        assertThat(CompteRendu.de("", List.of()).estVide()).isTrue();
        assertThat(CompteRendu.de("Passage réactivé", List.of()).estVide()).isFalse();
    }

    @Test
    @DisplayName("Le modèle est immuable : une liste modifiée après coup ne change pas le compte rendu")
    void immuable() {
        List<Constat> mutable = new java.util.ArrayList<>(List.of(Constat.de("Un fait.", Severite.INFO)));
        CompteRendu rendu = CompteRendu.de("Titre", mutable);

        mutable.add(Constat.de("Ajouté après coup.", Severite.ERREUR));

        assertThat(rendu.constats()).hasSize(1);
        assertThat(rendu.severite())
                .as("un compte rendu déjà publié ne change pas de sévérité dans le dos de la vue")
                .isEqualTo(Severite.INFO);
    }

    @Test
    @DisplayName("Les composants obligatoires sont exigés, plutôt que de rendre un « null » à l'écran")
    void champs_obligatoires() {
        assertThatThrownBy(() -> new CompteRendu(null, "", List.of(), "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Constat("fait", null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Detail("sujet", null)).isInstanceOf(NullPointerException.class);
    }
}
