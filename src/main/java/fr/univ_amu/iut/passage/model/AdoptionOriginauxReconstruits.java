package fr.univ_amu.iut.passage.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Remplace le **placeholder** d'un passage reconstruit (#1305) par les **vrais originaux** régénérés
/// depuis les bruts (#1651), une fois l'hydratation réussie.
///
/// Un passage reconstruit ne portait qu'un original placeholder `reconstruit.wav` (sans fréquence, sans
/// fichier). Après hydratation on connaît ses vrais originaux : chaque brut du dossier a un nom R6, une
/// fréquence d'acquisition (du log) et une taille. On les inscrit, on y **rattache** leurs séquences, on
/// supprime le placeholder devenu orphelin, et on **déclare les originaux purgés** : ils existent et sont
/// prouvés par régénération, mais ne sont pas stockés localement (l'utilisateur garde ses bruts) - le même
/// état qu'un passage archivé par purge. Sans ce marqueur, l'audit, une fois l'archivage levé, signalerait
/// les originaux absents du disque.
///
/// **En une seule transaction** pour les écritures de masse. Une nuit reconstruite compte des milliers de
/// séquences : chaque `INSERT` et chaque rattachement auto-commité, c'est un `fsync` par ordre, et plus de
/// deux minutes d'attente muette sur une nuit de 2000 bruts. Les écritures passent donc par une
/// [fr.univ_amu.iut.commun.persistence.UniteDeTravail], comme le fait déjà l'import, qui écrit la même masse.
///
/// La sûreté **par l'ordre** est conservée, et renforcée : on rattache **toutes** les séquences avant de
/// supprimer un placeholder, et on ne le supprime que s'il n'en porte plus aucune. Le nettoyage des
/// placeholders reste **hors** de la transaction - il ne compte qu'une poignée d'ordres, et il doit lire ce
/// que la transaction vient de valider. Une interruption entre les deux ne perd donc jamais de séquence :
/// elle laisse au pire un placeholder vidé, que le nettoyage suivant emportera.
///
/// L'empreinte `sha256` des originaux est celle **capturée lors de la régénération** (#1726) : la
/// transformation ayant déjà lu chaque brut pour la produire, l'inscrire ne coûte aucune re-lecture. Un
/// original hydraté porte donc son empreinte, comme un import récent (#1299).
public class AdoptionOriginauxReconstruits {

    private static final String SOUS_DOSSIER_BRUTS = "bruts";
    private static final int OCTETS_ENTETE_WAV = 44;
    private static final int OCTETS_PAR_TRAME = 2; // mono 16 bits (bruts PR)

    private final EnregistrementOriginalDao originalDao;
    private final SequenceDao sequenceDao;
    private final SessionDao sessionDao;
    private final UniteDeTravail uniteDeTravail;
    private final Horloge horloge;

    @Inject
    public AdoptionOriginauxReconstruits(
            EnregistrementOriginalDao originalDao,
            SequenceDao sequenceDao,
            SessionDao sessionDao,
            UniteDeTravail uniteDeTravail,
            Horloge horloge) {
        this.originalDao = Objects.requireNonNull(originalDao, "originalDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Adopte les originaux régénérés dans la session. Sans objet s'il n'y a rien à adopter.
    ///
    /// @param session la session du passage reconstruit
    /// @param placeholders les originaux placeholder à retirer (ceux sans fréquence d'acquisition)
    /// @param bruts les bruts rebranchés, avec leurs séquences
    /// @param frequenceAcquisitionHz la fréquence à inscrire sur les originaux (celle du log)
    public void adopter(
            SessionDEnregistrement session,
            List<EnregistrementOriginal> placeholders,
            List<BrutRebranche> bruts,
            int frequenceAcquisitionHz) {
        if (bruts.isEmpty()) {
            return;
        }
        Path racineBruts = Path.of(session.cheminRacine()).resolve(SOUS_DOSSIER_BRUTS);
        // Le gros de l'écriture, d'un seul tenant : un INSERT par brut et un rattachement par séquence, soit
        // des milliers d'ordres sur une nuit reconstruite. Auto-commités un à un, ils coûtaient autant de
        // `fsync` - et l'observateur attendait sans rien voir.
        uniteDeTravail.executer(connexion -> {
            for (BrutRebranche brut : bruts) {
                Long idReel = originalDao
                        .insert(connexion, construireOriginal(session.id(), racineBruts, brut, frequenceAcquisitionHz))
                        .id();
                for (SequenceDEcoute sequence : brut.sequences()) {
                    sequenceDao.majOriginal(connexion, sequence.id(), idReel);
                }
            }
        });
        // Hors transaction, à dessein : quelques ordres tout au plus, et la lecture doit voir les
        // rattachements que l'on vient de valider.
        for (EnregistrementOriginal placeholder : placeholders) {
            if (sequenceDao.findByOriginal(placeholder.id()).isEmpty()) {
                originalDao.delete(placeholder.id());
            }
        }
    }

    /// Construit l'enregistrement original **réel** d'un brut : nom R6, chemin canonique `bruts/`, taille et
    /// durée (déduites de la taille : mono 16 bits, en-tête 44 octets), fréquence du log, et **empreinte
    /// SHA-256** capturée lors de la régénération (#1726).
    private static EnregistrementOriginal construireOriginal(
            Long idSession, Path racineBruts, BrutRebranche brutRebranche, int frequenceAcquisitionHz) {
        BrutInventorie brut = brutRebranche.brut();
        long taille = tailleSource(brut.source());
        Double duree = taille > OCTETS_ENTETE_WAV
                ? (taille - OCTETS_ENTETE_WAV) / (double) OCTETS_PAR_TRAME / frequenceAcquisitionHz
                : null;
        return new EnregistrementOriginal(
                null,
                brut.nomOriginal(),
                racineBruts.resolve(brut.nomOriginal()).toString(),
                duree,
                frequenceAcquisitionHz,
                brutRebranche.empreinteSource(),
                idSession,
                taille);
    }

    private static long tailleSource(Path brut) {
        try {
            return Files.size(brut);
        } catch (IOException e) {
            return 0;
        }
    }
}
