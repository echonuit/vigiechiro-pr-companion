package fr.univ_amu.iut.cli;

import com.google.inject.Injector;
import fr.univ_amu.iut.cli.di.CliModule;
import fr.univ_amu.iut.cli.model.ArgumentsCli;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.cli.model.RegistrePassages;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.Lot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Point d'entrée **en ligne de commande** (sans JavaFX) du compagnon VigieChiro (parcours A10 :
/// scriptabilité pour Karim/Samuel). Orchestre les services métier existants ([ServiceImport],
/// [ServiceLot], lecture multi-sites…) résolus via l'injecteur Guice applicatif complet
/// ([RacineInjecteur#creer()]), enrichi des aides propres à la CLI ([CliModule]).
///
/// **Sous-commandes** (analyse manuelle, sans bibliothèque tierce pour ne pas toucher au
/// `pom.xml`/`module-info`) :
///
/// - `lister-passages` — liste les passages avec leur contexte site/point (P5) ;
/// - `importer --source <dir> --point <id> [--annee N] [--passage N]` — importe une nuit (P2) via
///   [ServiceImport] ;
/// - `exporter-lot --passage <id>` — prépare le lot à déposer (P4) via [ServiceLot] ;
/// - `exporter-vu --passage <id> --sortie <fichier>` — exporte le `*_Vu.csv` (P7) via
///   [ServiceValidation].
///
/// **Workspace surchargeable.** Le chemin du workspace (et donc de la base `vigiechiro.db`) est
/// surchargé par la propriété système `vigiechiro.workspace` (lue par `CommunModule`).
/// [#main(String[])] accepte l'option globale `--workspace <dir>` et la positionne **avant** de
/// créer l'injecteur ; les tests, eux, positionnent la propriété directement sur un `@TempDir`.
///
/// **Codes de sortie** : `0` succès, `2` mauvaise invocation (commande inconnue, argument
/// manquant), `1` échec d'exécution (règle métier, accès aux données).
public final class Cli {

    /// Succès.
    public static final int CODE_SUCCES = 0;

    /// Échec d'exécution (règle métier refusée, accès aux données, E/S).
    public static final int CODE_ERREUR_EXECUTION = 1;

    /// Mauvaise invocation : commande inconnue, argument requis manquant ou mal formé.
    public static final int CODE_ERREUR_ARGUMENTS = 2;

    /// Nom de l'option commune `--passage` (importer / exporter-lot / exporter-vu).
    private static final String ARG_PASSAGE = "passage";

    private final Injector injecteur;

    /// @param injecteur injecteur Guice résolvant le socle, les features et le [CliModule]
    ///     (typiquement [#injecteurApplicatif()])
    public Cli(Injector injecteur) {
        this.injecteur = Objects.requireNonNull(injecteur, "injecteur");
    }

    /// Injecteur applicatif complet ([RacineInjecteur#creer()]) augmenté du [CliModule] en
    /// injecteur enfant. À appeler après avoir éventuellement positionné `vigiechiro.workspace`.
    public static Injector injecteurApplicatif() {
        return RacineInjecteur.creer().createChildInjector(new CliModule());
    }

    /// CLI prête à l'emploi, branchée sur l'injecteur applicatif complet.
    public static Cli applicative() {
        return new Cli(injecteurApplicatif());
    }

    /// Exécute une invocation et renvoie son code de sortie (sans appeler `System.exit`, pour
    /// rester testable). La base est migrée au démarrage (idempotent), puis la sous-commande est
    /// dispatchée.
    ///
    /// @param args arguments (le premier est le nom de la sous-commande), `--workspace` déjà
    ///     consommé par [#main(String[])]
    /// @param sortie flux du compte rendu (typiquement `System.out`)
    /// @param erreur flux des messages d'erreur (typiquement `System.err`)
    /// @return le code de sortie ([#CODE_SUCCES] / [#CODE_ERREUR_ARGUMENTS] /
    ///     [#CODE_ERREUR_EXECUTION])
    public int executer(String[] args, PrintStream sortie, PrintStream erreur) {
        if (args.length == 0 || args[0].equals("aide") || args[0].equals("-h") || args[0].equals("--help")) {
            afficherAide(sortie);
            return CODE_SUCCES;
        }

        String commande = args[0];
        String[] reste = new String[args.length - 1];
        System.arraycopy(args, 1, reste, 0, reste.length);
        ArgumentsCli arguments = ArgumentsCli.analyser(reste);

        try {
            // Migration idempotente : garantit que la base et son schéma existent avant toute commande.
            injecteur.getInstance(MigrationSchema.class).migrer();

            return switch (commande) {
                case "lister-passages" -> listerPassages(sortie);
                case "importer" -> importer(arguments, sortie);
                case "exporter-lot" -> exporterLot(arguments, sortie);
                case "exporter-vu" -> exporterVu(arguments, sortie);
                default -> {
                    erreur.println("Commande inconnue : « " + commande + " ».");
                    afficherAide(erreur);
                    yield CODE_ERREUR_ARGUMENTS;
                }
            };
        } catch (ErreurUsage usage) {
            erreur.println("Erreur d'usage : " + usage.getMessage());
            erreur.println("Lancez « aide » pour la liste des commandes.");
            return CODE_ERREUR_ARGUMENTS;
        } catch (RuntimeException echec) {
            // RegleMetierException, IllegalArgumentException (validateurs R1/R2), DataAccessException…
            erreur.println("Échec : " + echec.getMessage());
            return CODE_ERREUR_EXECUTION;
        }
    }

