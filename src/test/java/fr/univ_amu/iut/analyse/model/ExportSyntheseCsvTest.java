package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.ClasseActivite;
import fr.univ_amu.iut.commun.model.ConfianceReferentiel;
import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.model.SaisonActivite;
import fr.univ_amu.iut.commun.model.SeuilsActivite;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie que le CSV de synthèse **emporte son contexte** (#2351).
///
/// C'est l'enjeu de cette tranche, et il n'est pas cosmétique : un CSV quitte l'application pour vivre
/// dans un tableur, loin de l'écran qui portait la mise en garde. Un fichier ouvert trois mois plus tard
/// par quelqu'un qui n'a jamais vu l'écran doit pouvoir savoir d'où sortent ces classes et ce qu'elles
/// valent — sans quoi l'avertissement ne sert à rien.
class ExportSyntheseCsvTest {

    private static ContexteActivite contexte() {
        return new ContexteActivite(Optional.of(SaisonActivite.ETE), Optional.of("Corse"), Optional.of("Foret"));
    }

    private static LigneSynthese ligne(String code, int contacts, ClasseActivite classe, boolean couvert) {
        SeuilsActivite seuils =
                new SeuilsActivite(12, 480, 1240, 8600, ConfianceReferentiel.TRES_BONNE, "habitat:Foret", "ete");
        return new LigneSynthese(
                code,
                code + " (nom)",
                "Chiroptères",
                contacts,
                contacts / 2,
                Optional.ofNullable(classe),
                classe == null ? Optional.empty() : Optional.of(seuils),
                couvert);
    }

    @Test
    @DisplayName("La citation et l'avertissement sont RECOPIÉS en tête de fichier")
    void citation_et_avertissement_recopies() {
        String csv = ExportSyntheseCsv.contenu(List.of(ligne("Pipkuh", 718, ClasseActivite.FORTE, true)), contexte());

        assertThat(csv)
                .as("la source est libre d'usage AVEC citation obligatoire : elle doit voyager avec le fichier")
                .contains("Bas Y.", "2020", "Muséum national d'Histoire naturelle");
        assertThat(csv)
                .as("un avertissement resté à l'écran ne prévient personne qui ouvre le CSV")
                .contains("n'est pas un niveau d'enjeu de conservation");
    }

    @Test
    @DisplayName("Le contexte de comparaison est écrit : sans lui, les classes seraient des oracles")
    void contexte_ecrit() {
        String csv = ExportSyntheseCsv.contenu(List.of(ligne("Pipkuh", 718, ClasseActivite.FORTE, true)), contexte());

        assertThat(csv)
                .as("le fichier dit à quoi les nombres ont été comparés, en français comme l'écran")
                .contains("Comparé au référentiel : milieu Foret");
    }

    @Test
    @DisplayName("Les lignes de contexte sont préfixées par # : lisibles par l'humain, sautables par un script")
    void contexte_commente() {
        String csv = ExportSyntheseCsv.contenu(List.of(), contexte());

        String[] lignes = csv.split("\\R");
        assertThat(lignes[0]).startsWith("\uFEFF# ");
        assertThat(java.util.Arrays.stream(lignes).filter(l -> !l.startsWith("\uFEFF#") && !l.startsWith("#")))
                .as("une seule ligne non commentée : les en-têtes de colonnes")
                .hasSize(1);
    }

    @Test
    @DisplayName("Une nuit sans espèce produit le contexte et les en-têtes, pas un fichier vide")
    void nuit_vide() {
        String csv = ExportSyntheseCsv.contenu(List.of(), contexte());

        assertThat(csv).contains("Code espèce", "Activité", "Q98");
        assertThat(csv).contains("Bas Y.");
    }

    @Test
    @DisplayName("Une espèce hors référentiel porte son motif, et ses colonnes de seuils restent vides")
    void hors_referentiel() {
        // Dans un CSV, une cellule vide se lit comme une absence de donnée — ce qui est exactement le cas
        // pour les quantiles. La colonne « Activité », elle, DIT pourquoi.
        String csv = ExportSyntheseCsv.contenu(List.of(ligne("Tetvir", 244, null, false)), contexte());

        String ligneEspece =
                csv.lines().filter(l -> l.startsWith("Tetvir")).findFirst().orElseThrow();
        assertThat(ligneEspece).contains("Non couvert par le référentiel");
        assertThat(ligneEspece).endsWith(";;;;;;");
    }

    @Test
    @DisplayName("Les quantiles accompagnent la classe, comme à l'écran")
    void quantiles_exportes() {
        String csv = ExportSyntheseCsv.contenu(List.of(ligne("Pipkuh", 718, ClasseActivite.FORTE, true)), contexte());

        String ligneEspece =
                csv.lines().filter(l -> l.startsWith("Pipkuh")).findFirst().orElseThrow();
        assertThat(ligneEspece).contains("Forte", "12", "480", "1240", "habitat:Foret", "ete", "TRES_BONNE");
    }
}
