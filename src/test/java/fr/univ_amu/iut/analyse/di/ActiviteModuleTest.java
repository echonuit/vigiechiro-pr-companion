package fr.univ_amu.iut.analyse.di;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import org.junit.jupiter.api.Test;

/// Épingle l'identité de la feature **Activité de la nuit** ([ActiviteModule]) : son identifiant, qui sert
/// de clé au réglage persisté (`feature.activite-nuit.active`) et à la propriété d'activation, et sa
/// catégorie `OPTIONNELLE` : désactivable, mais **offerte par défaut** depuis la clôture du lot #2352.
///
/// Un retour silencieux à `EXPERIMENTALE` masquerait l'écran pour tout le monde sans que rien ne rougisse
/// (patron de `SaisonModuleTest` et `CampagneModuleTest`).
class ActiviteModuleTest {

    @Test
    void la_feature_est_optionnelle_donc_offerte_par_defaut() {
        Fonctionnalite fonctionnalite = new ActiviteModule().fonctionnalite();

        assertThat(fonctionnalite.id()).isEqualTo("activite-nuit");
        assertThat(fonctionnalite.libelle()).isEqualTo("Activité de la nuit");
        assertThat(fonctionnalite.categorie()).isEqualTo(Categorie.OPTIONNELLE);
        assertThat(fonctionnalite.categorie().activeParDefaut()).isTrue();
        assertThat(fonctionnalite.categorie().desactivable())
                .as("l'écran reste débrayable : il n'est pas du socle")
                .isTrue();
    }
}
