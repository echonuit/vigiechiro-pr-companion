package fr.univ_amu.iut.importation.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.passage.model.RegenerationSequences;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Implémentation du port [RegenerationSequences] (#1406) : elle rejoue **exactement** la chaîne de
/// l'import ([TransformationAudio]), c'est-à-dire la seule chose qui garantisse que les tranches
/// régénérées soient identiques à celles d'origine - mêmes noms, et mêmes octets tant que le code de
/// transformation n'a pas changé.
///
/// Rien n'est réinventé ici : c'est un **adaptateur**. Toute divergence future de la transformation se
/// répercutera donc automatiquement, et sera détectée par la cascade de vérification plutôt que passée
/// sous silence.
public class RegenerationParTransformationAudio implements RegenerationSequences {

    private final TransformationAudio transformation;

    @Inject
    public RegenerationParTransformationAudio(TransformationAudio transformation) {
        this.transformation = Objects.requireNonNull(transformation, "transformation");
    }

    @Override
    public List<Path> regenerer(
            Path brut, String nomOriginal, Prefixe prefixe, int frequenceAcquisitionHz, Path dossierSortie) {
        return transformation
                .transformer(brut, nomOriginal, dossierSortie, prefixe, frequenceAcquisitionHz)
                .sequences()
                .stream()
                .map(SequenceProduite::chemin)
                .toList();
    }
}
