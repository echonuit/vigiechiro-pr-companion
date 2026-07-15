package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.PointParLocalite;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// **Reconstruit un passage jamais importé sur cette machine** (#1305, EPIC #1297), à partir de ce que
/// la plateforme VigieChiro en sait.
///
/// Les passages sans audio local forment **deux populations**, et les issues A à G n'en traitaient qu'une :
///
/// 1. **archivé par purge** : on l'a eu, on l'a supprimé (#1300, #1302) — le passage existe en base ;
/// 2. **jamais local** : la participation existe **sur la plateforme** (déposée depuis un autre poste,
///    avant l'application, ou après une réinstallation) mais **rien** n'est en base ici.
///
/// C'est la seconde qui réalise vraiment la promesse du chantier : « voir l'historique de ses passages
/// même sans avoir conservé les données d'origine ». [#reconstruire] recrée un passage local **en état
/// archivé** : ses lignes de séquences existent (sans fichier), ses observations sont rapatriées, il se
/// consulte comme tout passage archivé (#1301) et se réactive si l'utilisateur retrouve ses fichiers
/// (#1302).
///
/// **Pourquoi recréer les séquences.** L'import d'observations rattache chaque ligne à la séquence de
/// **même nom** et **ignore** celles qu'il ne trouve pas : sans lignes de séquences, un passage
/// reconstruit n'aurait aucune observation. On les crée donc à partir des `titre` des données
/// distantes — ce sont exactement les noms de fichiers attendus.
///
/// **Ce qui manque est dit, pas deviné** ([RapportReconstruction#lacunesConnues]) : ni journal du
/// capteur, ni relevé climatique, ni séquences non identifiées (le serveur ne les connaît pas), ni
/// empreintes (elles se posent à l'import local, qui n'a jamais eu lieu). Une réactivation s'appuiera
/// donc sur la **cascade** (#1309) : noms, durées, et surtout les **cris eux-mêmes**.
///
/// Les DAO sont construits depuis la [SourceDeDonnees] (fins adaptateurs sans état), comme
/// `ServiceAuditCoherence` : le constructeur reste court et le service testable sur une base jetable.
public class ServiceReconstructionPassages {

    /// Carré à six chiffres, extrait du **titre du site** VigieChiro (ex. `Vigiechiro - Point Fixe-130711`).
    private static final Pattern CARRE = Pattern.compile("(\\d{6})");

    /// Numéro de série de repli quand la participation ne dit pas quel enregistreur a produit la nuit :
    /// le schéma exige un enregistreur, et inventer un vrai numéro serait un mensonge.
    private static final String ENREGISTREUR_INCONNU = "INCONNU";

    private static final String CLE_NUMERO_SERIE = "detecteur_enregistreur_numserie";

    private final PassageDao passageDao;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final EnregistrementOriginalDao originalDao;
    private final EnregistreurDao enregistreurDao;
    private final LienVigieChiroDao liens;
    private final ClientVigieChiro client;
    private final PointParLocalite pointParLocalite;

    /// Port de l'import des observations (#1264), **optionnel** comme partout ailleurs : la feature
    /// « Import VigieChiro » est désactivable (#1057), et un module ne peut pas exiger en dur ce qu'une
    /// autre feature fournit : l'injecteur ne se construirait plus. Absent, [#reconstruire] le **dit**.
    private final Optional<ImportObservations> importObservations;

    private final Workspace workspace;
    private final Horloge horloge;

    /// Pour grouper la création des séquences (des milliers) en **une seule transaction** au lieu d'un
    /// commit par ligne (#1522). Construit depuis la même [SourceDeDonnees] que les DAO.
    private final UniteDeTravail uniteDeTravail;

