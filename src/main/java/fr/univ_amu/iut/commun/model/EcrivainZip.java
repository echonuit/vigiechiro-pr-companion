package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/// Écrivain d'archive ZIP **généraliste** du socle (#2792) : une archive, à un chemin choisi,
/// composée d'entrées **texte** (générées en mémoire, un CSV par exemple - aucun fichier
/// intermédiaire) et d'entrées **fichier** (recopiées en flux, mémoire bornée, patron
/// `CompacteurDepot`). Ce que le compacteur du dépôt ne sait pas faire : il est marié à sa
/// sémantique (nommage imposé, plafond, découpage) - lui reste le pipeline VigieChiro.
///
/// **Annulation et échec ne laissent rien** : le jeton est consulté avant chaque entrée, **pendant** la
/// copie de chacune ([CopieInterruptible], #2733) et une dernière fois avant de conclure ; sur
/// [OperationAnnuleeException] comme sur toute erreur, l'archive **partielle est supprimée** (patron
/// `ExtracteurZip` : un fichier absent vaut mieux qu'un fichier menteur). La progression est
/// **déterminée** (« Archive : X / N · nom »), notifiée après chaque entrée fichier et par paliers de
/// volume à l'intérieur d'une entrée longue, possiblement hors du fil JavaFX (l'appelant marshale).
public final class EcrivainZip {

    /// Une entrée générée en mémoire (UTF-8), typiquement le CSV qui accompagne les fichiers.
    public record EntreeTexte(String nomEntree, String contenu) {

        public EntreeTexte {
            exigerNom(nomEntree);
            Objects.requireNonNull(contenu, "contenu");
        }
    }

    /// Un fichier existant à recopier dans l'archive sous `nomEntree`.
    public record EntreeFichier(String nomEntree, Path source) {

        public EntreeFichier {
            exigerNom(nomEntree);
            Objects.requireNonNull(source, "source");
        }
    }

    private EcrivainZip() {}

    /// Écrit l'archive `destination` (remplacée si elle existe : l'appelant a déjà confirmé
    /// l'écrasement au moment de désigner le fichier). Renvoie la **taille de l'archive** en octets.
    ///
    /// @param surProgression notifié après chaque entrée fichier (avancement déterminé), et par paliers
    ///     de volume pendant une entrée longue (#2733)
    /// @param jeton annulation coopérative, consultée avant chaque entrée et pendant la copie de chacune
    /// @throws OperationAnnuleeException si `jeton` est annulé - l'archive partielle est supprimée
    /// @throws IOException sur échec d'écriture ou source illisible - l'archive partielle est supprimée
    public static long ecrire(
            Path destination,
            List<EntreeTexte> textes,
            List<EntreeFichier> fichiers,
            Consumer<Progression> surProgression,
            JetonAnnulation jeton)
            throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(surProgression, "surProgression");
        Objects.requireNonNull(jeton, "jeton");
        try (OutputStream sortie = new BufferedOutputStream(Files.newOutputStream(destination));
                ZipOutputStream zip = new ZipOutputStream(sortie)) {
            for (EntreeTexte texte : textes) {
                jeton.leverSiAnnule();
                zip.putNextEntry(new ZipEntry(texte.nomEntree()));
                zip.write(texte.contenu().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            int faits = 0;
            for (EntreeFichier fichier : fichiers) {
                jeton.leverSiAnnule();
                zip.putNextEntry(new ZipEntry(fichier.nomEntree()));
                int rang = faits;
                try (InputStream source = Files.newInputStream(fichier.source())) {
                    // Recopie en flux (mémoire bornée, #104) et interruptible en cours d'entrée (#2733).
                    CopieInterruptible.copier(
                            source,
                            zip,
                            jeton,
                            octets -> surProgression.accept(
                                    progressionEnCours(rang, fichiers.size(), fichier.nomEntree(), octets)));
                }
                zip.closeEntry();
                faits++;
                surProgression.accept(progression(faits, fichiers.size(), fichier.nomEntree()));
            }
            // Re-vérification finale : une annulation pendant la dernière entrée ne doit pas laisser
            // l'archive « aboutir » (le catch ci-dessous nettoie).
            jeton.leverSiAnnule();
        } catch (IOException | RuntimeException echec) {
            Files.deleteIfExists(destination);
            throw echec;
        }
        return Files.size(destination);
    }

    /// Point de progression d'une entrée **achevée** : « Archive : X / N · nom ».
    private static Progression progression(int faits, int total, String nomEntree) {
        return new Progression(
                "Archive : " + faits + " / " + total + " · " + nomCourt(nomEntree), (double) faits / total);
    }

    /// Point de progression **à l'intérieur** d'une entrée (#2733) : le compteur de fichiers n'a pas
    /// bougé, mais le volume recopié dit que l'archivage avance. La fraction reste celle des entrées
    /// achevées : une entrée en cours n'est pas une entrée écrite, et la barre ne doit pas le laisser
    /// croire.
    private static Progression progressionEnCours(int faits, int total, String nomEntree, long octetsEcrits) {
        Progression achevee = progression(faits, total, nomEntree);
        return new Progression(achevee.libelle() + " · " + Formats.octetsLisibles(octetsEcrits), achevee.fraction());
    }

    /// Dernier segment d'un nom d'entrée, pour afficher le **fichier courant** sans son chemin interne.
    private static String nomCourt(String nomEntree) {
        int barre = nomEntree.lastIndexOf('/');
        return barre < 0 ? nomEntree : nomEntree.substring(barre + 1);
    }

    private static void exigerNom(String nomEntree) {
        Objects.requireNonNull(nomEntree, "nomEntree");
        if (nomEntree.isBlank()) {
            throw new IllegalArgumentException("Le nom d'une entrée d'archive ne peut pas être vide.");
        }
    }
}
