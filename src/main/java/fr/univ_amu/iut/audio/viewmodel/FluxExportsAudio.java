package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.SondeAccessibilite;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Les exports du **sous-ensemble affiché** (CSV seul, ou archive « observations + sons » #2793),
/// extraits du [AudioViewModel] (plafond God Class) et exposés par lui, comme la file de positions
/// l'est par le ViewModel multisite : la vue les pilote, ils parlent au [ExporteurAudio] et
/// restituent par les messages du VM.
///
/// Trois temps : [#preparer] sur le fil JavaFX (sous-ensemble figé + sonde de destination, #2426),
/// [#exporter] **hors fil** dans la modale de progression, puis la restitution selon l'issue -
/// [#confirmer], [#annulee] ou [#echec], sur le fil JavaFX (issues de la modale).
public final class FluxExportsAudio {

    private final ExporteurAudio exporteur;
    private final MessagesAudio messages;
    private final Supplier<List<LigneObservationAudio>> affichees;

    FluxExportsAudio(
            ExporteurAudio exporteur, MessagesAudio messages, Supplier<List<LigneObservationAudio>> affichees) {
        this.exporteur = Objects.requireNonNull(exporteur, "exporteur");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.affichees = Objects.requireNonNull(affichees, "affichees");
    }

    /// Exporte en **CSV** les observations **actuellement affichées** (filtres appliqués) vers
    /// `destination` (#149). Le sous-ensemble est figé au moment de l'appel ; le bilan (ou l'erreur
    /// d'écriture) est restitué dans le message.
    ///
    /// @return `true` si le fichier a été écrit
    public boolean observationsCsv(Path destination) {
        ExporteurAudio.ResultatExport resultat = exporteur.observations(List.copyOf(affichees.get()), destination);
        messages.export(resultat.reussi(), resultat.message());
        return resultat.reussi();
    }

    /// **Prépare** l'export, sur le fil JavaFX : fige le sous-ensemble affiché et **sonde le dossier
    /// de destination** (#2426) avant d'ouvrir la modale. Vide = refus, le motif est posé en message.
    public Optional<List<LigneObservationAudio>> preparer(Path destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Path dossier = destination.toAbsolutePath().getParent();
        SondeAccessibilite.Verdict verdict = SondeAccessibilite.sonder(dossier);
        if (!verdict.accessible()) {
            messages.avertissement("Dossier inutilisable : " + verdict.motif() + " (" + dossier + ").");
            return Optional.empty();
        }
        return Optional.of(List.copyOf(affichees.get()));
    }

    /// Exporte `lignes` **et leurs sons** en archive ZIP. **Bloquant** : à appeler hors du fil JavaFX,
    /// dans la modale de progression, après [#preparer]. L'annulation remonte en
    /// [fr.univ_amu.iut.commun.model.OperationAnnuleeException] (issue « annulé » de la modale),
    /// l'échec d'écriture en [UncheckedIOException] (issue « échec ») - l'archive partielle est déjà
    /// supprimée.
    ///
    /// @return le message de bilan, à restituer par [#confirmer]
    public String exporter(
            List<LigneObservationAudio> lignes,
            Path destination,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        try {
            return exporteur.observationsEtSons(lignes, destination, progres, jeton);
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }

    /// Le bilan de l'export, en message de succès (issue nominale de la modale).
    public void confirmer(String message) {
        messages.export(true, message);
    }

    /// L'utilisateur a annulé : l'archive partielle a été supprimée, on le dit sans dramatiser.
    public void annulee() {
        messages.avertissement("Export annulé : aucune archive n'a été écrite.");
    }

    /// L'écriture a échoué : l'archive partielle a été supprimée, le motif est restitué.
    public void echec(Throwable echec) {
        String motif = echec.getCause() != null ? echec.getCause().getMessage() : echec.getMessage();
        messages.export(false, "Export impossible : " + motif);
    }
}
