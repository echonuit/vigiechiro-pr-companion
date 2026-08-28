package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.model.CarreCandidat;
import java.util.List;

/// Les carrés qu'une position ne départage pas, **à deux seuils** qui ne sont pas interchangeables.
///
/// Deux classes calculaient cette bande, avec deux valeurs pour la même grandeur géométrique. L'écart
/// est **délibéré**, et c'est ce que ce type existe pour porter : sans lui, il se lisait comme une
/// incohérence, et le premier lecteur pressé les aurait unifiées - dans un sens ou dans l'autre, selon
/// la classe qu'il aurait ouverte.
///
/// | Usage | Seuil | Ce que coûte l'erreur |
/// |---|---|---|
/// | **Proposer** un numéro qu'on écrit dans le champ | [#POUR_PROPOSER] | un numéro faux et plausible, validé sans se
/// relire |
/// | **Contrôler** un numéro déjà écrit | [#POUR_CONTROLER] | un contrôle en moins |
///
/// Les deux se dérivent de la même géométrie : pour un point à `x` mètres d'un bord, l'écart entre les
/// deux distances aux centres vaut environ `2x`. Le seuil se lit donc en doublant la marge visée.
///
/// Constaté à la passe 7 de la clôture du chantier #4671, et tranché plutôt qu'assumé - comme
/// [fr.univ_amu.iut.commun.model.ConversionGeographique] l'avait été pour les degrés (#4673).
public final class BandeDesIndiscernables {

    /// Cinquante mètres, soit les points à moins de **25 m** d'une frontière : l'ordre de grandeur de ce
    /// qu'on vise en cliquant sur une carte.
    public static final double POUR_PROPOSER = 50;

    /// Cent mètres, soit les points à moins de **50 m** d'une frontière (#4610) : un point d'écoute
    /// n'est pas relevé au mètre près, et l'observateur a déjà écrit son numéro.
    public static final double POUR_CONTROLER = 100;

    private BandeDesIndiscernables() {}

    /// Les candidats dont la distance ne se distingue pas de la plus courte, **le plus proche en tête**.
    ///
    /// La borne stricte ne se distingue pas de son contraire - un écart exactement égal au seuil n'est
    /// pas atteignable sur des distances calculées depuis des degrés - et PIT laisse donc survivre sa
    /// mutation. Ce qui se teste est la **valeur** de chaque seuil, encadrée de part et d'autre.
    public static List<CarreCandidat> dans(List<CarreCandidat> candidats, double seuilMetres) {
        if (candidats.isEmpty()) {
            return List.of();
        }
        double plusCourte = candidats.getFirst().distanceMetres();
        return candidats.stream()
                .filter(candidat -> candidat.distanceMetres() - plusCourte < seuilMetres)
                .toList();
    }
}
