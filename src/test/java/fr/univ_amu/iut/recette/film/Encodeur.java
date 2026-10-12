package fr.univ_amu.iut.recette.film;

import fr.univ_amu.iut.fixture.SystemeDeFichiers;
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
    ///
    /// La dernière image d'un clip ne dépend plus de la place de la dernière image clé (#6135) : les
    /// clés tombent toutes les [#PAS_DES_CLES] images, et [#close] recopie la dernière image.
    final class VersFfmpeg implements Encodeur {

        /// Le pas des images clés, à pas fixe et sans coupure de scène : 2,5 s à 10 i/s. Choisi par le
        /// porteur sur la mesure de #6135, entre la taille (×3,2) et la fin immobile ajoutée.
        static final int PAS_DES_CLES = 25;

        private final Process ffmpeg;
        private final OutputStream entree;
        private final Path fichier;
        private byte[] derniere;
        private int ecrites;

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
                    "-g",
                    String.valueOf(PAS_DES_CLES),
                    "-keyint_min",
                    String.valueOf(PAS_DES_CLES),
                    "-sc_threshold",
                    "0",
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
                if (!estExecutable(chemin, vuePosixDisponible(), System.getenv("PATHEXT"))) {
                    throw new IOException(
                            "recette." + programme + " désigne " + chemin + ", qui n'est pas un exécutable.");
                }
                return chemin;
            }
            // Sous Windows, le nom nu ne suffit pas : on essaie les suffixes de PATHEXT.
            List<String> noms = new ArrayList<>();
            noms.add(programme);
            String suffixes = System.getenv("PATHEXT");
            for (String suffixe : decouperPathext(suffixes)) {
                noms.add(programme + suffixe);
            }
            String chemins = System.getenv("PATH");
            for (String dossier : (chemins == null ? "" : chemins).split(File.pathSeparator)) {
                if (dossier.isBlank()) {
                    continue;
                }
                for (String nom : noms) {
                    Path candidat = Path.of(dossier, nom);
                    if (estExecutable(candidat, vuePosixDisponible(), suffixes)) {
                        return candidat;
                    }
                }
            }
            throw new IOException(programme + " est introuvable sur PATH. Le banc filmé en a besoin"
                    + " pour encoder. Installez-le, ou désignez-le par -Drecette." + programme
                    + "=<chemin>.");
        }

        /// La vue POSIX est-elle là ? C'est elle qui décide de la façon dont on juge l'exécutabilité.
        ///
        /// Déléguée à la couture depuis #5437 : la même question s'écrivait en cinq endroits, et une
        /// copie qui diverge de ses soeurs ne fait rougir personne.
        static boolean vuePosixDisponible() {
            return SystemeDeFichiers.posixDisponible();
        }

        /// Ce chemin désigne-t-il un exécutable ?
        ///
        /// `Files.isExecutable` ne tranche rien hors POSIX : sous Windows Server 2025 il rend `true`
        /// pour tout fichier existant, sans suffixe comme avec `.txt` (run 32942466901). Le refus d'un
        /// chemin imposé s'y réduisait à « le fichier existe ». Hors POSIX la question porte donc sur
        /// le suffixe, celui que `PATHEXT` déclare, comme la recherche sur `PATH` juste au-dessus.
        ///
        /// Ses deux dépendances sont en paramètre : sans cela un poste Linux ne jouerait jamais la
        /// branche Windows, couture que l'ADR 3802 demande (#4522).
        static boolean estExecutable(Path chemin, boolean vuePosix, String pathext) {
            if (!Files.isRegularFile(chemin)) {
                return false;
            }
            if (vuePosix) {
                return Files.isExecutable(chemin);
            }
            String nom = chemin.getFileName().toString().toLowerCase(Locale.ROOT);
            return decouperPathext(pathext).stream().anyMatch(nom::endsWith);
        }

        /// Les suffixes de `PATHEXT`, en minuscules. Une valeur absente rend une liste vide, ce qui
        /// ferme [#estExecutable] hors POSIX plutôt que de l'ouvrir à tout.
        static List<String> decouperPathext(String pathext) {
            List<String> suffixes = new ArrayList<>();
            for (String suffixe : (pathext == null ? "" : pathext).split(";")) {
                if (!suffixe.isBlank()) {
                    suffixes.add(suffixe.trim().toLowerCase(Locale.ROOT));
                }
            }
            return suffixes;
        }

        /// L'image DOIT être en `TYPE_3BYTE_BGR` : son tampon est alors exactement le
        /// bgr24 attendu, et se pousse sans conversion ni copie.
        @Override
        public void ajouter(BufferedImage image) throws IOException {
            if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
                throw new IOException("image en type " + image.getType() + ", bgr24 attendu");
            }
            derniere = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            entree.write(derniere);
            ecrites++;
        }

        /// Combien de copies de la dernière image suivent les `ecrites` images d'une séance.
        ///
        /// Jusqu'au multiple du pas, puis un pas entier : la dernière clé tombe alors sur une copie,
        /// et toujours au même rang avant la fin. Avec un pas entier seul, ce rang varie, et la
        /// quantification que x264 donne à la clé varie avec lui (0,354 % mesuré à ce pas, au lieu de 0).
        static int copiesDeLaFin(int ecrites, int pas) {
            return ecrites == 0 ? 0 : (pas - ecrites % pas) % pas + pas;
        }

        @Override
        public void close() throws IOException {
            for (int i = copiesDeLaFin(ecrites, PAS_DES_CLES); i > 0; i--) {
                entree.write(derniere);
            }
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
