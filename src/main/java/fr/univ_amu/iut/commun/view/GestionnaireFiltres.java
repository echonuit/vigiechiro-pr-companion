package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.javafx.FontIcon;

/// Barre de filtres d'une table, patron **« à la Notion »** (#470/#471/#537), **générique** sur le type de
/// ligne `T` : une **recherche texte permanente**, un bouton **« + Filtre »** qui liste les critères non
/// encore actifs, et des **puces** retirables pour les filtres actifs. Chaque puce branche/retire son
/// prédicat sur [Filtres] ; la conjonction est appliquée à la table et les compteurs suivent le sous-ensemble
/// affiché.
///
/// Socle partagé (`commun`) des vues tabulaires (audio, puis analyse / multisite). La **recherche texte**
/// dépend des champs du type filtré : elle est **injectée** (`rechercheTexte`) par la vue, ce qui garde ce
/// gestionnaire indépendant de tout type concret. Logique sortie du controller (pur câblage) pour tenir les
/// seuils de cohésion PMD.
///
/// @param <T> type des lignes filtrées (ex. `LigneObservationAudio`)
public final class GestionnaireFiltres<T> {

    /// Clé du filtre de **recherche texte** (permanent, distinct des critères du menu « + Filtre »).
    private static final String NOM_TEXTE = "texte";

    private final MenuButton menuAjout;
    private final Pane puces;
    private final Filtres<T> filtres;
    private final List<CritereFiltre<T>> criteres;
    private final TextField recherche;

    /// Écouteur notifié à **chaque changement de filtre** (recherche texte, ajout/retrait de puce, valeur d'un
    /// critère, réinitialisation). Permet aux onglets de vues mémorisées (#623) de détecter que les filtres
    /// courants ont **divergé** de la vue active. No-op par défaut ; un seul écouteur (le dernier posé).
    private Runnable auChangement = () -> {};

    /// Critères actifs, **par ordre d'ajout** (clé = [CritereFiltre#nom()], valeur = Node éditeur de la puce,
    /// `null` pour un critère booléen). Ordonné pour restituer les puces dans le même ordre, et porteur du
    /// Node éditeur dont on lit/écrit les valeurs lors de la mémorisation de session (#484).
    private final Map<String, Node> actifs = new LinkedHashMap<>();

    /// Construit la barre de filtres.
    ///
    /// @param recherche champ de recherche texte permanent
    /// @param menuAjout bouton « + Filtre »
    /// @param puces conteneur des puces actives
    /// @param filtres filtres composables pilotés (branchement/retrait des prédicats)
    /// @param criteres catalogue des critères proposés au menu
    /// @param rechercheTexte prédicat de correspondance texte propre au type filtré `(ligne, aiguille)`
    public GestionnaireFiltres(
            TextField recherche,
            MenuButton menuAjout,
            Pane puces,
            Filtres<T> filtres,
            List<CritereFiltre<T>> criteres,
            BiPredicate<T, String> rechercheTexte) {
        this.recherche = Objects.requireNonNull(recherche, "recherche");
        this.menuAjout = Objects.requireNonNull(menuAjout, "menuAjout");
        this.puces = Objects.requireNonNull(puces, "puces");
        this.filtres = Objects.requireNonNull(filtres, "filtres");
        this.criteres = List.copyOf(criteres);
        Objects.requireNonNull(rechercheTexte, "rechercheTexte");
        recherche.textProperty().addListener((obs, avant, texte) -> {
            filtres.definir(
                    NOM_TEXTE, texte == null || texte.isBlank() ? null : ligne -> rechercheTexte.test(ligne, texte));
            auChangement.run();
        });
        reconstruireMenu();
    }

    /// Enregistre l'écouteur appelé à chaque changement de filtre (un seul ; remplace le précédent). Les
    /// onglets de vues mémorisées s'y branchent pour repérer une divergence avec la vue active (#623).
    public void surChangement(Runnable ecouteur) {
        this.auChangement = Objects.requireNonNull(ecouteur, "ecouteur");
    }

