package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/// **Amène une nuit rapatriée au niveau « contenu »** (#2555) : lui crée ses séquences et rapatrie ses
/// observations, **en place**, sans toucher à son identité.
///
/// La synchro « mes sites » rapatrie l'historique des nuits en **squelettes** (ADR 0016) : point, date,
/// identité de l'enregistreur, mais **aucune séquence**. Trois niveaux de complétude se sont ainsi
/// installés - structure, identité (#1814), contenu - et le troisième n'était atteignable que par la
/// modale « Reconstruire un passage manquant ». Une nuit fraîchement synchronisée était donc un
/// **cul-de-sac** : sa fiche grisait « Réactiver ce passage », faute de séquences à confronter au dossier
/// désigné, et le seul recours s'annonçait comme concernant des nuits « qui n'existent pas sur cette
/// machine » - ce qu'un squelette n'est pas.
///
/// Ce collaborateur compose les coutures que l'ADR 0016 annonçait « réutilisables et composables », sans
/// rien dupliquer : la source distante ([PlateformeReconstruction]), la création des séquences
/// ([CreationPassageArchive#hydraterSequences]) et l'import des observations ([ImportObservations]).
///
/// ## En place, pas remplacé
///
/// La reconstruction, elle, **supprime** le squelette pour le recréer entier
/// ([ServiceReconstructionPassages#reconstruire]). Ce n'est pas jouable ici, pour deux raisons qui
/// n'existaient pas quand l'ADR 0016 a écarté l'hydratation en place :
///
/// - un **écran est ouvert** sur cet `idPassage` (on hydrate au moment où l'utilisateur réactive) ;
/// - un squelette porte peut-être des **saisies manuelles** que la plateforme ignore - n° de série
///   (#1828), météo (#1688), heures de nuit tant qu'aucun fichier ne les atteste - qu'un delete + recreate
///   écraserait en silence.
///
/// L'objection de duplication de l'ADR 0016 est quant à elle tombée : `creerSequences` a depuis été
/// factorisée, il n'y a plus de chemin de création à réécrire.
///
/// ## Deux sources, selon qui appelle
///
/// Le repli sur la pagination `donnees` (une cinquantaine de pages par nuit) est acceptable sur **une**
/// nuit que l'utilisateur a désignée ; il ne l'est pas sur un **balayage de compte**, où il ferait
/// resurgir le coût qui avait justement fait écarter « tout rapatrier à la synchro ». D'où [Source].
///
/// **Hors du fil JavaFX** (réseau + écritures base).
public final class HydratationSquelette {

    private final PlateformeReconstruction plateforme;
    private final CreationPassageArchive structure;
    private final LienVigieChiroDao liens;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final EnregistrementOriginalDao originalDao;

    /// Port d'import des observations (#1264), **optionnel** : la fonctionnalité « Import Vigie-Chiro » est
    /// désactivable (#1057). Absent, il n'y a pas d'hydratation possible - et [Source] décide si cela se
    /// dit ou se tait.
    private final Optional<ImportObservations> importObservations;

    public HydratationSquelette(
            SourceDeDonnees source,
            ClientVigieChiro client,
            Workspace workspace,
            Horloge horloge,
            Optional<ImportObservations> importObservations) {
        Objects.requireNonNull(source, "source");
        this.plateforme = new PlateformeReconstruction(Objects.requireNonNull(client, "client"));
        this.structure = new CreationPassageArchive(source, workspace, horloge);
        this.liens = new LienVigieChiroDao(source);
        this.sessionDao = new SessionDao(source);
        this.sequenceDao = new SequenceDao(source);
        this.originalDao = new EnregistrementOriginalDao(source);
        this.importObservations = Objects.requireNonNull(importObservations, "importObservations");
    }

    /// D'où tirer les observations, et ce que vaut une absence.
    public enum Source {

        /// **Le CSV seul** (#1565, deux GET). Pour la synchro, qui balaie tout un compte : une nuit dont le
        /// CSV n'est pas exposé (analyse non terminée, pièce jointe non montée) est **laissée telle
        /// quelle**, sans erreur, et sera hydratée plus tard. Un empêchement n'est pas un échec ici : c'est
        /// une nuit qui n'est pas encore prête.
        CSV_SEULEMENT,

