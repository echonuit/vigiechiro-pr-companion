package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// **Noyau de structure d'un passage archivé** : à partir de ce que la plateforme VigieChiro sait d'une
/// nuit (un [ParticipationDetail] + les noms de fichiers analysés), crée localement le **squelette** d'un
/// passage **sans audio** : le passage (déposé), sa session marquée **archivée** (#1300), et une **ligne**
/// de séquence par fichier distant (sans fichier sur disque), plus le rapatriement de l'**enregistreur**,
/// de la **météo** et du **micro** (#1689). Aucune observation, aucun audio : ces coutures-là vivent
/// ailleurs (le moteur d'import #1656 et la réactivation #1302).
///
/// Extrait de [ServiceReconstructionPassages] (EPIC #1662) pour être **réutilisable** : la reconstruction
/// le compose avec l'import des observations, et la synchro « mes sites » le composera pour rapatrier la
/// structure de tous les passages d'un point. Les DAO sont construits depuis la [SourceDeDonnees] (fins
/// adaptateurs sans état), comme les autres services de la feature.
public final class CreationPassageArchive {

    /// Numéro de série de repli quand la participation ne dit pas quel enregistreur a produit la nuit :
    /// le schéma exige un enregistreur, et inventer un vrai numéro serait un mensonge.
    private static final String ENREGISTREUR_INCONNU = "INCONNU";

    private final PassageDao passageDao;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final EnregistrementOriginalDao originalDao;
    private final EnregistreurDao enregistreurDao;
    private final MaterielMicroDao materielDao;
    private final Workspace workspace;
    private final Horloge horloge;

    /// Pour grouper la création des séquences (des milliers) en **une seule transaction** au lieu d'un
    /// commit par ligne (#1522). Construit depuis la même [SourceDeDonnees] que les DAO.
    private final UniteDeTravail uniteDeTravail;

