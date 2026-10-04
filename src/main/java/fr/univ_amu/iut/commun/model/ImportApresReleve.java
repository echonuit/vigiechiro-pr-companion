package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.api.Traitement;
import java.util.Objects;
import java.util.Optional;

/// Ce qu'un relevé « analyse terminée » fait des observations de la nuit (#5784).
///
/// L'observateur vient de demander où en est le traitement. Quand la réponse est « terminé », la seule
/// suite est d'importer : l'écran de lot et `etat-traitement-vigiechiro --importer` le font ici, par la
/// même règle, pour que la parité tienne à un seul endroit.
///
/// Jamais de remplacement : une nuit qui a déjà ses observations n'est pas touchée. Les remplacer se
/// décide nuit par nuit, depuis « Sons & validation ».
public final class ImportApresReleve {

    private ImportApresReleve() {}

    /// Ce que le relevé a fait des observations.
    public sealed interface Issue {

        /// Rien à importer : l'analyse n'est pas terminée, ou l'import n'existe pas dans ce contexte.
        record SansObjet() implements Issue {}

        /// Les observations viennent d'être importées.
        ///
        /// @param compteRendu le texte prêt à afficher que rend l'import
        record Fait(String compteRendu) implements Issue {}

        /// La nuit avait déjà ses observations : rien n'a été importé.
        record DejaLa() implements Issue {}

        /// L'import a été tenté et a échoué. L'analyse, elle, reste terminée.
        ///
        /// @param motif ce que l'import a dit de son échec
        record Echoue(String motif) implements Issue {}
    }

    /// Importe les observations si le relevé dit l'analyse terminée et que la nuit n'en a pas.
    /// **Bloquant** (réseau) : à appeler hors du fil JavaFX.
    public static Issue pour(Optional<ImportObservations> importation, Long idPassage, Traitement traitement) {
        Objects.requireNonNull(importation, "importation");
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(traitement, "traitement");
        if (importation.isEmpty() || !traitement.resultatsDisponibles()) {
            return new Issue.SansObjet();
        }
        ImportObservations port = importation.get();
        if (port.aDejaSesObservations(idPassage)) {
            return new Issue.DejaLa();
        }
        try {
            return new Issue.Fait(port.importer(idPassage, false));
        } catch (RuntimeException echec) {
            // L'analyse est terminée quoi qu'il arrive à l'import : son échec se rend, il ne se propage pas,
            // sans quoi l'appelant perdrait l'état qu'il vient de relever.
            return new Issue.Echoue(CauseLisible.messageDe(echec));
        }
    }

    /// Ce qu'il reste à faire d'une analyse terminée **lue du cache**, sans réseau : rien si la nuit a ses
    /// observations, un relevé sinon. Vide quand l'analyse n'est pas terminée.
    public static Optional<Boolean> dejaImportee(
            Optional<ImportObservations> importation, Long idPassage, Traitement traitement) {
        if (importation.isEmpty() || !traitement.resultatsDisponibles()) {
            return Optional.empty();
        }
        return Optional.of(importation.get().aDejaSesObservations(idPassage));
    }
}
