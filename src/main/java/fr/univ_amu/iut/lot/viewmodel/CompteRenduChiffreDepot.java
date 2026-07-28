package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Barre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.model.BilanDepot;
import java.util.ArrayList;
import java.util.List;

/// Traduit une **fin de dépôt de nuit** en compte rendu chiffré (#2358, #2653), celui que rend
/// [fr.univ_amu.iut.commun.view.PanneauCompteRendu].
///
/// Quatrième et dernier branchement annoncé par le lot 2 de #2350, et le seul que sa clôture avait
/// laissé : le dépôt a déjà une **table de suivi par fichier**, et il fallait décider qui porte quoi
/// plutôt que de brancher en passant. La réponse est un partage : la table garde le **détail par
/// fichier**, la bande porte le **verdict**, les **proportions**, le **volume** et l'**action suivante**.
///
/// Ce que la bande apporte, et que la table ne peut pas donner :
///
/// - la **proportion** d'un coup d'œil, là où une table de dizaines de lignes demande de compter ;
/// - le **volume téléversé**, qu'aucune surface ne disait (#2653) ;
/// - l'**action suivante** : « Lancer la participation » est l'étape ④ du dépôt, et rien ne la
///   désignait à la fin du geste qui la rend possible.
///
/// Le bandeau d'une ligne qu'elle remplace disparaît : deux restitutions du même fait, à deux endroits
/// et dans deux vocabulaires, est précisément ce que ce lot corrige ailleurs.
public final class CompteRenduChiffreDepot {

    private CompteRenduChiffreDepot() {}

    /// Ce que le **plan** sait et que le bilan d'une tentative ne sait pas.
    ///
    /// Le bilan compte ce que **cette tentative** a envoyé ; la table de suivi, elle, connaît le plan
    /// entier et ce qui est en ligne toutes tentatives confondues. Sans elle, un dépôt **interrompu** se
    /// lirait comme un succès : sa tentative peut n'avoir aucun échec alors qu'il reste des fichiers à
    /// envoyer (#1044).
    ///
    /// @param unitesDuPlan nombre total d'unités à déposer pour cette nuit
    /// @param enLigne nombre d'unités effectivement en ligne, toutes tentatives confondues
    /// @param interrompu l'utilisateur a demandé l'arrêt : ni un succès, ni une erreur
    public record Plan(int unitesDuPlan, int enLigne, boolean interrompu) {}

    /// Le compte rendu chiffré d'un dépôt terminé.
    ///
    /// @param bilan ce que la tentative a déposé, raté, et le volume parti
    /// @param plan ce que la table de suivi sait du dépôt entier
    /// @param actions ce que l'écran propose ensuite ; c'est lui qui sait où mènent ses boutons
    public static CompteRenduChiffre de(BilanDepot bilan, Plan plan, List<Action> actions) {
        return new CompteRenduChiffre(
                titre(plan),
                resultat(bilan, plan),
                severite(bilan, plan),
                volumes(bilan),
                ventilation(bilan, plan),
                motifs(bilan),
                avertissements(bilan, plan),
                actions);
    }

    private static String titre(Plan plan) {
        return plan.interrompu() ? "Dépôt interrompu" : "Nuit déposée sur Vigie-Chiro";
    }

    /// « 14 déposées », ou « 9 / 14 déposées » dès qu'il en manque. Compte ce qui est **en ligne**, pas ce
    /// que la tentative vient d'envoyer : c'est la question de l'utilisateur, et une reprise l'aurait
    /// sinon renseigné sur le seul dernier morceau.
    private static String resultat(BilanDepot bilan, Plan plan) {
        int total = total(bilan, plan);
        return plan.enLigne() == total ? total + " déposées" : plan.enLigne() + " / " + total + " déposées";
    }

    /// Le plan fait foi quand il est connu ; à défaut, ce que la tentative a vu passer.
    private static int total(BilanDepot bilan, Plan plan) {
        return plan.unitesDuPlan() > 0
                ? plan.unitesDuPlan()
                : plan.enLigne() + bilan.echecs().size();
    }

