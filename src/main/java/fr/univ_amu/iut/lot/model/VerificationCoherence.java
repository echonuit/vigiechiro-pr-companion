package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// Moteur de vérification de cohérence d'un passage avant préparation d'un lot (parcours
/// P4, story E4.S1). Rejoue tous les contrôles affichés dans la maquette M-Lot et les
/// restitue sous forme d'un [ResultatVerification] (cumul d'alertes) que l'IHM utilise pour
/// afficher chaque ligne ✓/✗ et pour activer/désactiver le bouton de dépôt.
///
/// Contrôles, dans l'ordre des règles métier :
///
/// - **R14** (bloquant) : un passage au verdict [Verdict#A_JETER] ne peut pas rejoindre un
///   lot. Restitué ici comme alerte bloquante pour l'affichage ; le refus dur est levé par
///   [ServiceLot].
/// - **Session d'enregistrement** (bloquant) : une session est rattachée au passage. Celui-ci
///   **arrête le parcours** - sans session, aucun des contrôles suivants n'est calculable, et la
///   liste rendue s'arrête là plutôt que d'être incomplète.
/// - **Transformation des enregistrements** (bloquant, R10) : des séquences d'écoute sont présentes
///   et **chaque** enregistrement original a été transformé en au moins une séquence.
/// - **Nommage des fichiers** (bloquant, R6/R7/R8) : tous les enregistrements originaux et toutes
///   les séquences portent le préfixe attendu `Car<carré>-<année>-Pass<n>-<point>-`.
/// - **Journal du capteur** (bloquant) : un `sensor_log` accompagne la session.
/// - **Relevé climatique** (soft, R20) : son absence est *signalée sans bloquer* (sonde non
///   installée ou défaillante) ; le dépôt reste possible.
///
/// Pure logique métier (aucun import JavaFX). Reçoit ses DAO par constructeur, à la manière
/// du service de référence `ServiceSites`. Dépendances inter-feature en lecture seule
/// autorisées : `lot → passage` (séquences, originaux, journal, relevé, session) et
/// `lot → sites` (point et site, pour calculer le préfixe attendu) ; le graphe reste
/// acyclique.
public class VerificationCoherence {

    private final SiteDao siteDao;
    private final PointDao pointDao;
    private final SessionDao sessionDao;
    private final EnregistrementOriginalDao originalDao;
    private final SequenceDao sequenceDao;
    private final JournalDuCapteurDao journalDao;
    private final ReleveClimatiqueDao releveDao;

