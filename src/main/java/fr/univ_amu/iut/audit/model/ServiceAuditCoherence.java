package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.PresenceFichiers;
import fr.univ_amu.iut.commun.model.PresenceFichiers.Presence;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.VerificationDepot;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Audit de cohérence **disque / base**, en lecture seule. Pour chaque passage, vérifie que les
/// fichiers persistés existent sur disque, que leurs noms portent le préfixe attendu, et que les
/// unités déjà déposées correspondent toujours à ce préfixe ; complété par un balayage inverse
/// (fichiers et dossiers orphelins). Un mode **en ligne** ([#auditerEnLigne]) confronte en plus le dépôt
/// au serveur via [VerificationDepot] (#1132), dégradé proprement hors connexion (injecté en `Optional`).
///
/// Le service construit ses DAO à partir de la [SourceDeDonnees] (les DAO sont de fins adaptateurs
/// sans état) : cela garde le constructeur court et le service testable sur une base SQLite jetable.
public class ServiceAuditCoherence {

    private static final String DETAIL_ABSENT = "Fichier attendu absent du disque.";
    private static final String DETAIL_EXTERNE = "Fichier externe (hors workspace) introuvable : carte SD non montée ?";

    private final PassageDao passageDao;
    private final SessionDao sessionDao;
    private final EnregistrementOriginalDao originalDao;
    private final SequenceDao sequenceDao;
    private final JournalDuCapteurDao journalDao;
    private final ReleveClimatiqueDao releveDao;
    private final ResultatsIdentificationDao resultatsDao;
    private final DepotUniteDao depotDao;
    private final PointDao pointDao;
    private final SiteDao siteDao;
    private final Workspace workspace;
    private final PresenceFichiers presenceFichiers;
    private final BalayageDisque balayage;
    private final AuditEnLigne auditEnLigne;

    public ServiceAuditCoherence(
            SourceDeDonnees source,
            Workspace workspace,
            Optional<VerificationDepot> verificationDepot,
            Optional<AuditPointsServeur> auditPointsServeur) {
        Objects.requireNonNull(source, "source");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.passageDao = new PassageDao(source);
        this.sessionDao = new SessionDao(source);
        this.originalDao = new EnregistrementOriginalDao(source);
        this.sequenceDao = new SequenceDao(source);
        this.journalDao = new JournalDuCapteurDao(source);
        this.releveDao = new ReleveClimatiqueDao(source);
        this.resultatsDao = new ResultatsIdentificationDao(source);
        this.depotDao = new DepotUniteDao(source);
        this.pointDao = new PointDao(source);
        this.siteDao = new SiteDao(source);
        this.presenceFichiers = new PresenceFichiers(workspace);
        this.balayage = new BalayageDisque();
        this.auditEnLigne = new AuditEnLigne(verificationDepot, auditPointsServeur, this.passageDao);
    }

    /// Audite tous les passages, puis les dossiers de session orphelins du workspace.
    public RapportAudit auditerTout() {
        List<ConstatAudit> constats = new ArrayList<>();
        Set<String> racinesConnues = new HashSet<>();
        for (Passage passage : passageDao.findAll()) {
            Optional<SessionDEnregistrement> session = sessionDao.trouverParPassage(passage.id());
            session.ifPresent(s -> racinesConnues.add(normaliser(Path.of(s.cheminRacine()))));
            constats.addAll(auditerUnPassage(passage, session));
        }
        constats.addAll(balayage.dossiersOrphelins(workspace.racine(), racinesConnues));
        return new RapportAudit(constats);
    }

