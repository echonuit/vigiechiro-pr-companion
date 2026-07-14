package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.RapportReactivation.EcartReactivation;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Acceptee;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Refusee;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// **Réactive un passage archivé** (#1302, EPIC #1297) : l'utilisateur désigne le dossier où il a
/// retrouvé ses fichiers, et l'application rebranche, **séquence par séquence**, uniquement ce
/// qu'elle a pu **vérifier**.
///
/// Le risque à fermer n'est pas de recopier des fichiers, c'est de recopier les **mauvais** : deux
/// jeux peuvent porter les mêmes noms sans être les mêmes (redécoupe, autre expansion, autre nuit du
/// même carré). Rebrancher des observations sur le mauvais audio produit un résultat
/// **scientifiquement faux, et silencieux** : l'utilisateur validerait un cri en écoutant autre
/// chose. Chaque candidat passe donc la **cascade de vérification** ([VerificationIdentiteAudio],
/// #1309) : empreinte (#1299) si elle existe, sinon structurelle (nom, taille, durée réelle
/// confrontée à l'en-tête WAV) et acoustique (les cris des observations sont-ils dans le fichier ?).
/// Un fichier qui échoue n'est **jamais** rebranché en silence : il est compté, motivé, rapporté.
///
/// **Passage sans empreinte.** Contrairement à ce que l'issue posait au départ, il **reste
/// réactivable** : c'est tout l'objet de #1309. La cascade descend d'un cran (structurelle +
/// acoustique valent une empreinte quand elles concordent), et le rapport affiche le **niveau de
/// confiance le plus faible** obtenu, jamais un accord opaque.
///
/// **Deux voies, une seule garde** (#1406). L'utilisateur désigne **un dossier** ; l'application
/// reconnaît ce qu'il contient en confrontant les noms des fichiers à ceux qu'elle a en base :
///
/// - il contient les **séquences** (tranches de 5 s) : on les rebranche directement ;
/// - il ne contient que les **bruts** : on les **régénère**. La transformation est **déterministe**
///   (R11, `TransformationAudioTest.determinisme_hash_stable`) et tout ce qu'il faut pour la rejouer à
///   l'identique est en base (nom R6 de l'original, vraie fréquence d'acquisition). Les tranches
///   régénérées passent alors **la même cascade** que n'importe quel candidat : si le code de
///   transformation n'a pas changé, leur empreinte correspond à celle capturée avant l'archivage →
///   identité **certaine** ; s'il a changé, la cascade descend d'un cran (structure + cris) au lieu
///   d'accorder une confiance aveugle.
///
/// C'est le point de tout ceci : la reproductibilité est une **preuve**, pas un prérequis. On ne fait
/// confiance à aucun fichier régénéré - on le **vérifie**, exactement comme les autres.
///
/// Les **bruts ne sont pas recopiés** : ils servent à régénérer, puis on les oublie. Recopier des
/// gigaoctets que l'utilisateur avait justement demandé de libérer serait un contresens.
///
/// **Idempotent** : une séquence dont le fichier est déjà là est comptée `dejaPresentes` et laissée
/// intacte ; rejouer la réactivation ne casse rien. **Non destructif** : les fichiers sont **copiés**
/// depuis le dossier source (jamais déplacés), et ni les observations ni les vérifications ne sont
/// touchées — on rebranche des chemins, on ne recalcule rien.
public class ServiceReactivationPassage {

    private static final String PARAM_ID_PASSAGE = "idPassage";

    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final EnregistrementOriginalDao originalDao;
    private final VerificationIdentiteAudio verification;
    private final ServiceDisponibiliteAudio disponibilite;

    /// Cris attendus des observations (#1309), **optionnel** : absent des injecteurs partiels, la
    /// cascade retombe alors sur la vérification structurelle seule.
    private final Optional<CrisAttendus> crisAttendus;

