package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.PresenceFichiers;
import fr.univ_amu.iut.commun.model.PresenceFichiers.Presence;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// **Bilan de récupérabilité** : ce que deviendrait chaque nuit si l'on repartait d'une base neuve
/// (#1151). C'est le garde-fou du reset : il n'interdit rien, il établit **avant toute écriture** un état
/// par nuit, et ne fait demander confirmation que s'il existe au moins une nuit « perdu ».
///
/// **Ce qui revient toujours** : les métadonnées et les observations, que le serveur rend (#1050). **Ce
/// qui ne revient pas forcément** : l'audio. D'où ce bilan, et sa cascade.
///
/// 1. **Disque** : les séquences sont là, se réimportent, et la nuit redevient complète. Cas normal, et
///    le seul rapide.
/// 2. **Serveur** : le disque ne les a plus, mais la nuit a été déposée **en WAV** *et* rattachée à une
///    participation, seul cas où le serveur les a gardées (`POST /fichiers` pose un `s3_id` définitif).
///    Rare, le mode par défaut étant le **ZIP** depuis #984.
/// 3. **Perdu**, ni l'un ni l'autre : un dépôt ZIP ne laisse aucun audio côté serveur, les archives
///    étant détruites après extraction.
///
/// « Perdu » n'est pas une impasse (#1297) : la nuit devient un **passage archivé**, consultable, non
/// écoutable, réactivable si l'utilisateur retrouve ses fichiers. Le mode de dépôt est **lu dans le
/// plan** (`depot_unite.type`), jamais présumé. Lecture seule, sans réseau.
public class ServiceRecuperabilite {

    private final PassageDao passageDao;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final DepotUniteDao depotDao;
    private final PointDao pointDao;
    private final SiteDao siteDao;
    private final LienVigieChiroDao liens;
    private final PresenceFichiers presenceFichiers;

    public ServiceRecuperabilite(SourceDeDonnees source, Workspace workspace) {
        Objects.requireNonNull(source, "source");
        this.passageDao = new PassageDao(source);
        this.sessionDao = new SessionDao(source);
        this.sequenceDao = new SequenceDao(source);
        this.depotDao = new DepotUniteDao(source);
        this.pointDao = new PointDao(source);
        this.siteDao = new SiteDao(source);
        this.liens = new LienVigieChiroDao(source);
        this.presenceFichiers = new PresenceFichiers(Objects.requireNonNull(workspace, "workspace"));
    }

    /// Bilan de **toutes** les nuits, dans l'ordre des passages : à lire avant tout reset.
    public BilanRecuperabilite bilan() {
        List<RecuperabiliteNuit> nuits = new ArrayList<>();
        for (Passage passage : passageDao.findAll()) {
            nuits.add(evaluer(passage));
        }
        return new BilanRecuperabilite(List.copyOf(nuits));
    }

    private RecuperabiliteNuit evaluer(Passage passage) {
        String libelle = libelle(passage);
        Optional<SessionDEnregistrement> session = sessionDao.trouverParPassage(passage.id());
        if (session.isEmpty()) {
            return new RecuperabiliteNuit(
                    passage.id(), libelle, SourceAudio.PERDU, 0, 0, "aucune session : nuit jamais importée ici");
        }
        List<SequenceDEcoute> sequences =
                sequenceDao.findBySession(session.get().id());
        int presentes = compterPresentes(sequences);
        if (presentes > 0 && presentes == sequences.size()) {
            return new RecuperabiliteNuit(
                    passage.id(),
                    libelle,
                    SourceAudio.DISQUE,
                    presentes,
                    sequences.size(),
                    "les " + presentes + " séquences sont sur le disque");
        }
        // Séquences partiellement présentes : le disque en rendra une partie, mais pas tout. On ne peut
        // promettre « disque » que si le compte y est ; sinon on retombe sur la cascade, car c'est du
        // manquant qu'il faut parler.
        return sansDisqueComplet(passage, libelle, presentes, sequences.size());
    }

    /// Quand le disque ne suffit pas : le serveur peut-il prendre le relais ? Uniquement si la nuit a été
    /// **rattachée** à une participation **et** déposée en **WAV**.
    private RecuperabiliteNuit sansDisqueComplet(Passage passage, String libelle, int presentes, int total) {
        boolean rattachee = liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(passage.id()))
                .isPresent();
        List<DepotUnite> plan = depotDao.parPassage(passage.id());
        boolean deposeEnWav = !plan.isEmpty() && plan.stream().allMatch(unite -> unite.type() == TypeDepotUnite.WAV);

        if (rattachee && deposeEnWav) {
            return new RecuperabiliteNuit(
                    passage.id(),
                    libelle,
                    SourceAudio.SERVEUR,
                    presentes,
                    total,
                    "déposée en WAV et rattachée : le serveur a gardé les fichiers");
        }
        return new RecuperabiliteNuit(
                passage.id(), libelle, SourceAudio.PERDU, presentes, total, motifPerte(rattachee, plan));
    }

    /// Pourquoi l'audio est perdu : en nommant la **vraie** cause, celle qui aurait pu être évitée.
    private static String motifPerte(boolean rattachee, List<DepotUnite> plan) {
        if (plan.isEmpty()) {
            return rattachee
                    ? "rattachée mais sans plan de dépôt (dépôt antérieur à #984 : fichiers orphelins sur S3)"
                    : "jamais déposée : le serveur n'en a aucune copie";
        }
        if (!rattachee) {
            return "déposée sans rattachement : ses fichiers sont orphelins côté serveur";
        }
        return "déposée en ZIP : le serveur ne garde aucun audio (archives détruites après extraction)";
    }

    /// Séquences réellement sur le disque, en **un balayage groupé** ([PresenceFichiers] : un accès disque
    /// par dossier, pas par fichier) : le même que celui de l'audit.
    private int compterPresentes(List<SequenceDEcoute> sequences) {
        if (sequences.isEmpty()) {
            return 0;
        }
        Map<String, Presence> presences = presenceFichiers.evaluer(
                sequences.stream().map(SequenceDEcoute::cheminFichier).toList());
        return (int) sequences.stream()
                .filter(sequence -> presences.get(sequence.cheminFichier()) == Presence.PRESENTE)
                .count();
    }

    /// De quelle nuit on parle : « Carré 130711 · point Z41 · passage 1 · 2026-07-03 ». L'utilisateur qui
    /// s'apprête à tout perdre ne raisonne pas en identifiants techniques.
    private String libelle(Passage passage) {
        String carre = pointDao.findById(passage.idPoint())
                .flatMap(point ->
                        siteDao.findById(point.idSite()).map(site -> site.numeroCarre() + " · point " + point.code()))
                .orElse("site inconnu");
        return "Carré " + carre + " · passage " + passage.numeroPassage() + " · " + passage.dateEnregistrement();
    }
}
