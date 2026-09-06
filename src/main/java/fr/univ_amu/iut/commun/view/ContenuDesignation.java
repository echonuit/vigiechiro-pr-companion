package fr.univ_amu.iut.commun.view;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/// Contenu du dialogue de désignation d'un fichier, **sans sa fenêtre** (ADR 5307, changement
/// `selecteur-de-repli`).
///
/// Séparé pour la raison habituelle du socle, et c'est le patron de [ContenuChoixSauvegarde] : la
/// fenêtre appellera `showAndWait()`, qui fige un test headless ; le contenu, lui, se monte dans une
/// scène de test et se regarde.
///
/// **Il n'imite pas un dialogue du système**, et c'est une décision. La documentation montre un geste
/// que le lecteur refera ; un dialogue qui ressemble au sien sans l'être le tromperait. Il garde le
/// style de l'application et n'emprunte au genre que les repères : savoir où l'on est, voir ce qu'il
/// y a, nommer ce qu'on écrit.
public final class ContenuDesignation {

    public static final String ID_CHEMIN = "champCheminDesignation";
    public static final String ID_LISTE = "listeDesignation";
    public static final String ID_NOM = "champNomDesignation";
    public static final String ID_MESSAGE = "messageDesignation";
    public static final String ID_VALIDER = "boutonValiderDesignation";
    public static final String ID_ANNULER = "boutonAnnulerDesignation";
    public static final String ID_REMONTER = "boutonRemonterDesignation";
    public static final String ID_NOUVEAU_DOSSIER = "boutonNouveauDossierDesignation";

    /// Ce que le dialogue demande. Les trois valeurs sont les trois méthodes de [SelecteurFichier],
    /// et non trois apparences : un dossier ne se choisit pas comme un nom de fichier à écrire.
    public enum Mode {
        DOSSIER,
        FICHIER,
        ENREGISTREMENT
    }

    /// Le nom que porte une entrée listée, et si c'est un dossier. Un record plutôt qu'un `Path` nu :
    /// la liste doit distinguer les deux sans relire le disque à chaque cellule.
    public record Entree(String nom, boolean dossier) {
        @Override
        public String toString() {
            return dossier ? nom + "/" : nom;
        }
    }

    private final VBox racine;
    private final TextField champChemin = new TextField();
    private final ListView<Entree> liste = new ListView<>();
    private final TextField champNom = new TextField();
    private final Label message = new Label();
    private final Mode mode;
    private final FiltreFichier filtre;

    private final EntreesDuDossier lecture;

    private Path dossierCourant;

