package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.EcrivainZip;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// Export **ZIP** du sous-ensemble d'observations affiché : le CSV (même formateur que l'export CSV
/// seul, [ExportObservationsCsv]) **et** les fichiers son que ces observations portent (#2792,
/// EPIC #2790). Le geste qui a fait naître le chantier : filtrer « les grands Rhinolophes de mes
/// nuits sur Aix » et envoyer le tout à un expert pour contre-écoute.
///
/// Structure d'archive : `observations.csv` à la racine, puis `sons/<dossier-session>/<fichier>.wav`.
/// Le sous-dossier par session rend toute collision de noms entre nuits impossible ; deux sessions
/// dont les dossiers seraient homonymes sont départagées par leur identifiant.
///
/// **Une séquence, une fois** : plusieurs observations peuvent pointer la même séquence (plusieurs
/// cris dans une tranche de 5 s), le son n'est emballé qu'une fois. **Un son introuvable ne bloque
/// pas** : il est compté et nommé au bilan (passage archivé, disque absent), même politique que
/// l'export de la bibliothèque.
public class ExportObservationsEtSons {

    /// Nom de l'entrée CSV, à la racine de l'archive.
    static final String NOM_CSV = "observations.csv";

    private final SequenceDao sequenceDao;
    private final SessionDao sessionDao;

    public ExportObservationsEtSons(SequenceDao sequenceDao, SessionDao sessionDao) {
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
    }

    /// Écrit l'archive `destination` : CSV des `lignes` (dans leur ordre) + sons dédupliqués.
    ///
    /// @param lignes le sous-ensemble affiché (déjà filtré par l'appelant)
    /// @param aEnjeu prédicat « espèce à enjeu » du CSV (#2353), reçoit le code du taxon retenu
    /// @param surProgression avancement déterminé, possiblement hors du fil JavaFX
    /// @param jeton annulation coopérative : l'archive partielle est supprimée
    /// @return le bilan chiffré (observations, sons copiés, sons introuvables, octets)
    /// @throws IOException sur échec d'écriture - l'archive partielle est supprimée
    public Bilan exporter(
            List<LigneObservationAudio> lignes,
            Path destination,
            Predicate<String> aEnjeu,
            Consumer<Progression> surProgression,
            JetonAnnulation jeton)
            throws IOException {
        Objects.requireNonNull(lignes, "lignes");
        Objects.requireNonNull(destination, "destination");
        String csv = ExportObservationsCsv.contenu(lignes, aEnjeu);
        List<EcrivainZip.EntreeFichier> sons = new ArrayList<>();
        List<String> introuvables = new ArrayList<>();
        Map<Long, String> dossiersParSession = new HashMap<>();
        // Séquences et sessions lues **par lot** (#4289) : la boucle en faisait deux requêtes par son
        // emballé - la séquence, puis sa session pour résoudre un chemin relatif. Un export porte
        // volontiers plusieurs milliers de cris.
        Set<Long> idsSequences = sequencesUniques(lignes);
        Map<Long, SequenceDEcoute> sequencesParId = sequenceDao.findParIds(idsSequences);
        Map<Long, SessionDEnregistrement> sessionsParId = new HashMap<>();
        for (SessionDEnregistrement session : sessionDao.findAll()) {
            sessionsParId.put(session.id(), session);
        }

        for (Long idSequence : idsSequences) {
            SequenceDEcoute sequence = sequencesParId.get(idSequence);
            if (sequence == null) {
                introuvables.add("séquence " + idSequence);
                continue;
            }
            Path source = resoudre(sequence, sessionsParId);
            if (source == null || !Files.isRegularFile(source)) {
                introuvables.add(sequence.nomFichier());
                continue;
            }
            String dossier = dossiersParSession.computeIfAbsent(
                    sequence.idSession(), id -> nomDossierUnique(id, dossiersParSession.values(), sessionsParId));
            sons.add(new EcrivainZip.EntreeFichier("sons/" + dossier + "/" + sequence.nomFichier(), source));
        }
        surProgression.accept(annonce(lignes.size(), sons));
        long octets = EcrivainZip.ecrire(
                destination, List.of(new EcrivainZip.EntreeTexte(NOM_CSV, csv)), sons, surProgression, jeton);
        return new Bilan(lignes.size(), sons.size(), List.copyOf(introuvables), octets);
    }

    /// L'annonce qui ouvre la modale : ce que l'archive va contenir, volume compris (tailles lues
    /// du disque, les fichiers viennent d'être vérifiés présents).
    private static Progression annonce(int observations, List<EcrivainZip.EntreeFichier> sons) {
        long octets = sons.stream().mapToLong(son -> taille(son.source())).sum();
        return new Progression(
                observations + " observation(s) · " + sons.size() + " son(s) · ~" + Formats.octetsLisibles(octets),
                0.0);
    }

    /// La taille du fichier, ou `0` s'il est parti entre la vérification et l'annonce : le volume
    /// annoncé est un ordre de grandeur, et l'écriture signalera la disparition mieux que l'annonce.
    private static long taille(Path source) {
        try {
            return Files.size(source);
        } catch (IOException disparu) {
            return 0L;
        }
    }

    /// Les séquences du sous-ensemble, **dédupliquées** dans l'ordre d'affichage (plusieurs
    /// observations peuvent partager une séquence).
    private static Set<Long> sequencesUniques(List<LigneObservationAudio> lignes) {
        Set<Long> ids = new LinkedHashSet<>();
        for (LigneObservationAudio ligne : lignes) {
            ids.add(ligne.idSequence());
        }
        return ids;
    }

    /// Chemin réel du fichier d'une séquence : absolu tel que stocké, ou résolu contre la racine de
    /// sa session pour les données héritées relatives (même repli que le dépôt).
    private Path resoudre(SequenceDEcoute sequence, Map<Long, SessionDEnregistrement> sessionsParId) {
        if (sequence.cheminFichier() == null) {
            return null;
        }
        Path chemin = Path.of(sequence.cheminFichier());
        return Optional.ofNullable(sessionsParId.get(sequence.idSession()))
                .map(session -> session.resoudre(chemin))
                .orElseGet(() -> chemin.isAbsolute() ? chemin : null);
    }

    /// Nom du sous-dossier d'archive d'une session : le **nom de son dossier** sur disque (le
    /// préfixe parlant `Car640380-2026-Pass1-Z1`), ou `session-<id>` s'il est illisible. Deux
    /// sessions aux dossiers homonymes sont départagées par l'identifiant - seulement dans ce cas,
    /// pour garder l'arborescence lisible : aucune collision d'entrées possible.
    private String nomDossierUnique(
            Long idSession, Collection<String> nomsDejaPris, Map<Long, SessionDEnregistrement> sessionsParId) {
        String nom = Optional.ofNullable(sessionsParId.get(idSession))
                .map(SessionDEnregistrement::cheminRacine)
                .map(racine -> Path.of(racine).getFileName())
                .map(Path::toString)
                .filter(valeur -> !valeur.isBlank())
                .orElse("session-" + idSession);
        return nomsDejaPris.contains(nom) ? nom + "-s" + idSession : nom;
    }

    /// Bilan d'un export : `observations` lignes du CSV, `sonsCopies` fichiers emballés,
    /// `sonsIntrouvables` nommés (comptés, jamais bloquants), `octets` de l'archive écrite.
    public record Bilan(int observations, int sonsCopies, List<String> sonsIntrouvables, long octets) {}
}
