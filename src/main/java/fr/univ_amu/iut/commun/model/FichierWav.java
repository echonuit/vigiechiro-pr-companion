package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/// Lecture / écriture d'un fichier WAV PCM, en `java.base` **pur** (aucun recours à
/// `javax.sound.sampled`).
///
/// **Pourquoi pas `javax.sound.sampled` ?** Cette API vit dans le module `java.desktop`, que le
/// module applicatif `vigiechiro` ne `requires` pas (et `module-info.java` est gelé). Le code de
/// production compile sur le *module path* : un import `javax.sound.sampled.*` ne compilerait pas.
/// Surtout, le parsing manuel de l'en-tête RIFF donne un contrôle **au bit près** sur les octets
/// écrits, indispensable au déterminisme R11 (l'API standard peut réordonner/ajouter des
/// sous-chunks selon l'implémentation).
///
/// Lecture robuste : on balaie les sous-chunks RIFF pour localiser `"fmt "` et `"data"` (en
/// tolérant des chunks intercalés comme `LIST`/`fact` et le padding de word-alignment). Écriture
/// canonique : en-tête fixe de 44 octets (`RIFF/WAVE/fmt /data`), little-endian, donc
/// **reproductible** pour des octets PCM identiques.
///
/// @param nombreCanaux nombre de canaux (1 = mono, attendu pour les ultrasons VigieChiro)
/// @param frequenceEchantillonnageHz fréquence d'échantillonnage en Hz
/// @param bitsParEchantillon profondeur en bits (16 attendu)
/// @param donneesPcm octets PCM bruts (le chunk `data`, sans en-tête)
public record FichierWav(int nombreCanaux, int frequenceEchantillonnageHz, int bitsParEchantillon, byte[] donneesPcm) {

    private static final int TAILLE_ENTETE = 44;
    private static final short FORMAT_PCM = 1;

    /// Étiquettes de chunks RIFF (extraites en constantes : réutilisées par lire / ecrire, sans quoi PMD
    /// signale des littéraux dupliqués).
    private static final String TAG_RIFF = "RIFF";

    private static final String TAG_WAVE = "WAVE";

    private static final String TAG_FMT = "fmt ";

    /// Placeholder « taille de données inconnue » (0xFFFFFFFF) écrit par certains enregistreurs en flux :
    /// signifie « jusqu'à la fin du fichier », à ne pas confondre avec une troncature (#156).
    private static final long TAILLE_DATA_INCONNUE = 0xFFFFFFFFL;

    /// Fenêtre lue pour un en-tête seul : largement de quoi contenir `fmt`, `data` et les chunks annexes
    /// (`LIST`, `bext`) que certains enregistreurs intercalent avant les données.
    private static final int FENETRE_ENTETE = 8192;

    /// Octets par trame (échantillon multi-canal) : `canaux * bits/8`.
    public int octetsParTrame() {
        return nombreCanaux * (bitsParEchantillon / 8);
    }

    /// Nombre de trames contenues dans [#donneesPcm].
    public long nombreTrames() {
        return (long) donneesPcm.length / octetsParTrame();
    }

    /// Durée en secondes du signal porté par ce fichier (trames / fréquence).
    public double dureeSecondes() {
        return nombreTrames() / (double) frequenceEchantillonnageHz;
    }

    /// Ce qu'un WAV dit de lui-même **sans qu'on charge son signal** (#1934) : de quoi calculer une durée,
    /// donc un nombre de tranches, au prix d'une lecture de quelques kilo-octets.
    ///
    /// L'hydratation en a besoin pour **toute une nuit** avant de régénérer quoi que ce soit : sans les
    /// durées, elle ne peut pas rejouer l'arbitrage des collisions de noms, et perd les tranches perdantes.
    /// Charger le PCM de 1800 fichiers pour n'en lire que l'en-tête serait absurde.
    ///
    /// @param octetsDonnees taille du chunk `data`, en octets
    public record EnteteWav(
            int nombreCanaux, int frequenceEchantillonnageHz, int bitsParEchantillon, long octetsDonnees) {

        public int octetsParTrame() {
            return nombreCanaux * (bitsParEchantillon / 8);
        }

        public long nombreTrames() {
            return octetsDonnees / octetsParTrame();
        }

        /// Durée du signal **au rythme donné**, en secondes. On passe la fréquence plutôt que d'utiliser
        /// celle de l'en-tête : un brut PR est déjà expansé ×10, et c'est la fréquence d'**acquisition**
        /// (celle du log) qui donne la durée réelle, donc le découpage (R10).
        public double dureeSecondes(int frequenceAcquisitionHz) {
            return nombreTrames() / (double) frequenceAcquisitionHz;
        }
    }