    /// @param mode ce que le dialogue demande
    /// @param depart dossier d'ouverture ; s'il n'est pas lisible, la racine de l'utilisateur sert
    /// @param nomPropose nom pré-rempli en mode enregistrement, ignoré sinon
    /// @param filtre types proposés en mode fichier ou enregistrement, `null` pour un dossier
    /// @param surValider reçoit le chemin désigné
    /// @param surAnnuler abandon, qui ne rend rien
    public ContenuDesignation(
            Mode mode,
            Path depart,
            String nomPropose,
            FiltreFichier filtre,
            Consumer<Path> surValider,
            Runnable surAnnuler) {
        this.mode = mode;
        this.filtre = filtre;
        this.lecture = new EntreesDuDossier(mode, filtre);
        this.dossierCourant = lisible(depart) ? depart : Path.of(System.getProperty("user.home"));

        champChemin.setId(ID_CHEMIN);
        liste.setId(ID_LISTE);
        champNom.setId(ID_NOM);
        message.setId(ID_MESSAGE);

        champNom.setText(nomPropose == null ? "" : nomPropose);
        champNom.setVisible(mode == Mode.ENREGISTREMENT);
        champNom.setManaged(mode == Mode.ENREGISTREMENT);

        // La saisie d un chemin se VALIDE a la frappe d entree, et non a chaque caractere : sinon un
        // chemin en cours de frappe serait refuse a chaque lettre, et le message deviendrait du bruit.
        champChemin.setOnAction(evenement -> allerVers(champChemin.getText()));

        liste.setOnMouseClicked(evenement -> {
            if (evenement.getClickCount() == 2) {
                ouvrirLaSelection();
            }
        });

        Button remonter = bouton(ID_REMONTER, "Dossier parent", this::remonter);
        Button nouveauDossier = bouton(ID_NOUVEAU_DOSSIER, "Nouveau dossier", this::creerUnDossier);
        nouveauDossier.setVisible(mode == Mode.ENREGISTREMENT);
        nouveauDossier.setManaged(mode == Mode.ENREGISTREMENT);

        Button valider =
                bouton(ID_VALIDER, libelleDeValidation(), () -> designer().ifPresent(surValider));
        Button annuler = bouton(ID_ANNULER, "Annuler", surAnnuler);

        HBox barreDuHaut = new HBox(8, champChemin, remonter);
        HBox.setHgrow(champChemin, Priority.ALWAYS);
        HBox barreDuBas = new HBox(8, champNom, nouveauDossier, annuler, valider);
        HBox.setHgrow(champNom, Priority.ALWAYS);
        // À DROITE, comme le pied de `ContenuChoixSauvegarde`. Le `Hgrow` ci-dessus ne suffit pas :
        // `champNom` n'est managé qu'en mode ENREGISTREMENT, si bien qu'en DOSSIER et en FICHIER -
        // les deux modes que le produit ouvre le plus - rien ne poussait les boutons, et ils
        // collaient à gauche. Vu sur le clip du parcours, pas par un test.
        barreDuBas.setAlignment(Pos.CENTER_RIGHT);

        racine = new VBox(10, barreDuHaut, liste, message, barreDuBas);
        racine.setPadding(new Insets(14));
        VBox.setVgrow(liste, Priority.ALWAYS);

        afficher(dossierCourant);
    }

    public Region racine() {
        return racine;
    }

    /// Le dossier affiché. Exposé parce que c'est ce qu'un test regarde après une navigation.
    public Path dossierCourant() {
        return dossierCourant;
    }

    /// Ce que le dialogue rendrait si l'on validait maintenant, ou rien.
    ///
    /// En mode enregistrement, c'est le dossier courant joint au nom saisi : un nom vide ne désigne
    /// rien, et rendre le dossier seul écrirait à un endroit que l'utilisateur n'a pas nommé.
    public Optional<Path> designer() {
        if (mode == Mode.ENREGISTREMENT) {
            String nom = champNom.getText() == null ? "" : champNom.getText().trim();
            return nom.isEmpty() ? Optional.empty() : Optional.of(dossierCourant.resolve(nom));
        }
        Entree choisie = liste.getSelectionModel().getSelectedItem();
        if (mode == Mode.DOSSIER) {
            return Optional.of(
                    choisie != null && choisie.dossier() ? dossierCourant.resolve(choisie.nom()) : dossierCourant);
        }
        return choisie == null || choisie.dossier()
                ? Optional.empty()
                : Optional.of(dossierCourant.resolve(choisie.nom()));
    }

    /// Va au chemin SAISI, ou refuse en le disant.
    ///
    /// Le refus est le point de cette méthode. Un chemin qu'on ne peut pas lire produirait sinon une
    /// liste vide, que l'utilisateur lirait comme « ce dossier ne contient rien » alors qu'il veut
    /// dire « je ne peux pas y aller » (ADR 2748). Sous bac à sable, les deux arrivent souvent.
    public void allerVers(String saisi) {
        if (saisi == null || saisi.isBlank()) {
            return;
        }
        Path vise;
        try {
            vise = Path.of(saisi.trim());
        } catch (InvalidPathException chemin_invalide) {
            refuser("Ce n'est pas un chemin valide : " + saisi.trim());
            return;
        }
        if (!Files.exists(vise)) {
            refuser("Ce chemin n'existe pas : " + vise);
            return;
        }
        if (!lisible(vise)) {
            refuser("Ce chemin existe, mais l'application ne peut pas le lire : " + vise);
            return;
        }
        afficher(Files.isDirectory(vise) ? vise : vise.getParent());
    }

