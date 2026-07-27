package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Barre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/// Surface du **compte rendu chiffré** d'une opération lourde (#2358, maquette M-CompteRendu) : le
/// verdict, les proportions, le coût, ce qui reste vrai, et l'action suivante - en une bande dense
/// plutôt qu'en rapport à sections.
///
/// Nommé `PanneauCompteRendu` et non `CompteRenduChiffre` comme l'annonçait l'issue : ce nom-là est celui
/// du **modèle** ([CompteRenduChiffre], dans `commun.viewmodel`), et deux types homonymes rendraient
/// l'import ambigu dans cette classe même.
///
/// **Présentationnel pur** : il reçoit un modèle et l'affiche. Il ne va rien chercher, ne décide de rien,
/// et n'appartient à aucune feature - l'import, le dépôt et la réactivation y projettent ce qu'ils ont
/// déjà produit.
///
/// ## La règle des proportions est structurelle, pas surveillée
///
/// La largeur de chaque segment est **liée** ([Bindings]) à la fraction que le modèle calcule sur les
/// quantités réelles. Il n'existe donc aucun endroit où poser une largeur à la main, et donc aucun
/// endroit où l'échelle puisse mentir - c'est précisément ce qui a fait rejeter la maquette d'origine,
/// qui dessinait 128 px/Go sur une barre et 94 sur l'autre.
///
/// Les barres de volume partagent l'échelle de la plus grande ([CompteRenduChiffre#echelleDesVolumes]) :
/// « lu » ne remplit donc pas toute la largeur quand « écrit » vaut davantage.
///
/// ## Ce qui n'a rien à dire ne s'affiche pas
///
/// Chaque bloc se masque (`visible` **et** `managed`) quand il est vide : un compte rendu sans rejet
/// n'affiche pas un cadre vide intitulé « Motifs de rejet », et la bande se referme sur l'essentiel.
public final class PanneauCompteRendu extends VBox {

    /// Classe CSS de la racine. **Pas** `compte-rendu` : ce nom appartient déjà à [VueCompteRendu], le
    /// compte rendu **textuel**, et une seconde règle de même nom plus bas dans la feuille lui imposait
    /// silencieusement le cadre de cette bande - carte blanche, bordure et 16 px de marge intérieure - sur
    /// tous les écrans qui l'affichent. Les autres classes de ce composant étaient déjà préfixées `cr-` ;
    /// seule la racine avait emprunté un nom pris.
    static final String CLASSE_RACINE = "panneau-compte-rendu";

    /// Largeur minimale d'un segment non nul, en pixels : un segment minuscule doit rester **visible**
    /// (sa valeur exacte est de toute façon en légende) plutôt que d'être arrondi en silence à zéro.
    private static final double LARGEUR_MINI_SEGMENT = 3;

    /// Séparateur des valeurs mises bout à bout (« 5,0 Go · 1,8 Go »), mutualisé pour ne pas répéter le
    /// même littéral (PMD `AvoidDuplicateLiterals`).
    private static final String SEPARATEUR = " · ";

    /// Classe CSS de la pastille selon la sévérité, comme [BandeauRetour] pour le bandeau : la couleur
    /// vient de la feuille de style, jamais du code.
    private static final Map<Severite, String> CLASSE_PASTILLE = Map.of(
            Severite.SUCCES, "cr-badge-succes",
            Severite.INFO, "cr-badge-info",
            Severite.AVERTISSEMENT, "cr-badge-avertissement",
            Severite.ERREUR, "cr-badge-erreur");

    private final Label titre = new Label();
    private final Label pastille = new Label();
    private final HBox enTete = new HBox(8, titre, espaceur(), pastille);

    private final HBox barreVentilation = new HBox();
    private final HBox legende = new HBox(18);
    private final VBox blocVentilation = new VBox(8, barreVentilation, legende);

    /// Les volumes vivent dans une **grille** et non dans des lignes indépendantes : la colonne de la
    /// barre y a la même largeur pour toutes les lignes, ce qui rend l'échelle réellement commune. Avec
    /// des lignes séparées, la place laissée à chaque barre dépendait de la longueur du texte à sa droite
    /// (« 5,0 Go » contre « 5,0 Go · 1,8 Go »), et « lu » se dessinait à une autre échelle qu'« écrit » -
    /// le défaut même que ce composant existe pour empêcher.
    private final GridPane blocVolumes = new GridPane();
    private final VBox blocAvertissements = new VBox(4);

