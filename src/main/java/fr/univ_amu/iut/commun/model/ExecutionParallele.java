package fr.univ_amu.iut.commun.model;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

/// Applique une transformation à chaque élément d'une liste **en parallèle** (un thread virtuel par
/// élément, Java 25), la concurrence étant **bornée** par un [Semaphore] (`parallelisme`). Utile quand
/// chaque tâche charge beaucoup en mémoire ou sur disque (transformation audio, régénération de séquences) :
/// sans plafond, une grosse nuit tiendrait trop de fichiers en vol → saturation. Pic borné ≈ `parallelisme`,
/// sans brider le débit CPU.
///
/// Patron extrait de `importation.model.DecoupageParallele` (#12) pour être réutilisé hors de l'import - la
/// **réactivation** régénère elle aussi des séquences, jusque-là séquentiellement et donc lentement (#1779).
///
/// - **Ordre préservé** : les résultats sont rendus **dans l'ordre de la liste d'entrée** (les `Future` sont
///   récupérés dans l'ordre de soumission) → résultat déterministe malgré le parallélisme.
/// - **Progression thread-safe** : un point « libellé k/N » par tâche **terminée**, émis sous verrou +
///   compteur atomique pour rester monotone et appelé un à un (#146).
/// - **Annulation** : le `jeton` est consulté en tête de chaque tâche ; à la première demande, les tâches
///   restantes sont annulées et l'[OperationAnnuleeException] propage.
///
/// Une tâche qui lève une [RuntimeException] (hors annulation) propage son échec ; les autres tâches déjà
/// soumises s'achèvent (fermeture de l'exécuteur) avant que l'exception ne remonte.
public final class ExecutionParallele {

    private final int parallelisme;

    public ExecutionParallele(int parallelisme) {
        if (parallelisme < 1) {
            throw new IllegalArgumentException("parallelisme doit valoir au moins 1, reçu " + parallelisme);
        }
        this.parallelisme = parallelisme;
    }

    /// Applique `tache` à chaque élément de `elements` en parallèle et rend les résultats **dans le même
    /// ordre** que l'entrée. `libelleProgression` préfixe le compte « k/N » émis à chaque tâche terminée
    /// (par exemple `"Régénération"` → `"Régénération 3/12"`). Une liste vide ne fait rien et rend une liste
    /// vide.
    public <T, R> List<R> cartographier(
            List<T> elements,
            String libelleProgression,
            Function<T, R> tache,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        Objects.requireNonNull(elements, "elements");
        Objects.requireNonNull(libelleProgression, "libelleProgression");
        Objects.requireNonNull(tache, "tache");
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");
        Campagne campagne = new Campagne(
                libelleProgression,
                elements.size(),
                progres,
                jeton,
                new AtomicInteger(0),
                new Object(),
                new Semaphore(parallelisme));
        try (ExecutorService executeur = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> enCours = IntStream.range(0, elements.size())
                    .mapToObj(i -> executeur.submit(() -> executerUne(elements.get(i), tache, campagne)))
                    .toList();
            try {
                return enCours.stream().map(ExecutionParallele::resultat).toList();
            } catch (OperationAnnuleeException annulation) {
                // Annulation (#146) : on arrête les tâches restantes au lieu d'attendre la fin de toutes
                // celles déjà soumises, puis on propage pour que l'appelant nettoie.
                enCours.forEach(tacheEnCours -> tacheEnCours.cancel(true));
                throw annulation;
            }
        }
    }

    /// Invariants d'**une** campagne, partagés par tous les threads : cadence globale (compteur + verrou de
    /// progression + sémaphore), callbacks et libellé. Objet-paramètre pour garder [#executerUne] lisible
    /// (PMD `ExcessiveParameterList`), calqué sur `DecoupageParallele.CampagneDecoupage`.
    private record Campagne(
            String libelle,
            int total,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            AtomicInteger traites,
            Object verrouProgression,
            Semaphore creneaux) {}

    private static <T, R> R executerUne(T element, Function<T, R> tache, Campagne campagne)
            throws InterruptedException {
        campagne.creneaux().acquire();
        try {
            campagne.jeton().leverSiAnnule(); // l'annulation interrompt ; le résultat, lui, est laissé à `tache`
            R resultat = tache.apply(element);
            synchronized (campagne.verrouProgression()) {
                int faits = campagne.traites().incrementAndGet();
                campagne.progres()
                        .accept(new Progression(
                                campagne.libelle() + " " + faits + "/" + campagne.total(),
                                (double) faits / campagne.total()));
            }
            return resultat;
        } finally {
            campagne.creneaux().release();
        }
    }

    /// Récupère le résultat d'une tâche lancée sur un thread virtuel. Ne propage que l'**annulation** (et
    /// restaure le drapeau d'interruption le cas échéant) ; toute autre [RuntimeException] de la tâche est
    /// relancée telle quelle.
    private static <R> R resultat(Future<R> tache) {
        try {
            return tache.get();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Exécution parallèle interrompue.", interruption);
        } catch (ExecutionException echec) {
            if (echec.getCause() instanceof RuntimeException relancable) {
                throw relancable;
            }
            throw new IllegalStateException("Échec d'une tâche parallèle.", echec.getCause());
        }
    }
}
