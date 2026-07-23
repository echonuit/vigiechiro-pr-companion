package fr.univ_amu.iut.importation.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.NommageSequences;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.importation.model.InventaireTransformesReferences.OriginalTransforme;
import fr.univ_amu.iut.importation.model.InventaireTransformesReferences.SequenceTransformee;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Micro;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Crée un **passage qui référence des séquences déjà transformées** : à partir d'un dossier local de WAV
/// `×10 + découpés` (par un autre outil, ou récupérés ailleurs), monte le graphe
/// passage→session→originaux→séquences **sans rejouer la transformation** et le persiste atomiquement,
/// exactement comme [MoteurImport] mais en **sautant l'étage de découpe**.
///
/// **Deux modes** ([#importer] paramètre `referencer`) :
///
/// - `true` : les WAV **restent en place**, chaque [SequenceDEcoute] pointe le fichier externe (aucune
///   copie, aucun octet audio écrit dans l'espace de travail) ;
/// - `false` : les WAV sont **copiés** dans `transformes/` de l'espace de travail, et les séquences
///   pointent la copie interne.
///
/// **Originaux placeholders.** Il n'y a pas de brut derrière ces séquences : chaque
/// [EnregistrementOriginal] est un **placeholder** sans fréquence d'échantillonnage, sans SHA-256, sans
/// durée ni taille (le marqueur que [fr.univ_amu.iut.passage.model.ServiceReactivationPassage] reconnaît).
/// Son `cheminFichier` est une **sentinelle** non ouvrable.
///
/// **Identité des séquences.** Chacune porte sa taille, son [fr.univ_amu.iut.commun.model.Empreintes
/// empreinte courte], sa durée réelle (en-tête ÷ 10) et son horodatage de capture, calculés à
/// l'inscription par [InventaireTransformesReferences] : ce sont les preuves que la réactivation
/// revérifiera au réveil.
///
/// **Découplage.** Le service charge le point d'écoute (et son site) par `idPoint` pour construire le
/// [Prefixe] R6, comme le fait la commande `importer` : la feature dépend de `sites.model` (le graphe de
/// features reste sans cycle, `sites` ne dépendant pas d'`importation`).
public class ServiceImportReference {

    private final PointDao pointDao;
    private final SiteDao siteDao;
    private final AgregatImportDao agregatDao;
    private final UniteDeTravail uniteDeTravail;
    private final Workspace workspace;
    private final ServiceDisponibiliteAudio disponibiliteAudio;
    private final Horloge horloge;
    private final FabriqueEntitesImport fabriqueEntites;

    @Inject
    public ServiceImportReference(
            PointDao pointDao,
            SiteDao siteDao,
            AgregatImportDao agregatDao,
            UniteDeTravail uniteDeTravail,
            Workspace workspace,
            Horloge horloge,
            ServiceDisponibiliteAudio disponibiliteAudio) {
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.agregatDao = Objects.requireNonNull(agregatDao, "agregatDao");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.disponibiliteAudio = Objects.requireNonNull(disponibiliteAudio, "disponibiliteAudio");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.fabriqueEntites = new FabriqueEntitesImport(horloge);
    }

    /// Compte rendu d'un import par référence : le passage créé, le nom de sa session, le nombre de
    /// séquences référencées.
    public record ResultatImportReference(long idPassage, String nomSession, int nombreSequences) {}