    /// Régénération des séquences depuis un brut (#1406), **optionnelle** : la transformation vit dans la
    /// feature « Importation », qui est désactivable. Absente, la voie « bruts » se refuse en le disant ;
    /// la voie « transformés » reste entière.
    private final Optional<RegenerationSequences> regeneration;

    public ServiceReactivationPassage(
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            EnregistrementOriginalDao originalDao,
            VerificationIdentiteAudio verification,
            ServiceDisponibiliteAudio disponibilite,
            Optional<CrisAttendus> crisAttendus,
            Optional<RegenerationSequences> regeneration) {
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.originalDao = Objects.requireNonNull(originalDao, "originalDao");
        this.verification = Objects.requireNonNull(verification, "verification");
        this.disponibilite = Objects.requireNonNull(disponibilite, "disponibilite");
        this.crisAttendus = Objects.requireNonNull(crisAttendus, "crisAttendus");
        this.regeneration = Objects.requireNonNull(regeneration, "regeneration");
    }

    /// Réactive le passage depuis `dossierSource` : confronte chaque séquence absente au fichier
    /// homonyme trouvé dans le dossier (exploré **récursivement**, l'utilisateur pouvant désigner la
    /// racine d'une sauvegarde), rebranche ce qui est vérifié, rapporte le reste.
    ///
    /// @param progres notifié au fil des séquences (opération longue : la vérification lit les
    ///     fichiers ; compter ~0,1 ms par séquence avec empreinte, ~3,4 ms sans, cf. #1309)
    /// @throws RegleMetierException si le passage est introuvable, jamais importé, ou si le dossier
    ///     source n'existe pas
    public RapportReactivation reactiver(Long idPassage, Path dossierSource, Consumer<Progression> progres) {
        Objects.requireNonNull(idPassage, PARAM_ID_PASSAGE);
        Objects.requireNonNull(dossierSource, "dossierSource");
        Objects.requireNonNull(progres, "progres");
        if (!Files.isDirectory(dossierSource)) {
            throw new RegleMetierException("Dossier introuvable : " + dossierSource + ".");
        }
        SessionDEnregistrement session = sessionDao
                .trouverParPassage(idPassage)
                .orElseThrow(() -> new RegleMetierException(
                        "Passage jamais importé localement : rien à réactiver pour " + idPassage + "."));

        List<SequenceDEcoute> sequences = sequenceDao.findBySession(session.id());
        Map<String, Path> candidats = indexerParNom(dossierSource);
        List<EnregistrementOriginal> originaux = originalDao.findBySession(session.id());

        // Ce que le dossier contient, constaté et non supposé : des séquences, des bruts, ou rien qui
        // nous concerne. Les séquences priment - elles sont là, il n'y a rien à recalculer.
        VoieReactivation voie = reconnaitre(sequences, originaux, candidats);
        Bilan bilan = voie == VoieReactivation.BRUTS
                ? rebrancherDepuisBruts(session, sequences, originaux, candidats, progres)
                : rebrancher(sequences, candidats, progres);

        disponibilite.invalider(idPassage);
        DecompteAudio decompte = disponibilite.decompte(idPassage);
        sessionDao.majVolumeSequences(session.id(), volumeSequences(sequences));
        // L'audio est entièrement revenu : le passage n'est plus archivé, le marqueur du geste tombe
        // (l'audit redevient exigeant sur ses séquences). Une réactivation **partielle** le conserve :
        // les absences restantes sont toujours expliquées par l'archivage, pas par une corruption.
        if (decompte.disponibilite() == DisponibiliteAudio.COMPLETE) {
            sessionDao.marquerArchivee(session.id(), null);
        }
        return new RapportReactivation(
                bilan.reactivees,
                bilan.ecarts.size(),
                bilan.manquantes,
                bilan.dejaPresentes,
                bilan.confianceMinimale,
                bilan.ecarts,
                decompte,
                voie);
    }

