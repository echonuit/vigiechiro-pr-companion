package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.RattachementDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/// Service du **rattachement rétroactif** d'un passage (E2.S8, #1192) : changer l'année et/ou le n° de
/// passage, **même site/point**, en re-préfixant tous les fichiers de la nuit et les chemins en base.
/// Extrait de [ServicePassage] (qui garde lecture/détail, création, règles de protocole et workflow) :
/// cette opération orchestrée disque + base porte ses propres dépendances ([ReprefixeurSession],
/// [UniteDeTravail], [RattachementDao]) et un client unique (`RattachementViewModel`).
public class ServiceRattachement {

    private final PassageDao passageDao;
    private final SessionDao sessionDao;
    private final ReprefixeurSession reprefixeur;
    private final UniteDeTravail uniteDeTravail;
    private final RattachementDao rattachementDao;
    private final UniciteQuadruplet unicite;

    public ServiceRattachement(
            PassageDao passageDao,
            SessionDao sessionDao,
            ReprefixeurSession reprefixeur,
            UniteDeTravail uniteDeTravail,
            RattachementDao rattachementDao) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.reprefixeur = Objects.requireNonNull(reprefixeur, "reprefixeur");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
        this.rattachementDao = Objects.requireNonNull(rattachementDao, "rattachementDao");
        this.unicite = new UniciteQuadruplet(passageDao);
    }

    /// Modifie rétroactivement le rattachement d'un passage (E2.S8) : nouvelle année et/ou n° de
    /// passage, **même site/point**. Le préfixe `Car<carré>-<année>-Pass<n>-<point>` change : tous
    /// les fichiers de la nuit (dossier, originaux, séquences) sont re-renommés.
    ///
    /// Ordre (atomicité best-effort base/disque) : (1) contrôle **R5** du nouveau quadruplet ;
    /// (2) re-préfixage **disque** ([ReprefixeurSession], rollback interne) ; (3) transaction
    /// **base** ([UniteDeTravail]) du quadruplet et des chemins (session, originaux, séquences,
    /// journal, relevé — [RattachementDao]). Si la transaction échoue, le disque est **remis dans
    /// son état initial** (compensation) avant que l'erreur ne soit propagée.
    ///
    /// Le carré et le code point (inchangés) sont fournis par l'appelant via `nouveau` (le `model` ne
    /// dépend pas de `sites`) ; l'ancien préfixe est reconstruit depuis l'année/n° courants.
    ///
    /// Un passage **déposé** (ou en cours de dépôt) n'est plus renommable : son nom est l'identité de
    /// ses fichiers côté serveur (renommer après dépôt divergerait de la plateforme). Il faut d'abord
    /// [ServicePassage#annulerDepot].
    ///
    /// @param nouveau préfixe cible (même carré/point, nouvelle année et/ou n° de passage)
    /// @throws RegleMetierException si le passage est introuvable, déjà déposé, ou si le nouveau
    ///     quadruplet existe déjà
    public void modifierRattachement(Long idPassage, Prefixe nouveau) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(nouveau, "nouveau");
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
        // « Sur la plateforme » (#2581) : l'année et le n° d'une nuit récupérée sont l'identité que le
        // serveur lui donne, exactement comme pour une nuit que nous aurions déposée. L'écran la gate
        // déjà, mais un service qui laisserait passer rendrait ce gating décoratif.
        if (passage.statutWorkflow().estSurLaPlateforme()
                || passage.statutWorkflow() == StatutWorkflow.DEPOT_EN_COURS) {
            throw new RegleMetierException(
                    "Renommage refusé : un passage déposé (ou en cours de dépôt) ne peut plus être renommé."
                            + " Annulez d'abord le dépôt.");
        }
        Prefixe ancien = new Prefixe(nouveau.carre(), passage.annee(), passage.numeroPassage(), nouveau.codePoint());
        if (ancien.equals(nouveau)) {
            return; // ni l'année ni le n° de passage n'ont changé : rien à faire
        }
        unicite.exiger(passage.idPoint(), nouveau.annee(), nouveau.numeroPassage());

        Optional<SessionDEnregistrement> session = sessionDao.trouverParPassage(idPassage);
        Long idSession = session.map(SessionDEnregistrement::id).orElse(null);
        Path ancienneRacine = session.map(s -> Path.of(s.cheminRacine())).orElse(null);
        // Une session en base implique un dossier sur disque : on le re-préfixe ([ReprefixeurSession]
        // échoue, avant toute écriture base, si le dossier est absent ou la cible occupée). Seul un
        // passage sans session du tout (jamais importé) saute l'étape disque.
        Path nouvelleRacine = ancienneRacine == null ? null : reprefixeur.reprefixer(ancienneRacine, ancien, nouveau);

        try {
            uniteDeTravail.executer(cx -> {
                rattachementDao.majQuadruplet(cx, idPassage, nouveau.annee(), nouveau.numeroPassage());
                if (idSession != null) {
                    rattachementDao.reprefixerChemins(
                            cx,
                            idPassage,
                            idSession,
                            ancienneRacine,
                            nouvelleRacine,
                            ancien.prefixeFichier(),
                            nouveau.prefixeFichier());
                }
            });
        } catch (RuntimeException echec) {
            if (nouvelleRacine != null) {
                compenser(nouvelleRacine, nouveau, ancien, echec);
            }
            throw echec;
        }
    }

    /// Remet le dossier de session dans son état initial après un échec de la transaction base ; une
    /// erreur de compensation est rattachée à l'erreur d'origine plutôt que de la masquer.
    private void compenser(Path nouvelleRacine, Prefixe nouveau, Prefixe ancien, RuntimeException origine) {
        try {
            reprefixeur.reprefixer(nouvelleRacine, nouveau, ancien);
        } catch (RuntimeException echecCompensation) {
            origine.addSuppressed(echecCompensation);
        }
    }
}
