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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/// Découpe (#12) en **parallèle** la transformation R10/R11 des originaux d'une nuit, extraite de
/// [ServiceImport] (qui gardait sinon trop de responsabilités, PMD GodClass). Un thread virtuel par
/// fichier (Java 25), la concurrence étant **bornée** par un [Semaphore] : `transformer` charge tout le
/// PCM en mémoire puis écrit sur disque, donc sans plafond une grosse nuit (~1572 fichiers) tiendrait
/// trop de WAV en vol → saturation mémoire. Pic mémoire borné ≈ nbCœurs PCM, sans brider le débit CPU.
///
/// L'**ordre d'origine est préservé** (Future récupérés dans l'ordre de soumission) → résultat
/// déterministe. La progression (#33) est émise **sous verrou + compteur** pour rester appelée un à un,
/// libellés « k/N · fichier » monotones (#146). Le **fail-fast** (#12) arrête les découpages restants à
/// la première erreur, mécanisme **réutilisé pour l'annulation** (#146) : lever le jeton agit comme une
/// erreur.
final class DecoupageParallele {

    private final TransformationAudio transformation;
    private final int parallelisme;

    DecoupageParallele(TransformationAudio transformation, int parallelisme) {
        this.transformation = Objects.requireNonNull(transformation, "transformation");
        this.parallelisme = parallelisme;
    }

    List<TransformationOriginal> decouper(
            List<Path> originaux,
            Path dossierTransformes,
            Prefixe prefixe,
            int nbOriginaux,
            int totalEtapes,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        AtomicInteger transfosFaites = new AtomicInteger(0);
        AtomicReference<RuntimeException> echecDecoupage = new AtomicReference<>();
        Object verrouProgression = new Object();
        Semaphore creneaux = new Semaphore(parallelisme);
        try (ExecutorService executeur = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<TransformationOriginal>> decoupagesEnCours = originaux.stream()
                    .map(original -> executeur.submit(() -> {
                        creneaux.acquire();
                        try {
                            // Fail-fast (#12) : si un découpage a déjà échoué (n'importe lequel, quel que
                            // soit l'ordre), on n'en lance pas un nouveau et on propage l'échec d'origine
                            // → plus aucun fichier décodé inutilement une fois l'erreur connue.
                            RuntimeException dejaEchoue = echecDecoupage.get();
                            if (dejaEchoue != null) {
                                throw dejaEchoue;
                            }
                            // Annulation (#146) : on réutilise le fail-fast — lever ici stoppe les
                            // découpages restants exactement comme une erreur, sans en lancer de nouveaux.
                            jeton.leverSiAnnule();
                            TransformationOriginal resultat =
                                    transformation.transformer(original, dossierTransformes, prefixe);
                            synchronized (verrouProgression) {
                                int faits = transfosFaites.incrementAndGet();
                                progres.accept(new Progression(
                                        "Transformation " + faits + "/" + nbOriginaux + " · " + original.getFileName(),
                                        (double) (nbOriginaux + faits) / totalEtapes));
                            }
                            return resultat;
                        } catch (RuntimeException echec) {
                            echecDecoupage.compareAndSet(null, echec);
                            throw echec;
                        } finally {
                            creneaux.release();
                        }
                    }))
                    .toList();
            try {
                return decoupagesEnCours.stream()
                        .map(DecoupageParallele::resultatDecoupage)
                        .toList();
            } catch (RuntimeException echec) {
                // Fail-fast (#12) : à la première erreur, on annule les découpages restants (ceux en
                // attente sur le sémaphore s'arrêtent net) au lieu d'attendre toute la nuit avant de
                // remonter l'échec. Sans cela, la fermeture du pool patienterait jusqu'à la fin de tous
                // les originaux déjà soumis.
                decoupagesEnCours.forEach(decoupage -> decoupage.cancel(true));
                throw echec;
            }
        }
    }

    /// Récupère le résultat d'un découpage lancé sur un thread virtuel (#12), en **dévoilant** la cause
    /// d'un éventuel échec : une [RuntimeException] (ex. [java.io.UncheckedIOException] d'écriture) est
    /// relancée telle quelle ; une interruption restaure le drapeau du thread avant de remonter l'erreur.
    private static TransformationOriginal resultatDecoupage(Future<TransformationOriginal> decoupage) {
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