    /// Reconnaît ce que le dossier désigné contient, en confrontant les noms des fichiers trouvés à ceux
    /// que la base connaît. Les **séquences** l'emportent sur les **bruts** : quand les deux sont là (une
    /// sauvegarde de l'espace de travail entier), rebrancher les tranches existantes est à la fois plus
    /// rapide et plus sûr que les recalculer.
    private static VoieReactivation reconnaitre(
            List<SequenceDEcoute> sequences, List<EnregistrementOriginal> originaux, Map<String, Path> candidats) {
        boolean sequencesTrouvees = sequences.stream()
                .filter(sequence -> !Files.exists(Path.of(sequence.cheminFichier())))
                .anyMatch(sequence -> candidats.containsKey(sequence.nomFichier()));
        if (sequencesTrouvees) {
            return VoieReactivation.TRANSFORMES;
        }
        boolean brutsTrouves = originaux.stream().anyMatch(original -> candidatBrut(original, candidats) != null);
        return brutsTrouves ? VoieReactivation.BRUTS : VoieReactivation.AUCUNE;
    }

    /// Le brut d'un original dans le dossier désigné, sous l'un des deux noms sous lesquels un utilisateur
    /// le garde : son **nom R6** (copie du dossier `bruts/`) ou son **nom d'enregistreur** non préfixé
    /// (copie de la carte SD). `null` si aucun des deux n'est là.
    private static Path candidatBrut(EnregistrementOriginal original, Map<String, Path> candidats) {
        Path parNomR6 = candidats.get(original.nomFichier());
        if (parNomR6 != null) {
            return parNomR6;
        }
        int tiret = original.nomFichier().lastIndexOf(Prefixe.TIRET);
        return tiret < 0 ? null : candidats.get(original.nomFichier().substring(tiret + 1));
    }

    /// Voie **bruts** : pour chaque original retrouvé, on **prouve le brut** (SHA-256 intégral capturé à
    /// l'import), on **régénère** ses tranches dans un dossier temporaire, et on les fait passer par la
    /// **même cascade** que n'importe quel candidat. Un brut refusé n'est pas régénéré : on ne recalcule
    /// pas des séquences à partir d'un fichier dont on n'a pas établi l'identité.
    ///
    /// Le temporaire est vidé **original par original** : régénérer une nuit entière d'un coup doublerait
    /// transitoirement l'occupation disque, ce que l'utilisateur cherchait précisément à éviter.
    private Bilan rebrancherDepuisBruts(
            SessionDEnregistrement session,
            List<SequenceDEcoute> sequences,
            List<EnregistrementOriginal> originaux,
            Map<String, Path> candidats,
            Consumer<Progression> progres) {
        RegenerationSequences moteur = regeneration.orElseThrow(() -> new RegleMetierException(
                "Ce dossier ne contient que les enregistrements bruts, et les régénérer demande la"
                        + " fonctionnalité « Importation », désactivée : réactivez-la (menu ☰ >"
                        + " Fonctionnalités) puis recommencez."));
        Prefixe prefixe = prefixeDe(session);

        Bilan bilan = new Bilan();
        int traites = 0;
        for (EnregistrementOriginal original : originaux) {
            traites++;
            progres.accept(new Progression(
                    "Régénération " + traites + "/" + originaux.size(), traites / (double) originaux.size()));
            List<SequenceDEcoute> sesSequences = sequencesDe(sequences, original);
            Path brut = candidatBrut(original, candidats);
            if (brut == null) {
                bilan.manquantes += absentesDuDisque(sesSequences);
                continue;
            }
            if (verification.verifierOriginal(original, brut) instanceof Refusee refus) {
                // Un brut douteux ne redonne pas des séquences douteuses : il ne redonne RIEN.
                bilan.ecarts.add(new EcartReactivation(original.nomFichier(), refus.motif()));
                bilan.manquantes += absentesDuDisque(sesSequences);
                continue;
            }
            regenererEtRebrancher(moteur, bilan, original, sesSequences, brut, prefixe);
        }
        return bilan;
    }