    /// Retire **tous** les filtres (texte + puces) : utilisé quand on doit garantir la visibilité d'une ligne
    /// ciblée (navigation), quel que soit le filtrage courant.
    public void reinitialiser() {
        recherche.clear(); // retire le filtre texte via son écouteur
        puces.getChildren().clear();
        actifs.clear();
        filtres.reinitialiser();
        reconstruireMenu();
        auChangement.run();
    }

    /// Menu « + Filtre » : les critères **non encore actifs** ; désactivé quand tout est déjà ajouté.
    private void reconstruireMenu() {
        menuAjout
                .getItems()
                .setAll(criteres.stream()
                        .filter(critere -> !actifs.containsKey(critere.nom()))
                        .map(this::itemMenu)
                        .toList());
        menuAjout.setDisable(menuAjout.getItems().isEmpty());
    }

    private MenuItem itemMenu(CritereFiltre<T> critere) {
        MenuItem item = new MenuItem(critere.libelle());
        item.setOnAction(evenement -> ajouterPuce(critere));
        return item;
    }

    private void ajouterPuce(CritereFiltre<T> critere) {
        Node editeur = critere.editeur(predicat -> {
            filtres.definir(critere.nom(), predicat);
            auChangement.run();
        });
        actifs.put(critere.nom(), editeur);
        puces.getChildren().add(construirePuce(critere, editeur));
        reconstruireMenu();
        // Notifier **après** l'enregistrement de la puce dans `actifs` : l'application initiale du critère
        // (dans `editeur(...)` ci-dessus) déclenche `auChangement` avant que `decrire()` ne voie la puce, donc
        // ne suffit pas à détecter l'ajout. Ce second appel garantit un descripteur à jour.
        auChangement.run();
    }

    private HBox construirePuce(CritereFiltre<T> critere, Node editeur) {
        HBox puce = new HBox(6.0, new Label(critere.libelle()));
        puce.getStyleClass().add("puce-filtre");
        if (editeur != null) {
            puce.getChildren().add(editeur);
        }
        Button retirer = new Button();
        retirer.setGraphic(new FontIcon("fas-times"));
        retirer.getStyleClass().add("puce-filtre-retirer");
        retirer.setAccessibleText("Retirer le filtre " + critere.libelle());
        retirer.setOnAction(evenement -> retirerPuce(critere, puce));
        puce.getChildren().add(retirer);
        return puce;
    }

    private void retirerPuce(CritereFiltre<T> critere, HBox puce) {
        puces.getChildren().remove(puce);
        actifs.remove(critere.nom());
        filtres.definir(critere.nom(), null);
        reconstruireMenu();
        auChangement.run();
    }

    /// **Pose** (ou met à jour) par programme le critère `nom` avec les `valeurs` sémantiques données :
    /// ajoute sa puce si elle n'est pas déjà active, puis y restaure les valeurs (via
    /// [CritereFiltre#restaurerValeurs(Node, List)]). Permet à la vue de piloter un filtre sans clic
    /// utilisateur : p. ex. le multisite filtre par le carré **cliqué sur la carte** (#152/#476). Sans
    /// effet si `nom` n'appartient pas au catalogue des critères.
    ///
    /// Une valeur que le critère **n'offre pas** est ignorée en silence : `restaurerValeurs` la rend,
    /// et cet appel la jette. Aujourd'hui sans conséquence, son unique appelant visant un **champ
    /// libre**, qui accepte tout. Cela cesserait d'être vrai le jour où l'on poserait une valeur sur un
    /// critère à liste : la puce apparaîtrait vide, et comme rien de coché n'écarte rien, l'écran
    /// montrerait plus que ce qu'il annonce - le mode de panne de #3056 et #3071. Il faudrait alors
    /// remonter ce que cette méthode jette, comme le font déjà `restaurer` et les vues mémorisées.
    public void poser(String nom, List<String> valeurs) {
        critereParNom(nom).ifPresent(critere -> {
            if (!actifs.containsKey(nom)) {
                ajouterPuce(critere);
            }
            critere.restaurerValeurs(actifs.get(nom), valeurs);
        });
    }

