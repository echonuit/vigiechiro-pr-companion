package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Répond à **« ce carré existe-t-il déjà sur Vigie-Chiro ? »** avant qu'on ne le déclare (#3458).
///
/// ## Pourquoi cette classe existe
///
/// C'est la question qu'un utilisateur a posée, mot pour mot : *« Je n'ai pas trouvé de solution pour
/// vérifier s'il existait déjà »*. Ne pouvant y répondre depuis l'application, il est allé activer le
/// carré sur le portail, l'a redéclaré ici, et le dépôt a échoué - loin de la cause.
///
/// La ligne de commande savait déjà répondre ; aucun écran ne le pouvait (#3769 pour la dette
/// symétrique côté CLI).
///
/// **Bloquant** (réseau) : à appeler hors du fil JavaFX. Réponse sous la seconde - une requête, pas les
/// deux cents pages du catalogue.
public class RechercheCarreExistant {

    private static final Logger LOG = Logger.getLogger(RechercheCarreExistant.class.getName());

    private final ClientVigieChiro client;

    public RechercheCarreExistant(ClientVigieChiro client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /// Ce que la plateforme répond sur un numéro de carré.
    public sealed interface Verdict {

        /// Aucun site ne porte ce carré : il est libre, la déclaration a un sens.
        record Inexistant() implements Verdict {}

        /// Le carré est **déjà déclaré**. Les titres disent sous quel protocole, ce qui compte : un même
        /// carré porte un site par protocole, et « il existe » sans dire lequel n'aide pas.
        record DejaDeclare(List<String> titres) implements Verdict {}

        /// On ne sait pas : hors connexion, plateforme injoignable, ou refus.
        ///
        /// ⚠️ **Ce n'est pas « il n'existe pas »**, et les confondre serait le pire des deux mondes :
        /// l'utilisateur déclarerait un carré déjà pris en croyant avoir vérifié. C'est exactement la
        /// panne que cette classe existe pour éviter.
        record Indisponible() implements Verdict {}
    }

    /// Cherche `numeroCarre` sur la plateforme.
    ///
    /// Toute issue non-succès rend [Verdict.Indisponible] : la vérification est un **confort**, jamais
    /// une condition. Travailler hors ligne reste normal, et elle ne doit ni bloquer la déclaration ni
    /// la commenter à tort - même patron que [ControleCarreStoc].
    public Verdict chercher(String numeroCarre) {
        Objects.requireNonNull(numeroCarre, "numeroCarre");
        return switch (client.chercherCarre(numeroCarre)) {
            case ReponseApi.Succes<List<SiteVigieChiro>>(List<SiteVigieChiro> trouves) -> verdict(trouves);
            case ReponseApi.NonConnecte<List<SiteVigieChiro>> nonConnecte -> new Verdict.Indisponible();
            case ReponseApi.Injoignable<List<SiteVigieChiro>>(String cause) -> {
                LOG.log(Level.FINE, () -> "Recherche du carré ignorée (Vigie-Chiro injoignable : " + cause + ")");
                yield new Verdict.Indisponible();
            }
            case ReponseApi.Refuse<List<SiteVigieChiro>>(int statut, String corps) -> {
                LOG.log(Level.FINE, () -> "Recherche du carré ignorée (refus HTTP " + statut + ")");
                yield new Verdict.Indisponible();
            }
        };
    }

    private static Verdict verdict(List<SiteVigieChiro> trouves) {
        if (trouves.isEmpty()) {
            return new Verdict.Inexistant();
        }
        return new Verdict.DejaDeclare(trouves.stream()
                .map(SiteVigieChiro::titre)
                .filter(Objects::nonNull)
                .toList());
    }
}