    /// Importe un dossier de séquences déjà transformées pour le point `idPoint`.
    ///
    /// @param dossier dossier local des WAV transformés (`×10 + découpés`)
    /// @param idPoint point d'écoute de rattachement (obligatoire)
    /// @param annee année du passage, ou `null` pour l'année courante
    /// @param numeroPassage numéro de passage, ou `null` pour le prochain libre du point
    /// @param referencer `true` pour référencer les WAV en place, `false` pour les copier dans l'espace
    ///     de travail
    /// @param progres suivi de progression (fraction 0→1)
    /// @param jeton jeton d'annulation, vérifié avant le point de non-retour (la persistance)
    /// @return le compte rendu de l'import
    /// @throws RegleMetierException dossier introuvable, aucun WAV, point (ou son site) inconnu, ou
    ///     quadruplet déjà pris (R5)
    public ResultatImportReference importer(
            Path dossier,
            Long idPoint,
            Integer annee,
            Integer numeroPassage,
            boolean referencer,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        Objects.requireNonNull(dossier, "dossier");
        Objects.requireNonNull(idPoint, "idPoint");
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");
        if (!Files.isDirectory(dossier)) {
            throw new RegleMetierException("Dossier de séquences transformées introuvable : " + dossier + ".");
        }
        List<OriginalTransforme> originaux = InventaireTransformesReferences.inventorier(dossier);
        if (originaux.isEmpty()) {
            throw new RegleMetierException("Aucun fichier .wav à référencer dans " + dossier + ".");
        }
        jeton.leverSiAnnule();

        Prefixe prefixe = resoudrePrefixe(idPoint, annee, numeroPassage);
        String nomSession = prefixe.nomDossierSession();
        Path dossierSession = workspace.dossierSession(nomSession);
        Path dossierTransformes = workspace.dossierTransformes(nomSession);

        Path cheminJournal;
        try {
            cheminJournal = JournalDeRepli.ecrireTraceSynthetique(dossierSession);
            if (!referencer) {
                creerDossier(dossierTransformes);
            }
            materialiser(referencer, dossierTransformes, originaux, progres, jeton);
            jeton.leverSiAnnule();
        } catch (RuntimeException echec) {
            ExtracteurZip.supprimerRecursivement(dossierSession);
            throw echec;
        }

        JournalParse journal = JournalDeRepli.depuis(cheminsSequences(originaux));
        long idPassage = persister(journal, idPoint, prefixe, cheminJournal, originaux, referencer, dossierTransformes);
        disponibiliteAudio.invalider(idPassage);
        return new ResultatImportReference(idPassage, nomSession, nombreSequences(originaux));
    }

    /// Charge le point (et son site) par `idPoint`, résout année et numéro effectifs (repli sur l'année
    /// courante et le prochain numéro libre), refuse le doublon R5, et construit le [Prefixe] R6.
    private Prefixe resoudrePrefixe(Long idPoint, Integer annee, Integer numeroPassage) {
        PointDEcoute point = pointDao.findById(idPoint)
                .orElseThrow(() -> new RegleMetierException("Point d'écoute introuvable : " + idPoint + "."));
        Site site = siteDao.findById(point.idSite())
                .orElseThrow(() -> new RegleMetierException(
                        "Site introuvable pour le point " + idPoint + " (incohérence en base)."));
        int anneeEffective = annee != null ? annee : horloge.aujourdhui().getYear();
        int numeroEffectif =
                numeroPassage != null ? numeroPassage : agregatDao.prochainNumeroPassageLibre(idPoint, anneeEffective);
        if (agregatDao.passageExistePour(idPoint, anneeEffective, numeroEffectif)) {
            throw new RegleMetierException("Un passage n°" + numeroEffectif + " existe déjà pour ce point en "
                    + anneeEffective + " (le quadruplet point/année/n° de passage doit être unique).");
        }
        return new Prefixe(site.numeroCarre(), anneeEffective, numeroEffectif, point.code());
    }

    /// Matérialise les séquences avant la persistance : en mode copie, recopie chaque WAV dans
    /// `transformes/` ; en mode référence, rien n'est écrit. Émet la progression par original et vérifie
    /// l'annulation entre deux originaux.
    private static void materialiser(
            boolean referencer,
            Path dossierTransformes,
            List<OriginalTransforme> originaux,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        int total = originaux.size();
        for (int i = 0; i < total; i++) {
            OriginalTransforme groupe = originaux.get(i);
            if (!referencer) {
                copierSequences(groupe, dossierTransformes);
            }
            String phase = referencer ? "Référencement " : "Copie ";
            progres.accept(
                    new Progression(phase + (i + 1) + "/" + total + " · " + groupe.nomOriginal(), fraction(i, total)));
            jeton.leverSiAnnule();
        }
    }

    private static double fraction(int index, int total) {
        return (double) (index + 1) / total;
    }

    private static void creerDossier(Path dossier) {
        try {
            Files.createDirectories(dossier);
        } catch (IOException e) {
            throw new UncheckedIOException("Création du dossier transformes impossible : " + dossier, e);
        }
    }

