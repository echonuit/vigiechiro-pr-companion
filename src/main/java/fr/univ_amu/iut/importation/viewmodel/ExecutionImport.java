package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.importation.model.JetonAnnulation;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.SuiviFichiers;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel.DemandeImport;
import java.util.Objects;
import java.util.function.Consumer;

/// Exécution du **travail lourd** d'un import mono-nuit (copie + renommage + transformation +
/// persistance) via [ServiceImport#importer], à partir d'un instantané [DemandeImport]. Extrait de
/// [ImportationViewModel] (Extract Class, même esprit que [ControleNumeroPassage] et
/// [CoordinationNuits]) pour garder l'orchestrateur cohésif : exécuter hors-thread est une
/// préoccupation autonome, sans lecture de `Property`.
///
/// Toutes les variantes sont **sûres sur un fil d'arrière-plan** : elles ne lisent aucune `Property`
/// et ne mutent rien ; la vue relaie progression et suivi par fichier au fil JavaFX.
public final class ExecutionImport {

    private final ServiceImport serviceImport;

    ExecutionImport(ServiceImport serviceImport) {
        this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
    }

    /// Exécute l'import sans suivi (variante synchrone minimale).
    ///
    /// @return le résultat de l'import
    /// @throws RuntimeException si l'import échoue (refus métier R5, journal manquant…)
    public ResultatImport executer(DemandeImport demande) {
        return executer(demande, progres -> {});
    }

    /// Variante avec **suivi de progression** (#33) : `progres` est notifié sur le fil d'exécution de
    /// l'import ; la vue le relaie au fil JavaFX.
    public ResultatImport executer(DemandeImport demande, Consumer<Progression> progres) {
        return executer(demande, progres, JetonAnnulation.neutre());
    }

    /// Variante **annulable** (#146) : `jeton` permet d'interrompre l'import en cours. Une annulation
    /// remonte une [fr.univ_amu.iut.importation.model.AnnulationImportException] (RuntimeException) que
    /// la vue traite via [ImportationViewModel#marquerAnnule()].
    public ResultatImport executer(DemandeImport demande, Consumer<Progression> progres, JetonAnnulation jeton) {
        return executer(demande, progres, jeton, SuiviFichiers.inerte());
    }

    /// Variante avec **suivi par fichier** (#947) : `suivi` est notifié hors-thread, fichier par fichier
    /// (plan, copie, transformation, rejet) ; la vue relaie au fil JavaFX vers
    /// [ImportationViewModel#suiviFichiers()].
    public ResultatImport executer(
            DemandeImport demande, Consumer<Progression> progres, JetonAnnulation jeton, SuiviFichiers suivi) {
        return serviceImport.importer(
                demande.dossier(),
                demande.idPoint(),
                demande.prefixe(),
                progres,
                jeton,
                demande.conserverOriginaux(),
                suivi);
    }
}
