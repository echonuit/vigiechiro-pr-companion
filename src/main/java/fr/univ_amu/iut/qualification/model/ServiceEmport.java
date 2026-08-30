package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.EcrivainPaquet;
import fr.univ_amu.iut.passage.model.ManifestePaquet;
import fr.univ_amu.iut.passage.model.OuvertureDePaquet;
import fr.univ_amu.iut.passage.model.PaquetOuvert;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.PlanDePaquet;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Le parcours d'emport, de bout en bout (#4726, ADR 4517 et ADR 4627).
///
/// Les pièces existaient depuis #4705 sans qu'aucun appelant de production ne les relie : ce service
/// est ce chaînon. Il **orchestre** et ne recalcule pas, conduite déjà tenue par [EcrivainPaquet].
///
/// **Il vit dans `qualification` et non dans `passage`.** Il lit une sélection, qui est ici, et écrit
/// un paquet, qui est là-bas. Mesuré : `qualification` importe `passage` dans cinq fichiers,
/// `passage` n'importe jamais `qualification`. C'est le seul sens autorisé.
public class ServiceEmport {

    private final SelectionDao selectionDao;
    private final SequenceDao sequenceDao;
    private final SessionDao sessionDao;
    private final PassageDao passageDao;
    private final PointDao pointDao;
    private final SiteDao siteDao;
    private final UniteDeTravail uniteDeTravail;

    public ServiceEmport(
            SelectionDao selectionDao,
            SequenceDao sequenceDao,
            SessionDao sessionDao,
            PassageDao passageDao,
            PointDao pointDao,
            SiteDao siteDao,
            UniteDeTravail uniteDeTravail) {
        this.selectionDao = Objects.requireNonNull(selectionDao, "selectionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
    }

    /// Ce qu'une reprise a posé.
    ///
    /// @param idPassage le passage du poste destinataire
    /// @param idSelection la sélection reçue, créée par la reprise
    /// @param sequences le nombre de séquences reprises
    /// @param pseudoRelecteur qui a ouvert le paquet
    public record BilanReprise(Long idPassage, Long idSelection, int sequences, String pseudoRelecteur) {}

    /// Un emport **préparé** : ce qu'il pèsera, et ce qu'il écrira, avant d'écrire.
    ///
    /// Le plan commande. Séparer préparer d'écrire est ce qui permet à l'écran d'annoncer le volume
    /// puis d'écrire **exactement ce qu'il a annoncé** ; refaire un plan au moment d'écrire ferait
    /// confirmer un volume et en produire un autre.
    ///
    /// @param destination l'archive à venir, jamais touchée par la préparation
    /// @param plan ce que le paquet pèsera, ventilé par nature
    /// @param manifeste le texte du manifeste, celui-là même que le plan a pesé
    /// @param fichiers les séquences à emporter, dans l'ordre du plan
    public record EmportPrepare(Path destination, PlanDePaquet plan, String manifeste, List<Path> fichiers) {}

    /// Prépare l'emport d'une nuit : **rien n'est écrit**.
    ///
    /// @param idPassage la nuit à emporter
    /// @param destination l'archive à venir
    /// @return ce qui sera écrit, et ce que cela pèsera
    /// @throws IllegalStateException si le passage n'a pas de sélection, ou si une séquence manque
    public EmportPrepare preparer(Long idPassage, Path destination) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(destination, "destination");

        SelectionDEcoute selection = selectionDao
                .findByPassage(idPassage)
                .orElseThrow(() -> new IllegalStateException(
                        "Cette nuit n'a pas de sélection d'écoute : il n'y a rien à faire relire."));
        List<SequenceSelectionnee> rattachements = selectionDao.listerSequences(selection.id());
        Map<Long, SequenceDEcoute> connues = sequenceDao.findParIds(
                rattachements.stream().map(SequenceSelectionnee::idSequence).toList());

        List<ManifestePaquet.SequenceEmportee> emportees = new ArrayList<>();
        List<Path> fichiers = new ArrayList<>();
        for (SequenceSelectionnee rattachement : rattachements) {
            SequenceDEcoute sequence = connues.get(rattachement.idSequence());
            if (sequence == null) {
                throw new IllegalStateException("La séquence " + rattachement.idSequence()
                        + " de la sélection est introuvable : le paquet serait amputé sans le dire.");
            }
            emportees.add(new ManifestePaquet.SequenceEmportee(
                    sequence.nomFichier(), rattachement.position(), rattachement.verdict()));
            fichiers.add(Path.of(sequence.cheminFichier()));
        }

        Prefixe prefixe = prefixeDe(idPassage);
        String manifeste = new ManifestePaquet(
                        prefixe.carre(),
                        prefixe.point(),
                        prefixe.annee(),
                        prefixe.nuit(),
                        selection.methode(),
                        emportees)
                .texte();
        return new EmportPrepare(destination, PlanDePaquet.pour(destination, manifeste, fichiers), manifeste, fichiers);
    }