        /// Le CSV, **puis la pagination `donnees`** en repli. Pour la réactivation, qui traite **une** nuit
        /// que l'utilisateur vient de désigner : le coût du repli est justifié par le geste, et un
        /// empêchement doit se **dire**, avec sa raison.
        COMPLETE
    }

    /// Ce qu'une hydratation a produit.
    ///
    /// @param sequences nombre de lignes de séquences créées
    /// @param observations nombre d'observations rapatriées
    public record BilanHydratation(int sequences, int observations) {}

    /// Hydrate `idPassage` **si c'est un squelette**, et rend ce qui a été rapatrié.
    ///
    /// Rend [Optional#empty()] quand il n'y avait **rien à faire** ou que la source n'a rien donné sans que
    /// ce soit un échec : nuit déjà hydratée (idempotence), nuit sans session, et - en
    /// [Source#CSV_SEULEMENT] seulement - nuit non rattachée, CSV absent, dossier de session renommé, ou
    /// fonctionnalité d'import éteinte. En [Source#COMPLETE], ces mêmes empêchements lèvent une
    /// [RegleMetierException] qui **dit quoi faire**.
    ///
    /// @param progres notifié aux étapes réseau et base
    /// @param jeton consulté aux frontières d'étape
    /// @throws RegleMetierException en [Source#COMPLETE], si l'hydratation est empêchée (avec la raison)
    public Optional<BilanHydratation> hydraterSiSquelette(
            Long idPassage, Source source, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");

        Optional<SessionDEnregistrement> session = sessionDao.trouverParPassage(idPassage);
        if (session.isEmpty() || !estSquelette(session.orElseThrow())) {
            return Optional.empty(); // rien à hydrater : idempotent, et sans objet sur une nuit importée
        }
        SessionDEnregistrement squelette = session.orElseThrow();

        Optional<String> idParticipation = liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage));
        if (idParticipation.isEmpty()) {
            return renoncer(
                    source,
                    "Cette nuit n'a aucune séquence en local et n'est rattachée à aucune participation"
                            + " Vigie-Chiro : il n'existe aucune source d'où récupérer la liste de ses"
                            + " fichiers. Rattachez-la depuis « Sons & validation », puis recommencez.");
        }
        if (importObservations.isEmpty()) {
            return renoncer(
                    source,
                    "Récupérer les observations de cette nuit demande la fonctionnalité « Import"
                            + " Vigie-Chiro », qui est désactivée : réactivez-la (menu ☰ > Fonctionnalités)"
                            + " puis recommencez.");
        }
        Optional<Prefixe> prefixe = squelette.prefixe();
        if (prefixe.isEmpty()) {
            return renoncer(
                    source,
                    "Le dossier de cette nuit a été renommé à la main : son préfixe (Car…-2026-Pass1-A1)"
                            + " n'est plus lisible, et les séquences ne peuvent pas être recréées sous le bon"
                            + " nom. Corrigez le nom du dossier, puis recommencez.");
        }

        jeton.leverSiAnnule();
        Optional<ObservationsAReconstruire> observations =
                telecharger(idParticipation.orElseThrow(), source, progres, jeton);
        if (observations.isEmpty()) {
            return Optional.empty(); // CSV_SEULEMENT : pas encore de CSV, la nuit attend son tour
        }
        return Optional.of(ecrire(idPassage, squelette, prefixe.orElseThrow(), observations.orElseThrow(), progres));
    }

    /// Un passage est un **squelette** s'il porte une session **sans aucune séquence** (#1710).
    private boolean estSquelette(SessionDEnregistrement session) {
        return sequenceDao.findBySession(session.id()).isEmpty();
    }

    /// Un empêchement : **passé sous silence** en [Source#CSV_SEULEMENT] (best-effort d'un balayage, la
    /// nuit sera reprise plus tard), **dit avec sa raison** en [Source#COMPLETE] (l'utilisateur attend une
    /// réponse sur la nuit qu'il vient de désigner).
    private static Optional<BilanHydratation> renoncer(Source source, String raison) {
        if (source == Source.COMPLETE) {
            throw new RegleMetierException(raison);
        }
        return Optional.empty();
    }

    private Optional<ObservationsAReconstruire> telecharger(
            String idParticipation, Source source, Consumer<Progression> progres, JetonAnnulation jeton) {
        ImportObservations importateur = importObservations.orElseThrow();
        Consumer<Progression> suivi = libelleSeul(progres);
        return source == Source.CSV_SEULEMENT
                ? plateforme.observationsCsv(idParticipation, importateur, suivi, jeton)
                : Optional.of(plateforme.observations(idParticipation, importateur, suivi, jeton));
    }

    /// L'étape qui **écrit** : les lignes de séquences (une transaction), puis les observations qui s'y
    /// rattachent par nom.
    ///
    /// Toute interruption après la première écriture est **compensée** en supprimant les originaux que
    /// cette hydratation vient de poser ; la cascade `ON DELETE CASCADE` emporte les séquences avec eux, et
    /// la nuit **redevient exactement un squelette**. On ne peut pas défaire en supprimant le passage,
    /// comme le fait la reconstruction : ici le passage doit survivre. Mais l'état de repli est déjà un
    /// état légal du modèle, il n'y a donc rien à inventer.
    private BilanHydratation ecrire(
            Long idPassage,
            SessionDEnregistrement session,
            Prefixe prefixe,
            ObservationsAReconstruire observations,
            Consumer<Progression> progres) {
        Set<Long> avant = originauxDe(session);
        progres.accept(new Progression("Création des séquences…", 0.0));
        int sequences = structure.hydraterSequences(session.id(), prefixe, observations.nomsFichiers());
        try {
            progres.accept(new Progression("Import des observations…", 0.0));
            observations.importer(idPassage);
        } catch (RuntimeException interruption) {
            annulerHydratationPartielle(session, avant, interruption);
            throw interruption;
        }
        return new BilanHydratation(sequences, observations.nbObservations());
    }

    /// Retire les originaux **posés par cette hydratation** (et, en cascade, leurs séquences). On compare à
    /// l'inventaire d'**avant** plutôt que de tout supprimer : un squelette n'a en principe aucun original,
    /// mais compenser en s'appuyant sur cette supposition détruirait des données le jour où elle serait
    /// fausse. Best-effort : un échec de compensation est **attaché** à la cause d'origine plutôt que de la
    /// masquer (observabilité, #1523).
    private void annulerHydratationPartielle(SessionDEnregistrement session, Set<Long> avant, RuntimeException cause) {
        try {
            for (Long idOriginal : originauxDe(session)) {
                if (!avant.contains(idOriginal)) {
                    originalDao.delete(idOriginal);
                }
            }
        } catch (RuntimeException echecCompensation) {
            cause.addSuppressed(echecCompensation);
        }
    }

    private Set<Long> originauxDe(SessionDEnregistrement session) {
        List<EnregistrementOriginal> originaux = originalDao.findBySession(session.id());
        Set<Long> ids = new HashSet<>();
        originaux.forEach(original -> ids.add(original.id()));
        return ids;
    }

    /// Relaie le **libellé** d'une progression en gardant la fraction à zéro.
    ///
    /// L'hydratation est une **phase 0** : la barre qu'elle partage appartient à la phase suivante (le
    /// rebranchement des séquences), qui part de zéro. Or [fr.univ_amu.iut.commun.viewmodel.ProgressionOperation]
    /// garde la fraction **monotone** (#814) : laisser passer les fractions du téléchargement épinglerait la
    /// barre au plus haut atteint, et le rebranchement resterait **invisible** jusqu'à l'avoir dépassé.
    ///
    /// La barre reste donc vide pendant que le libellé avance - le même idiome qu'en fin de phase disque,
    /// où c'est l'inverse : la barre reste pleine et le libellé avance (#1780). Une barre propre à cette
    /// phase relève de #2558.
    private static Consumer<Progression> libelleSeul(Consumer<Progression> progres) {
        return point -> progres.accept(new Progression(point.libelle(), 0.0));
    }
}