    /// Ce que le balayage des chunks a trouvé, avant toute copie de données. Positions et longueur en
    /// `long` (#104) : le chunk `data` d'un enregistrement qui ne découpe pas dépasse 2 Gio.
    private record Zones(int canaux, int frequence, int bits, long dataDebut, long dataLongueur) {}

    /// Ce que le balayage sait lire : le fichier entier déjà en mémoire, son seul début, ou le fichier
    /// sur disque. Un seul balayage pour les trois, sans quoi la lecture par morceaux pourrait situer le
    /// signal ailleurs que la lecture complète.
    @FunctionalInterface
    private interface Source {

        /// Copie `n` octets pris à `position` dans le début de `destination`.
        ///
        /// @return `false` si ces octets ne sont pas tous lisibles
        boolean lire(long position, byte[] destination, int n) throws IOException;
    }

    /// Un WAV ouvert pour être lu **par morceaux** (#104) : l'en-tête est analysé à l'ouverture, puis le
    /// signal se lit dans un tampon fourni par l'appelant, sans que le fichier entier passe en mémoire.
    /// C'est ce que demande l'objectif O3 : « jamais de chargement complet d'un fichier en mémoire ».
    public interface LecturePcm extends AutoCloseable {

        /// L'en-tête du fichier, tel que [FichierWav#lireEntete] le rendrait.
        EnteteWav entete();

        /// Remplit `tampon` avec les octets PCM suivants. Le tampon est rempli **en entier** tant qu'il reste
        /// assez de signal : une découpe positionnelle en dépend, une tranche ne commence jamais au milieu
        /// d'une lecture.
        ///
        /// @return le nombre d'octets écrits au début de `tampon`, `0` quand tout le signal a été rendu
        /// @throws IOException si le fichier ne rend pas les octets que son en-tête annonce
        int lire(byte[] tampon) throws IOException;

        @Override
        void close() throws IOException;
    }

    /// Lit **seulement l'en-tête** d'un WAV : les premiers kilo-octets suffisent à situer les chunks `fmt`
    /// et `data`, et la taille du fichier donne la longueur des données quand l'en-tête l'ignore.
    ///
    /// @throws IOException si l'en-tête est absent, incomplet, non PCM, ou si les chunks ne tiennent pas
    ///     dans la fenêtre lue (fichier exotique : l'appelant retombe alors sur [#lire])
    public static EnteteWav lireEntete(Path fichier) throws IOException {
        long taille = Files.size(fichier);
        byte[] debut = new byte[(int) Math.min(taille, FENETRE_ENTETE)];
        try (var flux = Files.newInputStream(fichier)) {
            int lus = flux.readNBytes(debut, 0, debut.length);
            if (lus < debut.length) {
                throw new IOException("Lecture d'en-tête incomplète : " + fichier);
            }
        }
        return entete(analyser(enMemoire(debut), debut.length, taille, fichier));
    }

    /// Lit un fichier WAV PCM depuis le disque, **en entier**. Réservé aux fichiers bornés par
    /// construction, une séquence d'écoute de 5 s par exemple ; un original se lit par [#ouvrirPcm].
    public static FichierWav lire(Path fichier) throws IOException {
        byte[] o = Files.readAllBytes(fichier);
        Zones zones = analyser(enMemoire(o), o.length, o.length, fichier);
        int debut = (int) zones.dataDebut();
        byte[] pcm = Arrays.copyOfRange(o, debut, debut + (int) zones.dataLongueur());
        return new FichierWav(zones.canaux(), zones.frequence(), zones.bits(), pcm);
    }

    /// Ouvre un WAV pour en lire le signal **par morceaux** (#104). Les chunks se repèrent par lectures
    /// positionnelles, sans fenêtre fixe : un chunk annexe volumineux avant `data` ne fait pas échouer
    /// l'ouverture, et aucune position ne déborde au-delà de 2 Gio.
    ///
    /// @throws IOException si le fichier est illisible, tronqué, non PCM ou sans chunk `fmt`/`data`
    public static LecturePcm ouvrirPcm(Path fichier) throws IOException {
        FileChannel canal = FileChannel.open(fichier, StandardOpenOption.READ);
        boolean rendu = false;
        try {
            long taille = canal.size();
            Zones zones = analyser(
                    (position, destination, n) -> lirePlein(canal, position, destination, n) == n,
                    taille,
                    taille,
                    fichier);
            LecturePcm lecture = new LecturePcmSurCanal(canal, entete(zones), zones.dataDebut(), fichier);
            rendu = true;
            return lecture;
        } finally {
            // Un en-tête refusé ne laisse pas de canal ouvert derrière lui ; un canal rendu, l'appelant le ferme.
            if (!rendu) {
                canal.close();
            }
        }
    }

