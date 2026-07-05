package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.FiltresAudio;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

/// Barre de filtres de la table audio, patron **« à la Notion »** (#470/#471) : une **recherche texte
/// permanente**, un bouton **« + Filtre »** qui liste les critères non encore actifs, et des **puces**
/// retirables pour les filtres actifs. Chaque puce branche/retire son prédicat sur [FiltresAudio] ; la
/// conjonction est appliquée à la table et les compteurs suivent le sous-ensemble affiché.
///
/// Prototype dans `audio/view` (composant partagé à venir en phase d'uniformisation, aussi pour analyse /
/// multisite). Logique sortie du controller (pur câblage) pour tenir les seuils de cohésion PMD.
final class GestionnaireFiltres {

    /// Clé du filtre de **recherche texte** (permanent, distinct des critères du menu « + Filtre »).
    private static final String NOM_TEXTE = "texte";

    private final MenuButton menuAjout;
    private final Pane puces;
    private final FiltresAudio filtres;
    private final List<CritereFiltre> criteres;
    private final TextField recherche;

    /// Critères actifs, **par ordre d'ajout** (clé = [CritereFiltre#nom()], valeur = Node éditeur de la puce,
    /// `null` pour un critère booléen). Ordonné pour restituer les puces dans le même ordre, et porteur du
    /// Node éditeur dont on lit/écrit les valeurs lors de la mémorisation de session (#484).
    private final Map<String, Node> actifs = new LinkedHashMap<>();

    GestionnaireFiltres(
            TextField recherche, MenuButton menuAjout, Pane puces, FiltresAudio filtres, List<CritereFiltre> criteres) {
        this.recherche = Objects.requireNonNull(recherche, "recherche");
        this.menuAjout = Objects.requireNonNull(menuAjout, "menuAjout");
        this.puces = Objects.requireNonNull(puces, "puces");
        this.filtres = Objects.requireNonNull(filtres, "filtres");
        this.criteres = List.copyOf(criteres);
        recherche
                .textProperty()
                .addListener((obs, avant, texte) -> filtres.definir(
                        NOM_TEXTE, texte == null || texte.isBlank() ? null : ligne -> correspond(ligne, texte)));
        reconstruireMenu();
    }

    /// Retire **tous** les filtres (texte + puces) : utilisé quand on doit garantir la visibilité d'une
    /// observation ciblée (navigation), quel que soit le filtrage courant.
    void reinitialiser() {
        recherche.clear(); // retire le filtre texte via son écouteur
        puces.getChildren().clear();
        actifs.clear();
        filtres.reinitialiser();
        reconstruireMenu();
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

    private MenuItem itemMenu(CritereFiltre critere) {
        MenuItem item = new MenuItem(critere.libelle());
        item.setOnAction(evenement -> ajouterPuce(critere));
        return item;
    }

    private void ajouterPuce(CritereFiltre critere) {
        Node editeur = critere.editeur(predicat -> filtres.definir(critere.nom(), predicat));
        actifs.put(critere.nom(), editeur);
        puces.getChildren().add(construirePuce(critere, editeur));
        reconstruireMenu();
    }

    private HBox construirePuce(CritereFiltre critere, Node editeur) {
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

    private void retirerPuce(CritereFiltre critere, HBox puce) {
        puces.getChildren().remove(puce);
        actifs.remove(critere.nom());
        filtres.definir(critere.nom(), null);
        reconstruireMenu();
    }

    /// **Photographie** l'état courant des filtres (recherche texte + puces actives avec leurs valeurs),
    /// pour le mémoriser le temps de la session (#484). Chaque puce est décrite par le [CritereFiltre#nom()]
    /// et les valeurs de ses contrôles (index de liste déroulante, valeur de curseur), dans l'ordre d'ajout.
    EtatFiltres capturer() {
        List<EtatCritere> etats = actifs.entrySet().stream()
                .map(entree -> new EtatCritere(entree.getKey(), valeursDe(entree.getValue())))
                .toList();
        return new EtatFiltres(Objects.requireNonNullElse(recherche.getText(), ""), etats);
    }

    /// Restitue un état capturé par [#capturer()] : réinitialise, réapplique la recherche texte puis ré-ajoute
    /// chaque puce (l'éditeur repart de ses valeurs par défaut, appliquées à l'ajout) avant d'y réinjecter les
    /// valeurs mémorisées. Les critères disparus du catalogue et les valeurs surnuméraires sont ignorés.
    void restaurer(EtatFiltres etat) {
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

    private Optional<CritereFiltre> critereParNom(String nom) {
        return criteres.stream().filter(critere -> critere.nom().equals(nom)).findFirst();
    }

    /// Valeurs des contrôles de valeur (`ComboBox`, `Slider`) d'un Node éditeur, dans l'ordre de l'arbre :
    /// index sélectionné pour une liste déroulante, valeur brute pour un curseur. Liste vide si `editeur`
    /// est `null` (critère booléen).
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

    /// Vrai si un des champs cherchables contient `texte` (comparaison **insensible casse/accents**) :
    /// fichier, **espèce retenue** (taxon + vernaculaire observateur `nomEspece`, ou Tadarida à défaut) et
    /// commentaire. On inclut `taxonObservateur`/`nomEspece` pour qu'une observation **corrigée** vers une
    /// autre espèce (visible en « Votre taxon ») soit trouvable en cherchant cette espèce.
    private static boolean correspond(LigneObservationAudio ligne, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return contient(ligne.nomFichier(), aiguille)
                || contient(ligne.taxonTadarida(), aiguille)
                || contient(ligne.nomTadarida(), aiguille)
                || contient(ligne.taxonObservateur(), aiguille)
                || contient(ligne.nomEspece(), aiguille)
                || contient(ligne.commentaire(), aiguille);
    }

    private static boolean contient(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }

    /// État mémorisable des filtres (recherche texte + puces actives), photographié par [#capturer()] et
    /// restitué par [#restaurer(EtatFiltres)]. Deux `EtatFiltres` sont égaux si texte et puces coïncident
    /// (record : égalité de valeur), ce qui rend la mémorisation de session testable simplement.
    record EtatFiltres(String texte, List<EtatCritere> criteres) {}

    /// État d'une puce : son [CritereFiltre#nom()] et les valeurs de ses contrôles (index de liste
    /// déroulante, valeur de curseur) dans l'ordre de l'arbre ; liste vide pour un critère booléen.
    record EtatCritere(String nom, List<Double> valeurs) {}
}
