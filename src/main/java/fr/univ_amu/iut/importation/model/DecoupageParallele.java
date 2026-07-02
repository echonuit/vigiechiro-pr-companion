package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/// Découpe (#12) en **parallèle** la transformation R10/R11 des originaux d'une nuit, extraite de
/// [ServiceImport]. Un thread virtuel par fichier (Java 25), la concurrence étant **bornée** par un
/// [Semaphore] : `transformer` charge tout le PCM en mémoire puis écrit sur disque, donc sans plafond
/// une grosse nuit (~1572 fichiers) tiendrait trop de WAV en vol → saturation mémoire. Pic mémoire
/// borné ≈ nbCœurs PCM, sans brider le débit CPU.
///
/// **Résilience (#155)** : un original illisible ou de format invalide n'**abat plus** l'import — il est
/// capturé en [ResultatDecoupage] rejeté (avec sa raison) et le découpage se poursuit sur les autres.
/// Seule une **annulation** ([AnnulationImportException], #146) interrompt l'ensemble.
///
/// L'**ordre d'origine est préservé** (Future récupérés dans l'ordre de soumission) → résultat
/// déterministe. La progression (#33) est émise **sous verrou + compteur** pour rester appelée un à un,
/// libellés « k/N · fichier » monotones (#146), un point par fichier traité (réussi **ou** rejeté).
final class DecoupageParallele {

    private final TransformationAudio transformation;
    private final int parallelisme;

    DecoupageParallele(TransformationAudio transformation, int parallelisme) {
        this.transformation = Objects.requireNonNull(transformation, "transformation");
        this.parallelisme = parallelisme;
    }

    List<ResultatDecoupage> decouper(
            List<Path> originaux,
            Path dossierTransformes,
            Prefixe prefixe,
            Integer frequenceAcquisitionLogHz,
            int nbOriginaux,
            int totalEtapes,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        AtomicInteger traites = new AtomicInteger(0);
        Object verrouProgression = new Object();
        Semaphore creneaux = new Semaphore(parallelisme);
        try (ExecutorService executeur = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ResultatDecoupage>> decoupagesEnCours = originaux.stream()
                    .map(original -> executeur.submit(() -> decouperUn(
                            original,
                            dossierTransformes,
                            prefixe,
                            frequenceAcquisitionLogHz,
                            nbOriginaux,
                            totalEtapes,
                            progres,
                            jeton,
                            traites,
                            verrouProgression,
                            creneaux)))
                    .toList();
            try {
                return decoupagesEnCours.stream()
                        .map(DecoupageParallele::resultat)
                        .toList();
            } catch (AnnulationImportException annulation) {
                // Annulation (#146) : on arrête les découpages restants au lieu d'attendre la fin de tous
                // les originaux déjà soumis, puis on propage pour que l'appelant nettoie la session.
                decoupagesEnCours.forEach(decoupage -> decoupage.cancel(true));
                throw annulation;
            }
        }
    }

    private ResultatDecoupage decouperUn(
            Path original,
            Path dossierTransformes,
            Prefixe prefixe,
            Integer frequenceAcquisitionLogHz,
            int nbOriginaux,
            int totalEtapes,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            AtomicInteger traites,
            Object verrouProgression,
            Semaphore creneaux)
            throws InterruptedException {
        creneaux.acquire();
        try {
            jeton.leverSiAnnule(); // l'annulation (#146) interrompt ; un rejet de fichier, lui, est capturé
            ResultatDecoupage resultat;
            try {
                TransformationOriginal t =
                        transformation.transformer(original, dossierTransformes, prefixe, frequenceAcquisitionLogHz);
                resultat = new ResultatDecoupage(original, t, null);
            } catch (OriginalIllisibleException | OriginalDejaRalentiException rejet) {
                // Résilience (#155) : une erreur de lecture/format SOURCE OU un enregistrement déjà ralenti
                // (garde-fou double expansion) est consigné en rejet et l'import continue. Une erreur
                // d'écriture workspace (UncheckedIOException) reste fatale et se propage.
                resultat = new ResultatDecoupage(original, null, raison(rejet));
            }
            synchronized (verrouProgression) {
                int faits = traites.incrementAndGet();
                progres.accept(new Progression(
                        "Transformation " + faits + "/" + nbOriginaux + " · " + original.getFileName(),
                        (double) (nbOriginaux + faits) / totalEtapes));
            }
            return resultat;
        } finally {
            creneaux.release();
        }
    }

    private static String raison(RuntimeException echec) {
        String message = echec.getMessage();
        return message == null || message.isBlank() ? echec.getClass().getSimpleName() : message;
    }

    /// Récupère le résultat d'un découpage lancé sur un thread virtuel (#12). Une erreur par fichier est
    /// déjà capturée dans le [ResultatDecoupage] ; ici on ne propage que l'**annulation** (et on restaure
    /// le drapeau d'interruption le cas échéant).
    private static ResultatDecoupage resultat(Future<ResultatDecoupage> decoupage) {
        try {
            return decoupage.get();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Découpage audio interrompu.", interruption);
        } catch (ExecutionException echec) {
            if (echec.getCause() instanceof RuntimeException relancable) {
                throw relancable;
            }
            throw new IllegalStateException("Échec du découpage audio.", echec.getCause());
        }
    }
}
