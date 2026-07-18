package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `recuperer-vigiechiro` (#1181) : rejoue les **rapprocheurs** Vigie-Chiro (référentiel des
/// taxons, sites et points) sans passer par la modale de connexion - l'équivalent scriptable de ce
/// que l'IHM déclenche à la connexion et du bouton « Récupérer depuis Vigie-Chiro » de M-Sites.
/// Même sémantique conservatrice : chaque rapprocheur est best-effort et n'écrase jamais de données
/// locales. Utile avant un `deposer-vigiechiro` (le site doit être rattaché).
///
/// Le geste ne fait que **recevoir** : il porte donc le verbe qui le dit (ADR 0022). L'ancien nom
/// `synchroniser-vigiechiro` survit en **alias** - un nom de commande est une interface, et des
/// scripts en dépendent.
///
/// **Jeton** : `--token`, sinon la variable d'environnement `VIGIECHIRO_TOKEN`, sinon la connexion
/// enregistrée dans l'application.
///
/// Code retour `0` si la connexion est vérifiée (`GET /moi`) et le rapatriement joué ; `1` si le
/// jeton est absent, expiré ou le réseau indisponible.
@Command(
        name = "recuperer-vigiechiro",
        aliases = "synchroniser-vigiechiro",
        description = "Récupère le référentiel des taxons et vos sites/points depuis Vigie-Chiro"
                + " (jamais d'écrasement local).")
public final class RecupererVigieChiro implements Callable<Integer> {

    @Option(
            names = "--token",
            paramLabel = "<jeton>",
            description = "Jeton Vigie-Chiro ponctuel (sinon : variable VIGIECHIRO_TOKEN, sinon la connexion"
                    + " enregistrée dans l'application).")
    private String token;

    @Spec
    private CommandSpec spec;

    private final ClientVigieChiro client;

    /// Résolu **paresseusement** : picocli instancie toutes les sous-commandes à la construction de
    /// la CLI, avant la migration du schéma — or résoudre les rapprocheurs déclenche le provider de
    /// l’utilisateur courant, qui touche la base. On ne les demande qu’à l’exécution.
    private final Provider<Set<RapprochementVigieChiro>> rapprocheurs;

    @Inject
    public RecupererVigieChiro(ClientVigieChiro client, Provider<Set<RapprochementVigieChiro>> rapprocheurs) {
        this.client = Objects.requireNonNull(client, "client");
        this.rapprocheurs = Objects.requireNonNull(rapprocheurs, "rapprocheurs");
    }

    @Override
    public Integer call() {
        if (token != null && !token.isBlank()) {
            // Jeton ponctuel : consulté par le client à chaque requête (cf. ConnexionModule), sans rien
            // persister — la connexion enregistrée de l'application n'est pas modifiée.
            System.setProperty("vigiechiro.token", token);
        }
        // Même garde que la modale : on vérifie l'identité avant de récupérer, pour distinguer un
        // « rien à récupérer » d'un jeton mort (les rapprocheurs, best-effort, seraient muets).
        // Depuis #1284, la cause est la vraie : jeton absent, jeton refusé et panne ne se confondent plus.
        ProfilVigieChiro profil =
                switch (client.moi()) {
                    case ReponseApi.Succes<ProfilVigieChiro>(ProfilVigieChiro identite) -> identite;
                    case ReponseApi.NonConnecte<ProfilVigieChiro> nonConnecte ->
                        throw new RegleMetierException("Aucun jeton Vigie-Chiro : fournissez --token, la variable"
                                + " VIGIECHIRO_TOKEN, ou enregistrez une connexion dans l'application.");
                    case ReponseApi.Injoignable<ProfilVigieChiro>(String cause) ->
                        throw new RegleMetierException(
                                "Vigie-Chiro est injoignable (" + cause + ") : vérifiez le réseau et réessayez.");
                    case ReponseApi.Refuse<ProfilVigieChiro>(int statut, String corps) ->
                        throw new RegleMetierException(
                                statut == 401
                                        ? "Jeton Vigie-Chiro invalide ou expiré : régénérez-en un depuis le site."
                                        : "Vigie-Chiro a refusé la connexion (HTTP " + statut + ") : " + corps);
                };
        PrintWriter sortie = spec.commandLine().getOut();
        sortie.println("Connecté : " + profil.pseudo() + ".");

        List<String> parties = new ArrayList<>();
        // Ordre (#1776) : structure d'abord (sites, taxons), puis ce qui en dépend (passages sur points locaux).
        for (RapprochementVigieChiro rapprocheur : RapprochementVigieChiro.ordonnes(rapprocheurs.get())) {
            rapprocheur.synchroniser(client).ifPresent(rapport -> parties.add(rapport.enClair()));
        }
        sortie.println(rendreResume(parties));
        return 0;
    }

    /// Résumé du rapatriement, calqué sur celui de la modale de connexion (« 385 taxons,
    /// 3 sites »). Fonction pure (testable sans réseau).
    static String rendreResume(List<String> parties) {
        if (parties.isEmpty()) {
            return "Rien à récupérer (la plateforme n'a renvoyé aucune donnée).";
        }
        return "Récupéré : " + String.join(", ", parties) + ".";
    }
}
