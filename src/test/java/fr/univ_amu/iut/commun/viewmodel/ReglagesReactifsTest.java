package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.nio.file.Path;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Couche réactive [ReglagesReactifs] au-dessus d'un [Reglages] réel sur base jetable. On vérifie
/// l'initialisation (depuis le défaut puis depuis la valeur persistée), le **write-through** (toute
/// modification de la Property est réécrite et persistée) et le **partage par clé** (deux appels sur
/// la même clé renvoient la même instance, donc des contrôles synchronisés).
///
/// Pas de TestFX : les `Property` JavaFX et leurs listeners fonctionnent hors du fil applicatif.
class ReglagesReactifsTest {

    private static final String CLE = "general.exemple";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private Reglages reglages;
    private ReglagesReactifs reactifs;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        reglages = new Reglages(new ReglagesDao(source));
        reactifs = new ReglagesReactifs(reglages);
    }

    @Test
    @DisplayName("une clé jamais écrite s'initialise sur le défaut")
    void initialisation_sur_le_defaut() {
        assertThat(reactifs.proprieteBooleen(CLE, true).get()).isTrue();
    }

    @Test
    @DisplayName("une clé déjà persistée s'initialise sur la valeur stockée")
    void initialisation_sur_la_valeur_persistee() {
        reglages.ecrireBooleen(CLE, true);

        ReglagesReactifs autre = new ReglagesReactifs(reglages);
        assertThat(autre.proprieteBooleen(CLE, false).get()).isTrue();
    }

    @Test
    @DisplayName("modifier la Property réécrit et persiste le réglage (write-through)")
    void write_through_persiste() {
        reactifs.proprieteBooleen(CLE, false).set(true);

        assertThat(reglages.lireBooleen(CLE, false)).isTrue();
        // Persistance réelle : une instance neuve, branchée sur la même base, relit true.
        Reglages relecture = new Reglages(new ReglagesDao(source));
        assertThat(relecture.lireBooleen(CLE, false)).isTrue();
    }

    @Test
    @DisplayName("deux appels sur la même clé renvoient la même Property (synchro)")
    void meme_cle_meme_propriete() {
        BooleanProperty premiere = reactifs.proprieteBooleen(CLE, false);
        BooleanProperty seconde = reactifs.proprieteBooleen(CLE, false);

        assertThat(seconde).isSameAs(premiere);
        premiere.set(true);
        assertThat(seconde.get()).isTrue();
    }

    @Test
    @DisplayName("write-through du texte et de l'entier")
    void write_through_texte_et_entier() {
        StringProperty texte = reactifs.proprieteTexte(CLE + ".txt", "defaut");
        texte.set("wikipedia");
        assertThat(reglages.lireTexte(CLE + ".txt", "defaut")).isEqualTo("wikipedia");

        IntegerProperty entier = reactifs.proprieteEntier(CLE + ".int", 0);
        entier.set(7);
        assertThat(reglages.lireEntier(CLE + ".int", 0)).isEqualTo(7);
    }
}