    private static EnteteWav entete(Zones zones) {
        return new EnteteWav(zones.canaux(), zones.frequence(), zones.bits(), zones.dataLongueur());
    }

    private static Source enMemoire(byte[] octets) {
        return (position, destination, n) -> {
            if (position + n > octets.length) {
                return false;
            }
            System.arraycopy(octets, (int) position, destination, 0, n);
            return true;
        };
    }

    /// Lit `n` octets à `position`, en relançant la lecture tant que le canal en rend moins : un canal ne
    /// promet pas de remplir le tampon d'un seul appel.
    ///
    /// @return le nombre d'octets lus, moins que `n` seulement si la fin du fichier est atteinte
    private static int lirePlein(FileChannel canal, long position, byte[] destination, int n) throws IOException {
        ByteBuffer tampon = ByteBuffer.wrap(destination, 0, n);
        while (tampon.hasRemaining()) {
            if (canal.read(tampon, position + tampon.position()) < 0) {
                break;
            }
        }
        return tampon.position();
    }

    /// La lecture par morceaux, sur le canal ouvert par [#ouvrirPcm].
    private static final class LecturePcmSurCanal implements LecturePcm {

        private final FileChannel canal;
        private final EnteteWav entete;
        private final long fin;
        private final Path fichier;
        private long position;

        LecturePcmSurCanal(FileChannel canal, EnteteWav entete, long dataDebut, Path fichier) {
            this.canal = canal;
            this.entete = entete;
            this.position = dataDebut;
            this.fin = dataDebut + entete.octetsDonnees();
            this.fichier = fichier;
        }

        @Override
        public EnteteWav entete() {
            return entete;
        }

        @Override
        public int lire(byte[] tampon) throws IOException {
            int attendus = (int) Math.min(tampon.length, fin - position);
            int lus = lirePlein(canal, position, tampon, attendus);
            if (lus < attendus) {
                // Le fichier a raccourci depuis l'ouverture : rendre moins sans le dire décalerait les
                // tranches suivantes, et la dernière serait fausse sans qu'on le sache (#156).
                throw new IOException("Fichier WAV tronqué en cours de lecture : " + fichier);
            }
            position += lus;
            return lus;
        }

        @Override
        public void close() throws IOException {
            canal.close();
        }
    }

