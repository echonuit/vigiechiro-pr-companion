package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/// Confirmation par défaut (vrai `Alert` modal), factorisant le patron
/// `Alert(CONFIRMATION, …, OK, CANCEL)` jusque-là dupliqué dans les controllers. Implémente
/// [Confirmateur] ; le [Navigateur] et [ConfirmateurModifiable] l'utilisent par défaut, les tests
/// injectent un stub. Un **titre** optionnel personnalise la barre du dialogue (ex. « Supprimer les
/// archives de dépôt ? ») quand le message seul manquerait de contexte.
public final class ConfirmationNavigation implements Confirmateur {

    /// Classe posée sur un compte rendu **porté par un dialogue** : elle lui rend la marge que le
    /// `DialogPane` ne donne pas, là où une modale FXML l'obtient de sa racine. Définie dans
    /// `design.css`, à côté des autres `compte-rendu-*`.
    static final String CLASSE_DANS_UN_DIALOGUE = "compte-rendu-dialogue";

    private final String titre;

    /// Dialogue au titre système par défaut.
    public ConfirmationNavigation() {
        this(null);
    }

    /// Dialogue au **titre** personnalisé (`null` = titre système), sans en-tête.
    public ConfirmationNavigation(String titre) {
        this.titre = titre;
    }

    @Override
    public boolean confirmer(String message) {
        return confirme(dialogue(message));
    }

    /// Confirmation d'un **compte rendu structuré** (#2060) : le dialogue montre un intitulé et ses détails
    /// alignés ([VueCompteRendu]) plutôt qu'une chaîne à puces qui se brise au retour à la ligne.
    @Override
    public boolean confirmer(CompteRendu compteRendu) {
        return confirme(dialogue(compteRendu));
    }

    private static boolean confirme(Alert alerte) {
        return alerte.showAndWait()
                .filter(bouton -> bouton == BoutonsDeDialogue.CONFIRMER)
                .isPresent();
    }

    /// Le dialogue **tel qu'il sera montré**, sans le montrer (#1468).
    ///
    /// Il existe pour les **captures de documentation**. Celles-ci reconstruisaient jusqu'ici les
    /// confirmations **à la main** (`CaptureDialogues`), faute d'un moyen d'obtenir le vrai dialogue sans
    /// bloquer sur `showAndWait`. Et « à l'identique » n'engageait personne : la documentation a montré des
    /// dialogues qui **avaient dérivé** du produit - jusqu'à une confirmation entière qui manquait.
    ///
    /// Désormais la capture et la production passent par **ce même code** : ce que la doc montre est ce que
    /// l'utilisateur verra.
    public Alert dialogue(String message) {
        Alert alerte =
                new Alert(AlertType.CONFIRMATION, message, BoutonsDeDialogue.CONFIRMER, BoutonsDeDialogue.ANNULER);
        Habillage.poser(alerte.getDialogPane());
        if (titre != null) {
            alerte.setTitle(titre);
            alerte.setHeaderText(null);
        }
        return alerte;
    }

    /// Le dialogue portant un **compte rendu structuré** (#2060), tel qu'il sera montré, sans le montrer.
    ///
    /// Le contenu du `DialogPane` n'est plus un texte mais le rendu de [VueCompteRendu] : un `Label` par
    /// détail, dont le retrait est porté par le CSS, de sorte que la continuation d'une ligne longue
    /// s'aligne sous le début du détail et non sous la puce. Comme [#dialogue(String)], il passe par le
    /// **code de production** pour que la capture montre ce que l'utilisateur verra (ADR 0025).
    public Alert dialogue(CompteRendu compteRendu) {
        Alert alerte = new Alert(AlertType.CONFIRMATION, "", BoutonsDeDialogue.CONFIRMER, BoutonsDeDialogue.ANNULER);
        var contenu = VueCompteRendu.rendre(compteRendu, VueCompteRendu.SANS_PLAFOND);
        // Le `DialogPane` ne rembourre presque pas son contenu, là où une modale FXML s'en charge par sa
        // racine : sans cette classe, le bloc touche le bord du dialogue (revue visuelle de #2225).
        contenu.getStyleClass().add(CLASSE_DANS_UN_DIALOGUE);
        alerte.getDialogPane().setContent(contenu);
        Habillage.poser(alerte.getDialogPane());
        if (titre != null) {
            alerte.setTitle(titre);
            alerte.setHeaderText(null);
        }
        return alerte;
    }
}