    private final HBox actions = new HBox(10);
    private final Label resumeMotifs = new Label();
    private final HBox pied = new HBox(10, actions, espaceur(), resumeMotifs);

    public PanneauCompteRendu() {
        getStyleClass().add(CLASSE_RACINE);
        setSpacing(12);
        titre.getStyleClass().add("cr-titre");
        pastille.getStyleClass().add("cr-badge");
        resumeMotifs.getStyleClass().add("cr-resume-motifs");
        barreVentilation.getStyleClass().add("cr-barre");
        // Sans cela, chaque segment est arrondi au pixel et la somme des arrondis dépasse la largeur de
        // la barre : « la somme des segments fait le total » ne serait vraie qu'à deux pixels près.
        barreVentilation.setSnapToPixel(false);
        blocVolumes.setHgap(10);
        blocVolumes.setVgap(6);
        blocVolumes.getColumnConstraints().addAll(colonneLibelle(), colonneBarre(), colonneValeur());
        enTete.setAlignment(Pos.CENTER_LEFT);
        legende.setAlignment(Pos.CENTER_LEFT);
        actions.setAlignment(Pos.CENTER_LEFT);
        pied.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(enTete, blocVentilation, blocVolumes, blocAvertissements, pied);
    }

    /// Affiche `rendu`, en masquant tout bloc qui n'a rien à dire. Appelable plusieurs fois : chaque appel
    /// remplace entièrement le contenu précédent.
    public void afficher(CompteRenduChiffre rendu) {
        titre.setText(rendu.titre());
        pastille.setText(rendu.resultat());
        pastille.getStyleClass().removeIf(classe -> classe.startsWith("cr-badge-"));
        pastille.getStyleClass().add(CLASSE_PASTILLE.getOrDefault(rendu.severite(), "cr-badge-info"));

        remplirVentilation(rendu);
        remplirVolumes(rendu);
        remplirAvertissements(rendu.avertissements());
        remplirPied(rendu);
    }

    private void remplirVentilation(CompteRenduChiffre rendu) {
        var ventilation = rendu.ventilation();
        barreVentilation.getChildren().clear();
        legende.getChildren().clear();
        if (ventilation.estVide()) {
            montrer(blocVentilation, false);
            return;
        }
        for (Segment segment : ventilation.segments()) {
            barreVentilation.getChildren().add(part(segment, ventilation.fraction(segment), barreVentilation));
            legende.getChildren().add(entreeDeLegende(segment, ventilation.pourcentage(segment)));
        }
        montrer(blocVentilation, true);
    }

    private void remplirVolumes(CompteRenduChiffre rendu) {
        blocVolumes.getChildren().clear();
        long echelle = rendu.echelleDesVolumes();
        if (rendu.volumes().isEmpty() || echelle == 0) {
            montrer(blocVolumes, false);
            return;
        }
        int rang = 0;
        for (Barre barre : rendu.volumes()) {
            Label libelle = new Label(barre.libelle());
            libelle.getStyleClass().add("cr-volume-libelle");
            Label valeur = new Label(barre.segments().stream()
                    .map(Segment::valeurLisible)
                    .reduce((a, b) -> a + SEPARATEUR + b)
                    .orElse(""));
            valeur.getStyleClass().add("cr-volume-valeur");
            blocVolumes.addRow(rang++, libelle, barreDeVolume(barre, echelle), valeur);
        }
        montrer(blocVolumes, true);
    }

    /// La barre d'une ligne de volume, calée sur l'**échelle commune** de l'ensemble : un segment de 1,8 Go
    /// occupe la même largeur qu'il soit sur la première ou la seconde ligne, puisque la colonne qui les
    /// porte est unique.
    private static HBox barreDeVolume(Barre barre, long echelle) {
        HBox parts = new HBox();
        parts.getStyleClass().add("cr-barre");
        parts.setSnapToPixel(false);
        parts.setMaxWidth(Double.MAX_VALUE);
        for (Segment segment : barre.segments()) {
            parts.getChildren().add(part(segment, (double) segment.quantite() / echelle, parts));
        }
        return parts;
    }

    /// Colonne des libellés : largeur fixe, alignée à droite contre les barres.
    private static ColumnConstraints colonneLibelle() {
        ColumnConstraints colonne = new ColumnConstraints();
        colonne.setMinWidth(110);
        colonne.setPrefWidth(110);
        colonne.setHalignment(javafx.geometry.HPos.RIGHT);
        return colonne;
    }