    /// Régénère les tranches d'**un** brut dans un temporaire, les rebranche par la cascade, puis efface
    /// le temporaire (quoi qu'il arrive).
    private void regenererEtRebrancher(
            RegenerationSequences moteur,
            Bilan bilan,
            EnregistrementOriginal original,
            List<SequenceDEcoute> sesSequences,
            Path brut,
            Prefixe prefixe) {
        Path temporaire = temporaire(original);
        try {
            moteur.regenerer(brut, original.nomFichier(), prefixe, frequenceAcquisition(original), temporaire);
            Map<String, Path> regenerees = indexerParNom(temporaire);
            Bilan partiel = rebrancher(sesSequences, regenerees, progres -> {});
            bilan.absorber(partiel);
        } finally {
            effacer(temporaire);
        }
    }

    /// Fréquence d'**acquisition** persistée à l'import (`Fe` du log, pas celle de l'en-tête) : c'est elle
    /// qui pilote le découpage à 5 s réelles, donc elle qui doit être rejouée à l'identique.
    private static int frequenceAcquisition(EnregistrementOriginal original) {
        Integer frequence = original.frequenceEchantillonnageHz();
        if (frequence == null) {
            throw new RegleMetierException("La fréquence d'acquisition de « " + original.nomFichier()
                    + " » est inconnue en base : ses séquences ne peuvent pas être régénérées à l'identique.");
        }
        return frequence;
    }

    /// Préfixe de la session, relu du **nom de son dossier** (`Car…-2026-Pass1-A1`) : c'est le seul endroit
    /// où `passage` peut le retrouver sans dépendre de `sites` (cycle).
    private static Prefixe prefixeDe(SessionDEnregistrement session) {
        Path racine = Path.of(session.cheminRacine());
        Path nom = racine.getFileName();
        return Prefixe.depuisNomDossier(nom == null ? null : nom.toString())
                .orElseThrow(() -> new RegleMetierException("Le dossier de session « " + racine
                        + " » ne porte pas un nom reconnaissable : impossible de régénérer les séquences."));
    }

    private static List<SequenceDEcoute> sequencesDe(List<SequenceDEcoute> sequences, EnregistrementOriginal original) {
        return sequences.stream()
                .filter(sequence -> original.id().equals(sequence.idEnregistrementOriginal()))
                .sorted(Comparator.comparing(SequenceDEcoute::nomFichier))
                .toList();
    }

    private static int absentesDuDisque(List<SequenceDEcoute> sequences) {
        return (int) sequences.stream()
                .filter(sequence -> !Files.exists(Path.of(sequence.cheminFichier())))
                .count();
    }

    private static Path temporaire(EnregistrementOriginal original) {
        try {
            return Files.createTempDirectory("vc-regen-" + original.id() + "-");
        } catch (IOException e) {
            throw new UncheckedIOException("Dossier temporaire impossible pour " + original.nomFichier(), e);
        }
    }

    /// Efface le temporaire **au mieux** : un reliquat dans le dossier temporaire du système n'est pas une
    /// raison de faire échouer une réactivation par ailleurs réussie.
    private static void effacer(Path dossier) {
        try (Stream<Path> contenu = Files.walk(dossier)) {
            contenu.sorted(Comparator.reverseOrder()).forEach(chemin -> {
                try {
                    Files.deleteIfExists(chemin);
                } catch (IOException ignore) {
                    // Reliquat toléré : voir la Javadoc.
                }
            });
        } catch (IOException ignore) {
            // Idem.
        }
    }

    /// Confronte chaque séquence **absente du disque** à son candidat homonyme et rebranche ce qui est
    /// vérifié.
    private Bilan rebrancher(
            List<SequenceDEcoute> sequences, Map<String, Path> candidats, Consumer<Progression> progres) {
        Bilan bilan = new Bilan();
        int traitees = 0;
        for (SequenceDEcoute sequence : sequences) {
            traitees++;
            progres.accept(new Progression(
                    "Vérification " + traitees + "/" + sequences.size(), traitees / (double) sequences.size()));
            Path destination = Path.of(sequence.cheminFichier());
            if (Files.exists(destination)) {
                bilan.dejaPresentes++;
                continue;
            }
            Path candidat = candidats.get(sequence.nomFichier());
            if (candidat == null) {
                bilan.manquantes++;
                continue;
            }
            appliquer(bilan, sequence, candidat, destination);
        }
        return bilan;
    }