    public VerificationCoherence(
            SiteDao siteDao,
            PointDao pointDao,
            SessionDao sessionDao,
            EnregistrementOriginalDao originalDao,
            SequenceDao sequenceDao,
            JournalDuCapteurDao journalDao,
            ReleveClimatiqueDao releveDao) {
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.originalDao = Objects.requireNonNull(originalDao, "originalDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.journalDao = Objects.requireNonNull(journalDao, "journalDao");
        this.releveDao = Objects.requireNonNull(releveDao, "releveDao");
    }

    /// Vérifie qu'un passage est prêt à déposer et renvoie le cumul d'alertes (vide si tout
    /// est conforme), **dérivé** de la checklist [#controler]. `estBloquant()` vaut `true` dès qu'au
    /// moins un contrôle échoue : dans ce cas [ServiceLot] refuse la préparation.
    ///
    /// @param passage le passage à contrôler (avec son `id` persisté)
    public ResultatVerification verifier(Passage passage) {
        ResultatVerification resultat = ResultatVerification.ok();
        for (ControleCoherence controle : controler(passage)) {
            if (controle.statut() == StatutControle.ECHEC) {
                resultat = resultat.avec(Alerte.bloquante(controle.detail()));
            } else if (controle.statut() == StatutControle.AVERTISSEMENT) {
                resultat = resultat.avec(Alerte.soft(controle.detail()));
            }
        }
        return resultat;
    }

    /// **Checklist** des contrôles de cohérence du passage (#254), chacun avec son statut (✓ / ✗ / ⚠) et
    /// un détail : affichée telle quelle à l'étape « Préparer le lot », même quand tout est satisfait.
    ///
    /// @param passage le passage à contrôler (avec son `id` persisté)
    public List<ControleCoherence> controler(Passage passage) {
        Objects.requireNonNull(passage, "passage");
        List<ControleCoherence> controles = new ArrayList<>();
        controles.add(controleVerdict(passage));

        Optional<SessionDEnregistrement> sessionOpt = sessionDao.trouverParPassage(passage.id());
        if (sessionOpt.isEmpty()) {
            // Sans session, aucun des contrôles suivants n'est calculable : on s'arrête là.
            controles.add(new ControleCoherence(
                    "Session d'enregistrement",
                    StatutControle.ECHEC,
                    "Aucune session d'enregistrement n'est rattachée à ce passage : importez et transformez la nuit"
                            + " avant de préparer le dépôt."));
            return controles;
        }
        SessionDEnregistrement session = sessionOpt.get();
        List<EnregistrementOriginal> originaux = originalDao.findBySession(session.id());
        List<SequenceDEcoute> sequences = sequenceDao.findBySession(session.id());

        controles.add(controleTransformation(originaux, sequences));
        controles.add(controlePrefixe(passage, originaux, sequences));
        controles.add(controleJournal(session));
        controles.add(controleReleve(session));
        return controles;
    }

    /// R14 : un passage au verdict « Inexploitable » ne peut pas être déposé.
    private static ControleCoherence controleVerdict(Passage passage) {
        if (passage.verdictVerification() == Verdict.A_JETER) {
            return new ControleCoherence(
                    "Verdict de vérification",
                    StatutControle.ECHEC,
                    "Ce passage porte le verdict « Inexploitable » et ne peut pas être déposé."
                            + " Re-vérifiez-le pour lui attribuer un autre verdict.");
        }
        return new ControleCoherence(
                "Verdict de vérification", StatutControle.OK, "Le passage n'est pas marqué « Inexploitable ».");
    }

    /// R10 : des séquences existent et chaque original a au moins une séquence dérivée.
    private static ControleCoherence controleTransformation(
            List<EnregistrementOriginal> originaux, List<SequenceDEcoute> sequences) {
        String libelle = "Transformation des enregistrements";
        if (sequences.isEmpty()) {
            return new ControleCoherence(
                    libelle,
                    StatutControle.ECHEC,
                    "Aucune séquence d'écoute : la transformation des enregistrements originaux n'a pas été"
                            + " effectuée.");
        }
        Set<Long> originauxTransformes = sequences.stream()
                .map(SequenceDEcoute::idEnregistrementOriginal)
                .collect(Collectors.toSet());
        long nonTransformes = originaux.stream()
                .filter(o -> !originauxTransformes.contains(o.id()))
                .count();
        if (nonTransformes > 0) {
            return new ControleCoherence(
                    libelle,
                    StatutControle.ECHEC,
                    nonTransformes
                            + " enregistrement(s) original(aux) n'ont pas encore été transformé(s) en séquences"
                            + " d'écoute.");
        }
        return new ControleCoherence(
                libelle, StatutControle.OK, "Chaque enregistrement original a ses séquences d'écoute.");
    }

    /// R6/R7/R8 : le préfixe attendu est présent sur tous les originaux et toutes les séquences.
    private ControleCoherence controlePrefixe(
            Passage passage, List<EnregistrementOriginal> originaux, List<SequenceDEcoute> sequences) {
        String libelle = "Nommage des fichiers";
        Optional<Prefixe> prefixeOpt = prefixeAttendu(passage);
        if (prefixeOpt.isEmpty()) {
            return new ControleCoherence(
                    libelle,
                    StatutControle.ECHEC,
                    "Impossible de calculer le préfixe attendu : le point d'écoute ou le site du passage est"
                            + " introuvable.");
        }
        String prefixe = prefixeOpt.get().prefixeFichier();
        long nonConformes = sequences.stream()
                        .map(SequenceDEcoute::nomFichier)
                        .filter(nom -> !commencePar(nom, prefixe))
                        .count()
                + originaux.stream()
                        .map(EnregistrementOriginal::nomFichier)
                        .filter(nom -> !commencePar(nom, prefixe))
                        .count();
        if (nonConformes > 0) {
            return new ControleCoherence(
                    libelle,
                    StatutControle.ECHEC,
                    "Préfixe « " + prefixe + " » manquant ou non conforme sur " + nonConformes + " fichier(s).");
        }
        return new ControleCoherence(
                libelle, StatutControle.OK, "Tous les fichiers portent le préfixe « " + prefixe + " ».");
    }

    /// Journal du capteur obligatoire (bloquant) : un `sensor_log` doit accompagner la session.
    private ControleCoherence controleJournal(SessionDEnregistrement session) {
        if (journalDao.trouverParSession(session.id()).isEmpty()) {
            return new ControleCoherence(
                    "Journal du capteur",
                    StatutControle.ECHEC,
                    "Journal du capteur (LogPR<n>.txt) absent : il doit accompagner les séquences déposées.");
        }
        return new ControleCoherence("Journal du capteur", StatutControle.OK, "Présent, à déposer avec les séquences.");
    }

    /// Relevé climatique optionnel (soft, R20) : son absence est signalée sans bloquer.
    private ControleCoherence controleReleve(SessionDEnregistrement session) {
        if (releveDao.trouverParSession(session.id()).isEmpty()) {
            return new ControleCoherence(
                    "Relevé climatique",
                    StatutControle.AVERTISSEMENT,
                    "Relevé climatique absent : sonde non installée ou défaillante. Le dépôt reste possible.");
        }
        return new ControleCoherence("Relevé climatique", StatutControle.OK, "Présent.");
    }

    /// Préfixe `Car<carré>-<année>-Pass<n>-<point>-` attendu, calculé depuis le point et le
    /// site.
    private Optional<Prefixe> prefixeAttendu(Passage passage) {
        return pointDao.findById(passage.idPoint())
                .flatMap((PointDEcoute point) -> siteDao.findById(point.idSite())
                        .map((Site site) -> new Prefixe(
                                site.numeroCarre(), passage.annee(), passage.numeroPassage(), point.code())));
    }

    private static boolean commencePar(String nomFichier, String prefixe) {
        return nomFichier != null && nomFichier.startsWith(prefixe);
    }
}