    /// Écrit un emport **déjà préparé**, sans recalculer ce qu'il emporte.
    ///
    /// @param prepare ce qui a été annoncé, et qui fait foi
    /// @return la taille de l'archive, en octets
    /// @throws IOException sur échec d'écriture
    public long ecrire(EmportPrepare prepare) throws IOException {
        Objects.requireNonNull(prepare, "prepare");
        return EcrivainPaquet.ecrire(prepare.destination(), prepare.plan(), prepare.manifeste(), prepare.fichiers());
    }

    /// Prépare puis écrit, d'un seul geste, pour un appelant qui n'a rien à annoncer.
    ///
    /// @param idPassage la nuit à emporter
    /// @param destination l'archive à écrire
    /// @return la taille de l'archive, en octets
    /// @throws IOException sur échec d'écriture
    public long composer(Long idPassage, Path destination) throws IOException {
        return ecrire(preparer(idPassage, destination));
    }

    /// Reprend un paquet reçu : crée la sélection de l'expéditeur, **figée**, avec ses verdicts.
    ///
    /// **Rien n'est écrit tant que tout n'est pas résolu.** Une nuit que le poste ne connaît pas, ou
    /// une séquence absente, fait échouer la reprise avant la première écriture : une base à demi
    /// reprise ne se distingue pas d'une base complète.
    ///
    /// @param paquet l'archive reçue
    /// @param identite l'identité du relecteur, apposée à l'ouverture
    /// @return ce que la reprise a posé
    /// **Une sélection locale est remplacée**, et c'est le cas normal : sur le poste du relecteur la
    /// nuit existe déjà, et ouvrir l'écran de vérification lui a posé une sélection tirée ici. Ouvrir
    /// un paquet est justement demander à juger **celle de l'expéditeur** ; refuser bloquerait le
    /// relecteur qui a simplement regardé la nuit avant. Le remplacement efface les verdicts locaux,
    /// et l'appelant le fait confirmer (#4728).
    ///
    /// @throws IllegalStateException si l'identité manque, si la nuit est inconnue, ou si une
    ///     séquence du paquet n'existe pas au poste destinataire
    /// @throws IOException sur échec de lecture
    public BilanReprise reprendre(Path paquet, Optional<ProfilVigieChiro> identite) throws IOException {
        Objects.requireNonNull(paquet, "paquet");
        PaquetOuvert ouvert = OuvertureDePaquet.ouvrir(paquet, identite);
        ManifestePaquet manifeste = ManifestePaquet.depuis(ouvert.manifeste());

        Passage passage = passageAttendu(manifeste);
        Map<String, SequenceDEcoute> parNom = sequencesDuPassage(passage.id());
        List<SequenceDEcoute> resolues = new ArrayList<>();
        for (ManifestePaquet.SequenceEmportee emportee : manifeste.sequences()) {
            SequenceDEcoute locale = parNom.get(emportee.nomFichier());
            if (locale == null) {
                throw new IllegalStateException("La séquence « " + emportee.nomFichier()
                        + " » du paquet n'existe pas sur ce poste : la nuit n'y est pas la même.");
            }
            resolues.add(locale);
        }

        // Une sélection locale est REMPLACÉE, atomiquement, sur le patron de `creerSelection`. C'est le
        // cas normal : sur le poste du relecteur la nuit existe, et ouvrir l'écran de vérification lui
        // a posé une sélection tirée ici. Insérer sans supprimer heurtait `passage_id UNIQUE`, et la
        // DataAccessException échappait aux catch de l'appelant : le geste ne rendait aucun compte,
        // ni succès ni refus (#4728).
        Optional<SelectionDEcoute> locale = selectionDao.findByPassage(passage.id());
        uniteDeTravail.executer(connexion -> {
            if (locale.isPresent()) {
                selectionDao.supprimerDansTransaction(connexion, locale.get().id());
            }
            long idSelection = selectionDao.insererDansTransaction(
                    connexion, MethodeSelection.RECUE_D_UN_PAQUET, resolues.size(), passage.id());
            for (int rang = 0; rang < resolues.size(); rang++) {
                selectionDao.attacherDansTransaction(
                        connexion,
                        idSelection,
                        resolues.get(rang).id(),
                        manifeste.sequences().get(rang).position(),
                        false);
            }
        });
        SelectionDEcoute recue = selectionDao
                .findByPassage(passage.id())
                .orElseThrow(() -> new IllegalStateException("Sélection reçue non persistée : " + passage.id()));
        for (int rang = 0; rang < resolues.size(); rang++) {
            ManifestePaquet.SequenceEmportee emportee = manifeste.sequences().get(rang);
            if (emportee.verdict() != VerdictFichier.NON_JUGE) {
                selectionDao.marquerVerdict(recue.id(), resolues.get(rang).id(), emportee.verdict());
            }
        }
        return new BilanReprise(passage.id(), recue.id(), resolues.size(), ouvert.pseudoRelecteur());
    }

