package fr.univ_amu.iut.recette.film;

import fr.univ_amu.iut.recette.CasDeRecette;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/// Démarre un enregistrement au début de chaque test et l'arrête à sa fin.
///
/// Le choix de [BeforeTestExecutionCallback] et non de `BeforeEachCallback` n'est
/// pas indifférent : il s'exécute APRÈS tous les `@BeforeEach`, donc après le démarrage de
/// l'application par TestFX. La caméra trouve ainsi une scène montée, et le clip ne s'ouvre pas
/// sur l'amorçage du test.
///
/// Emploi :
/// <pre>
/// &#64;ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class})
/// class ConnexionModaleViewTest { ... }
/// </pre>
///
/// Sans la propriété `-Drecette.film`, l'extension ne fait RIEN : une classe annotée
/// garde donc exactement le comportement qu'elle a aujourd'hui hors séance filmée.
public final class EnregistreurDeFilm implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final String PROPRIETE = "recette.film";
    private static final String CLE = "enregistrement";
    private static final int IMAGES_PAR_SECONDE = 10;
    private static final double DUREE_DU_CARTON = 2.0;

    private static final ExtensionContext.Namespace ESPACE =
            ExtensionContext.Namespace.create(EnregistreurDeFilm.class);

    @Override
    public void beforeTestExecution(ExtensionContext contexte) throws Exception {
        if (!filmeur()) {
            return;
        }
        Dimensions taille = Dimensions.demandees();
        String test = nomDuTest(contexte);
        List<String> cas = casCites(contexte);

        BufferedImage carton = CartonDeTitre.dessiner(
                taille.largeur(),
                taille.hauteur(),
                cas.isEmpty() ? "(aucun cas cité)" : String.join(", ", cas),
                libelleDe(cas),
                test);

        Enregistrement seance = new Enregistrement(
                dossier().resolve(test + ".mp4"),
                taille.largeur(),
                taille.hauteur(),
                IMAGES_PAR_SECONDE,
                carton,
                DUREE_DU_CARTON);
        seance.demarrer();
        contexte.getStore(ESPACE).put(CLE, seance);
    }

    @Override
    public void afterTestExecution(ExtensionContext contexte) throws Exception {
        Enregistrement seance = contexte.getStore(ESPACE).remove(CLE, Enregistrement.class);
        if (seance == null) {
            return;
        }
        Enregistrement.Bilan bilan = seance.arreter();
        System.out.printf("  film : %s · %s%n", bilan.fichier().getFileName(), bilan.resume());

        IndexDesCas index = index(contexte);
        String test = nomDuTest(contexte);
        for (String cas : casCites(contexte)) {
            index.ajouter(new IndexDesCas.Ligne(
                    cas, test, bilan.fichier().getFileName().toString(), bilan.fenetreVue()));
        }
    }

    // ---------------------------------------------------------------------------------------

    private static boolean filmeur() {
        return System.getProperty(PROPRIETE) != null;
    }

    private static Path dossier() {
        return Path.of(System.getProperty("recette.film.dossier", "target/recette/clips"));
    }

    private static String nomDuTest(ExtensionContext contexte) {
        String classe = contexte.getRequiredTestClass().getSimpleName();
        return classe + "." + contexte.getRequiredTestMethod().getName();
    }

    /// Les cas cités par le test, tels que [CasDeRecette] les porte : une même annotation peut en
    /// nommer plusieurs.
    private static List<String> casCites(ExtensionContext contexte) {
        Method methode = contexte.getRequiredTestMethod();
        CasDeRecette annotation = methode.getAnnotation(CasDeRecette.class);
        return annotation == null ? List.of() : List.of(annotation.value());
    }

    private static String libelleDe(List<String> cas) {
        if (cas.isEmpty()) {
            return "";
        }
        try {
            LibelleDesCas recueil =
                    LibelleDesCas.depuis(Path.of(System.getProperty("recette.sessions", "dev-docs/recette/sessions")));
            return recueil.de(cas.get(0)).orElse("");
        } catch (IOException echec) {
            // Un carton sans phrase vaut mieux qu'un clip qui n'existe pas.
            return "";
        }
    }

    private static IndexDesCas index(ExtensionContext contexte) {
        return contexte.getRoot()
                .getStore(ExtensionContext.Namespace.GLOBAL)
                .getOrComputeIfAbsent(
                        IndexDesCas.class, cle -> new IndexDesCas(dossier().resolve("index.md")), IndexDesCas.class);
    }

    /// La taille du film. Paire, parce que yuv420p n'accepte rien d'autre.
    private record Dimensions(int largeur, int hauteur) {
        static Dimensions demandees() {
            String demande = System.getProperty("recette.film.taille", "1280x900");
            String[] parts = demande.split("x");
            return new Dimensions(pair(Integer.parseInt(parts[0])), pair(Integer.parseInt(parts[1])));
        }

        private static int pair(int valeur) {
            return valeur - (valeur % 2);
        }
    }
}
