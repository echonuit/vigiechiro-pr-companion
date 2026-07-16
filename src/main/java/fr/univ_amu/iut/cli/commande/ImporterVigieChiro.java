package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.api.SuiviPagination;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `importer-vigiechiro` (#1181) : importe les **résultats Tadarida** d'un passage directement
/// depuis la plateforme (participation rattachée), sans fichier CSV - l'équivalent scriptable de
/// l'action ☰ « Importer depuis VigieChiro » de Sons & validation. À ne pas confondre avec
/// `importer-tadarida`, qui lit un CSV local (et reste le repli hors connexion).
///
/// **Rattachement** : par défaut, le lien passage → participation posé au dépôt est réutilisé.
/// `--participation <objectid>` rattache d'abord le passage à cette participation (l'équivalent du
/// choix manuel proposé par l'IHM pour une nuit déposée hors application).
///
/// **Jeton** : `--token`, sinon la variable d'environnement `VIGIECHIRO_TOKEN`, sinon la connexion
/// enregistrée dans l'application.
///
/// Code retour `0` si l'import aboutit ; `2` si le passage a déjà un jeu et que `--remplacer` n'est pas
/// donné (relancez avec `--remplacer`) ; `1` pour les autres refus (passage non rattaché, résultats pas
/// encore disponibles, jeton mort...), avec la raison sur la sortie d'erreur.
@Command(
        name = "importer-vigiechiro",
        description = "Importe les résultats Tadarida d'un passage depuis VigieChiro (sans CSV,"
                + " participation rattachée).")
public final class ImporterVigieChiro implements Callable<Integer> {

    @Option(names = "--passage", required = true, paramLabel = "<id>", description = "Passage à alimenter.")
    private Long idPassage;

    @Option(
            names = "--remplacer",
            description = "Remplace le jeu de résultats existant (les validations observateur sont préservées).")
    private boolean remplacer;

    @Option(
            names = "--participation",
            paramLabel = "<objectid>",
            description = "Rattache d'abord le passage à cette participation VigieChiro (sinon : le lien"
                    + " posé au dépôt est réutilisé).")
    private String participation;

    @Option(
            names = "--token",
            paramLabel = "<jeton>",
            description = "Jeton VigieChiro ponctuel (sinon : variable VIGIECHIRO_TOKEN, sinon la connexion"
                    + " enregistrée dans l'application).")
    private String token;

    @Spec
    private CommandSpec spec;

    private final Optional<ImportVigieChiro> importVigieChiro;
    private final ResultatsIdentificationDao resultatsDao;

    @Inject
    public ImporterVigieChiro(Optional<ImportVigieChiro> importVigieChiro, ResultatsIdentificationDao resultatsDao) {
        this.importVigieChiro = Objects.requireNonNull(importVigieChiro, "importVigieChiro");
        this.resultatsDao = Objects.requireNonNull(resultatsDao, "resultatsDao");
    }

    @Override
    public Integer call() {
        ImportVigieChiro moteur = importVigieChiro.orElseThrow(
                () -> new RegleMetierException("Import VigieChiro indisponible dans ce contexte d'exécution"
                        + " (fonctionnalité « import-vigiechiro » désactivée ?)."));
        // Même garde que `importer-tadarida` : refus d'usage (code 2) sur un passage déjà pourvu d'un jeu,
        // AVANT l'appel réseau, plutôt que le message d'IHM du garde-fou service (« ouvrez Sons & validation »).
        GardeJeuExistant.refuserSiDejaImporte(resultatsDao, idPassage, remplacer);
        if (token != null && !token.isBlank()) {
            // Jeton ponctuel : consulté par le client à chaque requête (cf. ConnexionModule), sans rien
            // persister — la connexion enregistrée de l'application n'est pas modifiée.
            System.setProperty("vigiechiro.token", token);
        }
        if (participation != null && !participation.isBlank()) {
            moteur.rattacher(idPassage, participation.trim());
        }
        // Un import peut brasser des milliers de fichiers : on relaie l'avancement par page sur la sortie
        // d'erreur (stdout reste réservé au bilan), à parité avec la barre de progression de l'IHM (#1622).
        SuiviPagination suivi = (page, totalPages) ->
                spec.commandLine().getErr().println("Import VigieChiro… page " + page + "/" + totalPages);
        BilanImport bilan = moteur.importer(idPassage, remplacer, suivi);
        // Même rendu que l'import CSV (ImporterTadarida) : les deux chemins alimentent le même écran.
        spec.commandLine().getOut().println(ImporterTadarida.rendreBilan(bilan, remplacer));
        return 0;
    }
}