    public ServiceReconstructionPassages(
            SourceDeDonnees source,
            ClientVigieChiro client,
            PointParLocalite pointParLocalite,
            Optional<ImportObservations> importObservations,
            Workspace workspace,
            Horloge horloge) {
        Objects.requireNonNull(source, "source");
        this.passageDao = new PassageDao(source);
        this.sessionDao = new SessionDao(source);
        this.sequenceDao = new SequenceDao(source);
        this.originalDao = new EnregistrementOriginalDao(source);
        this.enregistreurDao = new EnregistreurDao(source);
        this.liens = new LienVigieChiroDao(source);
        this.uniteDeTravail = new UniteDeTravail(source);
        this.client = Objects.requireNonNull(client, "client");
        this.pointParLocalite = Objects.requireNonNull(pointParLocalite, "pointParLocalite");
        this.importObservations = Objects.requireNonNull(importObservations, "importObservations");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Participations de la plateforme **sans équivalent local** : celles dont l'`_id` n'est rattaché à
    /// aucun passage d'ici. Chacune dit si son point est déjà connu localement — sinon, il faudra créer
    /// le site et le point avant de pouvoir la reconstruire.
    ///
    /// @throws RegleMetierException hors connexion, ou si la plateforme est injoignable (avec la cause)
    public List<ParticipationOrpheline> orphelines() {
        Set<String> rattachees =
                Set.copyOf(liens.tous(LienVigieChiro.ENTITE_PASSAGE).values());
        return participations().stream()
                .filter(participation -> !rattachees.contains(participation.id()))
                .map(this::enOrpheline)
                .toList();
    }

    /// Reconstruit localement la participation `idParticipation` en **passage archivé** : passage, session
    /// (marquée archivée), lignes de séquences sans fichier, puis rapatriement des observations.
    ///
    /// Variante **non suivie** : sans progression ni annulation (jeton neutre). Sert la CLI et les appels
    /// qui n'offrent pas de barre. L'IHM passe par la variante suivie depuis une orpheline.
    ///
    /// @throws RegleMetierException si la participation est déjà rattachée à un passage local, si son point
    ///     n'existe pas ici, si sa nuit est indatable, ou si ses observations ne sont pas récupérables
    ///     (analyse non terminée : le message dit laquelle de ces raisons)
    public RapportReconstruction reconstruire(String idParticipation) {
        return reconstruire(idParticipation, progression -> {}, JetonAnnulation.neutre());
    }

    /// Variante **suivie et annulable** par identifiant (chemin CLI, qui ne connaît que l'`_id`) : le
    /// carré et la localité, absents du détail par id, sont retrouvés via le résumé du compte - une
    /// lecture de la **liste entière** des participations. L'IHM, qui tient déjà la nuit choisie, évite ce
    /// coût par la variante depuis une orpheline.
    public RapportReconstruction reconstruire(
            String idParticipation, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(idParticipation, "idParticipation");
        ParticipationVigieChiro resume = resume(idParticipation);
        return reconstruire(idParticipation, carre(resume), resume.point(), progres, jeton);
    }

    /// Variante **suivie et annulable** depuis une [ParticipationOrpheline] déjà en main (chemin IHM) :
    /// carré et localité en sont tirés directement, **sans re-télécharger toute la liste** des
    /// participations pour retrouver une nuit qu'on a déjà sélectionnée (#1522).
    public RapportReconstruction reconstruire(
            ParticipationOrpheline orpheline, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(orpheline, "orpheline");
        return reconstruire(
                orpheline.idParticipation(), orpheline.numeroCarre(), orpheline.codePoint(), progres, jeton);
    }

    /// Cœur de la reconstruction, une fois carré et localité connus (quelle que soit leur origine). Émet
    /// des **points de progression** aux étapes lourdes et consulte le **jeton d'annulation** entre elles
    /// (#1252). Dès la première écriture, toute interruption (annulation ou échec) est **compensée** :
    /// aucun passage partiel ne subsiste.
    private RapportReconstruction reconstruire(
            String idParticipation,
            String carre,
            String localite,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");
        refuserSiDejaRattachee(idParticipation);

        // Vérifié AVANT toute écriture : un passage reconstruit sans ses observations ne serait qu'une
        // coquille, et mieux vaut ne rien créer que créer à moitié.
        ImportObservations importateur = importObservations.orElseThrow(() -> new RegleMetierException(
                "La reconstruction rapatrie les observations depuis VigieChiro, et la fonctionnalité"
                        + " « Import VigieChiro » est désactivée : réactivez-la (menu ☰ > Fonctionnalités)"
                        + " puis recommencez."));

        progres.accept(new Progression("Lecture de la participation…", 0.05));
        jeton.leverSiAnnule();
        ParticipationDetail detail = detail(idParticipation);
        Long idPoint = pointParLocalite
                .pour(carre, localite)
                .orElseThrow(() -> new RegleMetierException(
                        "Le point d'écoute de cette participation (carré " + carre + ", localité " + localite
                                + ") n'existe pas localement. Créez d'abord le site et le point, puis"
                                + " recommencez."));
        LocalDateTime debut = nuit(detail.dateDebut())
                .orElseThrow(() -> new RegleMetierException(
                        "La participation ne porte pas de date de début exploitable : impossible de dater la"
                                + " nuit."));
        LocalDateTime fin = nuit(detail.dateFin()).orElse(debut);

        // Les données portent les NOMS des fichiers analysés : ce sont eux qui permettront de rattacher les
        // observations aux séquences recréées, et plus tard de reconnaître les fichiers réimportés (#1302).
        // C'est l'étape la plus longue (des dizaines de pages) : on suit sa progression PAGE PAR PAGE et on
        // y honore l'annulation, sans quoi la barre restait figée et « Annuler » muet plusieurs minutes.
        jeton.leverSiAnnule();
        List<DonneeVigieChiro> donnees = donnees(idParticipation, page -> {
            jeton.leverSiAnnule();
            progres.accept(new Progression(
                    "Téléchargement des observations (page " + page + ")…", avancementTelechargement(page)));
        });
        if (donnees.isEmpty()) {
            throw new RegleMetierException("VigieChiro ne renvoie aucune donnée pour cette participation :"
                    + " l'analyse n'est probablement pas terminée. Réessayez plus tard.");
        }

        // Le préfixe R6 RÉEL de la nuit (carré, année, n° de passage, code du point) : c'est celui que
        // l'audit recalcule depuis le passage (`ServiceAuditCoherence#prefixeAttendu`), et il doit être le
        // même, sans quoi le passage reconstruit serait signalé PREFIXE_NON_CONFORME à vie (#1050).
        int numeroPassage = premierNumeroLibre(idPoint, debut.getYear());
        Prefixe prefixe = new Prefixe(carre, debut.getYear(), numeroPassage, localite);

        // À partir d'ici on ÉCRIT. La vraie transaction unique est hors de portée (l'import ouvre la
        // sienne sur une base SQLite mono-écrivain) ; à la place, toute interruption défait ce qui a été
        // créé - le schéma en ON DELETE CASCADE rend cette compensation sûre (#1522).
        Long idPassage = null;
        try {
            jeton.leverSiAnnule();
            progres.accept(new Progression("Création du passage…", 0.72));
            idPassage = creerPassage(idPoint, numeroPassage, debut, fin, enregistreur(detail));
            Long idSession = creerSessionArchivee(idPassage, prefixe);
            progres.accept(new Progression("Création des séquences…", 0.78));
            int sequences = creerSequences(idSession, prefixe, donnees);
            liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), idParticipation));

