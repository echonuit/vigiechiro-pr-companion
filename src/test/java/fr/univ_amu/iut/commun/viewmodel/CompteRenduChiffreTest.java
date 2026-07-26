package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Barre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Modèle du **compte rendu chiffré** (#2358, maquette M-CompteRendu) : les trois règles de conception y
/// sont tenues par le type, pas par la vigilance de l'appelant. Fonctions pures, aucun toolkit.
///
/// Les chiffres repris ici sont ceux de la maquette (612 enregistrements : 583 importés, 21 déjà
/// présents, 8 rejetés ; 5,0 Go lus, 6,8 Go écrits), pour que la vérification de l'une vaille pour l'autre.
class CompteRenduChiffreTest {

    private static Segment seg(String libelle, long quantite) {
        return new Segment(libelle, quantite, quantite + " fichiers");
    }

    @Test
    @DisplayName("règle 2 : une ventilation dont les segments ne font pas le total est REFUSÉE")
    void ventilation_non_exhaustive_refusee() {
        // 583 + 21 = 604 pour 612 : les 8 rejetés manquent. Le modèle refuse plutôt que d'inventer un
        // « autres » muet, ce qui contraint l'appelant à nommer le reliquat.
        assertThatThrownBy(
                        () -> new Ventilation("Devenir", 612, List.of(seg("Importés", 583), seg("Déjà présents", 21))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non exhaustive")
                .as("le message chiffre le reliquat pour qu'il n'y ait rien à deviner")
                .hasMessageContaining("604")
                .hasMessageContaining("612")
                .hasMessageContaining("8");
    }

    @Test
    @DisplayName("règle 2 : la ventilation complète est acceptée, et une ventilation vide reste licite")
    void ventilation_exhaustive_acceptee() {
        Ventilation complete = new Ventilation(
                "Devenir", 612, List.of(seg("Importés", 583), seg("Déjà présents", 21), seg("Rejetés", 8)));

        assertThat(complete.estVide()).isFalse();
        assertThat(complete.segments()).hasSize(3);

        // Aucune ventilation à montrer : licite, et le bloc se masquera.
        assertThat(Ventilation.aucune().estVide()).isTrue();
    }

    @Test
    @DisplayName("règle 1 : les fractions sortent des quantités réelles, jamais d'une impression")
    void fractions_a_l_echelle() {
        Segment importes = seg("Importés", 583);
        Segment rejetes = seg("Rejetés", 8);
        Ventilation devenir = new Ventilation("Devenir", 612, List.of(importes, seg("Déjà présents", 21), rejetes));

        assertThat(devenir.fraction(importes)).isCloseTo(583d / 612, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(devenir.fraction(rejetes)).isCloseTo(8d / 612, org.assertj.core.data.Offset.offset(1e-9));
        // Les largeurs de la maquette (840 px) découlent de ces fractions, pas d'un ajustement à la main.
        assertThat(devenir.fraction(importes) * 840).isCloseTo(800.2, org.assertj.core.data.Offset.offset(0.05));
        assertThat(devenir.fraction(rejetes) * 840).isCloseTo(11.0, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    @DisplayName("un ensemble vide ne divise pas par zéro : fraction nulle, pas d'exception")
    void ensemble_vide_ne_divise_pas_par_zero() {
        Segment rien = seg("Rien", 0);
        assertThat(new Ventilation("Devenir", 0, List.of(rien)).fraction(rien)).isZero();
        assertThat(new Barre("Écrit", List.of(rien)).fraction(rien)).isZero();
    }

    @Test
    @DisplayName("les pourcentages sont au DIXIÈME : à l'unité, cet import se lirait 99 %")
    void pourcentages_au_dixieme() {
        Segment importes = seg("Importés", 583);
        Segment presents = seg("Déjà présents", 21);
        Segment rejetes = seg("Rejetés", 8);
        Ventilation devenir = new Ventilation("Devenir", 612, List.of(importes, presents, rejetes));

        assertThat(devenir.pourcentage(importes)).isEqualTo(95.3);
        assertThat(devenir.pourcentage(presents)).isEqualTo(3.4);
        assertThat(devenir.pourcentage(rejetes)).isEqualTo(1.3);

        double somme = devenir.pourcentage(importes) + devenir.pourcentage(presents) + devenir.pourcentage(rejetes);
        assertThat(somme)
                .as("au dixième la somme retombe sur 100,0 ; à l'unité elle donnerait 99")
                .isEqualTo(100.0);
        assertThat(Math.round(95.26) + Math.round(3.43) + Math.round(1.31))
                .as("le défaut qu'on évite, gardé sous les yeux")
                .isEqualTo(99);
    }

    @Test
    @DisplayName("règle 1 : les barres de volume partagent une ÉCHELLE COMMUNE, la plus grande du lot")
    void echelle_commune_des_volumes() {
        Barre lu = Barre.unique("Lu sur la carte", new Segment("lu", 5_000, "5,0 Go"));
        Barre ecrit = new Barre(
                "Écrit sur le disque",
                List.of(new Segment("bruts", 5_000, "5,0 Go"), new Segment("séquences", 1_800, "1,8 Go")));
        CompteRenduChiffre rendu = new CompteRenduChiffre(
                "Import terminé",
                "583 / 612 importés",
                Severite.SUCCES,
                List.of(lu, ecrit),
                Ventilation.aucune(),
                List.of(),
                List.of(),
                List.of());

        // L'échelle est celle de la plus grande barre : « lu » n'occupe donc pas toute la largeur, sinon
        // 5,0 Go et 6,8 Go paraîtraient égaux (le défaut exact de la maquette précédente).
        assertThat(rendu.echelleDesVolumes()).isEqualTo(6_800);
        assertThat(lu.total()).isEqualTo(5_000);
        assertThat(ecrit.total()).isEqualTo(6_800);
        // Mêmes px/unité de part et d'autre, pour une largeur utile donnée.
        double largeur = 600;
        double pxParUnite = largeur / rendu.echelleDesVolumes();
        assertThat(lu.total() * pxParUnite).isCloseTo(441.2, org.assertj.core.data.Offset.offset(0.05));
        assertThat(1_800 * pxParUnite).isCloseTo(158.8, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    @DisplayName("une quantité négative est refusée : elle rendrait toute proportion absurde")
    void quantite_negative_refusee() {
        assertThatThrownBy(() -> new Segment("Importés", -1, "?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Importés");
    }

    @Test
    @DisplayName("le résumé des motifs dit de quoi il s'agit sans dérouler la liste des fichiers")
    void resume_des_motifs() {
        CompteRenduChiffre rendu = new CompteRenduChiffre(
                "Import terminé",
                "583 / 612 importés",
                Severite.AVERTISSEMENT,
                List.of(),
                Ventilation.aucune(),
                List.of(
                        new Motif(
                                "fichier déjà expansé", List.of("a.wav", "b.wav", "c.wav", "d.wav", "e.wav", "f.wav")),
                        new Motif("en-tête WAV illisible", List.of("g.wav", "h.wav"))),
                List.of(),
                List.of());

        assertThat(rendu.resumeDesMotifs()).isEqualTo("6 fichier déjà expansé, 2 en-tête WAV illisible");
        assertThat(rendu.nombreDeSujetsMotives()).isEqualTo(8);
        // Sans motif, le résumé est vide : la surface n'affiche alors aucune ligne de pied.
        assertThat(rendu.motifs().get(0).compte()).isEqualTo(6);
    }

    @Test
    @DisplayName("un compte rendu sans rien à signaler est licite : tous ses blocs sont vides")
    void compte_rendu_sans_rien_a_signaler() {
        Segment tout = seg("Importés", 584);
        CompteRenduChiffre rendu = new CompteRenduChiffre(
                "Import terminé",
                "584 / 584 importés",
                Severite.SUCCES,
                List.of(),
                new Ventilation("Devenir", 584, List.of(tout)),
                List.of(),
                List.of(),
                List.of(new Action("Ouvrir le passage", true, () -> {})));

        assertThat(rendu.motifs()).isEmpty();
        assertThat(rendu.avertissements()).isEmpty();
        assertThat(rendu.resumeDesMotifs()).isEmpty();
        assertThat(rendu.ventilation().pourcentage(tout)).isEqualTo(100.0);
        assertThat(rendu.actions()).extracting(Action::principale).containsExactly(true);
    }

    @Test
    @DisplayName("les collections sont recopiées : un compte rendu ne change pas dans le dos de sa surface")
    void collections_recopiees() {
        List<String> avertissements = new java.util.ArrayList<>(List.of("Relevé climatique absent"));
        CompteRenduChiffre rendu = new CompteRenduChiffre(
                "Import terminé",
                "584 / 584",
                Severite.INFO,
                List.of(),
                Ventilation.aucune(),
                List.of(),
                avertissements,
                List.of());

        avertissements.add("ajouté après coup");

        assertThat(rendu.avertissements()).containsExactly("Relevé climatique absent");
    }
}
