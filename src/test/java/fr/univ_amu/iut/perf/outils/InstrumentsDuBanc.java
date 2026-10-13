package fr.univ_amu.iut.perf.outils;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.perf.outils.DatationDesEcarts.EcartDate;
import fr.univ_amu.iut.recette.Attente;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.testfx.util.WaitForAsyncUtils;

/// Les instruments du banc de l'IHM (#105) : le chronomètre au pulse, la sonde des images, le relevé.
///
/// Séparés du banc pour qu'un autre banc d'écran les reprenne sans hériter de ses scénarios. Ils ne
/// connaissent aucun écran : ils mesurent le fil JavaFX et ce qu'une condition dit de la scène.
final class InstrumentsDuBanc {

    static final long DELAI_GESTE_MS = 120_000;
    private static final long MIO = 1024L * 1024L;
    private static final double SEUIL_GEL_MS = 200;

    /// Un écart se date dès 100 ms, la moitié du seuil : ce qui approche le seuil sans le franchir dit
    /// déjà où le fil JavaFX peine, et un tir sur deux le franchira au même endroit.
    private static final double SEUIL_DATATION_MS = 100;

    /// Les images qu'on laisse passer après un travail du fil JavaFX avant de relever la sonde.
    static final int IMAGES_DE_GARDE = 2;

    private InstrumentsDuBanc() {}