    public CreationPassageArchive(SourceDeDonnees source, Workspace workspace, Horloge horloge) {
        Objects.requireNonNull(source, "source");
        this.passageDao = new PassageDao(source);
        this.sessionDao = new SessionDao(source);
        this.sequenceDao = new SequenceDao(source);
        this.originalDao = new EnregistrementOriginalDao(source);
        this.enregistreurDao = new EnregistreurDao(source);
        this.materielDao = new MaterielMicroDao(source);
        this.uniteDeTravail = new UniteDeTravail(source);
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Résultat de la création : l'identifiant du passage créé et le **nombre de séquences** recréées.
    public record PassageArchive(Long idPassage, int nbSequences) {}

    /// Premier numéro de passage **libre** pour ce point et cette année. La plateforme ne le porte pas, et
    /// deviner « 1 ou 2 » selon la date serait une supposition : on prend le premier trou. L'appelant en a
    /// besoin **avant** la création (le préfixe R6 le contient), d'où une méthode à part.
    public int premierNumeroLibre(Long idPoint, int annee) {
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

    /// Crée le squelette local du passage archivé (passage + matériel + session archivée + séquences) et
    /// **émet les points de progression** de cette phase (« Création du passage… » puis « Création des
    /// séquences… »), aux mêmes fractions qu'auparavant. N'importe **aucune** observation et ne pose
    /// **aucun** ancrage : l'appelant s'en charge (le moteur d'import #1656 pour les observations).
    ///
    /// @param idPoint point d'écoute local (déjà résolu par l'appelant)
    /// @param numeroPassage premier numéro libre pour ce point et cette année (calculé par l'appelant, qui
    ///     en a aussi besoin pour le préfixe)
    /// @param debut début de nuit
    /// @param fin fin de nuit
    /// @param prefixe préfixe R6 réel (carré, année, n°, code point) - celui que recalcule l'audit (#1050)
    /// @param detail détail de la participation (enregistreur, météo, micro)
    /// @param nomsFichiers noms des fichiers analysés distants (une séquence par nom)
    /// @param progres notifié aux deux étapes lourdes de la structure
    /// @return l'identifiant du passage créé et le nombre de séquences recréées
    public PassageArchive creer(
            Long idPoint,
            int numeroPassage,
            LocalDateTime debut,
            LocalDateTime fin,
            Prefixe prefixe,
            ParticipationDetail detail,
            List<String> nomsFichiers,
            Consumer<Progression> progres) {
        progres.accept(new Progression("Création du passage…", 0.90));
        Long idPassage = creerPassage(idPoint, numeroPassage, debut, fin, enregistreur(detail), meteoDepuis(detail));
        rapatrierMateriel(idPassage, detail);
        Long idSession = creerSessionArchivee(idPassage, prefixe);
        progres.accept(new Progression("Création des séquences…", 0.93));
        int sequences = creerSequences(idSession, prefixe, nomsFichiers);
        return new PassageArchive(idPassage, sequences);
    }

    /// Crée le squelette **minimal** d'un passage archivé : le passage (déposé, enregistreur « inconnu »,
    /// sans météo) et sa session marquée **archivée** (#1300), **sans séquences, sans matériel, sans
    /// observations**. C'est le **repli** de la synchro « mes sites » quand le détail d'une nuit est
    /// indisponible (injoignable, refus) : la nuit apparaît quand même dans l'historique, son identité se
    /// rattrapera à la reconstruction (#1710) ou à une nouvelle synchro. Le cas nominal passe désormais par
    /// [#creerAvecIdentite] (#1814). N'émet **aucun** point de progression : la synchro en crée beaucoup et
    /// rythme au niveau du lot, pas de la nuit.
    ///
    /// @param idPoint point d'écoute local (déjà résolu par l'appelant)
    /// @param numeroPassage premier numéro libre pour ce point et cette année
    /// @param debut début de nuit
    /// @param fin fin de nuit
    /// @param prefixe préfixe R6 réel (carré, année, n°, code point) - celui que recalcule l'audit (#1050)
    /// @return l'identifiant du passage créé (nbSequences vaut 0 : un squelette n'a pas encore de séquence)
    public PassageArchive creerSquelette(
            Long idPoint, int numeroPassage, LocalDateTime debut, LocalDateTime fin, Prefixe prefixe) {
        Long idPassage =
                creerPassage(idPoint, numeroPassage, debut, fin, assurerEnregistreur(ENREGISTREUR_INCONNU), null);
        creerSessionArchivee(idPassage, prefixe);
        return new PassageArchive(idPassage, 0);
    }

    /// Crée un squelette de passage archivé **portant l'identité de la nuit** : passage déposé avec son
    /// **enregistreur réel** (lu dans le détail via [CorrespondanceParticipation#serieDepuis]), sa **météo**
    /// et son **micro**, mais **sans séquences ni observations**. Niveau intermédiaire entre [#creerSquelette]
    /// (structure nue, enregistreur « inconnu ») et [#creer] (structure + séquences) : la synchro « mes sites »
    /// paie un appel de détail par nuit nouvelle pour remonter l'identité dès le rapatriement (#1814), tout en
    /// laissant l'audio et les observations à la reconstruction (#1710). Reste un **squelette** (0 séquence →
    /// toujours « à reconstruire »). N'émet **aucun** point de progression.
    ///
    /// @param idPoint point d'écoute local (déjà résolu par l'appelant)
    /// @param numeroPassage premier numéro libre pour ce point et cette année
    /// @param debut début de nuit
    /// @param fin fin de nuit (issue du détail, ou repli sur le début)
    /// @param prefixe préfixe R6 réel (carré, année, n°, code point) - celui que recalcule l'audit (#1050)
    /// @param detail détail de la participation (enregistreur, météo, micro)
    /// @return l'identifiant du passage créé (nbSequences vaut 0 : un squelette n'a pas encore de séquence)
    public PassageArchive creerAvecIdentite(
            Long idPoint,
            int numeroPassage,
            LocalDateTime debut,
            LocalDateTime fin,
            Prefixe prefixe,
            ParticipationDetail detail) {
        Long idPassage = creerPassage(idPoint, numeroPassage, debut, fin, enregistreur(detail), meteoDepuis(detail));
        rapatrierMateriel(idPassage, detail);
        creerSessionArchivee(idPassage, prefixe);
        return new PassageArchive(idPassage, 0);
    }

    /// Crée une nuit **rapatriée par la synchro** (#1814) : avec son **identité** si son détail a pu être lu
    /// ([#creerAvecIdentite]), sinon en **squelette nu** ([#creerSquelette], enregistreur « inconnu »). Dans
    /// les deux cas 0 séquence : la nuit reste « à reconstruire » pour l'audio et les observations.
    ///
    /// @param detail détail de la participation, **vide** s'il était indisponible (best-effort par nuit)
    public PassageArchive creerNuitRapatriee(
            Long idPoint,
            int numeroPassage,
            LocalDateTime debut,
            LocalDateTime fin,
            Prefixe prefixe,
            Optional<ParticipationDetail> detail) {
        return detail.isPresent()
                ? creerAvecIdentite(idPoint, numeroPassage, debut, fin, prefixe, detail.get())
                : creerSquelette(idPoint, numeroPassage, debut, fin, prefixe);
    }

    /// Crée le passage, **déposé** (il l'est : la participation existe sur la plateforme) et sans verdict
    /// local (aucune vérification n'a eu lieu ici). Le numéro de passage est le **premier libre** pour ce
    /// point et cette année (calculé par l'appelant, qui en a aussi besoin pour le préfixe) : la
    /// plateforme ne le porte pas, et deviner « 1 ou 2 » selon la date serait une supposition.
    private Long creerPassage(
            Long idPoint,
            int numeroPassage,
            LocalDateTime debut,
            LocalDateTime fin,
            String idEnregistreur,
            String donneesMeteo) {
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
                        donneesMeteo,
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
    private int creerSequences(Long idSession, Prefixe prefixe, List<String> nomsFichiers) {
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
            for (String titre : nomsFichiers) {
                String nom = nomFichier(titre);
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

    /// Nom de fichier de la séquence : le `titre` distant est **sans extension** (`..._000`), les
    /// séquences locales portent `.wav`.
    private static String nomFichier(String titre) {
        return titre.toLowerCase(java.util.Locale.ROOT).endsWith(".wav") ? titre : titre + ".wav";
    }

    /// Enregistreur de la nuit : le numéro de série que porte la configuration de la participation, lu par
    /// [CorrespondanceParticipation#serieDepuis] qui accepte **les deux clés** en circulation (canonique
    /// VigieChiro `detecteur_enregistreur_numero_serie` du web, et `detecteur_enregistreur_numserie` poussée
    /// par l'app, #1689). Un enregistreur **« inconnu »** de repli seulement si aucune n'est présente (le
    /// schéma exige un enregistreur ; inventer un numéro serait pire que de dire qu'on ne le sait pas). Créé
    /// s'il n'existe pas encore.
    private String enregistreur(ParticipationDetail detail) {
        String serie = CorrespondanceParticipation.serieDepuis(detail.configuration());
        return assurerEnregistreur(serie == null ? ENREGISTREUR_INCONNU : serie);
    }

    /// Garantit qu'un enregistreur de ce numéro de série existe (clé étrangère du passage) et le renvoie ;
    /// le crée « nu » (marque/modèle inconnus) s'il manque. Partagé par la nuit détaillée ([#enregistreur])
    /// et le squelette ([#creerSquelette], qui n'a que « inconnu » sous la main).
    private String assurerEnregistreur(String serie) {
        if (enregistreurDao.findById(serie).isEmpty()) {
            enregistreurDao.insert(new Enregistreur(serie, null, null));
        }
        return serie;
    }

    /// Météo (vent + couverture) de la nuit, rapatriée de la participation (#1689) via les mêmes mappeurs
    /// que le « tir » de [SynchronisationParticipation]. `null` si la participation ne porte pas de météo
    /// (l'API la rend `null`). Les températures ne voyagent pas dans l'API : elles resteront à saisir à la
    /// main (« Modifier le passage », #1688).
    private static String meteoDepuis(ParticipationDetail detail) {
        if (detail.meteo() == null) {
            return null;
        }
        return MeteoPassage.definirReleve(
                null, CorrespondanceParticipation.fusionnerMeteo(MeteoReleve.VIDE, detail.meteo()));
    }

    /// Matériel du micro (position, hauteur, type) rapatrié de la participation (#1689) s'il est renseigné,
    /// via les mêmes clés `micro0_*` que le « tir » de [SynchronisationParticipation].
    private void rapatrierMateriel(Long idPassage, ParticipationDetail detail) {
        if (detail.configuration() != null && !detail.configuration().isEmpty()) {
            materielDao.definir(CorrespondanceParticipation.microDepuis(idPassage, detail.configuration()));
        }
    }
}
