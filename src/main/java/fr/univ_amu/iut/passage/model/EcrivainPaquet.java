package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.EcrivainZip;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Écrit le paquet d'emport d'une nuit, depuis un [PlanDePaquet] déjà établi (#4625, ADR 4517).
///
/// L'écriture réutilise [EcrivainZip], qui sait déjà supprimer une archive partielle sur échec ou
/// annulation. Rien de neuf n'est écrit ici pour empaqueter : ce qui est neuf, c'est **ce que le
/// paquet doit contenir pour se relire**.
///
/// **Le plan commande.** L'écriture ne recalcule pas ce qu'elle emporte : elle suit ce que le plan a
/// annoncé, sans quoi l'utilisateur aurait confirmé un volume et en aurait obtenu un autre.
public final class EcrivainPaquet {

    private EcrivainPaquet() {}

    /// Écrit le paquet décrit par `plan`.
    ///
    /// **Un plan qui porte des avertissements est refusé** : il signale qu'une séquence n'a pas pu
    /// être lue, donc que le paquet serait amputé. L'écrire quand même produirait une archive dont
    /// la relecture trouverait moins que la nuit, sans que rien ne le dise. Refuser laisse le choix
    /// à l'appelant : reprendre le plan, ou renoncer.
    ///
    /// @param destination l'archive à écrire
    /// @param plan ce qui a été annoncé, et qui fait foi
    /// @param manifeste le texte du manifeste, celui-là même que le plan a pesé
    /// @param sequences les séquences à emporter, dans l'ordre du plan
    /// @return la taille de l'archive écrite, en octets
    /// @throws IllegalStateException si le plan porte un avertissement
    /// @throws IOException sur échec d'écriture, l'archive partielle étant supprimée
    public static long ecrire(Path destination, PlanDePaquet plan, String manifeste, List<Path> sequences)
            throws IOException {
        return ecrire(destination, plan, manifeste, sequences, progression -> {}, JetonAnnulation.neutre());
    }

    /// Variante avec avancement et annulation, pour l'interface.
    ///
    /// @param destination l'archive à écrire
    /// @param plan ce qui a été annoncé, et qui fait foi
    /// @param manifeste le texte du manifeste
    /// @param sequences les séquences à emporter
    /// @param surProgression avancement, possiblement hors du fil JavaFX
    /// @param jeton annulation coopérative
    /// @return la taille de l'archive écrite, en octets
    /// @throws IllegalStateException si le plan porte un avertissement
    /// @throws IOException sur échec d'écriture
    public static long ecrire(
            Path destination,
            PlanDePaquet plan,
            String manifeste,
            List<Path> sequences,
            Consumer<Progression> surProgression,
            JetonAnnulation jeton)
            throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(sequences, "sequences");
        if (!plan.avertissements().isEmpty()) {
            throw new IllegalStateException(
                    "Le paquet serait amputé, il n'est pas écrit : " + String.join(" ; ", plan.avertissements()));
        }
        List<EcrivainZip.EntreeFichier> fichiers = sequences.stream()
                .map(source -> new EcrivainZip.EntreeFichier("sequences/" + source.getFileName(), source))
                .toList();
        return EcrivainZip.ecrire(
                destination,
                List.of(new EcrivainZip.EntreeTexte(PlanDePaquet.NOM_MANIFESTE, manifeste)),
                fichiers,
                surProgression,
                jeton);
    }
}
