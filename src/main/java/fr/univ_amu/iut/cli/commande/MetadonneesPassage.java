package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.EnvoiParticipation;
import fr.univ_amu.iut.passage.model.RattrapageMetadonnees;
import fr.univ_amu.iut.passage.model.RattrapageMetadonnees.BilanRattrapage;
import fr.univ_amu.iut.passage.model.RattrapageMetadonnees.IssuePassage;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `metadonnees-passage` (#1861) : les métadonnées d'une nuit **en ligne de commande**, à parité avec la
/// modale « Modifier le passage » ([ADR 0014]).
///
/// Quatre gestes, dont deux parlent à la plateforme :
///  - `--recuperer` : rapatrie météo, micro et n° de série depuis la participation Vigie-Chiro ;
///  - `--envoyer` : réécrit les métadonnées locales sur la participation ;
///  - `--enregistreur` : pose le n° de série lu sur l'appareil (local, sans réseau) ;
///  - `--heure-debut` / `--heure-fin` : corrige les heures d'une nuit que rien ne prouve (#1892).
///
/// Les verbes sont ceux des boutons de la modale, et pas « tirer »/« pousser » : l'[ADR 0022] pose que le
/// verbe dit le sens réel de l'échange, **y compris dans les noms de commandes**. Une parité qui livre les
/// mêmes gestes sous d'autres mots n'est qu'une demi-parité.
///
/// **`--tout` est la raison d'être de cette commande.** Trois correctifs de ce chantier (#1814, #1828,
/// #1844) et le réalignement des heures (#1878) ne réparent que la nuit sur laquelle on repasse : sans lot,
/// ils n'atteignent jamais les nuits déjà abîmées. Comme il **écrit sur la plateforme**, il exige
/// `--confirmer` ; sans lui, la commande se contente de **dire ce qu'elle ferait**.
@Command(
        name = "metadonnees-passage",
        description = "Récupère ou envoie les métadonnées d'une nuit (météo, micro, enregistreur, heures) "
                + "entre l'application et Vigie-Chiro, et pose localement le n° de série ou les heures. "
                + "Avec --tout, rattrape toutes les nuits liées en une passe.")
public final class MetadonneesPassage implements Callable<Integer> {

    private static final String VERS = " → ";

    @Option(
            names = "--passage",
            paramLabel = "<id>",
            description = "Identifiant technique de la nuit visée. Exclusif avec --tout.")
    private Long idPassage;

    @Option(
            names = "--tout",
            description = "Applique le geste à TOUTES les nuits liées à une participation (rattrapage de "
                    + "saison). N'accepte que --recuperer et --envoyer, et exige --confirmer : sans lui, la "
                    + "commande liste les nuits qu'elle traiterait sans rien écrire.")
    private boolean tout;

    @Option(
            names = "--recuperer",
            description = "Rapatrie météo, micro et n° de série depuis la participation Vigie-Chiro.")
    private boolean recuperer;

    @Option(
            names = "--envoyer",
            description = "Réécrit les métadonnées locales sur la participation Vigie-Chiro. Les heures de "
                    + "la nuit y sont réalignées sur ses enregistrements quand ils la contredisent.")
    private boolean envoyer;

    @Option(
            names = "--enregistreur",
            paramLabel = "<serie>",
            description = "Pose le n° de série de l'enregistreur (écriture locale, sans réseau).")
    private String enregistreur;

    @Option(
            names = "--heure-debut",
            paramLabel = "<HH:mm>",
            description = "Heure de début de la nuit. Va de pair avec --heure-fin. Refusée si les "
                    + "enregistrements de la nuit l'attestent déjà.")
    private String heureDebut;

    @Option(names = "--heure-fin", paramLabel = "<HH:mm>", description = "Heure de fin de la nuit.")
    private String heureFin;

    @Option(names = "--confirmer", description = "Confirme un rattrapage --tout, qui écrit sur la plateforme.")
    private boolean confirmer;

    @Option(names = "--json", description = "Émet le compte rendu au format JSON.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma
    // (cf. Auditer). Les Optional sont vides hors connexion : les deux gestes réseau ont besoin de la
    // plateforme, les deux gestes locaux non.
    private final Provider<Optional<SynchronisationParticipation>> synchronisation;
    private final Provider<Optional<RattrapageMetadonnees>> rattrapage;
    private final Provider<ServiceConditionsPassage> conditions;

    @Inject
    public MetadonneesPassage(
            Provider<Optional<SynchronisationParticipation>> synchronisation,
            Provider<Optional<RattrapageMetadonnees>> rattrapage,
            Provider<ServiceConditionsPassage> conditions) {
        this.synchronisation = Objects.requireNonNull(synchronisation, "synchronisation");
        this.rattrapage = Objects.requireNonNull(rattrapage, "rattrapage");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
    }

    @Override
    public Integer call() {
        verifierInvocation(idPassage, tout, recuperer, envoyer, enregistreur, heureDebut, heureFin);
        PrintWriter sortie = spec.commandLine().getOut();
        return tout ? rattraperTout(sortie) : traiterUneNuit(sortie);
    }

    /// Rejette les invocations qui n'ont pas de sens **avant** la moindre écriture, en disant laquelle.
    ///
    /// Un `--tout --enregistreur 1925492` poserait le même appareil sur toutes les nuits de la saison : ce
    /// n'est pas un rattrapage, c'est une falsification. Mieux vaut le refuser que l'exécuter.
    ///
    /// Fonction **pure et statique** (patron de [Qualifier#verdictDepuis]) : la règle d'invocation se lit et
    /// se teste sans picocli, et `call()` la consulte avant tout le reste.
    ///
    /// @throws ErreurUsage (code de sortie 2) en nommant ce qui cloche
    static void verifierInvocation(
            Long idPassage,
            boolean tout,
            boolean recuperer,
            boolean envoyer,
            String enregistreur,
            String heureDebut,
            String heureFin) {
        if (tout == (idPassage != null)) {
            throw new ErreurUsage("Précisez soit --passage <id>, soit --tout (pas les deux, pas aucun).");
        }
        boolean heures = heureDebut != null || heureFin != null;
        if (!recuperer && !envoyer && enregistreur == null && !heures) {
            throw new ErreurUsage("Aucun geste demandé : ajoutez --recuperer, --envoyer, --enregistreur"
                    + " ou --heure-debut/--heure-fin.");
        }
        if (tout && (enregistreur != null || heures)) {
            throw new ErreurUsage("--tout ne prend que --recuperer et --envoyer : un n° de série ou des heures"
                    + " valent pour UNE nuit, les appliquer à toutes inventerait des données.");
        }
        if (heures && (heureDebut == null || heureFin == null)) {
            throw new ErreurUsage(
                    "--heure-debut et --heure-fin vont de pair : une nuit se délimite par ses" + " deux bornes.");
        }
    }

    /// Applique à une nuit les gestes demandés, dans l'ordre où ils se composent : les écritures **locales**
    /// d'abord (n° de série, heures), puis la récupération, puis l'envoi. Ainsi une même invocation peut
    /// corriger une nuit **et** publier la correction, sans que l'envoi parte avant la saisie.
    private int traiterUneNuit(PrintWriter sortie) {
        Map<String, Object> compte = new LinkedHashMap<>();
        compte.put("passage", idPassage);
        if (enregistreur != null) {
            conditions.get().definirEnregistreur(idPassage, enregistreur);
            dire(sortie, compte, "enregistreur", "Enregistreur posé : " + enregistreur, enregistreur);
        }
        if (heureDebut != null) {
            var modifie = conditions.get().definirHoraires(idPassage, heureDebut, heureFin);
            dire(
                    sortie,
                    compte,
                    "heures",
                    "Heures de la nuit : " + modifie.heureDebut() + VERS + modifie.heureFin(),
                    modifie.heureDebut() + VERS + modifie.heureFin());
        }
        if (recuperer) {
            passerelle().tirerDepuis(idPassage);
            dire(sortie, compte, "recupere", "Métadonnées récupérées depuis Vigie-Chiro.", true);
        }
        return envoyer ? envoyerUneNuit(sortie, compte) : rendre(sortie, compte);
    }

    /// Envoie une nuit, et **regarde** ce que la plateforme en a fait : une écriture refusée ne lève pas
    /// d'exception, elle rend un résultat en échec ([ADR 0008], aucun échec silencieux). Sortie `1` alors,
    /// pour qu'un script ne prenne pas un refus pour un succès.
    private int envoyerUneNuit(PrintWriter sortie, Map<String, Object> compte) {
        EnvoiParticipation envoi = passerelle().pousserVers(idPassage);
        envoi.realignement()
                .ifPresent(realignement -> dire(
                        sortie,
                        compte,
                        "realignement",
                        "Attention, " + phrase(realignement) + ".",
                        phrase(realignement)));
        if (!envoi.ecriture().estReussie()) {
            dire(
                    sortie,
                    compte,
                    "echec",
                    "Envoi refusé : " + envoi.ecriture().echec(),
                    envoi.ecriture().echec());
            rendre(sortie, compte);
            return 1;
        }
        dire(sortie, compte, "envoye", "Métadonnées envoyées vers Vigie-Chiro.", true);
        return rendre(sortie, compte);
    }

    /// Rattrapage de saison. Sans `--confirmer`, **rien n'est écrit** : la commande énumère les nuits
    /// qu'elle traiterait. C'est la contrepartie d'un geste qui écrit sur la plateforme pour une saison
    /// entière ([ADR 0020]) - on doit pouvoir en mesurer la portée avant de le lancer.
    private int rattraperTout(PrintWriter sortie) {
        RattrapageMetadonnees lot = rattrapage
                .get()
                .orElseThrow(() -> new RegleMetierException("Le rattrapage a besoin de la connexion Vigie-Chiro :"
                        + " renseignez un jeton (VIGIECHIRO_TOKEN) puis recommencez."));
        List<Long> passages = lot.passagesLies();
        if (passages.isEmpty()) {
            sortie.println("Aucune nuit liée à une participation : rien à rattraper.");
            return 0;
        }
        if (!confirmer) {
            return annoncerLot(sortie, passages);
        }
        int total = passages.size();
        int[] rang = {0};
        BilanRattrapage bilan = lot.rattraper(passages, recuperer, envoyer, issue -> {
            rang[0]++;
            if (!json) {
                sortie.println("  " + ligneIssue(issue, rang[0], total));
            }
        });
        return rendreBilan(sortie, bilan);
    }

    /// Énumère ce que le lot **ferait**, sans rien écrire.
    private int annoncerLot(PrintWriter sortie, List<Long> passages) {
        if (json) {
            Map<String, Object> objet = new LinkedHashMap<>();
            objet.put("nuits", passages);
            objet.put("confirme", false);
            sortie.println(FormatJson.objet(objet));
            return 0;
        }
        sortie.println(passages.size() + " nuit(s) liée(s) seraient traitées (" + gestesDemandes() + ") :");
        passages.forEach(passage -> sortie.println("  - passage " + passage));
        sortie.println("Relancez avec --confirmer pour écrire.");
        return 0;
    }

    /// Les gestes demandés, en toutes lettres, pour que l'annonce du lot dise ce qui va se passer.
    private String gestesDemandes() {
        if (recuperer && envoyer) {
            return "récupération puis envoi";
        }
        return recuperer ? "récupération" : "envoi";
    }

    /// Bilan du lot. Une nuit ignorée n'est **pas** un succès : sortie `1` s'il en reste, pour qu'un script
    /// de saison ne conclue pas au vert sur un rattrapage incomplet.
    private int rendreBilan(PrintWriter sortie, BilanRattrapage bilan) {
        if (json) {
            Map<String, Object> objet = new LinkedHashMap<>();
            objet.put("traites", bilan.traites());
            objet.put("ignores", bilan.ignores());
            objet.put("realignes", bilan.realignes());
            sortie.println(FormatJson.objet(objet));
        } else {
            sortie.println(bilan.traites() + " nuit(s) traitée(s), " + bilan.ignores() + " ignorée(s), "
                    + bilan.realignes() + " dont les heures ont été réalignées.");
        }
        return bilan.ignores() == 0 ? 0 : 1;
    }

    /// Ligne de compte rendu d'une nuit du lot. La commande **pattern-matche** l'issue pour son rendu ; le
    /// service, lui, ne connaît ni la CLI ni l'IHM.
    private static String ligneIssue(IssuePassage issue, int rang, int total) {
        String position = "[" + rang + "/" + total + "] passage " + issue.passage();
        return switch (issue) {
            case IssuePassage.Traite traite -> position + " -> " + resume(traite);
            case IssuePassage.Ignore ignore -> position + " -> ignorée : " + ignore.cause();
        };
    }

    /// Ce qui a été fait sur une nuit traitée, **réalignement compris** : une correction d'heures est une
    /// modification des données de l'utilisateur, elle ne peut pas se noyer dans un compteur (#1885).
    private static String resume(IssuePassage.Traite traite) {
        String gestes = traite.recupere() && traite.envoye()
                ? "récupérée et envoyée"
                : (traite.recupere() ? "récupérée" : "envoyée");
        return traite.realignement()
                .map(realignement -> gestes + ", " + phrase(realignement))
                .orElse(gestes);
    }

    /// Les heures avant et après réalignement. Dire seulement la nouvelle valeur n'apprendrait pas ce qui a
    /// été corrigé, ni de combien.
    private static String phrase(EnvoiParticipation.Realignement realignement) {
        return "heures réalignées " + realignement.debutAvant() + VERS + realignement.debutApres() + " (début), "
                + realignement.finAvant() + VERS + realignement.finApres() + " (fin)";
    }

    /// La passerelle, ou un refus qui dit **pourquoi** : les deux gestes réseau n'existent pas hors
    /// connexion, alors que les deux gestes locaux, eux, fonctionnent toujours.
    private SynchronisationParticipation passerelle() {
        return synchronisation
                .get()
                .orElseThrow(() -> new RegleMetierException("Ce geste a besoin de la connexion Vigie-Chiro :"
                        + " renseignez un jeton (VIGIECHIRO_TOKEN) puis recommencez."));
    }

    /// Note un fait dans les deux rendus à la fois : la ligne de texte n'est imprimée qu'en mode texte, la
    /// clé n'est retenue que pour le JSON. Évite que les deux sorties divergent au fil des ajouts.
    private void dire(PrintWriter sortie, Map<String, Object> compte, String cle, String texte, Object valeur) {
        compte.put(cle, valeur);
        if (!json) {
            sortie.println(texte);
        }
    }

    /// Émet le compte rendu JSON s'il est demandé (le texte a déjà été imprimé au fil de l'eau).
    private int rendre(PrintWriter sortie, Map<String, Object> compte) {
        if (json) {
            sortie.println(FormatJson.objet(compte));
        }
        return 0;
    }
}
