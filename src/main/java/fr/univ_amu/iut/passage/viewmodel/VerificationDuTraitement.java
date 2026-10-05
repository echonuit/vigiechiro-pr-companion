package fr.univ_amu.iut.passage.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.ImportApresReleve;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.viewmodel.FormatsTraitement;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/// Le geste « Vérifier le traitement » de la vue d'un passage (#5862) : relever où en est l'analyse d'une
/// nuit déposée et, si elle est terminée, importer ses observations.
///
/// C'est le geste de la carte « Traitement Vigie-Chiro » de l'écran de lot, par la même règle
/// ([fr.univ_amu.iut.commun.model.ImportApresReleve]) et les mêmes phrases
/// ([fr.univ_amu.iut.commun.viewmodel.FormatsTraitement]). La vue d'un passage n'a pas de carte : le
/// résultat est un [RetourOperation], pour son bandeau.
public final class VerificationDuTraitement {

    /// Le nom du bouton, que la phrase d'un import en échec invite à recliquer.
    public static final String GESTE = "Vérifier le traitement";

    /// Les instants du serveur se lisent à l'heure du poste, comme sur la carte de l'écran de lot (#5683).
    private static final ZoneId FUSEAU = ZoneId.systemDefault();

    private final Optional<SuiviTraitement> suivi;
    private final Optional<ImportObservations> importation;

    @Inject
    public VerificationDuTraitement(Optional<SuiviTraitement> suivi, Optional<ImportObservations> importation) {
        this.suivi = Objects.requireNonNull(suivi, "suivi");
        this.importation = Objects.requireNonNull(importation, "importation");
    }

    /// Vrai quand l'application est connectée : sans suivi, rien ne se relève.
    public boolean disponible() {
        return suivi.isPresent();
    }

    /// Relève l'état puis importe si l'analyse est terminée. **Bloquant** (réseau) : hors du fil JavaFX.
    ///
    /// @throws fr.univ_amu.iut.commun.model.RegleMetierException si la plateforme n'a pas pu être lue
    public RetourOperation verifier(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        Traitement traitement = suivi.orElseThrow(() ->
                        new RegleMetierException("Non connecté à Vigie-Chiro : impossible de vérifier le traitement."))
                .relever(idPassage);
        return enMots(traitement, ImportApresReleve.pour(importation, idPassage, traitement));
    }

    /// Ce que la vue dit d'un relevé et de ce qu'il a fait des observations : la phrase d'état, puis la
    /// phrase d'import. Séparé du relevé pour qu'un aperçu rende ces phrases sans réseau.
    public static RetourOperation enMots(Traitement traitement, ImportApresReleve.Issue issue) {
        String etat = FormatsTraitement.libelle(traitement, FUSEAU);
        String texte = (etat + " " + FormatsTraitement.importObservations(issue, GESTE)).strip();
        return new RetourOperation(texte, severite(traitement, issue));
    }

    /// Importé : une bonne nouvelle. Analyse ou import en échec : une erreur. Le reste informe.
    private static Severite severite(Traitement traitement, ImportApresReleve.Issue issue) {
        if (traitement.enEchec() || issue instanceof ImportApresReleve.Issue.Echoue) {
            return Severite.ERREUR;
        }
        return issue instanceof ImportApresReleve.Issue.Fait ? Severite.SUCCES : Severite.INFO;
    }
}
