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
/// Elle encadre d'un `debut` et d'une `fin` **chaque test de la séance**, en notant les cas qu'il
/// cite ([CasDeRecette]) quand il en cite. Le journal décrit donc la séance **entière** : c'est
/// l'index du montage, et non ce journal, qui ne retient que les cas.
///
/// Elle ne filme pas, ne découpe pas, ne juge pas. Elle pose des instants, et c'est tout.
///
/// ## Quand elle est active, et pourquoi jamais autrement
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
        journal.ifPresent(ou -> ou.note(borne, nomDuTest(contexte), cas(contexte), System.currentTimeMillis()));
    }

    /// Les cas cités, ou aucun.
    ///
    /// Un test **sans** citation est consigné lui aussi, la colonne des cas restant vide. Ce
    /// n'était pas le cas d'abord, et la première séance filmée réelle a montré pourquoi il le
    /// faut : `ConnexionModaleViewTest` compte dix tests dont trois annotés, et les sept autres
    /// ouvrent des fenêtres. Le montage, qui vérifie que ce qui apparaît à l'écran tombe dans une
    /// plage connue, jugeait donc **hors sujet** les cinq sixièmes de ce qu'il voyait, et refusait
    /// un alignement correct.
    ///
    /// Le journal décrit désormais la séance entière ; c'est l'index qui ne retient que les cas.
    private static List<String> cas(ExtensionContext contexte) {
        return contexte.getTestMethod()
                .map(methode -> methode.getAnnotation(CasDeRecette.class))
                .map(annotation -> List.of(annotation.value()))
                .orElseGet(List::of);
    }

    private static String nomDuTest(ExtensionContext contexte) {
        String methode = contexte.getTestMethod().map(Method::getName).orElse("?");
        return contexte.getRequiredTestClass().getSimpleName() + "." + methode;
    }
}
