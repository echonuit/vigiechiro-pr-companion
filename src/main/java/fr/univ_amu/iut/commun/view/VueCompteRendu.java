package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
import java.util.List;
import java.util.Map;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/// Rend un [CompteRendu] **toujours de la même façon**, quel que soit l'écran (ADR 0031).
///
/// C'est l'objet de ce composant : un observateur qui enchaîne un import, un dépôt et une réactivation
/// doit **reconnaître la structure avant de lire**. Quatre écrans assemblaient jusqu'ici leur propre
/// texte, ce qui lui imposait trois grammaires pour la même intention.
///
/// ## Ce n'est pas un bandeau
///
/// [BandeauRetour] lie des nœuds **déjà déclarés** dans le FXML : un retour d'opération tient en une
/// ligne, sa forme est connue d'avance. Un compte rendu, non - il a autant de constats et de détails
/// qu'il y a de choses à dire. Le composant les **construit** donc, et vit dans un conteneur qui lui est
/// propre. Un compte rendu n'a jamais sa place dans un bandeau : l'y loger reviendrait à le tronquer.
///
/// ## Chaque détail est un nœud
///
/// C'est ce qui répare le défaut d'indentation (#1987) sans avoir à le traiter : dans un `Label` unique,
/// une puce trop longue repartait à la ligne **alignée sur la marge**, indiscernable de la puce suivante.
/// Ici chaque détail porte son propre nœud et son propre retrait, donc sa continuation s'aligne sous son
/// texte.
public final class VueCompteRendu {

    /// Classe CSS du conteneur : c'est **elle** qui rend un compte rendu reconnaissable d'un écran à
    /// l'autre. Les styles vivent dans `commun/view/design.css`, que les écrans attachent déjà.
    public static final String CLASSE_RACINE = "compte-rendu";

    private static final Map<Severite, String> CLASSE_CONSTAT = Map.of(
            Severite.SUCCES, "compte-rendu-succes",
            Severite.INFO, "compte-rendu-info",
            Severite.AVERTISSEMENT, "compte-rendu-avertissement",
            Severite.ERREUR, "compte-rendu-erreur");

    private VueCompteRendu() {}

    /// Construit le rendu d'un compte rendu.
    ///
    /// @param rendu ce qu'il y a à dire ; un compte rendu vide rend un conteneur vide, que l'appelant
    ///     peut masquer - mieux vaut ne rien afficher qu'un cadre sans contenu
    /// @param plafondDetails nombre de détails montrés par constat avant de résumer (« … et N autre(s) »).
    ///     La **surface** en décide : une modale doit rester lisible, une sortie de commande se filtre et
    ///     n'en veut aucun. Utiliser [#SANS_PLAFOND] pour tout montrer.
    public static VBox rendre(CompteRendu rendu, int plafondDetails) {
        VBox racine = new VBox();
        racine.getStyleClass().add(CLASSE_RACINE);
        if (rendu.estVide()) {
            return racine;
        }
        ajouterSiRenseigne(racine, rendu.titre(), "compte-rendu-titre");
        ajouterSiRenseigne(racine, rendu.preambule(), "compte-rendu-preambule");
        for (Constat constat : rendu.constats()) {
            racine.getChildren()
                    .add(ligne(constat.fait(), "compte-rendu-fait", CLASSE_CONSTAT.get(constat.severite())));
            ajouterDetails(racine, constat.details(), plafondDetails);
        }
        ajouterSiRenseigne(racine, rendu.conclusion(), "compte-rendu-conclusion");
        return racine;
    }

    /// Tout montrer : ce que fait la ligne de commande, dont la sortie se filtre.
    public static final int SANS_PLAFOND = Integer.MAX_VALUE;

    private static void ajouterDetails(VBox racine, List<Detail> details, int plafond) {
        details.stream()
                .limit(Math.max(plafond, 0))
                .forEach(detail -> racine.getChildren().add(ligne(texte(detail), "compte-rendu-detail")));
        int restants = details.size() - Math.max(plafond, 0);
        if (restants > 0) {
            racine.getChildren()
                    .add(ligne("… et " + restants + " autre(s).", "compte-rendu-detail", "compte-rendu-reste"));
        }
    }

    /// Sujet et précision restent **séparés jusqu'ici** : le modèle ne les fond pas, pour qu'une surface
    /// puisse un jour les rendre différemment (le sujet en évidence, la précision en second plan).
    private static String texte(Detail detail) {
        return detail.precision().isBlank() ? detail.sujet() : detail.sujet() + " : " + detail.precision();
    }

    private static void ajouterSiRenseigne(VBox racine, String texte, String classe) {
        if (!texte.isBlank()) {
            racine.getChildren().add(ligne(texte, classe));
        }
    }

    private static Label ligne(String texte, String... classes) {
        Label label = new Label(texte);
        label.setWrapText(true);
        label.getStyleClass().addAll(classes);
        return label;
    }
}
