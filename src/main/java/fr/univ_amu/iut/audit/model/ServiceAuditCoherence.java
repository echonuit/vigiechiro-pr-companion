package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.DepotUnite;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Audit de cohérence **disque / base**, en lecture seule. Pour chaque passage, vérifie que les
/// fichiers persistés existent sur disque, que leurs noms portent le préfixe attendu, et que les
/// unités déjà déposées correspondent toujours à ce préfixe ; complété par un balayage inverse
/// (fichiers et dossiers orphelins). Aucun accès réseau : la confrontation au serveur relève d'une
/// évolution ultérieure (audit en ligne).
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
    private final BalayageDisque balayage;

    public ServiceAuditCoherence(SourceDeDonnees source, Workspace workspace) {
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
        this.balayage = new BalayageDisque();
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

    private List<ConstatAudit> auditerUnPassage(Passage passage, Optional<SessionDEnregistrement> sessionOpt) {
        List<ConstatAudit> constats = new ArrayList<>();
        if (sessionOpt.isEmpty()) {
            constats.add(new ConstatAudit(
                    SeveriteConstat.INFO,
                    CategorieConstat.SESSION_ABSENTE,
                    passage.id(),
                    "passage " + passage.id(),
                    "Aucune session d'enregistrement : passage jamais importé."));
            return constats;
        }
        SessionDEnregistrement session = sessionOpt.get();
        List<EnregistrementOriginal> originaux = originalDao.findBySession(session.id());
        List<SequenceDEcoute> sequences = sequenceDao.findBySession(session.id());
        Optional<JournalDuCapteur> journal = journalDao.trouverParSession(session.id());
        Optional<ReleveClimatique> releve = releveDao.trouverParSession(session.id());
        Optional<ResultatsIdentification> resultats = resultatsDao.findByPassage(passage.id());

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
        if (!originauxPurges(session)) {
            for (EnregistrementOriginal original : originaux) {
                verifierExistence(constats, idPassage, original.cheminFichier(), SeveriteConstat.ERREUR);
            }
        }
        for (SequenceDEcoute sequence : sequences) {
            verifierExistence(constats, idPassage, sequence.cheminFichier(), SeveriteConstat.ERREUR);
        }
        journal.ifPresent(j -> verifierExistence(constats, idPassage, j.cheminFichier(), SeveriteConstat.ERREUR));
        releve.ifPresent(r -> verifierExistence(constats, idPassage, r.cheminFichier(), SeveriteConstat.AVERTISSEMENT));
        resultats.ifPresent(
                r -> verifierExistence(constats, idPassage, r.cheminFichier(), SeveriteConstat.AVERTISSEMENT));
    }

    /// Ajoute un constat si le fichier est absent. Un chemin **hors workspace** (carte SD externe) est
    /// signalé en [SeveriteConstat#INFO] (média peut-être non monté) ; sous le workspace, la gravité
    /// passée s'applique.
    private void verifierExistence(
            List<ConstatAudit> constats, Long idPassage, String chemin, SeveriteConstat severiteSousWorkspace) {
        if (chemin == null || chemin.isBlank()) {
            return;
        }
        Path fichier = Path.of(chemin);
        if (Files.exists(fichier)) {
            return;
        }
        boolean interne = estSousWorkspace(fichier);
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
                    "passage " + passage.id(),
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

    private boolean originauxPurges(SessionDEnregistrement session) {
        return session.volumeOriginauxOctets() != null && session.volumeOriginauxOctets() == 0L;
    }

    private boolean estSousWorkspace(Path fichier) {
        return fichier.toAbsolutePath().normalize().startsWith(workspace.racine());
    }

    private static String normaliser(Path chemin) {
        return chemin.toAbsolutePath().normalize().toString();
    }
}
