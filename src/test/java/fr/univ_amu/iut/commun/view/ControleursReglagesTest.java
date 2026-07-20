package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.DescripteurReglage.Enumeration.Option;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;

/// Fabrique de contrôles [ControleursReglages] : chaque [DescripteurReglage] produit le bon contrôle,
/// câblé (bidirectionnel) au réglage persistant via [ReglagesReactifs]. [ApplicationExtension]
/// initialise le toolkit JavaFX (construction des contrôles) ; aucune scène affichée.
@ExtendWith(ApplicationExtension.class)
class ControleursReglagesTest {

    @TempDir
    Path dossier;

    private Reglages reglages;
    private ReglagesReactifs reactifs;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        reglages = new Reglages(new ReglagesDao(source));
        reactifs = new ReglagesReactifs(reglages);
    }

    @Test
    @DisplayName("un descripteur booléen produit une case à cocher liée et persistante")
    void booleen_produit_une_case_liee() {
        Control controle = ControleursReglages.controle(
                new DescripteurReglage.Booleen("t.bool", "Conserver les originaux", "aide", false), reactifs);

        assertThat(controle).isInstanceOf(CheckBox.class);
        CheckBox coche = (CheckBox) controle;
        assertThat(coche.getText()).isEqualTo("Conserver les originaux");
        assertThat(coche.isSelected()).isFalse();
        assertThat(coche.getTooltip())
                .as("l'aide n'est plus une infobulle : elle est rendue sous le contrôle (#2085)")
                .isNull();

        coche.setSelected(true);
        assertThat(reglages.lireBooleen("t.bool", false)).isTrue();
    }

    @Test
    @DisplayName("#2085 : l'aide est rendue VISIBLE sous le contrôle, plus en infobulle")
    void l_aide_est_visible_sous_le_controle() {
        // Un texte qu'il faut survoler pour lire n'est pas lu : c'est un geste qu'on ne fait pas si on
        // ignore qu'il y a quelque chose à lire, et qui n'existe pas au tactile. Or plusieurs réglages
        // portent dans leur aide un arbitrage (ADR 0034, ADR 0036) que l'utilisateur doit connaître
        // AVANT de choisir.
        OngletReglages onglet =
                onglet(List.of(new DescripteurReglage.Booleen("a.bool", "Case", "Ce que ça engage.", false)));

        VBox ligne = (VBox) ControleursReglages.formulaire(onglet, reactifs)
                .getChildrenUnmodifiable()
                .get(0);

        assertThat(ligne.getChildren()).as("la case, puis son aide").hasSize(2);
        Label aide = (Label) ligne.getChildren().get(1);
        assertThat(aide.getText()).isEqualTo("Ce que ça engage.");
        assertThat(aide.isWrapText())
                .as("une aide se lit en entier, elle ne s'élide pas")
                .isTrue();
        assertThat(aide.getStyleClass()).contains("reglage-aide");
    }

    @Test
    @DisplayName("#2085 : sans aide déclarée, aucune ligne vide n'est ajoutée")
    void sans_aide_aucun_libelle_vide() {
        OngletReglages onglet = onglet(List.of(new DescripteurReglage.Booleen("t.bool2", "Sans aide", "", false)));

        VBox ligne = (VBox) ControleursReglages.formulaire(onglet, reactifs)
                .getChildrenUnmodifiable()
                .get(0);

        assertThat(ligne.getChildren()).hasSize(1);
    }

    @Test
    @DisplayName("un descripteur texte produit un champ lié bidirectionnellement")
    void texte_produit_un_champ_lie() {
        Control controle =
                ControleursReglages.controle(new DescripteurReglage.Texte("t.txt", "Nom", "", "defaut"), reactifs);

        assertThat(controle).isInstanceOf(TextField.class);
        TextField champ = (TextField) controle;
        assertThat(champ.getText()).isEqualTo("defaut");

        champ.setText("modifie");
        assertThat(reglages.lireTexte("t.txt", "defaut")).isEqualTo("modifie");
    }

    @Test
    @DisplayName("un descripteur entier produit un spinner borné et persistant")
    void entier_produit_un_spinner() {
        Control controle =
                ControleursReglages.controle(new DescripteurReglage.Entier("t.int", "Seuil", "", 3, 0, 10), reactifs);

        assertThat(controle).isInstanceOf(Spinner.class);
        @SuppressWarnings("unchecked")
        Spinner<Integer> spinner = (Spinner<Integer>) controle;
        assertThat(spinner.getValue()).isEqualTo(3);

        spinner.getValueFactory().setValue(7);
        assertThat(reglages.lireEntier("t.int", 0)).isEqualTo(7);
    }

    @Test
    @DisplayName("un descripteur énuméré produit une liste déroulante liée à la valeur persistée")
    void enumeration_produit_une_liste() {
        List<Option> options = List.of(new Option("gbif", "GBIF"), new Option("wikipedia", "Wikipédia"));
        Control controle = ControleursReglages.controle(
                new DescripteurReglage.Enumeration("t.enum", "Source", "", options, "gbif"), reactifs);

        assertThat(controle).isInstanceOf(ComboBox.class);
        @SuppressWarnings("unchecked")
        ComboBox<Option> combo = (ComboBox<Option>) controle;
        assertThat(combo.getSelectionModel().getSelectedItem().valeur()).isEqualTo("gbif");

        combo.getSelectionModel().select(options.get(1));
        assertThat(reglages.lireTexte("t.enum", "gbif")).isEqualTo("wikipedia");
    }

    @Test
    @DisplayName("le formulaire construit une ligne par réglage (la case porte son propre libellé)")
    void formulaire_une_ligne_par_reglage() {
        OngletReglages onglet = onglet(List.of(
                new DescripteurReglage.Booleen("f.bool", "Case", "", false),
                new DescripteurReglage.Texte("f.txt", "Champ", "", "")));

        Region formulaire = ControleursReglages.formulaire(onglet, reactifs);

        assertThat(((VBox) formulaire).getChildren()).hasSize(2);
        // Chaque réglage est désormais enveloppé dans sa ligne, y compris la case : c'est elle qui porte
        // l'aide sous le contrôle (#2085).
        VBox premiere = (VBox) ((VBox) formulaire).getChildren().get(0);
        assertThat(premiere.getChildren()).singleElement().isInstanceOf(CheckBox.class);
    }

    @Test
    @DisplayName("un onglet personnalisé ajoute son nœud custom après les contrôles générés")
    void formulaire_ajoute_le_noeud_personnalise() {
        Label custom = new Label("aperçu");
        OngletReglagesPersonnalise onglet =
                ongletPersonnalise(List.of(new DescripteurReglage.Booleen("p.bool", "Case", "", false)), custom);

        Region formulaire = ControleursReglages.formulaire(onglet, reactifs);

        List<Node> enfants = ((VBox) formulaire).getChildren();
        assertThat(enfants).hasSize(2);
        assertThat(enfants.get(1)).isSameAs(custom);
    }

    private static OngletReglages onglet(List<DescripteurReglage> reglages) {
        return new OngletReglages() {
            @Override
            public String idFeature() {
                return "test";
            }

            @Override
            public int ordre() {
                return 0;
            }

            @Override
            public String titre() {
                return "Test";
            }

            @Override
            public List<DescripteurReglage> reglages() {
                return reglages;
            }
        };
    }

    private static OngletReglagesPersonnalise ongletPersonnalise(List<DescripteurReglage> reglages, Node custom) {
        return new OngletReglagesPersonnalise() {
            @Override
            public String idFeature() {
                return "test";
            }

            @Override
            public int ordre() {
                return 0;
            }

            @Override
            public String titre() {
                return "Test";
            }

            @Override
            public List<DescripteurReglage> reglages() {
                return reglages;
            }

            @Override
            public Node formulairePersonnalise() {
                return custom;
            }
        };
    }
}
