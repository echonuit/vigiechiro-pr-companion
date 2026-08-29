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

    /// Compose le paquet d'une nuit depuis sa sélection d'écoute, et l'écrit.
    ///
    /// @param idPassage la nuit à emporter
    /// @param destination l'archive à écrire
    /// @return la taille de l'archive, en octets
    /// @throws IllegalStateException si le passage n'a pas de sélection, ou si une séquence manque
    /// @throws IOException sur échec d'écriture
    public long composer(Long idPassage, Path destination) throws IOException {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(destination, "destination");

        SelectionDEcoute selection = selectionDao
                .findByPassage(idPassage)
                .orElseThrow(() -> new IllegalStateException(
                        "Cette nuit n'a pas de sélection d'écoute : il n'y a rien à faire relire."));
        Map<Long, SequenceDEcoute> connues =
                sequenceDao.findParIds(selectionDao.listerSequences(selection.id()).stream()
                        .map(SequenceSelectionnee::idSequence)
                        .toList());

        List<ManifestePaquet.SequenceEmportee> emportees = new ArrayList<>();
        List<Path> fichiers = new ArrayList<>();
        for (SequenceSelectionnee rattachement : selectionDao.listerSequences(selection.id())) {
            SequenceDEcoute sequence = connues.get(rattachement.idSequence());
            if (sequence == null) {
                throw new IllegalStateException("La séquence " + rattachement.idSequence()
                        + " de la sélection est introuvable : le paquet serait amputé sans le dire.");
            }
            emportees.add(new ManifestePaquet.SequenceEmportee(
                    sequence.nomFichier(), rattachement.position(), rattachement.verdict()));
            fichiers.add(Path.of(sequence.cheminFichier()));
        }

        String manifeste = new ManifestePaquet(
                        prefixeDe(idPassage).carre(),
                        prefixeDe(idPassage).point(),
                        prefixeDe(idPassage).annee(),
                        prefixeDe(idPassage).nuit(),
                        selection.methode(),
                        emportees)
                .texte();
        PlanDePaquet plan = PlanDePaquet.pour(destination, manifeste, fichiers);
        return EcrivainPaquet.ecrire(destination, plan, manifeste, fichiers);
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

        SelectionDEcoute selection = selectionDao.insert(
                new SelectionDEcoute(null, MethodeSelection.RECUE_D_UN_PAQUET, resolues.size(), passage.id()));
        uniteDeTravail.executer(connexion -> {
            for (int rang = 0; rang < resolues.size(); rang++) {
                ManifestePaquet.SequenceEmportee emportee =
                        manifeste.sequences().get(rang);
                selectionDao.attacherSequence(new SequenceSelectionnee(
                        selection.id(), resolues.get(rang).id(), emportee.position(), false));
                if (emportee.verdict() != VerdictFichier.NON_JUGE) {
                    selectionDao.marquerVerdict(
                            selection.id(), resolues.get(rang).id(), emportee.verdict());
                }
            }
        });
        return new BilanReprise(passage.id(), selection.id(), resolues.size(), ouvert.pseudoRelecteur());
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