    /// Ce qu'un import d'avis a posé.
    ///
    /// @param idPassage la nuit chez l'expéditeur
    /// @param pseudoRelecteur qui a jugé, lu dans le manifeste du retour
    /// @param verdicts le nombre d'avis rangés à côté des nôtres
    public record BilanImportAvis(Long idPassage, String pseudoRelecteur, int verdicts) {}

    /// Renvoie l'avis d'un relecteur : **un manifeste signé, sans aucune séquence**.
    ///
    /// L'expéditeur possède déjà les séquences ; les lui renvoyer doublerait le volume du voyage pour
    /// un contenu qu'il a. Le format de l'aller suffit : [OuvertureDePaquet] refuse un paquet sans
    /// manifeste, jamais un paquet sans séquence.
    ///
    /// @param idPassage la nuit relue, sur le poste du relecteur
    /// @param destination l'archive à écrire
    /// @param pseudoJugeur le relecteur qui signe cet avis
    /// @return ce que l'avis renvoyé porte
    /// @throws IllegalStateException si la nuit n'a pas de sélection à renvoyer
    /// @throws IOException sur échec d'écriture
    public BilanAvisRenvoye renvoyerAvis(Long idPassage, Path destination, String pseudoJugeur) throws IOException {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(pseudoJugeur, "pseudoJugeur");

        SelectionDEcoute selection = selectionDao
                .findByPassage(idPassage)
                .orElseThrow(() -> new IllegalStateException(
                        "Cette nuit n'a pas de sélection d'écoute : il n'y a aucun avis à renvoyer."));
        List<SequenceSelectionnee> rattachements = selectionDao.listerSequences(selection.id());
        Map<Long, SequenceDEcoute> connues = sequenceDao.findParIds(
                rattachements.stream().map(SequenceSelectionnee::idSequence).toList());

        List<ManifestePaquet.SequenceEmportee> juges = new ArrayList<>();
        for (SequenceSelectionnee rattachement : rattachements) {
            SequenceDEcoute sequence = connues.get(rattachement.idSequence());
            if (sequence == null) {
                throw new IllegalStateException("La séquence " + rattachement.idSequence()
                        + " a disparu : l'avis renvoyé serait incomplet sans le dire.");
            }
            juges.add(new ManifestePaquet.SequenceEmportee(
                    sequence.nomFichier(), rattachement.position(), rattachement.verdict()));
        }

        Prefixe prefixe = prefixeDe(idPassage);
        String manifeste = new ManifestePaquet(
                        prefixe.carre(),
                        prefixe.point(),
                        prefixe.annee(),
                        prefixe.nuit(),
                        selection.methode(),
                        pseudoJugeur,
                        juges)
                .texte();
        PlanDePaquet plan = PlanDePaquet.pour(destination, manifeste, List.of());
        long octets = EcrivainPaquet.ecrire(destination, plan, manifeste, List.of());
        return new BilanAvisRenvoye(octets, juges.size(), pseudoJugeur);
    }