    /// Déclenche `action` sur le fil JavaFX et rend, en millisecondes, le temps jusqu'au premier pulse
    /// après mise en page où `fini` est vrai. L'écouteur est posé dans le même passage du fil que
    /// l'action : aucun pulse ne peut s'intercaler entre les deux.
    ///
    /// `intrus` est relu à chaque pulse : dès qu'il nomme quelque chose (une modale, une question), le
    /// chronomètre échoue au lieu d'attendre. Un banc ne dépend jamais d'un clic.
    static double chrono(Stage stage, Runnable action, BooleanSupplier fini, Supplier<String> intrus) throws Exception {
        CompletableFuture<Double> resultat = new CompletableFuture<>();
        Runnable[] ecouteur = new Runnable[1];
        Scene[] scene = new Scene[1];
        Platform.runLater(() -> {
            try {
                scene[0] = stage.getScene();
                long debut = System.nanoTime();
                ecouteur[0] = () -> {
                    String arret = intrus.get();
                    if (!resultat.isDone() && arret != null) {
                        resultat.completeExceptionally(new AssertionError("le banc s'arrête : " + arret));
                    } else if (!resultat.isDone() && fini.getAsBoolean()) {
                        resultat.complete((System.nanoTime() - debut) / 1e6);
                    }
                };
                scene[0].addPostLayoutPulseListener(ecouteur[0]);
                action.run();
            } catch (RuntimeException echec) {
                resultat.completeExceptionally(echec);
            }
        });
        try {
            return resultat.get(DELAI_GESTE_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException echec) {
            if (echec.getCause() instanceof AssertionError arret) {
                throw arret;
            }
            throw echec;
        } finally {
            Platform.runLater(() -> {
                if (scene[0] != null && ecouteur[0] != null) {
                    scene[0].removePostLayoutPulseListener(ecouteur[0]);
                }
            });
        }
    }

    static void bloquerLeFil(long ms) {
        long fin = System.nanoTime() + ms * 1_000_000;
        while (System.nanoTime() < fin) {
            Thread.onSpinWait();
        }
    }

    /// Le témoin de la datation (#6175) : bloque le fil JavaFX `ms` millisecondes à un instant relevé, et
    /// rend ce que la sonde en a daté. La sonde tourne déjà ; elle est remise à zéro, et le plus long
    /// écart de trente images au calme donne la durée d'une image sur ce poste.
    static BlocageDate bloquerEtDater(SondeImages sonde, long ms) throws Exception {
        reinitialiser(sonde);
        attendreLesImages(sonde, 30);
        long uneImageNs = Math.round(sonde.relever().maxMs() * 1e6);
        reinitialiser(sonde);
        attendreLesImages(sonde, 3);
        long[] bornes = new long[2];
        surLeFil(() -> {
            bornes[0] = System.nanoTime();
            bloquerLeFil(ms);
            bornes[1] = System.nanoTime();
            return null;
        });
        attendreLesImages(sonde, sonde.relever().images() + IMAGES_DE_GARDE);
        return new BlocageDate(bornes[0], bornes[1], uneImageNs, sonde.relever().dates());
    }

    /// Attend que la sonde ait vu `images` images depuis sa remise à zéro. Un écart ne se compte qu'à
    /// l'image qui le ferme : relever juste après un travail du fil JavaFX, c'est ne pas voir le sien.
    static void attendreLesImages(SondeImages sonde, long images) {
        Attente.que(
                () -> {
                    try {
                        return sonde.relever().images() >= images;
                    } catch (Exception echec) {
                        throw new IllegalStateException(echec);
                    }
                },
                "la sonde n'a pas vu " + images + " images",
                DELAI_GESTE_MS);
    }

    private static void reinitialiser(SondeImages sonde) throws Exception {
        surLeFil(() -> {
            sonde.reinitialiser();
            return null;
        });
    }

    static <T> T surLeFil(Callable<T> lecture) throws Exception {
        return WaitForAsyncUtils.asyncFx(lecture).get(DELAI_GESTE_MS, TimeUnit.MILLISECONDS);
    }

    /// Heap occupé juste après un GC complet, en Mo : ce que `jcmd <pid> GC.run` puis `GC.heap_info`
    /// rendraient. Sous G1, `System.gc()` est un GC complet tant que `ExplicitGCInvokesConcurrent`
    /// n'est pas posé, et le banc ne le pose pas. Un GC complet garde ce que seules des références douces
    /// tiennent, tant que le heap est loin d'être plein : `-XX:SoftRefLRUPolicyMSPerMB=0` les fait rendre
    /// à chaque GC (#6170).
    static long heapApresGc() {
        System.gc();
        System.gc();
        Runtime jvm = Runtime.getRuntime();
        return (jvm.totalMemory() - jvm.freeMemory()) / MIO;
    }

    static void supprimer(Path racine) throws IOException {
        if (!Files.exists(racine)) {
            return;
        }
        try (Stream<Path> chemins = Files.walk(racine)) {
            for (Path chemin : chemins.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(chemin);
            }
        }
    }

    // ------------------------------------------------------------------ sonde et relevé

    /// Le confirmateur d'un banc : il note chaque question posée et répond « non ». Une question notée
    /// arrête le chronomètre et l'attente d'import, avec son texte : l'écran a voulu une modale.
    static final class Questions implements Confirmateur {
        private final List<String> posees = new CopyOnWriteArrayList<>();

        @Override
        public boolean confirmer(String message) {
            posees.add(message);
            return false;
        }

        String premiere() {
            return posees.isEmpty() ? null : "l'écran a demandé une confirmation : " + posees.getFirst();
        }

        void oublier() {
            posees.clear();
        }
    }

    /// L'écart entre deux pulses, relevé à chaque image : l'extrait de `docs/benchmarks/README.md`,
    /// qui garde ses chiffres au lieu de les imprimer. Chaque écart de plus de 100 ms garde aussi les
    /// instants de ses deux images, pour se situer parmi les jalons d'un import (#6175).
    static final class SondeImages extends AnimationTimer {
        private long precedent;
        private long images;
        private long maxNs;
        private final List<Long> gels = new ArrayList<>();
        private final List<EcartDate> dates = new ArrayList<>();

        @Override
        public void handle(long maintenant) {
            if (precedent != 0) {
                long ecart = maintenant - precedent;
                maxNs = Math.max(maxNs, ecart);
                if (ecart > SEUIL_GEL_MS * 1e6) {
                    gels.add(ecart);
                }
                if (ecart > SEUIL_DATATION_MS * 1e6) {
                    dates.add(new EcartDate(precedent, maintenant));
                }
            }
            precedent = maintenant;
            images++;
        }

        void reinitialiser() {
            precedent = 0;
            images = 0;
            maxNs = 0;
            gels.clear();
            dates.clear();
        }

        Gel relever() throws Exception {
            return surLeFil(() ->
                    new Gel(images, maxNs / 1e6, gels.stream().map(n -> n / 1e6).toList(), List.copyOf(dates)));
        }
    }

    /// Ce que la sonde a vu sur une période : le nombre d'images, l'écart maximal, les écarts de plus
    /// de 200 ms, et les écarts de plus de 100 ms avec leurs instants.
    record Gel(long images, double maxMs, List<Double> gelsMs, List<EcartDate> dates) {}

    /// Un blocage du fil JavaFX posé par le banc, et les écarts que la sonde a datés autour de lui.
    ///
    /// @param debutNs l'instant juste avant le blocage
    /// @param finNs l'instant juste après
    /// @param uneImageNs le plus long écart entre deux images au calme, juste avant
    /// @param dates les écarts datés par la sonde
    record BlocageDate(long debutNs, long finNs, long uneImageNs, List<EcartDate> dates) {

        /// De combien l'écart daté commence avant le blocage, en ms : au plus une image.
        double avanceMs() {
            return (debutNs - dates.getFirst().debutNs()) / 1e6;
        }

        /// De combien l'écart daté finit après le blocage, en ms : au plus une image.
        double retardMs() {
            return (dates.getFirst().finNs() - finNs) / 1e6;
        }
    }

    /// Les lignes d'un banc, imprimées et ajoutées à `releve.tsv` sous la racine du banc.
    ///
    /// Le relevé s'ouvre sur l'échelle à laquelle le banc a dessiné (#6177) : ses chiffres en dépendent,
    /// et ils ne se comparent d'un poste à l'autre qu'avec elle.
    static final class Releve {
        private final Path racine;
        private final String titre;
        private final String echelle;
        private final List<String> lignes = new ArrayList<>();

        Releve(Path racine, String titre, EchelleDuRendu echelle) {
            this.racine = racine;
            this.titre = titre;
            this.echelle = echelle.ligne();
        }

        void noter(String geste, int rang, double ms) {
            String role = rang == 0 ? "jeté (premier)" : "relevé " + rang;
            lignes.add(String.format(Locale.ROOT, "%s\t%s\t%s\t%.1f ms", titre, geste, role, ms));
        }

        void noterGel(String quoi, Gel gel) {
            lignes.add(String.format(
                    Locale.ROOT,
                    "%s\t%s\t%d images\técart max %.1f ms, %d au-delà de 200 ms",
                    titre,
                    quoi,
                    gel.images(),
                    gel.maxMs(),
                    gel.gelsMs().size()));
        }

        void noterImport(int nuit, double dureeS, Gel gel) {
            lignes.add(String.format(
                    Locale.ROOT,
                    "%s\tpendant l'import de la nuit %d\t%.1f s, %d images\técart max %.1f ms, au-delà de 200 ms : %s",
                    titre,
                    nuit,
                    dureeS,
                    gel.images(),
                    gel.maxMs(),
                    enMs(gel.gelsMs())));
        }

        void noterNavigation(int nuit, Gel gel) {
            lignes.add(String.format(
                    Locale.ROOT,
                    "%s\tnavigation après la nuit %d\t%d images\técart max %.1f ms, au-delà de 200 ms : %s",
                    titre,
                    nuit,
                    gel.images(),
                    gel.maxMs(),
                    enMs(gel.gelsMs())));
        }

        /// Les jalons de l'import de la nuit, puis une ligne par écart de plus de 100 ms, situé parmi
        /// eux (#6175). La ligne des jalons finit sur l'heure du clic, qui recoupe le journal de la JVM.
        void noterDatation(int nuit, JalonsDeLImport jalons, Gel gel) {
            lignes.add(String.format(
                    Locale.ROOT,
                    "%s\tjalons de l'import de la nuit %d\t%s · %s",
                    titre,
                    nuit,
                    jalons.datation().jalons(),
                    jalons.contexte()));
            for (EcartDate ecart : gel.dates()) {
                lignes.add(String.format(
                        Locale.ROOT,
                        "%s\técart daté, import de la nuit %d\t%s",
                        titre,
                        nuit,
                        jalons.datation().ligne(ecart)));
            }
        }

        /// Le témoin de la datation : de combien l'écart daté déborde le blocage, de chaque côté.
        void noterTemoinDeDatation(long ms, BlocageDate temoin) {
            lignes.add(String.format(
                    Locale.ROOT,
                    "%s\tcontrôle : blocage de %d ms daté\t%.0f ms, commencé %.1f ms avant et fini %.1f ms après,"
                            + " pour une image de %.1f ms",
                    titre,
                    ms,
                    temoin.dates().getFirst().ms(),
                    temoin.avanceMs(),
                    temoin.retardMs(),
                    temoin.uneImageNs() / 1e6));
        }

        void noterHeap(String moment, long mo) {
            lignes.add(String.format(Locale.ROOT, "%s\theap après GC, %s\t%d Mo", titre, moment, mo));
        }

        private static String enMs(List<Double> gels) {
            return gels.isEmpty()
                    ? "aucun"
                    : gels.stream()
                            .map(g -> String.format(Locale.ROOT, "%.0f ms", g))
                            .collect(Collectors.joining(", "));
        }

        void ecrire() throws IOException {
            assertThat(lignes).as("un banc qui n'a rien relevé ne mesure rien").isNotEmpty();
            System.out.println("=== Banc de l'IHM (#105) : " + titre + " ===");
            BancImport.imprimerMachine();
            System.out.println("  échelle         : " + echelle);
            lignes.forEach(System.out::println);
            List<String> releve = new ArrayList<>();
            releve.add(titre + "\téchelle\t" + echelle);
            releve.addAll(lignes);
            Files.createDirectories(racine);
            Files.write(
                    racine.resolve("releve.tsv"),
                    releve,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }
    }
}
