package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.CarreCandidat;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Confronte la position d'un point d'écoute à la **grille STOC officielle** (#733).
///
/// L'issue demandait de *« proposer le n° de carré automatiquement depuis le GPS plutôt que de le saisir à
/// la main »*, à la création d'un point. Le n° de carré n'appartient pourtant **pas** au point : il
/// appartient au **site**, et il est saisi bien avant, à sa création : écran où aucune coordonnée n'est
/// connue. Il n'y a donc rien à pré-remplir dans la modale de point.
///
/// Ce qu'on peut faire, et qui sert la même intention (*fiabiliser le n° de carré*), c'est **vérifier** :
/// dès qu'un point reçoit ses coordonnées, demander à la plateforme dans quel carré elles tombent, et le
/// confronter au carré déclaré par le site. Une faute de frappe sur le carré ne se voyait jusqu'ici qu'au
/// **dépôt**, très loin en aval, après avoir contaminé le préfixe R6 de tous les fichiers.
///
/// **Bloquant** (réseau) : à appeler hors du fil JavaFX.
public class ControleCarreStoc {

    private static final Logger LOG = Logger.getLogger(ControleCarreStoc.class.getName());

    /// Écart de distance en deçà duquel deux carrés ne se départagent pas (#4610).
    ///
    /// **Dérivé de la géométrie, pas choisi** : pour un point à `x` mètres d'un bord, l'écart entre les
    /// deux distances aux centres vaut environ `2x`. Cent mètres désignent donc les points à moins de
    /// 50 m d'une frontière - un point d'écoute n'est pas relevé au mètre près, et 5 m de décalage
    /// suffisaient à faire basculer le verdict.
    ///
    /// Deux fois la valeur retenue pour la **proposition**, et c'est délibéré : proposer un numéro faux
    /// et plausible se paie cher, tandis qu'ici se taire ne coûte qu'un contrôle en moins. Le contrôle
    /// peut donc être plus prudent que la proposition sans que l'un contredise l'autre.
    private static final double ECART_INDISCERNABLE_METRES = 100;

    private final ClientVigieChiro client;

    public ControleCarreStoc(ClientVigieChiro client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /// Verdict de la grille STOC pour la position `(latitude, longitude)`, confrontée au carré déclaré par
    /// le site.
    ///
    /// Toute issue **non-succès** (hors connexion, plateforme injoignable, refus) rend
    /// [VerdictCarre.Indisponible] : le contrôle est un **confort**, jamais une condition. Il ne doit ni
    /// bloquer la saisie, ni la commenter à tort : travailler hors ligne reste normal.
    public VerdictCarre confronter(String carreDeclare, double latitude, double longitude) {
        Objects.requireNonNull(carreDeclare, "carreDeclare");
        return switch (client.carresStocProches(latitude, longitude)) {
            case ReponseApi.Succes<List<CarreCandidat>>(List<CarreCandidat> candidats) ->
                verdict(carreDeclare, candidats);
            case ReponseApi.NonConnecte<List<CarreCandidat>> nonConnecte -> new VerdictCarre.Indisponible();
            case ReponseApi.Injoignable<List<CarreCandidat>>(String cause) -> {
                LOG.log(Level.FINE, () -> "Contrôle du carré STOC ignoré (Vigie-Chiro injoignable : " + cause + ")");
                yield new VerdictCarre.Indisponible();
            }
            case ReponseApi.Refuse<List<CarreCandidat>>(int statut, String corps) -> {
                LOG.log(Level.FINE, () -> "Contrôle du carré STOC ignoré (refus HTTP " + statut + ")");
                yield new VerdictCarre.Indisponible();
            }
        };
    }

    /// Le verdict, sachant **tous** les candidats proches et non le seul premier.
    ///
    /// Le carré déclaré concorde s'il figure parmi les **indiscernables** : ceux dont la distance ne se
    /// distingue pas de la plus courte. Sur une frontière, l'observateur a raison quel que soit celui des
    /// deux qu'il a déclaré, et le contrôle n'a pas à trancher une question qui n'a pas de réponse.
    ///
    /// Hors de cette bande, la divergence se dit encore, et se dit contre **le plus proche** : c'est ce
    /// qui garde au contrôle son objet, une faute de frappe sur le carré.
    private static VerdictCarre verdict(String carreDeclare, List<CarreCandidat> candidats) {
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
