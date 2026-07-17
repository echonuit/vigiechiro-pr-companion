package fr.univ_amu.iut.passage.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Horloge;
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
/// Non transactionnel mais **sûr par l'ordre** : on rattache **toutes** les séquences avant de supprimer un
/// placeholder, et on ne le supprime que s'il n'en porte plus aucune. Une interruption ne perd donc jamais
/// de séquence (la suppression `ON DELETE CASCADE` ne s'applique qu'à un placeholder déjà vidé).
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
    private final Horloge horloge;

    @Inject
    public AdoptionOriginauxReconstruits(
            EnregistrementOriginalDao originalDao, SequenceDao sequenceDao, SessionDao sessionDao, Horloge horloge) {
        this.originalDao = Objects.requireNonNull(originalDao, "originalDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
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
        for (BrutRebranche brut : bruts) {
            Long idReel = originalDao
                    .insert(construireOriginal(session.id(), racineBruts, brut, frequenceAcquisitionHz))
                    .id();
            for (SequenceDEcoute sequence : brut.sequences()) {
                sequenceDao.majOriginal(sequence.id(), idReel);
            }
        }
        for (EnregistrementOriginal placeholder : placeholders) {
            if (sequenceDao.findByOriginal(placeholder.id()).isEmpty()) {
                originalDao.delete(placeholder.id());
            }
        }
        sessionDao.marquerOriginauxPurges(session.id(), horloge.maintenant());
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
