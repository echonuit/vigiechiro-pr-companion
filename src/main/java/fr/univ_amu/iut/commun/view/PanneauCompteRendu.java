package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Barre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
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

    /// Nombre de fichiers visibles d'un coup dans la liste d'un motif, au-delà duquel elle défile. Une
    /// nuit réelle peut rejeter des centaines de fichiers : la liste doit être bornée, mais assez haute
    /// pour qu'on voie de quoi il s'agit sans avoir à faire défiler d'abord.
    private static final int LIGNES_VISIBLES_PAR_MOTIF = 5;

    /// Hauteur d'une ligne de liste, en pixels. **Imposée** aux cellules ([ListView#setFixedCellSize]) et
    /// non seulement supposée : sans cela, la hauteur de la liste et celle de ses cellules divergent d'un
    /// pixil ou deux et la dernière ligne apparaît **coupée en deux** - ce qu'on lit comme un défaut, pas
    /// comme une invitation à faire défiler.
    private static final int HAUTEUR_LIGNE = 24;

    /// Les bordures haute et basse du cadre de la liste, à ajouter aux lignes pour que le compte soit juste.
    private static final int MARGE_LISTE = 2;

    /// Glyphe par défaut d'une mention : le triangle d'alerte.
    private static final String GLYPHE_ALERTE = "fas-exclamation-triangle";

    /// Glyphe d'une mention selon son registre. Un fait de contexte s'annonce par un « i », une bonne
    /// nouvelle par une coche : les trois ne se confondent plus d'un coup d'œil.
    private static final Map<Severite, String> GLYPHE_AVERTISSEMENT = Map.of(
            Severite.SUCCES, "fas-check-circle",
            Severite.INFO, "fas-info-circle",
            Severite.AVERTISSEMENT, GLYPHE_ALERTE,
            Severite.ERREUR, "fas-times-circle");

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
    /// La légende **passe à la ligne** quand elle ne tient pas : une ventilation à cinq parts (le dépôt)
    /// en déborde, et une boîte horizontale comprime alors chaque entrée jusqu'à l'ellipse - « Publiées ·
    /// 12 (60,0 % » ne se lit plus. Le défaut n'est apparu qu'en **intégration continue**, dont les
    /// métriques de police diffèrent de neuf pixels par entrée : c'est exactement le genre d'écart qu'une
    /// mise en page à ligne unique transforme en texte coupé, ici comme sur l'écran d'un utilisateur.
    private final FlowPane legende = new FlowPane(18, 6);
    private final VBox blocVentilation = new VBox(8, barreVentilation, legende);

    /// Les volumes vivent dans une **grille** et non dans des lignes indépendantes : la colonne de la
    /// barre y a la même largeur pour toutes les lignes, ce qui rend l'échelle réellement commune. Avec
    /// des lignes séparées, la place laissée à chaque barre dépendait de la longueur du texte à sa droite
    /// (« 5,0 Go » contre « 5,0 Go · 1,8 Go »), et « lu » se dessinait à une autre échelle qu'« écrit » -
    /// le défaut même que ce composant existe pour empêcher.
    private final GridPane blocVolumes = new GridPane();
    private final VBox blocAvertissements = new VBox(4);

    private final HBox actions = new HBox(10);

    /// Le résumé des motifs est un **lien** et non une étiquette : il annonce un détail qui existe, donc il
    /// doit se laisser ouvrir. Une étiquette qui énumère « 6 fichiers déjà expansés » sans dire lesquels
    /// pose une question à laquelle elle refuse de répondre.
    private final Hyperlink resumeMotifs = new Hyperlink();

    private final HBox pied = new HBox(10, actions, espaceur(), resumeMotifs);

    /// Le détail des motifs, replié par défaut : une bande compacte ne s'ouvre que si on le demande.
    private final VBox blocMotifs = new VBox(8);

    public PanneauCompteRendu() {
        getStyleClass().add(CLASSE_RACINE);
        setSpacing(12);
        titre.getStyleClass().add("cr-titre");
        pastille.getStyleClass().add("cr-badge");
        // `abregeable` : dans une modale étroite (réactivation), l'énumération des motifs ne tient pas sur
        // une ligne, et le garde-fou anti-troncature refuse une capture qui l'abrégerait en silence. Le
        // déficit doit bien tomber quelque part ; il tombe ici, parce que c'est le SEUL libellé de la bande
        // dont le contenu complet est à un clic - les motifs qu'il résume s'ouvrent juste dessous.
        // (Le nom est celui que le garde-fou de capture reconnaît ; il est posé en littéral, comme dans les
        // FXML qui l'utilisent, pour qu'une vue ne dépende pas d'un outil de capture.)
        resumeMotifs.getStyleClass().addAll("cr-resume-motifs", "abregeable");
        barreVentilation.getStyleClass().add("cr-barre");
        // Sans cela, chaque segment est arrondi au pixel et la somme des arrondis dépasse la largeur de
        // la barre : « la somme des segments fait le total » ne serait vraie qu'à deux pixels près.
        barreVentilation.setSnapToPixel(false);
        blocVolumes.setHgap(10);
        blocVolumes.setVgap(6);
        blocVolumes.getColumnConstraints().addAll(colonneLibelle(), colonneBarre(), colonneValeur());
        enTete.setAlignment(Pos.CENTER_LEFT);
        legende.setAlignment(Pos.CENTER_LEFT);
        legende.setRowValignment(javafx.geometry.VPos.CENTER);
        actions.setAlignment(Pos.CENTER_LEFT);
        pied.setAlignment(Pos.CENTER_LEFT);
        blocMotifs.getStyleClass().add("cr-motifs");
        resumeMotifs.setOnAction(evenement -> basculerLesMotifs());
        montrer(blocMotifs, false);
        getChildren().addAll(enTete, blocVentilation, blocVolumes, blocAvertissements, pied, blocMotifs);
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

    /// Ce qui reste vrai à la fin, **chacun avec son registre**. Le glyphe et la couleur suivent la
    /// sévérité de l'entrée : un triangle d'alerte devant « L'audio est de nouveau complet » apprend à ne
    /// plus regarder les alertes - défaut vu à la première capture de la réactivation (#2358).
    private void remplirAvertissements(List<Avertissement> avertissements) {
        blocAvertissements.getChildren().clear();
        if (avertissements.isEmpty()) {
            montrer(blocAvertissements, false);
            return;
        }
        for (Avertissement avertissement : avertissements) {
            FontIcon icone = new FontIcon(GLYPHE_AVERTISSEMENT.getOrDefault(avertissement.severite(), GLYPHE_ALERTE));
            icone.getStyleClass().addAll("cr-avertissement-icone", classeDeSeverite(avertissement.severite()));
            Label texte = new Label(avertissement.texte());
            texte.getStyleClass().addAll("cr-avertissement", classeDeSeverite(avertissement.severite()));
            texte.setWrapText(true);
            HBox ligne = new HBox(8, icone, texte);
            ligne.setAlignment(Pos.CENTER_LEFT);
            blocAvertissements.getChildren().add(ligne);
        }
        montrer(blocAvertissements, true);
    }

    private static String classeDeSeverite(Severite severite) {
        return "cr-mention-" + severite.name().toLowerCase(Locale.ROOT);
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
        remplirMotifs(rendu.motifs());
    }

    /// Le détail des motifs, un bloc par motif : son intitulé, puis la **liste de ses fichiers**. Reconstruit
    /// à chaque affichage et **replié**, l'ouverture étant une demande de l'utilisateur, pas un état hérité
    /// du compte rendu précédent.
    ///
    /// Un motif est une **raison**, et les raisons restent peu nombreuses même quand les fichiers sont des
    /// centaines : c'est la granularité qui rend « chaque motif ouvre sa liste » tenable à l'écran.
    private void remplirMotifs(List<Motif> motifs) {
        blocMotifs.getChildren().clear();
        for (Motif motif : motifs) {
            Label intitule = new Label(motif.compte() + " " + motif.libelle());
            intitule.getStyleClass().add("cr-motif-intitule");
            // Enroulable : la bande vit aussi dans une modale étroite (réactivation), où « 1 fichier(s) :
            // enregistrement absent du dossier » manquait 102 px et sortait abrégé. Un motif dit une cause,
            // et une cause abrégée ne se comprend plus.
            intitule.setWrapText(true);
            // `wrapText` seul ne suffit pas : sans largeur maximale, le Label prend sa largeur PRÉFÉRÉE,
            // déborde, et le contrôle l'abrège au lieu de l'enrouler. La borne est ce qui déclenche
            // l'enroulement.
            intitule.setMaxWidth(Double.MAX_VALUE);
            blocMotifs.getChildren().add(new VBox(4, intitule, listeDe(motif)));
        }
        replierLesMotifs();
    }

    /// La liste des fichiers d'un motif. Sa hauteur est **posée explicitement** à partir du nombre d'entrées,
    /// et bornée : une liste laissée à sa hauteur naturelle dans une boîte verticale se réduit à zéro et
    /// n'affiche plus rien, défaut relevé en recette sur l'ancienne liste de rejets (2ᵉ constat de #1486).
    private static ListView<String> listeDe(Motif motif) {
        ListView<String> liste = new ListView<>(FXCollections.observableArrayList(motif.sujets()));
        liste.getStyleClass().add("cr-motif-liste");
        liste.setFixedCellSize(HAUTEUR_LIGNE);
        liste.setPrefHeight(Math.min(motif.compte(), LIGNES_VISIBLES_PAR_MOTIF) * HAUTEUR_LIGNE + MARGE_LISTE);
        liste.setMinHeight(Region.USE_PREF_SIZE);
        return liste;
    }

    /// Ouvre ou referme le détail. Le chevron suit l'état : un lien qui ne dit pas s'il est ouvert oblige à
    /// cliquer pour le savoir.
    private void basculerLesMotifs() {
        boolean ouvert = !blocMotifs.isVisible();
        montrer(blocMotifs, ouvert);
        resumeMotifs.setGraphic(new FontIcon(ouvert ? "fas-caret-down" : "fas-caret-right"));
    }

    private void replierLesMotifs() {
        montrer(blocMotifs, false);
        resumeMotifs.setGraphic(new FontIcon("fas-caret-right"));
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