    /// Crée un dossier au nom saisi, et y entre.
    ///
    /// Le nom vient du champ de nom : un sélecteur de destination sert souvent à écrire là où rien
    /// n'existe encore, et demander un second champ pour cela ferait deux saisies pour un geste.
    /// Remonte au dossier parent, ou ne fait rien s'il n'y en a pas.
    ///
    /// Publique comme [#creerUnDossier] et [#allerVers], et pour la même raison : le geste vivait dans
    /// une lambda anonyme du bouton, donc sans porte pour l'éprouver. C'est ce que la passe 6 de la
    /// clôture de #5282 a trouvé - le seul des six gestes du dialogue qu'aucun cas ne touchait.
    public void remonter() {
        Path parent = dossierCourant.getParent();
        if (parent != null) {
            afficher(parent);
        }
    }

    public void creerUnDossier() {
        String nom = champNom.getText() == null ? "" : champNom.getText().trim();
        if (nom.isEmpty()) {
            refuser("Donnez d'abord un nom, il servira au dossier.");
            return;
        }
        Path cible = dossierCourant.resolve(nom);
        try {
            Files.createDirectories(cible);
        } catch (IOException echec) {
            refuser("Ce dossier n'a pas pu être créé : " + echec.getMessage());
            return;
        }
        champNom.clear();
        afficher(cible);
    }

    /// Ouvre l'entrée sélectionnée quand c'est un dossier.
    public void ouvrirLaSelection() {
        Entree choisie = liste.getSelectionModel().getSelectedItem();
        if (choisie != null && choisie.dossier()) {
            afficher(dossierCourant.resolve(choisie.nom()));
        }
    }

    /// Le message affiché, vide quand il n'y en a pas. Exposé pour que les tests lisent ce que
    /// l'utilisateur lit.
    public String message() {
        return message.getText();
    }

    /// Ce que la liste montre, dans son ordre.
    public List<Entree> entrees() {
        return List.copyOf(liste.getItems());
    }

    // ---------------------------------------------------------------------------------------

    private void afficher(Path dossier) {
        this.dossierCourant = dossier;
        champChemin.setText(dossier.toString());
        message.setText("");
        liste.setItems(FXCollections.observableArrayList(lire(dossier)));
    }

    /// Les entrées d'un dossier : les sous-dossiers d'abord, puis les fichiers que le filtre retient.
    ///
    /// Les fichiers cachés ne sont PAS montrés. Le dialogue du système les propose ; celui-ci ne le
    /// fait pas, et c'est plus facile à ajouter qu'à retirer. Le design du changement laissait la
    /// question ouverte parce qu'elle ne touche ni les exigences ni le découpage.
    private List<Entree> lire(Path dossier) {
        List<Entree> lues = lecture.de(dossier);
        if (lues == null) {
            message.setText(EntreesDuDossier.raisonDeLectureIncomplete());
            return List.of();
        }
        return lues;
    }

    private void refuser(String raison) {
        message.setText(raison);
    }

    private String libelleDeValidation() {
        return switch (mode) {
            case DOSSIER -> "Choisir ce dossier";
            case FICHIER -> "Ouvrir";
            case ENREGISTREMENT -> "Enregistrer";
        };
    }

    private static boolean lisible(Path chemin) {
        return chemin != null && Files.exists(chemin) && Files.isReadable(chemin);
    }

    private static Button bouton(String id, String libelle, Runnable action) {
        Button bouton = new Button(libelle);
        bouton.setId(id);
        bouton.getStyleClass().add("bouton-secondaire");
        bouton.setOnAction(evenement -> action.run());
        return bouton;
    }
}
