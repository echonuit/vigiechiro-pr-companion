package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.GesteAttenduCli;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.Horodatage;
import fr.univ_amu.iut.commun.model.ImportApresReleve;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `etat-traitement-vigiechiro` (#1265) : **où en est l'analyse Tadarida** de la nuit déposée ?
///
/// C'est le pendant en ligne de commande du suivi affiché à M-Lot (#1263). L'application ne **surveille**
/// jamais le serveur d'elle-même (aucun sondage périodique : un calcul dure des dizaines de minutes, et
/// le site officiel n'en fait pas davantage), mais un script, lui, peut la questionner à son rythme.
/// D'où des **codes de retour** faits pour être testés dans une boucle :
///
/// ```
/// until etat-traitement-vigiechiro --passage 12 ; [ $? -ne 3 ] ; do sleep 300 ; done
/// ```
///
/// | Code | Situation | Que faire |
/// |---|---|---|
/// | `0` | **terminé** | importer les observations (`--importer`, ou `importer-vigiechiro`) |
/// | `3` | planifié, en cours, ou nouvel essai | **patienter** |
/// | `1` | **en échec** côté serveur | lire la trace, éventuellement relancer |
/// | `4` | jamais lancée (le serveur répond, #1284) | lancer l'analyse (`lancer-traitement-vigiechiro`) |
/// | `2` | **indisponible** : on n'a pas pu demander | nuit non déposée, jeton absent, plateforme injoignable |
///
/// Chaque appel **rafraîchit le cache local** (#1262) : l'application affichera cet état même hors
/// connexion.
///
/// **Jeton** : `--token`, sinon la variable d'environnement `VIGIECHIRO_TOKEN`, sinon la **connexion
/// enregistrée** dans l'application (préférer la variable d'environnement à `--token`, qui laisse le
/// jeton dans l'historique du shell).
@Command(
        name = "etat-traitement-vigiechiro",
        description = "Où en est l'analyse Tadarida de la nuit déposée ? (0 = terminé, 3 = en cours,"
                + " 1 = en échec, 4 = jamais lancée, 2 = indisponible)")
public final class EtatTraitementVigieChiro implements Callable<Integer>, LectureSeule {

    /// Analyse terminée : les observations sont récupérables.
    private static final int TERMINE = 0;

    /// Le serveur a répondu « échoué » : l'analyse serveur a échoué (résultat métier). Distinct de
    /// [#INDISPONIBLE] (on n'a pas pu demander). Ce « partage » a fait passer l'échec serveur de `2` à `1`,
    /// pour laisser le `2` au sens #2294 (refus / pas pu faire).
    private static final int EN_ECHEC = 1;

    /// On n'a pas pu demander : suivi indisponible dans ce contexte, jeton absent, plateforme injoignable
    /// (#1284). C'est un « refus / pas pu faire » au sens #2294, donc `2`. La commande le pose **elle-même**
    /// (elle est scriptable : son code ne doit pas dépendre du harnais d'invocation, seulement de son [#call]).
    private static final int INDISPONIBLE = 2;

    /// Le serveur travaille (ou s'apprête à le faire) : il n'y a qu'à attendre.
    private static final int EN_ATTENTE = 3;

    /// Le serveur **répond** et ne connaît aucun traitement : l'analyse n'a jamais été lancée. Depuis
    /// #1284, « injoignable » ne tombe plus ici (c'est un code 1) : ce code redit vrai.
    private static final int JAMAIS_LANCE = 4;

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage dont on veut connaître l'état du traitement serveur (nuit déjà déposée).")
    private Long idPassage;

    @Option(
            names = "--token",
            paramLabel = "<jeton>",
            description = "Jeton Vigie-Chiro ponctuel (sinon : variable VIGIECHIRO_TOKEN, sinon la connexion"
                    + " enregistrée dans l'application).")
    private String token;

    @Option(
            names = "--importer",
            description = "Importe les observations de la nuit quand l'analyse est terminée, comme le fait"
                    + " « Actualiser » sur l'écran de lot. Une nuit déjà importée n'est pas réimportée."
                    + " Sans cette option, la commande ne modifie rien.")
    private boolean importer;

    @Spec
    private CommandSpec spec;

    private final Optional<SuiviTraitement> suivi;
    private final Optional<ImportObservations> importation;

    @Inject
    public EtatTraitementVigieChiro(Optional<SuiviTraitement> suivi, Optional<ImportObservations> importation) {
        this.suivi = Objects.requireNonNull(suivi, "suivi");
        this.importation = Objects.requireNonNull(importation, "importation");
    }

    @Override
    public Integer call() {
        // Barème scriptable : la commande pose TOUS ses codes elle-même, y compris le 2 « indisponible »
        // (on n'a pas pu demander). On capte donc la RegleMetierException plutôt que de la laisser au
        // handler central, pour que le code reste vrai quel que soit le harnais d'invocation (#2294).
        try {
            SuiviTraitement moteur = suivi.orElseThrow(
                    () -> new RegleMetierException("Suivi Vigie-Chiro indisponible dans ce contexte d'exécution."));
            if (token != null && !token.isBlank()) {
                // Jeton ponctuel consulté par le client à chaque requête, sans rien persister.
                System.setProperty("vigiechiro.token", token);
            }
            Traitement traitement = moteur.relever(idPassage);
            PrintWriter sortie = spec.commandLine().getOut();
            sortie.println(compteRendu(traitement));
            return importer ? importerSiTermine(traitement, sortie) : code(traitement);
        } catch (RegleMetierException indisponible) {
            spec.commandLine().getErr().println("Indisponible : " + GesteAttenduCli.message(indisponible));
            return INDISPONIBLE;
        }
    }

    /// Lecture seule tant qu'on ne demande pas d'importer : le verrou suit l'invocation (#5784).
    @Override
    public boolean neFaitQueLire() {
        return !importer;
    }

    /// `--importer` : le geste d'« Actualiser » sur l'écran de lot, par la même règle (#5784). Un import
    /// qui n'a pas pu se faire rend [#INDISPONIBLE] ; sinon le code reste celui de l'état.
    private int importerSiTermine(Traitement traitement, PrintWriter sortie) {
        if (traitement.resultatsDisponibles() && importation.isEmpty()) {
            throw new RegleMetierException("Import Vigie-Chiro indisponible dans ce contexte d'exécution.");
        }
        return switch (ImportApresReleve.pour(importation, idPassage, traitement)) {
            case ImportApresReleve.Issue.SansObjet sansObjet -> code(traitement);
            case ImportApresReleve.Issue.Fait fait -> {
                sortie.println(fait.compteRendu());
                yield code(traitement);
            }
            case ImportApresReleve.Issue.DejaLa dejaLa -> {
                sortie.println("Les observations de cette nuit sont déjà importées : rien n'a été réimporté.");
                yield code(traitement);
            }
            case ImportApresReleve.Issue.Echoue echoue -> {
                spec.commandLine().getErr().println("Import impossible : " + echoue.motif());
                yield INDISPONIBLE;
            }
        };
    }

    /// Code de retour, pensé pour un `until … ; [ $? -ne 3 ]` : c'est la seule interface que lit un script.
    private static int code(Traitement traitement) {
        if (traitement.estInconnu()) {
            return JAMAIS_LANCE;
        }
        if (traitement.resultatsDisponibles()) {
            return TERMINE;
        }
        return traitement.enEchec() ? EN_ECHEC : EN_ATTENTE;
    }

    /// Compte rendu lisible : l'état, la date qui le date, et ce qu'il reste à faire.
    private String compteRendu(Traitement traitement) {
        String passage = "Passage " + idPassage + " : ";
        if (traitement.estInconnu()) {
            return passage + "aucun traitement connu sur Vigie-Chiro : l'analyse n'a jamais été lancée."
                    + " Lancez-la (lancer-traitement-vigiechiro).";
        }
        return passage
                + switch (traitement.etat()) {
                    case FINI ->
                        "analyse TERMINÉE" + depuis(traitement.dateFin())
                                + ". Les observations sont récupérables (--importer, ou importer-vigiechiro).";
                    case PLANIFIE ->
                        "analyse PLANIFIÉE" + depuis(traitement.datePlanification())
                                + ", en attente d'un calculateur. Patientez.";
                    case EN_COURS -> "analyse EN COURS" + depuis(traitement.dateDebut()) + ". Patientez.";
                    case RETRY ->
                        "un essai a échoué, le serveur a RELANCÉ l'analyse" + essais(traitement) + ". Patientez.";
                    case ERREUR -> "analyse EN ÉCHEC" + depuis(traitement.dateFin()) + "." + trace(traitement);
                };
    }

    /// « le … » quand le serveur date l'événement, rien sinon (il ne garde pas toutes les dates).
    private static String depuis(String date) {
        return depuis(date, ZoneId.systemDefault());
    }

    /// [#depuis(String)], dans un fuseau **fourni**.
    ///
    /// L'instant du serveur était recopié tel quel au milieu d'une phrase française (#3678) : jour et mois
    /// en anglais, heure en UTC, format d'en-tête HTTP. L'UTC n'est pas un détail de présentation, « le
    /// 3 juillet à 19 h » n'étant pas la même heure pour l'observateur que pour le serveur, et c'est lui qui
    /// décide s'il attend ou s'il revient demain.
    ///
    /// La plateforme rend **deux** formes, RFC 1123 et ISO avec décalage, toutes deux acceptées. **Ce qu'on
    /// ne sait pas lire reste affiché tel quel** : une chaîne étrange s'interprète, une absence non. Le
    /// fuseau est un paramètre, `fuseau-alternatif` rejouant la suite sous `America/Cayenne` (ADR 3450).
    static String depuis(String date, ZoneId fuseau) {
        return date == null ? "" : " (le " + lisible(date, fuseau) + ")";
    }

    /// L'instant dans le fuseau demandé, ou la chaîne d'origine si elle ne se lit pas. La lecture est celle
    /// de la carte de l'écran : une seule pour les deux surfaces depuis #5683.
    private static String lisible(String date, ZoneId fuseau) {
        return Horodatage.instantDansUnePhrase(date, fuseau);
    }

    private static String essais(Traitement traitement) {
        return traitement.retry() == null ? "" : " (essai n°" + traitement.retry() + ")";
    }

    /// Motif de l'échec, rendu par le [Traitement] lui-même. La ligne de commande, elle, peut se permettre
    /// la trace ENTIÈRE : elle finit dans un terminal ou un journal, pas dans une carte d'écran.
    private static String trace(Traitement traitement) {
        return traitement.message() == null ? "" : "\n" + traitement.message();
    }
}
