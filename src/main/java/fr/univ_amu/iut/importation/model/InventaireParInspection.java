package fr.univ_amu.iut.importation.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.passage.model.BrutInventorie;
import fr.univ_amu.iut.passage.model.InventaireBruts;
import fr.univ_amu.iut.passage.model.InventaireBrutsSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Implémentation du port [InventaireBrutsSource] (#1649) : elle **inspecte** le dossier désigné avec le
/// même [InspecteurDossier] que l'import (lecture seule, R9), et n'en garde que ce qu'il faut pour
/// hydrater un passage reconstruit : la **fréquence d'acquisition** du log et le **nom R6** de chaque
/// brut trouvé.
///
/// Rien n'est réinventé : c'est un **adaptateur**. La reconnaissance du log, des originaux (à la racine
/// ou sous `bruts/`) et de leur état de nommage est celle, déjà éprouvée, de l'import.
public class InventaireParInspection implements InventaireBrutsSource {

    private final InspecteurDossier inspecteur;

    @Inject
    public InventaireParInspection(InspecteurDossier inspecteur) {
        this.inspecteur = Objects.requireNonNull(inspecteur, "inspecteur");
    }

    @Override
    public Optional<InventaireBruts> inventorier(Path dossierBruts, Prefixe prefixe) {
        Objects.requireNonNull(dossierBruts, "dossierBruts");
        Objects.requireNonNull(prefixe, "prefixe");
        RapportInspection rapport = inspecteur.inspecter(dossierBruts);
        JournalParse journal = rapport.journal();
        if (journal == null
                || journal.frequenceEchantillonnageHz() == null
                || journal.frequenceEchantillonnageHz() <= 0) {
            // Sans log exploitable, pas de fréquence d'acquisition sûre : on ne l'invente pas (#1648).
            return Optional.empty();
        }
        List<BrutInventorie> bruts = rapport.originaux().stream()
                .map(source -> new BrutInventorie(source, nomOriginal(source, prefixe)))
                .toList();
        return bruts.isEmpty()
                ? Optional.empty()
                : Optional.of(new InventaireBruts(journal.frequenceEchantillonnageHz(), bruts));
    }

    /// Nom R6 de l'original : le préfixe R6 est ce que l'import **ajoute** au nom d'enregistreur. Une copie
    /// de carte SD ne le porte pas (on l'ajoute), une copie du dossier `bruts/` le porte déjà (on le garde,
    /// sans jamais re-préfixer, cf. [Prefixe#estNomPrefixe]).
    private static String nomOriginal(Path source, Prefixe prefixe) {
        String nom = source.getFileName().toString();
        return Prefixe.estNomPrefixe(nom) ? nom : prefixe.nommerOriginal(nom);
    }
}
