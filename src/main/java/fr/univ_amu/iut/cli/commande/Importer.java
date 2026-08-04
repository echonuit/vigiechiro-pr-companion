package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.importation.model.ApercuEcrasement;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ReglageConservationOriginaux;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.VolumesImport;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `importer` (P2) : importe une nuit d'enregistrement (copie protégée + renommage + transformation) pour
/// un point d'écoute, via [ServiceImport]. L'année et le numéro de passage sont **déduits** si omis (année
/// courante, prochain numéro libre du point).
@Command(
        name = "importer",
        description = "Importe une nuit d'enregistrement (copie, renommage, transformation) pour un point.")
public final class Importer implements Callable<Integer> {

    /// Code de sortie d'un refus faute de `--ecraser` : distinct du succès (0) et de l'échec (1).
    /// Rien n'a été importé, et rien n'a été détruit.
    private static final int CODE_REFUS = 2;

    @Option(
            names = "--source",
            required = true,
            paramLabel = "<chemin>",
            description = "Dossier de la carte SD (ou copie), ou archive .zip de ce dossier. Lu en"
                    + " seule lecture ; une archive est décompressée sous le dossier de travail, puis"
                    + " le temporaire est effacé.")
    private Path source;

    @Option(
            names = "--point",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant du point d'écoute auquel rattacher la nuit (voir « lister-passages »).")
    private long point;

    @Option(names = "--annee", paramLabel = "<N>", description = "Année du passage. Défaut : année courante.")
    private Integer annee;

    @Option(
            names = "--passage",
            paramLabel = "<N>",
            description = "Numéro de passage. Défaut : prochain numéro libre pour ce point.")
    private Integer numeroPassage;

    @Option(
            names = "--conserver-originaux",
            description = "Copie les WAV bruts dans bruts/ avant transformation (option de ré-analyse :"
                    + " plusieurs Go par nuit, import environ trois fois plus long). Par défaut, le réglage"
                    + " « Conserver les originaux pour ré-analyse ultérieure ».")
    private Boolean conserverOriginaux;

    @Option(
            names = "--sans-originaux",
            description = "Force la transformation directe depuis la source, sans copier les bruts, quel que"
                    + " soit le réglage. Incompatible avec --conserver-originaux.")
    private boolean sansOriginaux;

    @Option(
            names = "--ecraser",
            description = "Écrase le passage existant qui porte déjà ce numéro : sa nuit entière (séquences,"
                    + " validations Tadarida) est DÉFINITIVEMENT perdue. Sans cette option, une collision"
                    + " chiffre la perte et n'importe rien.")
    private boolean ecraser;

    @Spec
    private CommandSpec spec;

    private final PointDao pointDao;
    private final SiteDao siteDao;
    private final PassageDao passageDao;
    private final ServiceImport service;
    private final Horloge horloge;

    /// Réglages : la commande y lit le mode de conservation par défaut, comme l'IHM (#2064).
    private final Reglages reglages;

    @Inject
    public Importer(
            PointDao pointDao,
            SiteDao siteDao,
            PassageDao passageDao,
            ServiceImport service,
            Horloge horloge,
            Reglages reglages) {
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.service = Objects.requireNonNull(service, "service");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.reglages = Objects.requireNonNull(reglages, "reglages");
    }

    @Override
    public Integer call() {
        // Une archive est décompressée sous le workspace, puis effacée quoi qu'il arrive : un script
        // n'a aucun écran où repasser, donc le temporaire se nettoie ici ou jamais (#3195).
        Path temporaire = ExtracteurZip.estZip(source) ? extraireArchive() : null;
        try {
            return importerDepuis(temporaire == null ? source : ExtracteurZip.racineEffective(temporaire));
        } finally {
            if (temporaire != null) {
                ExtracteurZip.supprimerRecursivement(temporaire);
            }
        }
    }

    /// Décompresse l'archive désignée par `--source` **sous le workspace**, jamais sous `/tmp` : ce
    /// dernier est souvent un tmpfs en RAM, où une nuit de ~10 Go finit en « Aucun espace disponible ».
    /// Même choix que l'écran d'import, et pour la même raison.
    ///
    /// Les bornes de ressources (#2732), la garde « zip-slip » et l'inventaire préalable viennent avec
    /// [ExtracteurZip] : un refus y lève une `RegleMetierException`, que la CLI traduit déjà en code 2.
    private Path extraireArchive() {
        Path base = service.racineWorkspace();
        // Même filet anti-fuite que l'IHM : un import précédent interrompu a pu laisser un temporaire.
        ExtracteurZip.nettoyerTemporairesResiduels(base);
        // Sur la sortie d'erreur : stdout reste le compte rendu, qu'un script peut vouloir analyser.
        spec.commandLine().getErr().println("Décompression de l'archive…");
        return ExtracteurZip.extraireVersDossierTemporaire(source, base);
    }

    private Integer importerDepuis(Path source) {
        PrintWriter sortie = spec.commandLine().getOut();

        PointDEcoute pointDEcoute = pointDao.findById(point)
                .orElseThrow(() -> new ErreurUsage("Point d'écoute introuvable : --point " + point + "."));
        Site site = siteDao.findById(pointDEcoute.idSite())
                .orElseThrow(
                        () -> new ErreurUsage("Site introuvable pour le point " + point + " (incohérence en base)."));

        int anneeEffective = annee != null ? annee : horloge.aujourdhui().getYear();
        int numeroEffectif = numeroPassage != null ? numeroPassage : prochainNumero(passageDao, point);
        Prefixe prefixe = new Prefixe(site.numeroCarre(), anneeEffective, numeroEffectif, pointDEcoute.code());

        // Collision de numéro : c'est le seul cas où l'import détruit quelque chose. On chiffre la perte
        // AVANT d'agir, comme la double confirmation de l'IHM, et sans --ecraser on n'importe rien (#2278).
        // Rien de tout cela ne s'imprime sur le chemin nominal : un numéro libre ne déclenche aucune ligne.
        boolean collision = service.numeroPassageDejaUtilise(point, anneeEffective, numeroEffectif);
        if (collision) {
            annoncerEcrasement(sortie, service.apercuEcrasement(point, anneeEffective, numeroEffectif), numeroEffectif);
            if (!ecraser) {
                spec.commandLine()
                        .getErr()
                        .println("Rien n'a été importé. Ajoutez --ecraser pour assumer cette perte.");
                return CODE_REFUS;
            }
        }

        ResultatImport resultat = importerSelonLeMode(source, point, prefixe, collision);

        String quadruplet = "carré " + site.numeroCarre()
                + " / point " + pointDEcoute.code()
                + " / " + anneeEffective + " / passage " + numeroEffectif;
        sortie.println(rendreBilan(resultat, quadruplet));
        Path rapportCsv = Path.of(resultat.session().cheminRacine()).resolve("rapport-import.csv");
        try {
            Files.writeString(rapportCsv, resultat.rapport().versCsv());
            sortie.println("  Rapport CSV : " + rapportCsv.toAbsolutePath());
        } catch (IOException echec) {
            sortie.println("  (rapport CSV non écrit : " + echec.getMessage() + ")");
        }
        return 0;
    }

    /// Rendu texte de la fin d'import. **Fonction pure** (aucune E/S, aucune base) : c'est ce qui la rend
    /// testable, et c'est le patron déjà en place chez sa voisine `deposer-vigiechiro`
    /// ([DeposerVigieChiro#rendreBilan]).
    ///
    /// Elle est extraite pour #2656 : trois lignes de cette sortie - les volumes, la participation créée,
    /// et leurs conditions - n'étaient exercées par **aucun** test. `CliImportTest` construit l'injecteur
    /// applicatif réel, sans passerelle de synchronisation : `participationCreee()` y est toujours faux,
    /// et la ligne jamais imprimée. Le trou était déclaré ; il est comblé.
    ///
    /// @param resultat ce que l'import a produit
    /// @param quadruplet carré / point / année / passage, composé par l'appelant qui seul les connaît
    static String rendreBilan(ResultatImport resultat, String quadruplet) {
        StringBuilder texte = new StringBuilder("Import réussi.\n");
        ligne(texte, "Passage     ", "#" + resultat.passage().id());
        ligne(texte, "Quadruplet  ", quadruplet);
        ligne(texte, "Statut      ", resultat.passage().statutWorkflow().libelle());
        ligne(texte, "Enregistreur", resultat.numeroSerieEnregistreur());
        ligne(texte, "Originaux   ", Integer.toString(resultat.nombreOriginaux()));
        ligne(texte, "Séquences   ", Integer.toString(resultat.nombreSequences()));
        // Parité avec l'IHM (clôture #2350) : le compte rendu chiffré de l'écran compare le volume LU sur
        // la carte au volume ÉCRIT sur le disque (#2358). La ligne de commande ne peut pas dessiner de
        // barres, mais elle peut dire les mêmes chiffres - c'est une donnée, pas une mise en forme.
        if (!resultat.volumes().estVide()) {
            ligne(texte, "Lu / écrit  ", volumesLisibles(resultat.volumes()));
        }
        // #1488 : l'import crée une participation sur un serveur distant. L'écran le dit depuis la clôture
        // du lot 2 ; la taire ici laisserait l'écriture se découvrir ailleurs, ce que l'issue interdit.
        if (resultat.participationCreee()) {
            ligne(texte, "Vigie-Chiro ", "participation créée (le dépôt la réutilisera)");
        }
        RapportImport rapport = resultat.rapport();
        ligne(texte, "Rapport     ", rapport.resume());
        // Parité avec l'IHM (clôture #2004) : l'écran d'import rend ces deux points depuis #2044, la
        // ligne de commande les taisait. Le doublon de nuit change ce que l'utilisateur croit avoir
        // importé, et les anomalies du journal étaient transportées jusqu'ici sans être montrées nulle
        // part - une capacité livrée d'un seul côté est à moitié livrée.
        for (PassageExistant doublon : rapport.doublonsDeNuit()) {
            ligne(
                    texte,
                    "Doublon     ",
                    "nuit déjà importée en passage n° " + doublon.numeroPassage() + " (" + doublon.annee()
                            + ") au carré " + doublon.carre() + ", point " + doublon.codePoint());
        }
        for (String anomalie : resultat.anomalies()) {
            ligne(texte, "Anomalie    ", anomalie);
        }
        return texte.toString().stripTrailing();
    }

    private static void ligne(StringBuilder texte, String libelle, String valeur) {
        texte.append("  ").append(libelle).append(": ").append(valeur).append('\n');
    }

    /// Les volumes lus et écrits, dans les mots de l'écran.
    ///
    /// La part des **bruts conservés** n'est dite que si elle existe : le compte rendu chiffré de l'écran
    /// omet ce segment quand le volume est nul, au même titre qu'il omet une part de ventilation à zéro.
    /// Une mention « dont 0 Ko de bruts conservés » annoncerait une absence, et ferait chercher ce qui
    /// n'a pas eu lieu (vu à la recette d'import, clôture #2350).
    private static String volumesLisibles(VolumesImport volumes) {
        String phrase = Formats.octetsLisibles(volumes.octetsLus())
                + " lus sur la source, "
                + Formats.octetsLisibles(volumes.octetsEcrits())
                + " écrits";
        if (volumes.octetsBruts() > 0) {
            phrase += " (dont " + Formats.octetsLisibles(volumes.octetsBruts()) + " de bruts conservés)";
        }
        return phrase;
    }

    /// Lance l'import dans le mode demandé : les options priment sur le **réglage**, qui sert de défaut.
    ///
    /// La commande lit le réglage **elle-même**, comme l'IHM. Auparavant elle passait par une variante
    /// courte du service qui conservait en **dur** : la CLI gardait donc toujours les originaux quel que
    /// soit le réglage, et le même geste ne faisait pas la même chose des deux côtés (#2064,
    /// [ADR 0014]). Le service, lui, n'a pas à connaître une préférence d'interface.
    private ResultatImport importerSelonLeMode(Path source, long point, Prefixe prefixe, boolean ecraserLExistant) {
        if (conserverOriginaux != null && sansOriginaux) {
            throw new RegleMetierException("--conserver-originaux et --sans-originaux s'excluent :"
                    + " choisissez l'un ou l'autre, ou aucun pour suivre le réglage.");
        }
        boolean conserver;
        if (sansOriginaux) {
            conserver = false;
        } else if (conserverOriginaux != null) {
            conserver = conserverOriginaux;
        } else {
            conserver = reglages.lireBooleen(ReglageConservationOriginaux.CLE, ReglageConservationOriginaux.DEFAUT);
        }
        // L'écrasement est une méthode DÉDIÉE du service, pas un drapeau : elle sauvegarde d'abord, protège
        // l'ancienne session sur disque, et supprime l'ancien passage dans la transaction d'insertion.
        return ecraserLExistant
                ? service.ecraserEtImporter(source, point, prefixe, p -> {}, JetonAnnulation.neutre(), conserver)
                : service.importer(source, point, prefixe, p -> {}, JetonAnnulation.neutre(), conserver);
    }

    /// Chiffre ce que l'écrasement détruirait, avec les mots de l'IHM ([ConfirmationsImport]) : les deux
    /// surfaces doivent dire la même chose d'un même geste.
    private static void annoncerEcrasement(PrintWriter sortie, ApercuEcrasement apercu, int numeroPassage) {
        sortie.println("Le n° de passage " + numeroPassage + " est déjà utilisé pour ce point.");
        sortie.println(
                "  Suppression DÉFINITIVE du passage existant et de ses " + apercu.sequences() + " séquence(s).");
        if (apercu.validations() > 0) {
            sortie.println("  Dont " + apercu.validations()
                    + " validation(s) Tadarida (correction, référence, commentaire) définitivement perdue(s).");
        }
    }

    private static int prochainNumero(PassageDao passageDao, long idPoint) {
        return passageDao.findByPoint(idPoint).stream()
                        .mapToInt(Passage::numeroPassage)
                        .max()
                        .orElse(0)
                + 1;
    }
}