    /// Un **échec** est une erreur : l'utilisateur croyait déposer et il manque des fichiers en ligne. Une
    /// **interruption** n'en est pas une - rien n'a raté, il a arrêté - mais ce n'est pas un succès non
    /// plus, puisqu'il manque des fichiers. C'est exactement le registre intermédiaire.
    private static Severite severite(BilanDepot bilan, Plan plan) {
        if (!bilan.echecs().isEmpty()) {
            return Severite.ERREUR;
        }
        return plan.interrompu() || plan.enLigne() < total(bilan, plan) ? Severite.AVERTISSEMENT : Severite.SUCCES;
    }

    /// Le **volume en ligne**, que rien ne disait avant ce lot. Une seule barre : il n'y a pas de second
    /// volume à comparer côté dépôt, contrairement à l'import qui oppose ce qu'il lit à ce qu'il écrit.
    private static List<Barre> volumes(BilanDepot bilan) {
        if (bilan.octetsDeposes() <= 0) {
            return List.of();
        }
        return List.of(Barre.unique(
                "Téléversé",
                new Segment(
                        "téléversé",
                        bilan.octetsDeposes(),
                        Formats.octetsLisibles(bilan.octetsDeposes()),
                        Teinte.REFERENCE)));
    }

    /// Le devenir des unités du plan : en ligne, en échec, et **restantes** quand on a interrompu.
    ///
    /// La troisième part est celle qui empêche un dépôt interrompu de se lire comme un succès. Sans elle,
    /// la barre serait pleine et verte alors qu'il manque des fichiers sur la plateforme.
    private static Ventilation ventilation(BilanDepot bilan, Plan plan) {
        int total = total(bilan, plan);
        if (total == 0) {
            return Ventilation.aucune();
        }
        int restantes = Math.max(0, total - plan.enLigne() - bilan.echecs().size());
        List<Segment> segments = new ArrayList<>();
        ajouterSiPresent(segments, "Déposées", plan.enLigne(), Teinte.RETENU);
        ajouterSiPresent(segments, "En échec", bilan.echecs().size(), Teinte.REFUSE);
        ajouterSiPresent(segments, "Restantes", restantes, Teinte.ECARTE);
        return new Ventilation("Devenir des " + total + " archives du plan", total, segments);
    }

    private static void ajouterSiPresent(List<Segment> segments, String libelle, int quantite, Teinte teinte) {
        if (quantite > 0) {
            segments.add(new Segment(libelle, quantite, String.valueOf(quantite), teinte));
        }
    }

    /// Les échecs, **groupés par cause** quand le moteur en donne une.
    ///
    /// `BilanDepot.echecs` ne porte que des **identifiants d'unité** (« Car-3.zip »), pas leur raison :
    /// celle-ci vit dans la table de suivi, ligne par ligne. Un seul motif les rassemble donc, et il
    /// renvoie là où la cause se lit - le partage assumé entre la bande et la table.
    private static List<Motif> motifs(BilanDepot bilan) {
        if (bilan.echecs().isEmpty()) {
            return List.of();
        }
        return List.of(new Motif("archive(s) en échec, cause détaillée dans la table", bilan.echecs()));
    }

    /// Ce que la ventilation ne porte pas : **quoi faire** de ce qui manque, et ce que la reprise promet.
    private static List<Avertissement> avertissements(BilanDepot bilan, Plan plan) {
        List<Avertissement> avertissements = new ArrayList<>();
        int total = total(bilan, plan);
        if (plan.interrompu()) {
            // Ni un succès ni une erreur : la reprise ne renverra que le reste, et le dire évite qu'on
            // recommence tout par précaution (#1044).
            avertissements.add(Avertissement.de("Vous avez arrêté le dépôt. « Reprendre le dépôt » ne renverra"
                    + " que les " + Math.max(0, total - plan.enLigne()) + " archive(s) manquante(s)."));
        } else if (!bilan.echecs().isEmpty()) {
            avertissements.add(Avertissement.de(bilan.echecs().size()
                    + " archive(s) ne sont pas en ligne : « Reprendre le dépôt » ne renverra que celles-là."));
        }
        if (plan.enLigne() == total && total > 0) {
            avertissements.add(Avertissement.succes(
                    "Toutes les archives de la nuit sont sur Vigie-Chiro. Il reste à lancer la participation"
                            + " pour que la plateforme les analyse."));
        }
        return avertissements;
    }
}