    /// Ce qu'un avis renvoyé porte.
    ///
    /// @param octets la taille de l'archive écrite
    /// @param verdicts le nombre de verdicts que le relecteur renvoie
    /// @param pseudoJugeur qui les signe
    public record BilanAvisRenvoye(long octets, int verdicts, String pseudoJugeur) {}

    /// Un import d'avis **préparé** : ce qu'il poserait, et ce qui l'en empêcherait.
    ///
    /// Séparer préparer d'appliquer évite de se servir d'une exception comme d'un branchement :
    /// l'écran lit le plan pour savoir s'il doit demander une confirmation, plutôt que de tenter et
    /// de rattraper.
    ///
    /// @param idPassage la nuit chez l'expéditeur
    /// @param avis l'avis revenu, signé
    /// @param plan ce qu'il poserait, et l'avis qu'il remplacerait
    public record ImportPrepare(Long idPassage, AvisRevenu avis, PlanDeReprise plan) {}

    /// Prépare la reprise d'un avis revenu : **rien n'est écrit**.
    ///
    /// @param paquetRevenu l'archive d'avis
    /// @return ce que la reprise poserait
    /// @throws IllegalStateException si le manifeste n'est pas signé, si la nuit est inconnue, ou si
    ///     une séquence de l'avis n'existe pas ici
    /// @throws IOException sur échec de lecture
    public ImportPrepare preparerImport(Path paquetRevenu) throws IOException {
        Objects.requireNonNull(paquetRevenu, "paquetRevenu");
        ManifestePaquet manifeste = ManifestePaquet.depuis(OuvertureDePaquet.lireManifeste(paquetRevenu));
        if (manifeste.pseudoJugeur() == null) {
            throw new IllegalStateException(
                    "Ce paquet ne dit pas qui a jugé : un avis anonyme ne s'attribue pas (ADR 4517).");
        }

        Passage passage = passageAttendu(manifeste);
        SelectionDEcoute selection = selectionDao
                .findByPassage(passage.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Cette nuit n'a plus de sélection d'écoute : l'avis n'a nulle part où se ranger."));
        Map<String, SequenceDEcoute> parNom = sequencesDuPassage(passage.id());

        Map<Long, VerdictFichier> verdicts = new LinkedHashMap<>();
        for (ManifestePaquet.SequenceEmportee jugee : manifeste.sequences()) {
            SequenceDEcoute locale = parNom.get(jugee.nomFichier());
            if (locale == null) {
                throw new IllegalStateException("La séquence « " + jugee.nomFichier()
                        + " » de l'avis n'existe pas ici : ce paquet ne vient pas de cette nuit.");
            }
            verdicts.put(locale.id(), jugee.verdict());
        }

        AvisRevenu avis = new AvisRevenu(manifeste.pseudoJugeur(), verdicts);
        return new ImportPrepare(
                selection.id(), avis, PlanDeReprise.pour(selectionDao.listerSequences(selection.id()), avis));
    }

    /// Applique une reprise préparée, sans la recalculer.
    ///
    /// @param prepare ce qui a été annoncé, et qui fait foi
    /// @param remplacementConfirme `true` quand l'utilisateur a confirmé d'écraser l'avis présent
    /// @return ce que l'import a posé
    /// @throws IllegalStateException si le plan refuse, ou s'il remplacerait sans confirmation
    public BilanImportAvis appliquerImport(ImportPrepare prepare, boolean remplacementConfirme) {
        Objects.requireNonNull(prepare, "prepare");
        int poses = RepriseAvis.appliquer(
                selectionDao, prepare.idPassage(), prepare.plan(), prepare.avis(), remplacementConfirme);
        return new BilanImportAvis(prepare.idPassage(), prepare.avis().pseudoRelecteur(), poses);
    }

    /// Prépare puis applique, d'un seul geste.
    ///
    /// @param paquetRevenu l'archive d'avis
    /// @param remplacementConfirme `true` quand l'utilisateur a confirmé d'écraser l'avis présent
    /// @return ce que l'import a posé
    /// @throws IOException sur échec de lecture
    public BilanImportAvis importerAvis(Path paquetRevenu, boolean remplacementConfirme) throws IOException {
        return appliquerImport(preparerImport(paquetRevenu), remplacementConfirme);
    }

    /// Le préfixe d'un passage, **lu en base** et non deviné d'un nom de fichier : un fichier renommé
    /// ferait alors mentir le manifeste. C'est le chemin que suit déjà `cli/commande/Importer`.
    ///
    /// **Trois mutants survivent ici, et c'est assumé.** Les trois `orElseThrow` gardent des liens que
    /// les clés étrangères du schéma garantissent : un passage a un point, un point a un site. Aucun
    /// test ne les tue parce qu'aucune base saine ne peut les atteindre, et fabriquer une base cassée
    /// pour les couvrir éprouverait SQLite plutôt que ce service. Les refus **atteignables**, eux, ont
    /// chacun leur test : carré inconnu, point inconnu, nuit inconnue, session absente.
    private Prefixe prefixeDe(Long idPassage) {
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new IllegalStateException("Passage introuvable : " + idPassage));
        PointDEcoute point = pointDao.findById(passage.idPoint())
                .orElseThrow(() -> new IllegalStateException("Point introuvable pour le passage " + idPassage));
        Site site = siteDao.findById(point.idSite())
                .orElseThrow(() -> new IllegalStateException("Site introuvable pour le passage " + idPassage));
        return new Prefixe(site.numeroCarre(), point.code(), passage.annee(), passage.numeroPassage());
    }

