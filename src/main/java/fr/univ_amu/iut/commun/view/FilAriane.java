package fr.univ_amu.iut.commun.view;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;

/// Le fil d'Ariane du chrome : il **élide des segments entiers** quand la place manque, au lieu de
/// laisser chacun de ses libellés se faire couper (#3798).
///
/// L'[ADR 3760](../../../../../../../dev-docs/decisions/3760-le-deficit-se-porte-il-ne-se-repartit-pas.md)
/// désigne le fil comme porteur du déficit de la barre. Une `HBox` le **répartirait** : à 900 les cinq
/// segments perdaient 39 px chacun, et une amputation égale en pixels est inégale en information.
///
/// Le **dernier** segment est gardé quoi qu'il arrive, puis les ancêtres tant qu'ils tiennent ;
/// l'**ancre** passe en dernier, ayant un recours que les autres n'ont pas. Le milieu devient un menu
/// « … » qui garde chaque libellé entier et son action : un ancêtre change de forme, jamais d'existence.
public final class FilAriane extends HBox {

    private static final String SEPARATEUR = "›";

    /// Le libellé du menu d'élision. Trois points en **un** caractère : « ... » en coûterait trois.
    private static final String ELISION = "…";

    private List<Lieu> segments = List.of();

    /// Les nœuds du fil, **construits une seule fois** par pose de segments : un libellé par segment, un
    /// séparateur devant chacun, et le menu d'élision avec le sien.
    ///
    /// Ils restent tous enfants, et l'ajustement à la largeur ne fait que basculer leur `managed` et
    /// leur `visible`. Reconstruire la liste et forcer `applyCss()` **depuis l'écouteur de largeur** -
    /// donc au milieu de `Region.resize` - laissait des nœuds voisins non disposés : trois exécutions
    /// de la CI ont rendu, dans des classes sans rapport avec le fil, des « nœud present mais non
    /// visible » absents en local comme sur les autres branches.
    private List<Labeled> noeuds = List.of();

    private List<Label> separateurs = List.of();

    private MenuButton elision;

    /// Largeurs voulues, mesurées **hors** mise en page, à la pose. Elles ne dépendent que des libellés
    /// et des styles : les recalculer à chaque changement de largeur ne les changerait pas.
    private List<Double> largeurs = List.of();

    private double largeurSeparateur;

    private double largeurElision;

    public FilAriane() {
        getStyleClass().add("fil-ariane");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(4.0);
        // Largeur minimale nulle, et c'est ce qui rend la reconstruction sûre : sans cela, la barre
        // distribue la place en tenant compte du minimum du fil, lequel dépend de son contenu. Poser le
        // menu « … » lui faisait alors gagner ou perdre une douzaine de pixels, qui relançaient un
        // recalcul, qui changeait le contenu. À min = 0, la largeur reçue ne dépend plus que des voisins.
        setMinWidth(0);
        // L'ajustement ne touche que `managed` / `visible` : aucun enfant n'est ajouté ni retiré ici, et
        // aucun passage CSS n'est forcé. Cf. le champ `noeuds` pour ce que la première version coûtait.
        widthProperty().addListener((observable, avant, apres) -> ajusterALaLargeur());
    }

    /// Pose les segments du fil. Le dernier est l'écran courant.
    ///
    /// Appelé sur changement de navigation, **hors** de toute mise en page : c'est le seul moment où les
    /// enfants changent et où l'on peut résoudre les styles pour mesurer.
    public void poser(List<Lieu> nouveaux) {
        segments = List.copyOf(nouveaux);
        construireLesNoeuds();
        ajusterALaLargeur();
    }

    private void construireLesNoeuds() {
        if (segments.isEmpty()) {
            noeuds = List.of();
            separateurs = List.of();
            largeurs = List.of();
            getChildren().clear();
            return;
        }

        noeuds = segments.stream().map(FilAriane::noeudDe).toList();
        // Un séparateur par segment, plus celui qui précède le menu : ils vivent tous dans la liste et
        // s'affichent selon le choix, plutôt que de naître et mourir à chaque redimensionnement.
        separateurs = noeuds.stream().map(noeud -> separateur()).toList();
        elision = menuDElision();

        List<Node> enfants = new ArrayList<>();
        enfants.add(noeuds.get(0));
        enfants.add(separateurs.get(0));
        enfants.add(elision);
        for (int i = 1; i < noeuds.size(); i++) {
            enfants.add(separateurs.get(i));
            enfants.add(noeuds.get(i));
        }
        getChildren().setAll(enfants);

        // Les largeurs voulues ne sont connues qu'une fois les styles résolus. Ici c'est légitime : on
        // n'est pas dans une mise en page.
        applyCss();
        largeurs = noeuds.stream().map(noeud -> Math.ceil(noeud.prefWidth(-1))).toList();
        largeurSeparateur = Math.ceil(separateurs.get(0).prefWidth(-1));
        largeurElision = Math.ceil(elision.prefWidth(-1));
    }

