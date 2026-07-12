package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
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

/// `synchroniser-vigiechiro` (#1181) : rejoue les **rapprocheurs** VigieChiro (référentiel des
/// taxons, sites et points) sans passer par la modale de connexion - l'équivalent scriptable de ce
/// que l'IHM déclenche à la connexion et du bouton « Synchroniser depuis VigieChiro » de M-Sites.
/// Même sémantique conservatrice : chaque rapprocheur est best-effort et n'écrase jamais de données
/// locales. Utile avant un `deposer-vigiechiro` (le site doit être rattaché).
///
/// **Jeton** : `--token`, sinon la variable d'environnement `VIGIECHIRO_TOKEN`, sinon la connexion
/// enregistrée dans l'application.
///
/// Code retour `0` si la connexion est vérifiée (`GET /moi`) et la synchronisation jouée ; `1` si le
/// jeton est absent, expiré ou le réseau indisponible.
@Command(
        name = "synchroniser-vigiechiro",
        description = "Synchronise le référentiel des taxons et vos sites/points depuis VigieChiro"
                + " (jamais d'écrasement local).")
public final class SynchroniserVigieChiro implements Callable<Integer> {

    @Option(
            names = "--token",
            paramLabel = "<jeton>",
            description = "Jeton VigieChiro ponctuel (sinon : variable VIGIECHIRO_TOKEN, sinon la connexion"
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
    public SynchroniserVigieChiro(ClientVigieChiro client, Provider<Set<RapprochementVigieChiro>> rapprocheurs) {
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
        // Même garde que la modale : on vérifie l'identité avant de synchroniser, pour distinguer un
        // « rien à synchroniser » d'un jeton mort (les rapprocheurs, best-effort, seraient muets).
        ProfilVigieChiro profil = client.moi()
                .orElseThrow(() -> new RegleMetierException("Connexion VigieChiro impossible : jeton absent,"
                        + " expiré ou réseau indisponible (--token, variable VIGIECHIRO_TOKEN, ou connexion"
                        + " enregistrée dans l'application)."));
        PrintWriter sortie = spec.commandLine().getOut();
        sortie.println("Connecté : " + profil.pseudo() + ".");

        List<String> parties = new ArrayList<>();
        for (RapprochementVigieChiro rapprocheur : rapprocheurs.get()) {
            rapprocheur
                    .synchroniser(client)
                    .ifPresent(rapport -> parties.add(rapport.nombre() + " " + rapport.libelle()));
        }
        sortie.println(rendreResume(parties));
        return 0;
    }

    /// Résumé de la synchronisation, calqué sur celui de la modale de connexion (« 385 taxons,
    /// 3 sites »). Fonction pure (testable sans réseau).
    static String rendreResume(List<String> parties) {
        if (parties.isEmpty()) {
            return "Rien à synchroniser (aucune donnée distante récupérée).";
        }
        return "Synchronisé : " + String.join(", ", parties) + ".";
    }
}
