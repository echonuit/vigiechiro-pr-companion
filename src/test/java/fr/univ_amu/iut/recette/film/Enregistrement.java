package fr.univ_amu.iut.recette.film;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;

/// Une séance, bornée par UN test.
///
/// C'est ici que disparaît la partie la plus délicate du script. Le journal des repères, le
/// calcul de `t0` par « arrêt moins durée », la découpe par `-ss/-to` et le contrôle
/// de couverture existaient tous pour recaler deux horloges : celle du journal et celle du film.
/// Un fichier par test les rend sans objet, puisqu'il n'y a plus qu'une horloge.
public final class Enregistrement {

    /// Le jeton de fin. Comparé par IDENTITÉ, jamais par contenu.
    private static final BufferedImage FIN = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);

    private static final int PROFONDEUR_DE_FILE = 60;

    public record Bilan(Path fichier, int imagesEcrites, int imagesPerdues, boolean fenetreVue) {
        public String resume() {
            return String.format(
                    "%d image(s)%s, %s",
                    imagesEcrites,
                    imagesPerdues > 0 ? " (" + imagesPerdues + " perdue(s))" : "",
                    fenetreVue ? "une fenêtre a paru" : "aucune fenêtre");
        }
    }

    private final Path fichier;
    private final int largeur;
    private final int hauteur;
    private final int imagesParSeconde;
    private final BufferedImage carton;
    private final double dureeDuCarton;

    private final BlockingQueue<BufferedImage> file = new ArrayBlockingQueue<>(PROFONDEUR_DE_FILE);
    private final AtomicInteger ecrites = new AtomicInteger();

    private CameraDeScene camera;
    private Thread scribe;
    private Encodeur encodeur;
    private volatile IOException panne;

    public Enregistrement(
            Path fichier, int largeur, int hauteur, int imagesParSeconde, BufferedImage carton, double dureeDuCarton) {
        this.fichier = fichier;
        this.largeur = largeur;
        this.hauteur = hauteur;
        this.imagesParSeconde = imagesParSeconde;
        this.carton = carton;
        this.dureeDuCarton = dureeDuCarton;
    }

    public void demarrer() throws IOException {
        Files.createDirectories(fichier.getParent());
        encodeur = new Encodeur.VersFfmpeg(fichier, largeur, hauteur, imagesParSeconde);

        scribe = new Thread(this::ecrire, "film-" + fichier.getFileName());
        scribe.setDaemon(true);
        scribe.start();

        // Le carton passe par la MÊME file et le MÊME encodeur que les images de la séance : il
        // n'y a donc rien à recoller, et rien qui puisse discorder.
        if (carton != null) {
            for (int i = 0; i < Math.max(1, (int) (dureeDuCarton * imagesParSeconde)); i++) {
                deposerSansPerdre(carton);
            }
        }

        camera = new CameraDeScene(largeur, hauteur, imagesParSeconde, file);
        surLeFilDeFx(camera::start);
    }

    public Bilan arreter() throws IOException {
        surLeFilDeFx(camera::stop);
        deposerSansPerdre(FIN);
        try {
            scribe.join();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
        encodeur.close();
        if (panne != null) {
            throw panne;
        }
        return new Bilan(fichier, ecrites.get(), camera.imagesPerdues(), camera.uneFenetreAParu());
    }

    private void ecrire() {
        try {
            while (true) {
                BufferedImage image = file.take();
                if (image == FIN) {
                    return;
                }
                encodeur.ajouter(image);
                ecrites.incrementAndGet();
            }
        } catch (IOException echec) {
            panne = echec;
            file.clear();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }

    /// Le carton et le jeton de fin ne se perdent pas : on attend la place plutôt que de céder.
    private void deposerSansPerdre(BufferedImage image) {
        try {
            file.put(image);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }

    static void surLeFilDeFx(Runnable travail) {
        if (Platform.isFxApplicationThread()) {
            travail.run();
            return;
        }
        CountDownLatch fait = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                travail.run();
            } finally {
                fait.countDown();
            }
        });
        try {
            fait.await();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
    }
}
