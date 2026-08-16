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
/// ## Pourquoi ce n'est pas une `HBox` ordinaire
///
/// L'[ADR 3760](../../../../../../../dev-docs/decisions/3760-le-deficit-se-porte-il-ne-se-repartit-pas.md)
/// a désigné le fil comme **porteur déclaré** du déficit de la barre du haut, pour que le titre et le
/// bouton ← Retour restent entiers. Mais une `HBox` **répartit** ce déficit entre ses enfants : à 900,
/// les cinq segments de l'écran le plus profond perdaient **39 px chacun**, et « Accueil » 65 % de sa
/// largeur. Le principe de l'ADR était tenu à l'étage de la barre et enfreint un cran plus bas.
///
/// Une répartition égale en pixels est une amputation **inégale** en information : ce qui reste de
/// « Détails du passage N° 1 » se lit encore, ce qui reste d'« Accueil » est un « A… ».
///
/// ## Ce qui est gardé, et dans quel ordre
///
/// Le **dernier** segment dit où l'on est : il est gardé quoi qu'il arrive. On remonte ensuite vers ses
/// ancêtres tant qu'ils tiennent. Le milieu retiré devient un menu « … ».
///
/// L'**ancre** (« Accueil ») passe en **dernier**, et se fait lâcher la première. Elle a un recours que
/// les autres n'ont pas : le titre de l'application et le bouton ← Retour y mènent déjà, et le menu
/// « … » la garde de toute façon. La mesure a tranché cet ordre plutôt qu'un avis : à 900, servir
/// l'ancre d'abord rendait `Accueil › … › Diagnostic matériel` ; servir les proches d'abord rend
/// `… › Détails du passage N° 1 › Diagnostic matériel`, dans le même espace.
///
/// Un schéma **fixe** ne pouvait pas convenir, et la mesure l'a montré aussi : « le premier et les deux
/// derniers », proposé à l'ouverture de #3798, demande 380 px là où il n'y en a que 351. Ce qui tient
/// dépend de la place, donc de la largeur de la fenêtre.
///
/// Le résultat se lit comme une descente régulière, sur l'écran le plus profond :
///
/// | Largeur | Fil rendu |
/// |---|---|
/// | 1100 | `Accueil › Mes sites › Carré 640380 › Détails du passage N° 1 › Diagnostic matériel` |
/// | 1000 | `… › Carré 640380 › Détails du passage N° 1 › Diagnostic matériel` |
/// | 900 | `… › Détails du passage N° 1 › Diagnostic matériel` |
///
/// ## Rien ne disparaît sans recours
///
/// Le « … » n'est pas un texte mais un **menu** : chaque segment retiré y garde son libellé entier et
/// son action. Un ancêtre change de forme, jamais d'existence.
public final class FilAriane extends HBox {

    private static final String SEPARATEUR = "›";

    /// Le libellé du menu d'élision. Trois points en **un** caractère : « ... » en coûterait trois.
    private static final String ELISION = "…";

    private List<Lieu> segments = List.of();

    /// Garde-fou de réentrance : reconstruire touche les enfants, ce qui peut relancer une mise en page.
    private boolean enReconstruction;

    public FilAriane() {
        getStyleClass().add("fil-ariane");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(4.0);
        // Largeur minimale nulle, et c'est ce qui rend la reconstruction sûre : sans cela, la barre
        // distribue la place en tenant compte du minimum du fil, lequel dépend de son contenu. Poser le
        // menu « … » lui faisait alors gagner ou perdre une douzaine de pixels, qui relançaient un
        // recalcul, qui changeait le contenu. À min = 0, la largeur reçue ne dépend plus que des voisins.
        setMinWidth(0);
        widthProperty().addListener((observable, avant, apres) -> reconstruire());
    }

    /// Pose les segments du fil. Le dernier est l'écran courant.
    public void poser(List<Lieu> nouveaux) {
        segments = List.copyOf(nouveaux);
        reconstruire();
    }

    private void reconstruire() {
        if (enReconstruction) {
            return;
        }
        enReconstruction = true;
        try {
            poserLesEnfants();
        } finally {
            enReconstruction = false;
        }
    }

    private void poserLesEnfants() {
        if (segments.isEmpty()) {
            getChildren().clear();
            return;
        }

        List<Labeled> noeuds = segments.stream().map(FilAriane::noeudDe).toList();
        MenuButton elision = menuDElision();
        Label separateur = separateur();

        // Mesure : les largeurs voulues ne sont connues qu'une fois les styles résolus, donc une fois les
        // nœuds dans la scène. On les y met tous, on mesure, puis on retient ce qui tient.
        getChildren().setAll(new ArrayList<Node>(noeuds));
        getChildren().addAll(elision, separateur);
        applyCss();

        // Le rembourrage de la barre n'est pas disponible pour les segments, et une largeur voulue se
        // demande à l'entier supérieur : un calcul optimiste de 3 px suffit à faire couper un libellé,
        // c'est-à-dire à produire exactement le défaut que ce composant existe pour supprimer.
        double dispo = getWidth() - getInsets().getLeft() - getInsets().getRight();
        List<Double> largeurs =
                noeuds.stream().map(noeud -> Math.ceil(noeud.prefWidth(-1))).toList();
        Choix choix = choisir(largeurs, Math.ceil(separateur.prefWidth(-1)), Math.ceil(elision.prefWidth(-1)), dispo);

        // Vider avant de reposer : la liste finale reprend des nœuds qui sont déjà enfants (ceux de la
        // mesure), et un `setAll` direct les compte deux fois.
        getChildren().clear();
        getChildren().setAll(assembler(noeuds, elision, choix));
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

    private List<Node> assembler(List<Labeled> noeuds, MenuButton elision, Choix choix) {
        List<Node> enfants = new ArrayList<>();
        if (choix.ancre()) {
            enfants.add(noeuds.get(0));
        }
        if (choix.elide(noeuds.size())) {
            elision.getItems().setAll(elides(choix));
            ajouterSepare(enfants, elision);
        }
        for (int i = choix.premier(); i < noeuds.size(); i++) {
            ajouterSepare(enfants, noeuds.get(i));
        }
        return enfants;
    }

    /// Ajoute `noeud`, précédé d'un « › » s'il n'ouvre pas le fil.
    private void ajouterSepare(List<Node> enfants, Node noeud) {
        if (!enfants.isEmpty()) {
            enfants.add(separateur());
        }
        enfants.add(noeud);
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
