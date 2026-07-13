package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.RapportReactivation.EcartReactivation;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Acceptee;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Refusee;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
/// **Périmètre : les transformés.** On rebranche les séquences 5 s, celles que pointe
/// `listening_sequence.file_path` et que Tadarida a analysées : la voie **exacte et vérifiable**. La
/// voie « réimporter les bruts et rejouer la découpe » suppose une transformation reproductible au
/// nom et à l'octet près : plausible, non prouvée, donc hors périmètre (elle rouvrirait le piège même
/// que l'empreinte referme).
///
/// **Idempotent** : une séquence dont le fichier est déjà là est comptée `dejaPresentes` et laissée
/// intacte ; rejouer la réactivation ne casse rien. **Non destructif** : les fichiers sont **copiés**
/// depuis le dossier source (jamais déplacés), et ni les observations ni les vérifications ne sont
/// touchées — on rebranche des chemins, on ne recalcule rien.
public class ServiceReactivationPassage {

    private static final String PARAM_ID_PASSAGE = "idPassage";

    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final VerificationIdentiteAudio verification;
    private final ServiceDisponibiliteAudio disponibilite;

    /// Cris attendus des observations (#1309), **optionnel** : absent des injecteurs partiels, la
    /// cascade retombe alors sur la vérification structurelle seule.
    private final Optional<CrisAttendus> crisAttendus;

    public ServiceReactivationPassage(
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            VerificationIdentiteAudio verification,
            ServiceDisponibiliteAudio disponibilite,
            Optional<CrisAttendus> crisAttendus) {
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.verification = Objects.requireNonNull(verification, "verification");
        this.disponibilite = Objects.requireNonNull(disponibilite, "disponibilite");
        this.crisAttendus = Objects.requireNonNull(crisAttendus, "crisAttendus");
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
        Bilan bilan = rebrancher(sequences, candidats, progres);

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
                decompte);
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