    /// Audite un passage précis (sans le balayage des dossiers orphelins, propre au workspace entier).
    ///
    /// @throws IllegalArgumentException si le passage est introuvable
    public RapportAudit auditerPassage(Long idPassage) {
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new IllegalArgumentException("Passage introuvable : " + idPassage));
        return new RapportAudit(auditerUnPassage(passage, sessionDao.trouverParPassage(idPassage)));
    }

    /// Audit **en ligne** (confrontation au serveur : dépôts #1132 + points #1178), délégué à [AuditEnLigne].
    /// Dégrade proprement hors connexion ; aucune exception ne remonte.
    public RapportAudit auditerEnLigne() {
        return new RapportAudit(auditEnLigne.auditer());
    }

    private List<ConstatAudit> auditerUnPassage(Passage passage, Optional<SessionDEnregistrement> sessionOpt) {
        List<ConstatAudit> constats = new ArrayList<>();
        if (sessionOpt.isEmpty()) {
            constats.add(new ConstatAudit(
                    SeveriteConstat.INFO,
                    CategorieConstat.SESSION_ABSENTE,
                    passage.id(),
                    ciblePassage(passage.id()),
                    "Aucune session d'enregistrement : passage jamais importé."));
            return constats;
        }
        SessionDEnregistrement session = sessionOpt.get();
        List<EnregistrementOriginal> originaux = originalDao.findBySession(session.id());
        List<SequenceDEcoute> sequences = sequenceDao.findBySession(session.id());
        Optional<JournalDuCapteur> journal = journalDao.trouverParSession(session.id());
        Optional<ReleveClimatique> releve = releveDao.trouverParSession(session.id());
        // Des observations rapatriées de la plateforme ne viennent d'AUCUN fichier : leur « chemin » est un
        // marqueur de provenance (#1050). Le chercher sur le disque faisait dire à l'audit qu'un fichier
        // externe était introuvable, et soupçonner une carte SD non montée là où il n'y en a jamais eu.
        Optional<ResultatsIdentification> resultats =
                resultatsDao.findByPassage(passage.id()).filter(ResultatsIdentification::issuDunFichier);

        controleExistence(constats, passage, session, originaux, sequences, journal, releve, resultats);
        controlePrefixe(constats, passage, originaux, sequences);
        constats.addAll(balayage.orphelinsDeSession(
                passage.id(),
                Path.of(session.cheminRacine()),
                cheminsConnus(originaux, sequences, journal, releve, resultats)));
        controleDepot(constats, passage);
        return constats;
    }

    // --- C1 : existence des file_path persistés -------------------------------------------------

    /// Vérifie l'existence de tous les fichiers persistés du passage en **un balayage groupé**
    /// ([PresenceFichiers] : un accès disque par dossier, pas par fichier), puis émet les constats
    /// dans le même ordre qu'avant l'extraction du noyau (#1298).
    ///
    /// **Passage archivé** (#1348, #1300) : l'audio (séquences + originaux) a été supprimé
    /// **volontairement**, marqueur explicite à l'appui : le contrôler fichier par fichier
    /// produirait des milliers d'erreurs fantômes. Il est remplacé par **un seul constat**
    /// [SeveriteConstat#INFO] ([CategorieConstat#AUDIO_ARCHIVE]) ; le journal, le relevé et les
    /// résultats Tadarida, eux, **survivent** à l'archivage : leur absence reste un vrai problème,
    /// contrôlé comme avant.
    private void controleExistence(
            List<ConstatAudit> constats,
            Passage passage,
            SessionDEnregistrement session,
            List<EnregistrementOriginal> originaux,
            List<SequenceDEcoute> sequences,
            Optional<JournalDuCapteur> journal,
            Optional<ReleveClimatique> releve,
            Optional<ResultatsIdentification> resultats) {
        Long idPassage = passage.id();
        boolean audioAudite = !session.archivee();
        boolean originauxAudites = audioAudite && !session.originauxPurges();
        List<String> chemins = new ArrayList<>();
        if (originauxAudites) {
            originaux.forEach(o -> chemins.add(o.cheminFichier()));
        }
        // Les séquences entrent TOUJOURS dans le balayage, même sur un passage archivé : elles ne
        // produisent alors aucun constat d'absence, mais leur décompte (présentes / total) alimente le
        // constat informatif (#1304, parité CLI : `audit-coherence` doit dire la disponibilité). Coût
        // nul : c'est le même listage de dossier, groupé.
        sequences.forEach(s -> chemins.add(s.cheminFichier()));
        journal.ifPresent(j -> chemins.add(j.cheminFichier()));
        releve.ifPresent(r -> chemins.add(r.cheminFichier()));
        resultats.ifPresent(r -> chemins.add(r.cheminFichier()));
        Map<String, Presence> presences = presenceFichiers.evaluer(chemins);

        if (session.archivee()) {
            constats.add(constatArchive(idPassage, session, sequences, presences));
        }
        if (originauxAudites) {
            for (EnregistrementOriginal original : originaux) {
                signalerAbsence(constats, idPassage, original.cheminFichier(), SeveriteConstat.ERREUR, presences);
            }
        }
        if (audioAudite) {
            for (SequenceDEcoute sequence : sequences) {
                signalerAbsence(constats, idPassage, sequence.cheminFichier(), SeveriteConstat.ERREUR, presences);
            }
        }
        journal.ifPresent(
                j -> signalerAbsence(constats, idPassage, j.cheminFichier(), SeveriteConstat.ERREUR, presences));
        releve.ifPresent(
                r -> signalerAbsence(constats, idPassage, r.cheminFichier(), SeveriteConstat.AVERTISSEMENT, presences));
        resultats.ifPresent(
                r -> signalerAbsence(constats, idPassage, r.cheminFichier(), SeveriteConstat.AVERTISSEMENT, presences));
    }

    /// Constat informatif d'un passage **archivé** : la disponibilité de son audio, avec le décompte
    /// `présentes / total` (#1304). Un passage réactivé **partiellement** (#1302) est donc décrit tel
    /// quel, sans qu'aucune séquence absente ne soit signalée comme une corruption.
    private static ConstatAudit constatArchive(
            Long idPassage,
            SessionDEnregistrement session,
            List<SequenceDEcoute> sequences,
            Map<String, Presence> presences) {
        long presentes = sequences.stream()
                .filter(sequence -> presences.get(sequence.cheminFichier()) == Presence.PRESENTE)
                .count();
        return new ConstatAudit(
                SeveriteConstat.INFO,
                CategorieConstat.AUDIO_ARCHIVE,
                idPassage,
                ciblePassage(idPassage),
                "Passage archivé le " + session.horodatageArchivage().toLocalDate() + " : audio "
                        + (presentes == 0 ? "ABSENTE" : "PARTIELLE") + " (" + presentes + "/" + sequences.size()
                        + " séquence(s) sur disque). Consultable, réactivable par réimport (« reactiver »).");
    }

    /// Ajoute un constat si le fichier est absent du verdict groupé. Un chemin **hors workspace**
    /// (carte SD externe) est signalé en [SeveriteConstat#INFO] (média peut-être non monté) ; sous le
    /// workspace, la gravité passée s'applique. Les chemins vides, ignorés du balayage, sont sans
    /// constat (comme avant #1298).
    private void signalerAbsence(
            List<ConstatAudit> constats,
            Long idPassage,
            String chemin,
            SeveriteConstat severiteSousWorkspace,
            Map<String, Presence> presences) {
        Presence verdict = presences.get(chemin);
        if (verdict == null || verdict == Presence.PRESENTE) {
            return;
        }
        boolean interne = verdict == Presence.ABSENTE;
        constats.add(new ConstatAudit(
                interne ? severiteSousWorkspace : SeveriteConstat.INFO,
                CategorieConstat.DISQUE_MANQUANT,
                idPassage,
                chemin,
                interne ? DETAIL_ABSENT : DETAIL_EXTERNE));
    }

    // --- C2 : conformité de préfixe des noms de fichiers ----------------------------------------

    private void controlePrefixe(
            List<ConstatAudit> constats,
            Passage passage,
            List<EnregistrementOriginal> originaux,
            List<SequenceDEcoute> sequences) {
        Optional<Prefixe> prefixeOpt = prefixeAttendu(passage);
        if (prefixeOpt.isEmpty()) {
            constats.add(new ConstatAudit(
                    SeveriteConstat.ERREUR,
                    CategorieConstat.PREFIXE_NON_CONFORME,
                    passage.id(),
                    ciblePassage(passage.id()),
                    "Préfixe attendu incalculable : point d'écoute ou site introuvable."));
            return;
        }
        String prefixe = prefixeOpt.get().prefixeFichier();
        for (EnregistrementOriginal original : originaux) {
            signalerPrefixe(constats, passage.id(), original.nomFichier(), prefixe);
        }
        for (SequenceDEcoute sequence : sequences) {
            signalerPrefixe(constats, passage.id(), sequence.nomFichier(), prefixe);
        }
    }

    private void signalerPrefixe(List<ConstatAudit> constats, Long idPassage, String nom, String prefixe) {
        if (nom != null && !nom.startsWith(prefixe)) {
            constats.add(new ConstatAudit(
                    SeveriteConstat.ERREUR,
                    CategorieConstat.PREFIXE_NON_CONFORME,
                    idPassage,
                    nom,
                    "Nom « " + nom + " » sans le préfixe attendu « " + prefixe + " »."));
        }
    }

    // --- C3 : unités déposées divergentes (renommage après dépôt), sans réseau ------------------

    private void controleDepot(List<ConstatAudit> constats, Passage passage) {
        Optional<Prefixe> prefixeOpt = prefixeAttendu(passage);
        if (prefixeOpt.isEmpty()) {
            return;
        }
        String prefixe = prefixeOpt.get().prefixeFichier();
        for (DepotUnite unite : depotDao.parPassage(passage.id())) {
            String nom = unite.identifiantUnite();
            if (nom != null && !nom.startsWith(prefixe)) {
                constats.add(new ConstatAudit(
                        SeveriteConstat.ERREUR,
                        CategorieConstat.DEPOT_DIVERGENT,
                        passage.id(),
                        nom,
                        "Unité déposée « " + nom + " » ne correspond plus au préfixe courant « " + prefixe
                                + " » : renommage après dépôt, divergence base / serveur."));
            }
        }
    }

    // --- Helpers --------------------------------------------------------------------------------

    /// Préfixe `Car<carré>-<année>-Pass<n>-<point>-` attendu, calculé depuis le point puis le site
    /// (même chaîne que la vérification pré-dépôt du lot).
    private Optional<Prefixe> prefixeAttendu(Passage passage) {
        return pointDao.findById(passage.idPoint())
                .flatMap(point -> siteDao.findById(point.idSite())
                        .map(site -> new Prefixe(
                                site.numeroCarre(), passage.annee(), passage.numeroPassage(), point.code())));
    }

    private Set<String> cheminsConnus(
            List<EnregistrementOriginal> originaux,
            List<SequenceDEcoute> sequences,
            Optional<JournalDuCapteur> journal,
            Optional<ReleveClimatique> releve,
            Optional<ResultatsIdentification> resultats) {
        Set<String> chemins = new HashSet<>();
        for (EnregistrementOriginal original : originaux) {
            ajouterChemin(chemins, original.cheminFichier());
        }
        for (SequenceDEcoute sequence : sequences) {
            ajouterChemin(chemins, sequence.cheminFichier());
        }
        journal.ifPresent(j -> ajouterChemin(chemins, j.cheminFichier()));
        releve.ifPresent(r -> ajouterChemin(chemins, r.cheminFichier()));
        resultats.ifPresent(r -> ajouterChemin(chemins, r.cheminFichier()));
        return chemins;
    }

    private static void ajouterChemin(Set<String> chemins, String chemin) {
        if (chemin != null && !chemin.isBlank()) {
            chemins.add(normaliser(Path.of(chemin)));
        }
    }

    /// Cible « passage <id> » d'un constat qui porte sur le passage entier (pas sur un fichier précis).
    private static String ciblePassage(Long idPassage) {
        return "passage " + idPassage;
    }

    private static String normaliser(Path chemin) {
        return chemin.toAbsolutePath().normalize().toString();
    }
}