    /// Le passage que le manifeste désigne, sur **ce** poste.
    private Passage passageAttendu(ManifestePaquet manifeste) {
        Site site = siteDao.findAll().stream()
                .filter(candidat -> manifeste.carre().equals(candidat.numeroCarre()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Le carré " + manifeste.carre()
                        + " est inconnu de ce poste : la copie signée suppose deux postes qui connaissent"
                        + " la même campagne."));
        PointDEcoute point = pointDao.findBySite(site.id()).stream()
                .filter(candidat -> manifeste.point().equals(candidat.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Le point " + manifeste.point() + " du carré " + manifeste.carre() + " est inconnu ici."));
        return passageDao
                .trouverParPointAnneePassage(point.id(), manifeste.annee(), manifeste.nuit())
                .orElseThrow(() -> new IllegalStateException("La nuit " + manifeste.nuit() + " de " + manifeste.annee()
                        + " sur le carré " + manifeste.carre() + " est inconnue ici."));
    }

    private Map<String, SequenceDEcoute> sequencesDuPassage(Long idPassage) {
        SessionDEnregistrement session = sessionDao
                .trouverParPassage(idPassage)
                .orElseThrow(() -> new IllegalStateException(
                        "Cette nuit n'a aucune session d'enregistrement sur ce poste : rien à quoi rattacher."));
        Map<String, SequenceDEcoute> parNom = new LinkedHashMap<>();
        for (SequenceDEcoute sequence : sequenceDao.findBySession(session.id())) {
            parNom.put(sequence.nomFichier(), sequence);
        }
        return parNom;
    }

    /// Les quatre métadonnées de nuit que le manifeste porte.
    private record Prefixe(String carre, String point, int annee, int nuit) {}
}