    // --- Commandes --------------------------------------------------------------

    private int listerPassages(PrintStream sortie) {
        List<RegistrePassages.LignePassage> lignes =
                injecteur.getInstance(RegistrePassages.class).lister();
        if (lignes.isEmpty()) {
            sortie.println("Aucun passage enregistré.");
            return CODE_SUCCES;
        }
        sortie.println(lignes.size() + " passage(s) :");
        for (RegistrePassages.LignePassage ligne : lignes) {
            sortie.println("  #"
                    + ligne.idPassage()
                    + "  carré "
                    + ligne.carre()
                    + "  point "
                    + ligne.codePoint()
                    + "  "
                    + ligne.annee()
                    + " passage "
                    + ligne.numeroPassage()
                    + "  ["
                    + ligne.statut().libelle()
                    + "]  verdict : "
                    + (ligne.verdict() == null ? "-" : ligne.verdict().libelle()));
        }
        return CODE_SUCCES;
    }

    private int importer(ArgumentsCli arguments, PrintStream sortie) {
        Path source = Path.of(arguments.exiger("source"));
        long idPoint = arguments.exigerLong("point");

        PointDao pointDao = injecteur.getInstance(PointDao.class);
        SiteDao siteDao = injecteur.getInstance(SiteDao.class);
        PassageDao passageDao = injecteur.getInstance(PassageDao.class);

        PointDEcoute point = pointDao.findById(idPoint)
                .orElseThrow(() -> new ErreurUsage("Point d'écoute introuvable : --point " + idPoint + "."));
        Site site = siteDao.findById(point.idSite())
                .orElseThrow(
                        () -> new ErreurUsage("Site introuvable pour le point " + idPoint + " (incohérence en base)."));

        int annee = arguments.entierOptionnel("annee").orElseGet(() -> anneeCourante());
        int numeroPassage = arguments.entierOptionnel(ARG_PASSAGE).orElseGet(() -> prochainNumero(passageDao, idPoint));

        Prefixe prefixe = new Prefixe(site.numeroCarre(), annee, numeroPassage, point.code());

        ServiceImport service = injecteur.getInstance(ServiceImport.class);
        ResultatImport resultat = service.importer(source, idPoint, prefixe);

        sortie.println("Import réussi.");
        sortie.println("  Passage     : #" + resultat.passage().id());
        sortie.println("  Quadruplet  : carré "
                + site.numeroCarre()
                + " / point "
                + point.code()
                + " / "
                + annee
                + " / passage "
                + numeroPassage);
        sortie.println("  Statut      : " + resultat.passage().statutWorkflow().libelle());
        sortie.println("  Enregistreur: " + resultat.numeroSerieEnregistreur());
        sortie.println("  Originaux   : " + resultat.nombreOriginaux());
        sortie.println("  Séquences   : " + resultat.nombreSequences());
        return CODE_SUCCES;
    }

