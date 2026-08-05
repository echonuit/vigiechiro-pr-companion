package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.Severite;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// Commande `audit-coherence` : audit de cohérence disque / base en lecture seule. Code de sortie `1`
/// dès qu'un constat de gravité [Severite#ERREUR] est relevé (pilotage par script), `0` sinon.
@Command(
        name = "audit-coherence",
        description = "Audite la cohérence disque / base en lecture seule : fichiers manquants ou orphelins, "
                + "préfixes non conformes, unités déposées divergentes.")
public final class Auditer implements Callable<Integer> {

    @Option(
            names = "--passage",
            paramLabel = "<id>",
            description = "Limite l'audit à ce passage. Sans cette option, audite tout le workspace.")
    private Long idPassage;

    @Option(names = "--gravite", description = "Ne garde que les constats de ce niveau : ${COMPLETION-CANDIDATES}.")
    private Severite gravite;

    @Option(names = "--categorie", description = "Ne garde que cette nature de constat : ${COMPLETION-CANDIDATES}.")
    private CategorieConstat categorie;

    @Option(names = "--json", description = "Émet les constats au format JSON (tableau) plutôt qu'en texte.")
    private boolean json;

    @Option(
            names = "--online",
            description = "Ajoute la vérification en ligne : confronte le dépôt au serveur Vigie-Chiro (#1132).")
    private boolean online;

    @Option(
            names = "--token",
            paramLabel = "<token>",
            description = "Token Vigie-Chiro pour --online (sinon $VIGIECHIRO_TOKEN ou la connexion enregistrée).")
    private String token;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma ;
    // résoudre ServiceAuditCoherence ici tirerait AuditPointsServeur → idUtilisateurCourant (requête SQL) sur
    // une base non migrée. On résout donc paresseusement, à l'exécution de la commande (cf. RecupererVigieChiro).
    private final Provider<ServiceAuditCoherence> service;

    @Inject
    public Auditer(Provider<ServiceAuditCoherence> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        if (online && token != null && !token.isBlank()) {
            System.setProperty("vigiechiro.token", token);
        }
        RapportAudit rapport = calculer();
        // Les deux filtres de l'écran (#3100), portés ici à la clôture de #3092. Ils ne touchent QUE
        // l'affichage : le code de sortie continue de juger le RAPPORT ENTIER, comme le verdict « sain »
        // de l'écran reste calculé sur l'audit entier. Sans cela, « --gravite INFO » sur un workspace
        // abîmé rendrait 0, et un script d'intégration conclurait que tout va bien.
        List<ConstatAudit> retenus = restreindre(rapport.constats());
        sortie.println(json ? enJson(retenus) : enTexte(rapport, retenus));
        return rapport.aDesErreurs() ? 1 : 0;
    }

    private RapportAudit calculer() {
        ServiceAuditCoherence audit = service.get();
        if (online) {
            List<ConstatAudit> tous = new ArrayList<>(audit.auditerTout().constats());
            tous.addAll(audit.auditerEnLigne().constats());
            return new RapportAudit(tous);
        }
        return idPassage == null ? audit.auditerTout() : audit.auditerPassage(idPassage);
    }

    /// Applique `--gravite` puis `--categorie`. Chaque option retient une valeur **exacte**, comme la
    /// puce correspondante de l'écran : c'est la sémantique de `CritereListe.enumeration`.
    private List<ConstatAudit> restreindre(List<ConstatAudit> constats) {
        return constats.stream()
                .filter(constat -> gravite == null || constat.severite() == gravite)
                .filter(constat -> categorie == null || constat.categorie() == categorie)
                .toList();
    }

    private static String enJson(List<ConstatAudit> constats) {
        List<Map<String, ?>> lignes = constats.stream().map(Auditer::projeter).toList();
        return FormatJson.tableau(lignes);
    }

    private static Map<String, ?> projeter(ConstatAudit constat) {
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("severite", constat.severite().name());
        champs.put("categorie", constat.categorie().name());
        champs.put("idPassage", constat.idPassage());
        champs.put("cible", constat.cible());
        champs.put("detail", constat.detail());
        return champs;
    }

    private static String enTexte(RapportAudit rapport, List<ConstatAudit> retenus) {
        if (rapport.sain()) {
            return "Cohérence disque / base : aucun écart détecté.";
        }
        if (retenus.isEmpty()) {
            // Un filtre qui ne retient rien se DIT. Sans cette phrase, la commande rendrait « aucun
            // écart détecté » sur un workspace abîmé : le filtre ferait passer la panne pour la santé.
            return "Aucun constat ne correspond aux filtres ("
                    + rapport.constats().size() + " constat(s) au total).";
        }
        StringBuilder texte = new StringBuilder();
        for (ConstatAudit constat : retenus) {
            texte.append('[').append(constat.severite()).append("] ").append(constat.categorie());
            if (constat.idPassage() != null) {
                texte.append(" (passage ").append(constat.idPassage()).append(')');
            }
            texte.append(' ')
                    .append(constat.cible())
                    .append(" - ")
                    .append(constat.detail())
                    .append(System.lineSeparator());
        }
        texte.append(rapport.constats().size())
                .append(" constat(s) : ")
                .append(rapport.nombre(Severite.ERREUR))
                .append(" erreur(s), ")
                .append(rapport.nombre(Severite.AVERTISSEMENT))
                .append(" avertissement(s), ")
                .append(rapport.nombre(Severite.INFO))
                .append(" info(s).");
        return texte.toString();
    }
}
