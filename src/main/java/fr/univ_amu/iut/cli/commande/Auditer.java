package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
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
public final class Auditer implements Callable<Integer>, LectureSeule {

    @Option(
            names = "--passage",
            paramLabel = "<id>",
            description = "Limite l'audit à ce passage. Sans cette option, audite tout le workspace.")
    private Long idPassage;

    @Option(names = "--gravite", description = "Ne garde que les constats de ce niveau : ${COMPLETION-CANDIDATES}.")
    private Severite gravite;

    @Option(names = "--categorie", description = "Ne garde que cette nature de constat : ${COMPLETION-CANDIDATES}.")
    private CategorieConstat categorie;

    @Option(
            names = "--contient",
            paramLabel = "<texte>",
            description = "Ne garde que les constats dont la cible ou le détail contient ce texte "
                    + "(casse et accents ignorés), comme la recherche libre de l'écran.")
    private String contient;

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
                .filter(constat -> contient == null || correspond(constat))
                .toList();
    }

    /// Même recherche que la barre de l'écran (`CriteresAudit.rechercheTexte`) : la **cible** et le
    /// **détail**, les deux colonnes en texte libre, casse et accents ignorés. Les autres colonnes ont
    /// leur option ; les inclure ici ferait répondre `--contient erreur` sur toutes les lignes en
    /// erreur, ce que `--gravite` dit mieux.
    private boolean correspond(ConstatAudit constat) {
        String aiguille = NormalisationTexte.normaliser(contient);
        return contientNormalise(constat.cible(), aiguille) || contientNormalise(constat.detail(), aiguille);
    }

    private static boolean contientNormalise(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }

    private static String enJson(List<ConstatAudit> constats) {
        // Le temoin de type explicite n'est pas decoratif : sans lui, `map` infere
        // `Stream<Map<String, capture-of ?>>`, que javac assigne a `List<Map<String, ?>>` et
        // qu'ecj refuse. Divergence de la meme famille que #3228, trouvee par le meme moyen.
        List<Map<String, ?>> lignes =
                constats.stream().<Map<String, ?>>map(Auditer::projeter).toList();
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
        int total = rapport.constats().size();
        if (retenus.size() < total) {
            // Le filtre a masqué quelque chose : la ligne de résumé DOIT dire qu'elle compte l'audit
            // entier. Sans ce « sur N », elle listait un constat puis annonçait « 3 constat(s) : 1
            // erreur(s) » - un lecteur y voyait trois lignes et cherchait les deux autres. Et surtout,
            // c'est ce total que juge le code de sortie : afficher zéro erreur en rendant 1 est correct,
            // mais seulement si la sortie l'explique.
            texte.append(retenus.size())
                    .append(" constat(s) affiché(s) sur ")
                    .append(total)
                    .append(". ");
        } else {
            texte.append(total).append(" constat(s) : ");
        }
        if (retenus.size() < total) {
            texte.append("L'audit entier compte ");
        }
        texte.append(rapport.nombre(Severite.ERREUR))
                .append(" erreur(s), ")
                .append(rapport.nombre(Severite.AVERTISSEMENT))
                .append(" avertissement(s), ")
                .append(rapport.nombre(Severite.INFO))
                .append(" info(s)");
        texte.append(retenus.size() < total ? ", et c'est lui que juge le code de sortie." : ".");
        return texte.toString();
    }
}
