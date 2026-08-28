package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.model.CarreCandidat;
import java.util.List;
import java.util.Objects;

/// Confronter un carré **déclaré** aux carrés qu'une position rend, sans se soucier d'où ils viennent.
///
/// La règle vit ici, une fois, parce qu'elle a **deux** appelants qui n'interrogent pas la même source :
/// [ControleCarreStoc] demande à la plateforme, la ligne de commande lit le carroyage embarqué. Deux
/// copies dériveraient, et l'écran finirait par dire d'une position ce que `ajouter-point` en nie.
public final class ConfrontationCarre {

    /// Écart de distance en deçà duquel deux carrés ne se départagent pas (#4610).
    ///
    /// **Dérivé de la géométrie, pas choisi** : pour un point à `x` mètres d'un bord, l'écart entre les
    /// deux distances aux centres vaut environ `2x`. Cent mètres désignent donc les points à moins de
    /// 50 m d'une frontière - un point d'écoute n'est pas relevé au mètre près.
    ///
    /// Deux fois la valeur retenue pour la **proposition**, et c'est délibéré : proposer un numéro faux
    /// et plausible se paie cher, tandis qu'ici se taire ne coûte qu'un contrôle en moins.
    public static final double ECART_INDISCERNABLE_METRES = 100;

    private ConfrontationCarre() {}

    /// Le verdict, sachant **tous** les candidats proches et non le seul premier.
    ///
    /// Le carré déclaré concorde s'il figure parmi les **indiscernables** : ceux dont la distance ne se
    /// distingue pas de la plus courte. Sur une frontière, l'observateur a raison quel que soit celui des
    /// deux qu'il a déclaré, et rien ici n'a à trancher une question qui n'a pas de réponse (#4621).
    ///
    /// Hors de cette bande, la divergence se dit, et se dit contre **le plus proche** : c'est ce qui garde
    /// au contrôle son objet, une faute de frappe sur le carré.
    public static VerdictCarre confronter(String carreDeclare, List<CarreCandidat> candidats) {
        Objects.requireNonNull(carreDeclare, "carreDeclare");
        if (candidats.isEmpty()) {
            return new VerdictCarre.HorsGrille();
        }
        double plusCourte = candidats.getFirst().distanceMetres();
        boolean declareEstIndiscernable = candidats.stream()
                .filter(candidat -> candidat.distanceMetres() - plusCourte < ECART_INDISCERNABLE_METRES)
                .anyMatch(candidat -> candidat.numero().equals(carreDeclare));
        return declareEstIndiscernable
                ? new VerdictCarre.Concorde(carreDeclare)
                : new VerdictCarre.Diverge(candidats.getFirst().numero(), carreDeclare);
    }
}
