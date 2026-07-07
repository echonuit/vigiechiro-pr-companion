package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Écrivain JSON minimal du CLI (#614) : tableau d'objets plats, types et échappement.
class FormatJsonTest {

    @Test
    @DisplayName("Une liste vide donne un tableau JSON vide")
    void liste_vide() {
        assertThat(FormatJson.tableau(List.of())).isEqualTo("[]");
    }

    @Test
    @DisplayName("Chaînes, nombres, booléens et null sont sérialisés selon leur type")
    void types_serialises() {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("passage", 12L);
        objet.put("carre", "640380");
        objet.put("depose", true);
        objet.put("verdict", null);

        String json = FormatJson.tableau(List.of(objet));

        assertThat(json)
                .contains("\"passage\": 12")
                .contains("\"carre\": \"640380\"")
                .contains("\"depose\": true")
                .contains("\"verdict\": null");
    }

    @Test
    @DisplayName("Un objet unique est sérialisé en objet JSON, clés dans l'ordre d'insertion")
    void objet_unique() {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("passage", 7L);
        objet.put("statut", "Déposé");
        objet.put("resultatsTadarida", true);
        objet.put("cheminResultatsTadarida", null);

        String json = FormatJson.objet(objet);

        assertThat(json)
                .isEqualTo("{\"passage\": 7, \"statut\": \"Déposé\", \"resultatsTadarida\": true, "
                        + "\"cheminResultatsTadarida\": null}");
    }

    @Test
    @DisplayName("Les guillemets, antislash et sauts de ligne sont échappés")
    void echappement() {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("note", "cri « net » \\ retour\nligne");

        String json = FormatJson.tableau(List.of(objet));

        assertThat(json).contains("\\\\").contains("\\n");
        assertThat(json).doesNotContain("retour\nligne"); // le vrai saut de ligne est échappé, pas littéral
    }
}
