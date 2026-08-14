package fr.univ_amu.iut.recette;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/// Consigne, pendant une séance filmée, l'instant où chaque cas de recette se joue (#3774).
///
/// ## Ce qu'elle fait, et ce qu'elle ne fait pas
///
/// Elle encadre d'un `debut` et d'une `fin` **les seuls tests qui citent un cas** ([CasDeRecette]).
/// Un test sans citation n'a pas de place dans l'index : il ne montre aucun cas, personne n'ira
/// chercher son extrait.
///
/// Elle ne filme pas, ne découpe pas, ne juge pas. Elle pose des instants, et c'est tout.
///
/// ## ⚠️ Quand elle est active, et pourquoi jamais autrement
///
/// Deux conditions, toutes deux posées par le seul profil `recette-filmee` :
///
/// 1. la détection automatique des extensions est activée, sans quoi cette classe n'est même pas
///    chargée ;
/// 2. [JournalDesReperes#PROPRIETE] dit où écrire, sans quoi elle ne fait rien.
///
/// La seconde suffirait ; la première existe pour qu'un `mvn test` ordinaire ne charge **rien** de
/// neuf, et qu'aucune extension venue d'une dépendance ne s'active au passage. C'est la même
/// prudence que les trois propriétés `recette.*` du `pom.xml`, inertes par défaut.
public class ReperesDeSeance implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    /// Résolu à la construction, et non dans un champ statique : le journal doit pouvoir être
    /// redirigé d'une exécution à l'autre, faute de quoi ses propres tests ne pourraient pas
    /// l'observer ailleurs que là où le profil l'envoie.
    private final Optional<JournalDesReperes> journal = JournalDesReperes.depuisLaPropriete();

    @Override
    public void beforeTestExecution(ExtensionContext contexte) {
        noter(contexte, JournalDesReperes.Borne.DEBUT);
    }

    @Override
    public void afterTestExecution(ExtensionContext contexte) {
        noter(contexte, JournalDesReperes.Borne.FIN);
    }

    private void noter(ExtensionContext contexte, JournalDesReperes.Borne borne) {
        journal.ifPresent(ou ->
                cas(contexte).ifPresent(cas -> ou.note(borne, nomDuTest(contexte), cas, System.currentTimeMillis())));
    }

    private static Optional<List<String>> cas(ExtensionContext contexte) {
        return contexte.getTestMethod()
                .map(methode -> methode.getAnnotation(CasDeRecette.class))
                .map(annotation -> List.of(annotation.value()));
    }

    private static String nomDuTest(ExtensionContext contexte) {
        String methode = contexte.getTestMethod().map(Method::getName).orElse("?");
        return contexte.getRequiredTestClass().getSimpleName() + "." + methode;
    }
}