    private static void copierSequences(OriginalTransforme groupe, Path dossierTransformes) {
        try {
            for (SequenceTransformee seq : groupe.sequences()) {
                Files.copy(
                        seq.cheminAbsolu(),
                        dossierTransformes.resolve(seq.nomFichier()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Copie d'une séquence transformée impossible : " + groupe.nomOriginal(), e);
        }
    }

    /// Persiste l'agrégat **tout ou rien** (O7), dans l'ordre de [MoteurImport] : enregistreur, micro si
    /// présent, passage, session, journal, puis pour chaque original placeholder ses séquences.
    private long persister(
            JournalParse journal,
            Long idPoint,
            Prefixe prefixe,
            Path cheminJournal,
            List<OriginalTransforme> originaux,
            boolean referencer,
            Path dossierTransformes) {
        Enregistreur enregistreur = new Enregistreur(journal.numeroSerie(), journal.versionModele(), null);
        Micro micro = fabriqueEntites.micro(journal);
        Passage passage = fabriqueEntites.passage(journal, idPoint, prefixe, journal.dateDebut());
        SessionDEnregistrement session = sessionDe(prefixe, originaux);
        JournalDuCapteur journalEntite = journalDe(journal, cheminJournal);

        long[] idPassage = new long[1];
        uniteDeTravail.executer(cx -> {
            agregatDao.upsertEnregistreur(cx, enregistreur);
            if (micro != null) {
                agregatDao.insererMicroSiAbsent(cx, micro);
            }
            idPassage[0] = agregatDao.insererPassage(cx, passage);
            long idSession = agregatDao.insererSession(cx, idPassage[0], session);
            agregatDao.insererJournal(cx, idSession, journalEntite);
            for (OriginalTransforme groupe : originaux) {
                long idOriginal = agregatDao.insererOriginal(cx, idSession, placeholder(groupe));
                for (SequenceTransformee seq : groupe.sequences()) {
                    agregatDao.insererSequence(
                            cx, idSession, idOriginal, sequenceDe(seq, referencer, dossierTransformes));
                }
            }
        });
        return idPassage[0];
    }

    /// Session sans bruts : volume d'originaux nul (aucun brut conservé), volume de séquences = somme des
    /// tailles référencées (même forme que `MoteurImport.sessionDe`).
    private SessionDEnregistrement sessionDe(Prefixe prefixe, List<OriginalTransforme> originaux) {
        Path racine = workspace.dossierSession(prefixe.nomDossierSession());
        return new SessionDEnregistrement(null, racine.toString(), 0L, volumeSequences(originaux), null);
    }

    private static JournalDuCapteur journalDe(JournalParse journal, Path cheminJournal) {
        return new JournalDuCapteur(
                null,
                cheminJournal.toString(),
                journal.evenementsJsonPourNuit(journal.dateDebut()),
                journal.anomaliesJsonPourNuit(journal.dateDebut()),
                null);
    }

    /// Original **placeholder** : pas de brut, donc pas de fréquence d'échantillonnage (marqueur reconnu
    /// par la réactivation), pas de SHA-256, pas de durée ni de taille. Le `cheminFichier` sentinelle est
    /// `NOT NULL` mais n'ouvre rien. L'`idSession` est posé par le DAO à l'insertion.
    private static EnregistrementOriginal placeholder(OriginalTransforme groupe) {
        return new EnregistrementOriginal(
                null, groupe.nomOriginal(), groupe.cheminOriginalSentinelle().toString(), null, null, null, null, null);
    }

    /// Séquence référencée : `cheminFichier` = WAV externe (mode référence) ou copie interne (mode copie),
    /// offset dérivé de l'index (tranches de 5 s), preuves d'identité calculées à l'inventaire. Les FK
    /// (`idEnregistrementOriginal`, `idSession`) sont posées par le DAO à l'insertion.
    private static SequenceDEcoute sequenceDe(SequenceTransformee seq, boolean referencer, Path dossierTransformes) {
        Path chemin = referencer ? seq.cheminAbsolu() : dossierTransformes.resolve(seq.nomFichier());
        return new SequenceDEcoute(
                null,
                seq.nomFichier(),
                null,
                seq.index(),
                seq.index() * (double) NommageSequences.DUREE_SEQUENCE_SECONDES,
                seq.dureeReelleSecondes(),
                chemin.toString(),
                false,
                null,
                seq.horodatageCapture(),
                seq.tailleOctets(),
                seq.empreinte());
    }

    private static long volumeSequences(List<OriginalTransforme> originaux) {
        return originaux.stream()
                .flatMap(o -> o.sequences().stream())
                .mapToLong(SequenceTransformee::tailleOctets)
                .sum();
    }

    private static int nombreSequences(List<OriginalTransforme> originaux) {
        return originaux.stream().mapToInt(o -> o.sequences().size()).sum();
    }

    private static List<Path> cheminsSequences(List<OriginalTransforme> originaux) {
        return originaux.stream()
                .flatMap(o -> o.sequences().stream())
                .map(SequenceTransformee::cheminAbsolu)
                .toList();
    }
}