    private int exporterLot(ArgumentsCli arguments, PrintStream sortie) {
        long idPassage = arguments.exigerLong(ARG_PASSAGE);
        ServiceLot serviceLot = injecteur.getInstance(ServiceLot.class);
        Lot lot = serviceLot.preparerLot(idPassage);
        sortie.println("Lot prêt à déposer pour le passage #" + lot.idPassage() + ".");
        sortie.println("  Séquences : " + lot.nombreSequences());
        sortie.println("  Volume    : "
                + (lot.volumeSequencesOctets() == null ? "-" : lot.volumeSequencesOctets() + " octets"));
        sortie.println("  Dossier   : " + lot.cheminDossier());

        // Génère les archives ZIP de dépôt Tadarida (≤ 700 Mo, <préfixe>-N.zip) prêtes à téléverser (#110).
        List<ArchiveDepot> archives = serviceLot.genererArchivesDepot(idPassage);
        sortie.println("  Archives de dépôt (" + archives.size() + ") :");
        for (ArchiveDepot archive : archives) {
            sortie.println("    - "
                    + archive.chemin().getFileName()
                    + " ("
                    + archive.nombreFichiers()
                    + " fichiers, "
                    + archive.tailleOctets()
                    + " octets)");
        }
        return CODE_SUCCES;
    }

    private int exporterVu(ArgumentsCli arguments, PrintStream sortie) {
        long idPassage = arguments.exigerLong(ARG_PASSAGE);
        Path sortieFichier = Path.of(arguments.exiger("sortie"));

        // L'export canonique vit dans la feature validation (ServiceValidation.exporter prend l'id du
        // jeu de résultats, pas du passage) : on résout d'abord les résultats annotant le passage.
        ResultatsIdentification resultats = injecteur
                .getInstance(ResultatsIdentificationDao.class)
                .findByPassage(idPassage)
                .orElseThrow(() -> new RegleMetierException("Aucun résultat Tadarida importé pour le passage "
                        + idPassage
                        + " : rien à exporter (importez d'abord le CSV d'observations Tadarida)."));

        // inclureMode=true : ajoute la colonne validation_mode (R24) au CSV réinjectable.
        Path ecrit = injecteur.getInstance(ServiceValidation.class).exporter(resultats.id(), sortieFichier, true);
        sortie.println("Export Vu écrit : " + ecrit.toAbsolutePath());
        return CODE_SUCCES;
    }

    // --- Aides internes ---------------------------------------------------------

    private int anneeCourante() {
        return injecteur.getInstance(Horloge.class).aujourdhui().getYear();
    }

    private static int prochainNumero(PassageDao passageDao, long idPoint) {
        return passageDao.findByPoint(idPoint).stream()
                        .mapToInt(Passage::numeroPassage)
                        .max()
                        .orElse(0)
                + 1;
    }

    private static void afficherAide(PrintStream flux) {
        flux.println("VigieChiro — interface en ligne de commande");
        flux.println();
        flux.println("Usage : vigiechiro [--workspace <dir>] <commande> [options]");
        flux.println();
        flux.println("Commandes :");
        flux.println("  lister-passages");
        flux.println("      Liste les passages enregistrés (carré, point, année, statut, verdict).");
        flux.println("  importer --source <dir> --point <id> [--annee N] [--passage N]");
        flux.println("      Importe une nuit (copie protégée + renommage + transformation, P2).");
        flux.println("  exporter-lot --passage <id>");
        flux.println("      Prépare le lot prêt à déposer d'un passage vérifié (P4).");
        flux.println("  exporter-vu --passage <id> --sortie <fichier>");
        flux.println("      Exporte le CSV de résultats validés *_Vu.csv d'un passage (P7).");
        flux.println("  aide");
        flux.println("      Affiche cette aide.");
        flux.println();
        flux.println("Option globale :");
        flux.println("  --workspace <dir>   Surcharge le workspace (sinon <Documents>/VigieChiro-Companion).");
    }

    /// Point d'entrée processus : extrait l'option globale `--workspace`, positionne la
    /// propriété système `vigiechiro.workspace` avant de bâtir l'injecteur, exécute la commande
    /// puis sort avec le code retourné.
    public static void main(String[] args) {
        List<String> restants = new ArrayList<>();
        String workspace = extraireWorkspace(args, restants);
        if (workspace != null) {
            System.setProperty("vigiechiro.workspace", workspace);
        }
        int code = applicative().executer(restants.toArray(new String[0]), System.out, System.err);
        System.exit(code);
    }

    /// Retire l'option globale `--workspace <dir>` du tableau d'arguments (où qu'elle se trouve)
    /// et renvoie sa valeur, en accumulant les autres jetons dans `restants`.
    static String extraireWorkspace(String[] args, List<String> restants) {
        String workspace = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--workspace") && i + 1 < args.length) {
                workspace = args[i + 1];
                i++; // saute la valeur
            } else {
                restants.add(args[i]);
            }
        }
        return workspace;
    }
}
