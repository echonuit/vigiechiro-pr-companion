package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.Filtres;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

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
        Button retirer = new Button("✕");
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
    public void poser(String nom, List<String> valeurs) {
        critereParNom(nom).ifPresent(critere -> {
            if (!actifs.containsKey(nom)) {
                ajouterPuce(critere);
            }
            critere.restaurerValeurs(actifs.get(nom), valeurs);
        });
    }

    /// **Photographie** l'état courant des filtres (recherche texte + puces actives avec leurs valeurs), pour
    /// le mémoriser le temps de la session (#484). Chaque puce est décrite par le [CritereFiltre#nom()] et les
    /// valeurs de ses contrôles (index de liste déroulante, valeur de curseur), dans l'ordre d'ajout.
    public EtatFiltres capturer() {
        List<EtatCritere> etats = actifs.entrySet().stream()
                .map(entree -> new EtatCritere(entree.getKey(), valeursDe(entree.getValue())))
                .toList();
        return new EtatFiltres(Objects.requireNonNullElse(recherche.getText(), ""), etats);
    }

    /// Restitue un état capturé par [#capturer()] : réinitialise, réapplique la recherche texte puis ré-ajoute
    /// chaque puce (l'éditeur repart de ses valeurs par défaut, appliquées à l'ajout) avant d'y réinjecter les
    /// valeurs mémorisées. Les critères disparus du catalogue et les valeurs surnuméraires sont ignorés.
    public void restaurer(EtatFiltres etat) {
        reinitialiser();
        if (etat == null) {
            return;
        }
        if (!etat.texte().isBlank()) {
            recherche.setText(etat.texte());
        }
        for (EtatCritere memorise : etat.criteres()) {
            critereParNom(memorise.nom()).ifPresent(critere -> {
                ajouterPuce(critere);
                appliquerValeurs(actifs.get(critere.nom()), memorise.valeurs());
            });
        }
    }

    /// Rejoue une vue mémorisée décrite **sémantiquement** ([DescripteurFiltre], #623) : réinitialise,
    /// réapplique la recherche texte, puis ré-ajoute chaque puce (l'éditeur repart de ses valeurs par défaut,
    /// appliquées à l'ajout) avant d'y restaurer les valeurs **en clair** via
    /// [CritereFiltre#restaurerValeurs(Node, List)]. À la différence de [#restaurer(EtatFiltres)] (index de
    /// contrôles, mémoire de session #484), l'entrée est **transportable / persistée** (base
    /// `vue_sauvegardee`) : les critères inconnus du catalogue sont ignorés.
    public void restaurer(DescripteurFiltre descripteur) {
        reinitialiser();
        if (descripteur == null) {
            return;
        }
        if (!descripteur.texte().isBlank()) {
            recherche.setText(descripteur.texte());
        }
        for (DescripteurCritere memorise : descripteur.criteres()) {
            critereParNom(memorise.nom()).ifPresent(critere -> {
                ajouterPuce(critere);
                critere.restaurerValeurs(actifs.get(critere.nom()), memorise.valeurs());
            });
        }
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

    private Optional<CritereFiltre<T>> critereParNom(String nom) {
        return criteres.stream().filter(critere -> critere.nom().equals(nom)).findFirst();
    }

    /// Valeurs des contrôles de valeur (`ComboBox`, `Slider`) d'un Node éditeur, dans l'ordre de l'arbre :
    /// index sélectionné pour une liste déroulante, valeur brute pour un curseur. Liste vide si `editeur` est
    /// `null` (critère booléen).
    private static List<Double> valeursDe(Node editeur) {
        return controlesDe(editeur).stream()
                .map(GestionnaireFiltres::valeurControle)
                .toList();
    }

    /// Réinjecte, dans l'ordre, les valeurs mémorisées sur les contrôles de `editeur` (déclenche les écouteurs
    /// et donc la réapplication des prédicats). Tolère un nombre de valeurs différent (contrôle en trop ignoré).
    private static void appliquerValeurs(Node editeur, List<Double> valeurs) {
        List<Node> controles = controlesDe(editeur);
        for (int i = 0; i < controles.size() && i < valeurs.size(); i++) {
            ecrireValeur(controles.get(i), valeurs.get(i));
        }
    }

    /// Contrôles de valeur (`ComboBox` / `Slider`) présents dans `editeur`, en **parcours préfixe** (ordre
    /// stable, identique à la capture). On ne descend pas dans les contrôles eux-mêmes (leur squelette interne
    /// n'est pas une valeur métier).
    private static List<Node> controlesDe(Node editeur) {
        List<Node> controles = new ArrayList<>();
        collecter(editeur, controles);
        return controles;
    }

    private static void collecter(Node noeud, List<Node> controles) {
        if (noeud instanceof ComboBox<?> || noeud instanceof Slider) {
            controles.add(noeud);
        } else if (noeud instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(enfant -> collecter(enfant, controles));
        }
    }

    private static double valeurControle(Node controle) {
        if (controle instanceof Slider curseur) {
            return curseur.getValue();
        }
        return ((ComboBox<?>) controle).getSelectionModel().getSelectedIndex();
    }

    private static void ecrireValeur(Node controle, double valeur) {
        if (controle instanceof Slider curseur) {
            curseur.setValue(valeur);
        } else if (controle instanceof ComboBox<?> liste) {
            int index = (int) valeur;
            if (index >= 0 && index < liste.getItems().size()) {
                liste.getSelectionModel().select(index);
            } else {
                liste.getSelectionModel().clearSelection();
            }
        }
    }
}
