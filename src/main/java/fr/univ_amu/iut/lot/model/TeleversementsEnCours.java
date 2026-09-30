package fr.univ_amu.iut.lot.model;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/// Les passages dont un téléversement tourne **dans ce processus** (#5599).
///
/// La génération des archives le consulte : pendant un téléversement, la source pipelinée produit
/// ses archives dans le même dossier `depot/`, et une génération concurrente y écrirait les mêmes
/// fichiers. Le service de lot ne peut pas interroger le moteur de dépôt, dont la liaison est
/// optionnelle ; les deux partagent donc ce registre, fourni par le module du lot.
///
/// Un registre en mémoire, et non un drapeau en base : un drapeau survivrait à un plantage et
/// bloquerait la génération à tort. Il ne voit pas un second processus (l'application et la CLI sur
/// la même base), et c'est un choix écrit dans le changement `un-depot-entame-se-regenere`.
public final class TeleversementsEnCours {

    private final ConcurrentHashMap<Long, Integer> inscriptions = new ConcurrentHashMap<>();

    /// Inscrit un téléversement du passage, jusqu'à la fermeture du jeton rendu.
    ///
    /// Les inscriptions se comptent : deux téléversements du même passage le laissent en cours jusqu'au
    /// second retrait. Fermer deux fois le même jeton ne retire qu'une fois.
    public Inscription inscrire(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        inscriptions.merge(idPassage, 1, Integer::sum);
        AtomicBoolean fermee = new AtomicBoolean();
        return () -> {
            if (fermee.compareAndSet(false, true)) {
                inscriptions.computeIfPresent(idPassage, (id, nombre) -> nombre > 1 ? nombre - 1 : null);
            }
        };
    }

    public boolean enCours(Long idPassage) {
        return inscriptions.containsKey(idPassage);
    }

    /// Le jeton d'une inscription : le fermer la retire. `AutoCloseable` pour que l'inscription ne se
    /// prenne que dans un `try`, et ne s'oublie donc pas.
    public interface Inscription extends AutoCloseable {
        @Override
        void close();
    }
}
