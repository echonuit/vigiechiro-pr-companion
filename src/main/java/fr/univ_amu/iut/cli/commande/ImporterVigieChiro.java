package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
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
/// Code retour `0` si l'import aboutit ; `1` sinon (passage non rattaché, résultats pas encore
/// disponibles, jeton mort...), avec la raison sur la sortie d'erreur.
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

    @Inject
    public ImporterVigieChiro(Optional<ImportVigieChiro> importVigieChiro) {
        this.importVigieChiro = Objects.requireNonNull(importVigieChiro, "importVigieChiro");
    }

    @Override
    public Integer call() {
        ImportVigieChiro moteur = importVigieChiro.orElseThrow(
                () -> new RegleMetierException("Import VigieChiro indisponible dans ce contexte d'exécution"
                        + " (fonctionnalité « import-vigiechiro » désactivée ?)."));
        if (token != null && !token.isBlank()) {
            // Jeton ponctuel : consulté par le client à chaque requête (cf. ConnexionModule), sans rien
            // persister — la connexion enregistrée de l'application n'est pas modifiée.
            System.setProperty("vigiechiro.token", token);
        }
        if (participation != null && !participation.isBlank()) {
            moteur.rattacher(idPassage, participation.trim());
        }
        BilanImport bilan = moteur.importer(idPassage, remplacer);
        // Même rendu que l'import CSV (ImporterTadarida) : les deux chemins alimentent le même écran.
        spec.commandLine().getOut().println(ImporterTadarida.rendreBilan(bilan, remplacer));
        return 0;
    }
}
