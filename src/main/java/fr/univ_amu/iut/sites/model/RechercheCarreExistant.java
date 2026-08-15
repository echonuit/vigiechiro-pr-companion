package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.Severite;
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
    ///
    /// Le verdict porte **son message et sa gravité**, comme [VerdictCarre] : la vue les rend, elle ne
    /// les compose pas, et ils restent éprouvables sans IHM.
    public sealed interface Verdict {

        /// Le message à afficher, tel quel.
        String message();

        /// La gravité, dont la vue tire sa couleur et son icône.
        Severite severite();

        /// Aucun site ne porte ce carré : il est libre, la déclaration a un sens.
        record Inexistant() implements Verdict {
            @Override
            public String message() {
                return "Ce carré n'existe pas encore sur Vigie-Chiro : vous pouvez le déclarer ici.";
            }

            @Override
            public Severite severite() {
                return Severite.SUCCES;
            }
        }

        /// Le carré est **déjà déclaré**. Les titres disent sous quel protocole, ce qui compte : un même
        /// carré porte un site par protocole, et « il existe » sans dire lequel n'aide pas.
        record DejaDeclare(List<String> titres) implements Verdict {

            /// Le message dit **quoi faire**, et non seulement ce qui est.
            ///
            /// ⚠️ Il a dit deux fois le contraire de ce qu'il fallait, et les deux erreurs se corrigent
            /// ensemble (#3806) :
            ///
            /// - il renvoyait à « Mes sites », « Récupérer depuis Vigie-Chiro ». Cette synchronisation
            ///   part des **participations** : elle n'atteint que les carrés où une nuit est **déjà**
            ///   déposée. Sur un carré fraîchement activé - le cas même qui a ouvert #3458 - le geste ne
            ///   fait rien ;
            /// - il disait « ne le redéclarez pas », alors que préparer une nuit *opportuniste* commence
            ///   précisément par déclarer le carré. Ce qui manquait au geste n'était pas de l'empêcher,
            ///   c'était de le **rattacher**.
            @Override
            public String message() {
                return "Ce carré existe déjà sur Vigie-Chiro (" + String.join(", ", titres)
                        + "). Récupérez-le ici : il sera rattaché au carré de la plateforme, avec ses"
                        + " points d'écoute déjà positionnés.";
            }

            @Override
            public Severite severite() {
                return Severite.AVERTISSEMENT;
            }
        }

        /// On ne sait pas : hors connexion, plateforme injoignable, ou refus.
        ///
        /// ⚠️ **Ce n'est pas « il n'existe pas »**, et les confondre serait le pire des deux mondes :
        /// l'utilisateur déclarerait un carré déjà pris en croyant avoir vérifié. C'est exactement la
        /// panne que cette classe existe pour éviter.
        record Indisponible() implements Verdict {

            /// ⚠️ Ce cas **parle**, là où [ControleCarreStoc] se tait. Le contrôle du carré STOC est
            /// automatique : son silence est discret. Ici l'utilisateur a **cliqué** ; ne rien afficher
            /// lui ferait croire que le geste a échoué, ou pire, que le carré est libre.
            @Override
            public String message() {
                return "Vérification impossible : Vigie-Chiro est injoignable ou vous n'êtes pas"
                        + " connecté. Ce carré n'a donc PAS été vérifié.";
            }

            @Override
            public Severite severite() {
                return Severite.INFO;
            }
        }
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
