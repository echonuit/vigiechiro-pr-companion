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
import org.opentest4j.TestAbortedException;

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
///
/// Avec elle, seuls les tests qui **citent un cas** sont filmés. L'index se lit par cas, et un
/// clip qu'aucun cas ne nomme est un clip que l'index ne sait pas ranger. `-Drecette.film.tout`
/// lève cette restriction, pour le débogage d'un test qui ne porte pas encore de cas.
public final class EnregistreurDeFilm implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final String PROPRIETE = "recette.film";

    /// L'échappatoire : filmer AUSSI les tests qui ne citent aucun cas. Présence suffit.
    private static final String PROPRIETE_TOUT = "recette.film.tout";
    private static final String CLE = "enregistrement";
    /// La cadence du film, pilotable comme la taille l'est déjà.
    ///
    /// Elle est réglable parce qu'elle a servi à ÉPROUVER le dispositif : poussée à 50, soit cinq
    /// fois la valeur nominale, la caméra a composé 710 images sur un parcours sans en perdre une
    /// seule. C'est ce qui a clos la question de l'allocation.
    private static final int IMAGES_PAR_SECONDE = Integer.getInteger("recette.film.cadence", 10);
    private static final double DUREE_DU_CARTON = 2.0;

    private static final ExtensionContext.Namespace ESPACE =
            ExtensionContext.Namespace.create(EnregistreurDeFilm.class);

    @Override
    public void beforeTestExecution(ExtensionContext contexte) throws Exception {
        if (!filmeur()) {
            return;
        }
        List<String> cas = casCites(contexte);
        // ⚠️ Le refus se prend ICI, avant tout le reste. Plus bas, l'enregistrement crée son dossier
        // puis lance l'encodeur : refuser après laisserait un dossier, et parfois un fichier vide,
        // pour un test qu'on a précisément décidé de ne pas filmer.
        if (!aFilmer(cas, tout())) {
            return;
        }
        Dimensions taille = Dimensions.demandees();
        String test = nomDuTest(contexte);

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

        // Un test ABANDONNÉ n'indexe rien. Le clip existe - il a filmé ce qui s'est passé avant
        // l'abandon - mais il ne montre pas le geste que ses cas décrivent, et l'indexer les ferait
        // passer pour couverts. C'est le faux vert le plus facile à produire ici : une case cochée
        // par un clip qui ne montre pas son cas.
        //
        // Un test en ÉCHEC, lui, indexe : son clip montre le geste ET son défaut, et c'est
        // justement celui-là qu'on veut regarder (`failure.ignore` est là pour ça).
        if (contexte.getExecutionException()
                .filter(TestAbortedException.class::isInstance)
                .isPresent()) {
            System.out.printf(
                    "  film : %s · ABANDONNÉ, aucun cas indexé - le geste n'a pas eu lieu%n",
                    bilan.fichier().getFileName());
            return;
        }

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

    /// Faut-il filmer ce test ?
    ///
    /// Un tournage complet est ce que ce banc sert à produire, et il s'indexe par CAS : un clip qu'aucun cas
    /// ne nomme est un clip que l'index ne sait pas ranger, et que personne n'ouvrira. Mesuré sur
    /// une sonde : vingt clips de carton pour une seule classe de service.
    ///
    /// L'échappatoire existe pour le débogage d'un test qui ne porte pas encore de cas. Elle se
    /// **demande**, elle ne s'obtient pas par défaut.
    static boolean aFilmer(List<String> casCites, boolean tout) {
        return tout || !casCites.isEmpty();
    }

    private static boolean tout() {
        return System.getProperty(PROPRIETE_TOUT) != null;
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