    /// Vérifie le candidat puis, s'il est accepté, le **copie** à la place attendue par la base.
    private void appliquer(Bilan bilan, SequenceDEcoute sequence, Path candidat, Path destination) {
        List<CriAttendu> cris =
                crisAttendus.map(port -> port.pour(sequence.id())).orElseGet(List::of);
        VerdictIdentite verdict = verification.verifierSequence(sequence, candidat, cris);
        switch (verdict) {
            case Acceptee acceptee -> {
                copier(candidat, destination);
                bilan.reactivees++;
                bilan.retenirConfiance(acceptee.niveau());
            }
            case Refusee refusee -> bilan.ecarts.add(new EcartReactivation(sequence.nomFichier(), refusee.motif()));
        }
    }

    /// Copie (jamais déplace : la sauvegarde de l'utilisateur reste intacte) le fichier vérifié à
    /// l'emplacement que la base attend.
    private static void copier(Path candidat, Path destination) {
        try {
            Path dossier = destination.getParent();
            if (dossier != null) {
                Files.createDirectories(dossier);
            }
            Files.copy(candidat, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Copie impossible vers " + destination, e);
        }
    }

    /// Index des fichiers du dossier source par **nom simple**, exploré récursivement (l'utilisateur
    /// peut désigner la racine d'une sauvegarde). En cas d'homonymes dans plusieurs sous-dossiers, le
    /// premier rencontré gagne : la cascade tranchera de toute façon sur le **contenu**, et un
    /// candidat refusé est rapporté, pas avalé.
    private static Map<String, Path> indexerParNom(Path dossierSource) {
        Map<String, Path> index = new HashMap<>();
        try (Stream<Path> fichiers = Files.walk(dossierSource)) {
            fichiers.filter(Files::isRegularFile)
                    .forEach(fichier -> index.putIfAbsent(fichier.getFileName().toString(), fichier));
        } catch (IOException e) {
            throw new UncheckedIOException("Exploration impossible du dossier " + dossierSource, e);
        }
        return index;
    }

    /// Volume des séquences **présentes** sur disque, pour remettre la fiche du passage d'aplomb
    /// (l'archivage l'avait mis à zéro).
    private static long volumeSequences(List<SequenceDEcoute> sequences) {
        long total = 0;
        for (SequenceDEcoute sequence : sequences) {
            total += tailleSilencieuse(Path.of(sequence.cheminFichier()));
        }
        return total;
    }

    private static long tailleSilencieuse(Path fichier) {
        try {
            return Files.exists(fichier) ? Files.size(fichier) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    /// Accumulateur de la boucle de rebranchement (mutable, local au service).
    private static final class Bilan {
        private int reactivees;
        private int manquantes;
        private int dejaPresentes;
        private NiveauConfiance confianceMinimale;
        private final List<EcartReactivation> ecarts = new ArrayList<>();

        /// Ajoute le bilan d'**un** brut régénéré au bilan de la nuit.
        private void absorber(Bilan partiel) {
            reactivees += partiel.reactivees;
            manquantes += partiel.manquantes;
            dejaPresentes += partiel.dejaPresentes;
            ecarts.addAll(partiel.ecarts);
            if (partiel.confianceMinimale != null) {
                retenirConfiance(partiel.confianceMinimale);
            }
        }

        /// Retient le niveau **le plus faible** : c'est celui qui qualifie honnêtement la
        /// réactivation entière (une seule séquence vérifiée à la structurelle seule suffit à
        /// ramener l'ensemble à `FORTE`).
        private void retenirConfiance(NiveauConfiance niveau) {
            if (confianceMinimale == null || niveau.ordinal() > confianceMinimale.ordinal()) {
                confianceMinimale = niveau;
            }
        }
    }
}
