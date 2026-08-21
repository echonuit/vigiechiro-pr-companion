package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.viewmodel.IndicateurAccueil;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Contrat d'**apparence** de l'accueil : chaque carte d'activité et chaque compteur publié par une
/// feature doit exposer un code d'icône FontAwesome exploitable par Ikonli (préfixe `fas-/far-/fab-`)
/// et une couleur d'accent hexadécimale valide. Garde-fou : une feature qui livrerait un code ou une
/// couleur mal formés casserait silencieusement le rendu bâti par le socle ([MainController]).
class AccueilApparenceTest {

    /// Préfixe Ikonli FontAwesome 5 (`fas-`, `far-`, `fab-`) suivi d'un nom d'icône en kebab-case.
    private static final String CODE_FONTAWESOME = "^(fas|far|fab)-[a-z0-9-]+$";

    /// Couleur hexadécimale CSS sur six chiffres (ex. `#4a90d9`).
    private static final String COULEUR_HEX = "^#[0-9a-fA-F]{6}$";

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Chaque carte d'activité expose un code d'icône FontAwesome et une couleur hexadécimale")
    void activites_exposent_icone_et_couleur(@TempDir Path tmp) {
        Set<ActiviteAccueil> activites =
                injecteur(tmp).getInstance(Key.get(new TypeLiteral<Set<ActiviteAccueil>>() {}));

        assertThat(activites).isNotEmpty();
        for (ActiviteAccueil activite : activites) {
            String nom = activite.getClass().getSimpleName();
            assertThat(activite.iconeLiteral()).as("icône de %s", nom).matches(CODE_FONTAWESOME);
            assertThat(activite.couleur()).as("couleur de %s", nom).matches(COULEUR_HEX);
            assertThat(activite.prisme()).as("prisme de %s", nom).isNotNull();
        }
    }

    @Test
    @DisplayName("Chaque prisme expose un intitulé et un code d'icône FontAwesome (en-tête de section)")
    void prismes_exposent_libelle_et_icone() {
        for (Prisme prisme : Prisme.values()) {
            assertThat(prisme.libelle()).as("intitulé de %s", prisme).isNotBlank();
            assertThat(prisme.iconeLiteral()).as("icône de %s", prisme).matches(CODE_FONTAWESOME);
        }
    }

    @Test
    @DisplayName("Chaque compteur du tableau de bord expose un code d'icône FontAwesome et une couleur hexadécimale")
    void compteurs_exposent_icone_et_couleur(@TempDir Path tmp) {
        Set<IndicateurAccueil> indicateurs =
                injecteur(tmp).getInstance(Key.get(new TypeLiteral<Set<IndicateurAccueil>>() {}));

        assertThat(indicateurs).isNotEmpty();
        for (IndicateurAccueil indicateur : indicateurs) {
            String nom = indicateur.getClass().getSimpleName();
            assertThat(indicateur.iconeLiteral()).as("icône de %s", nom).matches(CODE_FONTAWESOME);
            assertThat(indicateur.couleur()).as("couleur de %s", nom).matches(COULEUR_HEX);
        }
    }

    private Injector injecteur(Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        return RacineInjecteur.creer();
    }
}
