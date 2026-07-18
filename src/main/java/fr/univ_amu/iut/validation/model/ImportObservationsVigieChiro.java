package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.SuiviPagination;
import fr.univ_amu.iut.commun.model.ImportObservations;
import java.util.List;
import java.util.Objects;

/// Implémentation du port [ImportObservations] (#1264) : elle branche l'import réel ([ImportVigieChiro])
/// sur le contrat que le socle expose aux autres écrans — M-Passage en tête.
///
/// Elle ne fait que **traduire** : l'orchestration (résoudre la participation, récupérer les `donnees`,
/// diagnostiquer une absence de résultats) reste dans [ImportVigieChiro], et le compte rendu devient un
/// texte, le bilan détaillé n'ayant pas à traverser le socle.
public class ImportObservationsVigieChiro implements ImportObservations {

    private final ImportVigieChiro importateur;

    public ImportObservationsVigieChiro(ImportVigieChiro importateur) {
        this.importateur = Objects.requireNonNull(importateur, "importateur");
    }

    @Override
    public boolean estRattache(Long idPassage) {
        return importateur.estRattache(idPassage);
    }

    @Override
    public String importer(Long idPassage, boolean remplacer) {
        return compteRendu(importateur.importer(idPassage, remplacer));
    }

    @Override
    public String importer(Long idPassage, List<DonneeVigieChiro> donnees, boolean remplacer) {
        return compteRendu(importateur.importer(idPassage, donnees, remplacer));
    }

    @Override
    public String importer(Long idPassage, boolean remplacer, SuiviPagination suivi) {
        return compteRendu(importateur.importer(idPassage, remplacer, suivi));
    }

    @Override
    public List<String> nomsSequencesCsv(String contenuCsv) {
        return importateur.nomsSequencesCsv(contenuCsv);
    }

    @Override
    public String importerCsv(Long idPassage, String contenuCsv, boolean remplacer) {
        return compteRendu(importateur.importerCsv(idPassage, contenuCsv, remplacer));
    }

    @Override
    public boolean ancrageManquant(Long idPassage) {
        return importateur.ancrageManquant(idPassage);
    }

    private static String compteRendu(BilanImport bilan) {
        return "Observations importées depuis Vigie-Chiro : " + bilan.importees() + " observation(s)."
                + (bilan.ignorees() > 0 ? " " + bilan.ignorees() + " ignorée(s)." : "")
                + echanges(bilan);
    }

    /// Les **échanges avec le validateur** rapatriés au passage (#1867). Muet quand il n'y en a pas, ce
    /// qui est le cas courant : une phrase de plus à chaque import n'apprendrait rien. Le compte porte sur
    /// les **observations** concernées, parce que c'est ce qui dit à l'observateur où regarder.
    private static String echanges(BilanImport bilan) {
        int concernees = bilan.observationsAvecEchange();
        if (concernees == 0) {
            return "";
        }
        return " Le validateur s'est exprimé sur " + concernees + " observation(s).";
    }
}
