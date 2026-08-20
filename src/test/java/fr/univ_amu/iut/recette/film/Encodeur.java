package fr.univ_amu.iut.recette.film;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Reçoit des images et rend un fichier.
public interface Encodeur extends AutoCloseable {

    void ajouter(BufferedImage image) throws IOException;

    @Override
    void close() throws IOException;

    /// L'encodeur par défaut : le ffmpeg DÉJÀ présent sur le banc, nourri en rawvideo par son
    /// entrée standard.
    ///
    /// On garde donc libx264 et les réglages du script, mais l'orchestration cesse de vivre dans
    /// un tube nommé et un `printf q >&3`. La difficulté que cela contournait n'existe plus :
    /// ffmpeg s'arrête quand son entrée se ferme, et il n'y a plus d'instant d'arrêt à relever
    /// puisqu'il n'y a plus de `t0` à calculer.
    final class VersFfmpeg implements Encodeur {

        private final Process ffmpeg;
        private final OutputStream entree;
        private final Path fichier;

        public VersFfmpeg(Path fichier, int largeur, int hauteur, int imagesParSeconde) throws IOException {
            this.fichier = fichier;
            ProcessBuilder commande = new ProcessBuilder(
                    resoudre("ffmpeg").toString(),
                    "-loglevel",
                    "error",
                    "-f",
                    "rawvideo",
                    "-pix_fmt",
                    "bgr24",
                    "-s",
                    largeur + "x" + hauteur,
                    "-r",
                    String.valueOf(imagesParSeconde),
                    "-i",
                    "-",
                    "-an",
                    "-c:v",
                    "libx264",
                    "-preset",
                    "veryfast",
                    "-crf",
                    "22",
                    "-pix_fmt",
                    "yuv420p",
                    "-y",
                    fichier.toString());
            // Les plaintes de ffmpeg remontent dans la sortie de Maven. Les avaler rendrait un
            // fichier absent sans un mot, ce que ce banc combat par ailleurs.
            commande.redirectError(ProcessBuilder.Redirect.INHERIT);
            this.ffmpeg = commande.start();
            this.entree = ffmpeg.getOutputStream();
        }

        /// Le chemin ABSOLU du programme, cherché sur `PATH`.
        ///
        /// Deux raisons, et la seconde est celle qui compte au quotidien.
        ///
        /// Lancer `ffmpeg` par son nom nu laisse `PATH` décider de ce qui s'exécute : un programme
        /// homonyme placé plus tôt dans `PATH` serait lancé à sa place, ce que l'analyse de code
        /// signale à juste titre.
        ///
        /// Surtout, un `ffmpeg` absent produirait sinon un `IOException` du système, au moment
        /// d'écrire la première image, c'est-à-dire loin de la cause. Ici le refus est immédiat et
        /// il NOMME ce qui manque : un dispositif qui ne peut pas travailler le dit.
        ///
        /// `recette.ffmpeg` permet de désigner un binaire précis quand il n'est pas sur `PATH`.
        static Path resoudre(String programme) throws IOException {
            String impose = System.getProperty("recette." + programme);
            if (impose != null) {
                Path chemin = Path.of(impose);
                if (!Files.isExecutable(chemin)) {
                    throw new IOException(
                            "recette." + programme + " désigne " + chemin + ", qui n'est pas un exécutable.");
                }
                return chemin;
            }
            // Sous Windows, le nom nu ne suffit pas : on essaie les suffixes de PATHEXT.
            List<String> noms = new ArrayList<>();
            noms.add(programme);
            String suffixes = System.getenv("PATHEXT");
            if (suffixes != null) {
                for (String suffixe : suffixes.split(File.pathSeparator.equals(";") ? ";" : ":")) {
                    if (!suffixe.isBlank()) {
                        noms.add(programme + suffixe.toLowerCase(Locale.ROOT));
                    }
                }
            }
            String chemins = System.getenv("PATH");
            for (String dossier : (chemins == null ? "" : chemins).split(File.pathSeparator)) {
                if (dossier.isBlank()) {
                    continue;
                }
                for (String nom : noms) {
                    Path candidat = Path.of(dossier, nom);
                    if (Files.isExecutable(candidat) && !Files.isDirectory(candidat)) {
                        return candidat;
                    }
                }
            }
            throw new IOException(programme + " est introuvable sur PATH. Le banc filmé en a besoin"
                    + " pour encoder. Installez-le, ou désignez-le par -Drecette." + programme
                    + "=<chemin>.");
        }

        /// L'image DOIT être en `TYPE_3BYTE_BGR` : son tampon est alors exactement le
        /// bgr24 attendu, et se pousse sans conversion ni copie.
        @Override
        public void ajouter(BufferedImage image) throws IOException {
            if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
                throw new IOException("image en type " + image.getType() + ", bgr24 attendu");
            }
            entree.write(((DataBufferByte) image.getRaster().getDataBuffer()).getData());
        }

        @Override
        public void close() throws IOException {
            entree.close();
            try {
                int code = ffmpeg.waitFor();
                if (code != 0) {
                    throw new IOException("ffmpeg a rendu " + code + " pour " + fichier);
                }
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new IOException("encodage interrompu", interruption);
            }
        }
    }
}