    private void ajusterALaLargeur() {
        if (noeuds.isEmpty()) {
            return;
        }
        // Le rembourrage de la barre n'est pas disponible pour les segments, et une largeur voulue se
        // demande à l'entier supérieur : un calcul optimiste de 3 px suffit à faire couper un libellé,
        // c'est-à-dire à produire exactement le défaut que ce composant existe pour supprimer.
        double dispo = getWidth() - getInsets().getLeft() - getInsets().getRight();
        Choix choix = choisir(largeurs, largeurSeparateur, largeurElision, dispo);
        boolean elide = choix.elide(noeuds.size());

        montrer(noeuds.get(0), choix.ancre());
        montrer(separateurs.get(0), elide && choix.ancre());
        montrer(elision, elide);
        if (elide) {
            elision.getItems().setAll(elides(choix));
        }
        for (int i = 1; i < noeuds.size(); i++) {
            boolean rendu = i >= choix.premier();
            montrer(noeuds.get(i), rendu);
            montrer(separateurs.get(i), rendu && (choix.ancre() || elide || i > choix.premier()));
        }
    }

    private static void montrer(Node noeud, boolean visible) {
        noeud.setVisible(visible);
        noeud.setManaged(visible);
    }

    /// Ce que le fil rend : les segments à partir de `premier`, et l'ancre si elle tient encore.
    private record Choix(int premier, boolean ancre) {

        /// Vrai si des segments sont retirés, donc si un menu « … » doit les porter.
        boolean elide(int total) {
            return premier > (ancre ? 1 : 0) && total > 1;
        }
    }

    /// **La proximité l'emporte.** On part du dernier segment - celui qui dit où l'on est - et on remonte
    /// vers ses ancêtres tant qu'ils tiennent. L'ancre (« Accueil ») n'est ajoutée qu'**après**, si la
    /// place restante le permet.
    ///
    /// L'ordre compte, et la mesure l'a montré : à 900, garder l'ancre d'abord coûtait « Détails du
    /// passage N° 1 » et rendait `Accueil › … › Diagnostic matériel`. En servant les proches d'abord, le
    /// même espace rend `… › Détails du passage N° 1 › Diagnostic matériel` - le passage dont on
    /// diagnostique le matériel, plutôt qu'un accueil que le titre et le bouton ← Retour atteignent déjà.
    private Choix choisir(List<Double> largeurs, double separateur, double elision, double dispo) {
        if (dispo <= 0 || largeurs.size() == 1) {
            // Largeur pas encore connue (avant la première mise en page) ou fil réduit à l'écran courant :
            // on rend tout, et l'écoute de la largeur repassera.
            return new Choix(1, true);
        }
        int premier = largeurs.size() - 1;
        while (premier > 1 && largeurTotale(largeurs, separateur, elision, new Choix(premier - 1, false)) <= dispo) {
            premier--;
        }
        boolean ancre = largeurTotale(largeurs, separateur, elision, new Choix(premier, true)) <= dispo;
        return new Choix(premier, ancre);
    }

    /// Largeur demandée par le fil pour un choix donné : l'ancre si elle est gardée, le menu « … » si des
    /// segments sont retirés, puis les segments à partir de `premier`, séparateurs et intervalles compris.
    private double largeurTotale(List<Double> largeurs, double separateur, double elision, Choix choix) {
        double total = 0;
        int elements = 0;
        if (choix.ancre()) {
            total += largeurs.get(0);
            elements++;
        }
        if (choix.elide(largeurs.size())) {
            total += elision;
            elements++;
        }
        for (int i = choix.premier(); i < largeurs.size(); i++) {
            total += largeurs.get(i);
            elements++;
        }
        int separateurs = elements - 1;
        int intervalles = elements + separateurs - 1;
        return total + separateurs * separateur + intervalles * getSpacing();
    }

    /// Les segments retirés : ceux d'après l'ancre et d'avant `premier`, plus l'ancre elle-même quand la
    /// place a manqué jusqu'à elle.
    private List<MenuItem> elides(Choix choix) {
        List<MenuItem> entrees = new ArrayList<>();
        for (int i = choix.ancre() ? 1 : 0; i < choix.premier(); i++) {
            Lieu lieu = segments.get(i);
            MenuItem entree = new MenuItem(lieu.libelle());
            if (lieu.estCliquable()) {
                entree.setOnAction(evenement -> lieu.ouvrir().run());
            } else {
                entree.setDisable(true);
            }
            entrees.add(entree);
        }
        return entrees;
    }

    private static Labeled noeudDe(Lieu lieu) {
        if (lieu.estCliquable()) {
            Hyperlink lien = new Hyperlink(lieu.libelle());
            lien.getStyleClass().add("fil-ariane-segment");
            lien.setOnAction(evenement -> lieu.ouvrir().run());
            return lien;
        }
        Label courant = new Label(lieu.libelle());
        courant.getStyleClass().add("fil-ariane-courant");
        return courant;
    }

    private static MenuButton menuDElision() {
        MenuButton menu = new MenuButton(ELISION);
        menu.getStyleClass().add("fil-ariane-elision");
        menu.setAccessibleText("Étapes intermédiaires du fil d'Ariane");
        return menu;
    }

    private static Label separateur() {
        Label separateur = new Label(SEPARATEUR);
        separateur.getStyleClass().add("fil-ariane-separateur");
        return separateur;
    }
}