    /// Colonne des barres : elle absorbe l'espace restant, **la même pour toutes les lignes**. C'est ce
    /// partage qui fait l'échelle commune.
    private static ColumnConstraints colonneBarre() {
        ColumnConstraints colonne = new ColumnConstraints();
        colonne.setHgrow(Priority.ALWAYS);
        colonne.setFillWidth(true);
        return colonne;
    }

    /// Colonne des valeurs lisibles : à la largeur du texte le plus long, donc identique d'une ligne à
    /// l'autre - ce qui laisse la colonne des barres inchangée.
    private static ColumnConstraints colonneValeur() {
        ColumnConstraints colonne = new ColumnConstraints();
        colonne.setHgrow(Priority.NEVER);
        return colonne;
    }

    /// Un segment de barre, dont la largeur est **liée** à sa fraction : c'est le seul endroit où une
    /// largeur se décide, et elle ne peut venir que d'une quantité réelle.
    private static Region part(Segment segment, double fraction, Region conteneur) {
        Region part = new Region();
        part.getStyleClass()
                .addAll("cr-seg", "cr-seg-" + segment.teinte().name().toLowerCase(Locale.ROOT));
        DoubleBinding largeur = Bindings.createDoubleBinding(
                () -> segment.quantite() == 0 ? 0 : Math.max(LARGEUR_MINI_SEGMENT, conteneur.getWidth() * fraction),
                conteneur.widthProperty());
        part.minWidthProperty().bind(largeur);
        part.prefWidthProperty().bind(largeur);
        part.maxWidthProperty().bind(largeur);
        Tooltip.install(part, new Tooltip(segment.libelle() + SEPARATEUR + segment.valeurLisible()));
        return part;
    }

    private static Node entreeDeLegende(Segment segment, double pourcentage) {
        Region puce = new Region();
        puce.getStyleClass()
                .addAll("cr-puce", "cr-seg-" + segment.teinte().name().toLowerCase(Locale.ROOT));
        Label texte = new Label(segment.libelle() + SEPARATEUR + segment.valeurLisible() + " ("
                + String.format(Locale.FRANCE, "%.1f", pourcentage) + " %)");
        texte.getStyleClass().add("cr-legende");
        HBox entree = new HBox(6, puce, texte);
        entree.setAlignment(Pos.CENTER_LEFT);
        return entree;
    }

    private void remplirAvertissements(List<String> avertissements) {
        blocAvertissements.getChildren().clear();
        if (avertissements.isEmpty()) {
            montrer(blocAvertissements, false);
            return;
        }
        for (String avertissement : avertissements) {
            FontIcon icone = new FontIcon("fas-exclamation-triangle");
            icone.getStyleClass().add("cr-avertissement-icone");
            Label texte = new Label(avertissement);
            texte.getStyleClass().add("cr-avertissement");
            texte.setWrapText(true);
            HBox ligne = new HBox(8, icone, texte);
            ligne.setAlignment(Pos.CENTER_LEFT);
            blocAvertissements.getChildren().add(ligne);
        }
        montrer(blocAvertissements, true);
    }

    /// Le pied : l'action suivante mise en avant, les autres à sa droite, et le résumé des motifs à
    /// l'opposé. Un compte rendu ne se termine pas sur « Fermer » ; s'il n'a **aucune** action à proposer,
    /// le pied disparaît plutôt que d'afficher une barre vide.
    private void remplirPied(CompteRenduChiffre rendu) {
        actions.getChildren().clear();
        for (Action action : rendu.actions()) {
            Button bouton = new Button(action.libelle());
            bouton.getStyleClass().add(action.principale() ? "bouton-primaire" : "bouton-secondaire");
            bouton.setOnAction(evenement -> action.geste().run());
            actions.getChildren().add(bouton);
        }
        String resume = rendu.resumeDesMotifs();
        resumeMotifs.setText(resume);
        montrer(resumeMotifs, !resume.isEmpty());
        montrer(pied, !rendu.actions().isEmpty() || !resume.isEmpty());
    }

    /// Masque **et** démanage : un bloc invisible qui garde sa place laisserait un trou dans la bande.
    private static void montrer(Node noeud, boolean visible) {
        noeud.setVisible(visible);
        noeud.setManaged(visible);
    }

    private static Region espaceur() {
        Region espaceur = new Region();
        HBox.setHgrow(espaceur, Priority.ALWAYS);
        return espaceur;
    }
}