    /// Rejoue une vue mémorisée décrite **sémantiquement** ([DescripteurFiltre], #623) : réinitialise,
    /// réapplique la recherche texte, puis ré-ajoute chaque puce (l'éditeur repart de ses valeurs par défaut,
    /// appliquées à l'ajout) avant d'y restaurer les valeurs **en clair** via
    /// [CritereFiltre#restaurerValeurs(Node, List)]. L'entrée est **transportable / persistée** (base
    /// `vue_sauvegardee`) et sert aussi de **mémoire de session** depuis #3071. Les critères inconnus du
    /// catalogue ne sont pas posés, mais ils sont désormais **rapportés** (#3093).
    ///
    /// **Rend ce qu'aucun critère n'a su replacer** ([ResteDeRestauration], #3056 puis #3093) : une valeur
    /// renommée ou absente du jeu courant ne coche rien, et un critère absent du catalogue de cet écran
    /// n'est pas posé du tout. Comme rien de coché n'écarte rien, la restauration filtre alors **moins**
    /// que ce qu'elle promet. L'appelant décide quoi en dire ; l'ignorer rendrait la dégradation
    /// invisible.
    ///
    /// Les critères inconnus étaient auparavant jetés par un `ifPresent` sans branche « sinon » : le
    /// silence portait donc sur les deux causes, alors que seule la première était rapportée.
    ///
    /// @return les valeurs et les critères laissés de côté (vide en temps normal)
    public ResteDeRestauration restaurer(DescripteurFiltre descripteur) {
        reinitialiser();
        if (descripteur == null) {
            return ResteDeRestauration.RIEN;
        }
        if (!descripteur.texte().isBlank()) {
            recherche.setText(descripteur.texte());
        }
        List<String> valeursPerdues = new ArrayList<>();
        List<String> criteresInconnus = new ArrayList<>();
        for (DescripteurCritere memorise : descripteur.criteres()) {
            Optional<CritereFiltre<T>> connu = critereParNom(memorise.nom());
            if (connu.isEmpty()) {
                criteresInconnus.add(memorise.nom());
                continue;
            }
            CritereFiltre<T> critere = connu.get();
            ajouterPuce(critere);
            valeursPerdues.addAll(critere.restaurerValeurs(actifs.get(critere.nom()), memorise.valeurs()));
        }
        return new ResteDeRestauration(valeursPerdues, criteresInconnus);
    }

    /// **Décrit** l'état courant des filtres sous une forme **sémantique et transportable** (#537 étape 2) :
    /// recherche texte + valeur en clair de chaque puce active (via [CritereFiltre#valeurCourante]), dans
    /// l'ordre d'ajout. À la différence de [#capturer()] (index de contrôles, mémoire de session #484), ce
    /// descripteur est **réapplicable à une autre vue** partageant les mêmes clés de critères (base de
    /// « Voir sur la carte », #476).
    public DescripteurFiltre decrire() {
        List<DescripteurCritere> criteresActifs = actifs.entrySet().stream()
                .map(entree -> new DescripteurCritere(
                        entree.getKey(),
                        critereParNom(entree.getKey())
                                .map(critere -> critere.valeurCourante(entree.getValue()))
                                .orElseGet(List::of)))
                .toList();
        return new DescripteurFiltre(Objects.requireNonNullElse(recherche.getText(), ""), criteresActifs);
    }

    /// Le critère portant `nom`, **ou l'ayant porté** ([CritereFiltre#nomsHerites()], #3096) : une vue
    /// enregistrée avant un renommage continue ainsi de se rejouer, sans migration de base.
    private Optional<CritereFiltre<T>> critereParNom(String nom) {
        return criteres.stream()
                .filter(critere ->
                        critere.nom().equals(nom) || critere.nomsHerites().contains(nom))
                .findFirst();
    }
}
