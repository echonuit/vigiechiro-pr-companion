package fr.univ_amu.iut.commun.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Rend un [CompteRendu] **en texte**, pour la ligne de commande.
///
/// Jumeau de [fr.univ_amu.iut.commun.view.VueCompteRendu] : même contenu, autre médium. L'un construit
/// des nœuds JavaFX, l'autre des lignes ; les deux partent de la **même structure**, donc les deux
/// surfaces disent les mêmes faits dans les mêmes mots.
///
/// ## Pourquoi ce composant existe
///
/// Il n'existait pas, et la Javadoc de `VueCompteRendu` le supposait déjà : elle justifiait son plafond
/// de détails en écrivant « une sortie de commande se filtre et n'en veut aucun ». Ce consommateur était
/// imaginaire - les trois usages de `SANS_PLAFOND` étaient tous des vues.
///
/// Pendant ce temps, `Reactiver` et `PublierCorrectionsVigieChiro` **rédigeaient à la main** leur propre
/// narration des mêmes rapports. Deux rédactions pour un contenu : deux endroits où corriger une
/// formulation, et rien qui garantisse qu'elles disent la même chose (clôture #1990).
///
/// ## Les glyphes sont ici légitimes
///
/// L'[ADR 0035](../../../../../../dev-docs/decisions/0035-un-pictogramme-est-une-icone-pas-un-caractere.md)
/// point 6 exempte explicitement la console : un terminal ne rend pas de `FontIcon`, le caractère y est
/// le **seul** moyen d'écrire une sévérité. C'est la surface où la règle « un pictogramme est une icône »
/// ne s'applique pas - et la seule.
public final class TexteCompteRendu {

    /// Le marqueur de chaque sévérité. Volontairement en ASCII étendu simple : une sortie de commande
    /// traverse des terminaux dont on ne maîtrise pas la police.
    private static final Map<Severite, String> MARQUEUR = Map.of(
            Severite.SUCCES, "✓",
            Severite.INFO, "·",
            Severite.AVERTISSEMENT, "⚠",
            Severite.ERREUR, "✗");

    private static final String RETRAIT = "      ";

    private TexteCompteRendu() {}

    /// Rend le compte rendu, une ligne par constat, ses détails en retrait. Chaîne **vide** si le compte
    /// rendu l'est : une commande silencieuse vaut mieux qu'un titre sans contenu.
    ///
    /// Tous les détails sont rendus, sans plafond : une sortie de commande se **filtre** (`grep`) et se
    /// **redirige**, là où une modale doit tenir à l'écran. C'est la différence de médium qui justifie la
    /// différence de plafond, et c'est ce que la vue ne peut pas faire.
    public static String rendre(CompteRendu rendu) {
        if (rendu.estVide()) {
            return "";
        }
        List<String> lignes = new ArrayList<>();
        ajouterSiRenseigne(lignes, rendu.titre());
        ajouterSiRenseigne(lignes, rendu.preambule());
        for (Constat constat : rendu.constats()) {
            lignes.add(MARQUEUR.get(constat.severite()) + " " + constat.fait());
            for (Detail detail : constat.details()) {
                lignes.add(RETRAIT + texte(detail));
            }
        }
        ajouterSiRenseigne(lignes, rendu.conclusion());
        return String.join("\n", lignes);
    }

    private static String texte(Detail detail) {
        return detail.precision().isBlank() ? detail.sujet() : detail.sujet() + " : " + detail.precision();
    }

    private static void ajouterSiRenseigne(List<String> lignes, String texte) {
        if (!texte.isBlank()) {
            lignes.add(texte);
        }
    }
}
