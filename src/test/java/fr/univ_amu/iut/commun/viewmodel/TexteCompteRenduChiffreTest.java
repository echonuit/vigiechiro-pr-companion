package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Mise à plat d'un compte rendu chiffré, repli du port [fr.univ_amu.iut.commun.view.Notificateur].
///
/// Ce repli existe pour qu'une surface qui ne dessine pas de barre **ne cesse pas de rendre compte**.
/// Il est donc éprouvé sur ce qu'il doit préserver : les nombres, et le rapport qu'ils forment.
class TexteCompteRenduChiffreTest {

    @Test
    @DisplayName("le repli garde les nombres ET leur proportion, qu'il ne peut plus montrer")
    void le_repli_garde_les_nombres_et_leur_proportion() {
        String texte = TexteCompteRenduChiffre.rendre(new CompteRenduChiffre(
                "Préparer le dépôt",
                "14 / 20 traités",
                Severite.AVERTISSEMENT,
                List.of(),
                new Ventilation(
                        "passages soumis",
                        20,
                        List.of(
                                new Segment("Traités", 14, "14 passage(s)", Teinte.RETENU),
                                new Segment("En échec", 6, "6 passage(s)", Teinte.REFUSE))),
                List.of(new Motif("échec : la plateforme a refusé", List.of("640380 / A1", "640380 / B2"))),
                List.of(Avertissement.de("Relancer ne reprendra que les 6 passage(s) restants.")),
                List.of()));

        // Le pourcentage est ce que le texte peut offrir à la place d'une barre : un rapport que le lecteur
        // n'a pas à calculer. Sans lui, le repli perdrait la proportion, pas seulement sa forme visuelle.
        assertThat(texte)
                .contains("Préparer le dépôt - 14 / 20 traités")
                .contains("Traités : 14 passage(s) (70,0 %)")
                .contains("En échec : 6 passage(s) (30,0 %)")
                .contains("échec : la plateforme a refusé (2)")
                .contains("640380 / A1")
                .contains("[!] Relancer ne reprendra que les 6 passage(s) restants.");
    }

    @Test
    @DisplayName("un total nul ne produit pas de pourcentage : rien à rapporter à rien")
    void un_total_nul_ne_produit_pas_de_pourcentage() {
        String texte = TexteCompteRenduChiffre.rendre(new CompteRenduChiffre(
                "Rien à faire",
                "0 traités",
                Severite.INFO,
                List.of(),
                new Ventilation("passages soumis", 0, List.of()),
                List.of(),
                List.of(),
                List.of()));

        // Sans ce garde, une division par zéro produirait « NaN % » dans un compte rendu.
        assertThat(texte).isEqualTo("Rien à faire - 0 traités").doesNotContain("%");
    }
}
