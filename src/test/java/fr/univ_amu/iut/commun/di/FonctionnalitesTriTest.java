package fr.univ_amu.iut.commun.di;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Collator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'ordre dans lequel l'onglet **Fonctionnalités** des Réglages présente ses interrupteurs (#3833).
///
/// Il se lisait comme aléatoire, parce qu'il portait sur l'**identifiant technique** - une chaîne que
/// l'utilisateur ne voit nulle part. Les identifiants se suivaient parfaitement (`campagne`,
/// `carre-existant`, `controle-carre-stoc`), mais leurs libellés n'ont aucune raison de suivre le même
/// ordre : « Vérifier et récupérer un carré » se rangeait entre « Campagnes de suivi » et « Contrôle du
/// carré STOC ».
///
/// Le déterminisme reste une **exigence** et non un confort : l'aperçu
/// `apercu-reglages-fonctionnalites.png` est régénéré à chaque build, et deux exécutions doivent rendre
/// le même PNG au bit près. Trier sur le libellé est tout aussi déterministe - les libellés sont des
/// constantes du code - et donne en plus l'ordre attendu.
class FonctionnalitesTriTest {

    @Test
    @DisplayName("#3833 : les fonctionnalités se lisent dans l'ordre de leurs libellés")
    void les_fonctionnalites_sont_triees_sur_ce_qu_on_lit() {
        List<String> libelles =
                Fonctionnalites.toutes().stream().map(Fonctionnalite::libelle).toList();

        // Non-vacuité : un catalogue vide serait trié par accident, et ce test passerait sans rien dire.
        assertThat(libelles)
                .as("le catalogue est vide : c'est le ServiceLoader qui est cassé, pas le tri")
                .isNotEmpty();
        assertThat(libelles).isSortedAccordingTo(Collator.getInstance(Locale.FRENCH)::compare);
    }

    @Test
    @DisplayName("#3833 : un libellé accentué se range à sa lettre, pas après le Z")
    void un_accent_ne_part_pas_a_la_fin() {
        // C'est ce que `String::compareTo` fait : 'É' (U+00C9) est au-delà de 'Z' (U+005A), donc
        // « Étang » se rangerait APRÈS « Zone ». Un `Collator` français le remet à sa place.
        List<Fonctionnalite> melange = List.of(
                fonctionnalite("z", "Zone humide"),
                fonctionnalite("e", "Étang de la Tuilière"),
                fonctionnalite("a", "Activité de la nuit"));

        List<String> triees = melange.stream()
                .sorted(Fonctionnalites.parLibelle())
                .map(Fonctionnalite::libelle)
                .toList();

        assertThat(triees).containsExactly("Activité de la nuit", "Étang de la Tuilière", "Zone humide");
    }

    private static Fonctionnalite fonctionnalite(String id, String libelle) {
        return new Fonctionnalite(id, libelle, Categorie.OPTIONNELLE);
    }
}