    /// Balaye les chunks RIFF. **Un seul balayage** pour les deux lectures : l'en-tête seul et le fichier
    /// entier doivent voir exactement la même chose, sans quoi une durée pourrait dire autre chose que le
    /// signal qu'on découpe ensuite.
    ///
    /// @param source les octets lisibles - le fichier entier, seulement son début, ou le fichier sur disque
    /// @param tailleLisible combien d'octets `source` peut rendre, depuis le début du fichier
    /// @param tailleFichier la taille **réelle** du fichier, qui borne les données quand l'en-tête annonce
    ///     une taille inconnue ou trop grande
    private static Zones analyser(Source source, long tailleLisible, long tailleFichier, Path fichier)
            throws IOException {
        byte[] riff = new byte[12];
        if (tailleLisible < 12 || !source.lire(0, riff, 12) || !tag(riff, 0, TAG_RIFF) || !tag(riff, 8, TAG_WAVE)) {
            throw new IOException("Fichier WAV invalide (en-tête RIFF/WAVE absent) : " + fichier);
        }
        Integer canaux = null;
        Integer frequence = null;
        Integer bits = null;
        int formatAudio = 0;
        long dataDebut = -1;
        long dataLongueur = -1;

        byte[] chunk = new byte[8];
        byte[] fmt = new byte[16];
        long pos = 12;
        while (pos + 8 <= tailleLisible && source.lire(pos, chunk, 8)) {
            String id = new String(chunk, 0, 4, StandardCharsets.US_ASCII);
            long taille = lireUint32(chunk, 4);
            long corps = pos + 8;
            if (TAG_FMT.equals(id) && corps + 16 <= tailleLisible && source.lire(corps, fmt, 16)) {
                formatAudio = lireUint16(fmt, 0);
                canaux = lireUint16(fmt, 2);
                frequence = (int) lireUint32(fmt, 4);
                bits = lireUint16(fmt, 14);
            } else if ("data".equals(id)) {
                dataDebut = corps;
                long disponible = tailleFichier - corps;
                if (taille == TAILLE_DATA_INCONNUE) {
                    // Taille inconnue (enregistreur en flux) : les données vont jusqu'à la fin du fichier.
                    // On arrête ici le balayage des chunks : avancer de (int) 0xFFFFFFFF = -1 (+1 de padding)
                    // ramènerait `pos` au début du PCM et relirait les données audio comme de faux chunks
                    // RIFF (corruption de dataDebut/dataLongueur, PCM vide ou « tronqué » à tort).
                    dataLongueur = disponible;
                    break;
                }
                // Intégrité (#156) : un en-tête qui annonce plus d'octets de données que le fichier n'en
                // contient = fichier **tronqué/corrompu**. On le refuse au lieu de le lire silencieusement
                // plus court (ce qui « passerait inaperçu » et fausserait l'analyse).
                if (taille > disponible) {
                    throw new IOException("Fichier WAV tronqué (données annoncées "
                            + taille
                            + " octets, "
                            + disponible
                            + " présents) : "
                            + fichier);
                }
                dataLongueur = Math.min(taille, disponible);
            }
            if (canaux != null && dataDebut >= 0) {
                // Tout ce qu'on cherche est trouvé. S'arrêter là est nécessaire pour la lecture d'en-tête
                // seul - avancer au chunk suivant sortirait de la fenêtre lue - et sans effet sur la
                // lecture complète, qui n'exploite aucun chunk au-delà.
                break;
            }
            // Avance au chunk suivant (taille + padding éventuel pour rester aligné sur un mot).
            pos = corps + taille + (taille % 2 == 1 ? 1 : 0);
        }

        if (canaux == null || frequence == null || bits == null || dataDebut < 0) {
            throw new IOException("Fichier WAV incomplet (chunk fmt/data manquant) : " + fichier);
        }
        if (formatAudio != FORMAT_PCM) {
            throw new IOException("Seul le PCM non compressé est géré (format=" + formatAudio + ") : " + fichier);
        }
        return new Zones(canaux, frequence, bits, dataDebut, dataLongueur);
    }

    /// Écrit un WAV canonique (en-tête 44 octets) avec la fréquence et le format donnés, en copiant
    /// **tels quels** les octets `pcm[offset, offset+longueur)`.
    public static void ecrire(
            Path fichier,
            int nombreCanaux,
            int frequenceEchantillonnageHz,
            int bitsParEchantillon,
            byte[] pcm,
            int offset,
            int longueur)
            throws IOException {
        int blocAlign = nombreCanaux * (bitsParEchantillon / 8);
        int debitOctets = frequenceEchantillonnageHz * blocAlign;
        ByteBuffer buf = ByteBuffer.allocate(TAILLE_ENTETE + longueur).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(TAG_RIFF.getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + longueur); // taille du fichier - 8
        buf.put(TAG_WAVE.getBytes(StandardCharsets.US_ASCII));
        buf.put(TAG_FMT.getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16); // taille du sous-chunk fmt (PCM)
        buf.putShort(FORMAT_PCM);
        buf.putShort((short) nombreCanaux);
        buf.putInt(frequenceEchantillonnageHz);
        buf.putInt(debitOctets);
        buf.putShort((short) blocAlign);
        buf.putShort((short) bitsParEchantillon);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(longueur);
        buf.put(pcm, offset, longueur);
        Files.write(fichier, buf.array());
    }

    private static boolean tag(byte[] o, int pos, String attendu) {
        return new String(o, pos, 4, StandardCharsets.US_ASCII).equals(attendu);
    }

    private static int lireUint16(byte[] o, int pos) {
        return (o[pos] & 0xFF) | ((o[pos + 1] & 0xFF) << 8);
    }

    private static long lireUint32(byte[] o, int pos) {
        return (o[pos] & 0xFFL)
                | ((o[pos + 1] & 0xFFL) << 8)
                | ((o[pos + 2] & 0xFFL) << 16)
                | ((o[pos + 3] & 0xFFL) << 24);
    }
}
