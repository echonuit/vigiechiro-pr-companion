package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.importation.model.EtatNommage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// L'état du nommage dit ce qu'on va **faire** des fichiers, pas ce qu'ils **sont** (#1487).
///
/// ## Pourquoi ce libellé porte à lui seul une inquiétude
///
/// « Fichiers bruts (seront renommés) » se lit « on va renommer MES fichiers ». Ce sont les copies. La
/// phrase qui le disait a rejoint les réglages avancés avec la case « Conserver les originaux » : elle
/// est parfaite, et l'utilisateur inquiet ne l'ouvrira jamais. Ce libellé-ci est le seul qu'il lira.
class FormatsImportTest {

    @Nested
    @DisplayName("Ce que l'écran annonce")
    class Annonce {

        @Test
        @DisplayName("#1487 : des fichiers bruts seront copiés, renommés et transformés")
        void bruts_annoncent_les_trois_operations() {
            String libelle = FormatsImport.libelleNommage(EtatNommage.BRUT);

            // « copiés » est le mot qui lève la crainte : il dit que l'original n'est pas touché. Les
            // trois opérations sont nommées dans l'ordre où elles se produisent.
            assertThat(libelle).contains("copi");
            assertThat(libelle).contains("renomm");
            assertThat(libelle).contains("transform");
        }

        @Test
        @DisplayName("#1487 : des fichiers déjà préfixés sont copiés et transformés, pas renommés")
        void prefixes_ne_promettent_pas_de_renommage() {
            String libelle = FormatsImport.libelleNommage(EtatNommage.PREFIXE);

            // Le jumeau du défaut : « fichiers déjà préfixés » constate un état, là où l'utilisateur
            // veut savoir ce qu'on fera de ses fichiers. Mais ils portent déjà le préfixe : annoncer un
            // renommage remplacerait une phrase trompeuse par une autre.
            assertThat(libelle).contains("copi");
            assertThat(libelle).contains("transform");
            assertThat(libelle).doesNotContain("renomm");
        }

        @Test
        @DisplayName("Aucun fichier : rien n'est promis")
        void vide_ne_promet_rien() {
            String libelle = FormatsImport.libelleNommage(EtatNommage.VIDE);

            assertThat(libelle).isEqualTo("aucun fichier");
        }

        @Test
        @DisplayName("État inconnu : la valeur absente du socle, pas une phrase inventée")
        void inconnu_rend_la_valeur_absente() {
            assertThat(FormatsImport.libelleNommage(null)).isEqualTo(Formats.VALEUR_ABSENTE);
        }
    }
}