            // L'import des observations est le mécanisme de l'EPIC #1259, consommé par son port socle : il
            // rattache chaque ligne à la séquence de même nom - celles qu'on vient de recréer. On lui passe
            // les donnees DÉJÀ téléchargées : les re-parcourir page par page doublait le temps (#1522).
            progres.accept(new Progression("Import des observations…", 0.85));
            jeton.leverSiAnnule();
            importateur.importer(idPassage, donnees, false);
            int observations = donnees.stream()
                    .mapToInt(donnee -> donnee.observations().size())
                    .sum();
            progres.accept(new Progression("Terminé.", 1.0));
            return new RapportReconstruction(
                    idPassage, sequences, observations, RapportReconstruction.lacunesConnues());
        } catch (RuntimeException interruption) {
            if (idPassage != null) {
                annulerReconstructionPartielle(idPassage, interruption);
            }
            throw interruption;
        }
    }

    /// Défait une reconstruction interrompue : retire le lien VigieChiro (posé ou non) puis supprime le
    /// passage, dont la suppression **cascade** sur session, séquences et observations (`ON DELETE
    /// CASCADE`). Best-effort : un échec de compensation est **attaché** à la cause d'origine plutôt que de
    /// la masquer, pour qu'aucune trace ne se perde (observabilité, #1523).
    private void annulerReconstructionPartielle(Long idPassage, RuntimeException cause) {
        try {
            liens.supprimer(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage));
            passageDao.delete(idPassage);
        } catch (RuntimeException echecCompensation) {
            cause.addSuppressed(echecCompensation);
        }
    }

    // --- Création locale ---------------------------------------------------------------------------

    /// Crée le passage, **déposé** (il l'est : la participation existe sur la plateforme) et sans verdict
    /// local (aucune vérification n'a eu lieu ici). Le numéro de passage est le **premier libre** pour ce
    /// point et cette année (calculé par l'appelant, qui en a aussi besoin pour le préfixe) : la
    /// plateforme ne le porte pas, et deviner « 1 ou 2 » selon la date serait une supposition.
    private Long creerPassage(
            Long idPoint, int numeroPassage, LocalDateTime debut, LocalDateTime fin, String idEnregistreur) {
        int annee = debut.getYear();
        return passageDao
                .insert(new Passage(
                        null,
                        numeroPassage,
                        annee,
                        debut.toLocalDate().toString(),
                        debut.toLocalTime().toString(),
                        fin.toLocalTime().toString(),
                        null,
                        StatutWorkflow.DEPOSE,
                        null,
                        null,
                        null,
                        debut.toLocalDate().toString(),
                        idPoint,
                        idEnregistreur))
                .id();
    }

    /// Session **archivée d'emblée** : le passage naît sans audio (rien n'a jamais été importé ici). Le
    /// marqueur explicite (#1300) le dit, si bien que l'audit informe au lieu de crier (#1303).
    private Long creerSessionArchivee(Long idPassage, Prefixe prefixe) {
        Path racine = workspace.dossierSession(prefixe.nomDossierSession());
        Long idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racine.toString(), 0L, 0L, idPassage))
                .id();
        sessionDao.marquerArchivee(idSession, horloge.maintenant());
        return idSession;
    }

    /// Recrée une **ligne** de séquence par donnée distante (nom de fichier), sans fichier sur disque : le
    /// passage est archivé. Un enregistrement original **porteur** est créé pour satisfaire la clé
    /// étrangère ; il n'a pas plus de fichier que les séquences, mais il porte le **vrai** préfixe R6 : un
    /// nom fabriqué ferait échouer le contrôle de préfixe de l'audit (#1050).
    private int creerSequences(Long idSession, Prefixe prefixe, List<DonneeVigieChiro> donnees) {
        Path transformes = workspace.dossierTransformes(prefixe.nomDossierSession());
        // Des milliers de séquences en UNE transaction (#1522) : un commit par ligne, c'était un fsync par
        // ligne - plusieurs minutes pour une nuit. L'annulation est consultée AVANT ce bloc (désormais
        // rapide), pas dedans : l'unité de travail enveloppe les exceptions, ce qui masquerait l'annulation.
        int[] compte = {0};
        uniteDeTravail.executer(cx -> {
            Long idOriginal = originalDao
                    .insert(
                            cx,
                            new EnregistrementOriginal(
                                    null,
                                    prefixe.prefixeFichier() + "reconstruit.wav",
                                    "",
                                    null,
                                    null,
                                    null,
                                    idSession))
                    .id();
            int index = 0;
            for (DonneeVigieChiro donnee : donnees) {
                String nom = nomFichier(donnee.titre());
                sequenceDao.insert(
                        cx,
                        new SequenceDEcoute(
                                null,
                                nom,
                                idOriginal,
                                index,
                                null,
                                null,
                                transformes.resolve(nom).toString(),
                                false,
                                idSession,
                                Prefixe.horodatageDe(nom).orElse(null)));
                index++;
            }
            compte[0] = index;
        });
        return compte[0];
    }

    /// Avancement affiché pendant le téléchargement paginé : le nombre total de pages n'est pas connu
    /// d'avance (Eve ne le donne pas), donc la fraction **tend** vers 0,70 sans jamais l'atteindre - la
    /// barre avance à chaque page, de moins en moins vite. La plage [0,70 ; 1,0] reste à la création et à
    /// l'import.
    private static double avancementTelechargement(int page) {
        return 0.10 + 0.60 * (1.0 - 1.0 / (1.0 + 0.1 * page));
    }

    /// Nom de fichier de la séquence : le `titre` distant est **sans extension** (`..._000`), les
    /// séquences locales portent `.wav`.
    private static String nomFichier(String titre) {
        return titre.toLowerCase(java.util.Locale.ROOT).endsWith(".wav") ? titre : titre + ".wav";
    }

    /// Premier numéro de passage libre pour ce point et cette année (contrainte d'unicité du quadruplet).
    private int premierNumeroLibre(Long idPoint, int annee) {
        Set<Integer> pris = passageDao.findAll().stream()
                .filter(passage -> idPoint.equals(passage.idPoint()) && passage.annee() == annee)
                .map(Passage::numeroPassage)
                .collect(Collectors.toSet());
        int numero = 1;
        while (pris.contains(numero)) {
            numero++;
        }
        return numero;
    }

    /// Enregistreur de la nuit : celui que porte la configuration de la participation si elle le dit,
    /// sinon un enregistreur **« inconnu »** (le schéma en exige un ; inventer un numéro de série serait
    /// pire que de dire qu'on ne le sait pas). Créé s'il n'existe pas encore.
    private String enregistreur(ParticipationDetail detail) {
        String serie = detail.configuration().getOrDefault(CLE_NUMERO_SERIE, ENREGISTREUR_INCONNU);
        if (serie.isBlank()) {
            serie = ENREGISTREUR_INCONNU;
        }
        if (enregistreurDao.findById(serie).isEmpty()) {
            enregistreurDao.insert(new Enregistreur(serie, null, null));
        }
        return serie;
    }

    // --- Lectures distantes et helpers -------------------------------------------------------------

    private void refuserSiDejaRattachee(String idParticipation) {
        boolean rattachee = liens.tous(LienVigieChiro.ENTITE_PASSAGE).containsValue(idParticipation);
        if (rattachee) {
            throw new RegleMetierException(
                    "Cette participation est déjà rattachée à un passage local : il n'y a rien à reconstruire.");
        }
    }

    private List<ParticipationVigieChiro> participations() {
        return exiger(client.mesParticipations(), "la liste de vos participations");
    }

    private ParticipationVigieChiro resume(String idParticipation) {
        return participations().stream()
                .filter(participation -> idParticipation.equals(participation.id()))
                .findFirst()
                .orElseThrow(() -> new RegleMetierException(
                        "Participation introuvable parmi celles de votre compte : " + idParticipation + "."));
    }

    private ParticipationDetail detail(String idParticipation) {
        return exiger(client.participation(idParticipation), "le détail de cette participation");
    }

    private List<DonneeVigieChiro> donnees(String idParticipation, IntConsumer surPage) {
        return exiger(client.donnees(idParticipation, surPage), "les observations de cette participation");
    }

    /// Traduction **unique** d'une issue d'appel (#1284) en valeur ou en refus **motivé** : une seule
    /// formulation par cause (non connecté, injoignable, refusé), et le geste qui va avec — au lieu de
    /// répéter le même `switch` à chaque lecture distante.
    ///
    /// @param quoi ce qu'on lisait, pour que le message dise **ce qui** a échoué
    private static <T> T exiger(ReponseApi<T> reponse, String quoi) {
        return switch (reponse) {
            case ReponseApi.Succes<T>(T valeur) -> valeur;
            case ReponseApi.NonConnecte<T> ignore ->
                throw new RegleMetierException("Non connecté à VigieChiro : collez un jeton (menu ☰ >"
                        + " Se connecter à VigieChiro) avant de reconstruire un passage.");
            case ReponseApi.Injoignable<T>(String cause) ->
                throw new RegleMetierException(
                        "VigieChiro est injoignable (" + cause + ") : " + quoi + " n'a pas pu être lu.");
            case ReponseApi.Refuse<T>(int statut, String corps) ->
                throw new RegleMetierException("VigieChiro a refusé de rendre " + quoi + " (HTTP " + statut + " : "
                        + corps + "). C'est probablement un défaut de l'application : signalez-le.");
        };
    }

    private ParticipationOrpheline enOrpheline(ParticipationVigieChiro participation) {
        String carre = carre(participation);
        return new ParticipationOrpheline(
                participation.id(),
                carre,
                participation.point(),
                participation.dateDebut(),
                pointLocal(participation).isPresent());
    }

    private Optional<Long> pointLocal(ParticipationVigieChiro participation) {
        return pointParLocalite.pour(carre(participation), participation.point());
    }

    /// Numéro de carré, extrait du **titre du site** (ex. `Vigiechiro - Point Fixe-130711` → `130711`) :
    /// l'API ne l'expose pas autrement dans la liste des participations.
    private static String carre(ParticipationVigieChiro participation) {
        if (participation.siteTitre() == null) {
            return null;
        }
        Matcher trouve = CARRE.matcher(participation.siteTitre());
        return trouve.find() ? trouve.group(1) : null;
    }

    /// Date/heure d'une borne de nuit, tolérante au format (l'API rend de l'ISO 8601 avec décalage) ; vide
    /// si la borne est absente ou illisible.
    private static Optional<LocalDateTime> nuit(String horodatage) {
        if (horodatage == null || horodatage.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(horodatage).toLocalDateTime());
        } catch (DateTimeParseException premiere) {
            try {
                return Optional.of(LocalDateTime.parse(horodatage));
            } catch (DateTimeParseException seconde) {
                return Optional.empty();
            }
        }
    }
}
