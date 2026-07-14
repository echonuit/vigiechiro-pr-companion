package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Confronte la position d'un point d'écoute à la **grille STOC officielle** (#733).
///
/// L'issue demandait de *« proposer le n° de carré automatiquement depuis le GPS plutôt que de le saisir à
/// la main »*, à la création d'un point. Le n° de carré n'appartient pourtant **pas** au point : il
/// appartient au **site**, et il est saisi bien avant, à sa création — écran où aucune coordonnée n'est
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

    private final ClientVigieChiro client;

    public ControleCarreStoc(ClientVigieChiro client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /// Verdict de la grille STOC pour la position `(latitude, longitude)`, confrontée au carré déclaré par
    /// le site.
    ///
    /// Toute issue **non-succès** (hors connexion, plateforme injoignable, refus) rend
    /// [VerdictCarre.Indisponible] : le contrôle est un **confort**, jamais une condition. Il ne doit ni
    /// bloquer la saisie, ni la commenter à tort — travailler hors ligne reste normal.
    public VerdictCarre confronter(String carreDeclare, double latitude, double longitude) {
        Objects.requireNonNull(carreDeclare, "carreDeclare");
        return switch (client.carreStoc(latitude, longitude)) {
            case ReponseApi.Succes<Optional<String>>(Optional<String> numero) -> verdict(carreDeclare, numero);
            case ReponseApi.NonConnecte<Optional<String>> nonConnecte -> new VerdictCarre.Indisponible();
            case ReponseApi.Injoignable<Optional<String>>(String cause) -> {
                LOG.log(Level.FINE, () -> "Contrôle du carré STOC ignoré (VigieChiro injoignable : " + cause + ")");
                yield new VerdictCarre.Indisponible();
            }
            case ReponseApi.Refuse<Optional<String>>(int statut, String corps) -> {
                LOG.log(Level.FINE, () -> "Contrôle du carré STOC ignoré (refus HTTP " + statut + ")");
                yield new VerdictCarre.Indisponible();
            }
        };
    }

    private static VerdictCarre verdict(String carreDeclare, Optional<String> numero) {
        return numero.map(officiel -> officiel.equals(carreDeclare)
                        ? (VerdictCarre) new VerdictCarre.Concorde(officiel)
                        : new VerdictCarre.Diverge(officiel, carreDeclare))
                .orElseGet(VerdictCarre.HorsGrille::new);
    }
}
